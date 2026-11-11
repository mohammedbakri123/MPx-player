package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.view.Surface
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.TrackType
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin shell over [PlaybackCoordinator] (migration Phase 3, step 4).
 *
 * Every behaviour that used to live here — the published state, the
 * position ticker, the preparing watchdog, the sleep timer, transport,
 * session functions, and the three host implementations for
 * [SubtitleManager], [PlaybackQueue], and [EngineEventTranslator] —
 * now lives in [PlaybackCoordinator], which this class delegates to.
 * The public API is unchanged (same function signatures, same public
 * vals), so no screen, ViewModel, service, or Koin module changed in
 * this step.
 *
 * Two deliberate API notes:
 * - [settingsRepository] is exposed as the [AppSettingsRepository]
 *   interface now, not the concrete `SharedPrefsAppSettingsRepository`
 *   the old manager inferred. Every caller only uses `load()` /
 *   `save()`, which the interface declares; nothing relied on the
 *   concrete type.
 * - [engine] is a `val` now, not a `var` with a private setter.
 *   Nothing ever assigned to it (verified by grep: no `.engine =`
 *   anywhere), so the setter was dead.
 * - The old private `engineLoad` forwarder is gone, not moved: it had
 *   no callers left (the queue and the translator call
 *   `engine.load(...)` directly since Phase 3 steps 2–3).
 *
 * The final Phase 3 step rewires the callers to [PlaybackCoordinator]
 * directly (it already implements [PlaybackStateSource] and
 * [StreamPlayer], the interfaces the ViewModels depend on) and deletes
 * this class, including the [getInstance] companion below.
 */
class PlaybackManager private constructor(context: Context) : PlaybackStateSource, StreamPlayer {

    private val coordinator: PlaybackCoordinator =
        PlaybackCoordinator.production(context.applicationContext)

    val database get() = coordinator.database
    val persistenceRepository get() = coordinator.persistenceRepository
    val resumeManager get() = coordinator.resumeManager
    val settingsRepository: AppSettingsRepository get() = coordinator.settingsRepository
    val engine: PlayerEngine get() = coordinator.engine
    val subtitleManager get() = coordinator.subtitleManager
    val playbackQueue get() = coordinator.playbackQueue
    val engineEventTranslator get() = coordinator.engineEventTranslator

    override val playbackState: StateFlow<PlaybackState> get() = coordinator.playbackState

    /**
     * Issue #225: display size of the current video (engine's
     * `dwidth`/`dheight`), the PiP aspect-ratio source of truth. 0 when
     * no video is loaded.
     */
    val videoWidth: Int
        get() = coordinator.videoWidth

    val videoHeight: Int
        get() = coordinator.videoHeight

    fun attachSurface(surface: Surface) = coordinator.attachSurface(surface)

    fun surfaceChanged(width: Int, height: Int) = coordinator.surfaceChanged(width, height)

    fun detachSurface() = coordinator.detachSurface()

    fun applySubtitleSettings(settings: AppSettings) = coordinator.applySubtitleSettings(settings)

    fun applyPlaybackTweaks(settings: AppSettings) = coordinator.applyPlaybackTweaks(settings)

    fun setVideoZoom(zoom: Float) = coordinator.setVideoZoom(zoom)

    fun load(media: PlayableMedia, resumeMs: Long = 0L) = coordinator.load(media, resumeMs)

    override fun playStream(url: String) = coordinator.playStream(url)

    fun playQueue(
        items: List<PlayableMedia>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        forceResume: Boolean = false,
        preserveShuffleState: Boolean = false,
    ) = coordinator.playQueue(items, startIndex, autoPlay, forceResume, preserveShuffleState)

    fun playLiveQueueItem(index: Int) = coordinator.playLiveQueueItem(index)

    fun playAudioQueue(tracks: List<AudioTrack>, startIndex: Int = 0) =
        coordinator.playAudioQueue(tracks, startIndex)

    fun playVideoQueue(folderId: String = "camera") = coordinator.playVideoQueue(folderId)

    fun playVideoFiles(videos: List<VideoFile>, startIndex: Int = 0) =
        coordinator.playVideoFiles(videos, startIndex)

    fun play() = coordinator.play()

    fun pause() = coordinator.pause()

    fun togglePlayPause() = coordinator.togglePlayPause()

    fun seekTo(positionMs: Long) = coordinator.seekTo(positionMs)

    fun skipForward(deltaMs: Long = 10_000L) = coordinator.skipForward(deltaMs)

    fun skipBackward(deltaMs: Long = 10_000L) = coordinator.skipBackward(deltaMs)

    fun skipNext() = coordinator.skipNext()

    fun skipPrevious() = coordinator.skipPrevious()

    fun setSpeed(speed: Float) = coordinator.setSpeed(speed)

    fun cycleRepeatMode() = coordinator.cycleRepeatMode()

    fun setShuffleEnabled(enabled: Boolean) = coordinator.setShuffleEnabled(enabled)

    fun setAspectRatio(mode: AspectRatioMode) = coordinator.setAspectRatio(mode)

    fun setDecoderMode(mode: DecoderMode) = coordinator.setDecoderMode(mode)

    fun setLocked(locked: Boolean) = coordinator.setLocked(locked)

    fun selectTrack(type: TrackType, id: String?) = coordinator.selectTrack(type, id)

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
        coordinator.selectAudioTrack(groupIndex, trackIndex)

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) =
        coordinator.selectSubtitleTrack(groupIndex, trackIndex)

    fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean =
        coordinator.loadExternalSubtitle(uri, displayName, mimeType)

    fun clearDecoderFallbackNote() = coordinator.clearDecoderFallbackNote()

    fun clearUserNotification() = coordinator.clearUserNotification()

    fun setSleepTimer(minutes: Int) = coordinator.setSleepTimer(minutes)

    fun cancelSleepTimer() = coordinator.cancelSleepTimer()

    override fun restorePersistedQueueIfEmpty(): Boolean =
        coordinator.restorePersistedQueueIfEmpty()

    fun stop() = coordinator.stop()

    fun release() = coordinator.release()

    fun setVideoBackgroundActive(active: Boolean) = coordinator.setVideoBackgroundActive(active)

    fun setBackgroundPlaying(active: Boolean) = coordinator.setBackgroundPlaying(active)

    fun clearResumedNotification() = coordinator.clearResumedNotification()

    companion object {
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
