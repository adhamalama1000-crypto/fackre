package com.warehouse.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.StockOutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardStats(
    val totalProducts: Int = 0,
    val totalQuantity: Int = 0,
    val lowStock: Int = 0,
    val outToday: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    productRepository: ProductRepository,
    stockOutRepository: StockOutRepository
) : ViewModel() {

    val stats = combine(
        productRepository.observeTotalProducts(),
        productRepository.observeTotalQuantity(),
        productRepository.observeLowStockCount(),
        stockOutRepository.observeCountToday()
    ) { total, qty, low, today ->
        DashboardStats(total, qty, low, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())
}
