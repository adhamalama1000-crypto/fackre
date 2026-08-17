package com.warehouse.inventory.data.local.entity

import androidx.room.Embedded

/**
 * Query result that pairs a product with its category name (via LEFT JOIN),
 * so the UI can display the category label without a second lookup.
 */
data class ProductWithCategory(
    @Embedded val product: ProductEntity,
    val categoryName: String?
)
