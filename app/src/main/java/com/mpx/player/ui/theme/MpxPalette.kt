package com.mpx.player.ui.theme

import androidx.compose.ui.graphics.Color
import com.mpx.player.R

/**
 * Custom accent palettes (#147).
 *
 * A palette changes ONLY the accent colour — [MpxColors.primary],
 * [MpxColors.primaryHover] and [MpxColors.primaryVariant] — over the
 * existing Dark / Light surfaces. Dark stays true black (#000) and
 * Light stays white; the System / Dark / Light theme choice is
 * untouched. The default palette is the MPx identity blue, so
 * existing users see no change.
 *
 * Picker pattern modelled on mpvex's ThemePicker (Apache-2.0,
 * studied read-only): a horizontal row of mini UI preview cards,
 * each showing the palette's accent over the current theme's
 * surfaces, with the palette name below. Rebuilt here in MPx's own
 * code and tokens — no mpvex source, assets or branding.
 *
 * Each palette carries three shades of one hue (base / hover /
 * deeper variant) so buttons, pressed states and containers stay in
 * the same family. Accents are used sparingly, per the UX law.
 */
data class MpxPalette(
    /** Stable id persisted in [com.mpx.player.domain.settings.AppSettings.colorPalette]. */
    val id: String,
    val nameRes: Int,
    val primary: Color,
    val primaryHover: Color,
    val primaryVariant: Color,
)

val MpxPalettes = listOf(
    // MPx identity blue (the #105 token values) — the default.
    MpxPalette(
        id = "Blue",
        nameRes = R.string.palette_name_blue,
        primary = Color(0xFF3B82F6),
        primaryHover = Color(0xFF2563EB),
        primaryVariant = Color(0xFF1D4ED8),
    ),
    MpxPalette(
        id = "Emerald",
        nameRes = R.string.palette_name_emerald,
        primary = Color(0xFF10B981),
        primaryHover = Color(0xFF059669),
        primaryVariant = Color(0xFF047857),
    ),
    MpxPalette(
        id = "Violet",
        nameRes = R.string.palette_name_violet,
        primary = Color(0xFF8B5CF6),
        primaryHover = Color(0xFF7C3AED),
        primaryVariant = Color(0xFF6D28D9),
    ),
    MpxPalette(
        id = "Amber",
        nameRes = R.string.palette_name_amber,
        primary = Color(0xFFF59E0B),
        primaryHover = Color(0xFFD97706),
        primaryVariant = Color(0xFFB45309),
    ),
    MpxPalette(
        id = "Rose",
        nameRes = R.string.palette_name_rose,
        primary = Color(0xFFF43F5E),
        primaryHover = Color(0xFFE11D48),
        primaryVariant = Color(0xFFBE123C),
    ),
    MpxPalette(
        id = "Teal",
        nameRes = R.string.palette_name_teal,
        primary = Color(0xFF14B8A6),
        primaryHover = Color(0xFF0D9488),
        primaryVariant = Color(0xFF0F766E),
    ),
    // Issue #366: six more accents — same 3-shade shape, distinct hues.
    MpxPalette(
        id = "Sky",
        nameRes = R.string.palette_name_sky,
        primary = Color(0xFF0EA5E9),
        primaryHover = Color(0xFF0284C7),
        primaryVariant = Color(0xFF0369A1),
    ),
    MpxPalette(
        id = "Orange",
        nameRes = R.string.palette_name_orange,
        primary = Color(0xFFF97316),
        primaryHover = Color(0xFFEA580C),
        primaryVariant = Color(0xFFC2410C),
    ),
    MpxPalette(
        id = "Pink",
        nameRes = R.string.palette_name_pink,
        primary = Color(0xFFEC4899),
        primaryHover = Color(0xFFDB2777),
        primaryVariant = Color(0xFFBE185D),
    ),
    MpxPalette(
        id = "Lime",
        nameRes = R.string.palette_name_lime,
        primary = Color(0xFF84CC16),
        primaryHover = Color(0xFF65A30D),
        primaryVariant = Color(0xFF4D7C0F),
    ),
    MpxPalette(
        id = "Indigo",
        nameRes = R.string.palette_name_indigo,
        primary = Color(0xFF6366F1),
        primaryHover = Color(0xFF4F46E5),
        primaryVariant = Color(0xFF4338CA),
    ),
    MpxPalette(
        id = "Cyan",
        nameRes = R.string.palette_name_cyan,
        primary = Color(0xFF06B6D4),
        primaryHover = Color(0xFF0891B2),
        primaryVariant = Color(0xFF0E7490),
    ),
)

/** Unknown or blank stored ids fall back to the identity-blue default. */
fun mpxPaletteFor(id: String): MpxPalette =
    MpxPalettes.find { it.id == id } ?: MpxPalettes.first()
