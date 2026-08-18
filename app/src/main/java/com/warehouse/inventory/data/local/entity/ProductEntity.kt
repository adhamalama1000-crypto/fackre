package com.warehouse.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        // Unique when set. SQLite treats NULLs as distinct, so any number of products
        // may have no SKU / no barcode, but a given code can only belong to one product.
        // The repository stores null (never "") for blanks so this holds.
        Index(value = ["sku"], unique = true),
        Index(value = ["barcode"], unique = true)
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    /**
     * Total stock across every warehouse: a maintained cache of SUM(product_stock.quantity).
     * Only `InventoryRepository` may change it, and only in the same transaction as the
     * matching `product_stock` row, so the two can never drift.
     */
    val quantity: Int,
    /** Shelf / bin location text. Free-form, scoped to the warehouse holding the stock. */
    val location: String = "",
    val notes: String = "",
    /** Local file path (content/file URI string) to the product photo. */
    val imagePath: String? = null,
    val lowStockThreshold: Int = 5,

    /** Stock-keeping unit. Null when unset; unique across products when set. */
    val sku: String? = null,
    /** Scannable barcode / QR payload. Null when unset; unique across products when set. */
    val barcode: String? = null,
    /**
     * Unit of measure quantities are counted in.
     *
     * The SQL default is declared explicitly because the v1 -> v2 migration has to add
     * this NOT NULL column to rows that already exist, which SQLite only allows with a
     * DEFAULT. Declaring it here too keeps the entity and the migrated table identical
     * for Room's post-migration schema validation.
     */
    @ColumnInfo(defaultValue = "PIECE")
    val unit: ProductUnit = ProductUnit.PIECE,
    /** Optional purchase cost per [unit], used for inventory valuation. */
    val purchaseCost: Double? = null,
    /** Optional selling price per [unit]. */
    val sellingPrice: Double? = null,

    val createdAt: Long = System.currentTimeMillis(),

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
