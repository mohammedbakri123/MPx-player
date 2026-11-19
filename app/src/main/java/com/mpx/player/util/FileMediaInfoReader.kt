package com.mpx.player.util

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.playback.MediaAudioTrackInfo
import com.mpx.player.playback.MediaInfo
import com.mpx.player.playback.MediaVideoTrackInfo
import com.mpx.player.playback.SubtitleTrackInfo
import java.io.File
import java.util.Locale

/**
 * Reads [MediaInfo] for a local media file WITHOUT playing it, using
 * Android's built-in [MediaExtractor] with [MediaMetadataRetriever]
 * and MediaStore metadata fallback (mpvEx-style file-list Media
 * Information — long-press -> Info icon -> info page before playback).
 *
 * Android 10 (API 29) note: with `targetSdk = 34` and no
 * `requestLegacyExternalStorage`, Android 10 enforces Scoped Storage
 * WITHOUT Android 11+'s FUSE file-path redirection, while MediaStore
 * still populates `DATA` with `/storage/emulated/0/...`. Direct `File`
 * / `MediaExtractor.setDataSource(path)` calls on raw paths fail with
 * `EACCES`. Callers should pass `video.contentUri.ifBlank { video.path }`,
 * and [readMediaInfo] also resolves raw `/` paths to their MediaStore
 * `content://` URI whenever direct filesystem access is denied.
 *
 * Runs disk I/O — the caller must run it on a background thread.
 * Returns null on ANY failure (missing/unreadable file, no media
 * tracks or metadata) and never throws, so the page can show
 * "No media loaded".
 */
object FileMediaInfoReader {

