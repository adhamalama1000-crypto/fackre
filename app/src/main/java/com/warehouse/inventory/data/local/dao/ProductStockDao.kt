package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warehouse.inventory.data.local.entity.ProductStockEntity
import com.warehouse.inventory.data.local.entity.WarehouseStock
import com.warehouse.inventory.data.local.entity.WarehouseTotals
import kotlinx.coroutines.flow.Flow

/**
 * Per-warehouse stock levels.
 *
 * The mutating queries here are deliberately guarded UPDATE statements rather than
 * read-modify-write in Kotlin: `WHERE quantity >= :amount` makes "don't go negative" a
 * property of the SQL itself, so a concurrent caller cannot slip between a check and a
 * write. A return value of 0 rows means the guard rejected the operation.
 */
@Dao
interface ProductStockDao {

    @Query("SELECT quantity FROM product_stock WHERE productId = :productId AND warehouseId = :warehouseId LIMIT 1")
    suspend fun getQuantity(productId: Long, warehouseId: Long): Int?

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM product_stock WHERE productId = :productId")
    suspend fun totalForProduct(productId: Long): Int

    @Query(
        """
        SELECT w.id AS warehouseId, w.name AS warehouseName, w.code AS warehouseCode,
               ps.quantity AS quantity
        FROM product_stock ps
        INNER JOIN warehouses w ON w.id = ps.warehouseId
        WHERE ps.productId = :productId AND ps.quantity > 0
        ORDER BY w.isDefault DESC, w.name COLLATE NOCASE ASC
        """
    )
    fun observeForProduct(productId: Long): Flow<List<WarehouseStock>>

    @Query(
        """
        SELECT w.id AS warehouseId, w.name AS warehouseName, w.code AS warehouseCode,
               COUNT(CASE WHEN ps.quantity > 0 THEN 1 END) AS productCount,
               COALESCE(SUM(ps.quantity), 0) AS totalQuantity
        FROM warehouses w
        LEFT JOIN product_stock ps ON ps.warehouseId = w.id
        GROUP BY w.id, w.name, w.code, w.isDefault
        ORDER BY w.isDefault DESC, w.name COLLATE NOCASE ASC
        """
    )
    fun observeWarehouseTotals(): Flow<List<WarehouseTotals>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM product_stock WHERE warehouseId = :warehouseId")
    suspend fun totalForWarehouse(warehouseId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: ProductStockEntity): Long

    /** Adds stock. Returns rows updated: 1 = applied, 0 = no such (product, warehouse) row. */
    @Query(
        """
        UPDATE product_stock SET quantity = quantity + :amount, updatedAt = :now, synced = 0
        WHERE productId = :productId AND warehouseId = :warehouseId
        """
    )
    suspend fun increase(productId: Long, warehouseId: Long, amount: Int, now: Long): Int

    /**
     * Removes stock only if at least [amount] is present.
     * Returns 1 when applied, 0 when there was not enough (or no row) — never goes negative.
     */
    @Query(
        """
        UPDATE product_stock SET quantity = quantity - :amount, updatedAt = :now, synced = 0
        WHERE productId = :productId AND warehouseId = :warehouseId AND quantity >= :amount
        """
    )
    suspend fun decrease(productId: Long, warehouseId: Long, amount: Int, now: Long): Int

    /** Sets an absolute quantity (stock adjustment). Rejects negatives via the guard. */
    @Query(
        """
        UPDATE product_stock SET quantity = :quantity, updatedAt = :now, synced = 0
        WHERE productId = :productId AND warehouseId = :warehouseId AND :quantity >= 0
        """
    )
    suspend fun setQuantity(productId: Long, warehouseId: Long, quantity: Int, now: Long): Int

    @Query("DELETE FROM product_stock WHERE warehouseId = :warehouseId")
    suspend fun deleteForWarehouse(warehouseId: Long)
}
