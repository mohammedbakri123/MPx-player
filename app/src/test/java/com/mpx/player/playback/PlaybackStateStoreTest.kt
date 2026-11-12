package com.mpx.player.playback

import com.mpx.player.playback.engine.MpvPlaybackException
import com.mpx.player.playback.engine.PlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateStoreTest {

    private class FakeStrings : CoordinatorStrings {
        override fun codecError(codecName: String): String = "Codec error: $codecName"
        override fun subtitleLoaded(displayName: String): String = "Loaded: $displayName"
        override fun subtitleFailed(displayName: String): String = "Failed: $displayName"
        override fun backgroundActiveBanner(): String = "Background active"
    }

    private fun video(id: String) = PlayableMedia(
        id = id,
        title = "Title $id",
        uri = "content://media/$id",
        isVideo = true,
    )

    private fun audio(id: String) = PlayableMedia(
        id = id,
        title = "Audio $id",
        uri = "content://media/$id",
        isVideo = false,
    )

    @Test
    fun `initial state reflects supplied decoder mode and defaults`() {
        val storeHw = PlaybackStateStore(initialDecoderMode = DecoderMode.HW)
        assertEquals(DecoderMode.HW, storeHw.playbackState.value.decoderMode)
        assertEquals(DecoderMode.HW, storeHw.state.decoderMode)
        assertEquals(DecoderMode.HW, storeHw.value.decoderMode)

        val storeSw = PlaybackStateStore(initialDecoderMode = DecoderMode.SW)
        assertEquals(DecoderMode.SW, storeSw.playbackState.value.decoderMode)
    }

    @Test
    fun `update transforms state atomically`() {
        val store = PlaybackStateStore()
        store.update { it.copy(isLocked = true, speed = 1.25f) }

        assertTrue(store.state.isLocked)
        assertEquals(1.25f, store.state.speed)
        assertEquals(store.state, store.playbackState.value)
    }

    @Test
    fun `publishSession updates session fields and resets video dimensions`() {
        val store = PlaybackStateStore()
        store.update { it.copy(videoWidth = 1920, videoHeight = 1080) }

        val items = listOf(video("1"), video("2"))
        store.publishSession(
            items = items,
            targetIndex = 0,
            targetItem = items[0],
            resumedFromMs = 1200L,
            speed = 1.5f,
            shuffleEnabled = true,
            hasPrevious = false,
            hasNext = true,
        )

        val state = store.state
        assertEquals(items[0], state.currentItem)
        assertEquals(items, state.queue)
        assertEquals(0, state.currentIndex)
        assertEquals(1200L, state.resumedFromMs)
        assertEquals(1.5f, state.speed)
        assertTrue(state.shuffleEnabled)
        assertFalse(state.hasPrevious)
        assertTrue(state.hasNext)
        assertTrue("Video session should initially be preparing", state.isPreparing)
        assertEquals(0, state.videoWidth)
        assertEquals(0, state.videoHeight)
    }

    @Test
    fun `publishSession for audio does not mark preparing`() {
        val store = PlaybackStateStore()
        val item = audio("a1")
        store.publishSession(
            items = listOf(item),
            targetIndex = 0,
            targetItem = item,
            resumedFromMs = null,
            speed = 1.0f,
            shuffleEnabled = false,
            hasPrevious = false,
            hasNext = false,
        )

        assertFalse(store.state.isPreparing)
    }

    @Test
    fun `publishQueueOrder updates queue order and navigation flags`() {
        val store = PlaybackStateStore()
        val items = listOf(video("2"), video("1"))
        store.publishQueueOrder(
            items = items,
            currentIndex = 1,
            shuffleEnabled = true,
            hasPrevious = true,
            hasNext = false,
        )

        assertEquals(items, store.state.queue)
        assertEquals(1, store.state.currentIndex)
        assertTrue(store.state.shuffleEnabled)
        assertTrue(store.state.hasPrevious)
        assertFalse(store.state.hasNext)
    }

    @Test
    fun `publishShuffleEnabled and publishRepeatMode update state`() {
        val store = PlaybackStateStore()
        store.publishShuffleEnabled(true)
        assertTrue(store.state.shuffleEnabled)

        store.publishRepeatMode(RepeatMode.ALL)
        assertEquals(RepeatMode.ALL, store.state.repeatMode)
    }

    @Test
    fun `publishIsPlaying and publishEngineState update engine flags`() {
        val store = PlaybackStateStore()
        store.publishIsPlaying(true)
        assertTrue(store.state.isPlaying)

        store.publishEngineState(
            playbackState = PlayerEngine.STATE_READY,
            isPreparing = false,
            userNotification = "Notification",
        )
        assertEquals(PlayerEngine.STATE_READY, store.state.playbackState)
        assertFalse(store.state.isPreparing)
        assertEquals("Notification", store.state.userNotification)
    }

    @Test
    fun `publishTransition updates current item and resets video size`() {
        val store = PlaybackStateStore()
        store.update { it.copy(videoWidth = 1920, videoHeight = 1080) }

        val item = video("v2")
        store.publishTransition(
            item = item,
            index = 1,
            hasPrevious = true,
            hasNext = false,
            resumedFromMs = 5000L,
        )

        assertEquals(item, store.state.currentItem)
        assertEquals(1, store.state.currentIndex)
        assertTrue(store.state.hasPrevious)
        assertFalse(store.state.hasNext)
        assertEquals(5000L, store.state.resumedFromMs)
        assertEquals(0, store.state.videoWidth)
        assertEquals(0, store.state.videoHeight)
    }

    @Test
    fun `publishSpeed and publishVideoSize update dimensions and rate`() {
        val store = PlaybackStateStore()
        store.publishSpeed(2.0f)
        assertEquals(2.0f, store.state.speed)

        store.publishVideoSize(1280, 720)
        assertEquals(1280, store.state.videoWidth)
        assertEquals(720, store.state.videoHeight)
    }

    @Test
    fun `publishDecoderInitialized updates decoder info`() {
        val store = PlaybackStateStore()
        store.publishDecoderInitialized("mediacodec", "fallback to sw")
        assertEquals("mediacodec", store.state.activeDecoderName)
        assertEquals("fallback to sw", store.state.decoderFallbackNote)
    }

    @Test
    fun `publishEngineError updates state notification and dispatches event`() {
        val strings = FakeStrings()
        val store = PlaybackStateStore(strings = strings)
        val events = mutableListOf<PlaybackEvent>()
        store.setEventListener { events.add(it) }

        // MpvPlaybackException with codec name
        val codecException = MpvPlaybackException("error", codecName = "hevc")
        store.publishEngineError(codecException)
        assertFalse(store.state.isPreparing)
        assertEquals("Codec error: hevc", store.state.userNotification)
        assertEquals(listOf(PlaybackEvent.EngineError(codecException)), events)

        // Generic Exception
        val genericException = RuntimeException("io failure")
        store.publishEngineError(genericException)
        assertEquals("Playback error: io failure", store.state.userNotification)
        assertEquals(PlaybackEvent.EngineError(genericException), events[1])
    }

    @Test
    fun `subtitle helpers format messages through strings and update state`() {
        val strings = FakeStrings()
        val store = PlaybackStateStore(strings = strings)

        assertEquals("Loaded: en.srt", store.subtitleLoadedMessage("en.srt"))
        assertEquals("Failed: en.srt", store.subtitleFailedMessage("en.srt"))

        store.showNotification("test notice")
        assertEquals("test notice", store.state.userNotification)
    }

    @Test
    fun `event dispatch delivers immediately when listener is attached`() {
        val store = PlaybackStateStore()
        val events = mutableListOf<PlaybackEvent>()
        store.setEventListener { events.add(it) }

        store.startPositionTicker()
        store.stopPositionTicker()
        store.refreshPosition()
        store.refreshPositionAndTracks()
        store.refreshTracksState()
        store.flushPosition()
        store.armLoadWatchdog()
        store.cancelLoadWatchdog()
        store.startBackgroundService()
        store.clearAutoDetectAttempts()
        store.maybeAutoDetectSubtitles()

        val item = video("v1")
        store.recordHistory(item, 500L)
        store.recordPauseHistory(item, 500L, 10000L)
        store.commitSubtitleItem(1, item, "sub added")

        val expected = listOf(
            PlaybackEvent.StartPositionTicker,
            PlaybackEvent.StopPositionTicker,
            PlaybackEvent.RefreshPosition,
            PlaybackEvent.RefreshPositionAndTracks,
            PlaybackEvent.RefreshTracksState,
            PlaybackEvent.FlushPosition,
            PlaybackEvent.ArmLoadWatchdog,
            PlaybackEvent.CancelLoadWatchdog,
            PlaybackEvent.StartBackgroundService,
            PlaybackEvent.ClearAutoDetectAttempts,
            PlaybackEvent.MaybeAutoDetectSubtitles,
            PlaybackEvent.RecordHistory(item, 500L),
            PlaybackEvent.RecordPauseHistory(item, 500L, 10000L),
            PlaybackEvent.CommitSubtitleItem(1, item, "sub added"),
        )
        assertEquals(expected, events)
    }

    @Test
    fun `events dispatched before listener attachment are buffered and flushed in order`() {
        val store = PlaybackStateStore()
        store.startPositionTicker()
        store.armLoadWatchdog()

        val events = mutableListOf<PlaybackEvent>()
        store.setEventListener { events.add(it) }

        assertEquals(
            listOf(PlaybackEvent.StartPositionTicker, PlaybackEvent.ArmLoadWatchdog),
            events,
        )

        // Subsequent event dispatches arrive immediately
        store.stopPositionTicker()
        assertEquals(
            listOf(PlaybackEvent.StartPositionTicker, PlaybackEvent.ArmLoadWatchdog, PlaybackEvent.StopPositionTicker),
            events,
        )
    }
}
