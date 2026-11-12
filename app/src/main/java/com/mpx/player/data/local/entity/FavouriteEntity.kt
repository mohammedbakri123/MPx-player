package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mpx.player.domain.model.FavouriteItem

/**
 * Room entity representing a user-favourited media item.
 */
@Entity(
    tableName = "favourites",
    indices = [
        Index("addedAt"),
        Index("mediaId"),
    ],
)
data class FavouriteEntity(
    @PrimaryKey
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): FavouriteItem = FavouriteItem(
        mediaUri = mediaUri,
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        isVideo = isVideo,
        durationMs = durationMs,
        addedAt = addedAt,
    )

    companion object {
        fun fromDomain(domain: FavouriteItem): FavouriteEntity = FavouriteEntity(
            mediaUri = domain.mediaUri,
            mediaId = domain.mediaId,
            title = domain.title,
            subtitle = domain.subtitle,
            isVideo = domain.isVideo,
            durationMs = domain.durationMs,
            addedAt = domain.addedAt,
        )
    }
}
