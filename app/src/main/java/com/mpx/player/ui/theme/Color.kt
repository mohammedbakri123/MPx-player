package com.mpx.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * M2.5 theme tokens (#105). The single source of truth for colour.
 *
 * UX LAW (user, 2026-11-04): true black everywhere, ONE accent only
 * (MPx identity blue, used sparingly for selection / progress / primary
 * action), no colours in the wrong place. No second or third accent hues:
 * secondary and tertiary roles in the scheme also resolve to the blue
 * family. Red exists ONLY for errors. Screens and components must use
 * these tokens (via the scheme) — never hardcoded colours.
 */

// True-black surface scale (AMOLED). Background is pure black; surfaces
// lift only slightly, and only where separation is genuinely needed.
val MpxBlack = Color(0xFF000000)
val MpxSurface = Color(0xFF000000)
val MpxSurfaceRaised = Color(0xFF0A0A0A)
val MpxSurfaceVariant = Color(0xFF111111)
val MpxSurfaceElevated = Color(0xFF1A1A1A)

// The one accent: MPx identity blue. Sparingly — selection, progress,
// primary action. Pressed/dark variants for states, not new hues.
val MpxPrimary = Color(0xFF3B82F6)
val MpxPrimaryHover = Color(0xFF2563EB)
val MpxPrimaryVariant = Color(0xFF1D4ED8)

// Text on black. Contrast vs #000000: white 21:1, secondary ~11.5:1,
// muted ~7.5:1 — all readable on true black.
val MpxTextPrimary = Color(0xFFFFFFFF)
val MpxTextSecondary = Color(0xFFCBD5E1)
val MpxTextMuted = Color(0xFF94A3B8)

// Errors only. Never used for decoration, badges, or highlights.
val MpxDestructive = Color(0xFFEF4444)

// Hairlines only — no weird borders. Outlines are functional (divider,
// focused control), never decorative frames.
val MpxBorder = Color(0xFF222222)
val MpxBorderSubtle = Color(0x3394A3B8)
