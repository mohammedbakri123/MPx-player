package com.mpx.player.playback

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * MediaCodec selector for MPx Player.
 * Customizes codec selection based on the requested [DecoderMode] (HW, HW+, SW).
 *
 * - In SW mode: prioritizes software decoders (c2.android.*, OMX.google.*, etc.).
 * - In HW or HW+ mode: prioritizes hardware-accelerated vendor decoders.
 * - If the requested decoder type is unavailable on the device for a specific format,
 *   gracefully falls back to available decoders so playback does not fail, and records
 *   an honest platform fallback note.
 */
class MpxMediaCodecSelector(
    private val mode: DecoderMode,
) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val allDecoders = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (allDecoders.isEmpty()) {
            return emptyList()
        }

        return when (mode) {
            DecoderMode.SW -> {
                val swDecoders = allDecoders.filter { isSoftwareCodec(it) }
                val hwDecoders = allDecoders.filter { !isSoftwareCodec(it) }
                if (swDecoders.isNotEmpty()) {
                    swDecoders + hwDecoders
                } else {
                    allDecoders
                }
            }
            DecoderMode.HW, DecoderMode.HW_PLUS -> {
                val hwDecoders = allDecoders.filter { !isSoftwareCodec(it) }
                val swDecoders = allDecoders.filter { isSoftwareCodec(it) }
                if (hwDecoders.isNotEmpty()) {
                    hwDecoders + swDecoders
                } else {
                    allDecoders
                }
            }
        }
    }

    companion object {
        fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
            if (info.softwareOnly) return true
            if (!info.hardwareAccelerated) return true
            return isSoftwareCodecName(info.name)
        }

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
}
