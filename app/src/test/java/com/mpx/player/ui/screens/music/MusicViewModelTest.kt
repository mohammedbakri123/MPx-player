package com.mpx.player.ui.screens.music

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Migration Phase 2c: unit tests for MusicViewModel, through fakes
 * only — a fake [MediaRepository], a fake [MediaPersistenceRepository],
 * and a fake [AppSettingsRepository] — no mocking framework, no
 * Android runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {

    private lateinit var media: FakeMediaRepository
    private lateinit var persistence: FakeMediaPersistenceRepository
    private lateinit var settingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        media = FakeMediaRepository()
        persistence = FakeMediaPersistenceRepository()
        settingsRepo = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MusicViewModel(media, persistence, settingsRepo)

    /** viewModelScope runs on Dispatchers.Main; the unconfined test
     * dispatcher executes the ViewModel's collectors eagerly, so
     * StateFlow values are observable without advancing a scheduler. */
    private fun setTestMainDispatcher(): Unit =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String,
        path: String,
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationFormatted = "0:01",
        durationMs = 1_000L,
        path = path,
    )

    @Test
    fun `refreshLibrary loads the library and clears it without permission`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.refreshLibrary(hasPermission = true)

        assertFalse(viewModel.isLoading.value)
        assertEquals(listOf("Alpha Song", "Beta Song", "Gamma"), viewModel.tracks.value.map { it.title })
        assertEquals(listOf("Abbey", "Exile", "Secret Album"), viewModel.albums.value.map { it.title })
        assertEquals(listOf("Beatles", "Stones"), viewModel.artists.value.map { it.name })
        assertEquals(listOf("Device Mix"), viewModel.playlists.value.map { it.title })
        assertTrue(viewModel.hasPermission.value)

        viewModel.refreshLibrary(hasPermission = false)

        assertTrue(viewModel.tracks.value.isEmpty())
        assertTrue(viewModel.albums.value.isEmpty())
        assertTrue(viewModel.artists.value.isEmpty())
        assertTrue(viewModel.folders.value.isEmpty())
        assertTrue(viewModel.playlists.value.isEmpty())
        assertFalse(viewModel.hasPermission.value)
    }

    @Test
    fun `refreshLibrary applies the folder blacklist and recomputes counts`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = settingsRepo.stored.copy(
            blacklistedFolders = setOf("/storage/emulated/0/Music/Secret"),
        )
        val viewModel = createViewModel()

        viewModel.refreshLibrary(hasPermission = true)

        // The track in the blacklisted folder is hidden (issue #300).
        assertEquals(listOf("Alpha Song", "Beta Song"), viewModel.tracks.value.map { it.title })
        // Albums keep only rows with a visible track, counts recomputed.
        assertEquals(listOf("Abbey", "Exile"), viewModel.albums.value.map { it.title })
        assertEquals(2, viewModel.albums.value.first { it.title == "Abbey" }.trackCount)
        // Artists keep only rows with a visible track, counts recomputed.
        assertEquals(2, viewModel.artists.value.first { it.name == "Beatles" }.trackCount)
        assertEquals(1, viewModel.artists.value.first { it.name == "Beatles" }.albumCount)
        // Folders filter directly on their path.
        assertEquals(listOf("/storage/emulated/0/Music"), viewModel.folders.value.map { it.path })
        // Playlists are deliberately NOT filtered: their members are
        // explicit user choices.
        assertEquals(listOf("Device Mix"), viewModel.playlists.value.map { it.title })
    }

    @Test
    fun `search filters tracks and selection still resolves hidden tracks`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.refreshLibrary(hasPermission = true)

        viewModel.setSearchQuery("beatles")

        assertEquals(
            listOf("Alpha Song", "Gamma"),
            viewModel.sortedTracks.value.map { it.title },
        )

        // Select a track the search box hides (issue #234): the
        // selection resolves against the unfiltered sorted list.
        viewModel.toggleTrackSelection(viewModel.tracks.value.first { it.title == "Beta Song" })
        assertEquals(
            listOf("Beta Song"),
            viewModel.selectedTracksInView().map { it.title },
        )
    }

    @Test
    fun `sort orders tracks by name and reverse flips it`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.refreshLibrary(hasPermission = true)

        assertEquals(
            listOf("Alpha Song", "Beta Song", "Gamma"),
            viewModel.sortedTracks.value.map { it.title },
        )

        viewModel.setSortReverse(true)

        assertEquals(
            listOf("Gamma", "Beta Song", "Alpha Song"),
            viewModel.sortedTracks.value.map { it.title },
        )
        assertTrue(viewModel.settings.value.sortReverse)
    }

    @Test
    fun `album grouping respects artist and selection plays only its tracks`() = runTest {
        setTestMainDispatcher()
        media.tracks = listOf(
            track("1", "One", "Beatles", "Same Title", "/storage/emulated/0/Music/one.mp3"),
            track("2", "Two", "Stones", "Same Title", "/storage/emulated/0/Music/two.mp3"),
            track("3", "Three", "Beatles", "Same Title", "/storage/emulated/0/Music/three.mp3"),
        )
        media.albums = listOf(
            AudioAlbum(id = "a1", title = "Same Title", artist = "Beatles", trackCount = 2),
            AudioAlbum(id = "a2", title = "Same Title", artist = "Stones", trackCount = 1),
        )
        val viewModel = createViewModel()
        viewModel.refreshLibrary(hasPermission = true)

        // Same-named albums by different artists stay separate (#243).
        val beatlesAlbum = viewModel.albums.value.first { it.artist == "Beatles" }
        assertEquals(listOf("One", "Three"), viewModel.albumTracks(beatlesAlbum).map { it.title })

        viewModel.toggleAlbumSelection(beatlesAlbum)
        assertEquals(listOf("One", "Three"), viewModel.selectedAlbumTracks().map { it.title })
    }

    @Test
    fun `toggleMusicTab refuses to hide the last visible tab`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = AppSettings(
            showMusicTracksTab = true,
            showMusicPlaylistsTab = false,
            showMusicAlbumsTab = false,
            showMusicArtistsTab = false,
            showMusicFoldersTab = false,
        )
        val viewModel = createViewModel()

        assertFalse(viewModel.toggleMusicTab("tracks", currentValue = true))
        assertTrue(viewModel.settings.value.showMusicTracksTab)

        // With two tabs visible, hiding one is allowed.
        settingsRepo.stored = settingsRepo.stored.copy(showMusicPlaylistsTab = true)
        viewModel.reloadSettings()
        assertTrue(viewModel.toggleMusicTab("playlists", currentValue = true))
        assertFalse(viewModel.settings.value.showMusicPlaylistsTab)
        assertTrue(viewModel.settings.value.showMusicTracksTab)
    }

    @Test
    fun `toggleFavouriteForTracks removes all when all are favourites and adds otherwise`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.refreshLibrary(hasPermission = true)
        val selected = viewModel.tracks.value.take(2)

        // Nothing favourited yet: adds the missing ones.
        assertFalse(viewModel.toggleFavouriteForTracks(selected))
        assertEquals(2, persistence.favourites.value.size)

        // All favourited now: removes them all as a group.
        assertTrue(viewModel.toggleFavouriteForTracks(selected))
        assertTrue(persistence.favourites.value.isEmpty())
    }

    @Test
    fun `updateViewMode persists grid and mirrors local when layouts are shared`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = settingsRepo.stored.copy(separateViewLayout = false)
        val viewModel = createViewModel()

        viewModel.updateViewMode(com.mpx.player.ui.screens.local.ViewMode.GRID)

        assertEquals("GRID", settingsRepo.stored.musicViewMode)
        assertEquals("GRID", settingsRepo.stored.localViewMode)
        assertEquals(com.mpx.player.ui.screens.local.ViewMode.GRID, viewModel.viewMode.value)
    }

    @Test
    fun `loadMoreHistory pages the history dialog`() = runTest {
        setTestMainDispatcher()
        persistence.historyTotal = 250
        val viewModel = createViewModel()

        assertEquals(100, viewModel.historyLimit.value)
        viewModel.loadMoreHistory()
        assertEquals(200, viewModel.historyLimit.value)
    }

    /** Fake media repository: canned library lists the test controls. */
    private class FakeMediaRepository : MediaRepository {
        var tracks: List<AudioTrack> = listOf(
            track("1", "Alpha Song", "Beatles", "Abbey", "/storage/emulated/0/Music/alpha.mp3"),
            track("2", "Beta Song", "Stones", "Exile", "/storage/emulated/0/Music/beta.mp3"),
            track("3", "Gamma", "Beatles", "Secret Album", "/storage/emulated/0/Music/Secret/gamma.mp3"),
        )
        var albums: List<AudioAlbum> = listOf(
            AudioAlbum(id = "al1", title = "Abbey", artist = "Beatles", trackCount = 2),
            AudioAlbum(id = "al2", title = "Exile", artist = "Stones", trackCount = 1),
            AudioAlbum(id = "al3", title = "Secret Album", artist = "Beatles", trackCount = 1),
        )
        var artists: List<AudioArtist> = listOf(
            AudioArtist(id = "ar1", name = "Beatles", trackCount = 3, albumCount = 2),
            AudioArtist(id = "ar2", name = "Stones", trackCount = 1, albumCount = 1),
        )
        var folders: List<AudioFolder> = listOf(
            AudioFolder(id = "f1", name = "Music", path = "/storage/emulated/0/Music", trackCount = 2),
            AudioFolder(id = "f2", name = "Secret", path = "/storage/emulated/0/Music/Secret", trackCount = 1),
        )
        var playlists: List<Playlist> = listOf(
            Playlist(id = "5", title = "Device Mix", itemCount = 2),
        )

        override fun getMediaFolders(): List<com.mpx.player.domain.model.MediaFolder> = emptyList()
        override fun getAudioTracks(): List<AudioTrack> = tracks
        override fun getPlaylists(): List<Playlist> = playlists
        override fun getPlaylistTracks(playlistId: String): List<AudioTrack> = tracks
        override fun getAudioAlbums(): List<AudioAlbum> = albums
        override fun getAudioArtists(): List<AudioArtist> = artists
        override fun getAudioFolders(): List<AudioFolder> = folders

        private fun track(
            id: String,
            title: String,
            artist: String,
            album: String,
            path: String,
        ) = AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationFormatted = "0:01",
            durationMs = 1_000L,
            path = path,
        )
    }

    /** Fake persistence repository: in-memory favourites, playlists and
     * history; everything else is a no-op or an empty result. */
    private class FakeMediaPersistenceRepository : MediaPersistenceRepository {
        val favourites = MutableStateFlow<List<FavouriteItem>>(emptyList())
        val customPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
        val historyItems = MutableStateFlow<List<PlaybackHistoryItem>>(emptyList())
        var historyTotal = 0
        var playlistItems: Map<Long, List<PlaylistItem>> = emptyMap()
        private var nextPlaylistId = 1L

        override fun observeFavourites(): Flow<List<FavouriteItem>> = favourites.asStateFlow()
        override suspend fun getFavourites(): List<FavouriteItem> = favourites.value
        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> =
            flowOf(favourites.value.any { it.mediaUri == mediaUri })

        override suspend fun isFavourite(mediaUri: String): Boolean =
            favourites.value.any { it.mediaUri == mediaUri }

        override suspend fun addFavourite(item: FavouriteItem) {
            if (favourites.value.none { it.mediaUri == item.mediaUri }) {
                favourites.value += item
            }
        }

        override suspend fun removeFavourite(mediaUri: String) {
            favourites.value = favourites.value.filter { it.mediaUri != mediaUri }
        }

        override suspend fun toggleFavourite(item: FavouriteItem): Boolean {
            return if (favourites.value.any { it.mediaUri == item.mediaUri }) {
                removeFavourite(item.mediaUri)
                false
            } else {
                addFavourite(item)
                true
            }
        }

        override suspend fun clearFavourites() {
            favourites.value = emptyList()
        }

        override fun observeHistory(limit: Int, offset: Int): Flow<List<PlaybackHistoryItem>> =
            historyItems.asStateFlow()

        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> =
            historyItems.value

        override fun observeHistoryCount(): Flow<Int> = flowOf(historyTotal)
        override suspend fun recordHistory(item: PlaybackHistoryItem) {
            historyItems.value += item
        }

        override suspend fun deleteHistoryItem(mediaUri: String) {
            historyItems.value = historyItems.value.filter { it.mediaUri != mediaUri }
        }

        override suspend fun clearHistory() {
            historyItems.value = emptyList()
        }

        override fun observePlaylists(): Flow<List<CustomPlaylist>> = customPlaylists.asStateFlow()
        override suspend fun getPlaylists(): List<CustomPlaylist> = customPlaylists.value
        override suspend fun getPlaylistById(id: Long): CustomPlaylist? =
            customPlaylists.value.firstOrNull { it.id == id }

        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long {
            val id = nextPlaylistId++
            customPlaylists.value += CustomPlaylist(id = id, name = name, isVideoPlaylist = isVideoPlaylist)
            return id
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String) {
            customPlaylists.value = customPlaylists.value.map {
                if (it.id == playlistId) it.copy(name = newName) else it
            }
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            customPlaylists.value = customPlaylists.value.filter { it.id != playlistId }
        }

        override suspend fun clearPlaylists() {
            customPlaylists.value = emptyList()
        }

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
            flowOf(playlistItems[playlistId].orEmpty())

        override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> =
            playlistItems[playlistId].orEmpty()

        override suspend fun addItemToPlaylist(item: PlaylistItem): Long {
            val items = playlistItems[item.playlistId].orEmpty()
            if (items.any { it.mediaUri == item.mediaUri }) return -1L
            playlistItems = playlistItems + (item.playlistId to (items + item))
            return 1L
        }

        override suspend fun removeItemFromPlaylist(playlistItemId: Long) = Unit
        override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) = Unit
        override suspend fun clearPlaylistItems(playlistId: Long) {
            playlistItems = playlistItems - playlistId
        }

        override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> = flowOf(emptyList())
        override suspend fun getAllResumePositions(): List<PlaybackResumePosition> = emptyList()
        override suspend fun getResumePosition(mediaId: String): Long? = null
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

    /** Fake settings repository: an in-memory stored value. */
    private class FakeSettingsRepository(
        var stored: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        override fun load(): AppSettings = stored

        override fun save(settings: AppSettings, commit: Boolean) {
            stored = settings
        }
    }
}
