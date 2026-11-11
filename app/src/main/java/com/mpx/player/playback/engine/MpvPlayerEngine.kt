package com.mpx.player.playback.engine

import android.content.Context
import android.util.Log
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import `is`.xyz.mpv.MPV
import kotlin.math.log2

/**
 * Exception raised when libmpv encounters an unrecoverable decoding or playback error.
 */
class MpvPlaybackException(
    message: String,
    val codecName: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Native mpv implementation of [PlayerEngine] backed by [MPV] from mpv-android-lib.
 *
 * Phase 4 step 1: this class is now a thin facade over [MpvCoreEngine],
 * which owns the mpv lifecycle, the load/play commands, position/state
 * observation and the decoder fallback chain. Phase 4 step 2 adds
 * [MpvTrackSelection], which owns track listing/switching. Phase 4
 * step 3 adds [MpvSubtitleRenderer], which owns subtitle rendering/
 * confirmation (including [replaceMediaItem], whose body is sub-add +
 * #332 confirmation). What stays here until its own step moves it is
 * settings-to-mpv configuration (step 4). The [MpvCoreEngine.Host]
 * implementation below is the seam: the core calls back into it at
 * exactly the points the old monolith ran that code.
 *
 * Implements the contract defined in [PlayerEngine]:
 * - Synchronous surface detach barrier (vo=null and detachSurface before surfaceDestroyed returns)
 * - Background audio continuation when vo=null
 * - Progressive decoder fallback chain:
 *   mediacodec (HW) -> mediacodec-copy (HW+) -> hwdec=no (SW) -> hard failure reported as a real error
 * - Active decoder reporting via hwdec-current and video-codec properties
 * - Subtitle rendering with bundled subfont.ttf fallback font for libass and configurable styling overrides
 */
class MpvPlayerEngine(
    context: Context,
    initialDecoderMode: DecoderMode = DecoderMode.HW,
) : PlayerEngine {


    companion object {
        private const val TAG = "MpvPlayerEngine"
    }


    /**
     * Issue #226: the aspect mode currently applied to mpv. The manager
     * owns the UI-facing mode in PlaybackState; the engine tracks it too
     * because ZOOM owns the video-zoom property while it is active (see
     * [setAspectRatio]): applyPlaybackTweaks must not silently overwrite
     * ZOOM's 2x magnification when an unrelated setting changes, and
     * leaving ZOOM must restore the zoom the user had before. Defaults
     * to FIT, matching PlaybackState's default.
     */
    private var currentAspectRatio: AspectRatioMode = AspectRatioMode.FIT


    /**
     * Issue #226: the live mpv video-zoom (log2 scale) that was active
     * when ZOOM was entered, to restore when leaving ZOOM. Captured from
     * the live property so it includes an in-progress pinch that has not
     * been saved to settings yet; updated by any slider/pinch zoom change
     * made while ZOOM is active, so leaving ZOOM restores the newest
     * manual zoom, not a stale pre-ZOOM value. Null while ZOOM is not
     * the active mode.
     */
    private var zoomBeforeAspectZoom: Double? = null


    /**
     * Issue #226: the persisted settings.videoZoom value at the moment
     * ZOOM was entered — the baseline applyPlaybackTweaks compares
     * against to tell a user-driven zoom change (sheet slider / pinch-end
     * save) apart from an unrelated settings change while ZOOM is active.
     * Null while ZOOM is not the active mode.
     */
    private var videoZoomAtAspectZoomEntry: Float? = null


    /**
     * Phase 4 step 1 seam: the core engine, with the host callbacks for
     * the concerns still owned here. The callbacks run at exactly the
     * points the old monolith ran the equivalent code.
     *
     * Init-time note: the core calls [MpvCoreEngine.Host.hwdecValueFor]
     * and [MpvCoreEngine.Host.applyPostInitSettings] from its own init
     * block, while this `core` property is still being assigned — so
     * those two callbacks must not read `core` (they use the core passed
     * as an argument, and the pure hwdec mapping). The other callbacks
     * only ever run after construction.
     */
    private val core: MpvCoreEngine = MpvCoreEngine(
        context,
        initialDecoderMode,
        host = object : MpvCoreEngine.Host {
            override fun hwdecValueFor(mode: DecoderMode): String = hwdecValueFor(mode)

            override fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String =
                hwdecValueForStage(stage)

            override fun applyHwdecForMode(mode: DecoderMode) {
                applyHwdecForMode(mode)
            }

            override fun applyPostInitSettings(core: MpvCoreEngine) {
                MpvSubtitleRenderer.applySubtitleSettingsTo(core, core.loadSavedSubtitleSettings())
                applyPlaybackTweaksTo(core, core.loadSavedSubtitleSettings())
            }

            override fun onLoadStarted() {
                if (this@MpvPlayerEngine::subtitles.isInitialized) subtitles.onLoadStarted()
            }

            override fun onLoadTargetResolved(mediaId: String) {
                if (this@MpvPlayerEngine::subtitles.isInitialized) subtitles.onLoadTargetResolved(mediaId)
            }

            override fun onTrackListChanged() {
                if (this@MpvPlayerEngine::subtitles.isInitialized) subtitles.onTrackListChanged()
            }

            override fun onFileLoaded() {
                if (this@MpvPlayerEngine::subtitles.isInitialized) subtitles.onFileLoaded()
            }
        },
    )


    /**
     * Phase 4 step 2 seam: track listing/switching, with the host
     * callback for the subtitle state that now lives in
     * [MpvSubtitleRenderer] (step 3). The callback runs at exactly
     * the points the old monolith cleared that state; the seam stays
     * because the track class is built before the subtitle class,
     * so it reaches it through the facade. The host only ever runs
     * after construction (a track pick is a user action), so the
     * `::subtitles.isInitialized` guard never fires in practice.
     */
    private val tracks: MpvTrackSelection = MpvTrackSelection(
        core,
        host = object : MpvTrackSelection.Host {
            override fun clearRememberedExternalPick() {
                if (this@MpvPlayerEngine::subtitles.isInitialized) subtitles.clearRememberedExternalPick()
            }
        },
    )


    /**
     * Phase 4 step 3: subtitle rendering. Built after the core
     * and the track class (it needs both). `lateinit` on purpose:
     * the core's init runs while this property is still being
     * assigned, so the Host callbacks above guard every call with
     * `::subtitles.isInitialized` — they can only fire after
     * construction, but the guard keeps a (currently impossible)
     * early event from crashing on an uninitialised field. The
     * init-time subtitle work (applyPostInitSettings) does not
     * touch this instance: it calls the renderer's companion
     * function with the core passed as an argument.
     */
    private lateinit var subtitles: MpvSubtitleRenderer

    init {
        subtitles = MpvSubtitleRenderer(core, tracks)
    }


    // --- Delegating accessors for the state MpvCoreEngine now owns ---
    // The not-yet-moved code below still reads/writes it by its old
    // names; each step that moves a function deletes the accessors it
    // no longer needs.
    private val mpv: MPV get() = core.mpv

    // Phase 4 step 1: read-only here on purpose. A `var` with an
    // explicit `private set` body generates a JVM setter
    // `setDecoderMode(DecoderMode)` that clashes with the PlayerEngine
    // `setDecoderMode(mode)` function below (the old backing-field
    // `private set` did not); internal writes go through `core`
    // directly (see applyHwdecForMode). Externally this is unchanged:
    // the old setter was private too.
    val decoderMode: DecoderMode
        get() = core.decoderMode

    private var currentQueue: List<PlayableMedia>
        get() = core.currentQueue
        set(value) { core.currentQueue = value }
    private var currentMediaIndex: Int
        get() = core.currentMediaIndex
        set(value) { core.currentMediaIndex = value }
    private var fileLoadedForCurrentItem: Boolean
        get() = core.fileLoadedForCurrentItem
        set(value) { core.fileLoadedForCurrentItem = value }
    private var cachedPositionMs: Long
        get() = core.cachedPositionMs
        set(value) { core.cachedPositionMs = value }
    private var cachedIsPlaying: Boolean
        get() = core.cachedIsPlaying
        set(value) { core.cachedIsPlaying = value }
    private var currentDecoderStage: MpvCoreEngine.DecoderStage
        get() = core.currentDecoderStage
        set(value) { core.currentDecoderStage = value }
    private var fallbackHwdecStage: MpvCoreEngine.DecoderStage?
        get() = core.fallbackHwdecStage
        set(value) { core.fallbackHwdecStage = value }
    private var fallbackReloadPending: Boolean
        get() = core.fallbackReloadPending
        set(value) { core.fallbackReloadPending = value }
    private val mpvInitialized: Boolean get() = core.mpvInitialized
    private val isReleased: Boolean get() = core.isReleased

    private fun setRuntimeProperty(key: String, value: String) =
        core.setRuntimeProperty(key, value)


    private fun loadSavedSubtitleSettings(): AppSettings = core.loadSavedSubtitleSettings()


    /**
     * Applies the subtitle style options (issue #75) to a running mpv instance.
     * All sub-* options used here are runtime-changeable in mpv, so this is
     * safe to call whenever settings change mid-playback — but only through
     * the property API, since setOptionString is pre-init only. No-op before
     * init or after release.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        subtitles.applySubtitleSettings(settings)
    }

    // Phase 4 step 1: the core variant — takes the core explicitly
    // so the core's init can apply the saved settings before this
    // facade's `core` property is assigned (see the Host above).
    private fun applyPlaybackTweaksTo(core: MpvCoreEngine, settings: AppSettings) {
        if (!core.mpvInitialized || core.isReleased) return
        try {
            core.setRuntimeProperty("audio-delay", (settings.audioDelayMs / 1000.0).toString())
            if (currentAspectRatio == AspectRatioMode.ZOOM) {
                // Issue #226: ZOOM owns video-zoom (2x magnification, see
                // setAspectRatio). An unrelated settings change — audio
                // delay, subtitle style — must not silently undo it. Only
                // a user-driven zoom change moves it: the sheet slider and
                // the pinch-end save both change settings.videoZoom, and
                // then the new value becomes both the live zoom and the
                // restore point for leaving ZOOM.
                if (settings.videoZoom != videoZoomAtAspectZoomEntry) {
                    val zoom = mpvVideoZoom(settings.videoZoom)
                    core.setRuntimeProperty("video-zoom", zoom.toString())
                    zoomBeforeAspectZoom = zoom
                    videoZoomAtAspectZoomEntry = settings.videoZoom
                }
            } else {
                core.setRuntimeProperty("video-zoom", mpvVideoZoom(settings.videoZoom).toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying playback tweaks to mpv", e)
        }
    }

    /**
     * Applies audio delay + video zoom (issue #153, Playback sheet) to a
     * running mpv instance. Both are runtime-changeable mpv properties, so
     * this is safe to call whenever settings change mid-playback — same
     * pattern as [applySubtitleSettings]. No-op before init or after release.
     * audio-delay is in seconds (positive = audio later). videoZoom is
     * MPx's plain multiplier (1.0 = no zoom, 0.5..2.0) — it is converted
     * to mpv's log2 video-zoom ([mpvVideoZoom]) because mpv reads the
     * property as log2: 0 is unscaled, 1 doubles the size. Sending the
     * plain multiplier straight through (#172) made every video play at
     * 2x the fit size, cropped and zoomed.
     */
    fun applyPlaybackTweaks(settings: AppSettings) {
        applyPlaybackTweaksTo(core, settings)
    }

    /**
     * Live video-zoom for the pinch gesture (#172): same log2 conversion
     * as [applyPlaybackTweaks], without persisting — the caller saves the
     * final zoom to settings when the gesture ends.
     */
    fun setVideoZoom(zoom: Float) {
        if (!mpvInitialized || isReleased) return
        try {
            setRuntimeProperty("video-zoom", mpvVideoZoom(zoom).toString())
            // Issue #226: a pinch while ZOOM is active moves the zoom the
            // user will get back when leaving ZOOM — the preset must not
            // resurrect the pre-ZOOM value over a newer manual zoom. (The
            // pinch-end save flows through applyPlaybackTweaks and updates
            // the same restore point; this keeps it correct even if the
            // gesture ends without a save.)
            if (currentAspectRatio == AspectRatioMode.ZOOM) {
                zoomBeforeAspectZoom = mpvVideoZoom(zoom)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting video zoom in MPV", e)
        }
    }


    /**
     * mpv's video-zoom is log2, not a plain multiplier (#172): 0 is
     * unscaled, 1 doubles the size, -1 halves it. MPx's videoZoom is a
     * plain multiplier (1.0 = no zoom), so convert with log2. Clamped to
     * the 0.5..2.0 UI range first, so a stray persisted value can never
     * produce NaN (log2 of 0) or an extreme zoom.
     */
    private fun mpvVideoZoom(mpxZoom: Float): Double =
        log2(mpxZoom.coerceIn(0.5f, 2.0f).toDouble())


    private fun hwdecValueFor(mode: DecoderMode): String = hwdecValueForStage(
        when (mode) {
            DecoderMode.HW -> MpvCoreEngine.DecoderStage.HW
            DecoderMode.HW_PLUS -> MpvCoreEngine.DecoderStage.HW_PLUS
            DecoderMode.SW -> MpvCoreEngine.DecoderStage.SW
        }
    )


    private fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = when (stage) {
        MpvCoreEngine.DecoderStage.HW -> "mediacodec"
        MpvCoreEngine.DecoderStage.HW_PLUS -> "mediacodec-copy"
        MpvCoreEngine.DecoderStage.SW -> "no"
    }


    private fun applyHwdecForMode(mode: DecoderMode) {
        core.decoderMode = mode
        currentDecoderStage = when (mode) {
            DecoderMode.HW -> MpvCoreEngine.DecoderStage.HW
            DecoderMode.HW_PLUS -> MpvCoreEngine.DecoderStage.HW_PLUS
            DecoderMode.SW -> MpvCoreEngine.DecoderStage.SW
        }
        val hwdecVal = hwdecValueFor(mode)
        try {
            mpv.setPropertyString("hwdec", hwdecVal)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set hwdec option to $hwdecVal", e)
        }
    }


    /**
     * Issue #226: the six UI modes used to collapse to four engine states
     * — FILL and ZOOM both set panscan=1.0, FIT and ORIGINAL both set
     * panscan=0.0 — so picking ZOOM after FILL changed nothing on screen
     * and 100% just fitted like FIT. Now every mode has a distinct state:
     * ZOOM magnifies 2x beyond FILL via video-zoom (and owns that property
     * while active — see applyPlaybackTweaks), and ORIGINAL is a true 1:1
     * native size via video-unscaled. Every branch sets video-unscaled
     * explicitly, so leaving ORIGINAL re-enables scaling.
     */
    fun setAspectRatio(mode: AspectRatioMode) {
        try {
            val wasZoom = currentAspectRatio == AspectRatioMode.ZOOM
            if (mode == AspectRatioMode.ZOOM && !wasZoom) {
                // Entering ZOOM: capture the live zoom so leaving ZOOM
                // restores exactly what the user had — including an
                // in-progress pinch that has not been saved to settings
                // yet. Re-tapping ZOOM while already in it must not
                // re-capture (the live value is the preset by then).
                val savedZoom = loadSavedSubtitleSettings().videoZoom
                zoomBeforeAspectZoom = try {
                    mpv.getPropertyDouble("video-zoom")
                } catch (_: Exception) {
                    null
                } ?: mpvVideoZoom(savedZoom)
                videoZoomAtAspectZoomEntry = savedZoom
            }
            if (wasZoom && mode != AspectRatioMode.ZOOM) {
                // Leaving ZOOM: give the zoom back. The restore value
                // tracks any slider/pinch adjustment made while ZOOM was
                // active, so the newest manual zoom wins.
                zoomBeforeAspectZoom?.let { setRuntimeProperty("video-zoom", it.toString()) }
                zoomBeforeAspectZoom = null
                videoZoomAtAspectZoomEntry = null
            }
            currentAspectRatio = mode
            when (mode) {
                AspectRatioMode.FIT -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyString("video-unscaled", "no")
                }
                AspectRatioMode.FILL -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 1.0)
                    mpv.setPropertyString("video-unscaled", "no")
                }
                AspectRatioMode.ZOOM -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 1.0)
                    mpv.setPropertyString("video-unscaled", "no")
                    // Magnify 2x beyond FILL's crop-to-fill: video-zoom is
                    // log2, so mpvVideoZoom(2.0f) == 1.0.
                    setRuntimeProperty("video-zoom", mpvVideoZoom(2.0f).toString())
                }
                AspectRatioMode.SIXTEEN_NINE -> {
                    mpv.setPropertyString("video-aspect-override", "16:9")
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyString("video-unscaled", "no")
                }
                AspectRatioMode.FOUR_THREE -> {
                    mpv.setPropertyString("video-aspect-override", "4:3")
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyString("video-unscaled", "no")
                }
                AspectRatioMode.ORIGINAL -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 0.0)
                    // True 1:1: mpv displays the video at native size.
                    mpv.setPropertyString("video-unscaled", "yes")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting aspect ratio $mode in MPV", e)
        }
    }


    override fun selectTrack(type: TrackType, id: String?) = tracks.selectTrack(type, id)


    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
        tracks.selectAudioTrack(groupIndex, trackIndex)


    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) =
        tracks.selectSubtitleTrack(groupIndex, trackIndex)


    override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult =
        subtitles.replaceMediaItem(index, media)


    override fun stop() {
        // Issue #332: subtitle cleanup first (pending-subtitle fds,
        // pin/remembered/confirmation), then the core stop — same order
        // as the old monolith.
        subtitles.cleanupForStopOrRelease()
        core.stop()
    }

    override fun release() {
        // Issue #332: same as stop() — subtitle cleanup first, then
        // the core release.
        subtitles.cleanupForStopOrRelease()
        core.release()
    }

    override fun setDecoderMode(mode: DecoderMode) {
        if (decoderMode == mode) return
        // Issue #212: a deferred fallback reload must not override the
        // user's explicit choice.
        fallbackHwdecStage = null
        fallbackReloadPending = false
        applyHwdecForMode(mode)
        // A live hwdec property change only affects the NEXT decoder init, so
        // the current stream would keep its old decoder and the pill would go
        // stale. MX restarts playback with the newly chosen decoder — do the
        // same: reload the current item at its position. No-op before any load.
        // Issue #224: resume only if it was playing — a paused file stays
        // paused (mirrors reloadForDecoderFallback, which never had this bug).
        if (mpvInitialized && !isReleased && fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            val wasPlaying = cachedIsPlaying
            val pos = cachedPositionMs
            load(currentQueue, currentMediaIndex, pos)
            if (wasPlaying) play()
        }
    }


    override fun setListener(listener: PlayerEngine.Listener?) = core.setListener(listener)

    override fun attachSurface(surface: Surface) = core.attachSurface(surface)

    override fun onSurfaceChanged(width: Int, height: Int) = core.onSurfaceChanged(width, height)

    override fun detachSurface() = core.detachSurface()

    override fun load(media: PlayableMedia, resumeMs: Long) = core.load(media, resumeMs)

    override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) =
        core.load(items, startIndex, resumeMs)

    override fun play() = core.play()

    override fun pause() = core.pause()

    override fun seekTo(positionMs: Long) = core.seekTo(positionMs)

    override fun seekTo(index: Int, positionMs: Long) = core.seekTo(index, positionMs)

    override fun setSpeed(speed: Float) = core.setSpeed(speed)

    override fun getLoadStage(): MpvLoadStage? = core.getLoadStage()

    override fun getPlaybackMetrics(): EnginePlaybackMetrics? = core.getPlaybackMetrics()

    override val currentPosition: Long
        get() = core.currentPosition

    override val videoWidth: Int
        get() = core.videoWidth

    override val videoHeight: Int
        get() = core.videoHeight

    override val duration: Long
        get() = core.duration

    override val bufferedPosition: Long
        get() = core.bufferedPosition

    override val isPlaying: Boolean
        get() = core.isPlaying

    override val playbackState: Int
        get() = core.playbackState

    override val currentMediaItemIndex: Int
        get() = core.currentMediaItemIndex

    override val hasNextMediaItem: Boolean
        get() = core.hasNextMediaItem

    override val hasPreviousMediaItem: Boolean
        get() = core.hasPreviousMediaItem

    override fun seekToNextMediaItem() = core.seekToNextMediaItem()

    override fun seekToPreviousMediaItem() = core.seekToPreviousMediaItem()


    override fun getAudioTracks(): List<AudioTrackInfo> = tracks.getAudioTracks()


    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = tracks.getSubtitleTracks()
}
