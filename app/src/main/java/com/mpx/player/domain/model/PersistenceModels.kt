package com.mpx.player.domain.model

/**
 * Domain model representing a user-favourited media item (audio or video).
 */
data class FavouriteItem(
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Domain model representing an entry in playback history.
 */
data class PlaybackHistoryItem(
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Domain model representing a custom user-created playlist.
 * Maps seamlessly to the existing [Playlist] domain model.
 */
data class CustomPlaylist(
    val id: Long,
    val name: String,
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isVideoPlaylist: Boolean = false,
) {
    fun toPlaylist(): Playlist = Playlist(
        id = id.toString(),
        title = name,
        itemCount = itemCount,
        subtitle = if (isVideoPlaylist) "Video Playlist" else "Custom Playlist",
    )
}

/**
 * Domain model representing an item stored inside a custom playlist.
 */
data class PlaylistItem(
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
)

/**
 * Domain model representing a persistent media resume position.
 *
 * Issue #227: [mediaId] is the resume key — the stable media URI/path, not
 * the bare MediaStore row id. For rows written before the fix it may still
 * hold a legacy bare id (orphan, never looked up again).
 */
data class PlaybackResumePosition(
    val mediaId: String,
    val mediaUri: String,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis(),
)
