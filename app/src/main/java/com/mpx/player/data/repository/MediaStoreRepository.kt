package com.mpx.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.repository.MediaRepository
import java.io.File
import java.util.Locale

/**
 * Real repository backed by the Android MediaStore content provider.
 * Scans video folders with actual video counts and aggregate byte sizes,
 * and audio tracks with accurate title, artist, album, and duration metadata.
 *
 * Fully supports multi-volume scanning across both primary internal storage
 * and removable external storage (SD cards, USB OTG) using volume-specific
 * MediaStore content URIs ([MediaStore.getExternalVolumeNames]).
 */
class MediaStoreRepository(
    private val context: Context,
) : MediaRepository {

    companion object {
        // Process-lifetime cache of the last scan, keyed by the
        // (show-all-folders, show-hidden-files) flags that shape the lists.
        // Plain remember state is wiped when a tab's back-stack entry is
        // popped (tab switch with saveState), so without this, returning to
        // Local rebuilt from empty lists and flashed an empty page for a
        // few ms before the rescan filled it. The rescan still runs on
        // return and refreshes both the cache and the screen; this only
        // covers the gap with the previous real content. It dies with the
        // process, so a fresh launch always scans.
        @Volatile
        private var cachedFolders: Triple<Boolean, Boolean, List<MediaFolder>>? = null

        @Volatile
        private var cachedVideos: Triple<Boolean, Boolean, List<VideoFile>>? = null

        fun cachedFolders(showAllFolders: Boolean, showHiddenFiles: Boolean): List<MediaFolder>? =
            cachedFolders?.takeIf { it.first == showAllFolders && it.second == showHiddenFiles }?.third

        fun cachedVideos(showAll: Boolean, showHiddenFiles: Boolean): List<VideoFile>? =
            cachedVideos?.takeIf { it.first == showAll && it.second == showHiddenFiles }?.third
    }

    /**
     * Resolves all active external storage volume names available on the device.
     * On Android 10+ (API 29+), includes [MediaStore.VOLUME_EXTERNAL_PRIMARY] and
     * unique volume UUIDs of mounted removable SD cards or USB OTG drives.
     * On Android 11+ (API 30+), also inspects [StorageManager.getStorageVolumes]
     * to guarantee all mounted volumes known to the system are covered.
     */
    fun getExternalVolumeNames(): Set<String> {
        val volumeNames = LinkedHashSet<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                volumeNames.addAll(MediaStore.getExternalVolumeNames(context))
            } catch (_: Exception) {
                // MediaStore provider may throw on early boot or restricted sandbox
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    volume.mediaStoreVolumeName?.let { volumeNames.add(it) }
                }
            } catch (_: Exception) {
                // Storage service access fallback
            }
        }
        return volumeNames
    }

    /**
     * Builds list of [Uri]s for querying video content across all external storage volumes.
     * Falls back to [MediaStore.Video.Media.EXTERNAL_CONTENT_URI] on API < 29 or when
     * volume discovery returns empty.
     */
    fun getVideoContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Video.Media.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * Builds list of [Uri]s for querying audio content across all external storage volumes.
     * Falls back to [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] on API < 29 or when
     * volume discovery returns empty.
     */
    fun getAudioContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Audio.Media.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * Builds list of [Uri]s for querying playlists across all external storage volumes.
     */
    fun getPlaylistContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Audio.Playlists.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)
    }

    /**
     * Returns true if the path points to secondary/removable storage (e.g. physical SD card).
     */
    /**
     * "Show hidden files" setting, read fresh per scan so a toggle change in
     * Settings applies to the next library refresh. Falls back to hiding
     * hidden files if settings cannot be read.
     */
    private fun showHiddenFiles(): Boolean = try {
        SharedPrefsAppSettingsRepository(context).load().showHiddenFiles
    } catch (_: Exception) {
        false
    }

    /** A file or folder counts as hidden when its own name or any parent
     * directory name starts with '.'. System cache directories
     * (/Android/data, /Android/obb) are excluded separately and always. */
    private fun isHiddenPath(path: String, title: String = ""): Boolean =
        title.startsWith(".") ||
            path.split(File.separatorChar).any { it.startsWith(".") }

    /**
     * Issue #218: a path inside another app's private storage. These
     * directories are excluded from the library ALWAYS — regardless of
     * "Show all folders" and regardless of "Show hidden files". They used
     * to be excluded only on the non-show-all path, so turning show-all on
     * let other apps' private videos into the library and made them
     * playable. Keep the same substring match the old filters used, so
     * exactly the same paths are excluded as before on the normal path.
     */
    private fun isSystemCachePath(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb")

    fun isRemovablePath(path: String): Boolean {
        if (path.isBlank()) return false
        // Primary emulated internal storage is /storage/emulated/0
        // Removable SD cards are typically mounted under /storage/XXXX-XXXX or /mnt/media_rw/XXXX-XXXX
        if (path.startsWith("/storage/emulated/")) return false
        if (path.startsWith("/storage/") || path.startsWith("/mnt/media_rw/")) return true

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                storageManager?.storageVolumes?.forEach { volume ->
                    if (volume.isRemovable) {
                        val uuid = volume.uuid
                        if (!uuid.isNullOrBlank() && path.contains(uuid, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Resolves storage root directories across primary internal storage and mounted SD cards.
     */
    fun getStorageRootDirectories(): List<File> {
        val roots = LinkedHashSet<File>()

        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists()) {
                roots.add(primary)
            }
        } catch (_: Exception) {}

        val emulated = File("/storage/emulated/0")
        if (emulated.exists()) {
            roots.add(emulated)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    volume.directory?.let { if (it.exists()) roots.add(it) }
                }
            } catch (_: Exception) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    val uuid = volume.uuid
                    if (!uuid.isNullOrBlank()) {
                        val sdDir = File("/storage/$uuid")
                        if (sdDir.exists()) roots.add(sdDir)
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (dir in extDirs) {
                if (dir != null) {
                    val full = dir.absolutePath
                    val androidIdx = full.indexOf("/Android")
                    if (androidIdx > 0) {
                        val rootPath = full.substring(0, androidIdx)
                        val rootFile = File(rootPath)
                        if (rootFile.exists()) roots.add(rootFile)
                    }
                }
            }
        } catch (_: Exception) {}

        return roots.toList()
    }

    override fun getMediaFolders(): List<MediaFolder> = getMediaFolders(showAllFolders = false)

    override fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> {
        val folderMap = mutableMapOf<String, FolderAccumulator>()
        val seenPaths = mutableSetOf<String>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
        )

        val sevenDaysAgoSec = (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L) / 1000L
        val uris = getVideoContentUris()

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val dateModifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        val parentFile = if (dataPath.isNotBlank()) File(dataPath).parentFile else null
                        val parentPath = parentFile?.absolutePath.orEmpty()

                        // Issue #253: MediaStore bucket ids are the
                        // hash of the LOWER-CASED parent path, so the
                        // fallback must hash the lower-cased path too —
                        // the case-sensitive hash could never equal a
                        // real bucket id, and the folder then opened
                        // empty (see getVideosInFolder's path fallback).
                        val rawBucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) else null
                        val bucketId = rawBucketId?.ifBlank { null }
                            ?: parentPath.ifBlank { "unknown" }.lowercase(Locale.US).hashCode().toString()

                        val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                        val folderName = rawBucketName?.ifBlank { null }
                            ?: parentFile?.name?.ifBlank { "Videos" }
                            ?: "Videos"

                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                        val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                        val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                        val isRecent = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec
                        val isRemovable = isRemovablePath(dataPath.ifBlank { parentPath })

                        val acc = folderMap.getOrPut(bucketId) {
                            FolderAccumulator(
                                id = bucketId,
                                name = folderName,
                                path = parentPath,
                                isRemovable = isRemovable,
                            )
                        }
                        if (parentPath.isNotBlank()) {
                            seenPaths.add(parentPath)
                        }
                        acc.count += 1
                        acc.totalSizeBytes += size
                        acc.totalDurationMs += durationMs
                        if (dateModified > acc.lastModified) {
                            acc.lastModified = dateModified
                        }
                        if (isRecent) {
                            acc.isNew = true
                        }
                        if (isRemovable) {
                            acc.isRemovable = true
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Skip volume if permission error
            } catch (_: Exception) {
                // Skip volume if query fails
            }
        }

        // When showAllFolders is ON, enumerate directories across storage roots so every
        // existing folder (including ones filtered out due to 0 videos or hidden names) is included.
        if (showAllFolders) {
            val storageRoots = getStorageRootDirectories()
            val candidateDirs = mutableListOf<File>()

            for (root in storageRoots) {
                try {
                    val rootFiles = root.listFiles()
                    if (rootFiles != null) {
                        for (f in rootFiles) {
                            if (f.isDirectory) {
                                candidateDirs.add(f)
                                val nameLower = f.name.lowercase(Locale.US)
                                if (nameLower in setOf("dcim", "movies", "pictures", "download", "documents", "video", "videos")) {
                                    f.listFiles()?.filter { it.isDirectory }?.let { candidateDirs.addAll(it) }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            for (root in storageRoots) {
                val standardNames = listOf(
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MUSIC,
                    Environment.DIRECTORY_DOCUMENTS,
                    "Videos",
                )
                for (name in standardNames) {
                    val dir = File(root, name)
                    if (dir.exists() && dir.isDirectory) {
                        candidateDirs.add(dir)
                    }
                }
            }

            for (dir in candidateDirs) {
                val path = dir.absolutePath
                if (path.isNotBlank() && seenPaths.add(path)) {
                    // Issue #253: same lower-cased hash as the
                    // folder-list fallback above — a bucket id is the
                    // hash of the lower-cased parent path.
                    val bucketId = path.lowercase(Locale.US).hashCode().toString()
                    folderMap.getOrPut(bucketId) {
                        FolderAccumulator(
                            id = bucketId,
                            name = dir.name.ifBlank { "Folder" },
                            path = path,
                            isRemovable = isRemovablePath(path),
                        ).apply {
                            count = 0
                            totalSizeBytes = 0L
                            lastModified = dir.lastModified() / 1000L
                            isNew = false
                        }
                    }
                }
            }
        }

        val showHidden = showHiddenFiles()
        // Issue #218: ONE filter for both modes. "Show all folders" only
        // adds empty folders (count == 0); it must not defeat the other
        // exclusions. Hidden folders need the hidden toggle, and system
        // cache directories (/Android/data, /Android/obb) are always
        // excluded — the old show-all branch filtered on hidden only, so
        // those folders (and their videos) leaked into the library.
        val foldersToDisplay = folderMap.values.filter { acc ->
            (showAllFolders || acc.count > 0) &&
            (showHidden || !isHiddenPath(acc.path, acc.name)) &&
            !isSystemCachePath(acc.path)
        }

        val result = foldersToDisplay.map { acc ->
            MediaFolder(
                id = acc.id,
                name = acc.name,
                path = acc.path,
                videoCount = acc.count,
                totalSizeFormatted = formatSize(acc.totalSizeBytes),
                totalDurationMs = acc.totalDurationMs,
                isNew = acc.isNew,
                lastModified = acc.lastModified,
                isStorageRemovable = acc.isRemovable,
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
        cachedFolders = Triple(showAllFolders, showHidden, result)
        return result
    }

    override fun getVideosInFolder(bucketId: String): List<VideoFile> {
        val showHidden = showHiddenFiles()
        val sevenDaysAgoSec = (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L) / 1000L
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val uris = getVideoContentUris()

        // Issue #253: a folder id that is a fallback (its rows' stored
        // BUCKET_ID was blank at folder-list time) never matches a
        // BUCKET_ID = ? query, so the folder opens empty despite
        // videoCount > 0. Resolve by the folder's path from the cached
        // folder list instead — the parent directory is exactly what a
        // bucket id denotes. The path pass runs only when the bucket
        // query came back empty, so the normal (indexed) path is
        // untouched, and only when the folder list was loaded first
        // (otherwise the path is unknown and behaviour is unchanged).
        val folderPath = cachedFolders?.third?.firstOrNull { it.id == bucketId }?.path.orEmpty()

        fun queryVideos(parentPathFilter: String?): List<VideoFile> {
            val videos = mutableListOf<VideoFile>()
            val seenPaths = mutableSetOf<String>()
            for (uri in uris) {
                try {
                    context.contentResolver.query(
                        uri,
                        projection,
                        if (parentPathFilter == null) "${MediaStore.Video.Media.BUCKET_ID} = ?" else null,
                        if (parentPathFilter == null) arrayOf(bucketId) else null,
                        "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                        val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                        val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                        val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                        val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                        val dateModifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                        val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                        val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                            if (path.isNotBlank() && !seenPaths.add(path)) continue
                            // Path pass only: an exact parent match, so
                            // videos in subfolders never leak in (a LIKE
                            // on the path would include them).
                            if (parentPathFilter != null) {
                                val parent = if (path.isNotBlank()) File(path).parent else null
                                if (parent != parentPathFilter) continue
                            }
                            val file = if (path.isNotBlank()) File(path) else null
                            val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                            val title = rawTitle?.ifBlank { null }
                                ?: file?.name?.ifBlank { "Video" }
                                ?: "Video"
                            if (!showHidden && isHiddenPath(path, title)) continue
                            val rawId = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                            val id = rawId.ifBlank { path }
                            val effectivePath = path.ifBlank {
                                if (idCol >= 0) {
                                    ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                                } else ""
                            }
                            val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                            val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                            val widthPx = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                            val heightPx = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0
                            val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                            val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                            val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                            val folderName = rawBucketName?.ifBlank { null }
                                ?: file?.parentFile?.name?.ifBlank { "Videos" }
                                ?: "Videos"
                            videos.add(
                                VideoFile(
                                    id = id,
                                    title = title,
                                    path = effectivePath,
                                    durationMs = durationMs,
                                    sizeBytes = sizeBytes,
                                    sizeFormatted = formatSize(sizeBytes),
                                    dateModified = dateModified,
                                    bucketId = bucketId,
                                    folderName = folderName,
                                    formattedDuration = formatDuration(durationMs),
                                    formattedSize = formatSize(sizeBytes),
                                    isRemovable = isRemovablePath(path),
                                    isNew = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec,
                                    heightPx = heightPx,
                                    widthPx = widthPx,
                                )
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    // Continue to next volume
                } catch (_: Exception) {
                    // Continue to next volume
                }
            }
            return videos
        }

        val byBucketId = queryVideos(null)
        if (byBucketId.isNotEmpty() || folderPath.isBlank()) {
            return byBucketId.sortedBy { it.title.lowercase(Locale.US) }
        }
        return queryVideos(folderPath).sortedBy { it.title.lowercase(Locale.US) }
    }

    override fun getAllVideos(): List<VideoFile> = getAllVideos(showAll = false)

    override fun getAllVideos(showAll: Boolean): List<VideoFile> {
        val videos = mutableListOf<VideoFile>()
        val showHidden = showHiddenFiles()
        val seenPaths = mutableSetOf<String>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val sevenDaysAgoSec = (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L) / 1000L
        val uris = getVideoContentUris()
        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val dateModifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (path.isNotBlank() && !seenPaths.add(path)) continue

                        val file = if (path.isNotBlank()) File(path) else null
                        val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                        val title = rawTitle?.ifBlank { null }
                            ?: file?.name?.ifBlank { "Video" }
                            ?: "Video"

                        // Issue #218: these exclusions are independent of
                        // showAll. Hidden files need the "Show hidden files"
                        // toggle; system cache directories (/Android/data,
                        // /Android/obb) stay excluded regardless — the old
                        // `if (!showAll)` gate disabled all three at once.
                        // showAll only adds empty folders to the folder
                        // list; it never changes which videos are listed.
                        if ((isHiddenPath(path, title) && !showHidden) ||
                            isSystemCachePath(path)
                        ) {
                            continue
                        }

                        val rawId = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                        val id = rawId.ifBlank { path }
                        val effectivePath = path.ifBlank {
                            if (idCol >= 0) {
                                ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                            } else ""
                        }
                        val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val widthPx = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                        val heightPx = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0
                        val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                        val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                        // Issue #253: same lower-cased hash as the
                        // folder-list fallback — VideoFile.bucketId is
                        // passed back into getVideosInFolder, so it must
                        // equal the folder id for the same directory.
                        val rawBucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) else null
                        val bucketId = rawBucketId?.ifBlank { null }
                            ?: file?.parentFile?.absolutePath?.ifBlank { "unknown" }?.lowercase(Locale.US)?.hashCode()?.toString()
                            ?: "unknown"

                        val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                        val folderName = rawBucketName?.ifBlank { null }
                            ?: file?.parentFile?.name?.ifBlank { "Videos" }
                            ?: "Videos"

                        val isRecent = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec
                        val isRemovable = isRemovablePath(path)

                        videos.add(
                            VideoFile(
                                id = id,
                                title = title,
                                path = effectivePath,
                                durationMs = durationMs,
                                sizeBytes = size,
                                dateModified = dateModified,
                                bucketId = bucketId,
                                folderName = folderName,
                                formattedDuration = formatDuration(durationMs),
                                formattedSize = formatSize(size),
                                isRemovable = isRemovable,
                                isNew = isRecent,
                                heightPx = heightPx,
                                widthPx = widthPx,
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Continue to next volume
            } catch (_: Exception) {
                // Continue to next volume
            }
        }
        cachedVideos = Triple(showAll, showHidden, videos)
        return videos
    }

    override fun getAudioTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val showHidden = showHiddenFiles()
        val seenPaths = mutableSetOf<String>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val uris = getAudioContentUris()

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (path.isNotBlank() && !seenPaths.add(path)) continue

                        val id = if (idCol >= 0) cursor.getLong(idCol).toString() else path
                        val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                        val title = rawTitle?.ifBlank { "Unknown Track" } ?: "Unknown Track"
                        if (!showHidden && isHiddenPath(path, title)) continue

                        val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                        val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                            "Unknown Artist"
                        } else {
                            rawArtist
                        }

                        val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                        val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                            "Unknown Album"
                        } else {
                            rawAlbum
                        }

                        val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                        val effectivePath = path.ifBlank {
                            if (idCol >= 0) {
                                ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                            } else ""
                        }

                        tracks.add(
                            AudioTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationFormatted = formatDuration(durationMs),
                                durationMs = durationMs,
                                path = effectivePath,
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Continue to next volume
            } catch (_: Exception) {
                // Continue to next volume
            }
        }

        return tracks.sortedBy { it.title.lowercase(Locale.US) }
    }

    override fun getAudioAlbums(): List<AudioAlbum> {
        val tracks = getAudioTracks()
        // #243: group by (album, artist) — two artists with a same-named
        // album ("Greatest Hits") are two rows, each credited to its own
        // artist with its own track count, instead of one merged row
        // credited to whichever artist came first. The id is a hash of
        // the pair (NUL-separated so ("AB", "C") and ("A", "BC") cannot
        // collide), still a String hash like before, so the DATE sort's
        // id-recency approximation in MediaSortComparator is unchanged
        // in kind. Artists are untouched: an artist's name is its
        // identity by definition, so getAudioArtists() has no symmetric
        // problem.
        return tracks.groupBy { it.album to it.artist }
            .map { (pair, albumTracks) ->
                val (albumName, artistName) = pair
                AudioAlbum(
                    id = "$albumName\u0000$artistName".hashCode().toString(),
                    title = albumName,
                    artist = artistName,
                    trackCount = albumTracks.size,
                )
            }
            .sortedBy { it.title.lowercase(Locale.US) }
    }

    override fun getAudioArtists(): List<AudioArtist> {
        val tracks = getAudioTracks()
        return tracks.groupBy { it.artist }
            .map { (artistName, artistTracks) ->
                AudioArtist(
                    id = artistName.hashCode().toString(),
                    name = artistName,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.map { it.album }.distinct().size,
                )
            }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getAudioFolders(): List<AudioFolder> {
        val tracks = getAudioTracks()
        val folderMap = mutableMapOf<String, MutableList<AudioTrack>>()
        for (track in tracks) {
            // Issue #253: getAudioTracks substitutes a content:// URI
            // for a blank DATA path, and File(content://…).parent is
            // meaningless ("content://media/external/audio/media") —
            // every such track landed in one bogus folder. A content://
            // path has no filesystem parent, so it groups under the
            // Music fallback exactly like a blank path.
            val folderPath = if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
                File(track.path).parent ?: "/storage/emulated/0/Music"
            } else {
                "/storage/emulated/0/Music"
            }
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(track)
        }
        return folderMap.map { (path, folderTracks) ->
            val name = File(path).name.ifBlank { "Music" }
            AudioFolder(
                id = path.hashCode().toString(),
                name = name,
                path = path,
                trackCount = folderTracks.size,
                isStorageRemovable = isRemovablePath(path),
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getPlaylists(): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        val seenNames = mutableSetOf<String>()
        // One library scan shared by every playlist below, so the displayed
        // count is exactly the number of member tracks that will play on tap.
        val tracksById = getAudioTracks().associateBy { it.id }
        val projection = arrayOf(
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME,
        )
        val uris = getPlaylistContentUris()
        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Playlists.NAME} ASC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME)
                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Playlist" else "Playlist"
                        if (seenNames.add(name)) {
                            val memberTracks = id.toLongOrNull()?.let { playlistId ->
                                resolvePlaylistTracks(playlistId, tracksById)
                            }.orEmpty()
                            playlists.add(
                                Playlist(
                                    id = id,
                                    title = name,
                                    itemCount = memberTracks.size,
                                    subtitle = "Device Playlist",
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Playlists may be restricted or unsupported on newer Android targets
            }
        }
        return playlists
    }

    override fun getPlaylistTracks(playlistId: String): List<AudioTrack> {
        val id = playlistId.toLongOrNull() ?: return emptyList()
        return resolvePlaylistTracks(id, getAudioTracks().associateBy { it.id })
    }

    /**
     * Resolves a device playlist's members to library tracks, preserving the
     * playlist's own member order. Members whose audio id is not in the
     * library (removed file, non-music entry, path dedupe) are skipped, so
     * the result is exactly what tapping the playlist will queue.
     */
    private fun resolvePlaylistTracks(
        playlistId: Long,
        tracksById: Map<String, AudioTrack>,
    ): List<AudioTrack> {
        return getPlaylistMemberAudioIds(playlistId).mapNotNull { audioId ->
            tracksById[audioId.toString()]
        }
    }

    /**
     * Reads the MediaStore members table for one device playlist, in
     * PLAY_ORDER. Tries each storage volume in turn and returns the first
     * non-empty member list; playlist ids are only unique per volume, and
     * getPlaylists() keeps the first volume's id for a duplicated name.
     */
    private fun getPlaylistMemberAudioIds(playlistId: Long): List<Long> {
        val memberUris = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (volume in getExternalVolumeNames()) {
                try {
                    memberUris.add(
                        MediaStore.Audio.Playlists.Members.getContentUri(volume, playlistId)
                    )
                } catch (_: Exception) {
                    // Skip volumes that cannot build a members URI
                }
            }
        }
        if (memberUris.isEmpty()) {
            try {
                memberUris.add(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
                )
            } catch (_: Exception) {
                return emptyList()
            }
        }
        val projection = arrayOf(
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.PLAY_ORDER,
        )
        for (uri in memberUris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC",
                )?.use { cursor ->
                    val audioIdCol = cursor.getColumnIndex(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID
                    )
                    if (audioIdCol < 0) return@use
                    val ids = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        ids.add(cursor.getLong(audioIdCol))
                    }
                    if (ids.isNotEmpty()) return ids
                }
            } catch (_: Exception) {
                // This volume has no such playlist; try the next one
            }
        }
        return emptyList()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private class FolderAccumulator(
        val id: String,
        val name: String,
        val path: String,
        var isRemovable: Boolean = false,
    ) {
        var count: Int = 0
        var totalSizeBytes: Long = 0L
        var totalDurationMs: Long = 0L
        var lastModified: Long = 0L
        var isNew: Boolean = false
    }
}
