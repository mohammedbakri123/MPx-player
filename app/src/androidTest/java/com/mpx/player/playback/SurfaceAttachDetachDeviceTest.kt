package com.mpx.player.playback

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.engine.FakeMpvPort
import com.mpx.player.playback.engine.MpvCoreEngine
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.RealEngineHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented surface attach/detach device test (§7, §4).
 *
 * Runs on device or emulator with a real Android [Surface] and [SurfaceTexture].
 * Verifies the full surface lifecycle against [MpvCoreEngine] through [FakeMpvPort]:
 * 1. Initial attachSurface: passes real Surface to port and sets "force-window=yes".
 * 2. detachSurface: clears port surface, sets "vo=null", and sets voIsNull flag.
 * 3. surfaceChanged: delivers Android surface dimensions and restores "vo=gpu".
 * 4. Rapid attach/detach alternation: preserves state consistency without crashing.
 * 5. Re-attaching surface when load is deferred flushes the deferred load.
 * 6. Release: cleans up observers, destroys port, and releases native surface resources.
 */
@RunWith(AndroidJUnit4::class)
class SurfaceAttachDetachDeviceTest {

    private lateinit var context: Context
    private lateinit var fakePort: FakeMpvPort
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var realSurface: Surface
    private lateinit var testHost: DeviceTestHost
    private lateinit var testListener: DeviceTestListener
    // Phase 6 audit: MpvCoreEngine takes an EngineHandler, not a raw Handler.
    private val mainHandler = RealEngineHandler()

    private class DeviceTestHost : MpvCoreEngine.Host {
        var queue = mutableListOf<PlayableMedia>()
        var mediaIndex = 0
        var settings = AppSettings()

        override fun hwdecValueFor(mode: DecoderMode): String = "mediacodec"
        override fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = "mediacodec"
        override fun applyHwdecForMode(mode: DecoderMode) {}
        override fun applyPostInitSettings(core: MpvCoreEngine) {}
        override fun onLoadStarted() {}
        override fun onLoadTargetResolved(mediaId: String) {}
        override fun onTrackListChanged() {}
        override fun onFileLoaded() {}
        override fun getCurrentQueue(): List<PlayableMedia> = queue
        override fun getCurrentMediaIndex(): Int = mediaIndex
        override fun loadSettings(): AppSettings = settings
    }

    private class DeviceTestListener : PlayerEngine.Listener {
        var playbackState: Int? = null
        var isPlaying: Boolean? = null
        var videoWidth: Int? = null
        var videoHeight: Int? = null

        override fun onPlaybackStateChanged(playbackState: Int) {
            this.playbackState = playbackState
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this.isPlaying = isPlaying
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            this.videoWidth = width
            this.videoHeight = height
        }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fakePort = FakeMpvPort()
        surfaceTexture = SurfaceTexture(101)
        realSurface = Surface(surfaceTexture)
        testHost = DeviceTestHost()
        testListener = DeviceTestListener()
    }

    @After
    fun tearDown() {
        realSurface.release()
        surfaceTexture.release()
    }

    private fun createEngine(): MpvCoreEngine {
        val engine = MpvCoreEngine(
            context = context,
            initialDecoderMode = DecoderMode.HW,
            host = testHost,
            port = fakePort,
            mainHandler = mainHandler,
        )
        engine.setListener(testListener)
        return engine
    }

    @Test
    fun attachSurface_bindsRealSurfaceAndConfiguresForceWindow() {
        val engine = createEngine()

        engine.attachSurface(realSurface)

        assertEquals(realSurface, fakePort.attachedSurface)
        assertEquals("yes", fakePort.recordedProperties["force-window"])
        assertFalse(engine.voIsNull)
    }

    @Test
    fun detachSurface_nullsPortSurfaceAndSetsVoNull() {
        val engine = createEngine()
        engine.attachSurface(realSurface)

        engine.detachSurface()

        assertNull(fakePort.attachedSurface)
        assertTrue(engine.voIsNull)
        assertEquals("null", fakePort.recordedProperties["vo"])
    }

    @Test
    fun surfaceChanged_restoresVoGpuAndUpdatesDimensions() {
        val engine = createEngine()
        engine.attachSurface(realSurface)
        engine.detachSurface()
        assertTrue(engine.voIsNull)

        // Surface created / changed
        engine.attachSurface(realSurface)
        engine.onSurfaceChanged(1920, 1080)

        assertFalse(engine.voIsNull)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertEquals("1920x1080", fakePort.recordedProperties["android-surface-size"])
    }

    @Test
    fun rapidAttachDetachCycles_maintainStateConsistency() {
        val engine = createEngine()

        for (i in 1..10) {
            engine.attachSurface(realSurface)
            assertEquals(realSurface, fakePort.attachedSurface)
            assertFalse(engine.voIsNull)

            engine.detachSurface()
            assertNull(fakePort.attachedSurface)
            assertTrue(engine.voIsNull)
        }

        // Final reattach leaves engine in consistent attached state
        engine.attachSurface(realSurface)
        assertEquals(realSurface, fakePort.attachedSurface)
        assertFalse(engine.voIsNull)
    }

    @Test
    fun attachSurface_flushesDeferredLoad() {
        val engine = createEngine()
        val media = PlayableMedia(id = "item_1", uri = "content://media/1", title = "Test Video")
        testHost.queue.add(media)

        // Load before surface attached -> deferred
        engine.load(media, resumeMs = 0L)
        assertNotNull(engine.pendingLoad)
        assertTrue(fakePort.recordedCommands.none { it.firstOrNull() == "loadfile" })

        // Attach surface flushes the load
        engine.attachSurface(realSurface)
        assertNull(engine.pendingLoad)
        val loadCmd = fakePort.recordedCommands.find { it.firstOrNull() == "loadfile" }
        assertNotNull(loadCmd)
        assertEquals("content://media/1", loadCmd?.get(1))
    }

    @Test
    fun release_destroysPortAndIgnoresSubsequentSurfaceOperations() {
        val engine = createEngine()
        engine.attachSurface(realSurface)

        engine.release()

        assertTrue(engine.isReleased)
        assertTrue(fakePort.isDestroyed)

        // Subsequent attach or detach should be a safe no-op
        engine.detachSurface()
        engine.attachSurface(realSurface)
    }
}
