package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warehouse.inventory.data.local.entity.StockOutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockOutDao {

    @Query("SELECT * FROM stock_out ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<StockOutEntity>>

    @Query("SELECT * FROM stock_out WHERE productId = :productId ORDER BY date DESC")
    fun observeForProduct(productId: Long): Flow<List<StockOutEntity>>

    /** Number of stock-out operations recorded within the given day range [start, end). */
    @Query("SELECT COUNT(*) FROM stock_out WHERE date >= :startOfDay AND date < :endOfDay")
    fun observeCountToday(startOfDay: Long, endOfDay: Long): Flow<Int>

    /** Total units withdrawn within the given day range. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_out WHERE date >= :startOfDay AND date < :endOfDay")
    fun observeUnitsToday(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: StockOutEntity): Long
}
