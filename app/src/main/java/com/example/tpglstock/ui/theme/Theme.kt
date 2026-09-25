package com.example.tpglstock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private fun materialColors(c: TpglColors, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.ink,
    onPrimary = c.onInk,
    primaryContainer = c.track,
    onPrimaryContainer = c.ink,
    secondary = c.accent,
    onSecondary = c.onAccent,
    secondaryContainer = c.track,
    onSecondaryContainer = c.ink,
    tertiary = c.accent,
    background = c.paper,
    onBackground = c.ink,
    surface = c.surface,
    onSurface = c.ink,
    surfaceVariant = c.track,
    onSurfaceVariant = c.muted,
    surfaceContainerLowest = c.surface,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.paper,
    surfaceContainerHighest = c.track,
    outline = c.chipBorder,
    outlineVariant = c.border,
    error = c.out.fg,
    scrim = c.scrim,
)

val LocalTpglColors = staticCompositionLocalOf { LightTpglColors }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TPGLStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkTpglColors else LightTpglColors
    CompositionLocalProvider(LocalTpglColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors(colors, darkTheme),
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object StockTheme {
    val colors: TpglColors @Composable get() = LocalTpglColors.current
}
