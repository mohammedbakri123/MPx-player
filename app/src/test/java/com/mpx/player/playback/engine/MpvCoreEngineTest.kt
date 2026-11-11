package com.mpx.player.playback.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration Phase 4, step 1: unit tests for the extracted
 * [MpvCoreEngine].
 *
 * The engine itself is not unit-testable: constructing it creates a
 * real MPV instance (native lib), so it is verified by the CI build
 * and on device only. The one exception is [MpvCoreEngine.isPathMatch],
 * pure path logic that lives in the companion object precisely so it
 * can be tested without mpv. These tests pin it: it is the FILE_LOADED
 * staleness check (issue #332) and the pending-subtitle match, so a
 * wrong answer there drops a real load or dispatches subtitles to the
 * wrong file.
 */
class MpvCoreEngineTest {

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
}
