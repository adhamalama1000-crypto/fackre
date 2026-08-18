package com.warehouse.inventory.ui.navigation

/**
 * Every navigation destination.
 *
 * Destinations that can be opened either blank or for a specific product take an optional
 * `productId` query argument. `-1` is the "none" sentinel rather than omitting the argument,
 * because `NavType.LongType` cannot be nullable — the `create()` helpers keep that detail in
 * one place and out of the call sites.
 */
sealed class Route(val path: String) {

    data object Login : Route("login")
    data object Dashboard : Route("dashboard")
    data object Inventory : Route("inventory")
    data object Products : Route("products")
    data object Categories : Route("categories")
    data object Users : Route("users")
    data object History : Route("history")
    data object Suppliers : Route("suppliers")
    data object Employees : Route("employees")
    data object Warehouses : Route("warehouses")
    data object AuditLog : Route("audit_log")
    data object Reports : Route("reports")
    data object Settings : Route("settings")

    /** Full-screen barcode scanner. Returns its result via [NavKeys.SCANNED_CODE]. */
    data object Scanner : Route("scanner")

    data object StockIn : Route("stock_in?productId={productId}") {
        fun create(productId: Long? = null) = "stock_in?productId=${productId ?: NONE}"
    }

    data object StockOut : Route("stock_out?productId={productId}") {
        fun create(productId: Long? = null) = "stock_out?productId=${productId ?: NONE}"
    }

    data object Adjustment : Route("adjustment?productId={productId}") {
        fun create(productId: Long? = null) = "adjustment?productId=${productId ?: NONE}"
    }

    data object Transfer : Route("transfer?productId={productId}") {
        fun create(productId: Long? = null) = "transfer?productId=${productId ?: NONE}"
    }

    data object ProductForm : Route("product_form?productId={productId}") {
        fun create(productId: Long? = null) = "product_form?productId=${productId ?: NONE}"
    }

    companion object {
        /** Sentinel for "no product preselected". */
        const val NONE = -1L

        const val ARG_PRODUCT_ID = "productId"
    }
}
