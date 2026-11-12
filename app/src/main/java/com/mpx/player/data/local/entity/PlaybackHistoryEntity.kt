package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mpx.player.domain.model.PlaybackHistoryItem

/**
 * Room entity representing an entry in playback history.
 */
@Entity(
    tableName = "playback_history",
    indices = [
        Index("lastPlayedAt"),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    fun toDomain(): PlaybackHistoryItem = PlaybackHistoryItem(
        mediaUri = mediaUri,
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        isVideo = isVideo,
        lastPlayedAt = lastPlayedAt,
        playbackPositionMs = playbackPositionMs,
        durationMs = durationMs,
    )

    companion object {
        fun fromDomain(domain: PlaybackHistoryItem): PlaybackHistoryEntity = PlaybackHistoryEntity(
            mediaUri = domain.mediaUri,
            mediaId = domain.mediaId,
            title = domain.title,
            subtitle = domain.subtitle,
            isVideo = domain.isVideo,
            lastPlayedAt = domain.lastPlayedAt,
            playbackPositionMs = domain.playbackPositionMs,
            durationMs = domain.durationMs,
        )
    }
}
