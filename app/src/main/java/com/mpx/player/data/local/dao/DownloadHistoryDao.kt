package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mpx.player.data.local.entity.DownloadHistoryEntity

/**
 * DAO for the download history (issue #346). History is append-only:
 * one row per finished download, newest first. Deleting a history
 * row never deletes the file — it only forgets the record.
 */
@Dao
interface DownloadHistoryDao {
    /** Newest first, for the History section. */
    @Query("SELECT * FROM download_history ORDER BY completedAt DESC")
    suspend fun getAll(): List<DownloadHistoryEntity>

    /** Latest record for a video id — the archive-skip lookup. */
    @Query("SELECT * FROM download_history WHERE videoId = :videoId ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestByVideoId(videoId: String): DownloadHistoryEntity?

    /** Latest record for a URL — the fallback when there is no video id. */
    @Query("SELECT * FROM download_history WHERE url = :url ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestByUrl(url: String): DownloadHistoryEntity?

    @Insert
    suspend fun insert(entry: DownloadHistoryEntity): Long

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
