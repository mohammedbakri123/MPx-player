package com.mpx.player.playback

import com.mpx.player.domain.settings.AppSettings

/**
 * Shared subtitle-style resolution for issue #75 (subtitle depth).
 *
 * The [AppSettings] subtitle fields render through mpv's libass options
 * (sub-*) — mpv is the only engine (issue #150). This file holds the pure
 * mappings. Subtitle timing (issue #79) uses mpv's runtime-changeable
 * sub-delay (seconds, may be negative) and sub-speed (timing multiplier).
 * Clamp helpers keep persisted values in range.
 */

/** Clamped subtitle delay in ms (-10000..10000). */
fun subtitleDelayMsClamped(settings: AppSettings): Int =
    settings.subtitleDelayMs.coerceIn(-10000, 10000)

/** Clamped subtitle speed multiplier (0.5..2.0). */
fun subtitleSpeedClamped(settings: AppSettings): Float =
    settings.subtitleSpeed.coerceIn(0.5f, 2.0f)

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
fun presetTextColorMpv(style: String): String = when (style.lowercase()) {
    "yellow" -> "#FFFFFF00"
    "cyan" -> "#FF00FFFF"
    else -> "#FFFFFFFF"
}

fun subtitleScaleFactor(scale: String): Float = when (scale) {
    "75" -> 0.75f
    "125" -> 1.25f
    "150" -> 1.5f
    else -> 1.0f
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
    // Issue #440: pin subtitle size AND position to the video, not the
    // window. Without this, mpv sizes the font against the window
    // height: in landscape the window and the video are the same height
    // so it looks right, but in portrait the window is ~4x taller than
    // the letterboxed video strip and the text balloons to fill half
    // the screen, sitting at the bottom of the screen over the player
    // controls instead of just under the picture. MX Player sizes
    // subtitles to the video the same way.
    options["sub-scale-with-window"] = "no"
    when (settings.subtitleAssOverride.lowercase()) {
        "scale" -> options["sub-ass-override"] = "scale"
        "force" -> options["sub-ass-override"] = "force"
        "strip" -> options["sub-ass-override"] = "strip"
        else -> { /* "Default": leave mpv's own default untouched */ }
    }
    // Subtitle timing (issue #79): runtime-changeable in mpv, applied live
    // by the same PlayerScreen push path as the other sub-* options.
    options["sub-delay"] = (subtitleDelayMsClamped(settings) / 1000.0).toString()
    options["sub-speed"] = subtitleSpeedClamped(settings).toString()
    options["sub-visibility"] = "yes"
    return options
}
