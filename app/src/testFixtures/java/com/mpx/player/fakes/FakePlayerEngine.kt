package com.mpx.player.fakes

import android.view.Surface
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType

/**
 * Shared test fake for [PlayerEngine].
 *
 * Implements [PlayerEngine] and records calls across playback, seek, speed,
 * track selection, and queue operations without invoking native code.
 */
class FakePlayerEngine : PlayerEngine {
    val loadCalls = mutableListOf<Triple<List<PlayableMedia>, Int, Long>>()
    val singleLoadCalls = mutableListOf<Pair<PlayableMedia, Long>>()
    val seekCalls = mutableListOf<Long>()
    val speedCalls = mutableListOf<Float>()
    val replaceCalls = mutableListOf<Pair<Int, PlayableMedia>>()
    val selectTrackCalls = mutableListOf<Pair<TrackType, String?>>()
    val selectAudioCalls = mutableListOf<Pair<Int, Int>>()
    val selectSubtitleCalls = mutableListOf<Pair<Int?, Int?>>()
    val decoderModeCalls = mutableListOf<DecoderMode>()
    val surfaceChangedCalls = mutableListOf<Pair<Int, Int>>()

    var replaceResult: ReplaceMediaItemResult = ReplaceMediaItemResult.ISSUED
    var audioTracksToReturn: List<AudioTrackInfo> = emptyList()
    var subtitleTracksToReturn: List<SubtitleTrackInfo> = emptyList()
    var metricsToReturn: EnginePlaybackMetrics? = null
    var loadStageToReturn: MpvLoadStage? = null

    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var stopCalls: Int = 0
    var releaseCalls: Int = 0
    var nextCalls: Int = 0
    var previousCalls: Int = 0

    var capturedListener: PlayerEngine.Listener? = null
    var capturedQueueProvider: (() -> List<PlayableMedia>)? = null
    var capturedIndexProvider: (() -> Int)? = null

    override var currentPosition: Long = 0L
    override var duration: Long = 0L
    override var bufferedPosition: Long = 0L
    override var isPlaying: Boolean = false
    override var playbackState: Int = PlayerEngine.STATE_IDLE
    override var currentMediaItemIndex: Int = 0
    override var hasNextMediaItem: Boolean = false
    override var hasPreviousMediaItem: Boolean = false
    override var videoWidth: Int = 0
    override var videoHeight: Int = 0

    override fun getPlaybackMetrics(): EnginePlaybackMetrics? = metricsToReturn
    override fun getLoadStage(): MpvLoadStage? = loadStageToReturn

    override fun attachSurface(surface: Surface) = Unit
    override fun detachSurface() = Unit
    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceChangedCalls.add(width to height)
    }

    override fun load(media: PlayableMedia, resumeMs: Long) {
        singleLoadCalls.add(media to resumeMs)
    }

    override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
        loadCalls.add(Triple(items, startIndex, resumeMs))
    }

    override fun play() {
        playCalls++
        isPlaying = true
    }

    override fun pause() {
        pauseCalls++
        isPlaying = false
    }

    override fun seekTo(positionMs: Long) {
        seekCalls.add(positionMs)
    }

    override fun seekTo(index: Int, positionMs: Long) = Unit

    override fun setSpeed(speed: Float) {
        speedCalls.add(speed)
    }

    override fun selectTrack(type: TrackType, id: String?) {
        selectTrackCalls.add(type to id)
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        selectAudioCalls.add(groupIndex to trackIndex)
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        selectSubtitleCalls.add(groupIndex to trackIndex)
    }

    override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult {
        replaceCalls.add(index to media)
        isPlaying = false
        return replaceResult
    }

    override fun stop() {
        stopCalls++
    }

    override fun release() {
        releaseCalls++
    }

    override fun setDecoderMode(mode: DecoderMode) {
        decoderModeCalls.add(mode)
    }

    override fun setListener(listener: PlayerEngine.Listener?) {
        capturedListener = listener
    }

    override fun setQueueSource(
        queue: () -> List<PlayableMedia>,
        currentIndex: () -> Int,
    ) {
        capturedQueueProvider = queue
        capturedIndexProvider = currentIndex
    }

    override fun seekToNextMediaItem() {
        nextCalls++
    }

    override fun seekToPreviousMediaItem() {
        previousCalls++
    }

    override fun getAudioTracks(): List<AudioTrackInfo> = audioTracksToReturn
    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracksToReturn
}
