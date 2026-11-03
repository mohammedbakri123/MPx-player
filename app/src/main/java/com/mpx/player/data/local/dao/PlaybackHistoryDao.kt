package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getHistory(limit: Int = 100): List<PlaybackHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaUri = :mediaUri")
    suspend fun deleteHistoryByUri(mediaUri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun getCount(): Int
}
