package com.warehouse.inventory.ui.navigation

sealed class Route(val path: String) {
    data object Login : Route("login")
    data object Dashboard : Route("dashboard")
    data object Inventory : Route("inventory")
    data object Products : Route("products")
    data object Categories : Route("categories")
    data object Users : Route("users")
    data object History : Route("history")
    data object StockOut : Route("stockout?productId={productId}") {
        fun create(productId: Long? = null) =
            if (productId != null) "stockout?productId=$productId" else "stockout?productId=-1"
    }
    data object ProductForm : Route("product_form?productId={productId}") {
        fun create(productId: Long? = null) =
            if (productId != null) "product_form?productId=$productId" else "product_form?productId=-1"
    }
}
