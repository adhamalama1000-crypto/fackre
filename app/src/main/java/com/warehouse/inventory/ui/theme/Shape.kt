package com.warehouse.inventory.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Slightly softer corners than the Material defaults, applied through the theme so cards,
 * dialogs, menus and chips agree without every call site restating a radius.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Corner radius used by the app's own list and stat cards. */
val CardCorner = RoundedCornerShape(16.dp)
