package com.warehouse.inventory.data.repository

import androidx.room.withTransaction
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.dao.InventoryTransactionDao
import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.dao.ProductStockDao
import com.warehouse.inventory.data.local.dao.WarehouseDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductStockEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.util.ImageStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val productStockDao: ProductStockDao,
    private val transactionDao: InventoryTransactionDao,
    private val warehouseDao: WarehouseDao,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {
    fun observeAll(): Flow<List<ProductWithCategory>> = productDao.observeAllWithCategory()

    fun search(
        query: String,
        categoryId: Long? = null,
        warehouseId: Long? = null,
        lowStockOnly: Boolean = false
    ): Flow<List<ProductWithCategory>> =
        productDao.search(query.trim(), categoryId, warehouseId, lowStockOnly)

    suspend fun listOnce(
        categoryId: Long? = null,
        warehouseId: Long? = null,
        lowStockOnly: Boolean = false
    ): List<ProductWithCategory> = productDao.listOnce(categoryId, warehouseId, lowStockOnly)

    fun observeProduct(id: Long): Flow<ProductWithCategory?> = productDao.observeWithCategory(id)

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    /** Resolves a scanned barcode (falling back to SKU). Null when nothing matches. */
    suspend fun findByCode(code: String): ProductEntity? {
        val clean = code.trim()
        if (clean.isEmpty()) return null
        return productDao.findByCode(clean)
    }

    /**
     * Creates or updates a product.
     *
     * On create, the opening quantity is written as a `product_stock` row in
     * [initialWarehouseId] *and* recorded as an opening-balance STOCK_IN, so stock that
     * exists in the system always has a matching history entry.
     *
     * On update, the quantity is deliberately left alone. Stock only moves through
     * [InventoryRepository] — receiving, issuing, adjusting or transferring — so that
     * editing a product's name can never quietly rewrite its balance. Correcting a
     * quantity is a stock adjustment, which requires a reason.
     */
    suspend fun saveProduct(
        id: Long?,
        name: String,
        categoryId: Long?,
        sku: String,
        barcode: String,
        unit: ProductUnit,
        location: String,
        notes: String,
        imagePath: String?,
        lowStockThreshold: Int,
        purchaseCost: Double?,
        sellingPrice: Double?,
        initialWarehouseId: Long? = null,
        initialQuantity: Int = 0
    ): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        if (lowStockThreshold < 0 || initialQuantity < 0) return SaveResult.MissingRequiredField

        // Blank codes are stored as NULL, never "". The unique indexes treat NULLs as
        // distinct, so any number of products may have no SKU/barcode — but two products
        // sharing "" would collide, which is why this normalization matters.
        val cleanSku = sku.trim().ifBlank { null }
        val cleanBarcode = barcode.trim().ifBlank { null }

        val actor = permissionChecker.requireAdmin("product_save") ?: return SaveResult.NotAuthorized

        // Pre-check uniqueness to give a field-level Arabic error. The unique indexes remain
        // the actual guarantee, and the catch below covers the race.
        val excludeId = id ?: 0L
        if (cleanSku != null && productDao.countWithSku(cleanSku, excludeId) > 0) {
            return SaveResult.Duplicate(DuplicateField.SKU)
        }
        if (cleanBarcode != null && productDao.countWithBarcode(cleanBarcode, excludeId) > 0) {
            return SaveResult.Duplicate(DuplicateField.BARCODE)
        }

        return try {
            db.withTransaction {
                val now = System.currentTimeMillis()
                if (id == null) {
                    val warehouseId = initialWarehouseId
                        ?: warehouseDao.getDefault()?.id
                        ?: return@withTransaction SaveResult.Failed("no_warehouse")
                    val warehouse = warehouseDao.getById(warehouseId)
                        ?: return@withTransaction SaveResult.Failed("no_warehouse")

                    val newId = productDao.insert(
                        ProductEntity(
                            name = cleanName,
                            categoryId = categoryId,
                            quantity = initialQuantity,
                            location = location.trim(),
                            notes = notes.trim(),
                            imagePath = imagePath,
                            lowStockThreshold = lowStockThreshold,
                            sku = cleanSku,
                            barcode = cleanBarcode,
                            unit = unit,
                            purchaseCost = purchaseCost,
                            sellingPrice = sellingPrice
                        )
                    )
                    productStockDao.insertIgnore(
                        ProductStockEntity(
                            productId = newId,
                            warehouseId = warehouseId,
                            quantity = initialQuantity,
                            updatedAt = now
                        )
                    )
                    if (initialQuantity > 0) {
                        transactionDao.insert(
                            InventoryTransactionEntity(
                                type = TransactionType.STOCK_IN,
                                productId = newId,
                                productName = cleanName,
                                productSku = cleanSku,
                                unit = unit,
                                quantity = initialQuantity,
                                previousQuantity = 0,
                                newQuantity = initialQuantity,
                                difference = initialQuantity,
                                warehouseId = warehouseId,
                                warehouseName = warehouse.name,
                                notes = "الرصيد الافتتاحي",
                                date = now,
                                userId = actor.id,
                                userName = actor.name
                            )
                        )
                    }
                    auditLogRepository.recordAs(
                        actor, AuditAction.PRODUCT_CREATED, "product", newId, cleanName,
                        "الرصيد الافتتاحي ${initialQuantity} في ${warehouse.name}"
                    )
                    SaveResult.Success(newId)
                } else {
                    val existing = productDao.getById(id) ?: return@withTransaction SaveResult.NotFound
                    productDao.update(
                        existing.copy(
                            name = cleanName,
                            categoryId = categoryId,
                            // quantity intentionally untouched — see the doc comment.
                            location = location.trim(),
                            notes = notes.trim(),
                            imagePath = imagePath,
                            lowStockThreshold = lowStockThreshold,
                            sku = cleanSku,
                            barcode = cleanBarcode,
                            unit = unit,
                            purchaseCost = purchaseCost,
                            sellingPrice = sellingPrice,
                            updatedAt = now,
                            synced = false
                        )
                    )
                    auditLogRepository.recordAs(
                        actor, AuditAction.PRODUCT_EDITED, "product", id, cleanName
                    )
                    SaveResult.Success(id)
                }
            }
        } catch (e: Exception) {
            // Lost the uniqueness race between the pre-check and the insert.
            when {
                cleanBarcode != null && productDao.countWithBarcode(cleanBarcode, excludeId) > 0 ->
                    SaveResult.Duplicate(DuplicateField.BARCODE)
                cleanSku != null && productDao.countWithSku(cleanSku, excludeId) > 0 ->
                    SaveResult.Duplicate(DuplicateField.SKU)
                else -> SaveResult.Failed(e.message)
            }
        }
    }

    /**
     * Deletes a product and its photo. `product_stock` rows cascade away; history rows keep
     * the denormalized product name and have `productId` nulled, so past movements survive.
     */
    suspend fun deleteProduct(product: ProductEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("product_delete")
            ?: return DeleteResult.NotAuthorized
        return try {
            productDao.delete(product)
            ImageStorage.deleteIfExists(product.imagePath)
            auditLogRepository.recordAs(
                actor, AuditAction.PRODUCT_DELETED, "product", product.id, product.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }

    // Dashboard flows
    fun observeTotalProducts(): Flow<Int> = productDao.observeTotalProducts()
    fun observeTotalQuantity(): Flow<Int> = productDao.observeTotalQuantity()
    fun observeLowStockCount(): Flow<Int> = productDao.observeLowStockCount()
    fun observeLowStockProducts(): Flow<List<ProductEntity>> = productDao.observeLowStockProducts()
    suspend fun getLowStockProducts(): List<ProductEntity> = productDao.getLowStockProducts()

    /** Total stock valued at purchase cost. Admin-visible only — gated in the ViewModel. */
    fun observeInventoryValue(): Flow<Double> = productDao.observeInventoryValue()
}
