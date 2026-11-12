package com.mpx.player.fakes

import android.net.Uri
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.PlaybackStateSource
import com.mpx.player.playback.engine.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared test double for [PlaybackStateSource] (§7).
 */
class FakePlaybackStateSource(
    initialState: PlaybackState = PlaybackState(),
) : PlaybackStateSource {
    private val _playbackState = MutableStateFlow(initialState)
    override val playbackState: StateFlow<PlaybackState> =
        _playbackState.asStateFlow()

    var restoreCalls = 0
        private set
    var playCalls = 0
    var pauseCalls = 0
    var togglePlayPauseCalls = 0
    val seekCalls = mutableListOf<Long>()
    val speedCalls = mutableListOf<Float>()
    val aspectRatioCalls = mutableListOf<AspectRatioMode>()
    val videoZoomCalls = mutableListOf<Float>()
    var stopCalls = 0
    var lockedCalls = 0
    var skipNextCalls = 0
    var skipPreviousCalls = 0

    override fun restorePersistedQueueIfEmpty(): Boolean {
        restoreCalls++
        return true
    }

    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override fun togglePlayPause() { togglePlayPauseCalls++ }
    override fun seekTo(positionMs: Long) { seekCalls.add(positionMs) }
    override fun skipForward(deltaMs: Long) { seekCalls.add(deltaMs) }
    override fun skipBackward(deltaMs: Long) { seekCalls.add(-deltaMs) }
    override fun skipNext() { skipNextCalls++ }
    override fun skipPrevious() { skipPreviousCalls++ }
    override fun setSpeed(speed: Float) { speedCalls.add(speed) }
    override fun setAspectRatio(mode: AspectRatioMode) { aspectRatioCalls.add(mode) }
    override fun setVideoZoom(zoom: Float) { videoZoomCalls.add(zoom) }
    override fun setShuffleEnabled(enabled: Boolean) {}
    override fun cycleRepeatMode() {}
    override fun setDecoderMode(mode: DecoderMode) {}
    override fun setLocked(locked: Boolean) { lockedCalls++ }
    override fun setSleepTimer(minutes: Int) {}
    override fun cancelSleepTimer() {}
    override fun clearResumedNotification() {}
    override fun clearDecoderFallbackNote() {}
    override fun clearUserNotification() {}
    override fun stop() { stopCalls++ }
    override fun attachSurface(surface: Surface) {}
    override fun detachSurface() {}
    override fun surfaceChanged(width: Int, height: Int) {}
    override fun applySubtitleSettings(settings: AppSettings) {}
    override fun applyPlaybackTweaks(settings: AppSettings) {}
    override fun setVideoBackgroundActive(active: Boolean) {}
    override fun setBackgroundPlaying(active: Boolean) {}
    override fun selectTrack(type: TrackType, id: String?) {}
    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {}
    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {}
    override fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean = true

    override val videoWidth: Int = 1920
    override val videoHeight: Int = 1080

    fun emit(state: PlaybackState) {
        _playbackState.value = state
    }
}
