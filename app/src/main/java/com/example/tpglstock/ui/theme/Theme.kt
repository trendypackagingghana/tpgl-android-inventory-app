package com.example.tpglstock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = BrandBlueDeep,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF2EC),
    onSecondaryContainer = Color(0xFF00423B),
    tertiary = Color(0xFF7A4DD8),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFEBEEF4),
    surfaceContainerHighest = Color(0xFFE4E8F0),
    outline = LightOutline,
    outlineVariant = Color(0xFFE3E7EF),
    error = CriticalLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9BEFF),
    onPrimary = Color(0xFF0A2472),
    primaryContainer = Color(0xFF223D99),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4F47),
    onSecondaryContainer = Color(0xFFCFF2EC),
    tertiary = Color(0xFFC6B0FF),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0A0C11),
    surfaceContainerLow = Color(0xFF12161E),
    surfaceContainer = Color(0xFF181D27),
    surfaceContainerHigh = Color(0xFF1F2530),
    surfaceContainerHighest = Color(0xFF262D3A),
    outline = DarkOutline,
    outlineVariant = Color(0xFF262C38),
    error = CriticalDark,
)

@Immutable
data class StockColors(
    val good: Color,
    val warn: Color,
    val critical: Color,
    val seriesIn: Color,
    val seriesOut: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightStockColors = StockColors(
    good = GoodLight, warn = WarnLight, critical = CriticalLight,
    seriesIn = SeriesInLight, seriesOut = SeriesOutLight,
    heroStart = BrandBlue, heroEnd = BrandBlueDeep,
)

private val DarkStockColors = StockColors(
    good = GoodDark, warn = WarnDark, critical = CriticalDark,
    seriesIn = SeriesInDark, seriesOut = SeriesOutDark,
    heroStart = Color(0xFF2446B0), heroEnd = Color(0xFF14245E),
)

val LocalStockColors = staticCompositionLocalOf { LightStockColors }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TPGLStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStockColors provides if (darkTheme) DarkStockColors else LightStockColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object StockTheme {
    val colors: StockColors @Composable get() = LocalStockColors.current
}
