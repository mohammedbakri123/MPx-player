package com.mpx.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// MPx Theme (#105 tokens, #139 System / Dark / Light, #147 accent palette).
//
// Stored values: "System" (default — follows the OS, like
// Instagram/Facebook), "Dark" (true AMOLED black), "Light" (white).
// Legacy stored values "Midnight"/"Classic" resolve to Dark; the
// repository normalizes them on read, and this function maps them too
// so a stale value can never strand the app on an unknown theme.
// The old 4-theme picker was removed in #112; #139 restores the choice
// as System / Dark / Light instead of the four legacy schemes.
// #147 adds an accent palette (Blue default, Emerald, Violet, Amber,
// Rose, Teal) that recolours only the accent over those surfaces.
// #374 replaces it with one theme color (TrueBlack default,
// Charcoal, Graphite, Midnight, Forest, Plum) shared by Dark and
// Light — the mode only picks which brightness of it renders; the
// player route (always Dark) follows its dark scale.
object MpxTheme {
    val colors: MpxColors
        @Composable get() = LocalMpxColors.current
}

// The Material schemes are built from the resolved palette colours
// (base theme + chosen accent), so they are functions, not static
// vals — the accent comes from the palette (#147) while the surfaces
// stay the base theme's (true black in Dark, white in Light).
private fun mpxDarkScheme(colors: MpxColors) = darkColorScheme(
    primary = colors.primary,
    onPrimary = Color.White,
    primaryContainer = colors.primaryVariant,
    onPrimaryContainer = Color.White,
    secondary = colors.primary,
    onSecondary = Color.White,
    secondaryContainer = colors.primaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = colors.primary,
    onTertiary = Color.White,
    background = colors.black,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    surfaceContainerHighest = colors.surfaceElevated,
    surfaceContainerHigh = colors.surfaceVariant,
    surfaceContainer = colors.surfaceRaised,
    surfaceContainerLow = colors.surface,
    surfaceContainerLowest = colors.black,
    surfaceBright = colors.surfaceElevated,
    surfaceDim = colors.black,
    surfaceTint = Color.Transparent,
    outline = colors.border,
    outlineVariant = colors.borderSubtle,
    error = colors.destructive,
    onError = Color.White,
)

private fun mpxLightScheme(colors: MpxColors) = lightColorScheme(
    primary = colors.primary,
    onPrimary = Color.White,
    primaryContainer = colors.primaryVariant,
    onPrimaryContainer = Color.White,
    secondary = colors.primary,
    onSecondary = Color.White,
    secondaryContainer = colors.primaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = colors.primary,
    onTertiary = Color.White,
    background = colors.black,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    surfaceContainerHighest = colors.surfaceElevated,
    surfaceContainerHigh = colors.surfaceVariant,
    surfaceContainer = colors.surfaceRaised,
    surfaceContainerLow = colors.surface,
    surfaceContainerLowest = colors.black,
    surfaceBright = colors.black,
    surfaceDim = colors.surfaceElevated,
    surfaceTint = Color.Transparent,
    outline = colors.border,
    outlineVariant = colors.borderSubtle,
    error = colors.destructive,
    onError = Color.White,
)

@Composable
fun MpxTheme(
    theme: String = "System",
    // Accent palette id (#147, #366): "Blue" (default, identity blue),
    // "Emerald", "Violet", "Amber", "Rose", "Teal", "Sky", "Orange",
    // "Pink", "Lime", "Indigo", "Cyan". Unknown ids fall
    // back to Blue. Only primary / primaryHover / primaryVariant
    // change; the base theme's surfaces and text are untouched.
    palette: String = "Blue",
    // One theme color id (#374): "TrueBlack" (default), "Charcoal",
    // "Graphite", "Midnight", "Forest", "Plum". Shared by Dark and
    // Light — the resolved mode only picks which brightness of it
    // renders (dark scale in Dark, light scale in Light). Unknown ids
    // fall back to True Black / White.
    themeColor: String = "TrueBlack",
    content: @Composable () -> Unit,
) {
    val useDark = when (theme) {
        "Light" -> false
        "Dark", "Midnight", "Classic" -> true
        else -> isSystemInDarkTheme()
    }
    val base = mpxThemeColorsFor(themeColor, useDark)
    val accent = mpxPaletteFor(palette)
    val colors = base.copy(
        primary = accent.primary,
        primaryHover = accent.primaryHover,
        primaryVariant = accent.primaryVariant,
    )
    CompositionLocalProvider(LocalMpxColors provides colors) {
        MaterialTheme(
            colorScheme = if (useDark) mpxDarkScheme(colors) else mpxLightScheme(colors),
            typography = MpxTypography,
            shapes = MpxShapes,
            content = content,
        )
    }
}
