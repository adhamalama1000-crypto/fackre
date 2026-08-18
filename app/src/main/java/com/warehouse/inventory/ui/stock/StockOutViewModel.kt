package com.warehouse.inventory.ui.stock

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.ProductWithCategory
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

data class StockOutUiState(
    val productId: Long? = null,
    val warehouseId: Long? = null,
    val employeeId: Long? = null,
    val quantity: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val available: Int = 0,
    val unit: ProductUnit = ProductUnit.PIECE,
    val submitting: Boolean = false,
    val productError: Boolean = false,
    val quantityError: Boolean = false,
    @StringRes val message: Int? = null,
    val success: Boolean = false
) {
    val resultingBalance: Int?
        get() = quantity.toIntOrNull()?.takeIf { it > 0 }?.let { available - it }

    /**
     * Whether the entered quantity exceeds what the warehouse holds. Used to warn before
     * submitting; the repository's guarded UPDATE is what actually prevents it.
     */
    val exceedsAvailable: Boolean
        get() = (quantity.toIntOrNull() ?: 0) > available
}

/** Issuing stock. `new = current - issued`, refused if the warehouse holds less. */
@HiltViewModel
class StockOutViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val formData: StockFormData,
    private val lowStockNotifier: LowStockNotifier,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(StockOutUiState())
    val state: StateFlow<StockOutUiState> = _state.asStateFlow()

    val products: StateFlow<List<ProductWithCategory>> = formData.products()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val warehouses: StateFlow<List<WarehouseEntity>> = formData.warehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val employees: StateFlow<List<EmployeeEntity>> = formData.employees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scannedCode: StateFlow<String?> =
        savedStateHandle.getStateFlow(NavKeys.SCANNED_CODE, null)

    init {
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

    fun onEmployeeSelected(id: Long) = _state.update { it.copy(employeeId = id) }
    fun onQuantityChange(value: String) =
        _state.update { it.copy(quantity = value, quantityError = false, message = null) }
    fun onDateChange(millis: Long) = _state.update { it.copy(date = millis, message = null) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

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
    fun consumeSuccess() = _state.update { it.copy(success = false) }

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
            val result = inventoryRepository.recordStockOut(
                productId = current.productId,
                warehouseId = warehouseId,
                quantity = quantity,
                employeeId = current.employeeId,
                date = current.date,
                notes = current.notes
            )
            if (result is StockResult.Success) {
                // Issuing is the operation most likely to push a product below its
                // threshold, so this is where a low-stock alert usually originates.
                lowStockNotifier.refresh()
                _state.update {
                    StockOutUiState(
                        productId = it.productId,
                        warehouseId = it.warehouseId,
                        employeeId = it.employeeId,
                        date = it.date,
                        available = (it.available - quantity).coerceAtLeast(0),
                        unit = it.unit,
                        success = true,
                        message = R.string.stock_out_success
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        submitting = false,
                        quantityError = result is StockResult.InsufficientStock,
                        message = StockResultMessages.messageFor(result)
                    )
                }
            }
        }
    }

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
