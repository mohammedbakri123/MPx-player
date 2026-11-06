package com.mpx.player.download

import android.media.MediaCodecList
import android.os.Build
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * yt-dlp `-S` format sorter (issue #336), copied with attribution from Seal
 * (JunkFood02/Seal, GPL-3.0):
 * - `toVideoFormatSorter`, `toAudioFormatSorter`, `applyFormatSorter`,
 *   `toFormatSorter` — `app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt:489-561`
 * - `checkIfAv1HardwareAccelerated` — same file, :987
 * - `connectWithDelimiter` — `app/src/main/java/com/junkfood/seal/util/TextUtil.kt:135`
 *
 * Seal reads these values from its DownloadPreferences (MMKV); MPx
 * reads them from AppSettings (issue #338 — downloadCodec /
 * downloadResolution / downloadCustomSorter) and MpxDownloader passes
 * them in at request time. The functions take the same values as plain
 * parameters defaulting to Seal's own defaults
 * (PreferenceUtil.kt:220-233): quality format (vp9.2, or av01 when the
 * device hardware-decodes AV1), no resolution cap, no custom audio
 * preset, no custom sorting fields. With those defaults the sorter is
 * just `vcodec:vp9.2` (or `vcodec:av01`).
 *
 * The sorter does not depend on ffmpeg being present: without ffmpeg it
 * still improves single-file picks (codec preference, resolution cap
 * when one is set); with ffmpeg bundled, yt-dlp merges the best
 * video+audio streams instead of capping at the best single file.
 *
 * Seal caches the AV1 check in its prefs; MPx recomputes it per
 * download — one MediaCodecList scan is negligible next to a yt-dlp run.
 */

/** Video format modes, mirroring Seal's PreferenceUtil constants. */
internal const val FORMAT_COMPATIBILITY = 1
internal const val FORMAT_QUALITY = 2

/** Audio format codes, mirroring Seal's PreferenceUtil constants. */
internal const val AUDIO_FORMAT_OPUS = 1
internal const val AUDIO_FORMAT_M4A = 2

/** Audio quality codes, mirroring Seal's PreferenceUtil constants. */
internal const val AUDIO_QUALITY_HIGH = 1
internal const val AUDIO_QUALITY_MEDIUM = 2
internal const val AUDIO_QUALITY_LOW = 3

/** "No preference" — Seal's NOT_SPECIFIED (PreferenceUtil.kt:132). */
internal const val FORMAT_NOT_SPECIFIED = 0

internal fun connectWithDelimiter(vararg strings: String?, delimiter: String): String =
    strings
        .toList()
        .filter { !it.isNullOrBlank() }
        .joinToString(separator = delimiter) { it.toString() }

internal fun videoFormatSorter(
    videoFormat: Int = FORMAT_QUALITY,
    videoResolution: Int = FORMAT_NOT_SPECIFIED,
    supportAv1HardwareDecoding: Boolean = checkIfAv1HardwareAccelerated(),
): String {
    val format =
        when (videoFormat) {
            FORMAT_COMPATIBILITY -> "proto,vcodec:h264,ext"
            FORMAT_QUALITY ->
                if (supportAv1HardwareDecoding) {
                    "vcodec:av01"
                } else {
                    "vcodec:vp9.2"
                }

            else -> ""
        }
    val res =
        when (videoResolution) {
            1 -> "res:2160"
            2 -> "res:1440"
            3 -> "res:1080"
            4 -> "res:720"
            5 -> "res:480"
            6 -> "res:360"
            7 -> "+res"
            else -> ""
        }
    return if (videoFormat == FORMAT_COMPATIBILITY) {
        connectWithDelimiter(format, res, delimiter = ",")
    } else {
        connectWithDelimiter(res, format, delimiter = ",")
    }
}

internal fun audioFormatSorter(
    useCustomAudioPreset: Boolean = false,
    audioFormat: Int = FORMAT_NOT_SPECIFIED,
    audioQuality: Int = FORMAT_NOT_SPECIFIED,
): String {
    if (!useCustomAudioPreset) return ""
    val format =
        when (audioFormat) {
            AUDIO_FORMAT_M4A -> "acodec:aac"
            AUDIO_FORMAT_OPUS -> "acodec:opus"
            else -> ""
        }
    val quality =
        when (audioQuality) {
            AUDIO_QUALITY_HIGH -> "abr~192"
            AUDIO_QUALITY_MEDIUM -> "abr~128"
            AUDIO_QUALITY_LOW -> "abr~64"
            else -> ""
        }
    return connectWithDelimiter(format, quality, delimiter = ",")
}

internal fun formatSorter(
    videoFormat: Int = FORMAT_QUALITY,
    videoResolution: Int = FORMAT_NOT_SPECIFIED,
    supportAv1HardwareDecoding: Boolean = checkIfAv1HardwareAccelerated(),
    useCustomAudioPreset: Boolean = false,
    audioFormat: Int = FORMAT_NOT_SPECIFIED,
    audioQuality: Int = FORMAT_NOT_SPECIFIED,
): String =
    connectWithDelimiter(
        videoFormatSorter(videoFormat, videoResolution, supportAv1HardwareDecoding),
        audioFormatSorter(useCustomAudioPreset, audioFormat, audioQuality),
        delimiter = ",",
    )

internal fun YoutubeDLRequest.applyFormatSorter(
    sorter: String,
    formatSorting: Boolean = false,
    sortingFields: String = "",
) {
    if (formatSorting && sortingFields.isNotEmpty()) addOption("-S", sortingFields)
    else if (sorter.isNotEmpty()) addOption("-S", sorter)
}

internal fun checkIfAv1HardwareAccelerated(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        info.supportedTypes.any { it.equals("video/av01", ignoreCase = true) } &&
            info.isHardwareAccelerated
    }
}
