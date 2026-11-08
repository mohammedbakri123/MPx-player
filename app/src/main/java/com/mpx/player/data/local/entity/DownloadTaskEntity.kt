package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mpx.player.download.ActiveDownload
import com.mpx.player.download.DownloadStatus
import com.mpx.player.download.VideoClip

/**
 * Room entity for one persisted download task (issue #342): the queue
 * survives process death, so a killed app does not lose unfinished
 * downloads. Only the rows the user can still act on are kept —
 * QUEUED (resumed on start), DOWNLOADING (marked FAILED on start,
 * the process died mid-download), and FAILED (retryable). DONE and
 * CANCELED rows are deleted: a finished file already lives in the
 * completed list (a scan of the download dir), and a canceled row has
 * no action. The per-download cookie file (#337) is deliberately NOT
 * persisted — it is a staging file deleted when the download exits,
 * so a restored task always runs without cookies.
 *
 * [createdAt] is the queue order (FIFO). It is preserved across
 * upserts (see [DownloadTaskDao]) — a status write must never move a
 * task to the back of the queue.
 */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    /** [DownloadStatus] name. */
    val status: String,
    val error: String? = null,
    val audioOnly: Boolean = false,
    val subtitles: Boolean = false,
    /** Issue #383: the per-video format selector ("137+140"), null when the global settings apply. */
    val formatId: String? = null,
    /**
     * Issue #386: the clips, encoded "start-end,start-end" in
     * seconds (e.g. "83-296,600-720"), null when the whole video is
     * downloaded. A plain string keeps the table flat — no second
     * table for a per-download list.
     */
    val clips: String? = null,
    /** Issue #387: split this download by chapter, false for existing rows (one file). */
    val splitByChapter: Boolean = false,
    /** Issue #388: the rename from the format sheet, null when the video keeps its own title. */
    val newTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toActiveDownload(): ActiveDownload = ActiveDownload(
        id = id,
        url = url,
        title = title,
        status = runCatching { DownloadStatus.valueOf(status) }
            .getOrDefault(DownloadStatus.FAILED),
        error = error,
        audioOnly = audioOnly,
        subtitles = subtitles,
        formatId = formatId,
        clips = decodeClips(clips),
        splitByChapter = splitByChapter,
        newTitle = newTitle,
    )

    companion object {
        fun fromActiveDownload(
            item: ActiveDownload,
            createdAt: Long = System.currentTimeMillis(),
        ): DownloadTaskEntity = DownloadTaskEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            status = item.status.name,
            error = item.error,
            audioOnly = item.audioOnly,
            subtitles = item.subtitles,
            formatId = item.formatId,
            clips = encodeClips(item.clips),
            splitByChapter = item.splitByChapter,
            newTitle = item.newTitle,
            createdAt = createdAt,
        )

        /** "83-296,600-720", or null for no clips. */
        internal fun encodeClips(clips: List<VideoClip>): String? =
            clips.takeIf { it.isNotEmpty() }
                ?.joinToString(",") { "${it.start}-${it.end}" }

        /** The inverse — a bad stored value decodes to no clips, never a crash. */
        internal fun decodeClips(encoded: String?): List<VideoClip> {
            if (encoded.isNullOrBlank()) return emptyList()
            return encoded.split(",").mapNotNull { part ->
                val nums = part.split("-").mapNotNull { it.toIntOrNull() }
                val start = nums.getOrNull(0)
                val end = nums.getOrNull(1)
                if (start != null && end != null && start >= 0 && end > start) {
                    VideoClip(start, end)
                } else {
                    null
                }
            }
        }
    }
}
