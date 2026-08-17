package com.warehouse.inventory.ui.stockout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.StockOutRepository
import com.warehouse.inventory.data.repository.StockOutResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StockOutError { NONE, INVALID_QUANTITY, INSUFFICIENT, PRODUCT_NOT_FOUND }

data class StockOutUiState(
    val selectedProductId: Long? = null,
    val quantity: String = "",
    val employeeName: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val error: StockOutError = StockOutError.NONE,
    val success: Boolean = false,
    val submitting: Boolean = false
)

@HiltViewModel
class StockOutViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockOutRepository: StockOutRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StockOutUiState())
    val state: StateFlow<StockOutUiState> = _state.asStateFlow()

    val products: StateFlow<List<ProductWithCategory>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun preselect(productId: Long?) {
        if (productId != null && _state.value.selectedProductId == null) {
            _state.update { it.copy(selectedProductId = productId) }
        }
    }

    fun onProductSelected(id: Long) = _state.update { it.copy(selectedProductId = id, error = StockOutError.NONE) }
    fun onQuantity(v: String) = _state.update { it.copy(quantity = v.filter { c -> c.isDigit() }, error = StockOutError.NONE) }
    fun onEmployee(v: String) = _state.update { it.copy(employeeName = v) }
    fun onDate(millis: Long) = _state.update { it.copy(date = millis) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }

    fun availableFor(productId: Long?, products: List<ProductWithCategory>): Int =
        products.firstOrNull { it.product.id == productId }?.product?.quantity ?: 0

    fun submit() {
        val s = _state.value
        val qty = s.quantity.toIntOrNull()
        if (s.selectedProductId == null) {
            _state.update { it.copy(error = StockOutError.PRODUCT_NOT_FOUND) }
            return
        }
        if (qty == null || qty <= 0) {
            _state.update { it.copy(error = StockOutError.INVALID_QUANTITY) }
            return
        }
        _state.update { it.copy(submitting = true, error = StockOutError.NONE) }
        viewModelScope.launch {
            val result = stockOutRepository.recordStockOut(
                productId = s.selectedProductId,
                quantity = qty,
                employeeName = s.employeeName,
                date = s.date,
                notes = s.notes
            )
            when (result) {
                StockOutResult.Success ->
                    _state.update { it.copy(success = true, submitting = false) }
                StockOutResult.InsufficientStock ->
                    _state.update { it.copy(error = StockOutError.INSUFFICIENT, submitting = false) }
                StockOutResult.InvalidQuantity ->
                    _state.update { it.copy(error = StockOutError.INVALID_QUANTITY, submitting = false) }
                StockOutResult.ProductNotFound ->
                    _state.update { it.copy(error = StockOutError.PRODUCT_NOT_FOUND, submitting = false) }
            }
        }
    }

    /** Resets the form after a successful submission so more items can be withdrawn. */
    fun consumeSuccess() {
        _state.update {
            StockOutUiState(selectedProductId = it.selectedProductId)
        }
    }
}
