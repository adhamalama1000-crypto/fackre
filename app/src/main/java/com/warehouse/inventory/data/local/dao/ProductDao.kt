package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
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
     * Search by product name OR category name. Pass categoryId = null for "all categories".
     */
    @Query(
        """
        SELECT p.*, c.name AS categoryName
        FROM products p
        LEFT JOIN categories c ON p.categoryId = c.id
        WHERE (:query = '' OR p.name LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun search(query: String, categoryId: Long?): Flow<List<ProductWithCategory>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT p.*, c.name AS categoryName FROM products p LEFT JOIN categories c ON p.categoryId = c.id WHERE p.id = :id LIMIT 1")
    fun observeWithCategory(id: Long): Flow<ProductWithCategory?>

    // ---- Dashboard aggregates ----
    @Query("SELECT COUNT(*) FROM products")
    fun observeTotalProducts(): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM products")
    fun observeTotalQuantity(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE quantity <= lowStockThreshold")
    fun observeLowStockCount(): Flow<Int>

    @Query("SELECT * FROM products WHERE quantity <= lowStockThreshold ORDER BY quantity ASC")
    fun observeLowStockProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    /** Atomically decrease stock without allowing negatives. Returns rows updated (1 = ok, 0 = insufficient). */
    @Query("UPDATE products SET quantity = quantity - :amount, updatedAt = :now, synced = 0 WHERE id = :productId AND quantity >= :amount")
    suspend fun decreaseStock(productId: Long, amount: Int, now: Long): Int
}
