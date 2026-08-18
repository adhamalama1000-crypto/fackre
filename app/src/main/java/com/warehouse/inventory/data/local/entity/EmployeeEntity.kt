package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A warehouse employee who physically performs stock operations.
 *
 * Deliberately separate from [UserEntity]: a user is someone who signs in to this app,
 * an employee is someone stock is handed to or counted by. Most employees never get a
 * login, and a login is not proof of who moved the goods.
 */
@Entity(
    tableName = "employees",
    indices = [Index(value = ["code"], unique = true), Index(value = ["name"])]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Employee number / badge ID. Unique when present. */
    val code: String? = null,
    val phone: String = "",
    val department: String = "",
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