    private data class ContentMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val mimeType: String?,
    )

    private data class RetrieverMetadata(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val bitrate: Long?,
        val fps: Double?,
        val mimeType: String?,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
    )

    /**
     * Source string for [VideoFile] pre-playback Media Information:
     * prefers [VideoFile.contentUri] over [VideoFile.path] so Android 10
     * scoped storage (where `DATA` is non-blank `/storage/...` yet
     * direct filesystem access fails with `EACCES`) opens via the
     * ContentResolver instead of failing on the raw path.
     */
    fun preferredSourceFor(video: VideoFile): String =
        video.contentUri.ifBlank { video.path }

    /**
     * Build the [MediaInfo] for the media at [path], or null when it
     * cannot be read. [path] may be a `content://` URI, a `file://`
     * URI, or a plain `/` filesystem path.
     */
    fun readMediaInfo(context: Context, path: String): MediaInfo? {
        if (path.isBlank()) return null
        val normalizedPath = if (path.startsWith("file://")) {
            Uri.parse(path).path ?: return null
        } else {
            path
        }
        val isInitialContentUri = normalizedPath.startsWith("content://")
        if (!isInitialContentUri && !normalizedPath.startsWith("/")) return null

        var contentUri: Uri? = if (isInitialContentUri) Uri.parse(normalizedPath) else null
        var directFile: File? = null
        if (!isInitialContentUri) {
            val candidate = File(normalizedPath)
            if (candidate.isFile && candidate.canRead()) {
                directFile = candidate
            } else {
                // Android 10 scoped storage: raw MediaStore DATA paths
                // fail POSIX stat()/access() with EACCES because there
                // is no FUSE path bridge on API 29. Resolve the row's
                // content:// URI in MediaStore instead.
                contentUri = resolveRawPathToContentUri(context, normalizedPath)
                    ?: return null
            }
        }

        var contentMeta: ContentMetadata? = contentUri?.let { queryContentMetadata(context, it) }
        var displayName: String? = if (contentUri != null) {
            contentMeta?.displayName ?: fallbackNameFromPath(normalizedPath)
        } else {
            directFile?.name
        }
        var sizeBytes: Long? = if (contentUri != null) {
            contentMeta?.sizeBytes
        } else {
            directFile?.length()?.takeIf { it > 0L }
        }

        var videoTrack: MediaVideoTrackInfo? = null
        val audioTracks = mutableListOf<MediaAudioTrackInfo>()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
        var maxDurationMs: Long? = null

        val extractor = MediaExtractor()
        try {
            var extractorOpened = openExtractorSource(context, extractor, contentUri, directFile)
            if (!extractorOpened && contentUri == null && directFile != null) {
                // Even when File.canRead() returned true on VFS metadata,
                // open() inside MediaExtractor can still be denied by
                // scoped storage / SELinux on Android 10. Promote to
                // the MediaStore content:// URI and retry.
                val resolved = resolveRawPathToContentUri(context, directFile.absolutePath)
                if (resolved != null) {
                    contentUri = resolved
                    directFile = null
                    contentMeta = queryContentMetadata(context, resolved)
                    if (contentMeta?.displayName != null) displayName = contentMeta.displayName
                    if (contentMeta?.sizeBytes != null) sizeBytes = contentMeta.sizeBytes
                    extractorOpened = openExtractorSource(context, extractor, resolved, null)
                }
            }

            if (extractorOpened && extractor.trackCount > 0) {
                for (i in 0 until extractor.trackCount) {
                    val format = try {
                        extractor.getTrackFormat(i)
                    } catch (_: Exception) {
                        continue
                    }
                    val mime = try {
                        format.getString(MediaFormat.KEY_MIME)
                    } catch (_: Exception) {
                        null
                    } ?: continue
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
                                    fps = format.getFrameRateOrNull(),
                                    bitrate = format.getBitrateOrNull(),
                                )
                            }
                        }
                        mime.startsWith("audio/") -> {
                            val language = try {
                                format.getString(MediaFormat.KEY_LANGUAGE)
                            } catch (_: Exception) {
                                null
                            }
                            audioTracks.add(
                                MediaAudioTrackInfo(
                                    id = i,
                                    codec = codecFromMime(mime),
                                    channels = format.getIntOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                                    bitrate = format.getBitrateOrNull(),
                                    sampleRate = format.getIntOrNull(MediaFormat.KEY_SAMPLE_RATE),
                                    language = language,
                                    isSelected = false,
                                ),
                            )
                        }
                        isSubtitleMime(mime) -> {
                            val language = try {
                                format.getString(MediaFormat.KEY_LANGUAGE)
                            } catch (_: Exception) {
                                null
                            }.orEmpty()
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
            }
        } catch (_: Exception) {
            // Fall through to MediaMetadataRetriever enrichment/fallback.
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        val noTracksExtracted =
            videoTrack == null && audioTracks.isEmpty() && subtitleTracks.isEmpty()
        val needsEnrichment = noTracksExtracted ||
            maxDurationMs == null ||
            (videoTrack != null && (
                videoTrack.width == null ||
                    videoTrack.height == null ||
                    videoTrack.fps == null ||
                    videoTrack.bitrate == null
                )) ||
            displayName?.contains('.') != true

        val retrieverMeta = if (needsEnrichment) {
            readRetrieverMetadata(context, contentUri, directFile)
        } else {
            null
        }

        val finalDurationMs = maxDurationMs
            ?: retrieverMeta?.durationMs
            ?: contentMeta?.durationMs

        val mergedVideoTrack: MediaVideoTrackInfo? = when {
            videoTrack != null -> videoTrack.copy(
                width = videoTrack.width ?: retrieverMeta?.width ?: contentMeta?.width,
                height = videoTrack.height ?: retrieverMeta?.height ?: contentMeta?.height,
                fps = videoTrack.fps ?: retrieverMeta?.fps,
                bitrate = videoTrack.bitrate ?: retrieverMeta?.bitrate,
            )
            retrieverMeta?.hasVideo == true ||
                ((contentMeta?.width ?: 0) > 0 && (contentMeta?.height ?: 0) > 0 &&
                    isSourceReadable(context, contentUri, directFile)) -> {
                MediaVideoTrackInfo(
                    codec = null,
                    width = retrieverMeta?.width ?: contentMeta?.width,
                    height = retrieverMeta?.height ?: contentMeta?.height,
                    fps = retrieverMeta?.fps,
                    bitrate = retrieverMeta?.bitrate,
                )
            }
            else -> null
        }

        if (mergedVideoTrack == null &&
            audioTracks.isEmpty() &&
            subtitleTracks.isEmpty() &&
            retrieverMeta?.hasAudio != true
        ) {
            return null
        }

        val extension = extractExtension(
            displayName = displayName,
            fallbackMime = retrieverMeta?.mimeType ?: contentMeta?.mimeType,
        )

        return MediaInfo(
            title = displayName,
            format = extension,
            fileSize = sizeBytes,
            durationMs = finalDurationMs,
            videoTrack = mergedVideoTrack,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
        )
    }

    /**
     * Opens [extractor] against [contentUri] (via a read
     * ParcelFileDescriptor first so >4 GB files seek cleanly, falling
     * back to `setDataSource(Context, Uri, null)`) or [directFile].
     */
    private fun openExtractorSource(
        context: Context,
        extractor: MediaExtractor,
        contentUri: Uri?,
        directFile: File?,
    ): Boolean {
        if (contentUri != null) {
            try {
                val openedByFd = context.contentResolver
                    .openFileDescriptor(contentUri, "r")
                    ?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                        true
                    } ?: false
                if (openedByFd) return true
            } catch (_: Exception) {
                // Fall back to Context+Uri overload below.
            }
            return try {
                extractor.setDataSource(context, contentUri, null)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (directFile != null) {
            return try {
                extractor.setDataSource(directFile.absolutePath)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Reads container-level metadata via [MediaMetadataRetriever] (for
     * MKV Segment duration/bitrate that per-track MediaExtractor
     * formats omit, or containers MediaExtractor cannot demux). Uses
     * `release()` in `finally` rather than `.use {}` because
     * `MediaMetadataRetriever` only implements `AutoCloseable` from
     * API 29+ (`minSdk` is 24).
     */
    private fun readRetrieverMetadata(
        context: Context,
        contentUri: Uri?,
        directFile: File?,
    ): RetrieverMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            val opened = when {
                contentUri != null -> {
                    val openedByFd = try {
                        context.contentResolver
                            .openFileDescriptor(contentUri, "r")
                            ?.use { pfd ->
                                retriever.setDataSource(pfd.fileDescriptor)
                                true
                            } ?: false
                    } catch (_: Exception) {
                        false
                    }
                    if (openedByFd) {
                        true
                    } else {
                        try {
                            retriever.setDataSource(context, contentUri)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                directFile != null -> {
                    try {
                        retriever.setDataSource(directFile.absolutePath)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                else -> false
            }
            if (!opened) return null

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            val fps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }
            val mimeType = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.takeIf { it.isNotBlank() }
            val hasVideo = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true ||
                (width != null && height != null)
            val hasAudio = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true

            if (durationMs == null && width == null && height == null &&
                bitrate == null && !hasVideo && !hasAudio
            ) {
                null
            } else {
                RetrieverMetadata(
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    bitrate = bitrate,
                    fps = fps,
                    mimeType = mimeType,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun isSourceReadable(
        context: Context,
        contentUri: Uri?,
        directFile: File?,
    ): Boolean {
        if (contentUri != null) {
            return try {
                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
        return directFile != null && directFile.isFile && directFile.canRead()
    }

    /**
     * Queries `DISPLAY_NAME` and `SIZE` via [OpenableColumns], plus
     * `DURATION` / `WIDTH` / `HEIGHT` / `MIME_TYPE` when [uri] is a
     * MediaStore URI.
     */
    private fun queryContentMetadata(
        context: Context,
        uri: Uri,
    ): ContentMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = try {
                            cursor.getLong(sizeIndex).takeIf { it > 0L }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Leave OpenableColumns null and try MediaStore columns if applicable.
        }

        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var mimeType: String? = null
        if (uri.authority == MediaStore.AUTHORITY) {
            val projection = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.DURATION)
                }
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.MediaColumns.MIME_TYPE)
            }.toTypedArray()
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val durIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                        if (durIdx >= 0 && !cursor.isNull(durIdx)) {
                            durationMs = try {
                                cursor.getLong(durIdx).takeIf { it > 0L }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val wIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                        if (wIdx >= 0 && !cursor.isNull(wIdx)) {
                            width = try {
                                cursor.getInt(wIdx).takeIf { it > 0 }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val hIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                        if (hIdx >= 0 && !cursor.isNull(hIdx)) {
                            height = try {
                                cursor.getInt(hIdx).takeIf { it > 0 }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) {
                            mimeType = cursor.getString(mimeIdx)?.takeIf { it.isNotBlank() }
                        }
                    }
                }
            } catch (_: Exception) {
                // Optional enrichment only.
            }
        }

        return ContentMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
        )
    }

    /**
     * Resolves a raw MediaStore `DATA` path (`/storage/emulated/0/...`)
     * to its `content://` URI across all external video and audio
     * volumes (same lookup as [com.mpx.player.playback.MpvFdOpener]).
     */
    private fun resolveRawPathToContentUri(
        context: Context,
        rawPath: String,
    ): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val args = arrayOf(rawPath)
        for (base in mediaCollectionUris(context)) {
            try {
                context.contentResolver.query(base, projection, selection, args, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        if (idCol >= 0) {
                            return ContentUris.withAppendedId(base, cursor.getLong(idCol))
                        }
                    }
                }
            } catch (_: Exception) {
                // Volume unavailable — try next.
            }
        }
        return null
    }

    private fun mediaCollectionUris(context: Context): List<Uri> {
        val uris = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                for (name in MediaStore.getExternalVolumeNames(context)) {
                    try {
                        uris.add(MediaStore.Video.Media.getContentUri(name))
                    } catch (_: Exception) {
                    }
                    try {
                        uris.add(MediaStore.Audio.Media.getContentUri(name))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                // Fall back to primary external URIs below.
            }
        }
        if (uris.isEmpty()) {
            uris.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            uris.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
        return uris
    }

    private fun fallbackNameFromPath(path: String): String? =
        if (path.startsWith("/")) {
            File(path).name.takeIf { it.isNotBlank() }
        } else {
            null
        }

    /**
     * Map a MediaExtractor mime (e.g. "video/avc", "audio/mp4a-latm")
     * to the short codec name the shared `formatCodec` understands
     * ("avc" -> H.264, "aac" -> AAC). Unknown mimes keep their subtype,
     * which `formatCodec` uppercases.
     */
    internal fun codecFromMime(mime: String): String? {
        val subtype = mime.substringAfter('/', "").lowercase(Locale.US)
        if (subtype.isBlank()) return null
        return when (subtype) {
            "mp4a-latm" -> "aac"
            "mpeg" -> "mp3"
            "eac3-joc" -> "eac3"
            "vnd.dts" -> "dts"
            "vnd.dts.hd" -> "dtshd"
            "true-hd" -> "truehd"
            "x-vnd.on2.vp8" -> "vp8"
            "x-vnd.on2.vp9" -> "vp9"
            "mp4v-es" -> "mp4v"
            else -> subtype
        }
    }

    internal fun isSubtitleMime(mime: String): Boolean {
        val lower = mime.lowercase(Locale.US)
        return lower.startsWith("text/") ||
            lower == "application/x-subrip" ||
            lower == "application/x-ssa" ||
            lower == "application/x-ass" ||
            lower == "application/vobsub" ||
            lower == "application/pgs" ||
            lower == "application/ttml+xml" ||
            lower == "application/x-mp4-vtt" ||
            lower == "application/x-mp4-cea-608"
    }

    internal fun extractExtension(displayName: String?, fallbackMime: String? = null): String? {
        val fromName = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
        return fromName ?: formatFromContainerMime(fallbackMime)
    }

    internal fun formatFromContainerMime(mime: String?): String? {
        val subtype = mime?.substringAfter('/', "")?.lowercase(Locale.US)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        return when (subtype) {
            "x-matroska", "matroska" -> "mkv"
            "mp4" -> "mp4"
            "webm" -> "webm"
            "x-msvideo", "avi" -> "avi"
            "mp2t", "mp2ts" -> "ts"
            "3gpp" -> "3gp"
            "quicktime" -> "mov"
            "x-flv" -> "flv"
            "x-ms-wmv" -> "wmv"
            "ogg" -> "ogg"
            "mpeg" -> "mpg"
            else -> null
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

    /**
     * `KEY_FRAME_RATE` in Android's [MediaFormat] is documented as
     * either `Integer` (e.g. 24, 25, 30) or `Float` (e.g. 23.976f);
     * calling `getFloat` on an integer entry throws `ClassCastException`.
     */
    private fun MediaFormat.getFrameRateOrNull(): Double? {
        if (!containsKey(MediaFormat.KEY_FRAME_RATE)) return null
        try {
            val f = getFloat(MediaFormat.KEY_FRAME_RATE)
            if (f > 0f) return f.toDouble()
        } catch (_: Exception) {
        }
        return try {
            val i = getInteger(MediaFormat.KEY_FRAME_RATE)
            if (i > 0) i.toDouble() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun MediaFormat.getBitrateOrNull(): Long? =
        getIntOrNull(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }?.toLong()
            ?: getLongOrNull(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0L }

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
