package com.mpx.player.fakes

import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Shared test fake for [MediaPersistenceRepository].
 *
 * Implements in-memory state flows for favourites, history, custom playlists,
 * playlist items, and resume positions.
 */
class FakeMediaPersistenceRepository : MediaPersistenceRepository {

    val favouritesFlow = MutableStateFlow<List<FavouriteItem>>(emptyList())
    val historyFlow = MutableStateFlow<List<PlaybackHistoryItem>>(emptyList())
    val playlistsFlow = MutableStateFlow<List<CustomPlaylist>>(emptyList())

    // Property aliases supporting all existing test suites without code changes
    val favourites: MutableStateFlow<List<FavouriteItem>> get() = favouritesFlow
    val historyItems: MutableStateFlow<List<PlaybackHistoryItem>> get() = historyFlow
    val playlists: MutableStateFlow<List<CustomPlaylist>> get() = playlistsFlow
    val customPlaylists: MutableStateFlow<List<CustomPlaylist>> get() = playlistsFlow

    val historyRecords = mutableListOf<PlaybackHistoryItem>()
    val historyCount = MutableStateFlow(0)
    var historyTotal: Int? = null

    val playlistItems = mutableMapOf<Long, MutableList<PlaylistItem>>()
    val pendingLoads = mutableMapOf<Long, CompletableDeferred<List<PlaylistItem>>>()
    var resumePositions: List<PlaybackResumePosition> = emptyList()

    private var nextRowId = 1L
    private var nextPlaylistId = 1L

    override fun observeFavourites(): Flow<List<FavouriteItem>> = favouritesFlow.asStateFlow()

    override suspend fun getFavourites(): List<FavouriteItem> = favouritesFlow.value

    override fun observeIsFavourite(mediaUri: String): Flow<Boolean> =
        favouritesFlow.map { list -> list.any { it.mediaUri == mediaUri } }

    override suspend fun isFavourite(mediaUri: String): Boolean =
        favouritesFlow.value.any { it.mediaUri == mediaUri }

    override suspend fun addFavourite(item: FavouriteItem) {
        if (favouritesFlow.value.none { it.mediaUri == item.mediaUri }) {
            favouritesFlow.value = favouritesFlow.value + item
        }
    }

    override suspend fun removeFavourite(mediaUri: String) {
        favouritesFlow.value = favouritesFlow.value.filter { it.mediaUri != mediaUri }
    }

    override suspend fun toggleFavourite(item: FavouriteItem): Boolean {
        return if (favouritesFlow.value.any { it.mediaUri == item.mediaUri }) {
            removeFavourite(item.mediaUri)
            false
        } else {
            addFavourite(item)
            true
        }
    }

    override suspend fun clearFavourites() {
        favouritesFlow.value = emptyList()
    }

    override fun observeHistory(limit: Int, offset: Int): Flow<List<PlaybackHistoryItem>> =
        historyFlow.map { it.drop(offset).take(limit) }

    override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> =
        historyFlow.value.drop(offset).take(limit)

    override fun observeHistoryCount(): Flow<Int> =
        if (historyCount.value != 0) historyCount else historyFlow.map { historyTotal ?: it.size }

    override suspend fun recordHistory(item: PlaybackHistoryItem) {
        historyRecords.add(item)
        historyFlow.value = listOf(item) + historyFlow.value
    }

    override suspend fun deleteHistoryItem(mediaUri: String) {
        historyFlow.value = historyFlow.value.filter { it.mediaUri != mediaUri }
    }

    override suspend fun clearHistory() {
        historyFlow.value = emptyList()
    }

    override fun observePlaylists(): Flow<List<CustomPlaylist>> = playlistsFlow.asStateFlow()

    override suspend fun getPlaylists(): List<CustomPlaylist> = playlistsFlow.value

    override suspend fun getPlaylistById(id: Long): CustomPlaylist? =
        playlistsFlow.value.firstOrNull { it.id == id }

    override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long {
        val id = nextPlaylistId++
        playlistsFlow.value = playlistsFlow.value +
            CustomPlaylist(id = id, name = name, isVideoPlaylist = isVideoPlaylist)
        playlistItems[id] = mutableListOf()
        return id
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistsFlow.value = playlistsFlow.value.map {
            if (it.id == playlistId) it.copy(name = newName) else it
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsFlow.value = playlistsFlow.value.filter { it.id != playlistId }
        playlistItems.remove(playlistId)
    }

    override suspend fun clearPlaylists() {
        playlistsFlow.value = emptyList()
        playlistItems.clear()
    }

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
        flowOf(playlistItems[playlistId]?.toList() ?: emptyList())

    override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> {
        pendingLoads[playlistId]?.let { return it.await() }
        return playlistItems[playlistId]?.toList() ?: emptyList()
    }

    override suspend fun addItemToPlaylist(item: PlaylistItem): Long {
        val items = playlistItems.getOrPut(item.playlistId) { mutableListOf() }
        if (items.any { it.mediaUri == item.mediaUri }) return -1L
        val rowId = if (item.id != 0L) item.id else nextRowId++
        val assigned = item.copy(id = rowId)
        items.add(assigned)
        return rowId
    }

    override suspend fun removeItemFromPlaylist(playlistItemId: Long) {
        playlistItems.values.forEach { items ->
            items.removeAll { it.id == playlistItemId }
        }
    }

    override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) {
        playlistItems[playlistId]?.removeAll { it.mediaUri == mediaUri }
    }

    override suspend fun clearPlaylistItems(playlistId: Long) {
        playlistItems[playlistId]?.clear()
    }

    override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> =
        flowOf(resumePositions)

    override suspend fun getAllResumePositions(): List<PlaybackResumePosition> =
        resumePositions

    override suspend fun getResumePosition(mediaId: String): Long? =
        resumePositions.firstOrNull { it.mediaId == mediaId }?.positionMs

    override suspend fun saveResumePosition(
        mediaId: String,
        mediaUri: String,
        positionMs: Long,
        durationMs: Long,
    ) = Unit

    override suspend fun clearResumePosition(mediaId: String) = Unit

    override suspend fun clearAllResumePositions() = Unit

    override suspend fun loadAllResumePositionsMap(): Map<String, Long> = emptyMap()
}
