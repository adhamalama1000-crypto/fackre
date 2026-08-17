package com.warehouse.inventory.data.repository

import androidx.room.withTransaction
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.dao.StockOutDao
import com.warehouse.inventory.data.local.entity.StockOutEntity
import com.warehouse.inventory.util.DateUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StockOutResult {
    data object Success : StockOutResult
    data object InsufficientStock : StockOutResult
    data object InvalidQuantity : StockOutResult
    data object ProductNotFound : StockOutResult
}

@Singleton
class StockOutRepository @Inject constructor(
    private val db: AppDatabase,
    private val stockOutDao: StockOutDao,
    private val productDao: ProductDao
) {
    fun observeAll(): Flow<List<StockOutEntity>> = stockOutDao.observeAll()

    fun observeForProduct(productId: Long): Flow<List<StockOutEntity>> =
        stockOutDao.observeForProduct(productId)

    fun observeCountToday(): Flow<Int> {
        val (start, end) = DateUtils.todayRange()
        return stockOutDao.observeCountToday(start, end)
    }

    fun observeUnitsToday(): Flow<Int> {
        val (start, end) = DateUtils.todayRange()
        return stockOutDao.observeUnitsToday(start, end)
    }

    /**
     * Records a stock-out and decreases product quantity atomically.
     * Guarantees stock can never go negative (checked inside the SQL UPDATE + a DB transaction).
     */
    suspend fun recordStockOut(
        productId: Long,
        quantity: Int,
        employeeName: String,
        date: Long,
        notes: String
    ): StockOutResult {
        if (quantity <= 0) return StockOutResult.InvalidQuantity

        return db.withTransaction {
            val product = productDao.getById(productId)
                ?: return@withTransaction StockOutResult.ProductNotFound

            val updatedRows = productDao.decreaseStock(productId, quantity, System.currentTimeMillis())
            if (updatedRows == 0) {
                // Not enough stock -> transaction rolls back, nothing recorded.
                return@withTransaction StockOutResult.InsufficientStock
            }

            stockOutDao.insert(
                StockOutEntity(
                    productId = productId,
                    productName = product.name,
                    quantity = quantity,
                    employeeName = employeeName.trim(),
                    date = date,
                    notes = notes.trim()
                )
            )
            StockOutResult.Success
        }
    }
}
