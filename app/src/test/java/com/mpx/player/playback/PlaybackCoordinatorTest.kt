package com.mpx.player.playback

import android.view.Surface
import com.mpx.player.data.local.dao.FavouriteDao
import com.mpx.player.data.local.dao.PlaybackHistoryDao
import com.mpx.player.data.local.dao.PlaybackResumeDao
import com.mpx.player.data.local.dao.PlaylistDao
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.engine.EnginePlaybackMetrics
import com.mpx.player.playback.engine.MpvLoadStage
import com.mpx.player.playback.engine.MpvPlaybackException
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration Phase 3, step 4: unit tests for the extracted
 * [PlaybackCoordinator]. Tested through fakes only — a fake
 * [PlayerEngine], a fake [MediaPersistenceRepository], a fake
 * [MpxDatabase], a real [PlaybackResumeManager] over no repository,
 * and the real [PlaybackQueue] / [SubtitleManager] /
 * [EngineEventTranslator] the coordinator builds internally — no
 * mocking framework, no Android runtime.
 *
 * The coordinator's long-lived jobs (ticker, watchdog, sleep timer)
 * run in a scope derived from the test's `backgroundScope`, so they
 * are driven by virtual time (`advanceTimeBy`) and cancelled when
 * the test ends instead of blocking `runTest` from finishing. The
 * sleep-timer clock reads the test scheduler, so the timer counts
 * down in virtual time.
 *
 * What these tests pin: transport delegation to the engine, state
 * publishing (including the decoder-mode seed from settings), the
 * sleep timer and the position ticker under virtual time, the
 * preparing watchdog firing and being cancelled, stop/release, and
 * the play/pause effects through the real translator. What they
 * cannot pin, and can only be verified on device: real mpv playback
 * and event timing, the real watchdog against a stuck load, the
 * background service actually starting, the localised banner texts
 * themselves (the fake strings return markers), surface attach /
 * detach (needs a real `Surface`), `loadExternalSubtitle` through
 * the coordinator (needs a real `android.net.Uri` — the underlying
 * load path is covered by `SubtitleManagerTest`), and the mpv-only
 * engine calls behind `MpvPlayerEngine` casts
 * (`applyPlaybackTweaks`, `setVideoZoom`, `setAspectRatio` — the
 * fake engine is not an mpv engine, so only the state half is
 * pinned here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    /** Builds a coordinator with real queue/subtitle/translator pieces over fakes. */
    private suspend fun createFixture(
        scope: TestScope,
        settings: AppSettings = AppSettings(),
    ): Fixture {
        val engine = FakePlayerEngine()
        val settingsRepo = FakeSettingsRepository(settings)
        val persistence = FakeMediaPersistenceRepository()
        val database = FakeMpxDatabase()
        val store = FakeQueueStore()
        val strings = FakeCoordinatorStrings()
        val backgroundService = FakeBackgroundService()
        // Long-lived coordinator jobs run in the background scope:
        // virtual time still drives them, but they do not block
        // runTest from finishing when the test body returns.
        val workScope = CoroutineScope(scope.backgroundScope.coroutineContext)
        val resumeManager = PlaybackResumeManager(null, workScope)
        resumeManager.awaitPreload()
        val coordinator = PlaybackCoordinator(
            database = database,
            persistenceRepository = persistence,
            resumeManager = resumeManager,
            settingsRepository = settingsRepo,
            engine = engine,
            queueStore = store,
            subtitleFileAccess = SubtitleManager.FileAccess(
                detectCodepage = { null },
                hasAllFilesAccess = { true },
                findSiblings = { emptyList<File>() },
                validateSibling = { null },
            ),
            scope = workScope,
            // Unconfined: the history-record launches run eagerly, so
            // they are observable without advancing a scheduler.
            ioDispatcher = UnconfinedTestDispatcher(),
            strings = strings,
            backgroundService = backgroundService,
            clock = { scope.testScheduler.currentTime },
        )
        return Fixture(
            engine = engine,
            settingsRepo = settingsRepo,
            persistence = persistence,
            database = database,
            store = store,
            strings = strings,
            backgroundService = backgroundService,
            resumeManager = resumeManager,
            coordinator = coordinator,
        )
    }

    private class Fixture(
        val engine: FakePlayerEngine,
        val settingsRepo: FakeSettingsRepository,
        val persistence: FakeMediaPersistenceRepository,
        val database: FakeMpxDatabase,
        val store: FakeQueueStore,
        val strings: FakeCoordinatorStrings,
        val backgroundService: FakeBackgroundService,
        val resumeManager: PlaybackResumeManager,
        val coordinator: PlaybackCoordinator,
    ) {
        fun video(
            id: String,
            isVideo: Boolean = true,
            durationMs: Long = 0L,
        ) = PlayableMedia(
            id = id,
            title = "Video $id",
            uri = "content://video/$id",
            isVideo = isVideo,
            durationMs = durationMs,
        )

        /** Starts a queue session and clears the engine call logs. */
        fun playVideos(vararg ids: String, startIndex: Int = 0) {
            val items = ids.map { video(it) }
            coordinator.playQueue(items, startIndex = startIndex)
            engine.loadCalls.clear()
            engine.playCalls = 0
            engine.seekCalls.clear()
            engine.pauseCalls = 0
        }
    }

    /**
     * Fake engine: answers positions and has-next/previous from plain
     * vars; records loads, seeks, plays, pauses, stops, releases, and
     * track/decoder operations. A replace pauses playback in this fake
     * (isPlaying = false), which is what makes the subtitle manager's
     * restore-playback branch observable; [play] resumes it.
     */
    private class FakePlayerEngine : PlayerEngine {
        val loadCalls = mutableListOf<Triple<List<PlayableMedia>, Int, Long>>()
        val singleLoadCalls = mutableListOf<Pair<PlayableMedia, Long>>()
        val seekCalls = mutableListOf<Long>()
        val speedCalls = mutableListOf<Float>()
        val replaceCalls = mutableListOf<Pair<Int, PlayableMedia>>()
        val selectTrackCalls = mutableListOf<Pair<TrackType, String?>>()
        val selectAudioCalls = mutableListOf<Pair<Int, Int>>()
        val selectSubtitleCalls = mutableListOf<Pair<Int?, Int?>>()
        val decoderModeCalls = mutableListOf<DecoderMode>()
        val surfaceChangedCalls = mutableListOf<Pair<Int, Int>>()
        var replaceResult: ReplaceMediaItemResult = ReplaceMediaItemResult.ISSUED
        var audioTracksToReturn: List<AudioTrackInfo> = emptyList()
        var subtitleTracksToReturn: List<SubtitleTrackInfo> = emptyList()
        var metricsToReturn: EnginePlaybackMetrics? = null
        var loadStageToReturn: MpvLoadStage? = null
        var playCalls: Int = 0
        var pauseCalls: Int = 0
        var stopCalls: Int = 0
        var releaseCalls: Int = 0
        var capturedListener: PlayerEngine.Listener? = null
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

        override fun getPlaybackMetrics(): EnginePlaybackMetrics? = metricsToReturn
        override fun getLoadStage(): MpvLoadStage? = loadStageToReturn

        override fun attachSurface(surface: Surface) = Unit
        override fun detachSurface() = Unit
        override fun onSurfaceChanged(width: Int, height: Int) {
            surfaceChangedCalls.add(width to height)
        }
        override fun load(media: PlayableMedia, resumeMs: Long) {
            singleLoadCalls.add(media to resumeMs)
        }
        override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
            loadCalls.add(Triple(items, startIndex, resumeMs))
        }
        override fun play() {
            playCalls++
            isPlaying = true
        }
        override fun pause() {
            pauseCalls++
            isPlaying = false
        }
        override fun seekTo(positionMs: Long) {
            seekCalls.add(positionMs)
        }
        override fun seekTo(index: Int, positionMs: Long) = Unit
        override fun setSpeed(speed: Float) {
            speedCalls.add(speed)
        }
        override fun selectTrack(type: TrackType, id: String?) {
            selectTrackCalls.add(type to id)
        }
        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
            selectAudioCalls.add(groupIndex to trackIndex)
        }
        override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
            selectSubtitleCalls.add(groupIndex to trackIndex)
        }
        override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult {
            replaceCalls.add(index to media)
            isPlaying = false
            return replaceResult
        }
        override fun stop() {
            stopCalls++
        }
        override fun release() {
            releaseCalls++
        }
        override fun setDecoderMode(mode: DecoderMode) {
            decoderModeCalls.add(mode)
        }
        override fun setListener(listener: PlayerEngine.Listener?) {
            capturedListener = listener
        }
        override fun seekToNextMediaItem() = Unit
        override fun seekToPreviousMediaItem() = Unit
        override fun getAudioTracks(): List<AudioTrackInfo> = audioTracksToReturn
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracksToReturn
    }

    /** Fake settings repository: load returns [settings], save replaces it. */
    private class FakeSettingsRepository(
        var settings: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings, commit: Boolean) {
            this.settings = settings
        }
    }

    /**
     * Fake queue store: in-memory, mirroring the real store's
     * "empty means nothing persisted" rule.
     */
    private class FakeQueueStore : QueueStore {
        var saveCalls: Int = 0
        var savedItems: List<PlayableMedia> = emptyList()
        var savedIndex: Int = 0
        private var stored: PersistedQueue? = null

        override fun save(items: List<PlayableMedia>, currentIndex: Int) {
            saveCalls++
            savedItems = items
            savedIndex = currentIndex
            stored = if (items.isEmpty()) null else PersistedQueue(items, currentIndex)
        }

        override fun load(): PersistedQueue? = stored

        override fun clear() {
            stored = null
            savedItems = emptyList()
            savedIndex = 0
        }
    }

    /** Fake persistence repository: records history rows, answers nothing else. */
    private class FakeMediaPersistenceRepository : MediaPersistenceRepository {
        val historyRecords = mutableListOf<PlaybackHistoryItem>()

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
        override suspend fun recordHistory(item: PlaybackHistoryItem) {
            historyRecords.add(item)
        }
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

    /**
     * Fake database: the coordinator only holds it for the public
     * API (it builds the persistence repository in production), so
     * the DAOs are never called and throw if they ever are.
     */
    private class FakeMpxDatabase : MpxDatabase() {
        override fun favouriteDao(): FavouriteDao =
            throw UnsupportedOperationException("not used")
        override fun playbackHistoryDao(): PlaybackHistoryDao =
            throw UnsupportedOperationException("not used")
        override fun playlistDao(): PlaylistDao =
            throw UnsupportedOperationException("not used")
        override fun playbackResumeDao(): PlaybackResumeDao =
            throw UnsupportedOperationException("not used")
    }

    /** Fake strings: marker texts, so the codec branch is pinned without resources. */
    private class FakeCoordinatorStrings : CoordinatorStrings {
        override fun codecError(codecName: String): String = "codec:$codecName"
        override fun subtitleLoaded(displayName: String): String = "Loaded subtitle: $displayName"
        override fun subtitleFailed(displayName: String): String = "Failed to load subtitle: $displayName"
        override fun backgroundActiveBanner(): String = "banner:background"
    }

    /** Fake background service: records start/stop calls. */
    private class FakeBackgroundService : BackgroundService {
        var startCalls: Int = 0
        var stopCalls: Int = 0
        override fun start() {
            startCalls++
        }
        override fun stop() {
            stopCalls++
        }
    }

    @Test
    fun `initial state seeds the decoder mode from settings`() = runTest {
        val f = createFixture(this, AppSettings(decoderMode = "SW"))
        assertEquals(DecoderMode.SW, f.coordinator.playbackState.value.decoderMode)

        val g = createFixture(this, AppSettings(decoderMode = "XX"))
        assertEquals(DecoderMode.HW, g.coordinator.playbackState.value.decoderMode)
    }

    @Test
    fun `play delegates to the engine`() = runTest {
        val f = createFixture(this)

        f.coordinator.play()

        assertEquals(1, f.engine.playCalls)
        assertTrue(f.engine.seekCalls.isEmpty())
    }

    @Test
    fun `play at EOF seeks to zero first`() = runTest {
        val f = createFixture(this)
        f.coordinator.engineEventTranslator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)
        assertEquals(PlayerEngine.STATE_ENDED, f.coordinator.playbackState.value.playbackState)

        f.coordinator.play()

        assertEquals(listOf(0L), f.engine.seekCalls)
        assertEquals(1, f.engine.playCalls)
    }

    @Test
    fun `pause delegates to the engine`() = runTest {
        val f = createFixture(this)
        f.engine.isPlaying = true

        f.coordinator.pause()

        assertEquals(1, f.engine.pauseCalls)
        assertFalse(f.engine.isPlaying)
    }

    @Test
    fun `togglePlayPause pauses when playing`() = runTest {
        val f = createFixture(this)
        f.engine.isPlaying = true

        f.coordinator.togglePlayPause()

        assertEquals(1, f.engine.pauseCalls)
        assertEquals(0, f.engine.playCalls)
    }

    @Test
    fun `togglePlayPause plays when idle`() = runTest {
        val f = createFixture(this)

        f.coordinator.togglePlayPause()

        assertEquals(1, f.engine.playCalls)
        assertTrue(f.engine.seekCalls.isEmpty())
    }

    @Test
    fun `togglePlayPause at EOF seeks to zero then plays`() = runTest {
        val f = createFixture(this)
        f.coordinator.engineEventTranslator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)

        f.coordinator.togglePlayPause()

        assertEquals(listOf(0L), f.engine.seekCalls)
        assertEquals(1, f.engine.playCalls)
    }

    @Test
    fun `seekTo seeks and refreshes the position from the engine`() = runTest {
        val f = createFixture(this)
        f.engine.currentPosition = 5_000L
        f.engine.duration = 60_000L

        f.coordinator.seekTo(30_000L)

        assertEquals(listOf(30_000L), f.engine.seekCalls)
        assertEquals(5_000L, f.coordinator.playbackState.value.currentPositionMs)
        assertEquals(60_000L, f.coordinator.playbackState.value.durationMs)
    }

    @Test
    fun `skipForward and skipBackward use the engine position`() = runTest {
        val f = createFixture(this)
        f.engine.currentPosition = 30_000L

        f.coordinator.skipForward()
        f.coordinator.skipBackward()

        assertEquals(listOf(40_000L, 20_000L), f.engine.seekCalls)
    }

    @Test
    fun `setSpeed updates the engine and the state`() = runTest {
        val f = createFixture(this)

        f.coordinator.setSpeed(1.5f)

        assertEquals(listOf(1.5f), f.engine.speedCalls)
        assertEquals(1.5f, f.coordinator.playbackState.value.speed)
    }

    @Test
    fun `setDecoderMode with the same mode is a no-op`() = runTest {
        val f = createFixture(this)

        f.coordinator.setDecoderMode(DecoderMode.HW)

        assertTrue(f.engine.decoderModeCalls.isEmpty())
        assertEquals(DecoderMode.HW, f.coordinator.playbackState.value.decoderMode)
    }

    @Test
    fun `setDecoderMode changes the engine and clears the decoder note`() = runTest {
        val f = createFixture(this)
        f.coordinator.engineEventTranslator.onVideoDecoderInitialized("OMX.google.h264.decoder")
        assertEquals("OMX.google.h264.decoder", f.coordinator.playbackState.value.activeDecoderName)

        f.coordinator.setDecoderMode(DecoderMode.SW)

        assertEquals(listOf(DecoderMode.SW), f.engine.decoderModeCalls)
        assertEquals(DecoderMode.SW, f.coordinator.playbackState.value.decoderMode)
        assertNull(f.coordinator.playbackState.value.activeDecoderName)
        assertNull(f.coordinator.playbackState.value.decoderFallbackNote)
    }

    @Test
    fun `setAspectRatio updates the state`() = runTest {
        val f = createFixture(this)

        f.coordinator.setAspectRatio(AspectRatioMode.ZOOM)

        assertEquals(AspectRatioMode.ZOOM, f.coordinator.playbackState.value.aspectRatio)
    }

    @Test
    fun `surfaceChanged forwards to the engine`() = runTest {
        val f = createFixture(this)

        f.coordinator.surfaceChanged(1920, 1080)

        assertEquals(listOf(1920 to 1080), f.engine.surfaceChangedCalls)
    }

    @Test
    fun `playStream derives the title from the URL`() = runTest {
        val f = createFixture(this)

        f.coordinator.playStream("https://example.com/episode.mp4?token=1")

        val item = f.engine.loadCalls.single().first.single()
        assertEquals("episode.mp4", item.title)
        assertEquals("https://example.com/episode.mp4?token=1", item.uri)
        assertEquals("https://example.com/episode.mp4?token=1", item.subtitle)
        assertTrue(item.isVideo)
        assertEquals(1, f.engine.playCalls)
    }

    @Test
    fun `playStream with no path segment uses Network Stream`() = runTest {
        val f = createFixture(this)

        f.coordinator.playStream("https://example.com/")

        assertEquals("Network Stream", f.engine.loadCalls.single().first.single().title)
    }

    @Test
    fun `load with a resume position saves it and starts the item at it`() = runTest {
        val f = createFixture(this)
        val item = f.video("v1", durationMs = 120_000L)

        f.coordinator.load(item, resumeMs = 9_000L)

        assertEquals(9_000L, f.resumeManager.getResumePosition("v1", "content://video/v1"))
        assertEquals(listOf(Triple(listOf(item), 0, 9_000L)), f.engine.loadCalls)
        assertEquals(9_000L, f.coordinator.playbackState.value.resumedFromMs)
        assertEquals(item, f.coordinator.playbackState.value.currentItem)
    }

    @Test
    fun `playQueue publishes the session and the ticker refreshes the position`() = runTest {
        val f = createFixture(this)
        f.engine.currentPosition = 12_345L
        f.engine.duration = 60_000L
        val items = listOf(f.video("v1"), f.video("v2"))

        f.coordinator.playQueue(items)

        assertEquals(listOf(Triple(items, 0, 0L)), f.engine.loadCalls)
        assertEquals(1, f.engine.playCalls)
        assertEquals(items[0], f.coordinator.playbackState.value.currentItem)
        assertEquals(items, f.coordinator.playbackState.value.queue)
        assertTrue(f.coordinator.playbackState.value.isPreparing)
        advanceTimeBy(250)
        assertEquals(12_345L, f.coordinator.playbackState.value.currentPositionMs)
        assertEquals(60_000L, f.coordinator.playbackState.value.durationMs)
    }

    @Test
    fun `preparing watchdog fires after 20 seconds on a stuck video load`() = runTest {
        val f = createFixture(this)
        f.playVideos("v1")
        assertTrue(f.coordinator.playbackState.value.isPreparing)

        advanceTimeBy(19_999)
        assertTrue(f.coordinator.playbackState.value.isPreparing)
        assertNull(f.coordinator.playbackState.value.userNotification)

        advanceTimeBy(1)
        assertFalse(f.coordinator.playbackState.value.isPreparing)
        assertEquals(
            "Playback error: Playback load timed out after 20s (engine=MPV)",
            f.coordinator.playbackState.value.userNotification,
        )
    }

    @Test
    fun `READY cancels the preparing watchdog`() = runTest {
        val f = createFixture(this)
        f.playVideos("v1")

        f.coordinator.engineEventTranslator.onPlaybackStateChanged(PlayerEngine.STATE_READY)
        assertFalse(f.coordinator.playbackState.value.isPreparing)

        advanceTimeBy(30_000)
        assertNull(f.coordinator.playbackState.value.userNotification)
    }

    @Test
    fun `sleep timer counts down and pauses at expiry`() = runTest {
        val f = createFixture(this)
        f.engine.isPlaying = true

        f.coordinator.setSleepTimer(1)
        assertEquals(1, f.coordinator.playbackState.value.sleepTimerMinutes)
        assertEquals(60L, f.coordinator.playbackState.value.sleepTimerRemainingSeconds)

        advanceTimeBy(1000)
        assertEquals(59L, f.coordinator.playbackState.value.sleepTimerRemainingSeconds)

        advanceTimeBy(59_000)
        assertNull(f.coordinator.playbackState.value.sleepTimerMinutes)
        assertNull(f.coordinator.playbackState.value.sleepTimerRemainingSeconds)
        assertEquals(1, f.engine.pauseCalls)
        assertFalse(f.engine.isPlaying)
    }

    @Test
    fun `sleep timer of zero minutes cancels`() = runTest {
        val f = createFixture(this)
        f.coordinator.setSleepTimer(5)
        assertEquals(5, f.coordinator.playbackState.value.sleepTimerMinutes)

        f.coordinator.setSleepTimer(0)

        assertNull(f.coordinator.playbackState.value.sleepTimerMinutes)
        assertNull(f.coordinator.playbackState.value.sleepTimerRemainingSeconds)
    }

    @Test
    fun `cancelSleepTimer clears the state`() = runTest {
        val f = createFixture(this)
        f.coordinator.setSleepTimer(5)

        f.coordinator.cancelSleepTimer()

        assertNull(f.coordinator.playbackState.value.sleepTimerMinutes)
        assertNull(f.coordinator.playbackState.value.sleepTimerRemainingSeconds)
    }

    @Test
    fun `stop clears the session and the persisted queue`() = runTest {
        val f = createFixture(this)
        f.playVideos("v1", "v2")
        f.coordinator.setSleepTimer(5)

        f.coordinator.stop()

        val state = f.coordinator.playbackState.value
        assertNull(state.currentItem)
        assertTrue(state.queue.isEmpty())
        assertFalse(state.isPlaying)
        assertNull(state.sleepTimerMinutes)
        assertEquals(1, f.engine.stopCalls)
        assertEquals(1, f.backgroundService.stopCalls)
        assertNull(f.store.load())
    }

    @Test
    fun `release releases the engine without clearing the session`() = runTest {
        val f = createFixture(this)
        f.playVideos("v1")

        f.coordinator.release()

        assertEquals(1, f.engine.releaseCalls)
        assertEquals(1, f.backgroundService.stopCalls)
        assertEquals("v1", f.coordinator.playbackState.value.currentItem?.id)
        assertEquals(1, f.coordinator.playbackState.value.queue.size)
    }

    @Test
    fun `playing with background play starts the service and pause records history`() = runTest {
        val f = createFixture(this, AppSettings(backgroundPlay = true))
        f.playVideos("v1")
        // playVideos clears the engine logs, not the history record
        // from the session start (position 0).
        assertEquals(1, f.persistence.historyRecords.size)
        f.engine.currentPosition = 5_000L
        f.engine.duration = 60_000L

        f.coordinator.engineEventTranslator.onIsPlayingChanged(true)
        assertTrue(f.coordinator.playbackState.value.isPlaying)
        assertEquals(1, f.backgroundService.startCalls)

        f.coordinator.engineEventTranslator.onIsPlayingChanged(false)
        assertFalse(f.coordinator.playbackState.value.isPlaying)
        assertEquals(5_000L, f.coordinator.playbackState.value.currentPositionMs)
        assertEquals(2, f.persistence.historyRecords.size)
        val pauseRow = f.persistence.historyRecords[1]
        assertEquals("content://video/v1", pauseRow.mediaUri)
        assertEquals(5_000L, pauseRow.playbackPositionMs)
        assertEquals(60_000L, pauseRow.durationMs)
    }

    @Test
    fun `codec error publishes the codec branch`() = runTest {
        val f = createFixture(this)

        f.coordinator.engineEventTranslator.onError(MpvPlaybackException("codec boom", codecName = "H.265"))

        assertEquals("codec:H.265", f.coordinator.playbackState.value.userNotification)
        assertFalse(f.coordinator.playbackState.value.isPreparing)
    }

    @Test
    fun `plain error publishes the message`() = runTest {
        val f = createFixture(this)

        f.coordinator.engineEventTranslator.onError(Exception("boom"))

        assertEquals("Playback error: boom", f.coordinator.playbackState.value.userNotification)
    }

    @Test
    fun `selectTrack and selectAudioTrack refresh the track lists`() = runTest {
        val f = createFixture(this)
        f.engine.audioTracksToReturn = listOf(
            AudioTrackInfo(0, 0, "AAC", "eng", true),
            AudioTrackInfo(0, 1, "AC3", "fre", false),
        )
        f.engine.subtitleTracksToReturn = listOf(
            SubtitleTrackInfo(0, 0, "English", "eng", true),
        )

        f.coordinator.selectAudioTrack(0, 1)
        assertEquals(listOf(0 to 1), f.engine.selectAudioCalls)
        assertEquals(2, f.coordinator.playbackState.value.audioTracks.size)
        assertEquals(0, f.coordinator.playbackState.value.selectedAudioTrackIndex)
        assertEquals(1, f.coordinator.playbackState.value.subtitleTracks.size)
        assertEquals(0, f.coordinator.playbackState.value.selectedSubtitleTrackIndex)

        f.coordinator.selectTrack(TrackType.AUDIO, "a1")
        assertEquals(listOf(TrackType.AUDIO to "a1"), f.engine.selectTrackCalls)
    }

    @Test
    fun `selectSubtitleTrack delegates to the engine`() = runTest {
        val f = createFixture(this)

        f.coordinator.selectSubtitleTrack(0, 1)
        f.coordinator.selectSubtitleTrack(null, null)

        assertEquals(listOf(0 to 1, null to null), f.engine.selectSubtitleCalls)
    }

    @Test
    fun `background-active state publishes the banner`() = runTest {
        val f = createFixture(this)

        f.coordinator.setVideoBackgroundActive(true)
        assertTrue(f.coordinator.playbackState.value.isVideoBackgroundActive)
        assertTrue(f.coordinator.playbackState.value.isBackgroundPlaying)
        assertEquals("banner:background", f.coordinator.playbackState.value.userNotification)

        f.coordinator.setVideoBackgroundActive(false)
        assertFalse(f.coordinator.playbackState.value.isVideoBackgroundActive)
        assertFalse(f.coordinator.playbackState.value.isBackgroundPlaying)
        assertNull(f.coordinator.playbackState.value.userNotification)
    }

    @Test
    fun `restorePersistedQueueIfEmpty restores a saved queue paused`() = runTest {
        val f = createFixture(this)
        val items = listOf(f.video("v1"), f.video("v2"))
        f.store.save(items, 1)

        assertTrue(f.coordinator.restorePersistedQueueIfEmpty())
        assertEquals(listOf(Triple(items, 1, 0L)), f.engine.loadCalls)
        assertEquals(0, f.engine.playCalls)
        assertEquals(items[1], f.coordinator.playbackState.value.currentItem)

        assertFalse(f.coordinator.restorePersistedQueueIfEmpty())
    }

    @Test
    fun `lock and notification clears update the state`() = runTest {
        val f = createFixture(this)

        f.coordinator.setLocked(true)
        assertTrue(f.coordinator.playbackState.value.isLocked)
        f.coordinator.setLocked(false)
        assertFalse(f.coordinator.playbackState.value.isLocked)

        f.coordinator.showNotification("hi")
        assertEquals("hi", f.coordinator.playbackState.value.userNotification)
        f.coordinator.clearUserNotification()
        assertNull(f.coordinator.playbackState.value.userNotification)
    }

    @Test
    fun `clearResumedNotification clears the resume banner`() = runTest {
        val f = createFixture(this)
        val item = f.video("v1", durationMs = 120_000L)
        f.coordinator.load(item, resumeMs = 9_000L)
        assertEquals(9_000L, f.coordinator.playbackState.value.resumedFromMs)

        f.coordinator.clearResumedNotification()

        assertNull(f.coordinator.playbackState.value.resumedFromMs)
    }
}
