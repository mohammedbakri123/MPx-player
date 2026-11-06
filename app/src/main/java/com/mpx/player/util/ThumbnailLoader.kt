package com.mpx.player.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File

/**
 * High-performance, memory-bounded thumbnail loader and cache for MediaStore assets.
 * Fetches real video thumbnails and audio album art directly from MediaStore and
 * embedded audio ID3 metadata. Caches decoded bitmaps in an in-memory LruCache.
 * Never produces fake thumbnails — returns null on missing art to allow honest vector placeholders.
 */
object ThumbnailLoader {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Bound memory cache to 1/8th of JVM heap (16MB to 64MB typical)
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 64 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun getCached(key: String): Bitmap? = synchronized(memoryCache) {
        memoryCache.get(key)
    }

    fun putCached(key: String, bitmap: Bitmap) = synchronized(memoryCache) {
        memoryCache.put(key, bitmap)
    }

    fun clearCache() = synchronized(memoryCache) {
        memoryCache.evictAll()
    }

    // Issue #278: the MediaStore volume a file path or content URI belongs
    // to, or null when it cannot be determined. MediaStore row ids are only
    // unique per volume, so the same numeric id on internal storage and on
    // an SD card is two different files — every cache key and every
    // id-based lookup below must be scoped to the volume, or the first
    // volume's thumbnail wins for both.
    //
    // Two shapes arrive here: filesystem paths from the MediaStore DATA
    // column ("/storage/emulated/0/..." = primary, "/storage/<UUID>/..."
    // = the SD card whose directory name IS its MediaStore volume name)
    // and content URIs built by the repo ("content://media/<volume>/
    // video|audio/media/<id>"). The candidate is only trusted if the
    // system actually reports that volume — a stray segment (SAF tree,
    // SMB proxy, USB mount) falls back to the legacy bare-id behavior
    // instead of resolving against a volume that does not exist.
    private fun volumeNameForPathOrUri(context: Context, pathOrUri: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val candidate: String? = when {
            pathOrUri.startsWith("content://") -> {
                when (val segment = Uri.parse(pathOrUri).pathSegments.getOrNull(0)) {
                    null -> null
                    // The legacy "external" segment is the primary volume.
                    "external" -> MediaStore.VOLUME_EXTERNAL_PRIMARY
                    else -> segment
                }
            }
            pathOrUri.startsWith("/storage/emulated/") ||
                pathOrUri.startsWith("/storage/self/") ||
                pathOrUri.startsWith("/sdcard/") -> MediaStore.VOLUME_EXTERNAL_PRIMARY
            pathOrUri.startsWith("/storage/") -> {
                pathOrUri.removePrefix("/storage/").substringBefore("/").takeIf { it.isNotBlank() }
            }
            else -> null
        } ?: return null
        return try {
            if (MediaStore.getExternalVolumeNames(context).contains(candidate)) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    // Issue #278: the cache keys. The composables in AsyncMediaThumbnail
    // check the cache synchronously with the same key the loader stores
    // under, so these are the single source of truth — both sides must
    // agree, or the fast-path lookup misses. Path-based keys (no numeric
    // id) already disambiguate volumes because the path itself carries
    // the mount point; only numeric-id keys gain the volume segment.
    fun videoCacheKey(context: Context, videoId: Long, filePath: String): String {
        val volume = volumeNameForPathOrUri(context, filePath)
        return if (videoId > 0L) {
            if (volume != null) "video_${volume}_$videoId" else "video_$videoId"
        } else {
            "video_${filePath.ifBlank { "unknown" }}"
        }
    }

    fun audioTrackCacheKey(context: Context, trackId: String, trackPath: String): String {
        val numericId = trackId.toLongOrNull()
        val volume = volumeNameForPathOrUri(context, trackPath)
        return if (numericId != null && volume != null) {
            "audio_track_${volume}_$numericId"
        } else {
            "audio_track_${trackId.ifBlank { trackPath }}"
        }
    }

    fun folderCacheKey(context: Context, folderId: String, folderPath: String): String {
        val volume = volumeNameForPathOrUri(context, folderPath)
        return if (volume != null) {
            "folder_${volume}_${folderId.ifBlank { folderPath }}"
        } else {
            "folder_${folderId.ifBlank { folderPath }}"
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getVideoContentUris(context: Context): List<Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val volumeNames = MediaStore.getExternalVolumeNames(context)
                if (volumeNames.isNotEmpty()) {
                    val uris = mutableListOf<Uri>()
                    for (name in volumeNames) {
                        try {
                            uris.add(MediaStore.Video.Media.getContentUri(name))
                        } catch (_: Exception) {}
                    }
                    if (uris.isNotEmpty()) return uris
                }
            } catch (_: Exception) {}
        }
        return listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    private fun getAudioContentUris(context: Context): List<Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val volumeNames = MediaStore.getExternalVolumeNames(context)
                if (volumeNames.isNotEmpty()) {
                    val uris = mutableListOf<Uri>()
                    for (name in volumeNames) {
                        try {
                            uris.add(MediaStore.Audio.Media.getContentUri(name))
                        } catch (_: Exception) {}
                    }
                    if (uris.isNotEmpty()) return uris
                }
            } catch (_: Exception) {}
        }
        return listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * Loads a real video thumbnail using MediaStore ContentResolver or legacy ThumbnailUtils.
     */
    fun loadVideoThumbnail(
        context: Context,
        videoId: Long,
        filePath: String = "",
        width: Int = 160,
        height: Int = 160,
    ): Bitmap? {
        // Issue #278: the cache key is volume-scoped (see videoCacheKey),
        // so the same numeric id on two volumes no longer shares one entry.
        val key = videoCacheKey(context, videoId, filePath)
        getCached(key)?.let { return it }

        var bitmap: Bitmap? = null
        if (videoId > 0L) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Issue #278: resolve the id against its own volume's
                    // URI. The old loop broke on the first non-null bitmap,
                    // so an SD-card video whose row id also exists on
                    // internal storage got internal storage's thumbnail.
                    // Only loop all volumes when the volume is unknown
                    // (no usable path/URI was supplied).
                    val volume = volumeNameForPathOrUri(context, filePath)
                    val uris = if (volume != null) {
                        listOf(MediaStore.Video.Media.getContentUri(volume))
                    } else {
                        getVideoContentUris(context)
                    }
                    for (baseUri in uris) {
                        try {
                            val contentUri = ContentUris.withAppendedId(baseUri, videoId)
                            bitmap = context.contentResolver.loadThumbnail(
                                contentUri,
                                Size(width, height),
                                null,
                            )
                            if (bitmap != null) break
                        } catch (_: Exception) {}
                    }
                } else {
                    @Suppress("DEPRECATION")
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        videoId,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )
                }
            } catch (_: Exception) {
                // Silently fall through to file path extraction if possible
            }
        }

        if (bitmap == null && filePath.isNotBlank() && File(filePath).exists()) {
            try {
                @Suppress("DEPRECATION")
                bitmap = ThumbnailUtils.createVideoThumbnail(
                    filePath,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                )
            } catch (_: Exception) {}
        }

        if (bitmap == null && filePath.startsWith("asset:///")) {
            try {
                val assetPath = filePath.removePrefix("asset:///")
                context.assets.openFd(assetPath).use { afd ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
            } catch (_: Exception) {}
        }

        val finalBitmap = bitmap
        if (finalBitmap != null) {
            putCached(key, finalBitmap)
        }
        return finalBitmap
    }

    /**
     * Loads a representative real thumbnail for a folder by querying the newest video in that bucket.
     */
    fun loadFolderThumbnail(
        context: Context,
        folderId: String,
        folderPath: String,
        width: Int = 160,
        height: Int = 160,
    ): Bitmap? {
        // Issue #278: volume-scoped key (see folderCacheKey). The folder
        // id itself is a full-path hash so same-named folders on two
        // volumes already have distinct ids; the volume segment is
        // defense in depth, and the representative lookup below is what
        // actually mixed thumbnails up — it fed the bare video id into
        // loadVideoThumbnail, whose key had no volume.
        val key = folderCacheKey(context, folderId, folderPath)
        getCached(key)?.let { return it }

        var representativeVideoId: Long? = null
        var representativeFilePath: String = ""

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
        )

        // Issue #278: ask the folder's own volume only. The bucket id is
        // a full-path hash so it can only match there anyway; looping all
        // volumes and breaking on the first hit was the mix-up vector for
        // the LIKE fallback. Unknown volume keeps the old loop.
        val volume = volumeNameForPathOrUri(context, folderPath)
        val uris = if (volume != null) {
            listOf(MediaStore.Video.Media.getContentUri(volume))
        } else {
            getVideoContentUris(context)
        }
        for (uri in uris) {
            try {
                // Try bucket ID first
                context.contentResolver.query(
                    uri,
                    projection,
                    "${MediaStore.Video.Media.BUCKET_ID} = ?",
                    arrayOf(folderId),
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT 1",
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                        val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (idCol >= 0) representativeVideoId = cursor.getLong(idCol)
                        if (dataCol >= 0) representativeFilePath = cursor.getString(dataCol).orEmpty()
                    }
                }

                // Fallback by folder path pattern if bucket id did not match
                if (representativeVideoId == null && folderPath.isNotBlank()) {
                    context.contentResolver.query(
                        uri,
                        projection,
                        "${MediaStore.Video.Media.DATA} LIKE ?",
                        arrayOf("$folderPath/%"),
                        "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT 1",
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                            if (idCol >= 0) representativeVideoId = cursor.getLong(idCol)
                            if (dataCol >= 0) representativeFilePath = cursor.getString(dataCol).orEmpty()
                        }
                    }
                }
                if (representativeVideoId != null) break
            } catch (_: Exception) {
                // Continue to next volume
            }
        }

        val videoId = representativeVideoId ?: return null
        val bitmap = loadVideoThumbnail(context, videoId, representativeFilePath, width, height)
        val finalBitmap = bitmap
        if (finalBitmap != null) {
            putCached(key, finalBitmap)
        }
        return finalBitmap
    }

    /**
     * Loads real album art for an audio track from MediaStore or embedded ID3 picture metadata.
     */
    fun loadAudioTrackThumbnail(
        context: Context,
        trackId: String,
        trackPath: String,
        width: Int = 160,
        height: Int = 160,
    ): Bitmap? {
        // Issue #278: volume-scoped key (see audioTrackCacheKey) — the
        // same numeric track id on two volumes no longer shares one entry.
        val key = audioTrackCacheKey(context, trackId, trackPath)
        getCached(key)?.let { return it }

        var bitmap: Bitmap? = null

        // 1. Android Q+ ContentResolver.loadThumbnail for Audio
        val numericId = trackId.toLongOrNull()
        if (numericId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Issue #278: resolve the id against its own volume's URI.
            // The old loop broke on the first non-null bitmap, so an
            // SD-card track whose row id also exists on internal storage
            // got internal storage's artwork. Only loop all volumes when
            // the volume is unknown (no usable path was supplied).
            val volume = volumeNameForPathOrUri(context, trackPath)
            val uris = if (volume != null) {
                listOf(MediaStore.Audio.Media.getContentUri(volume))
            } else {
                getAudioContentUris(context)
            }
            for (baseUri in uris) {
                try {
                    val uri = ContentUris.withAppendedId(baseUri, numericId)
                    bitmap = context.contentResolver.loadThumbnail(uri, Size(width, height), null)
                    if (bitmap != null) break
                } catch (_: Exception) {}
            }
        }

        // 2. Embedded ID3 Picture in local audio file
        if (bitmap == null && trackPath.isNotBlank() && File(trackPath).exists()) {
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(trackPath)
                    val pictureBytes = mmr.embeddedPicture

                    if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, opts)
                        opts.inSampleSize = calculateInSampleSize(opts, width, height)
                        opts.inJustDecodeBounds = false
                        bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, opts)
                    }
                } finally {
                    mmr.release()
                }
            } catch (_: Exception) {}
        }

        // 3. Legacy albumart ContentProvider URI
        if (bitmap == null && numericId != null) {
            try {
                // Issue #278: the album id belongs to the track's own
                // volume — asking EXTERNAL_CONTENT_URI for an SD-card
                // track id returns the primary volume's album (or
                // nothing) when the id collides. The albumart URI is
                // volume-scoped the same way, except the primary volume
                // keeps its legacy "external" URI.
                val volume = volumeNameForPathOrUri(context, trackPath)
                val albumQueryUri = if (volume != null) {
                    MediaStore.Audio.Media.getContentUri(volume)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                context.contentResolver.query(
                    albumQueryUri,
                    arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                    "${MediaStore.Audio.Media._ID} = ?",
                    arrayOf(trackId),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                        if (albumIdCol >= 0) {
                            val albumId = cursor.getLong(albumIdCol)
                            val sArtworkUri = if (volume != null &&
                                volume != MediaStore.VOLUME_EXTERNAL_PRIMARY
                            ) {
                                Uri.parse("content://media/$volume/audio/albumart")
                            } else {
                                Uri.parse("content://media/external/audio/albumart")
                            }
                            val albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                            context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                                bitmap = BitmapFactory.decodeStream(stream)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val finalBitmap = bitmap
        if (finalBitmap != null) {
            putCached(key, finalBitmap)
        }
        return finalBitmap
    }

    /**
     * Loads real album art for an AudioAlbum by fetching the artwork of its first track.
     * #243: scoped to (album, artist) — same-named albums by different
     * artists must not share one cover. The artist filter is skipped
     * only when no artist is supplied (legacy callers).
     */
    fun loadAlbumThumbnail(
        context: Context,
        albumTitle: String,
        albumArtist: String = "",
        width: Int = 200,
        height: Int = 200,
    ): Bitmap? {
        val key = "album_$albumTitle\u0000$albumArtist"
        getCached(key)?.let { return it }

        var trackId: String? = null
        var trackPath: String = ""

        // #243: match the album's own artist as well as its title.
        val selection = if (albumArtist.isNotBlank()) {
            "${MediaStore.Audio.Media.ALBUM} = ? AND ${MediaStore.Audio.Media.ARTIST} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        } else {
            "${MediaStore.Audio.Media.ALBUM} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        val selectionArgs = if (albumArtist.isNotBlank()) {
            arrayOf(albumTitle, albumArtist)
        } else {
            arrayOf(albumTitle)
        }

        val uris = getAudioContentUris(context)
        for (baseUri in uris) {
            try {
                context.contentResolver.query(
                    baseUri,
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
                    selection,
                    selectionArgs,
                    "${MediaStore.Audio.Media.TITLE} ASC LIMIT 1",
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (idCol >= 0) trackId = cursor.getLong(idCol).toString()
                        if (dataCol >= 0) trackPath = cursor.getString(dataCol).orEmpty()
                    }
                }
                if (trackId != null) break
            } catch (_: Exception) {
                // Continue to next volume
            }
        }

        val id = trackId ?: return null
        val bitmap = loadAudioTrackThumbnail(context, id, trackPath, width, height)
        val finalBitmap = bitmap
        if (finalBitmap != null) {
            putCached(key, finalBitmap)
        }
        return finalBitmap
    }
}
