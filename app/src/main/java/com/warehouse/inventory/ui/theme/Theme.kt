package com.warehouse.inventory.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.warehouse.inventory.data.local.entity.TransactionType

private val LightColors = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = BrandNavyContainer,
    onPrimaryContainer = BrandNavyDeep,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = Color(0xFF1B2739),
    tertiary = BrandAmberDark,
    onTertiary = Color.White,
    tertiaryContainer = BrandAmberContainer,
    onTertiaryContainer = Color(0xFF4A2A00),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = BrandNavyLight,
    onPrimary = BrandNavyDeep,
    primaryContainer = BrandNavy,
    onPrimaryContainer = BrandNavyContainer,
    secondary = SlateLight,
    onSecondary = Color(0xFF293141),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = SlateContainer,
    tertiary = BrandAmberLight,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF7A4E00),
    onTertiaryContainer = BrandAmberContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

/**
 * @param dynamicColor opt-in only, and **off by default**.
 *
 * Material You would repaint the app from the user's wallpaper, which overrides the Rasid
 * navy/amber palette — the brand would look different on every device and the semantic
 * transaction colors would stop matching the rest of the UI. Branding consistency wins here;
 * the parameter stays so a deployment that prefers system theming can switch it on.
 */
@Composable
fun WarehouseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/**
 * Theme-aware colors for the transaction types, resolved against the current light/dark
 * scheme so the history list keeps adequate contrast either way.
 */
object TransactionColors {

    @Composable
    @ReadOnlyComposable
    fun forType(type: TransactionType): Color {
        val dark = isDarkScheme()
        return when (type) {
            TransactionType.STOCK_IN -> if (dark) StockInGreenDark else StockInGreen
            TransactionType.STOCK_OUT -> if (dark) StockOutOrangeDark else StockOutOrange
            TransactionType.ADJUSTMENT -> if (dark) AdjustmentPurpleDark else AdjustmentPurple
            TransactionType.TRANSFER_OUT,
            TransactionType.TRANSFER_IN -> if (dark) TransferTealDark else TransferTeal
        }
    }

    /**
     * Derived from the scheme's own luminance rather than `isSystemInDarkTheme()`, so an
     * explicitly-themed subtree (a preview, or a screen forced light) still resolves
     * correctly.
     */
    @Composable
    @ReadOnlyComposable
    private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
