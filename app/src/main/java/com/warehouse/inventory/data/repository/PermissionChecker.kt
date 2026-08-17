package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.AuditLogDao
import com.warehouse.inventory.data.local.dao.UserDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.session.SessionManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place every privileged repository operation asks "may the signed-in user do this?".
 *
 * Two deliberate properties:
 *
 * 1. The role is re-read from the `users` table by id, not taken from the cached
 *    [SessionManager] session. The session lives in a DataStore file that is writable on a
 *    rooted device, so trusting the role it carries would make "admin" a client-side claim.
 *    The database row is the authority.
 * 2. Enforcement sits in the repository, below the ViewModels. Hiding a button is a UX
 *    affordance, not a permission — a viewer who reaches a screen by any other route still
 *    gets [Nothing] back from these checks.
 *
 * Every rejection is recorded as [AuditAction.PERMISSION_DENIED] so attempts are visible.
 */
@Singleton
class PermissionChecker @Inject constructor(
    private val sessionManager: SessionManager,
    private val userDao: UserDao,
    private val auditLogDao: AuditLogDao
) {

    /** The signed-in user as stored in the database, or null if nobody is signed in. */
    suspend fun currentUser(): UserEntity? {
        val session = sessionManager.currentUser.first() ?: return null
        return userDao.getById(session.id)
    }

    suspend fun isAdmin(): Boolean = currentUser()?.role == UserRole.ADMIN

    /**
     * Returns the acting admin, or null if the caller may not perform [operation].
     * Denials are written to the audit log before returning.
     *
     * @param operation short stable identifier of the attempted action, e.g. "stock_in".
     */
    suspend fun requireAdmin(operation: String): UserEntity? {
        val user = currentUser()
        if (user?.role == UserRole.ADMIN) return user

        auditLogDao.insert(
            AuditLogEntity(
                userId = user?.id,
                userName = user?.name ?: "غير معروف",
                userEmail = user?.email.orEmpty(),
                action = AuditAction.PERMISSION_DENIED,
                targetType = "operation",
                targetName = operation,
                details = "محاولة تنفيذ عملية تتطلب صلاحية المدير"
            )
        )
        return null
    }
}
