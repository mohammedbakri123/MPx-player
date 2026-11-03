package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mpx.player.domain.model.PlaylistItem

/**
 * Room entity representing an item inside a custom playlist.
 * Foreign-keyed to [CustomPlaylistEntity] with cascade deletion.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = CustomPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "mediaUri"], unique = true),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistId: Long,
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): PlaylistItem = PlaylistItem(
        id = id,
        playlistId = playlistId,
        mediaUri = mediaUri,
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        isVideo = isVideo,
        durationMs = durationMs,
        orderIndex = orderIndex,
        addedAt = addedAt,
    )

    companion object {
        fun fromDomain(domain: PlaylistItem): PlaylistItemEntity = PlaylistItemEntity(
            id = domain.id,
            playlistId = domain.playlistId,
            mediaUri = domain.mediaUri,
            mediaId = domain.mediaId,
            title = domain.title,
            subtitle = domain.subtitle,
            isVideo = domain.isVideo,
            durationMs = domain.durationMs,
            orderIndex = domain.orderIndex,
            addedAt = domain.addedAt,
        )
    }
}
