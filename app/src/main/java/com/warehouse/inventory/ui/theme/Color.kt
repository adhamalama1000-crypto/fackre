package com.warehouse.inventory.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Rasid brand palette.
 *
 * A deep, desaturated navy carries the brand and an amber accent marks incoming stock and
 * warnings. Both were chosen for legibility in a warehouse rather than for vibrancy: high
 * contrast against white and near-black, and readable on a cheap phone screen under
 * fluorescent light or through a scratched screen protector.
 *
 * The full Material 3 role set is defined for light and dark rather than only a handful of
 * roles, so components that reach for `surfaceVariant`, `outline` or `*Container` get brand
 * colors instead of Material's purple defaults.
 */

// ---- Brand core ----
val BrandNavy = Color(0xFF0B4A8F)
val BrandNavyDeep = Color(0xFF002F5F)
val BrandNavyLight = Color(0xFFA6C8FF)
val BrandNavyContainer = Color(0xFFD6E4F7)

val BrandAmber = Color(0xFFF5A524)
val BrandAmberDark = Color(0xFFA9660A)
val BrandAmberLight = Color(0xFFFFB95C)
val BrandAmberContainer = Color(0xFFFFE3B8)

val Slate = Color(0xFF4A5B7A)
val SlateLight = Color(0xFFBFC7DC)
val SlateContainer = Color(0xFFDCE3F0)

// ---- Light scheme ----
val LightBackground = Color(0xFFF6F8FB)
val LightOnBackground = Color(0xFF1A1D22)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1D22)
val LightSurfaceVariant = Color(0xFFE6EAF1)
val LightOnSurfaceVariant = Color(0xFF454B54)
val LightOutline = Color(0xFF6E7681)
val LightOutlineVariant = Color(0xFFC6CCD6)

// ---- Dark scheme ----
val DarkBackground = Color(0xFF101418)
val DarkOnBackground = Color(0xFFE2E6EC)
val DarkSurface = Color(0xFF171C21)
val DarkOnSurface = Color(0xFFE2E6EC)
val DarkSurfaceVariant = Color(0xFF414750)
val DarkOnSurfaceVariant = Color(0xFFC1C7D1)
val DarkOutline = Color(0xFF8B929C)
val DarkOutlineVariant = Color(0xFF414750)

// ---- Errors ----
val ErrorLight = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/**
 * Semantic colors for the five transaction types. Deliberately outside the Material roles:
 * a movement's meaning (received / issued / corrected / moved) must stay recognisable at a
 * glance in the history list, and it should not shift when the theme does.
 *
 * Each has a light-theme and dark-theme variant so contrast holds in both; see
 * [com.warehouse.inventory.ui.theme.TransactionColors].
 */
val StockInGreen = Color(0xFF1E7A46)
val StockInGreenDark = Color(0xFF6FD59B)
val StockOutOrange = Color(0xFFB4531A)
val StockOutOrangeDark = Color(0xFFFFB68D)
val AdjustmentPurple = Color(0xFF6B4EA8)
val AdjustmentPurpleDark = Color(0xFFC9B6F5)
val TransferTeal = Color(0xFF0E6F79)
val TransferTealDark = Color(0xFF7DD8E3)

val SuccessGreen = Color(0xFF1E7A46)
val WarningRed = Color(0xFFB3261E)
