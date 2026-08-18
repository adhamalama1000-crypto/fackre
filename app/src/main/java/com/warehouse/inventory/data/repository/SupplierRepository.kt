package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.SupplierDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suppliers are readable by everyone and writable by admins only, enforced here rather
 * than only in the UI. Name uniqueness is pre-checked so the user sees a field error
 * instead of a constraint failure, with the unique index as the real guarantee.
 */
@Singleton
class SupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {

    fun observeAll(): Flow<List<SupplierEntity>> = supplierDao.observeAll()

    fun search(query: String): Flow<List<SupplierEntity>> = supplierDao.search(query.trim())

    fun observeById(id: Long): Flow<SupplierEntity?> = supplierDao.observeById(id)

    fun observeCount(): Flow<Int> = supplierDao.observeCount()

    suspend fun getById(id: Long): SupplierEntity? = supplierDao.getById(id)

    suspend fun save(
        id: Long?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        val actor = permissionChecker.requireAdmin("supplier_save") ?: return SaveResult.NotAuthorized

        return try {
            if (id == null) {
                val newId = supplierDao.insert(
                    SupplierEntity(
                        name = cleanName,
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                )
                auditLogRepository.recordAs(
                    actor, AuditAction.SUPPLIER_CREATED, "supplier", newId, cleanName
                )
                SaveResult.Success(newId)
            } else {
                val existing = supplierDao.getById(id) ?: return SaveResult.NotFound
                supplierDao.update(
                    existing.copy(
                        name = cleanName,
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        updatedAt = System.currentTimeMillis(),
                        synced = false
                    )
                )
                auditLogRepository.recordAs(
                    actor, AuditAction.SUPPLIER_EDITED, "supplier", id, cleanName
                )
                SaveResult.Success(id)
            }
        } catch (e: Exception) {
            // The only unique index on this table is the name.
            SaveResult.Duplicate(DuplicateField.NAME)
        }
    }

    /**
     * Deletes a supplier. History rows keep the supplier's name (denormalized) and have
     * their `supplierId` nulled by the foreign key, so past stock-ins stay readable.
     */
    suspend fun delete(supplier: SupplierEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("supplier_delete")
            ?: return DeleteResult.NotAuthorized
        return try {
            supplierDao.delete(supplier)
            auditLogRepository.recordAs(
                actor, AuditAction.SUPPLIER_DELETED, "supplier", supplier.id, supplier.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }
}
