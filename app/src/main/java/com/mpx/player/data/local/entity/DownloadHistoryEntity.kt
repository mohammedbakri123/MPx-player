package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mpx.player.download.DownloadedFile

/**
 * Room entity for one finished download (issue #346): the download
 * history. Seal keeps the same record as `DownloadedVideoInfo` and
 * inserts it in `onFinishDownloading` (JunkFood02/Seal, GPL-3.0 —
 * `DownloadUtil.kt:827` + `DatabaseUtil.insertInfo`); MPx's shape is
 * its own (see below), only the idea is copied.
 *
 * Unlike the completed list — a directory/MediaStore scan of files
 * that exist right now — history is a permanent record: it survives
 * the file being deleted, and it carries the title/uploader the scan
 * cannot know. It is also what the `--download-archive` skip looks
 * up: a re-download of an archived video marks DONE with the file
 * from the latest history row, when it still exists.
 *
 * One row per finished download (a log, not a set): downloading the
 * same video again after the archive was cleared adds a second row.
 * [videoId] is null for direct-link (HTTP fallback) downloads, which
 * have no extractor id. [fileUri] is the same string the completed
 * list uses — a `content://` MediaStore URI for public files, a file
 * path for private leftovers — so a history row converts back to a
 * [DownloadedFile] for playback.
 */
@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String? = null,
    val extractor: String? = null,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val fileUri: String,
    val fileName: String,
    val fileSize: Long = 0,
    val isPublic: Boolean = true,
    // Issue #393: the file lives on the SD card (SAF tree), not in
    // the public Downloads folder. NOT NULL DEFAULT 0 in the 8→9
    // migration — existing rows are public-Downloads files.
    val isSdCard: Boolean = false,
    val completedAt: Long = System.currentTimeMillis(),
) {
    /** The finished file, for playback. */
    fun toDownloadedFile(): DownloadedFile = DownloadedFile(
        uri = fileUri,
        name = fileName,
        size = fileSize,
        lastModified = completedAt,
        isPublic = isPublic,
        isSdCard = isSdCard,
    )
}
