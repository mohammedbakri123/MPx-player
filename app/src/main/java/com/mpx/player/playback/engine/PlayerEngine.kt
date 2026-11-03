package com.mpx.player.playback.engine

import android.view.Surface
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
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
 * Implementations wrap specific underlying playback engines (e.g., Media3/ExoPlayer, mpv).
 */
interface PlayerEngine {

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
     * Attaches a native video rendering surface to the engine.
     */
    fun attachSurface(surface: Surface)

    /**
     * Synchronously detaches the video surface from the engine.
     */
    fun detachSurface()

    /**
     * Loads a single media item with optional resume position.
     *
     * @param media Media item to load.
     * @param resumeMs Position in milliseconds from which playback should start/resume.
     */
    fun load(media: PlayableMedia, resumeMs: Long = 0L)

    /**
     * Loads a playlist/queue of media items starting at [startIndex].
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
     * Selects an audio track by ExoPlayer-compatible group index and track index within that group.
     */
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)

    /**
     * Selects a subtitle track by group index and track index, or disables subtitles if [groupIndex] is null.
     */
    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?)

    /**
     * Dynamically replaces the media item at [index] (e.g. when external subtitles are loaded).
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
