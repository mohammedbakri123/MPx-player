package com.mpx.player.ui.screens.local

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.VideoFile
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Migration Phase 2b: the Local tab's ViewModel, tested through
 * fakes only — a fake [MediaRepository], a fake
 * [MediaPersistenceRepository] and a fake [AppSettingsRepository] —
 * no mocking framework, no Android runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalViewModelTest {

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

    /** viewModelScope runs on Dispatchers.Main; the unconfined test
     * dispatcher executes the ViewModel's collectors eagerly, so
     * StateFlow values are observable without advancing a scheduler. */
    private fun setTestMainDispatcher(): Unit =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    private fun TestScope.createViewModel(
        hasPermission: Boolean = true,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) =
        LocalViewModel(
            media,
            persistence,
            settingsRepo,
            hasPermission,
            UnconfinedTestDispatcher(testScheduler),
            savedStateHandle,
        )

    @Test
    fun `initial state comes from the warm cache`() = runTest {
        setTestMainDispatcher()
        val cachedFolders = listOf(folder("b1", "/Movies"))
        val cachedVideos = listOf(video("v1", "/Movies/a.mp4", "b1"))
        media.cachedFoldersResult = cachedFolders
        media.cachedVideosResult = cachedVideos

        val viewModel = createViewModel()

        assertEquals(cachedFolders, viewModel.folders.value)
        assertEquals(cachedVideos, viewModel.allVideos.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `initial state is empty and loading when the cache is cold`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        assertTrue(viewModel.folders.value.isEmpty())
        assertTrue(viewModel.allVideos.value.isEmpty())
        assertTrue(viewModel.isLoading.value)
    }

    @Test
    fun `refreshFolders loads, blacklists, and clears loading`() = runTest {
        setTestMainDispatcher()
        media.folders = listOf(
            folder("b1", "/Movies"),
            folder("b2", "/Movies2"),
            folder("b3", "/Secret"),
        )
        media.videos = listOf(
            video("v1", "/Movies/a.mp4", "b1"),
            video("v2", "/Movies2/b.mp4", "b2"),
            video("v3", "/Secret/c.mp4", "b3"),
            // Issue #317: a content://-pathed video resolves its parent
            // through its bucket's folder, so it is hidden too.
            video("v4", "content://media/9", "b3"),
        )
        settingsRepo.stored = AppSettings(blacklistedFolders = setOf("/Secret"))
        val viewModel = createViewModel()

        viewModel.refreshFolders()

        // #202: the blacklisted sibling /Movies2 stays — the prefix
        // rule needs the separator, not a mere name prefix.
        assertEquals(listOf("b1", "b2"), viewModel.folders.value.map { it.id })
        assertEquals(listOf("v1", "v2"), viewModel.allVideos.value.map { it.id })
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `refreshFolders with no permission clears state`() = runTest {
        setTestMainDispatcher()
        media.cachedFoldersResult = listOf(folder("b1", "/Movies"))
        media.cachedVideosResult = listOf(video("v1", "/Movies/a.mp4", "b1"))
        val viewModel = createViewModel(hasPermission = false)
        assertEquals(1, viewModel.folders.value.size)

        viewModel.refreshFolders()

        assertTrue(viewModel.folders.value.isEmpty())
        assertTrue(viewModel.allVideos.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `sorted folders follow the NAME sort setting`() = runTest {
        setTestMainDispatcher()
        media.folders = listOf(
            folder("b1", "/Zebra", "Zebra"),
            folder("b2", "/Alpha", "Alpha"),
            folder("b3", "/Mango", "Mango"),
        )
        settingsRepo.stored = AppSettings(primarySortField = "NAME")
        val viewModel = createViewModel()

        viewModel.refreshFolders()

        assertEquals(
            listOf("Alpha", "Mango", "Zebra"),
            viewModel.sortedFolders.value.map { it.name },
        )
    }

    @Test
    fun `folder search filters by title or path`() = runTest {
        setTestMainDispatcher()
        media.folderVideos = mapOf(
            "b1" to listOf(
                video("v1", "/Movies/Episode 1.mp4", "b1", "Episode 1"),
                video("v2", "/Movies/Episode 2.mp4", "b1", "Episode 2"),
                video("v3", "/Movies/Trailer.mp4", "b1", "Trailer"),
            ),
        )
        val viewModel = createViewModel()
        viewModel.openFolder(folder("b1", "/Movies"))
        assertEquals(3, viewModel.filteredFolderVideos.value.size)

        viewModel.setFolderSearchQuery("episode")
        assertEquals(
            listOf("v1", "v2"),
            viewModel.filteredFolderVideos.value.map { it.id },
        )

        // Path matches count too, not just titles.
        viewModel.setFolderSearchQuery("trailer.mp4")
        assertEquals(listOf("v3"), viewModel.filteredFolderVideos.value.map { it.id })

        viewModel.setFolderSearchQuery("")
        assertEquals(3, viewModel.filteredFolderVideos.value.size)
    }

    @Test
    fun `search state restores from the SavedStateHandle`() = runTest {
        setTestMainDispatcher()
        // Keys must match the ViewModel's local.* constants — the
        // test pins that contract: a renamed key would silently
        // stop restoring.
        val viewModel = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "local.searchQuery" to "trailer",
                    "local.folderSearchQuery" to "episode",
                    "local.isFolderSearching" to true,
                ),
            ),
        )

        assertEquals("trailer", viewModel.searchQuery.value)
        assertEquals("episode", viewModel.folderSearchQuery.value)
        assertTrue(viewModel.isFolderSearching.value)

        // Writes go back through the handle.
        viewModel.setSearchQuery("clip")
        assertEquals("clip", viewModel.searchQuery.value)

        // Opening a folder resets the folder search — and the reset
        // reaches the handle, so a later restore does not resurrect it.
        media.folderVideos = mapOf("b1" to emptyList())
        viewModel.openFolder(folder("b1", "/Movies"))
        assertEquals("", viewModel.folderSearchQuery.value)
        assertFalse(viewModel.isFolderSearching.value)
    }

    @Test
    fun `selection toggles, counts, and clears`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val folder = folder("b1", "/Movies")
        val video = video("v1", "/Movies/a.mp4", "b1")

        viewModel.toggleFolderSelection(folder)
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(1, viewModel.selectionCount.value)

        viewModel.toggleVideoSelection(video)
        assertEquals(2, viewModel.selectionCount.value)

        // Toggling again removes.
        viewModel.toggleFolderSelection(folder)
        assertEquals(1, viewModel.selectionCount.value)

        viewModel.clearSelection()
        assertFalse(viewModel.isSelectionMode.value)
        assertEquals(0, viewModel.selectionCount.value)
        assertTrue(viewModel.selectedFolderIds.value.isEmpty())
        assertTrue(viewModel.selectedVideoIds.value.isEmpty())
    }

    @Test
    fun `selectedVideosInView resolves per view`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1", "/Movies/a.mp4", "b1")
        val v2 = video("v2", "/Clips/b.mp4", "b2")
        media.videos = listOf(v1, v2)
        media.folderVideos = mapOf("b1" to listOf(v1))
        val viewModel = createViewModel()
        viewModel.refreshFolders()
        viewModel.openFolder(folder("b1", "/Movies"))
        viewModel.toggleVideoSelection(v1)
        viewModel.toggleVideoSelection(v2)

        // Root in folder mode: video selections do not resolve here.
        assertTrue(viewModel.selectedVideosInView(null, false).isEmpty())
        // Inside the folder: only the folder's own videos.
        assertEquals(listOf(v1), viewModel.selectedVideosInView(folder("b1", "/Movies"), false))
        // #165: on the search page the full list resolves.
        assertEquals(listOf(v1, v2), viewModel.selectedVideosInView(null, true))

        // File view resolves the full list — and switching to it
        // clears the selection first (#276).
        viewModel.setFileView(true)
        assertTrue(viewModel.selectedVideosInView(null, false).isEmpty())
        viewModel.toggleVideoSelection(v1)
        viewModel.toggleVideoSelection(v2)
        assertEquals(listOf(v1, v2), viewModel.selectedVideosInView(null, false))
    }

    @Test
    fun `lastPlayedIn returns the newest matching history entry`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1", "/Movies/a.mp4", "b1")
        val v2 = video("v2", "/Movies/b.mp4", "b1")
        persistence.historyFlow.value = listOf(
            PlaybackHistoryItem(mediaUri = "/Movies/b.mp4", mediaId = "v2", title = "B", isVideo = true),
            PlaybackHistoryItem(mediaUri = "/Movies/a.mp4", mediaId = "v1", title = "A", isVideo = true),
            // Audio entries never match a video list.
            PlaybackHistoryItem(mediaUri = "/Music/song.mp3", mediaId = "a1", title = "S", isVideo = false),
        )
        val viewModel = createViewModel()

        // History is newest first: v2 wins over v1.
        assertEquals(v2, viewModel.lastPlayedIn(listOf(v1, v2)))
        // When the newest is not in the list, the next match wins.
        assertEquals(v1, viewModel.lastPlayedIn(listOf(v1)))
        assertEquals(null, viewModel.lastPlayedIn(emptyList()))
    }

    @Test
    fun `reloadSettings picks up changes made outside Local`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertFalse(viewModel.settings.value.showAllFolders)

        settingsRepo.stored = settingsRepo.stored.copy(showAllFolders = true)
        viewModel.reloadSettings()

        assertTrue(viewModel.settings.value.showAllFolders)
    }

    @Test
    fun `updateViewMode persists and mirrors music mode when layouts are shared`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = AppSettings(separateViewLayout = false)
        val viewModel = createViewModel()

        viewModel.updateViewMode(ViewMode.GRID)
        assertEquals("GRID", settingsRepo.stored.localViewMode)
        assertEquals("GRID", settingsRepo.stored.musicViewMode)
        assertEquals(ViewMode.GRID, viewModel.viewMode.value)

        // With separate layouts, Music keeps its own mode.
        viewModel.updateSettings(commit = true) { it.copy(separateViewLayout = true) }
        viewModel.updateViewMode(ViewMode.LIST)
        assertEquals("LIST", settingsRepo.stored.localViewMode)
        assertEquals("GRID", settingsRepo.stored.musicViewMode)
    }

    @Test
    fun `toggleSelectionFavourite adds missing then removes all`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1", "/Movies/a.mp4", "b1")
        val v2 = video("v2", "/Movies/b.mp4", "b1")
        media.folderVideos = mapOf("b1" to listOf(v1, v2))
        val viewModel = createViewModel()
        viewModel.openFolder(folder("b1", "/Movies"))

        // Nothing selected: nothing to toggle, selection kept.
        assertEquals(
            SelectionFavouriteOutcome.EMPTY,
            viewModel.toggleSelectionFavourite(folder("b1", "/Movies"), false),
        )

        viewModel.toggleVideoSelection(v1)
        viewModel.toggleVideoSelection(v2)
        assertEquals(
            SelectionFavouriteOutcome.ADDED,
            viewModel.toggleSelectionFavourite(folder("b1", "/Movies"), false),
        )
        assertEquals(
            setOf("/Movies/a.mp4", "/Movies/b.mp4"),
            persistence.favouritesFlow.value.map { it.mediaUri }.toSet(),
        )
        assertFalse(viewModel.isSelectionMode.value)

        // Toggling the same selection again removes them all.
        viewModel.toggleVideoSelection(v1)
        viewModel.toggleVideoSelection(v2)
        assertEquals(
            SelectionFavouriteOutcome.REMOVED,
            viewModel.toggleSelectionFavourite(folder("b1", "/Movies"), false),
        )
        assertTrue(persistence.favouritesFlow.value.isEmpty())
    }

    @Test
    fun `openFolder clears selection and loads the folders videos`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1", "/Movies/a.mp4", "b1")
        media.folderVideos = mapOf("b1" to listOf(v1))
        val viewModel = createViewModel()
        viewModel.toggleFolderSelection(folder("b9", "/Other"))
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.openFolder(folder("b1", "/Movies"))

        assertFalse(viewModel.isSelectionMode.value)
        assertEquals(listOf(v1), viewModel.folderVideos.value)
        assertFalse(viewModel.isFolderLoading.value)
    }

    @Test
    fun `clearMediaStateOnPermissionLoss empties lists and selection`() = runTest {
        setTestMainDispatcher()
        media.folders = listOf(folder("b1", "/Movies"))
        media.videos = listOf(video("v1", "/Movies/a.mp4", "b1"))
        media.folderVideos = mapOf("b1" to listOf(video("v1", "/Movies/a.mp4", "b1")))
        val viewModel = createViewModel()
        viewModel.refreshFolders()
        viewModel.openFolder(folder("b1", "/Movies"))
        viewModel.toggleVideoSelection(video("v1", "/Movies/a.mp4", "b1"))

        viewModel.clearMediaStateOnPermissionLoss()

        assertTrue(viewModel.folders.value.isEmpty())
        assertTrue(viewModel.allVideos.value.isEmpty())
        assertTrue(viewModel.folderVideos.value.isEmpty())
        assertFalse(viewModel.isSelectionMode.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isFolderLoading.value)
    }

    @Test
    fun `isPathBlacklisted matches folders and subfolders but not siblings or blank entries`() {
        assertTrue(isPathBlacklisted("/Movies", setOf("/Movies")))
        assertTrue(isPathBlacklisted("/Movies/Action", setOf("/Movies")))
        // #202: a sibling whose name merely starts the same way stays.
        assertFalse(isPathBlacklisted("/Movies2", setOf("/Movies")))
        assertFalse(isPathBlacklisted("/Other", setOf("/Movies")))
        // #316: blank entries never match anything.
        assertFalse(isPathBlacklisted("/Movies", setOf("")))
        assertFalse(isPathBlacklisted("/Movies", setOf("   ")))
        assertFalse(isPathBlacklisted("/Movies", setOf("/")))
        assertFalse(isPathBlacklisted("/Movies", emptySet()))
    }

    @Test
    fun `addPendingToPlaylist counts real adds and closes the dialog`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1", "/Movies/a.mp4", "b1")
        val v2 = video("v2", "/Movies/b.mp4", "b1")
        val viewModel = createViewModel()
        viewModel.toggleVideoSelection(v1)
        viewModel.showAddToPlaylist(listOf(v1, v2))
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        val first = viewModel.addPendingToPlaylist(1L)
        assertEquals(AddToPlaylistResult(added = 2, total = 2), first)
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.videosForPlaylistAdd.value.isEmpty())
        assertFalse(viewModel.isSelectionMode.value)

        // Adding the same videos again adds nothing (#230).
        viewModel.showAddToPlaylist(listOf(v1, v2))
        val second = viewModel.addPendingToPlaylist(1L)
        assertEquals(AddToPlaylistResult(added = 0, total = 2), second)
    }

    @Test
    fun `createPlaylistAndAddPending creates, adds, and reports Created when nothing is pending`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        val created = viewModel.createPlaylistAndAddPending("My List", true)
        assertEquals(CreatePlaylistOutcome.Created("My List"), created)
        val myList = persistence.getPlaylists().first { it.name == "My List" }
        assertTrue(myList.isVideoPlaylist)

        val v1 = video("v1", "/Movies/a.mp4", "b1")
        viewModel.showAddToPlaylist(listOf(v1))
        val added = viewModel.createPlaylistAndAddPending("Second", true)
        assertEquals(CreatePlaylistOutcome.Added("Second", added = 1, total = 1), added)
        val second = persistence.getPlaylists().first { it.name == "Second" }
        assertEquals(
            listOf("/Movies/a.mp4"),
            persistence.getPlaylistItems(second.id).map { it.mediaUri },
        )
        assertFalse(viewModel.showCreatePlaylistDialog.value)
    }

    @Test
    fun `dismissAddToPlaylist drops pending videos`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        viewModel.showAddToPlaylist(listOf(video("v1", "/Movies/a.mp4", "b1")))
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        viewModel.dismissAddToPlaylist()

        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.videosForPlaylistAdd.value.isEmpty())
    }

    private fun folder(id: String, path: String, name: String = path.substringAfterLast('/')) =
        MediaFolder(
            id = id,
            name = name,
            path = path,
            videoCount = 1,
            totalSizeFormatted = "1.0 MB",
        )

    private fun video(id: String, path: String, bucketId: String = "", title: String = id) =
        VideoFile(id = id, title = title, path = path, bucketId = bucketId)

    /** Fake media repository: in-memory lists the test controls. */
    private class FakeMediaRepository : MediaRepository {
        var folders: List<MediaFolder> = emptyList()
        var videos: List<VideoFile> = emptyList()
        var folderVideos: Map<String, List<VideoFile>> = emptyMap()
        var cachedFoldersResult: List<MediaFolder>? = null
        var cachedVideosResult: List<VideoFile>? = null

        override fun getMediaFolders(): List<MediaFolder> = folders

        override fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> = folders

        override fun getVideosInFolder(bucketId: String): List<VideoFile> =
            folderVideos[bucketId] ?: emptyList()

        override fun getAllVideos(): List<VideoFile> = videos

        override fun getAllVideos(showAll: Boolean): List<VideoFile> = videos

        override fun cachedFolders(
            showAllFolders: Boolean,
            showHiddenFiles: Boolean,
        ): List<MediaFolder>? = cachedFoldersResult

        override fun cachedVideos(
            showAll: Boolean,
            showHiddenFiles: Boolean,
        ): List<VideoFile>? = cachedVideosResult

        override fun getAudioTracks(): List<AudioTrack> = emptyList()

        override fun getPlaylists(): List<Playlist> = emptyList()

        override fun getAudioAlbums(): List<AudioAlbum> = emptyList()

        override fun getAudioArtists(): List<AudioArtist> = emptyList()

        override fun getAudioFolders(): List<AudioFolder> = emptyList()
    }

    /** Fake persistence repository: in-memory flows and lists. */
    private class FakeMediaPersistenceRepository : MediaPersistenceRepository {
        val favouritesFlow = MutableStateFlow<List<FavouriteItem>>(emptyList())
        val historyFlow = MutableStateFlow<List<PlaybackHistoryItem>>(emptyList())
        val playlistsFlow = MutableStateFlow<List<CustomPlaylist>>(emptyList())
        private val playlistItems = mutableListOf<PlaylistItem>()
        private var nextRowId = 1L
        private var nextPlaylistId = 1L

        override fun observeFavourites(): Flow<List<FavouriteItem>> =
            favouritesFlow.asStateFlow()

        override suspend fun getFavourites(): List<FavouriteItem> = favouritesFlow.value

        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> =
            flowOf(favouritesFlow.value.any { it.mediaUri == mediaUri })

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
            historyFlow.asStateFlow()

        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> =
            historyFlow.value

        override fun observeHistoryCount(): Flow<Int> =
            historyFlow.map { it.size }

        override suspend fun recordHistory(item: PlaybackHistoryItem) {
            historyFlow.value = listOf(item) + historyFlow.value
        }

        override suspend fun deleteHistoryItem(mediaUri: String) {
            historyFlow.value = historyFlow.value.filter { it.mediaUri != mediaUri }
        }

        override suspend fun clearHistory() {
            historyFlow.value = emptyList()
        }

        override fun observePlaylists(): Flow<List<CustomPlaylist>> =
            playlistsFlow.asStateFlow()

        override suspend fun getPlaylists(): List<CustomPlaylist> = playlistsFlow.value

        override suspend fun getPlaylistById(id: Long): CustomPlaylist? =
            playlistsFlow.value.firstOrNull { it.id == id }

        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long {
            val id = nextPlaylistId++
            playlistsFlow.value = playlistsFlow.value +
                CustomPlaylist(id = id, name = name, isVideoPlaylist = isVideoPlaylist)
            return id
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String) {
            playlistsFlow.value = playlistsFlow.value.map {
                if (it.id == playlistId) it.copy(name = newName) else it
            }
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            playlistsFlow.value = playlistsFlow.value.filter { it.id != playlistId }
        }

        override suspend fun clearPlaylists() {
            playlistsFlow.value = emptyList()
        }

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
            flowOf(playlistItems.filter { it.playlistId == playlistId })

        override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> =
            playlistItems.filter { it.playlistId == playlistId }

        override suspend fun addItemToPlaylist(item: PlaylistItem): Long {
            if (playlistItems.any {
                    it.playlistId == item.playlistId && it.mediaUri == item.mediaUri
                }
            ) return -1L
            val rowId = nextRowId++
            playlistItems += item.copy(id = rowId)
            return rowId
        }

        override suspend fun removeItemFromPlaylist(playlistItemId: Long) {
            playlistItems.removeAll { it.id == playlistItemId }
        }

        override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) {
            playlistItems.removeAll {
                it.playlistId == playlistId && it.mediaUri == mediaUri
            }
        }

        override suspend fun clearPlaylistItems(playlistId: Long) {
            playlistItems.removeAll { it.playlistId == playlistId }
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
