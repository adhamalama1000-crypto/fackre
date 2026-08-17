package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    /** Returns true on success, false if a category with the same name exists. */
    suspend fun addCategory(name: String): Boolean = try {
        categoryDao.insert(CategoryEntity(name = name.trim()))
        true
    } catch (e: Exception) {
        false
    }

    suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.update(category.copy(updatedAt = System.currentTimeMillis(), synced = false))

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)
}
