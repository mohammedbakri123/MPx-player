package com.mpx.player.ui.screens.settings

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
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakeSettingsRepository
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
 * tested through fakes only — a fake [AppSettingsRepository], a
 * fake [MediaPersistenceRepository], and a fake [MediaRepository] —
 * no mocking framework, no Android runtime. The persistence fake
 * only holds real data for the four count queries the diagnostics
 * section reads; every other method returns an empty default,
 * because the ViewModel never calls it. The media fake only holds
 * real data for the two folder queries the blacklist add dialog's
 * scan reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var persistence: FakeMediaPersistenceRepository
    private lateinit var mediaRepository: FakeMediaRepository

    @Before
    fun setUp() {
        settingsRepo = FakeSettingsRepository()
        persistence = FakeMediaPersistenceRepository()
        mediaRepository = FakeMediaRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(settingsRepo, persistence, mediaRepository)

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
    fun `loadBlacklistFolderOptions merges video and audio, dedupes, excludes blacklisted, sorts`() = runTest {
        mediaRepository.videoFolderList = listOf(
            MediaFolder(id = "1", name = "Movies", path = "/sdcard/Movies", videoCount = 3, totalSizeFormatted = "1 GB"),
            MediaFolder(id = "2", name = "clips", path = "/sdcard/Clips", videoCount = 1, totalSizeFormatted = "10 MB"),
            MediaFolder(id = "3", name = "ClipsSub", path = "/sdcard/Clips/Sub", videoCount = 1, totalSizeFormatted = "1 MB"),
            MediaFolder(id = "4", name = "Zebra", path = "/sdcard/Zebra", videoCount = 2, totalSizeFormatted = "5 MB"),
            MediaFolder(id = "5", name = "apple", path = "/sdcard/Apple", videoCount = 1, totalSizeFormatted = "1 MB"),
        )
        mediaRepository.audioFolderList = listOf(
            AudioFolder(id = "a1", name = "Music", path = "/sdcard/Music", trackCount = 5),
            // Same path as the video folder, different name — the
            // video entry wins (first occurrence) and the path
            // appears once.
            AudioFolder(id = "a2", name = "MoviesAudio", path = "/sdcard/Movies", trackCount = 4),
            AudioFolder(id = "a3", name = "Tunes", path = "/sdcard/Tunes", trackCount = 2),
            AudioFolder(id = "a4", name = "Banana", path = "/sdcard/Banana", trackCount = 1),
        )
        val viewModel = createViewModel()

        val options = viewModel.loadBlacklistFolderOptions(setOf("/sdcard/Clips"))

        // Video + audio merged, /sdcard/Movies once with the video
        // name, /sdcard/Clips and its subfolder excluded, sorted by
        // name lowercase — "apple" before "Banana" proves the
        // lowercase sort (a plain sort would put "Banana" first).
        assertEquals(
            listOf(
                BlacklistFolderOption(path = "/sdcard/Apple", name = "apple"),
                BlacklistFolderOption(path = "/sdcard/Banana", name = "Banana"),
                BlacklistFolderOption(path = "/sdcard/Movies", name = "Movies"),
                BlacklistFolderOption(path = "/sdcard/Music", name = "Music"),
                BlacklistFolderOption(path = "/sdcard/Tunes", name = "Tunes"),
                BlacklistFolderOption(path = "/sdcard/Zebra", name = "Zebra"),
            ),
            options,
        )
        // The scan asks for every folder, not just the visible ones.
        assertTrue(mediaRepository.showAllFoldersRequested)
    }

    @Test
    fun `loadDiagnosticsMediaCounts reads volumes and counts, skips without permission`() = runTest {
        mediaRepository.videoFolderList = listOf(
            MediaFolder(id = "1", name = "Movies", path = "/sdcard/Movies", videoCount = 3, totalSizeFormatted = "1 GB"),
            MediaFolder(id = "2", name = "Clips", path = "/sdcard/Clips", videoCount = 2, totalSizeFormatted = "5 MB"),
        )
        mediaRepository.audioTrackList = listOf(
            AudioTrack(id = "t1", title = "Song", artist = "A", album = "B", durationFormatted = "1:00", durationMs = 1000L, path = "/sdcard/Music/s.mp3"),
            AudioTrack(id = "t2", title = "Song 2", artist = "A", album = "B", durationFormatted = "2:00", durationMs = 2000L, path = "/sdcard/Music/s2.mp3"),
        )
        mediaRepository.audioFolderList = listOf(
            AudioFolder(id = "a1", name = "Music", path = "/sdcard/Music", trackCount = 2),
        )
        mediaRepository.audioAlbumList = listOf(
            AudioAlbum(id = "al1", title = "B", artist = "A", trackCount = 2),
        )
        mediaRepository.audioArtistList = listOf(
            AudioArtist(id = "ar1", name = "A", trackCount = 2, albumCount = 1),
        )
        mediaRepository.playlistList = listOf(
            Playlist(id = "p1", title = "Mix", itemCount = 2),
        )
        mediaRepository.volumeNames = setOf("primary", "1234-5678")
        val viewModel = createViewModel()

        val counts = viewModel.loadDiagnosticsMediaCounts(
            hasVideoPermission = true,
            hasAudioPermission = true,
        )

        assertEquals("primary, 1234-5678", counts.volumes)
        assertEquals(2, counts.videoFolders)
        assertEquals(5, counts.videoCount)
        assertEquals(2, counts.audioTracks)
        assertEquals(1, counts.audioFolders)
        assertEquals(1, counts.audioAlbums)
        assertEquals(1, counts.audioArtists)
        assertEquals(1, counts.playlists)

        // Without permission the MediaStore reads are skipped, not failed.
        val denied = viewModel.loadDiagnosticsMediaCounts(
            hasVideoPermission = false,
            hasAudioPermission = false,
        )
        assertEquals(0, denied.videoFolders)
        assertEquals(0, denied.audioTracks)
        // The volumes string does not need the permission.
        assertEquals("primary, 1234-5678", denied.volumes)

        // A failing volumes read falls back to "primary (internal)",
        // exactly as the screen's old inline scan did.
        mediaRepository.volumeNamesThrow = true
        val fallback = viewModel.loadDiagnosticsMediaCounts(
            hasVideoPermission = true,
            hasAudioPermission = true,
        )
        assertEquals("primary (internal)", fallback.volumes)
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
        persistence.favourites.value = listOf(
            FavouriteItem(mediaUri = "content://v/1", mediaId = "1", title = "Video 1", isVideo = true),
            FavouriteItem(mediaUri = "content://v/2", mediaId = "2", title = "Video 2", isVideo = true),
        )
        persistence.historyCount.value = 7
        persistence.playlists.value = listOf(CustomPlaylist(id = 1, name = "Mix"))
        persistence.resumePositions = listOf(
            PlaybackResumePosition(mediaId = "content://v/1", mediaUri = "content://v/1", positionMs = 1000L),
        )
        val viewModel = createViewModel()

        assertEquals(listOf(2, 7, 1, 1), viewModel.loadRecordCounts())
    }


    /** Fake media repository: only the two folder queries the
     * blacklist add dialog's scan reads hold real data. Every other
     * method returns an empty default — the ViewModel never calls
     * it. */
    private class FakeMediaRepository : MediaRepository {
        // Named *FolderList, not *Folders: a `var audioFolders`
        // property would generate a getAudioFolders() getter that
        // clashes on the JVM with the interface method of the same
        // name (both non-suspend).
        var videoFolderList: List<MediaFolder> = emptyList()
        var audioFolderList: List<AudioFolder> = emptyList()
        var audioTrackList: List<AudioTrack> = emptyList()
        var audioAlbumList: List<AudioAlbum> = emptyList()
        var audioArtistList: List<AudioArtist> = emptyList()
        var playlistList: List<Playlist> = emptyList()
        var volumeNames: Set<String> = emptySet()
        var volumeNamesThrow: Boolean = false
        var showAllFoldersRequested: Boolean = false

        override fun getMediaFolders(): List<MediaFolder> = videoFolderList

        override fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> {
            showAllFoldersRequested = showAllFolders
            return videoFolderList
        }

        override fun getAudioTracks(): List<AudioTrack> = audioTrackList

        override fun getPlaylists(): List<Playlist> = playlistList

        override fun getAudioAlbums(): List<AudioAlbum> = audioAlbumList

        override fun getAudioArtists(): List<AudioArtist> = audioArtistList

        override fun getAudioFolders(): List<AudioFolder> = audioFolderList

        override fun getExternalVolumeNames(): Set<String> {
            if (volumeNamesThrow) throw IllegalStateException("volumes failed")
            return volumeNames
        }
    }
}
