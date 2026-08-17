package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query(
        """
        SELECT p.*, c.name AS categoryName
        FROM products p
        LEFT JOIN categories c ON p.categoryId = c.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeAllWithCategory(): Flow<List<ProductWithCategory>>

    /**
     * Product search. Every filter is opt-in: pass '' for the query, null for
     * categoryId / warehouseId, and false for lowStockOnly to disable one.
     * A warehouseId restricts results to products actually holding stock there.
     */
    @Query(
        """
        SELECT p.*, c.name AS categoryName
        FROM products p
        LEFT JOIN categories c ON p.categoryId = c.id
        WHERE (
            :query = ''
            OR p.name LIKE '%' || :query || '%'
            OR c.name LIKE '%' || :query || '%'
            OR p.sku LIKE '%' || :query || '%'
            OR p.barcode LIKE '%' || :query || '%'
            OR p.location LIKE '%' || :query || '%'
          )
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:warehouseId IS NULL OR EXISTS (
                SELECT 1 FROM product_stock ps
                WHERE ps.productId = p.id AND ps.warehouseId = :warehouseId AND ps.quantity > 0
              ))
          AND (:lowStockOnly = 0 OR p.quantity <= p.lowStockThreshold)
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun search(
        query: String,
        categoryId: Long?,
        warehouseId: Long?,
        lowStockOnly: Boolean
    ): Flow<List<ProductWithCategory>>

    /** Same predicate as [search], fetched once — used by report exports. */
    @Query(
        """
        SELECT p.*, c.name AS categoryName
        FROM products p
        LEFT JOIN categories c ON p.categoryId = c.id
        WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:warehouseId IS NULL OR EXISTS (
                SELECT 1 FROM product_stock ps
                WHERE ps.productId = p.id AND ps.warehouseId = :warehouseId AND ps.quantity > 0
              ))
          AND (:lowStockOnly = 0 OR p.quantity <= p.lowStockThreshold)
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    suspend fun listOnce(
        categoryId: Long?,
        warehouseId: Long?,
        lowStockOnly: Boolean
    ): List<ProductWithCategory>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT p.*, c.name AS categoryName FROM products p LEFT JOIN categories c ON p.categoryId = c.id WHERE p.id = :id LIMIT 1")
    fun observeWithCategory(id: Long): Flow<ProductWithCategory?>

    // ---- Barcode / SKU identification ----

    /**
     * Resolves a scanned code. Matches the barcode first, then falls back to the SKU so a
     * printed SKU label scans too.
     */
    @Query("SELECT * FROM products WHERE barcode = :code OR sku = :code ORDER BY CASE WHEN barcode = :code THEN 0 ELSE 1 END LIMIT 1")
    suspend fun findByCode(code: String): ProductEntity?

    /**
     * Uniqueness pre-check for the product form, so the user gets an Arabic field error
     * instead of a raw constraint failure. The unique indexes remain the real guarantee.
     * Pass excludeId = the product being edited (or 0 when creating).
     */
    @Query("SELECT COUNT(*) FROM products WHERE barcode = :barcode AND id != :excludeId")
    suspend fun countWithBarcode(barcode: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE sku = :sku AND id != :excludeId")
    suspend fun countWithSku(sku: String, excludeId: Long): Int

    // ---- Dashboard aggregates ----

    @Query("SELECT COUNT(*) FROM products")
    fun observeTotalProducts(): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM products")
    fun observeTotalQuantity(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE quantity <= lowStockThreshold")
    fun observeLowStockCount(): Flow<Int>

    @Query("SELECT * FROM products WHERE quantity <= lowStockThreshold ORDER BY quantity ASC")
    fun observeLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE quantity <= lowStockThreshold ORDER BY quantity ASC")
    suspend fun getLowStockProducts(): List<ProductEntity>

    /** Total inventory value at purchase cost. Products without a cost contribute 0. */
    @Query("SELECT COALESCE(SUM(quantity * COALESCE(purchaseCost, 0)), 0) FROM products")
    fun observeInventoryValue(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    /**
     * Recomputes the cached `products.quantity` from the per-warehouse rows that own the
     * truth. Called inside the same transaction as every stock mutation, which is why the
     * cache cannot drift — it is never incremented by hand.
     */
    @Query(
        """
        UPDATE products
        SET quantity = (SELECT COALESCE(SUM(quantity), 0) FROM product_stock WHERE productId = :productId),
            updatedAt = :now,
            synced = 0
        WHERE id = :productId
        """
    )
    suspend fun refreshTotalQuantity(productId: Long, now: Long)
}
