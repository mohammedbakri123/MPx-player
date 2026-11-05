package com.mpx.player.ui.theme

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
    // Errors only. Never used for decoration, badges, or highlights.
    destructive = Color(0xFFEF4444),
    // Success / ready only (never an accent, never a badge).
    success = Color(0xFF10B981),
    // Hairlines only — no weird borders.
    border = Color(0xFF222222),
    borderSubtle = Color(0x3394A3B8),
)

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
    success = Color(0xFF059669),
    border = Color(0xFFE0E0E0),
    borderSubtle = Color(0x1F000000),
)

val LocalMpxColors = staticCompositionLocalOf { MpxDarkColors }
