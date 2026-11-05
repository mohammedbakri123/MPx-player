package com.mpx.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MPx Theme (M2.5 canonical scheme, #105, single scheme since #112):
// TRUE AMOLED pure black (#000000) background and surfaces, identity
// blue as the ONLY accent, accessible high-contrast text, hairline
// outlines only. Built entirely from the tokens in Color.kt — no
// hardcoded colours. The legacy Dark Slate / Classic Navy / Light
// schemes and the theme picker were removed in #112; the stored
// appTheme value is inert data (settings persistence untouched).
val MpxMidnightScheme = darkColorScheme(
    primary = MpxPrimary,
    onPrimary = Color.White,
    primaryContainer = MpxPrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = MpxPrimary,
    onSecondary = Color.White,
    secondaryContainer = MpxPrimaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = MpxPrimary,
    onTertiary = Color.White,
    background = MpxBlack,
    onBackground = MpxTextPrimary,
    surface = MpxSurface,
    onSurface = MpxTextPrimary,
    surfaceVariant = MpxSurfaceVariant,
    onSurfaceVariant = MpxTextSecondary,
    surfaceContainerHighest = MpxSurfaceElevated,
    surfaceContainerHigh = MpxSurfaceVariant,
    surfaceContainer = MpxSurfaceRaised,
    surfaceContainerLow = MpxSurface,
    surfaceContainerLowest = MpxBlack,
    surfaceBright = MpxSurfaceElevated,
    surfaceDim = MpxBlack,
    surfaceTint = Color.Transparent,
    outline = MpxBorder,
    outlineVariant = MpxBorderSubtle,
    error = MpxDestructive,
    onError = Color.White,
)

@Composable
fun MpxTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MpxMidnightScheme,
        typography = MpxTypography,
        shapes = MpxShapes,
        content = content,
    )
}
