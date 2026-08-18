package com.warehouse.inventory.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.EmployeeRepository
import com.warehouse.inventory.data.repository.HistoryFilter
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Backs the unified history screen.
 *
 * The single source of truth is [filter]: every control on the screen edits it, and the
 * list is derived from it by `flatMapLatest`, so a new query cancels the previous Room
 * flow instead of leaving two subscriptions racing to emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    productRepository: ProductRepository,
    employeeRepository: EmployeeRepository,
    warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    /**
     * The two date bounds as plain nullable dates, mirroring how the screen sets them.
     *
     * [HistoryFilter] encodes "unbounded" as `0L` / `Long.MAX_VALUE` because that is what
     * SQL needs; keeping that encoding out of the UI means the screen can simply ask
     * "is this bound set?". Only the three functions below write them, always alongside the
     * filter, so the two cannot drift apart.
     */
    private val _dateFrom = MutableStateFlow<Long?>(null)
    val dateFrom: StateFlow<Long?> = _dateFrom.asStateFlow()

    private val _dateTo = MutableStateFlow<Long?>(null)
    val dateTo: StateFlow<Long?> = _dateTo.asStateFlow()

    val history: StateFlow<List<InventoryTransactionEntity>> = _filter
        .flatMapLatest { inventoryRepository.observeFiltered(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Filter dropdown sources ----

    val products: StateFlow<List<ProductWithCategory>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Every employee, not just the active ones: an employee who has since left still has
     * movements in the history, and filtering by them must remain possible.
     */
    val employees: StateFlow<List<EmployeeEntity>> = employeeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> = warehouseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Filter mutations ----

    fun onQueryChange(value: String) = _filter.update { it.copy(query = value) }

    /**
     * "All types" is the unfiltered state, so the first tap on a single chip narrows to
     * that type rather than deselecting one of five. Emptying the selection widens back to
     * all, which is also how [HistoryFilter] treats an empty set for SQL.
     */
    fun toggleType(type: TransactionType) = _filter.update { current ->
        val all = TransactionType.entries.toSet()
        val next = when {
            current.types == all -> setOf(type)
            type in current.types -> current.types - type
            else -> current.types + type
        }
        current.copy(types = next.ifEmpty { all })
    }

    fun selectAllTypes() = _filter.update { it.copy(types = TransactionType.entries.toSet()) }

    fun onProductSelected(productId: Long?) = _filter.update { it.copy(productId = productId) }

    fun onEmployeeSelected(employeeId: Long?) = _filter.update { it.copy(employeeId = employeeId) }

    fun onWarehouseSelected(warehouseId: Long?) = _filter.update { it.copy(warehouseId = warehouseId) }

    /** Snapped to the start of the chosen day so the bound includes that whole day. */
    fun onDateFrom(millis: Long?) = _filter.update {
        it.copy(from = millis?.let(DateUtils::startOfDay) ?: 0L)
    }

    /** Snapped to the end of the chosen day, since the DAO bound is `date <= :to`. */
    fun onDateTo(millis: Long?) = _filter.update {
        it.copy(to = millis?.let(DateUtils::endOfDay) ?: Long.MAX_VALUE)
    }

    fun clearFilters() { _filter.value = HistoryFilter() }
}
