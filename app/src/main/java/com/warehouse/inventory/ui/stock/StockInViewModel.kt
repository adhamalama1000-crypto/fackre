package com.warehouse.inventory.ui.stock

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.SupplierEntity
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.StockResult
import com.warehouse.inventory.ui.navigation.NavKeys
import com.warehouse.inventory.util.LowStockNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockInUiState(
    val productId: Long? = null,
    val warehouseId: Long? = null,
    val supplierId: Long? = null,
    val employeeId: Long? = null,
    val quantity: String = "",
    val invoiceNumber: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    /** Stock currently in the selected warehouse, for the balance preview. */
    val available: Int = 0,
    val unit: ProductUnit = ProductUnit.PIECE,
    val submitting: Boolean = false,
    val productError: Boolean = false,
    val quantityError: Boolean = false,
    @StringRes val message: Int? = null,
    val success: Boolean = false
) {
    /** Balance the confirm button would produce, or null while the quantity is unusable. */
    val resultingBalance: Int?
        get() = quantity.toIntOrNull()?.takeIf { it > 0 }?.let { available + it }
}

/**
 * Receiving stock. `new = current + received`, recorded against a supplier, an optional
 * employee and an invoice/shipment number.
 */
@HiltViewModel
class StockInViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val formData: StockFormData,
    private val lowStockNotifier: LowStockNotifier,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(StockInUiState())
    val state: StateFlow<StockInUiState> = _state.asStateFlow()

    val products: StateFlow<List<ProductWithCategory>> = formData.products()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val warehouses: StateFlow<List<WarehouseEntity>> = formData.warehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val employees: StateFlow<List<EmployeeEntity>> = formData.employees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = formData.suppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Codes handed back by the scanner destination, which writes onto this entry's
     * SavedStateHandle before popping itself. Observing it (rather than taking a one-off
     * argument) is what lets the user scan repeatedly without leaving the form.
     */
    val scannedCode: StateFlow<String?> =
        savedStateHandle.getStateFlow(NavKeys.SCANNED_CODE, null)

    init {
        // Preselect the default warehouse so the common case is one tap shorter.
        viewModelScope.launch {
            formData.defaultWarehouseId()?.let { id ->
                if (_state.value.warehouseId == null) {
                    _state.update { it.copy(warehouseId = id) }
                    refreshAvailable()
                }
            }
        }
    }

    fun preselectProduct(productId: Long?) {
        if (productId == null || _state.value.productId != null) return
        onProductSelected(productId)
    }

    fun onProductSelected(id: Long) {
        _state.update { it.copy(productId = id, productError = false, message = null) }
        refreshAvailable()
    }

    fun onWarehouseSelected(id: Long) {
        _state.update { it.copy(warehouseId = id, message = null) }
        refreshAvailable()
    }

    fun onSupplierSelected(id: Long) = _state.update { it.copy(supplierId = id) }
    fun onEmployeeSelected(id: Long) = _state.update { it.copy(employeeId = id) }
    fun onQuantityChange(value: String) =
        _state.update { it.copy(quantity = value, quantityError = false, message = null) }
    fun onInvoiceChange(value: String) = _state.update { it.copy(invoiceNumber = value) }
    fun onDateChange(millis: Long) = _state.update { it.copy(date = millis, message = null) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    /** Consumes a scanned code: selects the matching product or reports an unknown code. */
    fun onCodeScanned(code: String) {
        savedStateHandle.set<String?>(NavKeys.SCANNED_CODE, null)
        viewModelScope.launch {
            val product = formData.findByCode(code)
            if (product == null) {
                _state.update { it.copy(message = R.string.error_barcode_not_found) }
            } else {
                onProductSelected(product.id)
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun submit() {
        val current = _state.value
        val quantity = current.quantity.toIntOrNull()
        if (current.productId == null) {
            _state.update { it.copy(productError = true, message = R.string.select_product) }
            return
        }
        if (quantity == null || quantity <= 0) {
            _state.update {
                it.copy(quantityError = true, message = R.string.error_invalid_quantity)
            }
            return
        }
        val warehouseId = current.warehouseId ?: run {
            _state.update { it.copy(message = R.string.error_warehouse_not_found) }
            return
        }

        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = inventoryRepository.recordStockIn(
                productId = current.productId,
                warehouseId = warehouseId,
                quantity = quantity,
                supplierId = current.supplierId,
                employeeId = current.employeeId,
                date = current.date,
                invoiceNumber = current.invoiceNumber,
                notes = current.notes
            )
            if (result is StockResult.Success) {
                // Receiving can lift a product back above its threshold; refreshing here is
                // what clears a stale low-stock notification.
                lowStockNotifier.refresh()
                _state.update {
                    // Product, warehouse and supplier are kept: receiving a shipment means
                    // entering many lines for the same supplier in a row.
                    StockInUiState(
                        productId = it.productId,
                        warehouseId = it.warehouseId,
                        supplierId = it.supplierId,
                        employeeId = it.employeeId,
                        invoiceNumber = it.invoiceNumber,
                        date = it.date,
                        available = it.available + quantity,
                        unit = it.unit,
                        success = true,
                        message = R.string.stock_in_success
                    )
                }
            } else {
                _state.update {
                    it.copy(submitting = false, message = StockResultMessages.messageFor(result))
                }
            }
        }
    }

    fun consumeSuccess() = _state.update { it.copy(success = false) }

    /** Reloads the selected product's level in the selected warehouse. */
    private fun refreshAvailable() {
        val current = _state.value
        val productId = current.productId ?: return
        val warehouseId = current.warehouseId ?: return
        viewModelScope.launch {
            val available = formData.quantityIn(productId, warehouseId)
            val unit = products.value.firstOrNull { it.product.id == productId }?.product?.unit
                ?: ProductUnit.PIECE
            _state.update { it.copy(available = available, unit = unit) }
        }
    }
}
