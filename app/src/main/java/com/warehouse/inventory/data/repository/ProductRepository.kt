package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.util.ImageStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    fun observeAll(): Flow<List<ProductWithCategory>> = productDao.observeAllWithCategory()

    fun search(query: String, categoryId: Long?): Flow<List<ProductWithCategory>> =
        productDao.search(query.trim(), categoryId)

    fun observeProduct(id: Long): Flow<ProductWithCategory?> = productDao.observeWithCategory(id)

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    suspend fun addProduct(product: ProductEntity): Long = productDao.insert(product)

    suspend fun updateProduct(product: ProductEntity) =
        productDao.update(product.copy(updatedAt = System.currentTimeMillis(), synced = false))

    suspend fun deleteProduct(product: ProductEntity) {
        ImageStorage.deleteIfExists(product.imagePath)
        productDao.delete(product)
    }

    // Dashboard flows
    fun observeTotalProducts(): Flow<Int> = productDao.observeTotalProducts()
    fun observeTotalQuantity(): Flow<Int> = productDao.observeTotalQuantity()
    fun observeLowStockCount(): Flow<Int> = productDao.observeLowStockCount()
    fun observeLowStockProducts(): Flow<List<ProductEntity>> = productDao.observeLowStockProducts()
}
