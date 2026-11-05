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
 * Issue #217: the UI-facing id of a user-created (Room) playlist.
 * Room's autoGenerate ids start at 1 — the same small ints MediaStore
 * uses for device playlist `_ID`s — so a bare `id.toString()` collided
 * with device playlists in the merged Playlists list (wrong playlist
 * opened/played, duplicate lazy-row keys). The "custom:" prefix keeps
 * the two kinds in separate namespaces; device playlists keep their
 * bare MediaStore id. The Room id itself is unchanged — only the
 * UI-facing [Playlist.id] is namespaced.
 */
fun customPlaylistId(roomId: Long): String = "custom:$roomId"

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
        id = customPlaylistId(id),
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
 */
data class PlaybackResumePosition(
    val mediaId: String,
    val mediaUri: String,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis(),
)
