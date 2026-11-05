package com.mpx.player.playback.engine

import android.view.Surface
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo

/**
 * Track classification types for [PlayerEngine.selectTrack].
 */
enum class TrackType {
    AUDIO,
    SUBTITLE,
    VIDEO
}

/**
 * Common playback engine interface extracted from PlaybackManager.
 * Defines the contract for core playback operations: surface attachment, media loading,
 * playback control, seeking, speed adjustments, track selection, and engine lifecycle.
 *
 * The only implementation is the native mpv engine ([MpvPlayerEngine], issue #150:
 * Media3 was removed entirely — the engine setting, both auto-fallbacks, the
 * "Try MPV" banner and the Media3 dependency are gone).
 */
interface PlayerEngine {

    companion object {
        /**
         * Playback states reported through [Listener.onPlaybackStateChanged] and
         * [playbackState] (issue #150: these used to be
         * androidx.media3.common.Player.STATE_*; the values are kept identical
         * so stored/compared ints keep their meaning, now defined here).
         */
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }

    /**
     * Listener interface to notify the orchestrator (PlaybackManager) of engine events.
     */
    interface Listener {
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onMediaItemTransition(index: Int, isAuto: Boolean) {}
        fun onTracksChanged() {}
        fun onPlaybackParametersChanged(speed: Float) {}
        fun onVideoDecoderInitialized(decoderName: String) {}
        fun onError(error: Exception) {}
    }

    /**
     * Measured playback metrics for the mpv engine. Default null: engines
     * that do not measure report nothing rather than invented numbers.
     */
    fun getPlaybackMetrics(): EnginePlaybackMetrics? = null

    /**
     * Step-level load diagnostics (mpv only). Default null: engines that do
     * not report stages report nothing rather than invented stages.
     */
    fun getLoadStage(): MpvLoadStage? = null

    /**
     * Display size of the current video (issue #225: the PiP aspect ratio
     * source of truth). 0 when no video is loaded — audio items and the
     * not-yet-loaded state report 0, never a guessed size.
     */
    val videoWidth: Int
        get() = 0

    /** Display height of the current video; 0 when no video is loaded. */
    val videoHeight: Int
        get() = 0

    /**
     * Attaches a native video rendering surface to the engine.
     */
    fun attachSurface(surface: Surface)

    /**
     * Synchronously detaches the video surface from the engine.
     */
    fun detachSurface()

    /**
     * Surface size changed (SurfaceHolder.Callback.surfaceChanged). Default
     * no-op; the mpv engine reports it as the "android-surface-size" property.
     */
    fun onSurfaceChanged(width: Int, height: Int) {}

    /**
     * Loads a single media item with optional resume position.
     *
     * The item is passed RAW (original URIs): the implementation resolves
     * only the target to an fd at load time (issues #213/#219), so a
     * re-load always opens a fresh descriptor.
     *
     * @param media Media item to load.
     * @param resumeMs Position in milliseconds from which playback should start/resume.
     */
    fun load(media: PlayableMedia, resumeMs: Long = 0L)

    /**
     * Loads a playlist/queue of media items starting at [startIndex].
     *
     * The items are passed RAW (original URIs) and stored as such: the
     * implementation resolves only the target item at load time, so every
     * re-load (Next/Previous, indexed seek, decoder reload) opens a fresh
     * fd instead of reusing a closed one (issues #213/#219).
     *
     * @param items Queue of media items to load.
     * @param startIndex Initial item index to play.
     * @param resumeMs Position in milliseconds from which the initial item should resume.
     */
    fun load(items: List<PlayableMedia>, startIndex: Int = 0, resumeMs: Long = 0L)

    /**
     * Starts or resumes playback.
     */
    fun play()

    /**
     * Pauses playback.
     */
    fun pause()

    /**
     * Seeks to a specific position in milliseconds within the current media item.
     */
    fun seekTo(positionMs: Long)

    /**
     * Seeks to a specific position in milliseconds within a specified playlist index.
     */
    fun seekTo(index: Int, positionMs: Long)

    /**
     * Sets playback speed multiplier (e.g. 1.0f, 1.25f, 2.0f).
     */
    fun setSpeed(speed: Float)

    /**
     * Selects a track by [type] and [id].
     * Passing a null or empty [id] deselects/disables tracks of the given type (e.g. subtitles off).
     */
    fun selectTrack(type: TrackType, id: String?)

    /**
     * Selects an audio track by group index and track index within that group.
     */
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)

    /**
     * Selects a subtitle track by group index and track index, or disables subtitles if [groupIndex] is null.
     */
    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?)

    /**
     * Dynamically replaces the media item at [index] (e.g. when external subtitles are loaded).
     *
     * The item is passed RAW (original URIs): the implementation resolves
     * only the subtitle URIs for the sub-add and never the media URI,
     * which is not reloaded here (issues #213/#219).
     */
    fun replaceMediaItem(index: Int, media: PlayableMedia)

    /**
     * Stops playback without releasing resources.
     */
    fun stop()

    /**
     * Releases all player resources.
     */
    fun release()

    /**
     * Configures the decoder mode (HW, HW+, SW).
     */
    fun setDecoderMode(mode: DecoderMode)

    /**
     * Registers or unregisters the engine event listener.
     */
    fun setListener(listener: Listener?)

    // Properties for position and state queries
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val isPlaying: Boolean
    val playbackState: Int
    val currentMediaItemIndex: Int
    val hasNextMediaItem: Boolean
    val hasPreviousMediaItem: Boolean

    fun seekToNextMediaItem()
    fun seekToPreviousMediaItem()

    fun getAudioTracks(): List<AudioTrackInfo>
    fun getSubtitleTracks(): List<SubtitleTrackInfo>
}
