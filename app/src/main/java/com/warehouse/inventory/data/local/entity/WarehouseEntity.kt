package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A physical stock location. Per-warehouse quantities live in [ProductStockEntity];
 * this table only describes the place.
 *
 * Exactly one warehouse is flagged [isDefault]. It is the one preselected in stock
 * forms and the one the v1 -> v2 migration moved all pre-existing stock into.
 */
@Entity(
    tableName = "warehouses",
    indices = [Index(value = ["code"], unique = true), Index(value = ["name"])]
)
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Short code, e.g. "MAIN" / "M1". Unique. */
    val code: String,
    val location: String = "",
    val notes: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
