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
}
