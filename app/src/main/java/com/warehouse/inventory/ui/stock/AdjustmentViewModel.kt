package com.warehouse.inventory.ui.stock

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.AdjustmentReason
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

data class AdjustmentUiState(
    val productId: Long? = null,
    val warehouseId: Long? = null,
    val employeeId: Long? = null,
    /** What was physically counted. Absolute, not a delta. */
    val physicalQuantity: String = "",
    val reason: AdjustmentReason? = null,
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    /** What the system currently believes is in the warehouse. */
    val systemQuantity: Int = 0,
    val unit: ProductUnit = ProductUnit.PIECE,
    val submitting: Boolean = false,
    val productError: Boolean = false,
    val quantityError: Boolean = false,
    val reasonError: Boolean = false,
    @StringRes val message: Int? = null,
    val success: Boolean = false
) {
    /** Signed correction: physical - system. Null until a usable number is entered. */
    val difference: Int?
        get() = physicalQuantity.toIntOrNull()?.let { it - systemQuantity }
}

/**
 * Stock adjustment: reconciles the system quantity to a physical count.
 *
 * The user enters what they counted and the signed difference is derived, rather than asking
 * for a delta — that is how a stocktake actually works, and it removes the chance of
 * entering "-3" as "3". A reason is mandatory, so a correction is never unexplained.
 */
@HiltViewModel
class AdjustmentViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val formData: StockFormData,
    private val lowStockNotifier: LowStockNotifier,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(AdjustmentUiState())
    val state: StateFlow<AdjustmentUiState> = _state.asStateFlow()

    val products: StateFlow<List<ProductWithCategory>> = formData.products()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val warehouses: StateFlow<List<WarehouseEntity>> = formData.warehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val employees: StateFlow<List<EmployeeEntity>> = formData.employees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reasons: List<AdjustmentReason> = AdjustmentReason.entries

    val scannedCode: StateFlow<String?> =
        savedStateHandle.getStateFlow(NavKeys.SCANNED_CODE, null)

    init {
        viewModelScope.launch {
            formData.defaultWarehouseId()?.let { id ->
                if (_state.value.warehouseId == null) {
                    _state.update { it.copy(warehouseId = id) }
                    refreshSystemQuantity()
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
        refreshSystemQuantity()
    }

    fun onWarehouseSelected(id: Long) {
        _state.update { it.copy(warehouseId = id, message = null) }
        refreshSystemQuantity()
    }

    fun onEmployeeSelected(id: Long) = _state.update { it.copy(employeeId = id) }
    fun onPhysicalQuantityChange(value: String) =
        _state.update { it.copy(physicalQuantity = value, quantityError = false, message = null) }
    fun onReasonSelected(reason: AdjustmentReason) =
        _state.update { it.copy(reason = reason, reasonError = false) }
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
        val physical = current.physicalQuantity.toIntOrNull()
        if (current.productId == null) {
            _state.update { it.copy(productError = true, message = R.string.select_product) }
            return
        }
        if (physical == null || physical < 0) {
            // Zero is legitimate here: counting nothing on the shelf is a real result.
            _state.update {
                it.copy(quantityError = true, message = R.string.error_invalid_quantity)
            }
            return
        }
        val reason = current.reason ?: run {
            _state.update { it.copy(reasonError = true, message = R.string.error_reason_required) }
            return
        }
        val warehouseId = current.warehouseId ?: run {
            _state.update { it.copy(message = R.string.error_warehouse_not_found) }
            return
        }

        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = inventoryRepository.recordAdjustment(
                productId = current.productId,
                warehouseId = warehouseId,
                newQuantity = physical,
                reason = reason,
                employeeId = current.employeeId,
                date = current.date,
                notes = current.notes
            )
            if (result is StockResult.Success) {
                lowStockNotifier.refresh()
                _state.update {
                    AdjustmentUiState(
                        productId = it.productId,
                        warehouseId = it.warehouseId,
                        employeeId = it.employeeId,
                        date = it.date,
                        systemQuantity = physical,
                        unit = it.unit,
                        success = true,
                        message = R.string.adjustment_success
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        submitting = false,
                        quantityError = result is StockResult.NoChange,
                        message = StockResultMessages.messageFor(result)
                    )
                }
            }
        }
    }

    private fun refreshSystemQuantity() {
        val current = _state.value
        val productId = current.productId ?: return
        val warehouseId = current.warehouseId ?: return
        viewModelScope.launch {
            val quantity = formData.quantityIn(productId, warehouseId)
            val unit = products.value.firstOrNull { it.product.id == productId }?.product?.unit
                ?: ProductUnit.PIECE
            _state.update { it.copy(systemQuantity = quantity, unit = unit) }
        }
    }
}
