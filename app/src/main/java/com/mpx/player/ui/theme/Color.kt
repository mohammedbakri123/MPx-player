package com.mpx.player.ui.theme
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * M2.5 theme tokens (#105), theme-aware since #139.
 *
 * UX LAW (user, 2026-11-04, CORRECTED 2026-11-05): the bar is
 * Instagram/Facebook level — clean, uncrowded, standard placement,
 * subtle motion — with ONE accent only (MPx identity blue, used
 * sparingly for selection / progress / primary action) and no colours
 * in the wrong place. The 2026-11-04 "true black everywhere, no theme
 * choice" reading was WRONG: Instagram and Facebook both ship a light
 * theme, so MPx offers System / Dark / Light (#139).
 *
 * Dark is the true-black AMOLED palette; Light is its white-based
 * mirror with the same blue accent. Screens and components must read
 * these through MpxTheme.colors — never hardcoded colours, and never
 * the old top-level token vals (removed in #139).
 */
@Immutable
data class MpxColors(
    val black: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val destructive: Color,
    val warning: Color = Color(0xFFFFB74D),
    val warningContainer: Color = Color(0x33FFB74D),
    val onWarningContainer: Color = Color(0xFFFFE082),
    val success: Color,
    val border: Color,
    val borderSubtle: Color,
)

// Dark: true-black surface scale (AMOLED). Background is pure black;
// surfaces lift only slightly, and only where separation is needed.
// Text contrast vs #000000: white 21:1, secondary ~11.5:1, muted ~7.5:1.
val MpxDarkColors = MpxColors(
    black = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceRaised = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF111111),
    surfaceElevated = Color(0xFF1A1A1A),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    // Hairlines only — no weird borders.
    border = Color(0xFF222222),
    borderSubtle = Color(0x3394A3B8),
)

// Charcoal (#182): #121212 surface scale — softer than true black,
// still dark enough for AMOLED. Background, surfaces and borders all
// shift together; text, accent and error/success stay the dark values.
val MpxCharcoalColors = MpxColors(
    black = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceRaised = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF1F1F1F),
    surfaceElevated = Color(0xFF2A2A2A),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    border = Color(0xFF2C2C2C),
    borderSubtle = Color(0x3394A3B8),
)

// Graphite (#182): #1E1E1E surface scale — the lightest dark option.
val MpxGraphiteColors = MpxColors(
    black = Color(0xFF1E1E1E),
    surface = Color(0xFF1E1E1E),
    surfaceRaised = Color(0xFF262626),
    surfaceVariant = Color(0xFF2C2C2C),
    surfaceElevated = Color(0xFF383838),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    border = Color(0xFF3A3A3A),
    borderSubtle = Color(0x3394A3B8),
)

// Midnight (#366): deep-navy dark surface — same scale shape as the
// grey presets, tinted blue. Text, accent and error/success stay the
// dark values.
val MpxMidnightColors = MpxColors(
    black = Color(0xFF0A0F1E),
    surface = Color(0xFF0A0F1E),
    surfaceRaised = Color(0xFF121A2E),
    surfaceVariant = Color(0xFF182642),
    surfaceElevated = Color(0xFF22304F),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    border = Color(0xFF2A3A5C),
    borderSubtle = Color(0x3394A3B8),
)

// Forest (#366): deep-green dark surface.
val MpxForestColors = MpxColors(
    black = Color(0xFF0C1510),
    surface = Color(0xFF0C1510),
    surfaceRaised = Color(0xFF14201A),
    surfaceVariant = Color(0xFF1A2B21),
    surfaceElevated = Color(0xFF24382C),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    border = Color(0xFF2C4636),
    borderSubtle = Color(0x3394A3B8),
)

// Plum (#366): deep-purple dark surface.
val MpxPlumColors = MpxColors(
    black = Color(0xFF16111F),
    surface = Color(0xFF16111F),
    surfaceRaised = Color(0xFF1E1829),
    surfaceVariant = Color(0xFF262036),
    surfaceElevated = Color(0xFF322A45),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0x33FFB74D),
    onWarningContainer = Color(0xFFFFE082),
    success = Color(0xFF10B981),
    border = Color(0xFF3A3250),
    borderSubtle = Color(0x3394A3B8),
)

