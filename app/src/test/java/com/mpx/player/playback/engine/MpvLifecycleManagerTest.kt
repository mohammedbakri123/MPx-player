package com.mpx.player.playback.engine

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

class MpvLifecycleManagerTest {

    private lateinit var testContext: TestContext
    private lateinit var fakePort: FakeMpvPort
    private lateinit var fakeHost: TestHost

    private class TestHost : MpvCoreEngine.Host {
        var queue = mutableListOf<PlayableMedia>()
        var mediaIndex = 0
        var currentHwdecMode: DecoderMode = DecoderMode.HW
        var currentHwdecVal: String = "mediacodec"
        var settings: AppSettings = AppSettings()

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
        override fun onLoadStarted() {}
        override fun onLoadTargetResolved(mediaId: String) {}
        override fun onTrackListChanged() {}
        override fun onFileLoaded() {}
        override fun getCurrentQueue(): List<PlayableMedia> = queue
        override fun getCurrentMediaIndex(): Int = mediaIndex
        override fun loadSettings(): AppSettings = settings
    }

    private val eventObserver = object : MpvPort.EventObserver {
        override fun eventProperty(property: String) {}
        override fun event(eventId: Int, endFile: MpvPort.EndFile?) {}
    }

    private val logObserver = MpvPort.LogObserver { _, _, _ -> }

    @Before
    fun setUp() {
        testContext = TestContext()
        fakePort = FakeMpvPort()
        fakeHost = TestHost()
    }

    private fun createManager(port: MpvPort = fakePort): MpvLifecycleManager =
        MpvLifecycleManager(
            appContext = testContext,
            port = port,
            host = fakeHost,
            eventObserver = eventObserver,
            logObserver = logObserver,
        )

    @Test
    fun `initialize configures options, sets force-window, and marks initialized`() {
        val manager = createManager()
        var postInitCalled = false
        var initFailedCalled = false

        assertFalse(manager.mpvInitialized)
        assertFalse(manager.isReleased)
        assertNull(manager.pendingInitError)

        manager.initialize(
            initialDecoderMode = DecoderMode.HW,
            loadSavedSettings = { AppSettings() },
            applyPostInit = { postInitCalled = true },
            onInitFailed = { initFailedCalled = true }
        )

        assertTrue(manager.mpvInitialized)
        assertFalse(manager.isReleased)
        assertTrue(postInitCalled)
        assertFalse(initFailedCalled)
        assertNull(manager.pendingInitError)

        // Verify key pre-init options set
        val optionsMap = fakePort.recordedOptions.toMap()
        assertEquals("yes", optionsMap["config"])
        assertEquals("yes", optionsMap["idle"])
        assertEquals("mediacodec", optionsMap["hwdec"])

        // Verify runtime force-window set to no
        assertEquals("no", fakePort.recordedProperties["force-window"])

        // Verify observers registered
        assertTrue(fakePort.eventObservers.contains(eventObserver))
        assertTrue(fakePort.logObservers.contains(logObserver))
    }

    @Test
    fun `initialize captures native error into pendingInitError without throwing`() {
        val failingPort = FakeMpvPort(initException = RuntimeException("native crash"))
        val manager = createManager(port = failingPort)
        var initFailedCalled = false

        manager.initialize(
            initialDecoderMode = DecoderMode.HW,
            loadSavedSettings = { AppSettings() },
            applyPostInit = {},
            onInitFailed = { initFailedCalled = true }
        )

        assertFalse(manager.mpvInitialized)
        assertTrue(initFailedCalled)
        assertNotNull(manager.pendingInitError)
        assertEquals("native crash", manager.pendingInitError?.message)
    }

    @Test
    fun `setOption records rejected option when port returns non-zero`() {
        val manager = createManager()
        fakePort.optionReturnCode = -1

        val rc = manager.setOption("unknown-opt", "val")
        assertEquals(-1, rc)

        val rejected = manager.rejectedOptionsText()
        assertTrue(rejected.contains("unknown-opt=val (rc=-1)"))
    }

    @Test
    fun `setOption returns 0 and leaves rejectedOptions empty on success`() {
        val manager = createManager()
        fakePort.optionReturnCode = 0

        val rc = manager.setOption("config", "yes")
        assertEquals(0, rc)
        assertEquals("", manager.rejectedOptionsText())
    }

    @Test
    fun `release sets isReleased, runs teardown hook, and unregisters observers under lock`() {
        val manager = createManager()
        manager.initialize(
            initialDecoderMode = DecoderMode.HW,
            loadSavedSettings = { AppSettings() },
            applyPostInit = {},
            onInitFailed = {}
        )

        var teardownCalled = false
        manager.release {
            teardownCalled = true
        }

        assertTrue(manager.isReleased)
        assertTrue(teardownCalled)
        assertTrue(fakePort.isDestroyed)
        assertFalse(fakePort.eventObservers.contains(eventObserver))
        assertFalse(fakePort.logObservers.contains(logObserver))
    }

    @Test
    fun `release is idempotent and safe against double-release`() {
        val manager = createManager()
        manager.initialize(
            initialDecoderMode = DecoderMode.HW,
            loadSavedSettings = { AppSettings() },
            applyPostInit = {},
            onInitFailed = {}
        )

        var teardownCount = 0
        manager.release { teardownCount++ }
        assertEquals(1, teardownCount)
        assertTrue(manager.isReleased)

        // Second release must be a no-op
        manager.release { teardownCount++ }
        assertEquals(1, teardownCount)
        assertTrue(manager.isReleased)
    }
}
