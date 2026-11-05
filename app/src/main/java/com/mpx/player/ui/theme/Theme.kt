package com.mpx.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// MPx Theme (#105 tokens, #139 System / Dark / Light).
//
// Stored values: "System" (default — follows the OS, like
// Instagram/Facebook), "Dark" (true AMOLED black), "Light" (white).
// Legacy stored values "Midnight"/"Classic" resolve to Dark; the
// repository normalizes them on read, and this function maps them too
// so a stale value can never strand the app on an unknown theme.
// The old 4-theme picker was removed in #112; #139 restores the choice
// as System / Dark / Light instead of the four legacy schemes.
object MpxTheme {
    val colors: MpxColors
        @Composable get() = LocalMpxColors.current
}

private val MpxDarkScheme = darkColorScheme(
    primary = MpxDarkColors.primary,
    onPrimary = Color.White,
    primaryContainer = MpxDarkColors.primaryVariant,
    onPrimaryContainer = Color.White,
    secondary = MpxDarkColors.primary,
    onSecondary = Color.White,
    secondaryContainer = MpxDarkColors.primaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = MpxDarkColors.primary,
    onTertiary = Color.White,
    background = MpxDarkColors.black,
    onBackground = MpxDarkColors.textPrimary,
    surface = MpxDarkColors.surface,
    onSurface = MpxDarkColors.textPrimary,
    surfaceVariant = MpxDarkColors.surfaceVariant,
    onSurfaceVariant = MpxDarkColors.textSecondary,
    surfaceContainerHighest = MpxDarkColors.surfaceElevated,
    surfaceContainerHigh = MpxDarkColors.surfaceVariant,
    surfaceContainer = MpxDarkColors.surfaceRaised,
    surfaceContainerLow = MpxDarkColors.surface,
    surfaceContainerLowest = MpxDarkColors.black,
    surfaceBright = MpxDarkColors.surfaceElevated,
    surfaceDim = MpxDarkColors.black,
    surfaceTint = Color.Transparent,
    outline = MpxDarkColors.border,
    outlineVariant = MpxDarkColors.borderSubtle,
    error = MpxDarkColors.destructive,
    onError = Color.White,
)

private val MpxLightScheme = lightColorScheme(
    primary = MpxLightColors.primary,
    onPrimary = Color.White,
    primaryContainer = MpxLightColors.primaryVariant,
    onPrimaryContainer = Color.White,
    secondary = MpxLightColors.primary,
    onSecondary = Color.White,
    secondaryContainer = MpxLightColors.primaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = MpxLightColors.primary,
    onTertiary = Color.White,
    background = MpxLightColors.black,
    onBackground = MpxLightColors.textPrimary,
    surface = MpxLightColors.surface,
    onSurface = MpxLightColors.textPrimary,
    surfaceVariant = MpxLightColors.surfaceVariant,
    onSurfaceVariant = MpxLightColors.textSecondary,
    surfaceContainerHighest = MpxLightColors.surfaceElevated,
    surfaceContainerHigh = MpxLightColors.surfaceVariant,
    surfaceContainer = MpxLightColors.surfaceRaised,
    surfaceContainerLow = MpxLightColors.surface,
    surfaceContainerLowest = MpxLightColors.black,
    surfaceBright = MpxLightColors.black,
    surfaceDim = MpxLightColors.surfaceElevated,
    surfaceTint = Color.Transparent,
    outline = MpxLightColors.border,
    outlineVariant = MpxLightColors.borderSubtle,
    error = MpxLightColors.destructive,
    onError = Color.White,
)

@Composable
fun MpxTheme(
    theme: String = "System",
    content: @Composable () -> Unit,
) {
    val useDark = when (theme) {
        "Light" -> false
        "Dark", "Midnight", "Classic" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = if (useDark) MpxDarkColors else MpxLightColors
    CompositionLocalProvider(LocalMpxColors provides palette) {
        MaterialTheme(
            colorScheme = if (useDark) MpxDarkScheme else MpxLightScheme,
            typography = MpxTypography,
            shapes = MpxShapes,
            content = content,
        )
    }
}
