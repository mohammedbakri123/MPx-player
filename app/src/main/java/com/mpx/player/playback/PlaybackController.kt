package com.mpx.player.playback

import android.net.Uri
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.engine.TrackType

/**
 * Interface defining playback transport and control operations (migration Phase 4, step 3: R-3, R-10).
 *
 * Implemented by [PlaybackCoordinator] and exposed to [com.mpx.player.ui.screens.player.PlayerViewModel]
 * to remove the UI's direct coupling to the coordinator implementation.
 */
interface PlaybackController {
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipForward(deltaMs: Long = 10_000L)
    fun skipBackward(deltaMs: Long = 10_000L)
    fun skipNext()
    fun skipPrevious()
    fun setSpeed(speed: Float)
    fun setAspectRatio(mode: AspectRatioMode)
    fun setVideoZoom(zoom: Float)
    fun setShuffleEnabled(enabled: Boolean)
    fun cycleRepeatMode()
    fun setDecoderMode(mode: DecoderMode)
    fun setLocked(locked: Boolean)
    fun setSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun clearResumedNotification()
    fun clearDecoderFallbackNote()
    fun clearUserNotification()
    fun stop()
    fun attachSurface(surface: Surface)
    fun detachSurface()
    fun surfaceChanged(width: Int, height: Int)
    fun applySubtitleSettings(settings: AppSettings)
    fun applyPlaybackTweaks(settings: AppSettings)
    fun setSubtitleMarginY(margin: Int)
    fun setVideoBackgroundActive(active: Boolean)
    fun setBackgroundPlaying(active: Boolean)
    fun selectTrack(type: TrackType, id: String?)
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)
    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?)
    fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean

    val videoWidth: Int
    val videoHeight: Int
}
