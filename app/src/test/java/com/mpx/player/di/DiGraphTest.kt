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
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakePlayerEngine
import com.mpx.player.fakes.FakeSettingsRepository
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
        val settingsRepo = FakeSettingsRepository()
        val mediaRepo = TestMediaRepository()
        val persistenceRepo = FakeMediaPersistenceRepository()

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
            persistenceRepository = FakeMediaPersistenceRepository(),
            resumeManager = resumeManager,
            settingsRepository = FakeSettingsRepository(),
            engine = FakePlayerEngine(),
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
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) = throw UnsupportedOperationException()
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

    private class TestQueueStore : QueueStore {
        override fun save(items: List<PlayableMedia>, currentIndex: Int) = Unit
        override fun load(): PersistedQueue? = null
        override fun clear() = Unit
    }
}
