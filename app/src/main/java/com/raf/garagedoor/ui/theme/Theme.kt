package com.raf.garagedoor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1767C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FB),
    onPrimaryContainer = Color(0xFF07254A),
    secondary = Color(0xFF0E2A47),
    onSecondary = Color.White,
    background = Color(0xFFF3F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6ECF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB5F5),
    onPrimary = Color(0xFF07254A),
    primaryContainer = Color(0xFF184A85),
    onPrimaryContainer = Color(0xFFD6E6FB),
    secondary = Color(0xFF9FB7D3),
    onSecondary = Color(0xFF0E2A47),
    background = Color(0xFF0B1420),
    surface = Color(0xFF132030),
    surfaceVariant = Color(0xFF1C2B3D),
)

@Immutable
data class GaragePalette(
    val closed: Color,
    val open: Color,
    val moving: Color,
    val unknown: Color,
    val house: Color,
    val garageInterior: Color,
    val door: Color,
    val doorSeam: Color,
)

private val LightGaragePalette = GaragePalette(
    closed = Color(0xFF2E8B57),
    open = Color(0xFFC77700),
    moving = Color(0xFF1767C9),
    unknown = Color(0xFF6B7785),
    house = Color(0xFF0E2A47),
    garageInterior = Color(0xFF1B1F24),
    door = Color(0xFFE9EEF5),
    doorSeam = Color(0xFFA9B6C6),
)

private val DarkGaragePalette = GaragePalette(
    closed = Color(0xFF6FD39A),
    open = Color(0xFFFFC266),
    moving = Color(0xFF7DB5F5),
    unknown = Color(0xFF9AA6B4),
    house = Color(0xFF2A3D55),
    garageInterior = Color(0xFF05080C),
    door = Color(0xFFC3D0E0),
    doorSeam = Color(0xFF7D8DA3),
)

val LocalGaragePalette = staticCompositionLocalOf { LightGaragePalette }

@Composable
fun RafsGarageTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalGaragePalette provides if (isDark) DarkGaragePalette else LightGaragePalette) {
        MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors, content = content)
    }
}
