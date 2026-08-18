package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.AuditLogDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads the audit trail.
 *
 * Reading is admin-only and that is enforced here, not just by hiding the menu tile: a
 * viewer calling [observeFiltered] gets an empty stream. Writing is unrestricted on
 * purpose — a viewer's actions (and denied attempts) must still be recorded.
 */
@Singleton
class AuditLogRepository @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val permissionChecker: PermissionChecker
) {

    /** Admin-only. Emits an empty list for non-admins rather than leaking rows. */
    fun observeFiltered(filter: AuditFilter): Flow<List<AuditLogEntity>> = flow {
        if (!permissionChecker.isAdmin()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            auditLogDao.filter(
                actions = filter.actionNames,
                userId = filter.userId,
                from = filter.from,
                to = filter.to,
                query = filter.query.trim()
            )
        )
    }

    /** Records an action performed by the currently signed-in user. */
    suspend fun record(
        action: AuditAction,
        targetType: String = "",
        targetId: Long? = null,
        targetName: String = "",
        details: String = ""
    ) {
        recordAs(permissionChecker.currentUser(), action, targetType, targetId, targetName, details)
    }

    /**
     * Records an action for an already-resolved actor. Preferred inside repository
     * operations that have just called [PermissionChecker.requireAdmin], so the audit row
     * names the same user the permission check approved and no second lookup is needed.
     *
     * Failures are swallowed: an audit write must never roll back or mask the business
     * operation the user actually asked for.
     */
    suspend fun recordAs(
        user: UserEntity?,
        action: AuditAction,
        targetType: String = "",
        targetId: Long? = null,
        targetName: String = "",
        details: String = ""
    ) {
        runCatching {
            auditLogDao.insert(
                AuditLogEntity(
                    userId = user?.id,
                    userName = user?.name ?: "غير معروف",
                    userEmail = user?.email.orEmpty(),
                    action = action,
                    targetType = targetType,
                    targetId = targetId,
                    targetName = targetName,
                    details = details
                )
            )
        }
    }
}
