package com.mpx.player.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
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
     * cannot be read. [path] is either a plain filesystem path
     * (VideoFile.path) or a content:// URI — the latter happens when
     * MediaStore DATA is blank under scoped storage and VideoFile.path
     * falls back to the content URI (Talha, 2026-11-15: the page showed
     * "No media loaded" for exactly those files). A plain path is
     * opened directly; a content URI is opened through the
     * [Context]'s ContentResolver, so MediaExtractor can read it.
     */
    fun readMediaInfo(context: Context, path: String): MediaInfo? {
        if (path.isBlank()) return null
        val isContentUri = path.startsWith("content://")
        if (!isContentUri && !path.startsWith("/")) return null

        val file = if (isContentUri) null else File(path)
        if (!isContentUri && (file == null || !file.isFile)) return null

        // General-section metadata: for a plain path it comes from
        // the File; for a content URI it comes from the ContentResolver
        // (DISPLAY_NAME / SIZE) — the File route cannot open it.
        val displayName: String?
        val sizeBytes: Long?
        if (isContentUri) {
            val (name, size) = queryDisplayNameAndSize(context, Uri.parse(path))
            displayName = name
            sizeBytes = size
        } else {
            displayName = file!!.name
            sizeBytes = file.length().takeIf { it > 0L }
        }

        val extractor = MediaExtractor()
        try {
            if (isContentUri) {
                extractor.setDataSource(context, Uri.parse(path), null)
            } else {
                extractor.setDataSource(path)
            }
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

            val extension = displayName?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }

            return MediaInfo(
                title = displayName,
                format = extension,
                fileSize = sizeBytes,
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
     * DISPLAY_NAME and SIZE for a content URI, via the ContentResolver.
     * Returns (null, null) when the query fails or the columns are
     * missing — the caller then shows "Unknown" for those rows, but the
     * track sections still render.
     */
    private fun queryDisplayNameAndSize(
        context: Context,
        uri: Uri,
    ): Pair<String?, Long?> {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null to null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) {
                    try {
                        cursor.getLong(sizeIndex).takeIf { it > 0L }
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                name to size
            } ?: (null to null)
        } catch (_: Exception) {
            null to null
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
