package com.mpx.player.ui.screens.me

import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakeSettingsRepository
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

    @Test
    fun `renamePlaylist and removeItemFromPlaylist when no playlist is selected handle missing selection safely`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val playlist = CustomPlaylist(id = 1, name = "Mix")
        persistence.playlists.value = listOf(playlist)
        persistence.playlistItems[1] = mutableListOf(playlistItem(11, 1, "Song A"))

        // No playlist selected
        assertEquals(null, viewModel.selectedPlaylist.value)
        viewModel.renamePlaylist(1, "Updated Mix")
        assertEquals(null, viewModel.selectedPlaylist.value)
        assertEquals("Updated Mix", persistence.playlists.value.first().name)

        viewModel.removeItemFromPlaylist(11)
        assertTrue(viewModel.playlistItems.value.isEmpty())
        assertTrue(persistence.playlistItems[1]?.isEmpty() == true)
    }

    @Test
    fun `createPlaylist throws when persistence repository fails`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        persistence.createPlaylistError = IllegalStateException("Disk write error")

        try {
            viewModel.createPlaylist("FailPlaylist", isVideoPlaylist = true)
            org.junit.Assert.fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Disk write error", e.message)
        }
    }

    // Fixed timestamps: the models default addedAt / lastPlayedAt to
    // System.currentTimeMillis(), so two helper calls would build
    // unequal instances and every list-equality assert would fail.
    private fun historyItem(n: Int) = PlaybackHistoryItem(
        mediaUri = "content://v/$n",
        mediaId = "$n",
        title = "Video $n",
        isVideo = true,
        lastPlayedAt = 0L,
    )

    private fun playlistItem(id: Long, playlistId: Long, title: String) = PlaylistItem(
        id = id,
        playlistId = playlistId,
        mediaUri = "content://s/$id",
        mediaId = "$id",
        title = title,
        addedAt = 0L,
    )

}
