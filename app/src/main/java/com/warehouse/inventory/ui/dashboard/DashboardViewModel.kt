package com.warehouse.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Counters that do not depend on the viewer's role. */
private data class Counts(
    val totalProducts: Int,
    val totalQuantity: Int,
    val lowStock: Int,
    val warehouses: Int
)

private data class TodayMovements(val stockIn: Int, val stockOut: Int)

data class DashboardStats(
    val totalProducts: Int = 0,
    val totalQuantity: Int = 0,
    val lowStock: Int = 0,
    val inToday: Int = 0,
    val outToday: Int = 0,
    val warehouses: Int = 0,
    /**
     * Total stock valued at purchase cost, or null when the signed-in user is a viewer.
     *
     * Null is the "not permitted to see this" signal and the screen omits the tile entirely
     * rather than showing a zero, which would read as "the warehouse is worth nothing".
     */
    val inventoryValue: Double? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    productRepository: ProductRepository,
    inventoryRepository: InventoryRepository,
    warehouseRepository: WarehouseRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val counts: Flow<Counts> = combine(
        productRepository.observeTotalProducts(),
        productRepository.observeTotalQuantity(),
        productRepository.observeLowStockCount(),
        warehouseRepository.observeCount()
    ) { products, quantity, low, warehouses ->
        Counts(products, quantity, low, warehouses)
    }

    private val today: Flow<TodayMovements> = combine(
        inventoryRepository.observeStockInToday(),
        inventoryRepository.observeStockOutToday()
    ) { stockIn, stockOut -> TodayMovements(stockIn, stockOut) }

    /**
     * Inventory value is only collected for admins. Gating it here rather than in the
     * screen means a viewer's session never even runs the SUM query, so the figure cannot
     * leak through a state snapshot.
     */
    private val inventoryValue: Flow<Double?> = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user?.isAdmin == true) productRepository.observeInventoryValue().map { it }
            else flowOf(null)
        }

    val stats: StateFlow<DashboardStats> =
        combine(counts, today, inventoryValue) { c, t, value ->
            DashboardStats(
                totalProducts = c.totalProducts,
                totalQuantity = c.totalQuantity,
                lowStock = c.lowStock,
                inToday = t.stockIn,
                outToday = t.stockOut,
                warehouses = c.warehouses,
                inventoryValue = value
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    /** Newest movements across every type, for the activity strip. */
    val recentTransactions: StateFlow<List<InventoryTransactionEntity>> =
        inventoryRepository.observeRecent(RECENT_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Products at or below their threshold, capped so the dashboard stays scannable. */
    val lowStockProducts: StateFlow<List<ProductEntity>> =
        productRepository.observeLowStockProducts()
            .map { it.take(LOW_STOCK_PREVIEW) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private companion object {
        const val RECENT_LIMIT = 6
        const val LOW_STOCK_PREVIEW = 5
    }
}
