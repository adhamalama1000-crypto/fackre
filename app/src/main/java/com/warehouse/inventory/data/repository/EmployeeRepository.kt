package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.EmployeeDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warehouse employees — the people stock is handed to or counted by, as opposed to the
 * app's login accounts in [UserRepository].
 */
@Singleton
class EmployeeRepository @Inject constructor(
    private val employeeDao: EmployeeDao,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {

    fun observeAll(): Flow<List<EmployeeEntity>> = employeeDao.observeAll()

    /** Only employees still employed — what stock forms should offer. */
    fun observeActive(): Flow<List<EmployeeEntity>> = employeeDao.observeActive()

    fun search(query: String): Flow<List<EmployeeEntity>> = employeeDao.search(query.trim())

    fun observeCount(): Flow<Int> = employeeDao.observeCount()

    suspend fun getById(id: Long): EmployeeEntity? = employeeDao.getById(id)

    suspend fun save(
        id: Long?,
        name: String,
        code: String,
        phone: String,
        department: String,
        notes: String,
        active: Boolean = true
    ): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        // Stored as null rather than "" so the unique index does not treat every
        // code-less employee as a collision with every other one.
        val cleanCode = code.trim().ifBlank { null }

        val actor = permissionChecker.requireAdmin("employee_save") ?: return SaveResult.NotAuthorized

        if (cleanCode != null) {
            val clash = employeeDao.getByCode(cleanCode)
            if (clash != null && clash.id != id) return SaveResult.Duplicate(DuplicateField.CODE)
        }

        return try {
            if (id == null) {
                val newId = employeeDao.insert(
                    EmployeeEntity(
                        name = cleanName,
                        code = cleanCode,
                        phone = phone.trim(),
                        department = department.trim(),
                        notes = notes.trim(),
                        active = active
                    )
                )
                auditLogRepository.recordAs(
                    actor, AuditAction.EMPLOYEE_CREATED, "employee", newId, cleanName
                )
                SaveResult.Success(newId)
            } else {
                val existing = employeeDao.getById(id) ?: return SaveResult.NotFound
                employeeDao.update(
                    existing.copy(
                        name = cleanName,
                        code = cleanCode,
                        phone = phone.trim(),
                        department = department.trim(),
                        notes = notes.trim(),
                        active = active,
                        updatedAt = System.currentTimeMillis(),
                        synced = false
                    )
                )
                auditLogRepository.recordAs(
                    actor, AuditAction.EMPLOYEE_EDITED, "employee", id, cleanName
                )
                SaveResult.Success(id)
            }
        } catch (e: Exception) {
            SaveResult.Duplicate(DuplicateField.CODE)
        }
    }

    /**
     * Deletes an employee. Past transactions keep the recorded employee name and have
     * their `employeeId` nulled by the foreign key, so history is not rewritten.
     */
    suspend fun delete(employee: EmployeeEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("employee_delete")
            ?: return DeleteResult.NotAuthorized
        return try {
            employeeDao.delete(employee)
            auditLogRepository.recordAs(
                actor, AuditAction.EMPLOYEE_DELETED, "employee", employee.id, employee.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }
}
