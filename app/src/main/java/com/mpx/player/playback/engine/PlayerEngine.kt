package com.mpx.player.playback.engine

import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MediaInfo
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
 * Outcome of [PlayerEngine.replaceMediaItem] for the external subtitles it
 * was asked to add (issue #332).
 *
 * The synchronous result is deliberately NOT "the subtitle loaded":
 * sub-add is async and `mpv.command()` never throws for a rejected file
 * (the native JNI command only logs `mpv_error_string`), so the issued
 * command alone proves nothing. ISSUED/QUEUED mean a sub-add was actually
 * attempted; whether the track really loads is confirmed asynchronously
 * via [PlayerEngine.Listener.onExternalSubtitleConfirmed] once the track
 * appears in track-list (or the confirmation times out).
 */
enum class ReplaceMediaItemResult {
    /**
     * The file is loaded and at least one sub-add command was issued for a
     * usable URI. Confirmed asynchronously via
     * [PlayerEngine.Listener.onExternalSubtitleConfirmed].
     */
    ISSUED,

    /**
     * The file is not loaded yet; the subtitles were queued and will be
     * sub-added on FILE_LOADED. Confirmed asynchronously at dispatch time,
     * the same way as [ISSUED].
     */
    QUEUED,

    /**
     * Nothing new was added because every listed external subtitle is
     * already in the item. No sub-add was issued and no confirmation is
     * armed; callers should check the live track-list to see whether the
     * track is actually there.
     */
    ALREADY_ADDED,

    /**
     * Nothing could be attempted: an invalid index, no new subtitles, or
     * every subtitle URI provably unusable (unreadable `content://`,
     * missing file). No confirmation is armed.
     */
    FAILED,
}

/**
 * Common playback engine interface extracted from the old PlaybackManager.
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
     * Listener interface to notify the orchestrator (PlaybackCoordinator) of engine events.
     */
    interface Listener {
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        // Issue #283: no isAuto flag — the old Media3-style
        // MEDIA_ITEM_TRANSITION_REASON_AUTO never survived the mpv
        // port (the engine always passed false), and no resume seek
        // happens here: resume is applied in-place via load()'s
        // resumeMs. A transition only reports which index is now live.
        fun onMediaItemTransition(index: Int) {}
        fun onTracksChanged() {}
        fun onPlaybackParametersChanged(speed: Float) {}
        /**
         * Issue #260: the video's display size (mpv dwidth/dheight) became
         * known or changed. 0x0 while no video is loaded.
         */
        fun onVideoSizeChanged(width: Int, height: Int) {}
        fun onVideoDecoderInitialized(decoderName: String) {}
        fun onError(error: Exception) {}

        /**
         * Issue #332: the outcome of a manually added external subtitle,
         * confirmed once its track actually appears in track-list (sub-add
         * is async and `mpv.command()` never throws for a rejected file,
         * so the command returning says nothing). Fires only when the
         * engine armed a confirmation for this [mediaId] — queue restores
         * arm one the same way, and the manager ignores ids it did not
         * arm. [success] true means the track appeared and `sid` was
         * pinned to it; false means it never appeared within the
         * confirmation timeout. Always delivered on the main thread.
         */
        fun onExternalSubtitleConfirmed(mediaId: String, success: Boolean) {}
    }

    /**
     * Measured playback metrics for the mpv engine. Default null: engines
     * that do not measure report nothing rather than invented numbers.
     */
    fun getPlaybackMetrics(): EnginePlaybackMetrics? = null

    /**
     * Technical information about the currently playing media item and its tracks (issue #468).
     * Default null: engines that do not report media info report nothing.
     */
    fun getMediaInfo(): MediaInfo? = null

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
     *
     * Issue #332: returns what actually happened to the subtitles — the
     * old `Unit` return let the caller claim success unconditionally.
     * ISSUED/QUEUED are confirmed asynchronously via
     * [Listener.onExternalSubtitleConfirmed]; see [ReplaceMediaItemResult].
     */
    fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult

    /**
     * Stops playback without releasing resources.
     */
    fun stop()

    /**
     * Releases all player resources.
     *
     * **Test-only lifecycle method (D-11).**
     *
     * In production, the engine is intentionally process-lifetime and must **NOT**
     * be wired to any Android component lifecycle (Activity or Service). Wiring
     * release to component lifecycles destroys the native mpv instance while other
     * components remain active. Retained exclusively for test fixtures.
     */
    @VisibleForTesting
    fun release()

    /**
     * Configures the decoder mode (HW, HW+, SW).
     */
    fun setDecoderMode(mode: DecoderMode)

    /**
     * Registers or unregisters the engine event listener.
     */
    fun setListener(listener: Listener?)

    /**
     * Connects the live queue and index providers to the engine (D-1 single source of truth).
     * Eliminates engine-side mirroring of the playback queue.
     */
    fun setQueueSource(
        queue: () -> List<PlayableMedia>,
        currentIndex: () -> Int,
    ) = Unit

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

    /**
     * Applies audio delay and video zoom tweaks from settings (issue #153, R-3).
     */
    fun applyPlaybackTweaks(settings: AppSettings) = Unit

    /**
     * Live video-zoom for the pinch gesture (#172, R-3).
     */
    fun setVideoZoom(zoom: Float) = Unit

    /**
     * Sets aspect ratio mode (FIT, CROP, STRETCH, 16:9, 4:3, ORIGINAL, ZOOM) (R-3).
     */
    fun setAspectRatio(mode: AspectRatioMode) = Unit

    /**
     * Sets dynamic vertical subtitle margin (e.g. to avoid bottom floating islands).
     */
    fun setSubtitleMarginY(margin: Int) = Unit
}
