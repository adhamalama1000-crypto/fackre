package com.warehouse.inventory.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.repository.CategoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.util.ImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductFormState(
    val productId: Long? = null,
    val name: String = "",
    val categoryId: Long? = null,
    val quantity: String = "",
    val location: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    val lowStockThreshold: String = "5",
    val nameError: Boolean = false,
    val quantityError: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = false
) {
    val isEditing: Boolean get() = productId != null
}

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProductFormState())
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var loadedFor: Long? = null

    /** Loads an existing product for editing (once). Pass null for a new product. */
    fun load(productId: Long?) {
        if (productId == null) return
        if (loadedFor == productId) return
        loadedFor = productId
        viewModelScope.launch {
            productRepository.getById(productId)?.let { p ->
                _state.update {
                    it.copy(
                        productId = p.id,
                        name = p.name,
                        categoryId = p.categoryId,
                        quantity = p.quantity.toString(),
                        location = p.location,
                        notes = p.notes,
                        imagePath = p.imagePath,
                        lowStockThreshold = p.lowStockThreshold.toString()
                    )
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun onCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun onQuantity(v: String) = _state.update { it.copy(quantity = v.filter { c -> c.isDigit() }, quantityError = false) }
    fun onLocation(v: String) = _state.update { it.copy(location = v) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }
    fun onThreshold(v: String) = _state.update { it.copy(lowStockThreshold = v.filter { c -> c.isDigit() }) }

    fun onImageCaptured(path: String?) {
        if (path == null) return
        // Remove previous image if it was replaced before saving.
        val old = _state.value.imagePath
        if (old != null && old != path) ImageStorage.deleteIfExists(old)
        _state.update { it.copy(imagePath = path) }
    }

    fun save() {
        val s = _state.value
        val nameBlank = s.name.isBlank()
        val qty = s.quantity.toIntOrNull()
        val qtyInvalid = qty == null || qty < 0
        if (nameBlank || qtyInvalid) {
            _state.update { it.copy(nameError = nameBlank, quantityError = qtyInvalid) }
            return
        }
        _state.update { it.copy(loading = true) }
        val threshold = s.lowStockThreshold.toIntOrNull() ?: 5
        viewModelScope.launch {
            if (s.isEditing) {
                val existing = productRepository.getById(s.productId!!)
                if (existing != null) {
                    productRepository.updateProduct(
                        existing.copy(
                            name = s.name.trim(),
                            categoryId = s.categoryId,
                            quantity = qty!!,
                            location = s.location.trim(),
                            notes = s.notes.trim(),
                            imagePath = s.imagePath,
                            lowStockThreshold = threshold
                        )
                    )
                }
            } else {
                productRepository.addProduct(
                    ProductEntity(
                        name = s.name.trim(),
                        categoryId = s.categoryId,
                        quantity = qty!!,
                        location = s.location.trim(),
                        notes = s.notes.trim(),
                        imagePath = s.imagePath,
                        lowStockThreshold = threshold
                    )
                )
            }
            _state.update { it.copy(saved = true, loading = false) }
        }
    }
}
