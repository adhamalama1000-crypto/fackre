package com.warehouse.inventory.data.repository

import androidx.room.withTransaction
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.dao.ProductStockDao
import com.warehouse.inventory.data.local.dao.WarehouseDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.local.entity.WarehouseTotals
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val db: AppDatabase,
    private val warehouseDao: WarehouseDao,
    private val productStockDao: ProductStockDao,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {

    fun observeAll(): Flow<List<WarehouseEntity>> = warehouseDao.observeAll()

    fun search(query: String): Flow<List<WarehouseEntity>> = warehouseDao.search(query.trim())

    fun observeTotals(): Flow<List<WarehouseTotals>> = productStockDao.observeWarehouseTotals()

    fun observeCount(): Flow<Int> = warehouseDao.observeCount()

    suspend fun getAll(): List<WarehouseEntity> = warehouseDao.getAll()

    suspend fun getById(id: Long): WarehouseEntity? = warehouseDao.getById(id)

    suspend fun getDefault(): WarehouseEntity? = warehouseDao.getDefault()

    /**
     * Guarantees a warehouse exists to hold stock. Called on first launch; the v1 -> v2
     * migration does the equivalent for upgrades. Returns the default warehouse's id.
     *
     * If warehouses exist but somehow none is flagged default, the first one is promoted
     * rather than a second "main" warehouse being created.
     */
    suspend fun ensureDefaultWarehouse(): Long = db.withTransaction {
        warehouseDao.getDefault()?.let { return@withTransaction it.id }

        val existing = warehouseDao.getAll()
        val now = System.currentTimeMillis()
        if (existing.isNotEmpty()) {
            warehouseDao.markDefault(existing.first().id, now)
            return@withTransaction existing.first().id
        }
        warehouseDao.insert(
            WarehouseEntity(
                name = "المخزن الرئيسي",
                code = "MAIN",
                isDefault = true
            )
        )
    }

    suspend fun save(
        id: Long?,
        name: String,
        code: String,
        location: String,
        notes: String,
        makeDefault: Boolean = false
    ): SaveResult {
        val cleanName = name.trim()
        // Codes are compared and stored upper-case so "main" and "MAIN" cannot coexist.
        val cleanCode = code.trim().uppercase()
        if (cleanName.isEmpty() || cleanCode.isEmpty()) return SaveResult.MissingRequiredField

        val actor = permissionChecker.requireAdmin("warehouse_save")
            ?: return SaveResult.NotAuthorized

        val clash = warehouseDao.getByCode(cleanCode)
        if (clash != null && clash.id != id) return SaveResult.Duplicate(DuplicateField.CODE)

        return try {
            db.withTransaction {
                val now = System.currentTimeMillis()
                val savedId: Long
                if (id == null) {
                    // The very first warehouse is default regardless of the flag, so stock
                    // always has somewhere to go.
                    val isFirst = warehouseDao.count() == 0
                    savedId = warehouseDao.insert(
                        WarehouseEntity(
                            name = cleanName,
                            code = cleanCode,
                            location = location.trim(),
                            notes = notes.trim(),
                            isDefault = isFirst
                        )
                    )
                    auditLogRepository.recordAs(
                        actor, AuditAction.WAREHOUSE_CREATED, "warehouse", savedId, cleanName
                    )
                } else {
                    val existing = warehouseDao.getById(id)
                        ?: return@withTransaction SaveResult.NotFound
                    warehouseDao.update(
                        existing.copy(
                            name = cleanName,
                            code = cleanCode,
                            location = location.trim(),
                            notes = notes.trim(),
                            updatedAt = now,
                            synced = false
                        )
                    )
                    savedId = id
                    auditLogRepository.recordAs(
                        actor, AuditAction.WAREHOUSE_EDITED, "warehouse", id, cleanName
                    )
                }

                if (makeDefault) {
                    warehouseDao.clearDefault(now)
                    warehouseDao.markDefault(savedId, now)
                }
                SaveResult.Success(savedId)
            }
        } catch (e: Exception) {
            SaveResult.Duplicate(DuplicateField.CODE)
        }
    }

    /**
     * Deletes a warehouse, but refuses while it still holds stock.
     *
     * `product_stock` cascades on delete, so without this check removing a warehouse would
     * quietly destroy every quantity in it and leave the cached product totals overstated.
     * The last remaining warehouse cannot be deleted either — stock needs somewhere to live.
     */
    suspend fun delete(warehouse: WarehouseEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("warehouse_delete")
            ?: return DeleteResult.NotAuthorized

        val held = productStockDao.totalForWarehouse(warehouse.id)
        if (held > 0) return DeleteResult.InUse(held.toString())
        if (warehouseDao.count() <= 1) return DeleteResult.InUse("last")

        return try {
            db.withTransaction {
                // Only zero-quantity rows remain; clearing them keeps the cascade a no-op.
                productStockDao.deleteForWarehouse(warehouse.id)
                warehouseDao.delete(warehouse)
                if (warehouse.isDefault) {
                    warehouseDao.getAll().firstOrNull()?.let {
                        warehouseDao.markDefault(it.id, System.currentTimeMillis())
                    }
                }
            }
            auditLogRepository.recordAs(
                actor, AuditAction.WAREHOUSE_DELETED, "warehouse", warehouse.id, warehouse.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }

    suspend fun setDefault(id: Long): SaveResult {
        val actor = permissionChecker.requireAdmin("warehouse_set_default")
            ?: return SaveResult.NotAuthorized
        val warehouse = warehouseDao.getById(id) ?: return SaveResult.NotFound
        val now = System.currentTimeMillis()
        db.withTransaction {
            warehouseDao.clearDefault(now)
            warehouseDao.markDefault(id, now)
        }
        auditLogRepository.recordAs(
            actor, AuditAction.WAREHOUSE_EDITED, "warehouse", id,
            warehouse.name, "تعيين كمخزن افتراضي"
        )
        return SaveResult.Success(id)
    }
}
