package com.mpx.player.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private class RecordingHost : SleepTimerHost {
        var startedMinutes: Int? = null
        var startedRemainingSeconds: Long? = null
        val ticks = mutableListOf<Long>()
        var expiredCount: Int = 0
        var cancelledCount: Int = 0

        override fun onSleepTimerStarted(minutes: Int, remainingSeconds: Long) {
            startedMinutes = minutes
            startedRemainingSeconds = remainingSeconds
        }

        override fun onSleepTimerTick(remainingSeconds: Long) {
            ticks.add(remainingSeconds)
        }

        override fun onSleepTimerExpired() {
            expiredCount++
        }

        override fun onSleepTimerCancelled() {
            cancelledCount++
        }
    }

    @Test
    fun `start with positive minutes notifies host of start, ticks, and expiry`() = runTest {
        val host = RecordingHost()
        val timer = SleepTimer(
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            host = host,
        )

        timer.start(1)
        assertEquals(1, host.startedMinutes)
        assertEquals(60L, host.startedRemainingSeconds)

        // 1001ms to clear the first 1s delay boundary
        advanceTimeBy(1001L)
        assertEquals(listOf(59L), host.ticks)
        assertEquals(0, host.expiredCount)

        // Advance remainder (59_000ms) to hit the 60_001ms boundary
        advanceTimeBy(59_000L)
        assertEquals(1, host.expiredCount)
    }

    @Test
    fun `start with zero or negative minutes cancels timer`() = runTest {
        val host = RecordingHost()
        val timer = SleepTimer(
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            host = host,
        )

        timer.start(5)
        assertEquals(5, host.startedMinutes)

        timer.start(0)
        assertEquals(1, host.cancelledCount)

        timer.start(-1)
        assertEquals(2, host.cancelledCount)
    }

    @Test
    fun `cancel stops countdown and notifies host`() = runTest {
        val host = RecordingHost()
        val timer = SleepTimer(
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            host = host,
        )

        timer.start(5)
        timer.cancel()
        assertEquals(1, host.cancelledCount)

        advanceTimeBy(60_000L)
        assertTrue(host.ticks.isEmpty())
        assertEquals(0, host.expiredCount)
    }
}
