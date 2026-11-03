package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mpx.player.domain.model.CustomPlaylist

/**
 * Room entity representing a custom playlist container.
 */
@Entity(tableName = "custom_playlists")
data class CustomPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isVideoPlaylist: Boolean = false,
) {
    fun toDomain(itemCount: Int = 0): CustomPlaylist = CustomPlaylist(
        id = id,
        name = name,
        itemCount = itemCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isVideoPlaylist = isVideoPlaylist,
    )
}
