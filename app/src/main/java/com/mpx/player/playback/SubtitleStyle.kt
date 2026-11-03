package com.mpx.player.playback

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import com.mpx.player.domain.settings.AppSettings

/**
 * Shared subtitle-style resolution for issue #75 (subtitle depth).
 *
 * Both engines consume the same [AppSettings] subtitle fields, but render
 * them through different mechanisms: Media3 via [CaptionStyleCompat] on the
 * PlayerView subtitle view, mpv via libass options (sub-*). This file holds
 * the pure mappings so the two paths cannot drift apart.
 *
 * Notes on honest approximations (documented, not hidden):
 * - Media3's [CaptionStyleCompat] has a single edge type, so shadow wins
 *   over outline when both are requested; border-thickness granularity
 *   (Thin/Medium/Thick) only changes mpv's sub-border-size.
 * - Media3's SubtitleView has no alignment API, so alignment is approximated
 *   with a bottom padding fraction (Center ~0.45, Top ~0.85 of view height).
 * - ASS override is mpv-only; Media3 always applies embedded styles.
 */

/** Named subtitle colour -> ARGB int. Unknown names fall back to white. */
fun subtitleColorArgb(name: String): Int = when (name.lowercase()) {
    "white" -> Color.WHITE
    "yellow" -> Color.YELLOW
    "cyan" -> Color.CYAN
    "green" -> Color.GREEN
    "magenta" -> Color.MAGENTA
    "red" -> Color.RED
    "black" -> Color.BLACK
    "transparent" -> Color.TRANSPARENT
    "semiblack" -> 0x80000000.toInt()
    else -> Color.WHITE
}

/** Named subtitle colour -> mpv #AARRGGBB string. */
fun subtitleColorMpv(name: String): String = when (name.lowercase()) {
    "white" -> "#FFFFFFFF"
    "yellow" -> "#FFFFFF00"
    "cyan" -> "#FF00FFFF"
    "green" -> "#FF00FF00"
    "magenta" -> "#FFFF00FF"
    "red" -> "#FFFF0000"
    "black" -> "#FF000000"
    "transparent" -> "#00000000"
    "semiblack" -> "#80000000"
    else -> "#FFFFFFFF"
}

/** Text colour resolved from the legacy preset, for when textColor is "Preset". */
fun presetTextColorArgb(style: String): Int = when (style.lowercase()) {
    "yellow" -> Color.YELLOW
    "cyan" -> Color.CYAN
    else -> Color.WHITE // "White" and "Default" both render white in a custom style
}

fun presetTextColorMpv(style: String): String = when (style.lowercase()) {
    "yellow" -> "#FFFFFF00"
    "cyan" -> "#FF00FFFF"
    else -> "#FFFFFFFF"
}

fun subtitleBaseTextSizeSp(size: String): Float = when (size.lowercase()) {
    "small" -> 14f
    "large" -> 24f
    else -> 18f
}

fun subtitleScaleFactor(scale: String): Float = when (scale) {
    "75" -> 0.75f
    "125" -> 1.25f
    "150" -> 1.5f
    else -> 1.0f
}

