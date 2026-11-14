package com.sankare.wifikill.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B0F14)
private val SurfaceDark = Color(0xFF131A22)
private val SurfaceVariantDark = Color(0xFF1D2733)
private val Accent = Color(0xFF35E0B0)
private val AccentSoft = Color(0xFF8FF5DA)
private val Danger = Color(0xFFFF6B6B)
private val Warning = Color(0xFFFFC857)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    primaryContainer = Color(0xFF124D3F),
    onPrimaryContainer = AccentSoft,
    secondary = Color(0xFF5AB0FF),
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFE6EDF3),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFA9B7C6),
    error = Danger,
    onError = Ink,
    tertiary = Warning,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00795F),
    onPrimary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF11181F),
    surface = Color.White,
    onSurface = Color(0xFF11181F),
    surfaceVariant = Color(0xFFE7EDF2),
    onSurfaceVariant = Color(0xFF44515E),
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography()

@Composable
fun WiFiKillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
