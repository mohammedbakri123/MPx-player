package com.mpx.player.ui.screens.settings

import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Migration Phase 2e (Settings follow-up): SettingsViewModel is
 * tested through fakes only — a fake [AppSettingsRepository] and a
 * fake [MediaPersistenceRepository] — no mocking framework, no
 * Android runtime. The persistence fake only holds real data for
 * the four count queries the diagnostics section reads; every other
 * method returns an empty default, because the ViewModel never
 * calls it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var persistence: FakeMediaPersistenceRepository

    @Before
    fun setUp() {
        settingsRepo = FakeSettingsRepository()
        persistence = FakeMediaPersistenceRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(settingsRepo, persistence)

    /** viewModelScope runs on Dispatchers.Main; the unconfined test
     * dispatcher executes the ViewModel's collectors eagerly, so
     * StateFlow values are observable without advancing a scheduler. */
    private fun setTestMainDispatcher(): Unit =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    @Test
    fun `updateSettings persists and publishes`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.updateSettings(viewModel.settings.value.copy(appLanguage = "ar"))

        assertEquals("ar", settingsRepo.stored.appLanguage)
        assertEquals("ar", viewModel.settings.value.appLanguage)
    }

    @Test
    fun `reloadSettings picks up changes made in another destination`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertEquals("system", viewModel.settings.value.appLanguage)

        // Language changed elsewhere (the Overlay / Advanced screen
        // while Settings sat in the back stack) — issue #270.
        settingsRepo.stored = settingsRepo.stored.copy(appLanguage = "fr")
        viewModel.reloadSettings()

        assertEquals("fr", viewModel.settings.value.appLanguage)
    }

    @Test
    fun `loadSettingsNow reads fresh from the repository`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        settingsRepo.stored = settingsRepo.stored.copy(decoderMode = "SW")

        assertEquals("SW", viewModel.loadSettingsNow().decoderMode)
        // The published flow is untouched until reloadSettings runs.
        assertEquals("HW", viewModel.settings.value.decoderMode)
    }

    @Test
    fun `activeLanguage prefers the external language, falls back to settings`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = settingsRepo.stored.copy(appLanguage = "ar")
        val viewModel = createViewModel()

        assertEquals("fr", viewModel.activeLanguage("fr"))
        assertEquals("ar", viewModel.activeLanguage(""))
        assertEquals("ar", viewModel.activeLanguage("   "))
    }

    @Test
    fun `toggleMusicTab blocks disabling the last visible tab`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = settingsRepo.stored.copy(
            showMusicTracksTab = true,
            showMusicPlaylistsTab = false,
            showMusicAlbumsTab = false,
            showMusicArtistsTab = false,
            showMusicFoldersTab = false,
        )
        val viewModel = createViewModel()

        assertFalse(viewModel.toggleMusicTab("tracks", true))
        assertTrue(viewModel.settings.value.showMusicTracksTab)
        assertEquals(settingsRepo.stored, viewModel.settings.value)
    }

    @Test
    fun `toggleMusicTab toggles when more than one tab is visible`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertEquals(5, viewModel.settings.value.visibleMusicTabsCount)

        assertTrue(viewModel.toggleMusicTab("albums", true))

        assertFalse(viewModel.settings.value.showMusicAlbumsTab)
        assertFalse(settingsRepo.stored.showMusicAlbumsTab)
        assertEquals(4, viewModel.settings.value.visibleMusicTabsCount)

        // Enabling a hidden tab always succeeds.
        assertTrue(viewModel.toggleMusicTab("albums", false))
        assertTrue(viewModel.settings.value.showMusicAlbumsTab)
    }

    @Test
    fun `handleBack consumes search first, then the category, then leaves`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        assertFalse(viewModel.handleBack())

        viewModel.setSearching(true)
        viewModel.selectCategory("player")
        assertTrue(viewModel.handleBack())
        assertFalse(viewModel.isSearching.value)
        // The category is still selected — search closed first (#124).
        assertEquals("player", viewModel.selectedCategoryId.value)

        assertTrue(viewModel.handleBack())
        assertEquals(null, viewModel.selectedCategoryId.value)

        assertFalse(viewModel.handleBack())
    }

    @Test
    fun `search query is stored and cleared`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.setSearchQuery("dark")
        assertEquals("dark", viewModel.searchQuery.value)

        viewModel.setSearchQuery("")
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `menu layout starts at the default order with nothing hidden`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        assertEquals(PLAYER_MENU_IDS, viewModel.visibleMenuItems.value)
        assertTrue(viewModel.hiddenMenuItems.value.isEmpty())
    }

    @Test
    fun `hideMenuItem removes the row and records it hidden`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.hideMenuItem("pip")

        assertFalse(viewModel.visibleMenuItems.value.contains("pip"))
        assertEquals(listOf("pip"), viewModel.hiddenMenuItems.value)
        // #201: the hidden id is recorded, so the parse cannot
        // resurrect it — and the write commits synchronously.
        assertEquals("pip", settingsRepo.stored.hiddenPlayerMenuIds)
        assertTrue(settingsRepo.lastCommit)
    }

    @Test
    fun `showMenuItem restores a hidden row at the end`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.hideMenuItem("pip")
        viewModel.hideMenuItem("speed")

        viewModel.showMenuItem("pip")

        assertTrue(viewModel.visibleMenuItems.value.contains("pip"))
        assertEquals("pip", viewModel.visibleMenuItems.value.last())
        assertEquals(listOf("speed"), viewModel.hiddenMenuItems.value)
    }

    @Test
    fun `moveMenuItemUp and Down reorder, and stop at the ends`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        // Default order starts decoder, audio, subtitles, ...
        assertEquals(listOf("decoder", "audio", "subtitles"), viewModel.visibleMenuItems.value.take(3))

        viewModel.moveMenuItemDown("decoder")
        assertEquals(listOf("audio", "decoder", "subtitles"), viewModel.visibleMenuItems.value.take(3))

        viewModel.moveMenuItemUp("decoder")
        assertEquals(listOf("decoder", "audio", "subtitles"), viewModel.visibleMenuItems.value.take(3))

        // No-ops at the ends: the stored layout is untouched.
        val before = settingsRepo.stored.playerMenuLayout
        viewModel.moveMenuItemUp("decoder")
        viewModel.moveMenuItemDown("playlist")
        assertEquals(before, settingsRepo.stored.playerMenuLayout)
    }

    @Test
    fun `resetMenuLayout restores the default order`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.hideMenuItem("pip")
        viewModel.moveMenuItemDown("decoder")
        assertEquals(12, viewModel.visibleMenuItems.value.size)

        viewModel.resetMenuLayout()

        assertEquals(PLAYER_MENU_IDS, viewModel.visibleMenuItems.value)
        assertTrue(viewModel.hiddenMenuItems.value.isEmpty())
    }

    @Test
    fun `blacklist add remove and clear keep the list sorted`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.addBlacklistedFolders(setOf("/sdcard/Movies", "/sdcard/Clips"))
        assertEquals(
            listOf("/sdcard/Clips", "/sdcard/Movies"),
            viewModel.blacklistedFolders.value,
        )
        assertTrue(settingsRepo.lastCommit)

        // Adding an already-listed path changes nothing.
        viewModel.addBlacklistedFolders(setOf("/sdcard/Movies"))
        assertEquals(2, viewModel.blacklistedFolders.value.size)

        viewModel.removeBlacklistedFolder("/sdcard/Clips")
        assertEquals(listOf("/sdcard/Movies"), viewModel.blacklistedFolders.value)

        viewModel.clearBlacklistedFolders()
        assertTrue(viewModel.blacklistedFolders.value.isEmpty())
        assertTrue(settingsRepo.stored.blacklistedFolders.isEmpty())
    }

    @Test
    fun `updatePopupPlayPosition remembers explicit picks for RememberLast`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        // #292: picking an explicit position remembers it.
        val next = viewModel.updatePopupPlayPosition("TopLeft")
        assertEquals("TopLeft", next.popupPlayPosition)
        assertEquals("TopLeft", next.lastPopupPlayPosition)
        assertEquals("TopLeft", viewModel.settings.value.lastPopupPlayPosition)

        // Picking RememberLast itself leaves the last explicit choice
        // untouched.
        val remembered = viewModel.updatePopupPlayPosition("RememberLast")
        assertEquals("RememberLast", remembered.popupPlayPosition)
        assertEquals("TopLeft", remembered.lastPopupPlayPosition)
    }

    @Test
    fun `loadRecordCounts reads the four diagnostics counts`() = runTest {
        setTestMainDispatcher()
        persistence.favourites = listOf(
            FavouriteItem(mediaUri = "content://v/1", mediaId = "1", title = "Video 1", isVideo = true),
            FavouriteItem(mediaUri = "content://v/2", mediaId = "2", title = "Video 2", isVideo = true),
        )
        persistence.historyCount.value = 7
        persistence.playlists = listOf(CustomPlaylist(id = 1, name = "Mix"))
        persistence.resumePositions = listOf(
            PlaybackResumePosition(mediaId = "content://v/1", mediaUri = "content://v/1", positionMs = 1000L),
        )
        val viewModel = createViewModel()

        assertEquals(listOf(2, 7, 1, 1), viewModel.loadRecordCounts())
    }

    /** Fake settings repository: an in-memory stored value, recording
     * whether the last save committed synchronously. */
    private class FakeSettingsRepository(
        var stored: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        var lastCommit: Boolean = false

        override fun load(): AppSettings = stored

        override fun save(settings: AppSettings, commit: Boolean) {
            stored = settings
            lastCommit = commit
        }
    }

    /** Fake persistence repository: only the four count queries the
     * diagnostics section reads hold real data. Every other method
     * returns an empty default — the ViewModel never calls it. */
    private class FakeMediaPersistenceRepository : MediaPersistenceRepository {
        var favourites: List<FavouriteItem> = emptyList()
        val historyCount = MutableStateFlow(0)
        var playlists: List<CustomPlaylist> = emptyList()
        var resumePositions: List<PlaybackResumePosition> = emptyList()

        override fun observeFavourites(): Flow<List<FavouriteItem>> = flowOf(favourites)

        override suspend fun getFavourites(): List<FavouriteItem> = favourites

        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> = flowOf(false)

        override suspend fun isFavourite(mediaUri: String): Boolean = false

        override suspend fun addFavourite(item: FavouriteItem) = Unit

        override suspend fun removeFavourite(mediaUri: String) = Unit

        override suspend fun toggleFavourite(item: FavouriteItem): Boolean = false

        override suspend fun clearFavourites() = Unit

        override fun observeHistory(limit: Int, offset: Int): Flow<List<PlaybackHistoryItem>> =
            flowOf(emptyList())

        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> =
            emptyList()

        override fun observeHistoryCount(): Flow<Int> = historyCount

        override suspend fun recordHistory(item: PlaybackHistoryItem) = Unit

        override suspend fun deleteHistoryItem(mediaUri: String) = Unit

        override suspend fun clearHistory() = Unit

        override fun observePlaylists(): Flow<List<CustomPlaylist>> = flowOf(playlists)

        override suspend fun getPlaylists(): List<CustomPlaylist> = playlists

        override suspend fun getPlaylistById(id: Long): CustomPlaylist? = null

        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long = -1L

        override suspend fun renamePlaylist(playlistId: Long, newName: String) = Unit

        override suspend fun deletePlaylist(playlistId: Long) = Unit

        override suspend fun clearPlaylists() = Unit

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
            flowOf(emptyList())

        override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> = emptyList()

        override suspend fun addItemToPlaylist(item: PlaylistItem): Long = -1L

        override suspend fun removeItemFromPlaylist(playlistItemId: Long) = Unit

        override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) = Unit

        override suspend fun clearPlaylistItems(playlistId: Long) = Unit

        override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> =
            flowOf(resumePositions)

        override suspend fun getAllResumePositions(): List<PlaybackResumePosition> = resumePositions

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
}
