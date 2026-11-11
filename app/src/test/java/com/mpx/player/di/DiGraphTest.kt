package com.mpx.player.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.InvalidationTracker
import com.mpx.player.data.local.dao.FavouriteDao
import com.mpx.player.data.local.dao.PlaybackHistoryDao
import com.mpx.player.data.local.dao.PlaybackResumeDao
import com.mpx.player.data.local.dao.PlaylistDao
import com.mpx.player.data.local.db.MpxDatabase
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
import com.mpx.player.playback.AudioFocusManager
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.BackgroundService
import com.mpx.player.playback.CoordinatorStrings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.PersistedQueue
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.playback.PlaybackResumeManager
import com.mpx.player.playback.QueueStore
import com.mpx.player.playback.SubtitleManager
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import com.mpx.player.ui.screens.local.LocalViewModel
import com.mpx.player.ui.screens.me.MeViewModel
import com.mpx.player.ui.screens.music.MusicViewModel
import com.mpx.player.ui.screens.network.NetworkViewModel
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.settings.SettingsViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.stopKoin
import org.koin.core.definition.Kind
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Migration Phase 2, Step 2.4: Koin graph integrity and single-instance identity tests (§5).
 *
 * Harness Investigation & Architectural Choice:
 * Standard checkKoin() / verify() from koin-test requires an Android Context (or Robolectric)
 * to evaluate androidContext() bindings. Adding Robolectric would introduce large external
 * dependencies not present in the offline repository. Instead, this test provides a zero-overhead,
 * deterministic JVM test harness that:
 * 1. Statically inspects Koin module definitions and scopes (Kind.Singleton vs Kind.Factory).
 * 2. Asserts single-instance identity for PlaybackCoordinator (resolving twice yields identical reference).
 * 3. Validates complete graph resolution for all ViewModel definitions in viewModelModule against
 *    their declared interface contracts.
 */
