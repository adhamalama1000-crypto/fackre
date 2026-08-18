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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    val productId: Long? = null,
    val fromWarehouseId: Long? = null,
    val toWarehouseId: Long? = null,
    val employeeId: Long? = null,
    val quantity: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    /** Stock in the source warehouse. */
    val sourceAvailable: Int = 0,
    /** Stock in the destination warehouse. */
    val destinationAvailable: Int = 0,
    val unit: ProductUnit = ProductUnit.PIECE,
    val submitting: Boolean = false,
    val productError: Boolean = false,
    val quantityError: Boolean = false,
    val warehouseError: Boolean = false,
    @StringRes val message: Int? = null,
    val success: Boolean = false
) {
    private val parsedQuantity: Int?
        get() = quantity.toIntOrNull()?.takeIf { it > 0 }

    val sourceResulting: Int?
        get() = parsedQuantity?.let { sourceAvailable - it }

    val destinationResulting: Int?
        get() = parsedQuantity?.let { destinationAvailable + it }

    val exceedsAvailable: Boolean
        get() = (quantity.toIntOrNull() ?: 0) > sourceAvailable

    val sameWarehouse: Boolean
        get() = fromWarehouseId != null && fromWarehouseId == toWarehouseId
}

/**
 * Warehouse-to-warehouse transfer. The total across warehouses does not change; one
 * warehouse is debited and another credited in a single transaction.
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val formData: StockFormData,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

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
                if (_state.value.fromWarehouseId == null) {
                    _state.update { it.copy(fromWarehouseId = id) }
                    refreshBalances()
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
        refreshBalances()
    }

    fun onSourceSelected(id: Long) {
        _state.update {
            // Picking the destination as the source would leave an invalid pair on screen;
            // clearing it is clearer than showing an error the user did not cause.
            it.copy(
                fromWarehouseId = id,
                toWarehouseId = it.toWarehouseId?.takeIf { to -> to != id },
                warehouseError = false,
                message = null
            )
        }
        refreshBalances()
    }

    fun onDestinationSelected(id: Long) {
        _state.update { it.copy(toWarehouseId = id, warehouseError = false, message = null) }
        refreshBalances()
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
        val from = current.fromWarehouseId
        val to = current.toWarehouseId
        if (from == null || to == null) {
            _state.update { it.copy(warehouseError = true, message = R.string.select_warehouse) }
            return
        }
        if (from == to) {
            _state.update {
                it.copy(warehouseError = true, message = R.string.error_same_warehouse)
            }
            return
        }

        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = inventoryRepository.recordTransfer(
                productId = current.productId,
                fromWarehouseId = from,
                toWarehouseId = to,
                quantity = quantity,
                employeeId = current.employeeId,
                date = current.date,
                notes = current.notes
            )
            if (result is StockResult.Success) {
                // No low-stock refresh: a transfer leaves the company-wide total unchanged,
                // and the threshold is defined against that total, so nothing can cross it.
                _state.update {
                    TransferUiState(
                        productId = it.productId,
                        fromWarehouseId = it.fromWarehouseId,
                        toWarehouseId = it.toWarehouseId,
                        employeeId = it.employeeId,
                        date = it.date,
                        sourceAvailable = (it.sourceAvailable - quantity).coerceAtLeast(0),
                        destinationAvailable = it.destinationAvailable + quantity,
                        unit = it.unit,
                        success = true,
                        message = R.string.transfer_success
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        submitting = false,
                        quantityError = result is StockResult.InsufficientStock,
                        warehouseError = result is StockResult.SameWarehouse,
                        message = StockResultMessages.messageFor(result)
                    )
                }
            }
        }
    }

    private fun refreshBalances() {
        val current = _state.value
        val productId = current.productId ?: return
        viewModelScope.launch {
            val source = current.fromWarehouseId?.let { formData.quantityIn(productId, it) } ?: 0
            val destination = current.toWarehouseId?.let { formData.quantityIn(productId, it) } ?: 0
            val unit = products.value.firstOrNull { it.product.id == productId }?.product?.unit
                ?: ProductUnit.PIECE
            _state.update {
                it.copy(sourceAvailable = source, destinationAvailable = destination, unit = unit)
            }
        }
    }
}
