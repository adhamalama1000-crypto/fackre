package com.warehouse.inventory.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue80,
    onPrimaryContainer = Blue30,
    secondary = BlueGrey40,
    tertiary = Amber40,
    background = LightBackground,
    surface = LightSurface,
    error = WarningRed
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue30,
    primaryContainer = Blue40,
    onPrimaryContainer = Blue80,
    secondary = BlueGrey80,
    tertiary = Amber80,
    background = DarkBackground,
    surface = DarkSurface,
    error = Color(0xFFFFB4AB)
)

@Composable
fun WarehouseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You) available on Android 12+
    dynamicColor: Boolean = true,
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
        content = content
    )
}
