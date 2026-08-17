package com.warehouse.inventory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One immutable line in the unified inventory history: a stock-in, a stock-out, an
 * adjustment, or one leg of a transfer. This table replaced the old `stock_out` table
 * in schema v2; the migration copied every existing stock-out row into it.
 *
 * Names of the product, warehouse, supplier and employee are denormalized on purpose so
 * the history stays readable after any of those records is renamed or deleted. The `*Id`
 * columns are nullable and `ON DELETE SET NULL` for the same reason: deleting a product
 * must never delete the record that it moved.
 *
 * Rows are never updated or deleted by the app.
 */
@Entity(
    tableName = "inventory_transactions",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["id"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["warehouseId"]),
        Index(value = ["supplierId"]),
        Index(value = ["employeeId"]),
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["transferGroupId"])
    ]
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,

    val productId: Long?,
    /** Denormalized so history survives product deletion. */
    val productName: String,
    val productSku: String? = null,
    val unit: ProductUnit = ProductUnit.PIECE,

    /** Magnitude entered by the user; always > 0. Signed movement is [difference]. */
    val quantity: Int,
    /**
     * Warehouse stock before/after this movement, and the signed change.
     * Null on rows migrated from the v1 `stock_out` table, where the app never
     * recorded them — the UI shows "—" rather than inventing a number.
     */
    val previousQuantity: Int?,
    val newQuantity: Int?,
    val difference: Int?,

    val warehouseId: Long?,
    val warehouseName: String = "",
    /** The other side of a transfer: destination on TRANSFER_OUT, source on TRANSFER_IN. */
    val counterpartWarehouseId: Long? = null,
    val counterpartWarehouseName: String = "",
    /** Shared by the two legs of one transfer so they can be paired in reports. */
    val transferGroupId: String? = null,

    val supplierId: Long? = null,
    val supplierName: String = "",
    val employeeId: Long? = null,
    val employeeName: String = "",

    /** Set on ADJUSTMENT rows only. */
    val reason: AdjustmentReason? = null,
    /** Set on STOCK_IN rows: supplier invoice / shipment number. */
    val invoiceNumber: String = "",
    val notes: String = "",

    /** Business date of the movement (epoch millis, user-selectable). */
    val date: Long,
    /** Wall-clock time the row was written. */
    val createdAt: Long = System.currentTimeMillis(),

    /** The signed-in app user who recorded it. */
    val userId: Long? = null,
    val userName: String = "",

    // ---- Sync-ready metadata ----
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
