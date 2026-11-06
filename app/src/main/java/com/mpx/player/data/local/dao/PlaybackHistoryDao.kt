package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    // #265: offset-based paging — the old LIMIT 100 with no offset made
    // rows past 100 unreachable. Callers page by growing the limit.
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit OFFSET :offset")
    fun observeHistory(limit: Int = 100, offset: Int = 0): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistory(limit: Int = 100, offset: Int = 0): List<PlaybackHistoryEntity>

    // #265: reactive total — Room re-emits on every table change, so the
    // counts in Me / Music / History stay honest instead of freezing at
    // the 100-row cap (the old code rendered history.size as the total).
    @Query("SELECT COUNT(*) FROM playback_history")
    fun observeHistoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: PlaybackHistoryEntity): Long

    @Query("DELETE FROM playback_history WHERE mediaUri = :mediaUri")
    suspend fun deleteHistoryByUri(mediaUri: String): Int

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory(): Int

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun getCount(): Int
}
