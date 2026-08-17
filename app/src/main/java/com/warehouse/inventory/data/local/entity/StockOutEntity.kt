package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single stock-out (withdrawal) operation. Kept as an immutable history log.
 * productName is denormalized so history is preserved even if the product is later deleted/renamed.
 */
@Entity(
    tableName = "stock_out",
    indices = [Index(value = ["productId"]), Index(value = ["date"])]
)
data class StockOutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val employeeName: String,
    /** The date the withdrawal happened (epoch millis). */
    val date: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
