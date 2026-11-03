package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.PlaybackResumeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackResumeDao {
    @Query("SELECT * FROM playback_resume ORDER BY lastUpdated DESC")
    fun observeResumePositions(): Flow<List<PlaybackResumeEntity>>

    @Query("SELECT * FROM playback_resume")
    suspend fun getAllResumePositions(): List<PlaybackResumeEntity>

    @Query("SELECT * FROM playback_resume WHERE mediaId = :mediaId")
    suspend fun getResumePosition(mediaId: String): PlaybackResumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResumePosition(resume: PlaybackResumeEntity)

    @Query("DELETE FROM playback_resume WHERE mediaId = :mediaId")
    suspend fun deleteResumePosition(mediaId: String)

    @Query("DELETE FROM playback_resume")
    suspend fun clearAllResumePositions()
}