fun subtitleTypeface(typefaceName: String, bold: Boolean): Typeface {
    val base = when (typefaceName.lowercase()) {
        "sans" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }
    return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** True when every depth option is at its default, so the legacy Default preset keeps exact old behaviour. */
fun subtitleUsesSystemDefaultStyle(settings: AppSettings): Boolean =
    settings.subtitleStyle.equals("Default", ignoreCase = true) &&
        settings.subtitleTextColor.equals("Preset", ignoreCase = true) &&
        settings.subtitleBackgroundColor.equals("Transparent", ignoreCase = true) &&
        settings.subtitleBorderColor.equals("Black", ignoreCase = true) &&
        settings.subtitleBorderThickness.equals("Medium", ignoreCase = true) &&
        settings.subtitleBold &&
        !settings.subtitleShadow &&
        settings.subtitleAlignment.equals("Bottom", ignoreCase = true) &&
        settings.subtitleBottomPadding.equals("Medium", ignoreCase = true) &&
        settings.subtitleScale == "100" &&
        settings.subtitleTypeface.equals("Default", ignoreCase = true)

data class ResolvedMedia3SubtitleStyle(
    val textSizeSp: Float,
    val foregroundColor: Int,
    val backgroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    val typeface: Typeface,
    val bottomPaddingFraction: Float,
    val useSystemDefaultStyle: Boolean,
)

fun resolveMedia3SubtitleStyle(settings: AppSettings): ResolvedMedia3SubtitleStyle {
    val foreground = if (settings.subtitleTextColor.equals("Preset", ignoreCase = true)) {
        presetTextColorArgb(settings.subtitleStyle)
    } else {
        subtitleColorArgb(settings.subtitleTextColor)
    }
    val borderTransparent = settings.subtitleBorderColor.equals("Transparent", ignoreCase = true) ||
        settings.subtitleBorderThickness.equals("None", ignoreCase = true)
    val edgeType = when {
        settings.subtitleShadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        borderTransparent -> CaptionStyleCompat.EDGE_TYPE_NONE
        else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    }
    val edgeColor = if (borderTransparent && !settings.subtitleShadow) {
        Color.TRANSPARENT
    } else if (settings.subtitleBorderColor.equals("Transparent", ignoreCase = true)) {
        Color.BLACK
    } else {
        subtitleColorArgb(settings.subtitleBorderColor)
    }
    val bottomPaddingFraction = when (settings.subtitleAlignment.lowercase()) {
        "center" -> 0.45f
        "top" -> 0.85f
        else -> when (settings.subtitleBottomPadding.lowercase()) {
            "small" -> 0.02f
            "large" -> 0.12f
            else -> 0.06f
        }
    }
    return ResolvedMedia3SubtitleStyle(
        textSizeSp = subtitleBaseTextSizeSp(settings.subtitleSize) * subtitleScaleFactor(settings.subtitleScale),
        foregroundColor = foreground,
        backgroundColor = subtitleColorArgb(settings.subtitleBackgroundColor),
        edgeType = edgeType,
        edgeColor = edgeColor,
        typeface = subtitleTypeface(settings.subtitleTypeface, settings.subtitleBold),
        bottomPaddingFraction = bottomPaddingFraction,
        useSystemDefaultStyle = subtitleUsesSystemDefaultStyle(settings),
    )
}

/**
 * mpv libass options for the given settings. Keys are mpv option names
 * (without the leading "--"), values are the strings to pass to
 * setOptionString. The ASS-override entry is omitted when "Default" so
 * mpv keeps its own default.
 */
fun subtitleMpvOptions(settings: AppSettings): Map<String, String> {
    val options = linkedMapOf<String, String>()
    options["sub-font"] = when (settings.subtitleTypeface.lowercase()) {
        "serif" -> "serif"
        "mono" -> "monospace"
        else -> "sans-serif" // "Default" and "Sans"
    }
    val baseMpvSize = when (settings.subtitleSize.lowercase()) {
        "small" -> 40f
        "large" -> 70f
        else -> 55f
    }
    options["sub-font-size"] = (baseMpvSize * subtitleScaleFactor(settings.subtitleScale))
        .toInt().toString()
    options["sub-color"] = if (settings.subtitleTextColor.equals("Preset", ignoreCase = true)) {
        presetTextColorMpv(settings.subtitleStyle)
    } else {
        subtitleColorMpv(settings.subtitleTextColor)
    }
    options["sub-back-color"] = subtitleColorMpv(settings.subtitleBackgroundColor)
    options["sub-border-color"] = subtitleColorMpv(settings.subtitleBorderColor)
    options["sub-border-size"] = when (settings.subtitleBorderThickness.lowercase()) {
        "none" -> "0"
        "thin" -> "1.5"
        "thick" -> "4.5"
        else -> "3"
    }
    options["sub-bold"] = if (settings.subtitleBold) "yes" else "no"
    options["sub-shadow-offset"] = if (settings.subtitleShadow) "2" else "0"
    if (settings.subtitleShadow) {
        options["sub-shadow-color"] = "#80000000"
    }
    options["sub-align-x"] = "center"
    options["sub-align-y"] = when (settings.subtitleAlignment.lowercase()) {
        "center" -> "center"
        "top" -> "top"
        else -> "bottom"
    }
    options["sub-margin-y"] = when (settings.subtitleBottomPadding.lowercase()) {
        "small" -> "25"
        "large" -> "100"
        else -> "50"
    }
    options["sub-scale"] = when (settings.subtitleScale) {
        "75" -> "0.75"
        "125" -> "1.25"
        "150" -> "1.5"
        else -> "1.0"
    }
    when (settings.subtitleAssOverride.lowercase()) {
        "scale" -> options["sub-ass-override"] = "scale"
        "force" -> options["sub-ass-override"] = "force"
        "strip" -> options["sub-ass-override"] = "strip"
        else -> { /* "Default": leave mpv's own default untouched */ }
    }
    return options
}
