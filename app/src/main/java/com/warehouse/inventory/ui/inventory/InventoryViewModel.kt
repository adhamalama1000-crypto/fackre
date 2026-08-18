package com.warehouse.inventory.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.CategoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The active filter set. Held as one object so a change re-runs exactly one query. */
data class InventoryFilter(
    val query: String = "",
    val categoryId: Long? = null,
    val warehouseId: Long? = null,
    val lowStockOnly: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    categoryRepository: CategoryRepository,
    warehouseRepository: WarehouseRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val _filter = MutableStateFlow(InventoryFilter())
    val filter: StateFlow<InventoryFilter> = _filter.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> = warehouseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Whether to render purchase cost. Viewers must not see financial figures, so the flag
     * comes from the session rather than from a screen parameter.
     */
    val showFinancials: StateFlow<Boolean> = sessionManager.currentUser
        .map { it?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * A blank query matches everything in the DAO and null filters are ignored there, so this
     * single flow serves the unfiltered list and every combination of filters alike.
     */
    val products: StateFlow<List<ProductWithCategory>> = _filter
        .flatMapLatest { f ->
            productRepository.search(
                query = f.query,
                categoryId = f.categoryId,
                warehouseId = f.warehouseId,
                lowStockOnly = f.lowStockOnly
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) = _filter.update { it.copy(query = value) }
    fun onCategorySelected(id: Long?) = _filter.update { it.copy(categoryId = id) }
    fun onWarehouseSelected(id: Long?) = _filter.update { it.copy(warehouseId = id) }
    fun onLowStockOnlyChange(enabled: Boolean) =
        _filter.update { it.copy(lowStockOnly = enabled) }
}
