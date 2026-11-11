package com.mpx.player.playback

import com.mpx.player.fakes.FakePlayerEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PositionTrackerTest {

    private class RecordingPositionSink : PositionSink {
        override var currentItem: PlayableMedia? = null

        var publishedPositionMs: Long? = null
        var publishedDurationMs: Long? = null
        var publishedBufferedMs: Long? = null
        var publishedHasPrevious: Boolean = false
        var publishedHasNext: Boolean = false
        var publishedMetrics: EnginePlaybackMetrics? = null
        var publishedLoadStage: MpvLoadStage? = null
        var positionPublishCount: Int = 0

        var publishedAudioTracks: List<AudioTrackInfo> = emptyList()
        var publishedAudioIndex: Int = -1
        var publishedSubtitleTracks: List<SubtitleTrackInfo> = emptyList()
        var publishedSubtitleIndex: Int = -1
        var tracksPublishCount: Int = 0

        override fun updatePositionState(
            currentPositionMs: Long,
            durationMs: Long,
            bufferedPositionMs: Long,
            hasPrevious: Boolean,
            hasNext: Boolean,
            playbackMetrics: EnginePlaybackMetrics?,
            mpvLoadStage: MpvLoadStage?,
        ) {
            publishedPositionMs = currentPositionMs
            publishedDurationMs = durationMs
            publishedBufferedMs = bufferedPositionMs
            publishedHasPrevious = hasPrevious
            publishedHasNext = hasNext
            publishedMetrics = playbackMetrics
            publishedLoadStage = mpvLoadStage
            positionPublishCount++
        }

        override fun updateTracksState(
            audioTracks: List<AudioTrackInfo>,
            selectedAudioTrackIndex: Int,
            subtitleTracks: List<SubtitleTrackInfo>,
            selectedSubtitleTrackIndex: Int,
        ) {
            publishedAudioTracks = audioTracks
            publishedAudioIndex = selectedAudioTrackIndex
            publishedSubtitleTracks = subtitleTracks
            publishedSubtitleIndex = selectedSubtitleTrackIndex
            tracksPublishCount++
        }
    }

    @Test
    fun `startTicker runs periodically at 250ms cadence`() = runTest {
        val engine = FakePlayerEngine()
        val resumeManager = PlaybackResumeManager(null, backgroundScope)
        val sink = RecordingPositionSink()
        val tracker = PositionTracker(engine, resumeManager, backgroundScope, sink)

        engine.currentPosition = 1000L
        engine.duration = 10_000L

        tracker.startTicker()
        assertEquals(0, sink.positionPublishCount)

        advanceTimeBy(250)
        assertEquals(1, sink.positionPublishCount)
        assertEquals(1000L, sink.publishedPositionMs)
        assertEquals(10_000L, sink.publishedDurationMs)

        advanceTimeBy(250)
        assertEquals(2, sink.positionPublishCount)

        tracker.stopTicker()
        advanceTimeBy(500)
        assertEquals(2, sink.positionPublishCount)
    }

    @Test
    fun `updatePosition throttles resume save under 1500ms or within 2s of duration`() = runTest {
        val engine = FakePlayerEngine()
        val resumeManager = PlaybackResumeManager(null, backgroundScope)
        resumeManager.awaitPreload()
        val sink = RecordingPositionSink()
        val tracker = PositionTracker(engine, resumeManager, backgroundScope, sink)

        sink.currentItem = PlayableMedia(id = "item-1", uri = "uri-1", title = "Title")

        // < 1500ms -> should not save
        engine.currentPosition = 1200L
        engine.duration = 60_000L
        tracker.updatePosition()
        assertEquals(0L, resumeManager.getResumePosition("item-1", "uri-1"))

        // > 1500ms and not near end -> saves
        engine.currentPosition = 5000L
        tracker.updatePosition()
        assertEquals(5000L, resumeManager.getResumePosition("item-1", "uri-1"))

        // Near end (< duration - 2000L) -> should not save
        engine.currentPosition = 59_000L
        tracker.updatePosition()
        assertEquals(5000L, resumeManager.getResumePosition("item-1", "uri-1"))
    }

    @Test
    fun `updateTracksState annotates external subtitles and updates selected indices`() = runTest {
        val engine = FakePlayerEngine()
        val resumeManager = PlaybackResumeManager(null, backgroundScope)
        val sink = RecordingPositionSink()
        val tracker = PositionTracker(engine, resumeManager, backgroundScope, sink)

        sink.currentItem = PlayableMedia(
            id = "item-1",
            uri = "uri-1",
            title = "Title",
            externalSubtitles = listOf(ExternalSubtitle("uri-sub", "ext.srt", "text/vtt")),
        )
        engine.audioTracksToReturn = listOf(
            AudioTrackInfo(0, 0, "English", "eng", isSelected = true),
            AudioTrackInfo(0, 1, "Spanish", "spa", isSelected = false),
        )
        engine.subtitleTracksToReturn = listOf(
            SubtitleTrackInfo(0, 0, "ext.srt", "eng", isSelected = true),
            SubtitleTrackInfo(0, 1, "Internal", "fre", isSelected = false),
        )

        tracker.updateTracksState()

        assertEquals(0, sink.publishedAudioIndex)
        assertEquals(2, sink.publishedAudioTracks.size)
        assertEquals(0, sink.publishedSubtitleIndex)
        assertEquals(2, sink.publishedSubtitleTracks.size)
        assertTrue(sink.publishedSubtitleTracks[0].isExternal)
        assertFalse(sink.publishedSubtitleTracks[1].isExternal)
    }
}
