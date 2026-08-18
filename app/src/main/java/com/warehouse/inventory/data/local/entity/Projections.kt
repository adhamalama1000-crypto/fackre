package com.warehouse.inventory.data.local.entity

/**
 * Read-only query results. These are not tables — each is the shape of one JOIN or
 * aggregate that the UI needs in a single round trip.
 */

/** A product's stock in one specific warehouse, with that warehouse's label. */
data class WarehouseStock(
    val warehouseId: Long,
    val warehouseName: String,
    val warehouseCode: String,
    val quantity: Int
)

/** Aggregate stock held by one warehouse, for the warehouses list and reports. */
data class WarehouseTotals(
    val warehouseId: Long,
    val warehouseName: String,
    val warehouseCode: String,
    val productCount: Int,
    val totalQuantity: Int
)

/** One row of the "top issued products" report. */
data class ProductMovementTotal(
    val productId: Long?,
    val productName: String,
    val unit: ProductUnit,
    val totalQuantity: Int,
    val transactionCount: Int
)

/** One bucket of the daily/monthly activity report. */
data class PeriodActivity(
    /** "yyyy-MM-dd" for daily, "yyyy-MM" for monthly — produced by SQLite strftime. */
    val period: String,
    val stockInQuantity: Int,
    val stockOutQuantity: Int,
    val adjustmentCount: Int,
    val transactionCount: Int
)
