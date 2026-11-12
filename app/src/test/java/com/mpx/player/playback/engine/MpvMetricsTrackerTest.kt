package com.mpx.player.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvMetricsTrackerTest {

    private var currentTimeMs: Long = 1000L
    private val tracker = MpvMetricsTracker(clock = { currentTimeMs })

    @Test
    fun `recordLoadStarted resets metrics state and records start time`() {
        // Prime tracker with old values
        tracker.recordLoadStarted()
        currentTimeMs += 120L
        tracker.recordReady()
        currentTimeMs += 50L
        tracker.recordFirstFrame()

        assertTrue(tracker.firstFrameRecorded)
        assertNotNull(tracker.getPlaybackMetrics().lastTimeToReadyMs)
        assertNotNull(tracker.getPlaybackMetrics().lastTimeToFirstFrameMs)

        // Fresh load start
        currentTimeMs = 5000L
        tracker.recordLoadStarted()

        assertFalse(tracker.firstFrameRecorded)
        val metrics = tracker.getPlaybackMetrics()
        assertNull(metrics.lastTimeToReadyMs)
        assertNull(metrics.lastTimeToFirstFrameMs)
    }

    @Test
    fun `recordReady computes latency and ignores duplicate calls`() {
        tracker.recordLoadStarted()
        currentTimeMs += 250L

        tracker.recordReady()
        assertEquals(250L, tracker.getPlaybackMetrics().lastTimeToReadyMs)

        // Subsequent ready calls do not overwrite
        currentTimeMs += 500L
        tracker.recordReady()
        assertEquals(250L, tracker.getPlaybackMetrics().lastTimeToReadyMs)
    }

    @Test
    fun `recordReady without loadStart does nothing`() {
        tracker.recordReady()
        assertNull(tracker.getPlaybackMetrics().lastTimeToReadyMs)
    }

    @Test
    fun `recordFirstFrame computes latency and marks firstFrameRecorded`() {
        tracker.recordLoadStarted()
        currentTimeMs += 350L

        assertFalse(tracker.firstFrameRecorded)
        tracker.recordFirstFrame()
        assertTrue(tracker.firstFrameRecorded)
        assertEquals(350L, tracker.getPlaybackMetrics().lastTimeToFirstFrameMs)

        // Duplicate call does not overwrite
        currentTimeMs += 200L
        tracker.recordFirstFrame()
        assertEquals(350L, tracker.getPlaybackMetrics().lastTimeToFirstFrameMs)
    }

    @Test
    fun `recordSeekStarted and recordPositionUpdate computes seek latency when within 500ms threshold`() {
        tracker.recordSeekStarted(10_000L)
        currentTimeMs += 85L

        // Position update arrives at 10_200L (diff is 200ms <= 500ms threshold)
        tracker.recordPositionUpdate(10_200L)

        assertEquals(85L, tracker.getPlaybackMetrics().lastSeekLatencyMs)

        // Following position update does not trigger without a new seek
        currentTimeMs += 50L
        tracker.recordPositionUpdate(10_250L)
        assertEquals(85L, tracker.getPlaybackMetrics().lastSeekLatencyMs)
    }

    @Test
    fun `recordPositionUpdate ignores updates exceeding 500ms delta from target`() {
        tracker.recordSeekStarted(10_000L)
        currentTimeMs += 50L

        // Position update arrives at 5_000L (diff is 5000ms > 500ms threshold)
        tracker.recordPositionUpdate(5_000L)

        assertNull(tracker.getPlaybackMetrics().lastSeekLatencyMs)

        // Subsequent update lands within 500ms (10_100L)
        currentTimeMs += 30L
        tracker.recordPositionUpdate(10_100L)
        assertEquals(80L, tracker.getPlaybackMetrics().lastSeekLatencyMs)
    }

    @Test
    fun `counters increment correctly`() {
        assertEquals(0, tracker.getPlaybackMetrics().decoderFallbackCount)
        assertEquals(0, tracker.getPlaybackMetrics().hardErrorCount)

        tracker.recordDecoderFallback()
        tracker.recordDecoderFallback()
        assertEquals(2, tracker.getPlaybackMetrics().decoderFallbackCount)

        tracker.recordHardError()
        assertEquals(1, tracker.getPlaybackMetrics().hardErrorCount)
    }

    @Test
    fun `reset clears timing and frame flags`() {
        tracker.recordLoadStarted()
        currentTimeMs += 100L
        tracker.recordReady()
        tracker.recordFirstFrame()
        tracker.recordSeekStarted(5000L)
        currentTimeMs += 40L
        tracker.recordPositionUpdate(5000L)

        tracker.reset()

        assertFalse(tracker.firstFrameRecorded)
        val metrics = tracker.getPlaybackMetrics()
        assertNull(metrics.lastTimeToReadyMs)
        assertNull(metrics.lastTimeToFirstFrameMs)
        assertNull(metrics.lastSeekLatencyMs)
    }
}
