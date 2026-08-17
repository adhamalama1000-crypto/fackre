package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun addCategory(name: String): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        val actor = permissionChecker.requireAdmin("category_create")
            ?: return SaveResult.NotAuthorized
        return try {
            val id = categoryDao.insert(CategoryEntity(name = cleanName))
            auditLogRepository.recordAs(actor, AuditAction.CATEGORY_CREATED, "category", id, cleanName)
            SaveResult.Success(id)
        } catch (e: Exception) {
            SaveResult.Duplicate(DuplicateField.NAME)
        }
    }

    suspend fun renameCategory(category: CategoryEntity, name: String): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        val actor = permissionChecker.requireAdmin("category_edit")
            ?: return SaveResult.NotAuthorized
        return try {
            categoryDao.update(
                category.copy(
                    name = cleanName,
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
            )
            auditLogRepository.recordAs(
                actor, AuditAction.CATEGORY_EDITED, "category", category.id, cleanName,
                "الاسم السابق: ${category.name}"
            )
            SaveResult.Success(category.id)
        } catch (e: Exception) {
            SaveResult.Duplicate(DuplicateField.NAME)
        }
    }

    /** Products in a deleted category keep existing; their `categoryId` is nulled by the FK. */
    suspend fun deleteCategory(category: CategoryEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("category_delete")
            ?: return DeleteResult.NotAuthorized
        return try {
            categoryDao.delete(category)
            auditLogRepository.recordAs(
                actor, AuditAction.CATEGORY_DELETED, "category", category.id, category.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }
}
