package com.mpx.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MPx Midnight Theme: Pure black background, deep navy surfaces, electric blue accent.
// Distinct MPx visual identity inspired by MX Player's utility-first, high-density layout.
private val MpxMidnightScheme = darkColorScheme(
    primary = MpxPrimary,
    onPrimary = Color.White,
    primaryContainer = MpxPrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = MpxAccentCyan,
    onSecondary = Color.Black,
    secondaryContainer = MpxSurfaceElevated,
    onSecondaryContainer = Color.White,
    tertiary = MpxAccentAmber,
    onTertiary = Color.Black,
    background = MpxBlack,
    onBackground = MpxTextPrimary,
    surface = MpxSurface,
    onSurface = MpxTextPrimary,
    surfaceVariant = MpxSurfaceVariant,
    onSurfaceVariant = MpxTextSecondary,
    surfaceContainerHighest = MpxSurfaceElevated,
    outline = MpxBorder,
    outlineVariant = MpxBorderSubtle,
    error = MpxDestructive,
    onError = Color.White,
)

@Composable
fun MpxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MpxMidnightScheme,
        typography = MpxTypography,
        content = content,
    )
}
