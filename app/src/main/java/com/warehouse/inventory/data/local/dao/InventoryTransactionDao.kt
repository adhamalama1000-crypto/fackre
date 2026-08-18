package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.PeriodActivity
import com.warehouse.inventory.data.local.entity.ProductMovementTotal
import kotlinx.coroutines.flow.Flow

/**
 * The unified inventory history: stock-in, stock-out, adjustments and transfer legs.
 *
 * Filtering note: [filter] takes `types` as a non-empty list of [com.warehouse.inventory.data.local.entity.TransactionType]
 * names. "All types" must be expressed by passing every name rather than an empty list,
 * because SQLite rejects an empty `IN ()`. `HistoryFilter.typeNames` does this.
 * The remaining filters are opt-in: pass null (or '' for the text query, 0 /
 * Long.MAX_VALUE for the date bounds) to disable one.
 */
@Dao
interface InventoryTransactionDao {

    @Query("SELECT * FROM inventory_transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions ORDER BY date DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions WHERE productId = :productId ORDER BY date DESC, id DESC")
    fun observeForProduct(productId: Long): Flow<List<InventoryTransactionEntity>>

    @Query(
        """
        SELECT t.* FROM inventory_transactions t
        LEFT JOIN products p ON p.id = t.productId
        WHERE t.type IN (:types)
          AND (:productId IS NULL OR t.productId = :productId)
          AND (:employeeId IS NULL OR t.employeeId = :employeeId)
          AND (:warehouseId IS NULL OR t.warehouseId = :warehouseId)
          AND (:supplierId IS NULL OR t.supplierId = :supplierId)
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND t.date >= :from AND t.date <= :to
          AND (
            :query = ''
            OR t.productName LIKE '%' || :query || '%'
            OR t.productSku LIKE '%' || :query || '%'
            OR t.employeeName LIKE '%' || :query || '%'
            OR t.supplierName LIKE '%' || :query || '%'
            OR t.invoiceNumber LIKE '%' || :query || '%'
            OR t.warehouseName LIKE '%' || :query || '%'
            OR t.notes LIKE '%' || :query || '%'
          )
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun filter(
        types: List<String>,
        productId: Long?,
        employeeId: Long?,
        warehouseId: Long?,
        supplierId: Long?,
        categoryId: Long?,
        from: Long,
        to: Long,
        query: String
    ): Flow<List<InventoryTransactionEntity>>

    /** Same predicate as [filter], returned once instead of observed — used by exports. */
    @Query(
        """
        SELECT t.* FROM inventory_transactions t
        LEFT JOIN products p ON p.id = t.productId
        WHERE t.type IN (:types)
          AND (:productId IS NULL OR t.productId = :productId)
          AND (:employeeId IS NULL OR t.employeeId = :employeeId)
          AND (:warehouseId IS NULL OR t.warehouseId = :warehouseId)
          AND (:supplierId IS NULL OR t.supplierId = :supplierId)
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND t.date >= :from AND t.date <= :to
          AND (
            :query = ''
            OR t.productName LIKE '%' || :query || '%'
            OR t.productSku LIKE '%' || :query || '%'
            OR t.employeeName LIKE '%' || :query || '%'
            OR t.supplierName LIKE '%' || :query || '%'
            OR t.invoiceNumber LIKE '%' || :query || '%'
            OR t.warehouseName LIKE '%' || :query || '%'
            OR t.notes LIKE '%' || :query || '%'
          )
        ORDER BY t.date DESC, t.id DESC
        """
    )
    suspend fun filterOnce(
        types: List<String>,
        productId: Long?,
        employeeId: Long?,
        warehouseId: Long?,
        supplierId: Long?,
        categoryId: Long?,
        from: Long,
        to: Long,
        query: String
    ): List<InventoryTransactionEntity>

    // ---- Dashboard aggregates ----

    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM inventory_transactions
        WHERE type = :type AND date >= :from AND date < :to
        """
    )
    fun observeQuantityInRange(type: String, from: Long, to: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM inventory_transactions
        WHERE type = :type AND date >= :from AND date < :to
        """
    )
    fun observeCountInRange(type: String, from: Long, to: Long): Flow<Int>

    // ---- Report aggregates ----

    /** Top products by moved quantity for one transaction type in a date range. */
    @Query(
        """
        SELECT t.productId AS productId, t.productName AS productName, t.unit AS unit,
               COALESCE(SUM(t.quantity), 0) AS totalQuantity,
               COUNT(*) AS transactionCount
        FROM inventory_transactions t
        WHERE t.type = :type AND t.date >= :from AND t.date <= :to
        GROUP BY t.productId, t.productName, t.unit
        ORDER BY totalQuantity DESC
        LIMIT :limit
        """
    )
    suspend fun topProductsByType(type: String, from: Long, to: Long, limit: Int): List<ProductMovementTotal>

    /**
     * Activity grouped per day or per month.
     * [format] is a SQLite strftime pattern: '%Y-%m-%d' for daily, '%Y-%m' for monthly.
     * `date` is epoch millis, so it is divided to seconds and read as local time.
     */
    @Query(
        """
        SELECT strftime(:format, t.date / 1000, 'unixepoch', 'localtime') AS period,
               COALESCE(SUM(CASE WHEN t.type = 'STOCK_IN'  THEN t.quantity ELSE 0 END), 0) AS stockInQuantity,
               COALESCE(SUM(CASE WHEN t.type = 'STOCK_OUT' THEN t.quantity ELSE 0 END), 0) AS stockOutQuantity,
               COUNT(CASE WHEN t.type = 'ADJUSTMENT' THEN 1 END) AS adjustmentCount,
               COUNT(*) AS transactionCount
        FROM inventory_transactions t
        WHERE t.date >= :from AND t.date <= :to
        GROUP BY period
        ORDER BY period DESC
        """
    )
    suspend fun activityByPeriod(format: String, from: Long, to: Long): List<PeriodActivity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: InventoryTransactionEntity): Long
}
