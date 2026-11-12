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
    val skipForwardCalls = mutableListOf<Long>()
    val skipBackwardCalls = mutableListOf<Long>()
    val speedCalls = mutableListOf<Float>()
    val aspectRatioCalls = mutableListOf<AspectRatioMode>()
    val videoZoomCalls = mutableListOf<Float>()
    var stopCalls = 0
    var lockedCalls = 0
    var skipNextCalls = 0
    var skipPreviousCalls = 0

    val shuffleEnabledCalls = mutableListOf<Boolean>()
    var cycleRepeatModeCalls = 0
    val decoderModeCalls = mutableListOf<DecoderMode>()
    val sleepTimerCalls = mutableListOf<Int>()
    var cancelSleepTimerCalls = 0
    var clearResumedNotificationCalls = 0
    var clearDecoderFallbackNoteCalls = 0
    var clearUserNotificationCalls = 0
    val selectTrackCalls = mutableListOf<Pair<TrackType, String?>>()
    val selectAudioTrackCalls = mutableListOf<Pair<Int, Int>>()
    val selectSubtitleTrackCalls = mutableListOf<Pair<Int?, Int?>>()
    val loadExternalSubtitleCalls = mutableListOf<Triple<Uri, String, String>>()
    val appliedSubtitleSettings = mutableListOf<AppSettings>()
    val appliedPlaybackTweaks = mutableListOf<AppSettings>()
    val videoBackgroundActiveCalls = mutableListOf<Boolean>()
    val backgroundPlayingCalls = mutableListOf<Boolean>()
    val attachedSurfaces = mutableListOf<Surface>()
    var detachSurfaceCalls = 0
    val surfaceChangedCalls = mutableListOf<Pair<Int, Int>>()

    override fun restorePersistedQueueIfEmpty(): Boolean {
        restoreCalls++
        return true
    }

    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override fun togglePlayPause() { togglePlayPauseCalls++ }
    override fun seekTo(positionMs: Long) { seekCalls.add(positionMs) }
    override fun skipForward(deltaMs: Long) {
        skipForwardCalls.add(deltaMs)
        seekCalls.add(deltaMs)
    }
    override fun skipBackward(deltaMs: Long) {
        skipBackwardCalls.add(deltaMs)
        seekCalls.add(-deltaMs)
    }
    override fun skipNext() { skipNextCalls++ }
    override fun skipPrevious() { skipPreviousCalls++ }
    override fun setSpeed(speed: Float) { speedCalls.add(speed) }
    override fun setAspectRatio(mode: AspectRatioMode) { aspectRatioCalls.add(mode) }
    override fun setVideoZoom(zoom: Float) { videoZoomCalls.add(zoom) }
    override fun setShuffleEnabled(enabled: Boolean) { shuffleEnabledCalls.add(enabled) }
    override fun cycleRepeatMode() { cycleRepeatModeCalls++ }
    override fun setDecoderMode(mode: DecoderMode) { decoderModeCalls.add(mode) }
    override fun setLocked(locked: Boolean) { lockedCalls++ }
    override fun setSleepTimer(minutes: Int) { sleepTimerCalls.add(minutes) }
    override fun cancelSleepTimer() { cancelSleepTimerCalls++ }
    override fun clearResumedNotification() { clearResumedNotificationCalls++ }
    override fun clearDecoderFallbackNote() { clearDecoderFallbackNoteCalls++ }
    override fun clearUserNotification() { clearUserNotificationCalls++ }
    override fun stop() { stopCalls++ }
    override fun attachSurface(surface: Surface) { attachedSurfaces.add(surface) }
    override fun detachSurface() { detachSurfaceCalls++ }
    override fun surfaceChanged(width: Int, height: Int) { surfaceChangedCalls.add(width to height) }
    override fun applySubtitleSettings(settings: AppSettings) { appliedSubtitleSettings.add(settings) }
    override fun applyPlaybackTweaks(settings: AppSettings) { appliedPlaybackTweaks.add(settings) }
    override fun setVideoBackgroundActive(active: Boolean) { videoBackgroundActiveCalls.add(active) }
    override fun setBackgroundPlaying(active: Boolean) { backgroundPlayingCalls.add(active) }
    override fun selectTrack(type: TrackType, id: String?) { selectTrackCalls.add(type to id) }
    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) { selectAudioTrackCalls.add(groupIndex to trackIndex) }
    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) { selectSubtitleTrackCalls.add(groupIndex to trackIndex) }
    override fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean {
        loadExternalSubtitleCalls.add(Triple(uri, displayName, mimeType))
        return true
    }

    override val videoWidth: Int = 1920
    override val videoHeight: Int = 1080

    fun emit(state: PlaybackState) {
        _playbackState.value = state
    }
}
