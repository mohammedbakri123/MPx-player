package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mpx.player.domain.model.PlaybackResumePosition

/**
 * Room entity representing a saved playback resume position for a media item.
 */
@Entity(tableName = "playback_resume")
data class PlaybackResumeEntity(
    @PrimaryKey
    val mediaId: String,
    val mediaUri: String,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    fun toDomain(): PlaybackResumePosition = PlaybackResumePosition(
        mediaId = mediaId,
        mediaUri = mediaUri,
        positionMs = positionMs,
        durationMs = durationMs,
        lastUpdated = lastUpdated,
    )

    companion object {
        fun fromDomain(domain: PlaybackResumePosition): PlaybackResumeEntity = PlaybackResumeEntity(
            mediaId = domain.mediaId,
            mediaUri = domain.mediaUri,
            positionMs = domain.positionMs,
            durationMs = domain.durationMs,
            lastUpdated = domain.lastUpdated,
        )
    }
}
