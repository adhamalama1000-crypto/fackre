package com.warehouse.inventory.ui.products

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.util.LowStockNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val lowStockNotifier: LowStockNotifier
) : ViewModel() {

    /** One-shot snackbar message, as a string resource id. */
    private val _message = MutableStateFlow<Int?>(null)
    @get:StringRes
    val message: StateFlow<Int?> = _message.asStateFlow()

    val products: StateFlow<List<ProductWithCategory>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(item: ProductWithCategory) {
        viewModelScope.launch {
            when (productRepository.deleteProduct(item.product)) {
                DeleteResult.Success ->
                    // The deleted product may have been one of the low-stock warnings, so the
                    // shade and the suppression set both need reconciling.
                    lowStockNotifier.refresh()

                DeleteResult.NotAuthorized -> _message.value = R.string.error_not_authorized

                // History rows keep the product name and null their foreign key, so nothing
                // can hold a product back; an InUse here would be a bug, not a user error.
                else -> _message.value = R.string.error_generic
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}
