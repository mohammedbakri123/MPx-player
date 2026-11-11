package com.mpx.player.ui.screens.me

import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 * Migration Phase 2e: MeViewModel is tested through fakes only — a
 * fake [MediaPersistenceRepository] and a fake
 * [AppSettingsRepository] — no mocking framework, no Android
 * runtime. The fakes hold in-memory lists behind the same flows
 * Room exposes, so paging, selection, and the #269 stale-load
 * guard behave the way the real repository does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    private lateinit var persistence: FakeMediaPersistenceRepository
    private lateinit var settingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        persistence = FakeMediaPersistenceRepository()
        settingsRepo = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MeViewModel(persistence, settingsRepo)

    /** viewModelScope runs on Dispatchers.Main; the unconfined test
     * dispatcher executes the ViewModel's collectors eagerly, so
     * StateFlow values are observable without advancing a scheduler. */
    private fun setTestMainDispatcher(): Unit =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    @Test
    fun `reloadSettings picks up changes made in Settings`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertEquals("system", viewModel.settings.value.appLanguage)

        // Language changed elsewhere (the Settings screen while the
        // Me tab sat in the back stack) — issue #231.
        settingsRepo.stored = settingsRepo.stored.copy(appLanguage = "ar")
        viewModel.reloadSettings()

        assertEquals("ar", viewModel.settings.value.appLanguage)
    }

    @Test
    fun `updateLanguage persists and publishes`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.updateLanguage("fr")

        assertEquals("fr", settingsRepo.stored.appLanguage)
        assertEquals("fr", viewModel.settings.value.appLanguage)
    }

    @Test
    fun `updatePopupPlay persists enabled and controls`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.updatePopupPlay(false, "Seek10")

        assertFalse(settingsRepo.stored.popupPlayEnabled)
        assertEquals("Seek10", settingsRepo.stored.popupPlayControls)
        assertFalse(viewModel.settings.value.popupPlayEnabled)
        assertEquals("Seek10", viewModel.settings.value.popupPlayControls)
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
    fun `isResumePlaybackEnabled reads the setting fresh every time`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        assertTrue(viewModel.isResumePlaybackEnabled())

        settingsRepo.stored = settingsRepo.stored.copy(resumePlayback = false)
        assertFalse(viewModel.isResumePlaybackEnabled())
    }

    @Test
    fun `history paging loads the next page only while rows remain`() = runTest {
        setTestMainDispatcher()
        persistence.historyItems.value = (1..250).map { historyItem(it) }
        val viewModel = createViewModel()

        // First page, and the total is the real count (#265), not the
        // capped list size.
        assertEquals(100, viewModel.history.value.size)
        assertEquals(250, viewModel.historyTotal.value)

        // Not near the end: no load.
        viewModel.loadMoreHistoryIfNearEnd(false)
        assertEquals(100, viewModel.history.value.size)

        viewModel.loadMoreHistoryIfNearEnd(true)
        assertEquals(200, viewModel.history.value.size)

        viewModel.loadMoreHistoryIfNearEnd(true)
        assertEquals(250, viewModel.history.value.size)

        // All rows loaded: further near-end signals stop by themselves.
        viewModel.loadMoreHistoryIfNearEnd(true)
        assertEquals(250, viewModel.history.value.size)
    }

    @Test
    fun `favourites flow exposes the stored favourites`() = runTest {
        setTestMainDispatcher()
        persistence.favourites.value = listOf(
            FavouriteItem(mediaUri = "content://v/1", mediaId = "1", title = "Video 1", isVideo = true),
        )
        val viewModel = createViewModel()

        assertEquals(1, viewModel.favourites.value.size)
        assertEquals("Video 1", viewModel.favourites.value.first().title)

        viewModel.removeFavourite("content://v/1")
        assertTrue(viewModel.favourites.value.isEmpty())
    }

    @Test
    fun `clearHistory clears the persisted history`() = runTest {
        setTestMainDispatcher()
        persistence.historyItems.value = listOf(historyItem(1), historyItem(2))
        val viewModel = createViewModel()
        assertEquals(2, viewModel.historyTotal.value)

        viewModel.clearHistory()

        assertTrue(viewModel.history.value.isEmpty())
        assertEquals(0, viewModel.historyTotal.value)
    }

    @Test
    fun `selectPlaylist clears old items at once and loads the new ones`() = runTest {
        setTestMainDispatcher()
        val playlistA = CustomPlaylist(id = 1, name = "A")
        val playlistB = CustomPlaylist(id = 2, name = "B")
        persistence.playlists.value = listOf(playlistA, playlistB)
        persistence.playlistItems[1] = mutableListOf(playlistItem(11, 1, "Song A"))
        persistence.playlistItems[2] = mutableListOf(playlistItem(21, 2, "Song B"))
        val viewModel = createViewModel()

        viewModel.selectPlaylist(playlistA)
        assertEquals(playlistA, viewModel.selectedPlaylist.value)
        assertEquals(listOf(playlistItem(11, 1, "Song A")), viewModel.playlistItems.value)

        viewModel.selectPlaylist(playlistB)
        assertEquals(playlistB, viewModel.selectedPlaylist.value)
        assertEquals(listOf(playlistItem(21, 2, "Song B")), viewModel.playlistItems.value)
    }

    @Test
    fun `selectPlaylist stale load cannot overwrite a newer selection`() = runTest {
        setTestMainDispatcher()
        val playlistA = CustomPlaylist(id = 1, name = "A")
        val playlistB = CustomPlaylist(id = 2, name = "B")
        persistence.playlists.value = listOf(playlistA, playlistB)
        persistence.playlistItems[1] = mutableListOf(playlistItem(11, 1, "Song A"))
        persistence.playlistItems[2] = mutableListOf(playlistItem(21, 2, "Song B"))
        // A's load hangs; B's returns at once.
        val deferredA = CompletableDeferred<List<PlaylistItem>>()
        persistence.pendingLoads[1] = deferredA
        val viewModel = createViewModel()

        viewModel.selectPlaylist(playlistA)
        // Items cleared at once, even though A's load is still pending.
        assertEquals(emptyList<PlaylistItem>(), viewModel.playlistItems.value)

        viewModel.selectPlaylist(playlistB)
        assertEquals(listOf(playlistItem(21, 2, "Song B")), viewModel.playlistItems.value)

        // The slow A load finally lands — it must not overwrite B's
        // items under B's title (#269).
        deferredA.complete(listOf(playlistItem(11, 1, "Song A")))
        assertEquals(listOf(playlistItem(21, 2, "Song B")), viewModel.playlistItems.value)
        assertEquals(playlistB, viewModel.selectedPlaylist.value)
    }

    @Test
    fun `renamePlaylist updates the selected snapshot at once`() = runTest {
        setTestMainDispatcher()
        val playlist = CustomPlaylist(id = 1, name = "Old")
        persistence.playlists.value = listOf(playlist)
        val viewModel = createViewModel()
        viewModel.selectPlaylist(playlist)

        viewModel.renamePlaylist(1, "New")

        // Issue #230: the dialog title reads the selected snapshot.
        assertEquals("New", viewModel.selectedPlaylist.value?.name)
        assertEquals("New", viewModel.playlists.value.first().name)
    }

    @Test
    fun `removeItemFromPlaylist refreshes the dialog items`() = runTest {
        setTestMainDispatcher()
        val playlist = CustomPlaylist(id = 1, name = "Mix", itemCount = 2)
        persistence.playlists.value = listOf(playlist)
        persistence.playlistItems[1] = mutableListOf(
            playlistItem(11, 1, "Song A"),
            playlistItem(12, 1, "Song B"),
        )
        val viewModel = createViewModel()
        viewModel.selectPlaylist(playlist)
        assertEquals(2, viewModel.playlistItems.value.size)

        viewModel.removeItemFromPlaylist(11)

        assertEquals(listOf(playlistItem(12, 1, "Song B")), viewModel.playlistItems.value)
    }

    private fun historyItem(n: Int) = PlaybackHistoryItem(
        mediaUri = "content://v/$n",
        mediaId = "$n",
        title = "Video $n",
        isVideo = true,
    )

    private fun playlistItem(id: Long, playlistId: Long, title: String) = PlaylistItem(
        id = id,
        playlistId = playlistId,
        mediaUri = "content://s/$id",
        mediaId = "$id",
        title = title,
    )

    /** Fake settings repository: an in-memory stored value. */
    private class FakeSettingsRepository(
        var stored: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        override fun load(): AppSettings = stored

        override fun save(settings: AppSettings, commit: Boolean) {
            stored = settings
        }
    }

    /** Fake persistence repository: in-memory lists behind the same
     * flows Room exposes. Loads can be held open per playlist via
     * [pendingLoads] to exercise the #269 stale-load guard. */
    private class FakeMediaPersistenceRepository : MediaPersistenceRepository {
        val favourites = MutableStateFlow<List<FavouriteItem>>(emptyList())
        val historyItems = MutableStateFlow<List<PlaybackHistoryItem>>(emptyList())
        val playlists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
        val playlistItems = mutableMapOf<Long, MutableList<PlaylistItem>>()
        val pendingLoads = mutableMapOf<Long, CompletableDeferred<List<PlaylistItem>>>()
        private var nextPlaylistId = 100L

        override fun observeFavourites(): Flow<List<FavouriteItem>> = favourites

        override suspend fun getFavourites(): List<FavouriteItem> = favourites.value

        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> =
            favourites.map { list -> list.any { it.mediaUri == mediaUri } }

        override suspend fun isFavourite(mediaUri: String): Boolean =
            favourites.value.any { it.mediaUri == mediaUri }

        override suspend fun addFavourite(item: FavouriteItem) {
            favourites.value = favourites.value + item
        }

        override suspend fun removeFavourite(mediaUri: String) {
            favourites.value = favourites.value.filter { it.mediaUri != mediaUri }
        }

        override suspend fun toggleFavourite(item: FavouriteItem): Boolean {
            return if (isFavourite(item.mediaUri)) {
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
            historyItems.map { it.drop(offset).take(limit) }

        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> =
            historyItems.value.drop(offset).take(limit)

        override fun observeHistoryCount(): Flow<Int> =
            historyItems.map { it.size }

        override suspend fun recordHistory(item: PlaybackHistoryItem) {
            historyItems.value = historyItems.value + item
        }

        override suspend fun deleteHistoryItem(mediaUri: String) {
            historyItems.value = historyItems.value.filter { it.mediaUri != mediaUri }
        }

        override suspend fun clearHistory() {
            historyItems.value = emptyList()
        }

        override fun observePlaylists(): Flow<List<CustomPlaylist>> = playlists

        override suspend fun getPlaylists(): List<CustomPlaylist> = playlists.value

        override suspend fun getPlaylistById(id: Long): CustomPlaylist? =
            playlists.value.find { it.id == id }

        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long {
            val id = nextPlaylistId++
            playlists.value = playlists.value + CustomPlaylist(
                id = id,
                name = name,
                isVideoPlaylist = isVideoPlaylist,
            )
            playlistItems[id] = mutableListOf()
            return id
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String) {
            playlists.value = playlists.value.map {
                if (it.id == playlistId) it.copy(name = newName) else it
            }
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            playlists.value = playlists.value.filter { it.id != playlistId }
            playlistItems.remove(playlistId)
        }

        override suspend fun clearPlaylists() {
            playlists.value = emptyList()
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
            if (items.any { it.mediaUri == item.mediaUri }) return -1
            items.add(item)
            return item.id
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
            flowOf(emptyList())

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
}
