package com.mpx.player.data.repository

import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.local.entity.CustomPlaylistEntity
import com.mpx.player.data.local.entity.FavouriteEntity
import com.mpx.player.data.local.entity.PlaybackHistoryEntity
import com.mpx.player.data.local.entity.PlaybackResumeEntity
import com.mpx.player.data.local.entity.PlaylistItemEntity
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [MediaPersistenceRepository] backed by Room SQLite database.
 * Provides reactive [Flow] queries and suspend functions for CRUD operations.
 */
class RoomMediaPersistenceRepository(
    private val database: MpxDatabase,
) : MediaPersistenceRepository {

    private val favouriteDao = database.favouriteDao()
    private val playbackHistoryDao = database.playbackHistoryDao()
    private val playlistDao = database.playlistDao()
    private val playbackResumeDao = database.playbackResumeDao()

    // --- Favourites ---

    override fun observeFavourites(): Flow<List<FavouriteItem>> {
        return favouriteDao.observeFavourites().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFavourites(): List<FavouriteItem> {
        return favouriteDao.getFavourites().map { it.toDomain() }
    }

    override fun observeIsFavourite(mediaUri: String): Flow<Boolean> {
        return favouriteDao.observeIsFavourite(mediaUri)
    }

    override suspend fun isFavourite(mediaUri: String): Boolean {
        return favouriteDao.isFavourite(mediaUri)
    }

    override suspend fun addFavourite(item: FavouriteItem) {
        favouriteDao.insertFavourite(FavouriteEntity.fromDomain(item))
    }

    override suspend fun removeFavourite(mediaUri: String) {
        favouriteDao.deleteFavouriteByUri(mediaUri)
    }

    override suspend fun toggleFavourite(item: FavouriteItem): Boolean {
        val currentlyFav = favouriteDao.isFavourite(item.mediaUri)
        return if (currentlyFav) {
            favouriteDao.deleteFavouriteByUri(item.mediaUri)
            false
        } else {
            favouriteDao.insertFavourite(FavouriteEntity.fromDomain(item))
            true
        }
    }

    override suspend fun clearFavourites() {
        favouriteDao.clearFavourites()
    }

    // --- Playback History ---

    override fun observeHistory(limit: Int): Flow<List<PlaybackHistoryItem>> {
        return playbackHistoryDao.observeHistory(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getHistory(limit: Int): List<PlaybackHistoryItem> {
        return playbackHistoryDao.getHistory(limit).map { it.toDomain() }
    }

    override suspend fun recordHistory(item: PlaybackHistoryItem) {
        playbackHistoryDao.upsertHistory(PlaybackHistoryEntity.fromDomain(item))
    }

    override suspend fun deleteHistoryItem(mediaUri: String) {
        playbackHistoryDao.deleteHistoryByUri(mediaUri)
    }

    override suspend fun clearHistory() {
        playbackHistoryDao.clearHistory()
    }

    // --- Custom Playlists ---

    override fun observePlaylists(): Flow<List<CustomPlaylist>> {
        return playlistDao.observePlaylistsWithCount().map { list ->
            list.map {
                CustomPlaylist(
                    id = it.id,
                    name = it.name,
                    itemCount = it.itemCount,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    isVideoPlaylist = it.isVideoPlaylist,
                )
            }
        }
    }

    override suspend fun getPlaylists(): List<CustomPlaylist> {
        return playlistDao.getPlaylistsWithCount().map {
            CustomPlaylist(
                id = it.id,
                name = it.name,
                itemCount = it.itemCount,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                isVideoPlaylist = it.isVideoPlaylist,
            )
        }
    }

    override suspend fun getPlaylistById(id: Long): CustomPlaylist? {
        val entity = playlistDao.getPlaylistById(id) ?: return null
        val count = playlistDao.getItemCount(id)
        return entity.toDomain(count)
    }

    override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long {
        val now = System.currentTimeMillis()
        val entity = CustomPlaylistEntity(
            name = name.trim().ifBlank { "New Playlist" },
            createdAt = now,
            updatedAt = now,
            isVideoPlaylist = isVideoPlaylist,
        )
        return playlistDao.insertPlaylist(entity)
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val existing = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            existing.copy(
                name = newName.trim().ifBlank { existing.name },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    override suspend fun clearPlaylists() {
        playlistDao.clearPlaylists()
    }

    // --- Playlist Items ---

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistDao.observePlaylistItems(playlistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> {
        return playlistDao.getPlaylistItems(playlistId).map { it.toDomain() }
    }

    override suspend fun addItemToPlaylist(item: PlaylistItem): Long {
        val order = playlistDao.getNextOrderIndex(item.playlistId)
        val entity = PlaylistItemEntity.fromDomain(item.copy(orderIndex = order))
        // Issue #230: IGNORE returns -1 when the item is already in the
        // playlist — the existing row keeps its position. Only bump
        // updatedAt when something was actually added.
        val rowId = playlistDao.insertPlaylistItem(entity)
        if (rowId != -1L) {
            playlistDao.getPlaylistById(item.playlistId)?.let { pl ->
                playlistDao.updatePlaylist(pl.copy(updatedAt = System.currentTimeMillis()))
            }
        }
        return rowId
    }

    override suspend fun removeItemFromPlaylist(playlistItemId: Long) {
        playlistDao.deletePlaylistItem(playlistItemId)
    }

    override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) {
        playlistDao.removeMediaFromPlaylist(playlistId, mediaUri)
    }

    override suspend fun clearPlaylistItems(playlistId: Long) {
        playlistDao.clearPlaylistItems(playlistId)
    }

    // --- Resume Positions ---

    override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> {
        return playbackResumeDao.observeResumePositions().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllResumePositions(): List<PlaybackResumePosition> {
        return playbackResumeDao.getAllResumePositions().map { it.toDomain() }
    }

    override suspend fun getResumePosition(mediaId: String): Long? {
        return playbackResumeDao.getResumePosition(mediaId)?.positionMs
    }

    override suspend fun saveResumePosition(
        mediaId: String,
        mediaUri: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val entity = PlaybackResumeEntity(
            mediaId = mediaId,
            mediaUri = mediaUri,
            positionMs = positionMs,
            durationMs = durationMs,
            lastUpdated = System.currentTimeMillis(),
        )
        playbackResumeDao.upsertResumePosition(entity)
    }

    override suspend fun clearResumePosition(mediaId: String) {
        playbackResumeDao.deleteResumePosition(mediaId)
    }

    override suspend fun clearAllResumePositions() {
        playbackResumeDao.clearAllResumePositions()
    }

    override suspend fun loadAllResumePositionsMap(): Map<String, Long> {
        return playbackResumeDao.getAllResumePositions().associate {
            it.mediaId to it.positionMs
        }
    }
}
