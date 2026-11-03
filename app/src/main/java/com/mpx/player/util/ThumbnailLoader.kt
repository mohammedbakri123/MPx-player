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
        val key = "video_$videoId"
        getCached(key)?.let { return it }

        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uris = getVideoContentUris(context)
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

        if (bitmap == null && filePath.isNotBlank() && File(filePath).exists()) {
            try {
                @Suppress("DEPRECATION")
                bitmap = ThumbnailUtils.createVideoThumbnail(
                    filePath,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                )
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
        val key = "folder_${folderId.ifBlank { folderPath }}"
        getCached(key)?.let { return it }

        var representativeVideoId: Long? = null
        var representativeFilePath: String = ""

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
        )

        val uris = getVideoContentUris(context)
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
        val key = "audio_track_${trackId.ifBlank { trackPath }}"
        getCached(key)?.let { return it }

        var bitmap: Bitmap? = null

        // 1. Android Q+ ContentResolver.loadThumbnail for Audio
        val numericId = trackId.toLongOrNull()
        if (numericId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = getAudioContentUris(context)
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
                mmr.setDataSource(trackPath)
                val pictureBytes = mmr.embeddedPicture
                mmr.release()

                if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, opts)
                    opts.inSampleSize = calculateInSampleSize(opts, width, height)
                    opts.inJustDecodeBounds = false
                    bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, opts)
                }
            } catch (_: Exception) {}
        }

        // 3. Legacy albumart ContentProvider URI
        if (bitmap == null && numericId != null) {
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                    "${MediaStore.Audio.Media._ID} = ?",
                    arrayOf(trackId),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                        if (albumIdCol >= 0) {
                            val albumId = cursor.getLong(albumIdCol)
                            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
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
     */
    fun loadAlbumThumbnail(
        context: Context,
        albumTitle: String,
        width: Int = 200,
        height: Int = 200,
    ): Bitmap? {
        val key = "album_$albumTitle"
        getCached(key)?.let { return it }

        var trackId: String? = null
        var trackPath: String = ""

        val uris = getAudioContentUris(context)
        for (baseUri in uris) {
            try {
                context.contentResolver.query(
                    baseUri,
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
                    "${MediaStore.Audio.Media.ALBUM} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    arrayOf(albumTitle),
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
