package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.DownloadTaskEntity

/**
 * DAO for persisted download tasks (issue #342). The queue is small
 * (a handful of rows at most) and only read once per launch, so a
 * plain list query is enough — no Flow.
 */
@Dao
interface DownloadTaskDao {
    /** Queue order: oldest first. */
    @Query("SELECT * FROM download_tasks ORDER BY createdAt ASC")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
