package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Append-only record of a security- or data-relevant action.
 *
 * Intentionally carries no foreign keys: an audit trail that disappears when the user or
 * product it refers to is deleted is useless, so actor and target are stored as plain
 * denormalized text. Rows are only ever inserted, never updated or deleted by the app.
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["action"]),
        Index(value = ["userId"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val userId: Long?,
    val userName: String,
    val userEmail: String = "",

    val action: AuditAction,
    /** What kind of thing was affected, e.g. "product" / "user". Localized at display time. */
    val targetType: String = "",
    val targetId: Long? = null,
    val targetName: String = "",
    /** Human-readable specifics, already Arabic where user-facing. */
    val details: String = "",

    val timestamp: Long = System.currentTimeMillis(),

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