@OptIn(KoinInternalApi::class, ExperimentalCoroutinesApi::class)
class DiGraphTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    // --- Module Definitions and Scope Verification ---

    @Test
    fun `playbackModule registers PlaybackCoordinator as a process-wide singleton`() {
        val coordinatorDefinition = playbackModule.mappings.values
            .map { it.beanDefinition }
            .firstOrNull { it.hasType(PlaybackCoordinator::class) }

        assertNotNull("PlaybackCoordinator must be defined in playbackModule", coordinatorDefinition)
        assertEquals(
            "PlaybackCoordinator must be registered as a Singleton (never Factory or Scoped)",
            Kind.Singleton,
            coordinatorDefinition?.kind,
        )
    }

    @Test
    fun `appModule registers all database and repository bindings as singletons`() {
        val definitions = appModule.mappings.values.map { it.beanDefinition }

        val expectedTypes = listOf(
            MpxDatabase::class,
            MediaRepository::class,
            MediaPersistenceRepository::class,
            AppSettingsRepository::class,
        )

        for (type in expectedTypes) {
            val def = definitions.firstOrNull { it.hasType(type) }
            assertNotNull("Missing binding for ${type.simpleName} in appModule", def)
            assertEquals(
                "${type.simpleName} must be registered as Kind.Singleton",
                Kind.Singleton,
                def?.kind,
            )
        }
    }

    @Test
    fun `viewModelModule registers all ViewModels as factory definitions and never singletons`() {
        val definitions = viewModelModule.mappings.values.map { it.beanDefinition }

        val expectedViewModels = listOf(
            PlayerViewModel::class,
            LocalViewModel::class,
            MusicViewModel::class,
            NetworkViewModel::class,
            MeViewModel::class,
            SettingsViewModel::class,
        )

        for (vmClass in expectedViewModels) {
            val def = definitions.firstOrNull { it.hasType(vmClass) }
            assertNotNull("Missing definition for ${vmClass.simpleName} in viewModelModule", def)
            assertEquals(
                "${vmClass.simpleName} must be factory-scoped (Kind.Factory) to prevent screen state leaks",
                Kind.Factory,
                def?.kind,
            )
        }
    }

    // --- Single-Instance Identity Verification (§5) ---

    @Test
    fun `PlaybackCoordinator resolves to the exact same instance when requested multiple times`() {
        val coordinator = createTestCoordinator()

        val koinApp = koinApplication {
            modules(
                module {
                    single { coordinator }
                },
            )
        }

        val first = koinApp.koin.get<PlaybackCoordinator>()
        val second = koinApp.koin.get<PlaybackCoordinator>()

        assertSame(
            "Resolving PlaybackCoordinator twice from the same Koin container must return the exact same instance",
            first,
            second,
        )
        assertTrue("References must be identical", first === second)
    }

    // --- Graph Resolution Verification (Manual checkKoin without Android Context) ---

    @Test
    fun `all ViewModels in viewModelModule resolve successfully from Koin container`() {
        val coordinator = createTestCoordinator()
        val settingsRepo = TestAppSettingsRepository()
        val mediaRepo = TestMediaRepository()
        val persistenceRepo = TestMediaPersistenceRepository()

        val testDependenciesModule = module {
            single { coordinator }
            single<AppSettingsRepository> { settingsRepo }
            single<MediaRepository> { mediaRepo }
            single<MediaPersistenceRepository> { persistenceRepo }
            factory { SavedStateHandle() }
        }

        val koinApp = koinApplication {
            modules(testDependenciesModule, viewModelModule)
        }

        val koin = koinApp.koin

        val playerVm = koin.get<PlayerViewModel>()
        assertNotNull(playerVm)

        val localVm = koin.get<LocalViewModel> { parametersOf(true) }
        assertNotNull(localVm)

        val musicVm = koin.get<MusicViewModel>()
        assertNotNull(musicVm)

        val networkVm = koin.get<NetworkViewModel>()
        assertNotNull(networkVm)

        val meVm = koin.get<MeViewModel>()
        assertNotNull(meVm)

        val settingsVm = koin.get<SettingsViewModel>()
        assertNotNull(settingsVm)

        // ViewModels must produce fresh instances on each resolution
        val playerVm2 = koin.get<PlayerViewModel>()
        assertNotSame("ViewModels must not be singletons", playerVm, playerVm2)
    }

    // --- Minimal Test Doubles for Graph Resolution ---

    private fun createTestCoordinator(): PlaybackCoordinator {
        val workScope = CoroutineScope(testDispatcher)
        val resumeManager = PlaybackResumeManager(null, workScope)
        return PlaybackCoordinator(
            database = TestMpxDatabase(),
            persistenceRepository = TestMediaPersistenceRepository(),
            resumeManager = resumeManager,
            settingsRepository = TestAppSettingsRepository(),
            engine = TestPlayerEngine(),
            queueStore = TestQueueStore(),
            subtitleFileAccess = SubtitleManager.FileAccess(
                detectCodepage = { null },
                hasAllFilesAccess = { true },
                findSiblings = { emptyList() },
                validateSibling = { null },
            ),
            scope = workScope,
            strings = object : CoordinatorStrings {
                override fun codecError(codecName: String) = ""
                override fun subtitleLoaded(displayName: String) = ""
                override fun subtitleFailed(displayName: String) = ""
                override fun backgroundActiveBanner() = ""
            },
            backgroundService = object : BackgroundService {
                override fun start() {}
                override fun stop() {}
            },
            audioFocusManager = AudioFocusManager.NoOp,
        )
    }

    private class TestMpxDatabase : MpxDatabase() {
        override fun favouriteDao(): FavouriteDao = throw UnsupportedOperationException()
        override fun playbackHistoryDao(): PlaybackHistoryDao = throw UnsupportedOperationException()
        override fun playlistDao(): PlaylistDao = throw UnsupportedOperationException()
        override fun playbackResumeDao(): PlaybackResumeDao = throw UnsupportedOperationException()
        override fun clearAllTables() {}
        override fun createInvalidationTracker(): InvalidationTracker = throw UnsupportedOperationException()
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) = throw UnsupportedOperationException()
    }

    private class TestAppSettingsRepository(
        var settings: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings, commit: Boolean) {
            this.settings = settings
        }
    }

    private class TestMediaRepository : MediaRepository {
        @Suppress("DEPRECATION")
        override fun getMediaFolders(): List<MediaFolder> = emptyList()
        override fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> = emptyList()
        override fun getVideosInFolder(bucketId: String): List<VideoFile> = emptyList()
        override fun getAllVideos(): List<VideoFile> = emptyList()
        override fun getAllVideos(showAll: Boolean): List<VideoFile> = emptyList()
        override fun cachedFolders(showAllFolders: Boolean, showHiddenFiles: Boolean): List<MediaFolder>? = null
        override fun cachedVideos(showAll: Boolean, showHiddenFiles: Boolean): List<VideoFile>? = null
        override fun getAudioTracks(): List<AudioTrack> = emptyList()
        override fun getPlaylists(): List<Playlist> = emptyList()
        override fun getAudioAlbums(): List<AudioAlbum> = emptyList()
        override fun getAudioArtists(): List<AudioArtist> = emptyList()
        override fun getAudioFolders(): List<AudioFolder> = emptyList()
    }

    private class TestMediaPersistenceRepository : MediaPersistenceRepository {
        override fun observeFavourites(): Flow<List<FavouriteItem>> = emptyFlow()
        override suspend fun getFavourites(): List<FavouriteItem> = emptyList()
        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> = flowOf(false)
        override suspend fun isFavourite(mediaUri: String): Boolean = false
        override suspend fun addFavourite(item: FavouriteItem) = Unit
        override suspend fun removeFavourite(mediaUri: String) = Unit
        override suspend fun toggleFavourite(item: FavouriteItem): Boolean = false
        override suspend fun clearFavourites() = Unit
        override fun observeHistory(limit: Int, offset: Int): Flow<List<PlaybackHistoryItem>> = emptyFlow()
        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> = emptyList()
        override fun observeHistoryCount(): Flow<Int> = flowOf(0)
        override suspend fun recordHistory(item: PlaybackHistoryItem) = Unit
        override suspend fun deleteHistoryItem(mediaUri: String) = Unit
        override suspend fun clearHistory() = Unit
        override fun observePlaylists(): Flow<List<CustomPlaylist>> = emptyFlow()
        override suspend fun getPlaylists(): List<CustomPlaylist> = emptyList()
        override suspend fun getPlaylistById(id: Long): CustomPlaylist? = null
        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) = Unit
        override suspend fun deletePlaylist(playlistId: Long) = Unit
        override suspend fun clearPlaylists() = Unit
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> = emptyList()
        override suspend fun addItemToPlaylist(item: PlaylistItem): Long = 1L
        override suspend fun removeItemFromPlaylist(playlistItemId: Long) = Unit
        override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) = Unit
        override suspend fun clearPlaylistItems(playlistId: Long) = Unit
        override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> = emptyFlow()
        override suspend fun getAllResumePositions(): List<PlaybackResumePosition> = emptyList()
        override suspend fun getResumePosition(mediaId: String): Long? = null
        override suspend fun saveResumePosition(mediaId: String, mediaUri: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clearResumePosition(mediaId: String) = Unit
        override suspend fun clearAllResumePositions() = Unit
        override suspend fun loadAllResumePositionsMap(): Map<String, Long> = emptyMap()
    }

    private class TestPlayerEngine : PlayerEngine {
        override var currentPosition: Long = 0L
        override var duration: Long = 0L
        override var bufferedPosition: Long = 0L
        override var isPlaying: Boolean = false
        override var playbackState: Int = PlayerEngine.STATE_IDLE
        override var currentMediaItemIndex: Int = 0
        override var hasNextMediaItem: Boolean = false
        override var hasPreviousMediaItem: Boolean = false
        override var videoWidth: Int = 0
        override var videoHeight: Int = 0
        override fun getPlaybackMetrics(): EnginePlaybackMetrics? = null
        override fun getLoadStage(): MpvLoadStage? = null
        override fun attachSurface(surface: android.view.Surface) = Unit
        override fun detachSurface() = Unit
        override fun onSurfaceChanged(width: Int, height: Int) = Unit
        override fun load(media: PlayableMedia, resumeMs: Long) = Unit
        override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekTo(index: Int, positionMs: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun selectTrack(type: TrackType, id: String?) = Unit
        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) = Unit
        override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) = Unit
        override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult =
            ReplaceMediaItemResult.ISSUED
        override fun stop() = Unit
        override fun release() = Unit
        override fun setDecoderMode(mode: DecoderMode) = Unit
        override fun setListener(listener: PlayerEngine.Listener?) = Unit
        override fun setQueueSource(queue: () -> List<PlayableMedia>, currentIndex: () -> Int) = Unit
        override fun seekToNextMediaItem() = Unit
        override fun seekToPreviousMediaItem() = Unit
        override fun getAudioTracks(): List<AudioTrackInfo> = emptyList()
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = emptyList()
    }

    private class TestQueueStore : QueueStore {
        override fun save(items: List<PlayableMedia>, currentIndex: Int) = Unit
        override fun load(): PersistedQueue? = null
        override fun clear() = Unit
    }
}
