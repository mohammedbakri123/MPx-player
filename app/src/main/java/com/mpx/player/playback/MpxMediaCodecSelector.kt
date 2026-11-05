package com.mpx.player.playback

/**
 * Codec-name helper (issue #150: the Media3 MediaCodecSelector this file
 * used to wrap is gone with Media3 — mpv picks its own decoders through
 * the hwdec chain, so only the name-based software check remains).
 *
 * Used by the decoder pill / fallback note to tell a hardware decoder
 * name (e.g. "c2.qti.avc.decoder") from a software one
 * (e.g. "OMX.google.h264.decoder").
 */
object MpxMediaCodecSelector {

    fun isSoftwareCodecName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("c2.android.") ||
                lower.startsWith("omx.google.") ||
                lower.startsWith("omx.ffmpeg.") ||
                lower.startsWith("c2.goldfish.") ||
                lower.contains(".sw.") ||
                lower.contains("software") ||
                lower.endsWith(".sw")
    }

    /**
     * Short display label for a codec name (#188): maps mpv's video-codec
     * (FFmpeg's long name, e.g. "H.265 / HEVC (High Efficiency Video Coding)")
     * — or an already-short decoder name — to a short label ("H.265").
     * Returns null when the codec is blank or unrecognised, so callers
     * omit the label instead of printing "(unknown)". Same mapping the
     * decoder pill used to do by hand on the display string.
     */
    fun shortCodecLabel(codec: String): String? {
        val lower = codec.lowercase()
        return when {
            codec.isBlank() -> null
            "avc" in lower || "h264" in lower || "h.264" in lower -> "H.264"
            "hevc" in lower || "h265" in lower || "h.265" in lower -> "H.265"
            "vp9" in lower -> "VP9"
            "av1" in lower -> "AV1"
            "vp8" in lower -> "VP8"
            "mp4v" in lower || "mpeg4" in lower -> "MPEG-4"
            else -> null
        }
    }
}
