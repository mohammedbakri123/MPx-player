package com.mpx.player.ui.screens.music

import androidx.lifecycle.SavedStateHandle
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
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.ui.screens.local.AddToPlaylistResult
import com.mpx.player.ui.screens.local.CreatePlaylistOutcome
import com.mpx.player.ui.screens.local.SortField
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

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = MusicViewModel(media, persistence, settingsRepo, savedStateHandle = savedStateHandle)

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
        // Abbey has 3 library tracks, one of them in the blacklisted
        // folder — its count must recompute from 3 to 2, not stay stale.
        media.tracks = listOf(
            track("1", "Alpha Song", "Beatles", "Abbey", "/storage/emulated/0/Music/alpha.mp3"),
            track("2", "Beta Song", "Stones", "Exile", "/storage/emulated/0/Music/beta.mp3"),
            track("3", "Gamma", "Beatles", "Abbey", "/storage/emulated/0/Music/Secret/gamma.mp3"),
            track("4", "Delta", "Beatles", "Abbey", "/storage/emulated/0/Music/delta.mp3"),
            track("5", "Epsilon", "Beatles", "Secret Album", "/storage/emulated/0/Music/Secret/epsilon.mp3"),
        )
        media.albums = listOf(
            AudioAlbum(id = "al1", title = "Abbey", artist = "Beatles", trackCount = 3),
            AudioAlbum(id = "al2", title = "Exile", artist = "Stones", trackCount = 1),
            AudioAlbum(id = "al3", title = "Secret Album", artist = "Beatles", trackCount = 1),
        )
        media.artists = listOf(
            AudioArtist(id = "ar1", name = "Beatles", trackCount = 3, albumCount = 1),
            AudioArtist(id = "ar2", name = "Stones", trackCount = 1, albumCount = 1),
        )
        val viewModel = createViewModel()

        viewModel.refreshLibrary(hasPermission = true)

        // The track in the blacklisted folder is hidden (issue #300).
        assertEquals(listOf("Alpha Song", "Beta Song", "Delta"), viewModel.tracks.value.map { it.title })
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
    fun `search and history state restores from SavedStateHandle verifying pop behaviour`() = runTest {
        setTestMainDispatcher()
        // Issue #255 & #326: SavedStateHandle retains search and history paging
        // state when navigating away or switching tabs
        val handle = SavedStateHandle(
            mapOf(
                "music.searchQuery" to "beethoven",
                "music.isSearching" to true,
                "music.historyLimit" to 200,
            ),
        )
        val viewModel = createViewModel(savedStateHandle = handle)

        assertEquals("beethoven", viewModel.searchQuery.value)
        assertTrue(viewModel.isSearching.value)
        assertEquals(200, viewModel.historyLimit.value)

        // Writes go back through the handle
        viewModel.setSearchQuery("bach")
        assertEquals("bach", viewModel.searchQuery.value)
        assertEquals("bach", handle.get<String>("music.searchQuery"))

        viewModel.setSearching(false)
        assertFalse(viewModel.isSearching.value)
        assertEquals(false, handle.get<Boolean>("music.isSearching"))

        viewModel.setHistoryLimit(300)
        assertEquals(300, viewModel.historyLimit.value)
        assertEquals(300, handle.get<Int>("music.historyLimit"))

        viewModel.resetSearch()
        assertEquals("", viewModel.searchQuery.value)
        assertFalse(viewModel.isSearching.value)
        assertEquals("", handle.get<String>("music.searchQuery"))
        assertEquals(false, handle.get<Boolean>("music.isSearching"))
    }

    @Test
    fun `showAddToPlaylist, dismiss, and addPendingToPlaylist manage pending audio tracks`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val t1 = track("1", "T1", "A1", "Alb1", "/storage/1.mp3")
        val t2 = track("2", "T2", "A2", "Alb2", "/storage/2.mp3")

        viewModel.showAddToPlaylist(listOf(t1, t2))
        assertTrue(viewModel.showAddToPlaylistDialog.value)
        assertEquals(listOf(t1, t2), viewModel.tracksForPlaylistAdd.value)

        val plId = persistence.createPlaylist("Classical", isVideoPlaylist = false)
        val result = viewModel.addPendingToPlaylist(plId)

        assertEquals(AddToPlaylistResult(added = 2, total = 2), result)
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.tracksForPlaylistAdd.value.isEmpty())

        val items = persistence.getPlaylistItems(plId)
        assertEquals(listOf("/storage/1.mp3", "/storage/2.mp3"), items.map { it.mediaUri })
    }

    @Test
    fun `createPlaylistAndAddPending creates audio playlist and adds pending tracks`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val t1 = track("1", "T1", "A1", "Alb1", "/storage/1.mp3")

        viewModel.showAddToPlaylist(t1)
        val outcome = viewModel.createPlaylistAndAddPending("New List", isVideo = false)

        assertEquals(CreatePlaylistOutcome.Added("New List", added = 1, total = 1), outcome)
        val pl = persistence.getPlaylists().first { it.name == "New List" }
        assertFalse(pl.isVideoPlaylist)
        assertEquals(listOf("/storage/1.mp3"), persistence.getPlaylistItems(pl.id).map { it.mediaUri })
    }

    @Test
    fun `openCreatePlaylist and dismissCreatePlaylist manage dialog flags`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val t1 = track("1", "T1", "A1", "Alb1", "/storage/1.mp3")

        viewModel.showAddToPlaylist(t1)
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        viewModel.openCreatePlaylist()
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.showCreatePlaylistDialog.value)

        // Dismissing create with pending tracks returns to add dialog (#310)
        viewModel.dismissCreatePlaylist()
        assertFalse(viewModel.showCreatePlaylistDialog.value)
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        viewModel.dismissAddToPlaylist()
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.tracksForPlaylistAdd.value.isEmpty())
    }

    @Test
    fun `createPlaylistAndAddPending returns Failed and restores add dialog when persistence repository throws`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val t1 = track("1", "T1", "A1", "Alb1", "/storage/1.mp3")

        viewModel.showAddToPlaylist(t1)
        viewModel.openCreatePlaylist()
        assertTrue(viewModel.showCreatePlaylistDialog.value)

        // Simulate repository error
        persistence.createPlaylistError = RuntimeException("I/O failure")

        val outcome = viewModel.createPlaylistAndAddPending("Favorites", isVideo = false)

        assertEquals(CreatePlaylistOutcome.Failed, outcome)
        assertFalse(viewModel.showCreatePlaylistDialog.value)
        assertTrue(viewModel.showAddToPlaylistDialog.value)
        assertEquals(listOf(t1), viewModel.tracksForPlaylistAdd.value)
    }

    @Test
    fun `addPendingToPlaylist handles insertion failure or duplicate item correctly`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val t1 = track("1", "T1", "A1", "Alb1", "/storage/1.mp3")
        val t2 = track("2", "T2", "A2", "Alb2", "/storage/2.mp3")

        val plId = persistence.createPlaylist("Rock", isVideoPlaylist = false)
        // Pre-add t1 to cause duplicate rejection
        persistence.addItemToPlaylist(
            PlaylistItem(
                playlistId = plId,
                mediaUri = t1.path,
                mediaId = t1.id,
                title = t1.title,
                isVideo = false,
                durationMs = t1.durationMs,
            )
        )

        viewModel.showAddToPlaylist(listOf(t1, t2))
        val result = viewModel.addPendingToPlaylist(plId)

        assertEquals(AddToPlaylistResult(added = 1, total = 2), result)
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.tracksForPlaylistAdd.value.isEmpty())
    }

    @Test
    fun `corrupt sort field and all tabs hidden recover to defaults`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = AppSettings(
            musicPrimarySortField = "CORRUPT_SORT_FIELD",
            showMusicTracksTab = false,
            showMusicPlaylistsTab = false,
            showMusicAlbumsTab = false,
            showMusicArtistsTab = false,
            showMusicFoldersTab = false,
        )
        val viewModel = createViewModel()

        assertEquals(SortField.NAME, viewModel.sortField.value)
        assertEquals(listOf(MusicTab.TRACKS), viewModel.visibleTabs.value)
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
        var devicePlaylists: List<Playlist> = listOf(
            Playlist(id = "5", title = "Device Mix", itemCount = 2),
        )

        override fun getMediaFolders(): List<com.mpx.player.domain.model.MediaFolder> = emptyList()
        override fun getAudioTracks(): List<AudioTrack> = tracks
        override fun getPlaylists(): List<Playlist> = devicePlaylists
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
            durationMs = 1_000L,
            path = path,
        )
    }

}
