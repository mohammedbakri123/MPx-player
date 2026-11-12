package com.mpx.player.playback.engine

import android.os.SystemClock
import android.util.Log
import com.mpx.player.playback.EnginePlaybackMetrics
import kotlin.math.abs

/**
 * Tracks performance metrics and diagnostics for [MpvCoreEngine] (R-7).
 * Encapsulates load-to-ready time, first-frame latency, seek latency,
 * decoder fallback counts, and hard error counts under a dedicated lock.
 */
internal class MpvMetricsTracker(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val metricsLock = Any()

    private var loadStartMs: Long = 0L

    var firstFrameRecorded: Boolean = false
        private set

    private var lastTimeToReadyMs: Long? = null
    private var lastTimeToFirstFrameMs: Long? = null
    private var lastSeekLatencyMs: Long? = null
    private var seekStartMs: Long? = null
    private var seekTargetMs: Long? = null
    private var decoderFallbackCount: Int = 0
    private var hardErrorCount: Int = 0

    fun recordLoadStarted() {
        synchronized(metricsLock) {
            loadStartMs = clock()
            firstFrameRecorded = false
            lastTimeToFirstFrameMs = null
            lastTimeToReadyMs = null
        }
    }

    fun recordReady() {
        synchronized(metricsLock) {
            if (loadStartMs != 0L && lastTimeToReadyMs == null) {
                lastTimeToReadyMs = clock() - loadStartMs
                logMetrics("ready", "ready_ms=$lastTimeToReadyMs")
            }
        }
    }

    fun recordFirstFrame() {
        synchronized(metricsLock) {
            if (loadStartMs != 0L && !firstFrameRecorded) {
                lastTimeToFirstFrameMs = clock() - loadStartMs
                firstFrameRecorded = true
                logMetrics("first_frame", "first_frame_ms=$lastTimeToFirstFrameMs")
            }
        }
    }

    fun recordSeekStarted(targetMs: Long) {
        synchronized(metricsLock) {
            seekStartMs = clock()
            seekTargetMs = targetMs
        }
    }

    fun recordPositionUpdate(currentPositionMs: Long) {
        synchronized(metricsLock) {
            val start = seekStartMs
            val target = seekTargetMs
            if (start != null && target != null &&
                abs(currentPositionMs - target) <= 500L
            ) {
                lastSeekLatencyMs = clock() - start
                logMetrics("seek", "seek_ms=$lastSeekLatencyMs target_ms=$target")
                seekStartMs = null
                seekTargetMs = null
            }
        }
    }

    fun recordDecoderFallback() {
        synchronized(metricsLock) {
            decoderFallbackCount++
        }
    }

    fun recordHardError() {
        synchronized(metricsLock) {
            hardErrorCount++
        }
    }

    fun getPlaybackMetrics(): EnginePlaybackMetrics =
        synchronized(metricsLock) {
            EnginePlaybackMetrics(
                lastTimeToReadyMs = lastTimeToReadyMs,
                lastTimeToFirstFrameMs = lastTimeToFirstFrameMs,
                lastSeekLatencyMs = lastSeekLatencyMs,
                decoderFallbackCount = decoderFallbackCount,
                hardErrorCount = hardErrorCount,
            )
        }

    fun reset() {
        synchronized(metricsLock) {
            loadStartMs = 0L
            firstFrameRecorded = false
            lastTimeToReadyMs = null
            lastTimeToFirstFrameMs = null
            lastSeekLatencyMs = null
            seekStartMs = null
            seekTargetMs = null
        }
    }

    private fun logMetrics(tag: String, detail: String) {
        Log.i("MpxMetrics", "$tag $detail")
    }
}
