package com.mpx.player.playback

import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakePlayerEngine
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.playback.engine.MpvPlaybackException
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration Phase 3, step 3: unit tests for the extracted
 * [EngineEventTranslator]. Tested through fakes only — a fake
 * [PlayerEngine], real [PlaybackQueue] / [SubtitleManager] over
 * fake hosts and stores, a real [PlaybackResumeManager] over no
 * repository, and a fake [EngineEventHost] — no mocking framework,
 * no Android runtime.
 *
 * What these tests pin: the isPlaying start/stop effects (ticker,
 * background service, pause flush + history), the READY/ENDED
 * state publishes and the ENDED decision execution, transition
 * index handling (including the #330 banner rule and the persisted
 * queue), track/subtitle delegation, and the speed/size/decoder/
 * error publishes. What they cannot pin, and can only be verified
 * on device: real mpv event timing (the fake engine answers
 * synchronously), the real preparing watchdog against a stuck load,
 * the background service actually starting, and the codec-error
 * banner text itself (a string resource, formatted by the host —
 * the fake marks the codec branch instead).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineEventTranslatorTest {

    /** Builds a translator with real queue/subtitle/resume pieces over fakes. */
    private suspend fun createFixture(scope: CoroutineScope): Fixture {
        val engine = FakePlayerEngine()
        val settings = FakeSettingsRepository()
        val store = FakeQueueStore()
        val queueHost = FakeQueueHost()
        val subHost = FakeSubtitleHost()
        val eventHost = FakeEngineEventHost()
        val resumeManager = PlaybackResumeManager(null, scope)
        resumeManager.awaitPreload()
        val queue = PlaybackQueue(
            engine = engine,
            queueStore = store,
            resumeManager = resumeManager,
            settingsRepository = settings,
            host = queueHost,
            scope = scope,
        )
        val subtitleManager = SubtitleManager(
            engine = engine,
            host = subHost,
            scope = scope,
            fileAccess = SubtitleManager.FileAccess(
                detectCodepage = { null },
                hasAllFilesAccess = { true },
                findSiblings = { emptyList<File>() },
                validateSibling = { null },
            ),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        val translator = EngineEventTranslator(
            engine = engine,
            queue = queue,
            subtitleManager = subtitleManager,
            resumeManager = resumeManager,
            settingsRepository = settings,
            host = eventHost,
            scope = scope,
            // Unconfined: the transition-history launch runs eagerly
            // inside onMediaItemTransition(), so it is observable
            // without advancing a scheduler.
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        return Fixture(
            engine = engine,
            settings = settings,
            store = store,
            queueHost = queueHost,
            subHost = subHost,
            eventHost = eventHost,
            resumeManager = resumeManager,
            queue = queue,
            subtitleManager = subtitleManager,
            translator = translator,
        )
    }

    private class Fixture(
        val engine: FakePlayerEngine,
        val settings: FakeSettingsRepository,
        val store: FakeQueueStore,
        val queueHost: FakeQueueHost,
        val subHost: FakeSubtitleHost,
        val eventHost: FakeEngineEventHost,
        val resumeManager: PlaybackResumeManager,
        val queue: PlaybackQueue,
        val subtitleManager: SubtitleManager,
        val translator: EngineEventTranslator,
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

        /** Starts a queue session and mirrors its state into the event host. */
        fun playQueue(vararg ids: String, startIndex: Int = 0, repeatMode: RepeatMode = RepeatMode.OFF) {
            val items = ids.map { video(it) }
            queue.playQueue(items, startIndex = startIndex)
            eventHost.state = queueHost.state.copy(repeatMode = repeatMode)
            engine.loadCalls.clear()
            engine.playCalls = 0
            engine.seekCalls.clear()
        }

        /** Arms a pending external-subtitle pick in the subtitle manager. */
        fun armPendingSubtitle(item: PlayableMedia = video("v1")) {
            subHost.currentItem = item
            subHost.queue = listOf(item)
            subtitleManager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")
        }
    }

    @Test
    fun `playing with background play starts the service and the ticker`() = runTest {
        val f = createFixture(this)
        f.settings.settings = AppSettings(backgroundPlay = true)

        f.translator.onIsPlayingChanged(true)

        assertTrue(f.eventHost.state.isPlaying)
        assertEquals(1, f.eventHost.backgroundServiceCalls)
        assertEquals(1, f.eventHost.tickerStartCalls)
        assertEquals(0, f.eventHost.tickerStopCalls)
    }

    @Test
    fun `playing without background play starts the ticker but not the service`() = runTest {
        val f = createFixture(this)
        f.settings.settings = AppSettings(backgroundPlay = false)

        f.translator.onIsPlayingChanged(true)

        assertTrue(f.eventHost.state.isPlaying)
        assertEquals(0, f.eventHost.backgroundServiceCalls)
        assertEquals(1, f.eventHost.tickerStartCalls)
    }

    @Test
    fun `pause stops the ticker flushes position and records history`() = runTest {
        val f = createFixture(this)
        val item = f.video("v1", durationMs = 120_000L)
        f.eventHost.state = PlaybackState(currentItem = item, isPlaying = true)
        f.engine.currentPosition = 5_000L
        f.engine.duration = 60_000L

        f.translator.onIsPlayingChanged(false)

        assertFalse(f.eventHost.state.isPlaying)
        assertEquals(1, f.eventHost.tickerStopCalls)
        assertEquals(1, f.eventHost.flushCalls)
        assertEquals(listOf(Triple("v1", 5_000L, 60_000L)), f.eventHost.pauseHistoryCalls)
    }

    @Test
    fun `pause within the first 1_5 seconds records no history`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(currentItem = f.video("v1"), isPlaying = true)
        f.engine.currentPosition = 1_000L
        f.engine.duration = 60_000L

        f.translator.onIsPlayingChanged(false)

        assertEquals(1, f.eventHost.flushCalls)
        assertTrue(f.eventHost.pauseHistoryCalls.isEmpty())
    }

    @Test
    fun `pause with no engine duration falls back to the item duration`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(currentItem = f.video("v1", durationMs = 180_000L), isPlaying = true)
        f.engine.currentPosition = 5_000L
        f.engine.duration = 0L

        f.translator.onIsPlayingChanged(false)

        assertEquals(listOf(Triple("v1", 5_000L, 180_000L)), f.eventHost.pauseHistoryCalls)
    }

    @Test
    fun `pause with no current item records nothing`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(isPlaying = true)
        f.engine.currentPosition = 5_000L

        f.translator.onIsPlayingChanged(false)

        assertEquals(1, f.eventHost.flushCalls)
        assertTrue(f.eventHost.pauseHistoryCalls.isEmpty())
    }

    @Test
    fun `READY cancels the watchdog clears preparing and the error banner and runs auto-detect`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(
            isPreparing = true,
            userNotification = "Playback error: boom",
            playbackState = PlayerEngine.STATE_BUFFERING,
        )

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_READY)

        assertEquals(PlayerEngine.STATE_READY, f.eventHost.state.playbackState)
        assertFalse(f.eventHost.state.isPreparing)
        assertNull(f.eventHost.state.userNotification)
        assertEquals(1, f.eventHost.watchdogCancelCalls)
        assertEquals(1, f.eventHost.refreshPositionAndTracksCalls)
        assertEquals(1, f.eventHost.autoDetectCalls)
    }

    @Test
    fun `READY keeps a non-error notification`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(
            isPreparing = true,
            userNotification = "Loaded subtitle: sub.srt",
        )

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_READY)

        assertEquals("Loaded subtitle: sub.srt", f.eventHost.state.userNotification)
    }

    @Test
    fun `READY clears a codec-failure banner too`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(
            userNotification = "Playback failed: unsupported codec H.265",
        )

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_READY)

        assertNull(f.eventHost.state.userNotification)
    }

    @Test
    fun `BUFFERING keeps preparing and does not cancel the watchdog or auto-detect`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(isPreparing = true)

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_BUFFERING)

        assertEquals(PlayerEngine.STATE_BUFFERING, f.eventHost.state.playbackState)
        assertTrue(f.eventHost.state.isPreparing)
        assertEquals(0, f.eventHost.watchdogCancelCalls)
        assertEquals(0, f.eventHost.autoDetectCalls)
        assertEquals(1, f.eventHost.refreshPositionAndTracksCalls)
    }

    @Test
    fun `ENDED with repeat OFF on the last item stops and clears the resume position`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", "v3", startIndex = 2, repeatMode = RepeatMode.OFF)
        f.resumeManager.saveResumePosition("v3", 9_000L, "content://video/v3")

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)

        assertEquals(1, f.eventHost.watchdogCancelCalls)
        assertFalse(f.eventHost.state.isPreparing)
        assertTrue(f.engine.loadCalls.isEmpty())
        assertEquals(0, f.engine.playCalls)
        assertTrue(f.engine.seekCalls.isEmpty())
        assertNull(f.resumeManager.getResumePosition("v3", "content://video/v3"))
    }

    @Test
    fun `ENDED with repeat ALL on the last item wraps to the first`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", "v3", startIndex = 2, repeatMode = RepeatMode.ALL)
        val items = f.queue.items
        f.resumeManager.saveResumePosition("v3", 9_000L, "content://video/v3")

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)

        assertEquals(listOf(Triple(items, 0, 0L)), f.engine.loadCalls)
        assertEquals(1, f.engine.playCalls)
        assertNull(f.resumeManager.getResumePosition("v3", "content://video/v3"))
    }

    @Test
    fun `ENDED with repeat ONE replays the current item`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", "v3", startIndex = 1, repeatMode = RepeatMode.ONE)

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)

        assertEquals(listOf(0L), f.engine.seekCalls)
        assertEquals(1, f.engine.playCalls)
        assertTrue(f.engine.loadCalls.isEmpty())
    }

    @Test
    fun `ENDED with repeat OFF mid-queue advances`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", "v3", startIndex = 0, repeatMode = RepeatMode.OFF)
        val items = f.queue.items
        f.resumeManager.saveResumePosition("v1", 9_000L, "content://video/v1")

        f.translator.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)

        assertEquals(listOf(Triple(items, 1, 0L)), f.engine.loadCalls)
        assertEquals(1, f.engine.playCalls)
        assertNull(f.resumeManager.getResumePosition("v1", "content://video/v1"))
    }

    @Test
    fun `transition publishes the new item refreshes and persists the index`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", "v3", startIndex = 0)
        f.resumeManager.saveResumePosition("v2", 7_000L, "content://video/v2")
        f.engine.hasNextMediaItem = true
        f.engine.hasPreviousMediaItem = true

        f.translator.onMediaItemTransition(1)

        assertEquals("v2", f.eventHost.state.currentItem?.id)
        assertEquals(1, f.eventHost.state.currentIndex)
        assertTrue(f.eventHost.state.hasNext)
        assertTrue(f.eventHost.state.hasPrevious)
        assertEquals(0, f.eventHost.state.videoWidth)
        assertEquals(0, f.eventHost.state.videoHeight)
        assertEquals(1, f.eventHost.refreshPositionAndTracksCalls)
        // playQueue persisted once at session start; the transition persists again.
        assertEquals(2, f.store.saveCalls)
        assertEquals(1, f.store.savedIndex)
        assertEquals(listOf("v2" to 7_000L), f.eventHost.historyCalls)
    }

    @Test
    fun `transition to the same item keeps the resume banner`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", startIndex = 0)
        f.eventHost.state = f.eventHost.state.copy(resumedFromMs = 5_000L)

        f.translator.onMediaItemTransition(0)

        assertEquals(5_000L, f.eventHost.state.resumedFromMs)
        assertEquals("v1", f.eventHost.state.currentItem?.id)
    }

    @Test
    fun `transition to another item clears the resume banner`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", startIndex = 0)
        f.eventHost.state = f.eventHost.state.copy(resumedFromMs = 5_000L)

        f.translator.onMediaItemTransition(1)

        assertNull(f.eventHost.state.resumedFromMs)
        assertEquals("v2", f.eventHost.state.currentItem?.id)
    }

    @Test
    fun `transition to no item clears the banner and the current item`() = runTest {
        val f = createFixture(this)
        f.playQueue("v1", "v2", startIndex = 0)
        f.eventHost.state = f.eventHost.state.copy(resumedFromMs = 5_000L)

        f.translator.onMediaItemTransition(9)

        assertNull(f.eventHost.state.currentItem)
        assertNull(f.eventHost.state.resumedFromMs)
        assertEquals(9, f.eventHost.state.currentIndex)
        // playQueue persisted once at session start; the transition persists again.
        assertEquals(2, f.store.saveCalls)
        assertEquals(9, f.store.savedIndex)
    }

    @Test
    fun `tracks-changed refreshes tracks and re-selects the pending subtitle`() = runTest {
        val f = createFixture(this)
        f.armPendingSubtitle()
        f.subHost.tracks = listOf(
            SubtitleTrackInfo(0, 0, "embedded", "en", isSelected = false),
            SubtitleTrackInfo(0, 1, "srt", "en", isSelected = false, isExternal = true),
            SubtitleTrackInfo(0, 2, "srt", "en", isSelected = false, isExternal = true),
        )

        f.translator.onTracksChanged()

        assertEquals(1, f.eventHost.refreshTracksCalls)
        assertEquals(listOf(0 to 2), f.engine.selectSubtitleCalls)
    }

    @Test
    fun `external subtitle confirmation reaches the subtitle manager`() = runTest {
        val f = createFixture(this)
        f.armPendingSubtitle()

        f.translator.onExternalSubtitleConfirmed("v1", success = true)

        assertEquals(listOf("Loaded subtitle: sub.srt"), f.subHost.notifications)
    }

    @Test
    fun `external subtitle confirmation for an unknown id is ignored`() = runTest {
        val f = createFixture(this)

        f.translator.onExternalSubtitleConfirmed("v1", success = true)

        assertTrue(f.subHost.notifications.isEmpty())
    }

    @Test
    fun `playback parameters publish the speed`() = runTest {
        val f = createFixture(this)

        f.translator.onPlaybackParametersChanged(1.5f)

        assertEquals(1.5f, f.eventHost.state.speed)
        assertEquals(listOf(1.5f), f.eventHost.speedCalls)
    }

    @Test
    fun `video size publishes width and height`() = runTest {
        val f = createFixture(this)

        f.translator.onVideoSizeChanged(1920, 1080)

        assertEquals(1920, f.eventHost.state.videoWidth)
        assertEquals(1080, f.eventHost.state.videoHeight)
    }

    @Test
    fun `decoder init notes a hardware-to-software fallback`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(decoderMode = DecoderMode.HW)

        f.translator.onVideoDecoderInitialized("OMX.google.h264.decoder")

        assertEquals("OMX.google.h264.decoder", f.eventHost.state.activeDecoderName)
        assertEquals(
            "Platform override: Hardware decoder unavailable for this format on this device. Fallback to OMX.google.h264.decoder.",
            f.eventHost.state.decoderFallbackNote,
        )
    }

    @Test
    fun `decoder init notes a software-to-hardware fallback`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(decoderMode = DecoderMode.SW)

        f.translator.onVideoDecoderInitialized("c2.qti.avc.decoder")

        assertEquals(
            "Platform override: Software decoder unavailable for this format. Fallback to c2.qti.avc.decoder.",
            f.eventHost.state.decoderFallbackNote,
        )
    }

    @Test
    fun `decoder init with a matching decoder leaves no note`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(decoderMode = DecoderMode.HW)

        f.translator.onVideoDecoderInitialized("c2.qti.avc.decoder")

        assertEquals("c2.qti.avc.decoder", f.eventHost.state.activeDecoderName)
        assertNull(f.eventHost.state.decoderFallbackNote)
    }

    @Test
    fun `error cancels the watchdog ends preparing and publishes the banner`() = runTest {
        val f = createFixture(this)
        f.eventHost.state = PlaybackState(isPreparing = true)

        f.translator.onError(Exception("boom"))

        assertEquals(1, f.eventHost.watchdogCancelCalls)
        assertFalse(f.eventHost.state.isPreparing)
        assertEquals("Playback error: boom", f.eventHost.state.userNotification)
        assertEquals(1, f.eventHost.errorCalls.size)
    }

    @Test
    fun `codec error takes the codec branch`() = runTest {
        val f = createFixture(this)

        f.translator.onError(MpvPlaybackException("codec boom", codecName = "H.265"))

        assertEquals("codec:H.265", f.eventHost.state.userNotification)
    }

    @Test
    fun `engineEventTranslator writes directly to PlaybackStateStore without proxy`() = runTest {
        val store = PlaybackStateStore()
        val dispatchedEvents = mutableListOf<PlaybackEvent>()
        store.setEventListener { dispatchedEvents.add(it) }

        val fixture = createFixture(this)
        val translator = EngineEventTranslator(
            engine = fixture.engine,
            queue = fixture.queue,
            subtitleManager = fixture.subtitleManager,
            resumeManager = fixture.resumeManager,
            settingsRepository = fixture.settings,
            host = store,
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

        translator.onPlaybackStateChanged(PlayerEngine.STATE_READY)
        assertEquals(PlayerEngine.STATE_READY, store.state.playbackState)
        assertFalse(store.state.isPreparing)

        translator.onIsPlayingChanged(true)
        assertTrue(store.state.isPlaying)
        assertTrue(dispatchedEvents.contains(PlaybackEvent.StartPositionTicker))
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

    /**
     * Fake queue host: mirrors the real coordinator's publishes (the
     * session and queue-order copies apply exactly the fields the
     * manager applies) and records history, ticker, and watchdog
     * effects.
     */
    private class FakeQueueHost : QueueHost {
        override var state: PlaybackState = PlaybackState()
        val historyCalls = mutableListOf<Pair<String, Long>>()
        var clearAutoDetectCalls: Int = 0
        var refreshCalls: Int = 0
        var tickerCalls: Int = 0
        var watchdogCalls: Int = 0
        var shuffleFlagCalls: Int = 0
        var repeatCalls: Int = 0

        override fun publishSession(
            items: List<PlayableMedia>,
            targetIndex: Int,
            targetItem: PlayableMedia,
            resumedFromMs: Long?,
            speed: Float,
            shuffleEnabled: Boolean,
            hasPrevious: Boolean,
            hasNext: Boolean,
        ) {
            state = state.copy(
                currentItem = targetItem,
                queue = items,
                currentIndex = targetIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                speed = speed,
                shuffleEnabled = shuffleEnabled,
                isPreparing = targetItem.isVideo,
                videoWidth = 0,
                videoHeight = 0,
            )
        }

        override fun publishQueueOrder(
            items: List<PlayableMedia>,
            currentIndex: Int,
            shuffleEnabled: Boolean,
            hasPrevious: Boolean,
            hasNext: Boolean,
        ) {
            state = state.copy(
                queue = items,
                currentIndex = currentIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                shuffleEnabled = shuffleEnabled,
            )
        }

        override fun publishShuffleEnabled(enabled: Boolean) {
            shuffleFlagCalls++
            state = state.copy(shuffleEnabled = enabled)
        }

        override fun publishRepeatMode(mode: RepeatMode) {
            repeatCalls++
            state = state.copy(repeatMode = mode)
        }

        override fun recordHistory(item: PlayableMedia, positionMs: Long) {
            historyCalls.add(item.id to positionMs)
        }

        override fun clearAutoDetectAttempts() {
            clearAutoDetectCalls++
        }

        override fun refreshPositionAndTracks() {
            refreshCalls++
        }

        override fun refreshPosition() {
            refreshCalls++
        }

        override fun startPositionTicker() {
            tickerCalls++
        }

        override fun armLoadWatchdog() {
            watchdogCalls++
        }
    }

    /**
     * Fake subtitle host: mirrors the real coordinator's commit (the
     * queue item and the current item are replaced, the notification
     * rides along) and records notifications and track refreshes.
     */
    private class FakeSubtitleHost : SubtitleHost {
        override var currentItem: PlayableMedia? = null
        var queue: List<PlayableMedia> = emptyList()
        var tracks: List<SubtitleTrackInfo> = emptyList()
        val commits = mutableListOf<Triple<Int, PlayableMedia, String?>>()
        val notifications = mutableListOf<String>()
        var refreshCalls: Int = 0

        override val liveQueue: List<PlayableMedia>
            get() = queue
        override val subtitleTracks: List<SubtitleTrackInfo>
            get() = tracks

        override fun commitSubtitleItem(index: Int, updatedItem: PlayableMedia, notification: String?) {
            commits.add(Triple(index, updatedItem, notification))
            queue = queue.toMutableList().also { it[index] = updatedItem }
            currentItem = updatedItem
        }

        override fun subtitleLoadedMessage(displayName: String): String =
            "Loaded subtitle: $displayName"

        override fun subtitleFailedMessage(displayName: String): String =
            "Failed to load subtitle: $displayName"

        override fun showNotification(message: String) {
            notifications.add(message)
        }

        override fun refreshTracksState() {
            refreshCalls++
        }
    }

    /**
     * Fake event host: mirrors the real coordinator's publishes (each
     * publish applies exactly the state copy the manager applies) and
     * records every lifecycle effect the translator asks for. The
     * codec-error branch is mirrored without the string resource:
     * a codec error publishes "codec:<name>" instead of the
     * localised banner, so the test pins the branch, not the text.
     */
    private class FakeEngineEventHost : EngineEventHost {
        override var state: PlaybackState = PlaybackState()
        val historyCalls = mutableListOf<Pair<String, Long>>()
        val pauseHistoryCalls = mutableListOf<Triple<String, Long, Long>>()
        val speedCalls = mutableListOf<Float>()
        val errorCalls = mutableListOf<Exception>()
        var backgroundServiceCalls: Int = 0
        var tickerStartCalls: Int = 0
        var tickerStopCalls: Int = 0
        var flushCalls: Int = 0
        var watchdogCancelCalls: Int = 0
        var refreshPositionAndTracksCalls: Int = 0
        var autoDetectCalls: Int = 0
        var refreshTracksCalls: Int = 0

        override fun publishIsPlaying(isPlaying: Boolean) {
            state = state.copy(isPlaying = isPlaying)
        }

        override fun startBackgroundService() {
            backgroundServiceCalls++
        }

        override fun startPositionTicker() {
            tickerStartCalls++
        }

        override fun stopPositionTicker() {
            tickerStopCalls++
        }

        override fun flushPosition() {
            flushCalls++
        }

        override fun recordHistory(item: PlayableMedia, positionMs: Long) {
            historyCalls.add(item.id to positionMs)
        }

        override fun recordPauseHistory(item: PlayableMedia, positionMs: Long, durationMs: Long) {
            pauseHistoryCalls.add(Triple(item.id, positionMs, durationMs))
        }

        override fun cancelLoadWatchdog() {
            watchdogCancelCalls++
        }

        override fun publishEngineState(playbackState: Int, isPreparing: Boolean, userNotification: String?) {
            state = state.copy(
                playbackState = playbackState,
                isPreparing = isPreparing,
                userNotification = userNotification,
            )
        }

        override fun refreshPositionAndTracks() {
            refreshPositionAndTracksCalls++
        }

        override fun maybeAutoDetectSubtitles() {
            autoDetectCalls++
        }

        override fun publishTransition(
            item: PlayableMedia?,
            index: Int,
            hasPrevious: Boolean,
            hasNext: Boolean,
            resumedFromMs: Long?,
        ) {
            state = state.copy(
                currentItem = item,
                currentIndex = index,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                videoWidth = 0,
                videoHeight = 0,
            )
        }

        override fun refreshTracksState() {
            refreshTracksCalls++
        }

        override fun publishSpeed(speed: Float) {
            speedCalls.add(speed)
            state = state.copy(speed = speed)
        }

        override fun publishVideoSize(width: Int, height: Int) {
            state = state.copy(videoWidth = width, videoHeight = height)
        }

        override fun publishDecoderInitialized(decoderName: String, fallbackNote: String?) {
            state = state.copy(
                activeDecoderName = decoderName,
                decoderFallbackNote = fallbackNote,
            )
        }

        override fun publishEngineError(error: Exception) {
            errorCalls.add(error)
            state = state.copy(
                isPreparing = false,
                userNotification = if (error is MpvPlaybackException && error.codecName != null) {
                    "codec:${error.codecName}"
                } else {
                    "Playback error: ${error.message}"
                },
            )
        }
    }
}
