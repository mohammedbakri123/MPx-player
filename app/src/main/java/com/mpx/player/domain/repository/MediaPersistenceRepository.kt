package com.mpx.player.domain.repository

import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for local persistent storage of:
 * - User favourites (audio and video)
 * - Playback history
 * - Custom playlists and playlist media items
 * - Persistent playback resume positions
 */
interface MediaPersistenceRepository {

    // --- Favourites ---
    fun observeFavourites(): Flow<List<FavouriteItem>>
    suspend fun getFavourites(): List<FavouriteItem>
    fun observeIsFavourite(mediaUri: String): Flow<Boolean>
    suspend fun isFavourite(mediaUri: String): Boolean
    suspend fun addFavourite(item: FavouriteItem)
    suspend fun removeFavourite(mediaUri: String)
    suspend fun toggleFavourite(item: FavouriteItem): Boolean
    suspend fun clearFavourites()

    // --- Playback History ---
    fun observeHistory(limit: Int = 100): Flow<List<PlaybackHistoryItem>>
    suspend fun getHistory(limit: Int = 100): List<PlaybackHistoryItem>
    suspend fun recordHistory(item: PlaybackHistoryItem)
    suspend fun deleteHistoryItem(mediaUri: String)
    suspend fun clearHistory()

    // --- Custom Playlists ---
    fun observePlaylists(): Flow<List<CustomPlaylist>>
    suspend fun getPlaylists(): List<CustomPlaylist>
    suspend fun getPlaylistById(id: Long): CustomPlaylist?
    suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean = false): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun clearPlaylists()

    // --- Playlist Items ---
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>>
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem>
    /**
     * Adds [item] to its playlist. Issue #230: returns the new row id, or
     * -1 when the item is already in the playlist (the insert is a no-op —
     * the existing row keeps its position, it is NOT moved to the end).
     */
    suspend fun addItemToPlaylist(item: PlaylistItem): Long
    suspend fun removeItemFromPlaylist(playlistItemId: Long)
    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String)
    suspend fun clearPlaylistItems(playlistId: Long)

    // --- Resume Positions ---
    fun observeResumePositions(): Flow<List<PlaybackResumePosition>>
    suspend fun getAllResumePositions(): List<PlaybackResumePosition>
    suspend fun getResumePosition(mediaId: String): Long?
    suspend fun saveResumePosition(mediaId: String, mediaUri: String, positionMs: Long, durationMs: Long = 0L)
    suspend fun clearResumePosition(mediaId: String)
    suspend fun clearAllResumePositions()
    suspend fun loadAllResumePositionsMap(): Map<String, Long>
}
