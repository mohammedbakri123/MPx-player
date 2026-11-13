package com.mpx.player.util

import android.media.MediaExtractor
import android.media.MediaFormat
import com.mpx.player.playback.MediaAudioTrackInfo
import com.mpx.player.playback.MediaInfo
import com.mpx.player.playback.MediaVideoTrackInfo
import com.mpx.player.playback.SubtitleTrackInfo
import java.io.File

/**
 * Reads [MediaInfo] for a local video file WITHOUT playing it, using
 * Android's built-in [MediaExtractor] (mpvEx-style file-list Media
 * Information — long-press -> Info icon -> info page before playback).
 *
 * This is the pre-playback counterpart of the player version (issue
 * #468), which reads the same model from mpv's track-list while the
 * file is playing. No new library or native dependency: MediaExtractor
 * is Android built-in, so the APK does not grow.
 *
 * Runs disk I/O — the caller must run it on a background thread.
 * Returns null on ANY failure (missing file, extractor throws, no
 * tracks) and never throws, so the page can show "No media loaded".
 */
object FileMediaInfoReader {

    /**
     * Build the [MediaInfo] for the file at [path], or null when it
     * cannot be read. [path] must be a plain filesystem path
     * (VideoFile.path); anything else returns null — never guessed.
     */
    fun readMediaInfo(path: String): MediaInfo? {
        if (path.isBlank() || !path.startsWith("/")) return null
        val file = File(path)
        if (!file.isFile) return null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            if (extractor.trackCount <= 0) return null

            var videoTrack: MediaVideoTrackInfo? = null
            val audioTracks = mutableListOf<MediaAudioTrackInfo>()
            val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
            var maxDurationMs: Long? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val durationUs = format.getLongOrNull(MediaFormat.KEY_DURATION)
                if (durationUs != null && durationUs > 0L) {
                    val durationMs = durationUs / 1000L
                    if (maxDurationMs == null || durationMs > maxDurationMs) {
                        maxDurationMs = durationMs
                    }
                }
                when {
                    mime.startsWith("video/") -> {
                        // First video track wins (the model holds one).
                        if (videoTrack == null) {
                            videoTrack = MediaVideoTrackInfo(
                                codec = codecFromMime(mime),
                                width = format.getIntOrNull(MediaFormat.KEY_WIDTH),
                                height = format.getIntOrNull(MediaFormat.KEY_HEIGHT),
                                fps = format.getFloatOrNull(MediaFormat.KEY_FRAME_RATE)
                                    ?.toDouble(),
                                bitrate = format.getIntOrNull(MediaFormat.KEY_BIT_RATE)
                                    ?.toLong(),
                            )
                        }
                    }
                    mime.startsWith("audio/") -> {
                        audioTracks.add(
                            MediaAudioTrackInfo(
                                id = i,
                                codec = codecFromMime(mime),
                                channels = format.getIntOrNull(
                                    MediaFormat.KEY_CHANNEL_COUNT,
                                ),
                                bitrate = format.getIntOrNull(MediaFormat.KEY_BIT_RATE)
                                    ?.toLong(),
                                sampleRate = format.getIntOrNull(
                                    MediaFormat.KEY_SAMPLE_RATE,
                                ),
                                language = format.getString(MediaFormat.KEY_LANGUAGE),
                                isSelected = false,
                            ),
                        )
                    }
                    mime.startsWith("text/") || mime == "application/x-subrip" -> {
                        val language = format.getString(MediaFormat.KEY_LANGUAGE)
                            .orEmpty()
                        subtitleTracks.add(
                            SubtitleTrackInfo(
                                groupIndex = i,
                                trackIndex = i,
                                label = "Subtitle",
                                language = language,
                                isSelected = false,
                                isExternal = false,
                            ),
                        )
                    }
                }
            }

            if (videoTrack == null && audioTracks.isEmpty() &&
                subtitleTracks.isEmpty()
            ) {
                return null
            }

            val extension = file.name.substringAfterLast('.', "")
                .lowercase().takeIf { it.isNotEmpty() }
            val size = file.length().takeIf { it > 0L }

            return MediaInfo(
                title = file.name,
                format = extension,
                fileSize = size,
                durationMs = maxDurationMs,
                videoTrack = videoTrack,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
            )
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    /**
     * Map a MediaExtractor mime (e.g. "video/avc", "audio/mp4a-latm")
     * to the short codec name the shared `formatCodec` understands
     * ("avc" -> H.264, "aac" -> AAC). Unknown mimes keep their subtype,
     * which `formatCodec` uppercases.
     */
    private fun codecFromMime(mime: String): String? {
        val subtype = mime.substringAfter('/', "").lowercase()
        if (subtype.isBlank()) return null
        return when (subtype) {
            "mp4a-latm" -> "aac"
            "mpeg" -> "mp3"
            "x-vnd.on2.vp8" -> "vp8"
            "x-vnd.on2.vp9" -> "vp9"
            "mp4v-es" -> "mp4v"
            else -> subtype
        }
    }

    private fun MediaFormat.getIntOrNull(key: String): Int? =
        if (containsKey(key)) {
            try {
                getInteger(key)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    private fun MediaFormat.getFloatOrNull(key: String): Float? =
        if (containsKey(key)) {
            try {
                getFloat(key)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) {
            try {
                getLong(key)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
}
