package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Authoritative per-warehouse stock level: how much of one product sits in one warehouse.
 *
 * `products.quantity` is kept as a maintained cache of the SUM of these rows so the
 * existing dashboard aggregates, low-stock queries and product search keep working
 * unchanged. Both are written inside the same Room transaction by
 * `InventoryRepository`, which is the only place stock is allowed to change.
 */
@Entity(
    tableName = "product_stock",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["id"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId", "warehouseId"], unique = true),
        Index(value = ["warehouseId"])
    ]
)
data class ProductStockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val warehouseId: Long,
    val quantity: Int,

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