// Forest light (#374): pale green-tinted light surface — the light
// scale of the Forest theme color.
val MpxForestLightColors = MpxColors(
    black = Color(0xFFF2F7F3),
    surface = Color(0xFFF2F7F3),
    surfaceRaised = Color(0xFFE8F0EA),
    surfaceVariant = Color(0xFFDCE8DF),
    surfaceElevated = Color(0xFFCBDACF),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    warningContainer = Color(0x26D97706),
    onWarningContainer = Color(0xFF92400E),
    success = Color(0xFF059669),
    border = Color(0xFFB9CDBF),
    borderSubtle = Color(0x1F000000),
)

// Plum light (#374): pale lavender light surface — the light scale
// of the Plum theme color.
val MpxPlumLightColors = MpxColors(
    black = Color(0xFFF7F4FA),
    surface = Color(0xFFF7F4FA),
    surfaceRaised = Color(0xFFEFE9F5),
    surfaceVariant = Color(0xFFE4DBEE),
    surfaceElevated = Color(0xFFD5C9E3),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    warningContainer = Color(0x26D97706),
    onWarningContainer = Color(0xFF92400E),
    success = Color(0xFF059669),
    border = Color(0xFFC3B5D6),
    borderSubtle = Color(0x1F000000),
)

// One theme color, two brightnesses (#374). Each theme color is a
// pair: a dark scale + a light scale of the same family. Dark mode
// renders the dark scale, Light mode the light scale — flipping the
// mode never changes the app's color identity. Unknown ids fall back
// to True Black in Dark and White in Light, so a stale stored value
// can never strand the theme.
fun mpxThemeColorsFor(themeColor: String, useDark: Boolean): MpxColors =
    if (useDark) when (themeColor) {
        "Charcoal" -> MpxCharcoalColors
        "Graphite" -> MpxGraphiteColors
        "Midnight" -> MpxMidnightColors
        "Forest" -> MpxForestColors
        "Plum" -> MpxPlumColors
        else -> MpxDarkColors
    } else when (themeColor) {
        "Charcoal" -> MpxWarmLightColors
        "Midnight" -> MpxCoolLightColors
        "Forest" -> MpxForestLightColors
        "Plum" -> MpxPlumLightColors
        else -> MpxLightColors
    }

val LocalMpxColors = staticCompositionLocalOf { MpxDarkColors }

// Light: white-based mirror of the dark scale, same blue accent.
// Text contrast vs #FFFFFF: primary ~16:1, secondary ~7.5:1, muted ~4.6:1.
val MpxLightColors = MpxColors(
    black = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF6F6F6),
    surfaceVariant = Color(0xFFEDEDED),
    surfaceElevated = Color(0xFFE2E2E2),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    warningContainer = Color(0x26D97706),
    onWarningContainer = Color(0xFF92400E),
    success = Color(0xFF059669),
    border = Color(0xFFE0E0E0),
    borderSubtle = Color(0x1F000000),
)

// Warm (#366): warm-paper light surface — same scale shape as White.
val MpxWarmLightColors = MpxColors(
    black = Color(0xFFFAF7F2),
    surface = Color(0xFFFAF7F2),
    surfaceRaised = Color(0xFFF3EFE8),
    surfaceVariant = Color(0xFFEAE4D9),
    surfaceElevated = Color(0xFFE0D8C9),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    warningContainer = Color(0x26D97706),
    onWarningContainer = Color(0xFF92400E),
    success = Color(0xFF059669),
    border = Color(0xFFD8D0C2),
    borderSubtle = Color(0x1F000000),
)

// Cool (#366): cool-mist light surface.
val MpxCoolLightColors = MpxColors(
    black = Color(0xFFF4F7FA),
    surface = Color(0xFFF4F7FA),
    surfaceRaised = Color(0xFFEAF0F5),
    surfaceVariant = Color(0xFFE0E8F0),
    surfaceElevated = Color(0xFFD3DEE9),
    primary = Color(0xFF3B82F6),
    primaryHover = Color(0xFF2563EB),
    primaryVariant = Color(0xFF1D4ED8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    warningContainer = Color(0x26D97706),
    onWarningContainer = Color(0xFF92400E),
    success = Color(0xFF059669),
    border = Color(0xFFC9D5E0),
    borderSubtle = Color(0x1F000000),
)

