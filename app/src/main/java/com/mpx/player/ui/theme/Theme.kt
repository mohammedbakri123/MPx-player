package com.mpx.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Locale

// MPx Midnight Theme: Pure OLED black background, deep navy surfaces, electric blue accent.
// Distinct MPx visual identity inspired by MX Player's utility-first, high-density layout.
val MpxMidnightScheme = darkColorScheme(
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

// MPx Dark Slate Theme: Deep charcoal background, modern slate surfaces, sky blue accent.
val MpxDarkSlateScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0284C7),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF818CF8),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color.White,
    tertiary = MpxAccentAmber,
    onTertiary = Color.Black,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerHighest = Color(0xFF475569),
    outline = Color(0xFF334155),
    outlineVariant = Color(0x3394A3B8),
    error = MpxDestructive,
    onError = Color.White,
)

// MPx Classic MX Navy: Deep navy blue-black background, steel blue surfaces, vibrant blue accent.
val MpxClassicNavyScheme = darkColorScheme(
    primary = Color(0xFF4895EF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4CC9F0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF273555),
    onSecondaryContainer = Color.White,
    tertiary = MpxAccentAmber,
    onTertiary = Color.Black,
    background = Color(0xFF0A0F1D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF131D31),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1F2C47),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerHighest = Color(0xFF2B3D5E),
    outline = Color(0xFF2B3D5E),
    outlineVariant = Color(0x3394A3B8),
    error = MpxDestructive,
    onError = Color.White,
)

// MPx Light Cinema Theme: High-visibility daylight palette for well-lit environments.
val MpxLightScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = MpxDestructive,
    onError = Color.White,
)

@Composable
fun MpxTheme(
    appTheme: String = "Midnight",
    content: @Composable () -> Unit,
) {
    val colorScheme = when (appTheme.lowercase(Locale.US)) {
        "dark", "dark slate", "slate" -> MpxDarkSlateScheme
        "classic", "classic navy", "navy" -> MpxClassicNavyScheme
        "light", "light cinema" -> MpxLightScheme
        else -> MpxMidnightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MpxTypography,
        content = content,
    )
}
