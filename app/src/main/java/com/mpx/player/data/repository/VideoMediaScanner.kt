package com.mpx.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import java.io.File
import java.util.Locale

/**
 * Scanner for video folders, videos in folders, and whole-library video queries
 * backed by MediaStore.
 */
internal class VideoMediaScanner(
    private val context: Context,
    private val helper: MediaStoreScannerHelper,
    private val getCachedFolderPath: (bucketId: String) -> String? = { null },
) {
    fun getMediaFolders(): List<MediaFolder> = getMediaFolders(showAllFolders = false)

    fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> {
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
        val uris = helper.getVideoContentUris()

        // Issue #275: read once per scan — the row loop below needs it
        // for the per-row hidden filter, and the display filter below
        // reuses the same value.
        val showHidden = helper.showHiddenFiles()

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

                        // Issue #275: apply the hidden filter before
                        // accumulating — getVideosInFolder drops hidden
                        // rows, so counting them here made the tile
                        // count/size/duration (and the root strip, and
                        // the stored subtitle size) lie about what the
                        // folder contains. Same predicate as the folder
                        // display filter and getVideosInFolder; the
                        // path's own components carry the check (a
                        // dotfile's name is its last component) — there
                        // is no TITLE column in this projection.
                        if (!showHidden && helper.isHiddenPath(dataPath)) continue

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
                            ?: parentFile?.name?.ifBlank { context.getString(R.string.media_videos) }
                            ?: context.getString(R.string.media_videos)

                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                        val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                        val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                        val isRecent = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec
                        val isRemovable = helper.isRemovablePath(dataPath.ifBlank { parentPath })

                        val acc = folderMap.getOrPut(bucketId) {
                            FolderAccumulator(
                                id = bucketId,
                                name = folderName,
                                path = parentPath,
                                isRemovable = isRemovable,
                            )
                        }
                        // Issue #317: the accumulator's path is whatever
                        // the first row in the bucket had — if that row's
                        // DATA was blank, the path starts "" and would
                        // stay "" even when later rows in the same bucket
                        // carry a real parent, and the bucket lookup in
                        // applyFolderBlacklist could then never resolve
                        // the bucket's content:// videos. Fill it in from
                        // the first row that has one.
                        if (acc.path.isBlank() && parentPath.isNotBlank()) acc.path = parentPath
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
            val storageRoots = helper.getStorageRootDirectories()
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
                            name = dir.name.ifBlank { context.getString(R.string.media_folder) },
                            path = path,
                            isRemovable = helper.isRemovablePath(path),
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

        // Issue #275: showHidden is read once at the top of the scan
        // (the row loop uses it for the per-row hidden filter).
        // Issue #218: ONE filter for both modes. "Show all folders" only
        // adds empty folders (count == 0); it must not defeat the other
        // exclusions. Hidden folders need the hidden toggle, and system
        // cache directories (/Android/data, /Android/obb) are always
        // excluded — the old show-all branch filtered on hidden only, so
        // those folders (and their videos) leaked into the library.
        val foldersToDisplay = folderMap.values.filter { acc ->
            (showAllFolders || acc.count > 0) &&
            (showHidden || !helper.isHiddenPath(acc.path, acc.name)) &&
            !helper.isSystemCachePath(acc.path)
        }

        return foldersToDisplay.map { acc ->
            MediaFolder(
                id = acc.id,
                name = acc.name,
                path = acc.path,
                videoCount = acc.count,
                totalSizeBytes = acc.totalSizeBytes,
                totalDurationMs = acc.totalDurationMs,
                isNew = acc.isNew,
                lastModified = acc.lastModified,
                isStorageRemovable = acc.isRemovable,
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
    }

    fun getVideosInFolder(bucketId: String): List<VideoFile> {
        val showHidden = helper.showHiddenFiles()
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
        val uris = helper.getVideoContentUris()

        // Issue #253: a folder id that is a fallback (its rows' stored
        // BUCKET_ID was blank at folder-list time) never matches a
        // BUCKET_ID = ? query, so the folder opens empty despite
        // videoCount > 0. Resolve by the folder's path from the cached
        // folder list instead — the parent directory is exactly what a
        // bucket id denotes. The path pass runs only when the bucket
        // query came back empty, so the normal (indexed) path is
        // untouched, and only when the folder list was loaded first
        // (otherwise the path is unknown and behaviour is unchanged).
        val folderPath = getCachedFolderPath(bucketId).orEmpty()

        fun queryVideos(parentPathFilter: String?, recursive: Boolean = false): List<VideoFile> {
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
                            // on the path would include them). Recursive
                            // (#358, synthetic tree folders only): the
                            // video's parent is the folder itself or
                            // any directory under it.
                            if (parentPathFilter != null) {
                                val parent = if (path.isNotBlank()) File(path).parent else null
                                if (recursive) {
                                    if (parent != parentPathFilter &&
                                        !(parent?.startsWith("$parentPathFilter/") ?: false)
                                    ) continue
                                } else if (parent != parentPathFilter) continue
                            }
                            val file = if (path.isNotBlank()) File(path) else null
                            val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                            val title = rawTitle?.ifBlank { null }
                                ?: file?.name?.ifBlank { context.getString(R.string.media_video) }
                                ?: context.getString(R.string.media_video)
                            if (!showHidden && helper.isHiddenPath(path, title)) continue
                            val rawId = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                            val id = rawId.ifBlank { path }
                            val effectivePath = path.ifBlank {
                                if (idCol >= 0) {
                                    ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                                } else ""
                            }
                            // Local playback fix: always carry the content:// URI for
                            // this row, even when DATA is non-blank — mpv must be
                            // handed it (via MpvFdOpener -> fd://), never the raw path.
                            val contentUri = if (idCol >= 0) {
                                ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                            } else ""
                            val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                            val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                            val widthPx = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                            val heightPx = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0
                            val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                            val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                            val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                            val folderName = rawBucketName?.ifBlank { null }
                                ?: file?.parentFile?.name?.ifBlank { context.getString(R.string.media_videos) }
                                ?: context.getString(R.string.media_videos)
                            videos.add(
                                VideoFile(
                                    id = id,
                                    title = title,
                                    path = effectivePath,
                                    contentUri = contentUri,
                                    durationMs = durationMs,
                                    sizeBytes = sizeBytes,
                                    dateModified = dateModified,
                                    bucketId = bucketId,
                                    folderName = folderName,
                                    isRemovable = helper.isRemovablePath(path),
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

        // Issue #358: a synthetic tree folder ("tree:<path>", built by
        // buildFolderTree for an intermediate parent the scan never
        // listed) has no bucket id — resolve it by path. Issue #375
        // changed this from recursive to direct-videos-only: the folder
        // view now lists the subfolders as rows (MX/mpvEx drill-in), so
        // a recursive dump would show every descendant's videos twice
        // — once here, once under its subfolder. A synthetic folder has
        // no direct videos by definition (the scan lists every directory
        // that holds videos), so its view is its subfolder rows; real
        // folder ids keep the bucket / exact-parent behaviour below.
        if (bucketId.startsWith("tree:")) {
            val treePath = bucketId.removePrefix("tree:")
            if (treePath.isBlank()) return emptyList()
            return queryVideos(treePath, recursive = false)
                .sortedBy { it.title.lowercase(Locale.US) }
        }

        val byBucketId = queryVideos(null)
        if (byBucketId.isNotEmpty() || folderPath.isBlank()) {
            return byBucketId.sortedBy { it.title.lowercase(Locale.US) }
        }
        return queryVideos(folderPath).sortedBy { it.title.lowercase(Locale.US) }
    }

    fun getAllVideos(): List<VideoFile> = getAllVideos(showAll = false)

    fun getAllVideos(showAll: Boolean): List<VideoFile> {
        val videos = mutableListOf<VideoFile>()
        val showHidden = helper.showHiddenFiles()
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
        val uris = helper.getVideoContentUris()
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
                            ?: file?.name?.ifBlank { context.getString(R.string.media_video) }
                            ?: context.getString(R.string.media_video)

                        // Issue #218: these exclusions are independent of
                        // showAll. Hidden files need the "Show hidden files"
                        // toggle; system cache directories (/Android/data,
                        // /Android/obb) stay excluded regardless — the old
                        // `if (!showAll)` gate disabled all three at once.
                        // showAll only adds empty folders to the folder
                        // list; it never changes which videos are listed.
                        if ((helper.isHiddenPath(path, title) && !showHidden) ||
                            helper.isSystemCachePath(path)
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
                        // Local playback fix: always carry the content:// URI for
                        // this row, even when DATA is non-blank — mpv must be
                        // handed it (via MpvFdOpener -> fd://), never the raw path.
                        val contentUri = if (idCol >= 0) {
                            ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                        } else ""
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
                            ?: file?.parentFile?.name?.ifBlank { context.getString(R.string.media_videos) }
                            ?: context.getString(R.string.media_videos)

                        val isRecent = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec
                        val isRemovable = helper.isRemovablePath(path)

                        videos.add(
                            VideoFile(
                                id = id,
                                title = title,
                                path = effectivePath,
                                contentUri = contentUri,
                                durationMs = durationMs,
                                sizeBytes = size,
                                dateModified = dateModified,
                                bucketId = bucketId,
                                folderName = folderName,
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
        return videos
    }

    private class FolderAccumulator(
        val id: String,
        val name: String,
        // Issue #317: var, not val — the path is whatever the first row
        // in the bucket had, and if that row's DATA was blank the path
        // starts "" and is filled in from the first later row that has
        // one (see getMediaFolders), so the bucket lookup in
        // applyFolderBlacklist can resolve content:// videos.
        var path: String,
        var isRemovable: Boolean = false,
    ) {
        var count: Int = 0
        var totalSizeBytes: Long = 0L
        var totalDurationMs: Long = 0L
        var lastModified: Long = 0L
        var isNew: Boolean = false
    }
}
