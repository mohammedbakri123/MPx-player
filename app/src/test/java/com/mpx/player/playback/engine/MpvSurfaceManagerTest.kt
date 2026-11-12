package com.mpx.player.playback.engine

import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MpvSurfaceManager] (Phase 8, Step 3.4 / PI-2).
 *
 * Verifies observable behaviour:
 * - Surface attach/detach barriers, force-window options, and surface lifecycle.
 * - Sizing notifications to mpv ("android-surface-size").
 * - VO restore logic: "vo=gpu" on attach, surfaceChanged, time-pos, and vo property changes.
 * - VO restore suppression when a load is about to initialize VO itself.
 */
class MpvSurfaceManagerTest {

    private lateinit var fakePort: FakeMpvPort
    private lateinit var surfaceManager: MpvSurfaceManager
    private lateinit var surface: Surface

    @Before
    fun setUp() {
        fakePort = FakeMpvPort()
        surfaceManager = MpvSurfaceManager(fakePort)
        surface = Surface(SurfaceTexture(0))
    }

    @Test
    fun `attachSurface with willLoad false attaches surface sets force-window and sets vo to gpu`() {
        assertFalse(surfaceManager.hasSurface)
        assertNull(surfaceManager.currentSurface)

        surfaceManager.attachSurface(surface, willLoad = false)

        assertTrue(surfaceManager.hasSurface)
        assertEquals(surface, surfaceManager.currentSurface)
        assertEquals(surface, fakePort.attachedSurface)
        assertEquals("yes", fakePort.recordedProperties["force-window"])
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertFalse(surfaceManager.voIsNull)
    }

    @Test
    fun `attachSurface with willLoad true and voIsNull false skips setting vo property`() {
        surfaceManager.voIsNull = false

        surfaceManager.attachSurface(surface, willLoad = true)

        assertTrue(surfaceManager.hasSurface)
        assertEquals(surface, fakePort.attachedSurface)
        assertEquals("yes", fakePort.recordedProperties["force-window"])
        // vo property should NOT be touched because loadfile will initialize VO
        assertNull(fakePort.recordedProperties["vo"])
    }

    @Test
    fun `attachSurface with willLoad true and voIsNull true restores vo to gpu`() {
        surfaceManager.voIsNull = true

        surfaceManager.attachSurface(surface, willLoad = true)

        assertTrue(surfaceManager.hasSurface)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertFalse(surfaceManager.voIsNull)
    }

    @Test
    fun `detachSurface sets currentSurface null voIsNull true vo to null force-window to no and detaches`() {
        surfaceManager.attachSurface(surface, willLoad = false)
        assertTrue(surfaceManager.hasSurface)

        surfaceManager.detachSurface()

        assertFalse(surfaceManager.hasSurface)
        assertNull(surfaceManager.currentSurface)
        assertTrue(surfaceManager.voIsNull)
        assertNull(fakePort.attachedSurface)
        assertEquals("null", fakePort.recordedProperties["vo"])
        assertEquals("no", fakePort.recordedProperties["force-window"])
    }

    @Test
    fun `onSurfaceChanged sets android-surface-size and restores vo to gpu when voIsNull is true`() {
        surfaceManager.attachSurface(surface, willLoad = false)
        surfaceManager.voIsNull = true

        surfaceManager.onSurfaceChanged(1080, 1920)

        assertEquals("1080x1920", fakePort.recordedProperties["android-surface-size"])
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertFalse(surfaceManager.voIsNull)
    }

    @Test
    fun `onSurfaceChanged ignores non-positive dimensions`() {
        surfaceManager.onSurfaceChanged(-1, 1080)
        assertNull(fakePort.recordedProperties["android-surface-size"])

        surfaceManager.onSurfaceChanged(1080, 0)
        assertNull(fakePort.recordedProperties["android-surface-size"])
    }

    @Test
    fun `restoreVoIfNull returns false and does not touch vo when no surface attached`() {
        surfaceManager.voIsNull = true

        val restored = surfaceManager.restoreVoIfNull("test")

        assertFalse(restored)
        assertNull(fakePort.recordedProperties["vo"])
        assertTrue(surfaceManager.voIsNull)
    }

    @Test
    fun `restoreVoIfNull returns false when voIsNull is false`() {
        surfaceManager.attachSurface(surface, willLoad = false)
        fakePort.recordedProperties.remove("vo")
        surfaceManager.voIsNull = false

        val restored = surfaceManager.restoreVoIfNull("test")

        assertFalse(restored)
        assertNull(fakePort.recordedProperties["vo"])
    }

    @Test
    fun `restoreVoIfNull restores vo to gpu when surface attached and voIsNull is true`() {
        surfaceManager.attachSurface(surface, willLoad = false)
        fakePort.recordedProperties.remove("vo")
        surfaceManager.voIsNull = true

        val restored = surfaceManager.restoreVoIfNull("test")

        assertTrue(restored)
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertFalse(surfaceManager.voIsNull)
    }

    @Test
    fun `onVoPropertyChanged when mpv reports null sets voIsNull and restores immediately if surface attached`() {
        surfaceManager.attachSurface(surface, willLoad = false)
        fakePort.recordedProperties["vo"] = "null"

        surfaceManager.onVoPropertyChanged()

        // Restored immediately to gpu because currentSurface != null
        assertEquals("gpu", fakePort.recordedProperties["vo"])
        assertFalse(surfaceManager.voIsNull)
    }

    @Test
    fun `onVoPropertyChanged when mpv reports null and no surface attached sets voIsNull to true`() {
        fakePort.recordedProperties["vo"] = "null"

        surfaceManager.onVoPropertyChanged()

        assertTrue(surfaceManager.voIsNull)
        assertEquals("null", fakePort.recordedProperties["vo"])
    }

    @Test
    fun `onVoPropertyChanged when mpv reports live vo clears voIsNull`() {
        surfaceManager.voIsNull = true
        fakePort.recordedProperties["vo"] = "gpu"

        surfaceManager.onVoPropertyChanged()

        assertFalse(surfaceManager.voIsNull)
    }
}
