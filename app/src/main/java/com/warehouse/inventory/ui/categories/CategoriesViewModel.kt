package com.warehouse.inventory.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val categories = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CategoryEntity>())

    fun add(name: String, onDuplicate: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val ok = categoryRepository.addCategory(name)
            if (!ok) onDuplicate()
        }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { categoryRepository.deleteCategory(category) }
    }
}
