package com.mpx.player.playback.engine

import android.graphics.SurfaceTexture
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MpvCoreEngineTest {

    private lateinit var testContext: TestContext
    private lateinit var fakePort: FakeMpvPort
    private lateinit var directHandler: DirectEngineHandler
    private lateinit var fakeHost: TestHost
    private lateinit var fakeListener: TestListener
    private lateinit var surface: Surface

    private class TestHost : MpvCoreEngine.Host {
        var queue = mutableListOf<PlayableMedia>()
        var mediaIndex = 0
        var currentHwdecMode: DecoderMode = DecoderMode.HW
        var currentHwdecVal: String = "mediacodec"
        val loadStartedEvents = mutableListOf<Unit>()
        val loadResolvedEvents = mutableListOf<String>()
        val trackListChangedEvents = mutableListOf<Unit>()
        val fileLoadedEvents = mutableListOf<Unit>()

        override fun hwdecValueFor(mode: DecoderMode): String = when (mode) {
            DecoderMode.HW -> "mediacodec"
            DecoderMode.HW_PLUS -> "mediacodec-copy"
            DecoderMode.SW -> "no"
        }

        override fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = when (stage) {
            MpvCoreEngine.DecoderStage.HW -> "mediacodec"
            MpvCoreEngine.DecoderStage.HW_PLUS -> "mediacodec-copy"
            MpvCoreEngine.DecoderStage.SW -> "no"
        }

        override fun applyHwdecForMode(mode: DecoderMode) {
            currentHwdecMode = mode
            currentHwdecVal = hwdecValueFor(mode)
        }

        override fun applyPostInitSettings(core: MpvCoreEngine) {}

        override fun onLoadStarted() {
            loadStartedEvents.add(Unit)
        }

        override fun onLoadTargetResolved(mediaId: String) {
            loadResolvedEvents.add(mediaId)
        }

        override fun onTrackListChanged() {
            trackListChangedEvents.add(Unit)
        }

        override fun onFileLoaded() {
            fileLoadedEvents.add(Unit)
        }

        override fun getCurrentQueue(): List<PlayableMedia> = queue

        override fun getCurrentMediaIndex(): Int = mediaIndex

        var settings: AppSettings = AppSettings()
        override fun loadSettings(): AppSettings = settings
    }

    private class TestListener : PlayerEngine.Listener {
        var playbackState: Int? = null
        var isPlaying: Boolean? = null
        val errors = mutableListOf<Exception>()
        var transitionIndex: Int? = null
        var speed: Float? = null
        var videoDecoderInitialized: String? = null
        var videoWidth: Int? = null
        var videoHeight: Int? = null

        override fun onPlaybackStateChanged(playbackState: Int) {
            this.playbackState = playbackState
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this.isPlaying = isPlaying
        }

        override fun onMediaItemTransition(index: Int) {
            transitionIndex = index
        }

        override fun onPlaybackParametersChanged(speed: Float) {
            this.speed = speed
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            this.videoWidth = width
            this.videoHeight = height
        }

        override fun onError(error: Exception) {
            errors.add(error)
        }

        override fun onVideoDecoderInitialized(decoderName: String) {
            videoDecoderInitialized = decoderName
        }
    }

    @Before
    fun setUp() {
        testContext = TestContext()
        fakePort = FakeMpvPort()
        directHandler = DirectEngineHandler()
        fakeHost = TestHost()
        fakeListener = TestListener()
        surface = Surface(SurfaceTexture(0))
    }

    private fun createEngine(
        initialDecoderMode: DecoderMode = DecoderMode.HW,
        port: MpvPort = fakePort,
    ): MpvCoreEngine {
        val engine = MpvCoreEngine(
            context = testContext,
            initialDecoderMode = initialDecoderMode,
            host = fakeHost,
            port = port,
            mainHandler = directHandler,
        )
        engine.setListener(fakeListener)
        return engine
    }

    // --- 1. Init failure capture ---

    @Test
    fun `init failure captured when port init fails`() {
        val failingPort = FakeMpvPort(initException = RuntimeException("libmpv crash"))
        val engine = MpvCoreEngine(
            context = testContext,
            host = fakeHost,
            port = failingPort,
            mainHandler = directHandler,
        )

        assertFalse(engine.mpvInitialized)
        assertNotNull(engine.pendingInitError)
        assertEquals("libmpv crash", engine.pendingInitError?.message)

        // Setting listener flushes pendingInitError
        engine.setListener(fakeListener)
        assertEquals(1, fakeListener.errors.size)
        assertEquals("libmpv crash", fakeListener.errors.first().message)
        assertNull(engine.pendingInitError)
    }

    @Test
    fun `init success registers observers and configures options`() {
        val engine = createEngine()

        assertTrue(engine.mpvInitialized)
        assertNull(engine.pendingInitError)
        assertTrue(fakePort.isCreated)
        assertTrue(fakePort.isInitialized)
        assertEquals(1, fakePort.eventObservers.size)
        assertEquals(1, fakePort.logObservers.size)
    }

    // --- 2. Full load sequence incl. deferred-for-no-surface + pendingLoad flush ---

    @Test
    fun `load without surface is deferred until attachSurface`() {
        val engine = createEngine()
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)

        engine.load(media, resumeMs = 0L)

        // Deferred: no loadfile command issued yet
        assertNotNull(engine.pendingLoad)
        assertTrue(fakePort.recordedCommands.none { it.firstOrNull() == "loadfile" })

        // Attach surface flushes the load
        engine.attachSurface(surface)

        assertNull(engine.pendingLoad)
        val loadCmd = fakePort.recordedCommands.find { it.firstOrNull() == "loadfile" }
        assertNotNull("Expected loadfile command after attachSurface", loadCmd)
        assertEquals("http://localhost/video.mp4", loadCmd?.get(1))
    }

    @Test
    fun `play intent preserved during surface deferral`() {
        val engine = createEngine()
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)

        engine.load(media, resumeMs = 0L)
        // User pressed play while load was deferred
        engine.play()

        assertTrue(engine.playRequested)

        // Attaching surface flushes load AND resumes play
        engine.attachSurface(surface)

        // Verifies pause=false was sent to port
        assertEquals(false, fakePort.recordedProperties["pause"])
    }

    @Test
    fun `paused intent preserved during surface deferral`() {
        val engine = createEngine()
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)

        engine.load(media, resumeMs = 0L)
        engine.pause() // User paused

        assertFalse(engine.playRequested)

        engine.attachSurface(surface)

        // Should NOT call play
        assertFalse(engine.playRequested)
    }

    // --- 3. Play / Pause transitions & cachedIsPlaying ---

    @Test
    fun `play sends pause false without lying about cachedIsPlaying`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        engine.play()

        assertEquals(false, fakePort.recordedProperties["pause"])
        // Deliberately false until mpv property observer confirms real playback
        assertFalse(engine.isPlaying)

        // mpv reports pause=false
        fakePort.emitProperty("pause", false)
        assertTrue(engine.isPlaying)
        assertEquals(true, fakeListener.isPlaying)
    }

    @Test
    fun `pause immediately updates cachedIsPlaying and notifies listener`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        // Simulate playing
        fakePort.emitProperty("pause", false)
        assertTrue(engine.isPlaying)

        engine.pause()

        assertEquals(true, fakePort.recordedProperties["pause"])
        assertFalse(engine.isPlaying)
        assertEquals(false, fakeListener.isPlaying)
    }

    // --- 4. Fallback chain end-to-end ---

    @Test
    fun `decoder fallback chain steps HW to HW_PLUS to SW to hard error`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)
        fakeHost.mediaIndex = 0
        engine.attachSurface(surface)
        engine.load(media, resumeMs = 0L)
        fakePort.recordedProperties["path"] = "http://localhost/video.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        assertEquals(MpvCoreEngine.DecoderStage.HW, engine.currentDecoderStage)

        // Step 1: Mediacodec error triggers fallback to HW_PLUS
        fakePort.emitLog("mediacodec", 10, "codec error: initialization failed")
        assertEquals(MpvCoreEngine.DecoderStage.HW_PLUS, engine.currentDecoderStage)

        // Clear in-flight reload flag by simulating reload completion
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Step 2: Next error triggers fallback to SW
        fakePort.emitLog("mediacodec", 10, "mediacodec-copy error")
        assertEquals(MpvCoreEngine.DecoderStage.SW, engine.currentDecoderStage)

        // Clear in-flight reload flag
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Step 3: Next error at SW triggers hard error
        fakePort.emitLog("mediacodec", 10, "sw decoder error")
        assertEquals(1, fakeListener.errors.size)
        assertTrue(fakeListener.errors.first() is MpvPlaybackException)
    }

    @Test
    fun `duplicate decoder errors within fallback window are coalesced`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)
        fakeHost.mediaIndex = 0
        engine.attachSurface(surface)
        engine.load(media, resumeMs = 0L)
        fakePort.recordedProperties["path"] = "http://localhost/video.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Fire two identical error logs back-to-back while fallback reload is in-flight
        fakePort.emitLog("mediacodec", 10, "error 1")
        fakePort.emitLog("mediacodec", 10, "error 2")

        // Should only step down once (to HW_PLUS) because fallbackReloadPending coalesces duplicates
        assertEquals(MpvCoreEngine.DecoderStage.HW_PLUS, engine.currentDecoderStage)
    }

    @Test
    fun `deliberate stop drops in-flight fallback override preventing D-5 leak`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        val media = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test")
        fakeHost.queue.add(media)
        fakeHost.mediaIndex = 0
        engine.attachSurface(surface)
        engine.load(media, resumeMs = 0L)
        fakePort.recordedProperties["path"] = "http://localhost/video.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Trigger fallback stage (in-flight reload started)
        fakePort.emitLog("mediacodec", 10, "mediacodec error")
        assertTrue(engine.fallbackReloadPending)

        // Stop should drop in-flight fallback state
        engine.stop()

        assertNull(engine.fallbackHwdecStage)
        assertFalse(engine.fallbackReloadPending)
    }

    // --- 5. Media replacement preserving pause & skip navigation ---

    @Test
    fun `seekToNextMediaItem preserves paused state`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        val media2 = PlayableMedia(id = "2", uri = "http://localhost/2.mp4", title = "2")
        fakeHost.queue.addAll(listOf(media1, media2))
        fakeHost.mediaIndex = 0

        engine.load(fakeHost.queue, 0, 0L)

        // Paused state
        engine.pause()
        assertFalse(engine.isPlaying)

        // Move to next
        engine.seekToNextMediaItem()

        assertFalse(engine.playRequested)
    }

    @Test
    fun `seekToNextMediaItem advances index and loads next item when next exists`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media0 = PlayableMedia(id = "0", uri = "http://localhost/0.mp4", title = "0")
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        val media2 = PlayableMedia(id = "2", uri = "http://localhost/2.mp4", title = "2")
        fakeHost.queue.addAll(listOf(media0, media1, media2))
        fakeHost.mediaIndex = 0

        engine.load(fakeHost.queue, 0, 0L)

        // At index 0, hasNextMediaItem is true
        assertTrue(engine.hasNextMediaItem)
        engine.seekToNextMediaItem()

        // Verified next item was loaded (loadfile command with media1's uri)
        val loadCmds = fakePort.recordedCommands.filter { it.firstOrNull() == "loadfile" }
        assertEquals(2, loadCmds.size) // initial load + seekToNext load
        assertEquals("http://localhost/1.mp4", loadCmds.last()[1])
    }

    @Test
    fun `seekToNextMediaItem at last index stops at end and does not wrap or advance`() {
        val engine = createEngine()
        val media0 = PlayableMedia(id = "0", uri = "http://localhost/0.mp4", title = "0")
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.addAll(listOf(media0, media1))
        fakeHost.mediaIndex = 1 // at lastIndex

        engine.attachSurface(surface)
        engine.load(fakeHost.queue, 1, 0L)

        // At lastIndex, hasNextMediaItem is false
        assertFalse(engine.hasNextMediaItem)
        val loadCountBefore = fakePort.recordedCommands.count { it.firstOrNull() == "loadfile" }

        engine.seekToNextMediaItem()

        // No new load issued
        val loadCountAfter = fakePort.recordedCommands.count { it.firstOrNull() == "loadfile" }
        assertEquals(loadCountBefore, loadCountAfter)
    }

    @Test
    fun `seekToPreviousMediaItem decrements index and loads previous item`() {
        val engine = createEngine()
        val media0 = PlayableMedia(id = "0", uri = "http://localhost/0.mp4", title = "0")
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.addAll(listOf(media0, media1))
        fakeHost.mediaIndex = 1

        engine.attachSurface(surface)
        engine.load(fakeHost.queue, 1, 0L)

        assertTrue(engine.hasPreviousMediaItem)
        engine.seekToPreviousMediaItem()

        val loadCmds = fakePort.recordedCommands.filter { it.firstOrNull() == "loadfile" }
        assertEquals("http://localhost/0.mp4", loadCmds.last()[1])
    }

    @Test
    fun `seekToPreviousMediaItem at index 0 seeks to position zero without changing item`() {
        val engine = createEngine()
        val media0 = PlayableMedia(id = "0", uri = "http://localhost/0.mp4", title = "0")
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.addAll(listOf(media0, media1))
        fakeHost.mediaIndex = 0

        engine.attachSurface(surface)
        engine.load(fakeHost.queue, 0, 0L)

        // Simulate playing at 15s into media 0
        fakePort.recordedProperties["path"] = "http://localhost/0.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)
        fakePort.emitProperty("time-pos", 15.0)
        assertEquals(15000L, engine.cachedPositionMs)

        assertFalse(engine.hasPreviousMediaItem)
        engine.seekToPreviousMediaItem()

        // Verified seek command issued with position 0.0
        val seekCmd = fakePort.recordedCommands.find { it.firstOrNull() == "seek" }
        assertNotNull(seekCmd)
        assertEquals("0.0", seekCmd?.get(1))
    }

    @Test
    fun `seekToNextMediaItem resumes playback if engine was playing`() {
        val engine = createEngine()
        val media0 = PlayableMedia(id = "0", uri = "http://localhost/0.mp4", title = "0")
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.addAll(listOf(media0, media1))
        fakeHost.mediaIndex = 0

        engine.attachSurface(surface)
        engine.load(fakeHost.queue, 0, 0L)

        // Simulate active playback
        fakePort.emitProperty("pause", false)
        assertTrue(engine.isPlaying)

        engine.seekToNextMediaItem()

        // playRequested stays true, pause=false was sent to port
        assertTrue(engine.playRequested)
        assertEquals(false, fakePort.recordedProperties["pause"])
    }

    // --- 6. Event ordering ---

    @Test
    fun `stale FILE_LOADED dropped when path does not match active item`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)

        engine.load(media, 0L)

        // Simulate port reporting path for old item
        fakePort.recordedProperties["path"] = "http://localhost/old_item.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Should be dropped as stale
        assertFalse(engine.fileLoadedForCurrentItem)
        assertEquals(0, fakeHost.fileLoadedEvents.size)

        // Simulate port reporting correct path
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        assertTrue(engine.fileLoadedForCurrentItem)
        assertEquals(1, fakeHost.fileLoadedEvents.size)
    }

    @Test
    fun `benign END_FILE with reason stop is dropped without error`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // Delivery of END_FILE with reason=stop (e.g. stop interrupted load)
        fakePort.emitEvent(MpvPort.MPV_EVENT_END_FILE, MpvPort.EndFile(reason = "stop", error = 0L))

        assertEquals(0, fakeListener.errors.size)
    }

    @Test
    fun `premature END_FILE triggers decoder fallback`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // Simulate file successfully loaded with 100s duration and at position 10s
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)
        fakePort.emitProperty("duration", 100.0)
        fakePort.emitProperty("time-pos", 10.0)
        fakePort.recordedProperties["eof-reached"] = false

        // Premature end-file (position < duration - 1s, eof-reached false)
        fakePort.emitEvent(MpvPort.MPV_EVENT_END_FILE, MpvPort.EndFile(reason = "error", error = -1L))

        // Triggered decoder fallback to HW_PLUS
        assertEquals(MpvCoreEngine.DecoderStage.HW_PLUS, engine.currentDecoderStage)
    }

    @Test
    fun `SHUTDOWN event surfaces as playback error`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        fakePort.emitEvent(MpvPort.MPV_EVENT_SHUTDOWN)

        assertEquals(1, fakeListener.errors.size)
        assertTrue(fakeListener.errors.first().message?.contains("MPV player shut down unexpectedly") == true)
    }

    @Test
    fun `END_FILE with fatal error before FILE_LOADED reports startup error`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // Port emits fatal error before FILE_LOADED arrived (e.g. demuxer cannot open stream)
        fakePort.emitEvent(MpvPort.MPV_EVENT_END_FILE, MpvPort.EndFile(reason = "error", error = -1L))

        assertEquals(1, fakeListener.errors.size)
        assertTrue(fakeListener.errors.first().message?.contains("MPV could not play this file") == true)
        assertFalse(engine.fileLoadedForCurrentItem)
    }

    @Test
    fun `stale FILE_LOADED arriving after deliberate stop is dropped`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // User stops playback before FILE_LOADED arrives
        engine.stop()
        assertNull(engine.activeMediaUri)

        // Stale FILE_LOADED arrives from mpv
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Dropped: engine must not transition to READY for stopped session
        assertFalse(engine.fileLoadedForCurrentItem)
        assertEquals(0, fakeHost.fileLoadedEvents.size)
        assertNull(fakeListener.playbackState)
    }

    @Test
    fun `rapid consecutive loads with out of order FILE_LOADED drops stale completion`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media1 = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        val media2 = PlayableMedia(id = "item2", uri = "http://localhost/item2.mp4", title = "Item 2")
        fakeHost.queue.addAll(listOf(media1, media2))

        // Rapid load 1 then 2
        engine.load(media1, 0L)
        val gen1 = engine.currentLoadGeneration
        engine.load(media2, 0L)
        val gen2 = engine.currentLoadGeneration
        assertTrue(gen2 > gen1)

        // In-flight FILE_LOADED for media 1 arrives late
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Dropped: current item is media2
        assertFalse(engine.fileLoadedForCurrentItem)
        assertEquals(0, fakeHost.fileLoadedEvents.size)

        // In-flight FILE_LOADED for media 2 arrives
        fakePort.recordedProperties["path"] = "http://localhost/item2.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Accepted: current item is media2
        assertTrue(engine.fileLoadedForCurrentItem)
        assertEquals(1, fakeHost.fileLoadedEvents.size)
        assertEquals(PlayerEngine.STATE_READY, fakeListener.playbackState)
    }

    @Test
    fun `out of order property updates before FILE_LOADED do not prematurely ready engine`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // Pre-FILE_LOADED property changes
        fakePort.emitProperty("time-pos", 12.0)
        fakePort.emitProperty("duration", 180.0)

        assertEquals(12000L, engine.cachedPositionMs)
        assertEquals(180000L, engine.cachedDurationMs)
        assertFalse(engine.fileLoadedForCurrentItem)
        assertNull(fakeListener.playbackState)

        // Now FILE_LOADED arrives
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        assertTrue(engine.fileLoadedForCurrentItem)
        assertEquals(PlayerEngine.STATE_READY, fakeListener.playbackState)
    }

    @Test
    fun `idle-active and pause interleavings reflect playing state accurately`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        // Both false -> playing
        fakePort.emitProperty("pause", false)
        fakePort.emitProperty("idle-active", false)
        assertTrue(engine.isPlaying)
        assertEquals(true, fakeListener.isPlaying)

        // idle-active becomes true (e.g. idle between items) -> not playing
        fakePort.emitProperty("idle-active", true)
        assertFalse(engine.isPlaying)
        assertEquals(false, fakeListener.isPlaying)

        // idle-active becomes false -> playing again
        fakePort.emitProperty("idle-active", false)
        assertTrue(engine.isPlaying)
        assertEquals(true, fakeListener.isPlaying)

        // pause becomes true -> not playing
        fakePort.emitProperty("pause", true)
        assertFalse(engine.isPlaying)
        assertEquals(false, fakeListener.isPlaying)
    }

    @Test
    fun `END_FILE with eof-reached true is normal completion without fallback`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)
        fakePort.emitProperty("duration", 100.0)
        fakePort.emitProperty("time-pos", 100.0)
        fakePort.recordedProperties["eof-reached"] = true

        // Normal EOF end-file
        fakePort.emitEvent(MpvPort.MPV_EVENT_END_FILE, MpvPort.EndFile(reason = "eof", error = 0L))

        // Decoder stage remains HW (no fallback triggered)
        assertEquals(MpvCoreEngine.DecoderStage.HW, engine.currentDecoderStage)
        assertEquals(0, fakeListener.errors.size)
    }

    @Test
    fun `vo property null event restores vo=gpu immediately when surface is attached`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        // Simulate mpv nulling VO on reconfig error
        fakePort.emitProperty("vo", "null")

        // Immediately restored because currentSurface != null
        assertFalse(engine.voIsNull)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
    }

    // --- 7. Release lifecycle (D-4, D-8) ---

    @Test
    fun `release cleans up observers, destroys port, and ignores subsequent calls`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        engine.release()

        assertTrue(engine.isReleased)
        assertTrue(fakePort.isDestroyed)
        assertEquals(0, fakePort.eventObservers.size)
        assertEquals(0, fakePort.logObservers.size)

        // Post-release calls must be no-ops
        engine.play()
        engine.pause()
        engine.seekTo(1000L)
        engine.setSpeed(1.5f)
        engine.stop()
        engine.release() // Double-release safety

        assertEquals(0, fakePort.recordedCommands.count { it.firstOrNull() == "seek" || it.firstOrNull() == "stop" })
    }

    @Test
    fun `getPlaybackMetrics returns tracked metrics through tracker`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        fakePort.recordedProperties["path"] = "http://localhost/1.mp4"
        fakePort.recordedProperties["hwdec-current"] = "mediacodec"
        fakePort.recordedProperties["video-codec"] = "h264"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)
        fakePort.emitEvent(MpvPort.MPV_EVENT_VIDEO_RECONFIG)

        val metrics = engine.getPlaybackMetrics()
        assertNotNull(metrics.lastTimeToReadyMs)
        assertNotNull(metrics.lastTimeToFirstFrameMs)
        assertEquals(0, metrics.decoderFallbackCount)
        assertEquals(0, metrics.hardErrorCount)
    }

    @Test
    fun `log observer message after destroy does not crash or dispatch (D-4)`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        val logObserver = fakePort.logObservers.firstOrNull()
        assertNotNull(logObserver)

        engine.release()

        // If a lingering log arrives after release
        logObserver?.logMessage("mediacodec", 10, "error after release")

        // No fallback or errors triggered
        assertEquals(0, fakeListener.errors.size)
    }

    // --- 8. pendingSeekMs apply ---

    @Test
    fun `seekTo before FILE_LOADED parks seek and applies it on FILE_LOADED`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        // Scrub during prepare
        engine.seekTo(45000L)

        assertEquals(45000L, engine.pendingSeekMs)
        // Command not sent to mpv yet
        assertTrue(fakePort.recordedCommands.none { it.firstOrNull() == "seek" })

        // FILE_LOADED arrives
        fakePort.recordedProperties["path"] = "http://localhost/item1.mp4"
        fakePort.emitProperty("duration", 120.0)
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Pending seek applied
        assertNull(engine.pendingSeekMs)
        val seekCmd = fakePort.recordedCommands.find { it.firstOrNull() == "seek" }
        assertNotNull("Expected seek command on FILE_LOADED", seekCmd)
        assertEquals("45.0", seekCmd?.get(1))
        assertEquals("absolute+exact", seekCmd?.get(2))
    }

    // --- 9. voIsNull restore ---

    @Test
    fun `voIsNull restored to gpu on attachSurface`() {
        val engine = createEngine()
        engine.attachSurface(surface)

        // Detach surface sets vo=null and voIsNull=true
        engine.detachSurface()
        assertTrue(engine.voIsNull)
        assertEquals("null", fakePort.recordedProperties["vo"])

        // Reattach restores vo=gpu
        engine.attachSurface(surface)
        assertFalse(engine.voIsNull)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
    }

    @Test
    fun `voIsNull restored to gpu on surfaceChanged`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        engine.detachSurface()
        assertTrue(engine.voIsNull)

        // Re-attach surface but simulate voIsNull remaining true
        engine.attachSurface(surface)
        engine.voIsNull = true

        engine.onSurfaceChanged(1920, 1080)
        assertFalse(engine.voIsNull)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
    }

    // --- 10. Phase 1 defect verification ---

    @Test
    fun `attachSurface does not reload when load is already in flight (D-6)`() {
        val engine = createEngine()
        val media = PlayableMedia(id = "item1", uri = "http://localhost/item1.mp4", title = "Item 1")
        fakeHost.queue.add(media)
        engine.attachSurface(surface)
        engine.load(media, 0L)

        assertTrue(engine.loadIssued)
        val loadCountBefore = fakePort.recordedCommands.count { it.firstOrNull() == "loadfile" }

        // Second attachSurface while load is still in flight (e.g. rapid configuration change)
        engine.attachSurface(surface)

        val loadCountAfter = fakePort.recordedCommands.count { it.firstOrNull() == "loadfile" }
        assertEquals("Should not issue duplicate loadfile while in flight", loadCountBefore, loadCountAfter)
    }

    @Test
    fun `load generation increments and stale events are ignored (D-7)`() {
        val engine = createEngine()
        engine.attachSurface(surface)
        val media1 = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        val media2 = PlayableMedia(id = "2", uri = "http://localhost/2.mp4", title = "2")
        fakeHost.queue.addAll(listOf(media1, media2))

        engine.load(media1, 0L)
        val gen1 = engine.currentLoadGeneration

        engine.load(media2, 0L)
        val gen2 = engine.currentLoadGeneration

        assertTrue(gen2 > gen1)
    }

    @Test
    fun `decoder stage published on transition and video reconfig (D-9)`() {
        val engine = createEngine(initialDecoderMode = DecoderMode.HW)
        engine.attachSurface(surface)
        val media = PlayableMedia(id = "1", uri = "http://localhost/1.mp4", title = "1")
        fakeHost.queue.add(media)
        engine.load(media, 0L)

        fakePort.recordedProperties["path"] = "http://localhost/1.mp4"
        fakePort.recordedProperties["hwdec-current"] = "mediacodec"
        fakePort.recordedProperties["video-codec"] = "h264"
        fakePort.emitEvent(MpvPort.MPV_EVENT_FILE_LOADED)

        // Video reconfig triggers first frame rendered and publishes decoder
        fakePort.emitEvent(MpvPort.MPV_EVENT_VIDEO_RECONFIG)

        assertTrue(engine.firstFrameRecorded)
        assertNotNull(fakeListener.videoDecoderInitialized)
        assertTrue(fakeListener.videoDecoderInitialized?.contains("mediacodec") == true)
    }

    // --- Path match tests (preserved) ---

    @Test
    fun `identical paths match`() {
        assertTrue(MpvCoreEngine.isPathMatch("/a/b.mp4", "/a/b.mp4"))
    }

    @Test
    fun `file scheme prefix is ignored on one side`() {
        assertTrue(MpvCoreEngine.isPathMatch("file:///a/b.mp4", "/a/b.mp4"))
    }

    @Test
    fun `file scheme prefix is ignored on both sides`() {
        assertTrue(MpvCoreEngine.isPathMatch("file:///a/b.mp4", "file:///a/b.mp4"))
    }

    @Test
    fun `different paths do not match`() {
        assertFalse(MpvCoreEngine.isPathMatch("/a/b.mp4", "/a/c.mp4"))
    }

    @Test
    fun `null path never matches`() {
        assertFalse(MpvCoreEngine.isPathMatch(null, "/a/b.mp4"))
        assertFalse(MpvCoreEngine.isPathMatch("/a/b.mp4", null))
        assertFalse(MpvCoreEngine.isPathMatch(null, null))
    }

    @Test
    fun `fd uri only matches itself`() {
        assertTrue(MpvCoreEngine.isPathMatch("fd://12", "fd://12"))
        assertFalse(MpvCoreEngine.isPathMatch("fd://12", "fd://13"))
    }

    @Test
    fun `loadSavedSubtitleSettings delegates to host loadSettings fresh on each call`() {
        val engine = createEngine()

        fakeHost.settings = AppSettings(subtitleSize = "Large")
        assertEquals("Large", engine.loadSavedSubtitleSettings().subtitleSize)

        fakeHost.settings = AppSettings(subtitleSize = "Small")
        assertEquals("Small", engine.loadSavedSubtitleSettings().subtitleSize)
    }

    // --- Property tests: dwidth/dheight, demuxer-cache-time, speed (Step 3.3) ---

    @Test
    fun `demuxer-cache-time property update caches buffered position in milliseconds`() {
        val engine = createEngine()
        assertEquals(0L, engine.bufferedPosition)

        fakePort.emitProperty("demuxer-cache-time", 15.25)
        assertEquals(15250L, engine.bufferedPosition)

        // Negative values coerced to zero
        fakePort.emitProperty("demuxer-cache-time", -5.0)
        assertEquals(0L, engine.bufferedPosition)
    }

    @Test
    fun `dwidth and dheight property updates notify video size listener and cache dimensions`() {
        val engine = createEngine()
        assertEquals(0, engine.videoWidth)
        assertEquals(0, engine.videoHeight)

        fakePort.emitProperty("dwidth", 1920)
        fakePort.emitProperty("dheight", 1080)

        assertEquals(1920, engine.videoWidth)
        assertEquals(1080, engine.videoHeight)
        assertEquals(1920, fakeListener.videoWidth)
        assertEquals(1080, fakeListener.videoHeight)
    }

    @Test
    fun `dwidth and dheight coerce negative dimensions to zero`() {
        val engine = createEngine()

        fakePort.emitProperty("dwidth", -100)
        fakePort.emitProperty("dheight", -200)

        assertEquals(0, engine.videoWidth)
        assertEquals(0, engine.videoHeight)
    }

    @Test
    fun `dwidth and dheight do not re-notify listener when dimensions are unchanged`() {
        val engine = createEngine()

        fakePort.emitProperty("dwidth", 1280)
        fakePort.emitProperty("dheight", 720)
        assertEquals(1280, fakeListener.videoWidth)
        assertEquals(720, fakeListener.videoHeight)

        // Reset listener recording
        fakeListener.videoWidth = null
        fakeListener.videoHeight = null

        // Emit same dimensions
        fakePort.emitProperty("dwidth", 1280)
        fakePort.emitProperty("dheight", 720)

        assertNull(fakeListener.videoWidth)
        assertNull(fakeListener.videoHeight)
    }

    @Test
    fun `videoWidth and videoHeight return zero after engine is released`() {
        val engine = createEngine()
        fakePort.recordedProperties["dwidth"] = 1920
        fakePort.recordedProperties["dheight"] = 1080

        assertEquals(1920, engine.videoWidth)
        assertEquals(1080, engine.videoHeight)

        engine.release()

        assertEquals(0, engine.videoWidth)
        assertEquals(0, engine.videoHeight)
    }

    @Test
    fun `setSpeed writes speed property to port updates cached speed and notifies listener`() {
        val engine = createEngine()

        engine.setSpeed(1.5f)

        assertEquals(1.5, (fakePort.recordedProperties["speed"] as Number).toDouble(), 0.001)
        assertEquals(1.5f, engine.cachedSpeed)
        assertEquals(1.5f, fakeListener.speed)
    }

    @Test
    fun `speed property event from port updates cached speed and notifies listener`() {
        val engine = createEngine()

        fakePort.emitProperty("speed", 0.75)

        assertEquals(0.75f, engine.cachedSpeed)
        assertEquals(0.75f, fakeListener.speed)
    }

    @Test
    fun `speed property event does not re-notify listener when speed is unchanged`() {
        val engine = createEngine()

        fakePort.emitProperty("speed", 1.25)
        assertEquals(1.25f, fakeListener.speed)

        fakeListener.speed = null
        fakePort.emitProperty("speed", 1.25)

        assertNull(fakeListener.speed)
    }

    @Test
    fun `setSpeed is ignored after engine is released`() {
        val engine = createEngine()
        engine.setSpeed(1.2f)
        assertEquals(1.2, (fakePort.recordedProperties["speed"] as Number).toDouble(), 0.001)

        engine.release()
        engine.setSpeed(2.0f)

        // Still 1.2, not updated to 2.0
        assertEquals(1.2, (fakePort.recordedProperties["speed"] as Number).toDouble(), 0.001)
    }
}
