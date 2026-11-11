package com.mpx.player.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Host interface for [SleepTimer]. The coordinator implements this
 * to update its published [PlaybackState] and execute the expiry action
 * (pausing playback).
 */
interface SleepTimerHost {
    fun onSleepTimerStarted(minutes: Int, remainingSeconds: Long)
    fun onSleepTimerTick(remainingSeconds: Long)
    fun onSleepTimerExpired()
    fun onSleepTimerCancelled()
}

/**
 * Manages the sleep timer countdown and cancellation (migration Phase 4, step 1: R-1).
 *
 * Owned by [PlaybackCoordinator]. When expired, triggers [SleepTimerHost.onSleepTimerExpired],
 * which pauses playback and clears the timer state fields in the published [PlaybackState].
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val host: SleepTimerHost,
) {
    private var job: Job? = null

    /**
     * Starts or resets the sleep timer countdown for [minutes].
     * If [minutes] <= 0, cancels any running timer.
     */
    fun start(minutes: Int) {
        job?.cancel()
        if (minutes <= 0) {
            cancel()
            return
        }
        val durationMs = minutes * 60 * 1000L
        val targetElapsedRealtime = clock() + durationMs
        host.onSleepTimerStarted(minutes, durationMs / 1000L)

        job = scope.launch {
            while (isActive) {
                delay(1000L)
                val remainingMs = targetElapsedRealtime - clock()
                if (remainingMs <= 0L) {
                    host.onSleepTimerExpired()
                    break
                } else {
                    val remainingSec = (remainingMs + 999L) / 1000L
                    host.onSleepTimerTick(remainingSec)
                }
            }
        }
    }

    /**
     * Cancels any running sleep timer and notifies the host to clear timer state.
     */
    fun cancel() {
        job?.cancel()
        job = null
        host.onSleepTimerCancelled()
    }
}
