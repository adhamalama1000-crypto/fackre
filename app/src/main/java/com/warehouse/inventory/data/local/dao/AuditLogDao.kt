package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-only audit trail. There is deliberately no update or delete method: the DAO
 * itself gives the app no way to rewrite history.
 *
 * As with [InventoryTransactionDao.filter], `actions` must be a non-empty list of
 * [com.warehouse.inventory.data.local.entity.AuditAction] names.
 */
@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE action IN (:actions)
          AND (:userId IS NULL OR userId = :userId)
          AND timestamp >= :from AND timestamp <= :to
          AND (
            :query = ''
            OR userName LIKE '%' || :query || '%'
            OR userEmail LIKE '%' || :query || '%'
            OR targetName LIKE '%' || :query || '%'
            OR details LIKE '%' || :query || '%'
          )
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun filter(
        actions: List<String>,
        userId: Long?,
        from: Long,
        to: Long,
        query: String
    ): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_logs")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditLogEntity): Long
}
