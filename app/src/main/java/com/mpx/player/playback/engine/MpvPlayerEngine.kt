package com.mpx.player.playback.engine

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.ExternalSubtitle
import com.mpx.player.playback.MpvFdOpener
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.subtitleMpvOptions
import `is`.xyz.mpv.MPV
import java.io.File
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
 * [MpvTrackSelection], which owns track listing/switching. What stays
 * here until its own step moves it: subtitle rendering/confirmation
 * (step 3) and settings-to-mpv configuration (step 4) — plus
 * [replaceMediaItem], which straddles the core and the subtitle concern
 * (its body is sub-add + #332 confirmation) and so stays here until
 * step 3. The [MpvCoreEngine.Host] implementation below is the seam:
 * the core calls back into it at exactly the points the old monolith
 * ran that code.
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

        /**
         * Issue #332: how long to wait for a sub-added external subtitle
         * track to appear in track-list before reporting the add as
         * failed. Local .srt/.vtt files appear in well under a second;
         * 5s leaves room for slow storage. Issue #372: the timeout also
         * clears the armed pin state, so a track that lands after it
         * still shows (sub-add "select" selected it) but is no longer
         * remembered — the next re-evaluation can lose it, and the
         * failure banner stands as the honest verdict for a 5s-late
         * track.
         */
        private const val SUBTITLE_CONFIRMATION_TIMEOUT_MS = 5_000L
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
                applySubtitleSettingsTo(core, core.loadSavedSubtitleSettings())
                applyPlaybackTweaksTo(core, core.loadSavedSubtitleSettings())
            }

            override fun onLoadStarted() {
                pendingExternalPin = false
            }

            override fun onLoadTargetResolved(mediaId: String) {
                if (rememberedExternalPick?.mediaId != mediaId) {
                    rememberedExternalPick = null
                }
            }

            override fun onTrackListChanged() {
                // Issue #372: re-apply the remembered external pick on
                // every track-list refresh — sub-add is async, so the pin
                // right after sub-add usually runs before the new track
                // is in track-list, and a later seek, VO switch or
                // decoder re-init can unselect the track again (user's
                // 2026-11-08 01:15 log). The re-apply matches by title,
                // not by isSelected, so it lands the moment the track
                // appears and re-lands it after any re-evaluation.
                // Issue #332: a pin landing is the only proof the sub-add
                // actually worked, so it resolves an armed confirmation.
                // Only a pin that WAS pending and just landed counts.
                val hadPendingPin = pendingExternalPin
                val result = reapplyRememberedPick()
                if (hadPendingPin && result.confirms) {
                    pendingExternalPin = false
                    confirmPendingSubtitle(true)
                }
                mainHandler.post {
                    engineListener?.onTracksChanged()
                }
            }

            override fun onFileLoaded() {
                // Issue #102: Dispatch queued external subtitles for the current item now that file context is active
                val pending = pendingSubtitles
                pendingSubtitles = null
                if (pending != null) {
                    // Issue #213/#219: the match also accepts the raw URI
                    // of the current queue item. Subtitles queued while the
                    // load was still deferred for lack of surface carry the
                    // raw URI, and the fresh fd opened when the load flushed
                    // can never equal it — without this the subtitles queued
                    // in replaceMediaItem would be dropped as stale.
                    val matches = activeMediaUri == null ||
                        isPathMatch(pending.mediaUri, activeMediaUri) ||
                        pending.mediaUri == currentQueue.getOrNull(currentMediaIndex)?.uri
                    if (matches) {
                        var issuedAny = false
                        var lastIssuedExternal: PendingSubtitle? = null
                        val subs = pending.subtitles
                        for ((i, sub) in subs.withIndex()) {
                            // Issue #332: skip a URI mpv provably cannot
                            // open — a content:// URI whose fd resolution
                            // failed, or a file deleted since it was
                            // queued — instead of sub-adding it and
                            // letting mpv fail silently. mpv.command()
                            // never throws for a rejected file, so without
                            // this the caller could never know it failed.
                            if (!isUsableSubtitleUri(sub.uri)) {
                                Log.w(TAG, "Skipping unusable pending subtitle ${sub.uri}")
                                continue
                            }
                            try {
                                // Issue #441: decode this file with its
                                // detected codepage, right before mpv
                                // loads it.
                                applySubtitleCodepage(sub.codepage)
                                if (sub.title != null) {
                                    mpv.command("sub-add", sub.uri, sub.flag, sub.title)
                                } else {
                                    mpv.command("sub-add", sub.uri, sub.flag)
                                }
                                issuedAny = true
                                // Issue #372: remember the last external
                                // subtitle issued — each "select" sub-add
                                // selects that track, so the last one is
                                // the pick. The legacy subtitleUri (title
                                // null) has no identity to remember.
                                if (sub.title != null) lastIssuedExternal = sub
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed adding pending subtitle ${sub.uri}", e)
                                // Issue #332: the remaining queued entries
                                // were never handed to mpv, so their
                                // descriptors are still ours — close them.
                                // The failed entry's descriptor is NOT
                                // closed: mpv.command is synchronous, so
                                // by the time it throws libmpv has already
                                // run the open attempt and owns (and
                                // closes) it; closing here would
                                // double-close.
                                fdOpener.closeFds(subs.drop(i + 1).map { it.uri })
                                break
                            }
                        }
                        // Issue #372: remember the picked external
                        // subtitle and pin sid to it — sub-add "select"
                        // alone does not stick. Arm the remembered pick
                        // first: sub-add is async, so the immediate
                        // attempt usually runs before the new track is in
                        // track-list, and the track-list handler
                        // re-applies it until it lands — and again after
                        // any later re-evaluation unselects it. A failed
                        // add (issuedAny false) never arms anything.
                        if (issuedAny) {
                            val lastExternal = lastIssuedExternal
                            if (lastExternal != null && lastExternal.title != null) {
                                rememberedExternalPick = RememberedExternalPick(
                                    pending.mediaId, lastExternal.title, lastExternal.uri,
                                )
                            }
                            pendingExternalPin = true
                            // Issue #332: arm the outcome confirmation
                            // BEFORE the immediate pin attempt — the
                            // track may already be in track-list, and a
                            // synchronous pin landing must resolve it.
                            armSubtitleConfirmation(pending.mediaId)
                            if (reapplyRememberedPick().confirms) {
                                pendingExternalPin = false
                                confirmPendingSubtitle(true)
                            }
                        }
                    } else {
                        Log.w(TAG, "Dropping stale pending subtitles for ${pending.mediaUri} (active: $activeMediaUri)")
                        // Issue #332: the dropped subtitles were never
                        // handed to mpv, so their descriptors are still
                        // ours — close them instead of leaking one fd per
                        // dropped subtitle until the process dies.
                        fdOpener.closeFds(pending.subtitles.map { it.uri })
                    }
                } else {
                    // Issue #189: auto-select an embedded subtitle track
                    // when none is selected. MPx never sets mpv's 'slang'
                    // and never sets 'sid' at load, so mpv's default
                    // sid=auto had no language to match against and
                    // embedded subtitles never appeared unless the user
                    // picked one by hand every time. Prefer the
                    // default-flagged track, else the first embedded track.
                    // Skipped when external subtitles were just added with
                    // 'select' above (they become the selected track), and
                    // when mpv already selected a track itself.
                    try {
                        val tracks = getSubtitleTracks()
                        if (tracks.none { it.isSelected }) {
                            val candidate = tracks.firstOrNull { it.isDefault && !it.isExternal }
                                ?: tracks.firstOrNull { !it.isExternal }
                            if (candidate != null) {
                                selectSubtitleTrack(candidate.groupIndex, candidate.trackIndex)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed auto-selecting a subtitle track", e)
                    }
                }
            }
        },
    )


    /**
     * Phase 4 step 2 seam: track listing/switching, with the host
     * callback for the subtitle state that stays here until step 3.
     * The callback runs at exactly the points the old monolith
     * cleared that state. The host only ever runs after construction
     * (a track pick is a user action), so reading the facade's
     * subtitle fields here is safe.
     */
    private val tracks: MpvTrackSelection = MpvTrackSelection(
        core,
        host = object : MpvTrackSelection.Host {
            override fun clearRememberedExternalPick() {
                rememberedExternalPick = null
                pendingExternalPin = false
            }
        },
    )


    // --- Delegating accessors for the state MpvCoreEngine now owns ---
    // The not-yet-moved code below still reads/writes it by its old
    // names; each step that moves a function deletes the accessors it
    // no longer needs.
    private val mpv: MPV get() = core.mpv
    private val mainHandler: Handler get() = core.mainHandler
    private val fdOpener: MpvFdOpener get() = core.fdOpener
    private val engineListener: PlayerEngine.Listener? get() = core.listener

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
    private var pendingLoad: Triple<List<PlayableMedia>, Int, Long>?
        get() = core.pendingLoad
        set(value) { core.pendingLoad = value }
    private var activeMediaId: String?
        get() = core.activeMediaId
        set(value) { core.activeMediaId = value }
    private var activeMediaUri: String?
        get() = core.activeMediaUri
        set(value) { core.activeMediaUri = value }
    private var pendingSubtitles: PendingSubtitles?
        get() = core.pendingSubtitles
        set(value) { core.pendingSubtitles = value }
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

    private fun isPathMatch(path1: String?, path2: String?): Boolean =
        MpvCoreEngine.isPathMatch(path1, path2)

    private fun loadSavedSubtitleSettings(): AppSettings = core.loadSavedSubtitleSettings()


    /** Issue #372: whether the #332 confirmation is still awaiting the
     * remembered external pick landing (see [rememberedExternalPick]).
     * Set together with the remembered pick whenever sub-adds are issued
     * (replaceMediaItem's loaded branch, or the FILE_LOADED dispatch),
     * cleared the moment a track-list refresh confirms sid is pinned to
     * the remembered track — or by the #332 timeout when the track never
     * appears, so a failed add can never leave a stale armed pin
     * retrying forever. Also cleared on a manual embedded/Off pick
     * (selectSubtitleTrack) and on load/stop/release. Armed on Main
     * (replaceMediaItem) and on the event thread (FILE_LOADED dispatch);
     * read/cleared on the event thread: volatile. */
    @Volatile
    private var pendingExternalPin: Boolean = false


    /** Issue #372: the external subtitle the user picked for the current
     * item, remembered by identity (display name + the URI handed to
     * mpv) so it can be re-applied after mpv unselects it. The #192
     * pin only pinned a track that was currently selected AND external,
     * so once a seek, VO switch or decoder re-init unselected the track
     * the pin could never land (user's 2026-11-08 01:15 log: vo=null
     * surface destroy + decoder re-init + seeks, pin precondition dead).
     * mpv has no sticky pick of its own — sub-add returns no track id
     * and sid resets to auto at playback init — so the app owns the pick
     * (mpvEx's TrackSelector Pass 00 does the same: select ANY external
     * track by explicit id on every file load). [reapplyRememberedPick]
     * sets sid to the remembered track's id on every track-list refresh
     * where the track is present but sid is not already on it, matched
     * by title (the only identity track-list carries — the sub-add title
     * argument sets the track title, per the mpv manual), never by
     * isSelected — so the pin lands the moment the track appears and
     * survives later re-evaluations. Guarded to the current track-list,
     * so a failed sub-add can never resurrect a non-existent track.
     * Cleared when the user picks an embedded track or turns subtitles
     * Off (selectSubtitleTrack), on load() of a different item, on the
     * #332 timeout (track never appeared), and on stop/release. Survives
     * a successful pin: that is the point — a later VO change or reload
     * (fresh track ids) re-applies it by title again. */
    @Volatile
    private var rememberedExternalPick: RememberedExternalPick? = null


    /**
     * Issue #332: outcome confirmation for a sub-add. `mpv.command()`
     * never throws for a rejected subtitle file — the native JNI command
     * only logs `mpv_error_string` (libplayer.so contains no Java
     * exception class at all) — so the issued command proves nothing and
     * the old unconditional success toast lied. The only real signal is
     * the new track appearing in track-list, which the #192 pin already
     * watches: a confirmation is armed whenever a sub-add is issued
     * (replaceMediaItem's loaded branch, or the FILE_LOADED dispatch),
     * resolved the moment the pin lands for the current item, and
     * reported as failure if the track never appears within
     * [SUBTITLE_CONFIRMATION_TIMEOUT_MS]. The listener is notified for
     * every armed confirmation; the manager ignores ids it did not arm
     * (queue restores confirm the same way). Armed on Main
     * (replaceMediaItem) and on the event thread (FILE_LOADED dispatch);
     * read/cleared on the event thread (track-list handler) and Main
     * (timeout): volatile. Cleared on stop/release.
     */
    @Volatile
    private var pendingSubtitleConfirmation: PendingSubtitleConfirmation? = null


    private data class PendingSubtitleConfirmation(val mediaId: String)


    /** Issue #372: identity of a picked external subtitle (see
     * [rememberedExternalPick]). [uri] is the URI actually handed to mpv
     * (resolved: fd:// for content:// picks), [name] the display name
     * passed as the sub-add title — the title is what track-list
     * carries, so it is the match key in [reapplyRememberedPick]. */
    private data class RememberedExternalPick(val mediaId: String, val name: String, val uri: String)


    private val subtitleConfirmationTimeout = Runnable {
        val pending = pendingSubtitleConfirmation ?: return@Runnable
        pendingSubtitleConfirmation = null
        // Issue #372: the track never appeared within the timeout — the
        // sub-add failed. Clear the armed pin state too: the old code
        // reported the failure but left pendingExternalPin armed, so the
        // pin kept retrying on every track-list refresh forever. The
        // remembered pick goes with it — there is no track to remember.
        pendingExternalPin = false
        rememberedExternalPick = null
        Log.d(TAG, "External subtitle confirmation timed out for ${pending.mediaId}: track never appeared, remembered pick cleared")
        engineListener?.onExternalSubtitleConfirmed(pending.mediaId, false)
    }


    /**
     * Issue #332: arm the sub-add outcome confirmation for [mediaId]
     * (replacing any earlier one — the manager only ever waits for the
     * latest pick per item). The timeout reports failure if the track
     * never appears; [confirmPendingSubtitle] resolves it early when the
     * pin lands.
     */
    private fun armSubtitleConfirmation(mediaId: String) {
        mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        pendingSubtitleConfirmation = PendingSubtitleConfirmation(mediaId)
        mainHandler.postDelayed(subtitleConfirmationTimeout, SUBTITLE_CONFIRMATION_TIMEOUT_MS)
    }


    /**
     * Issue #332: resolve an armed confirmation. Only confirms when the
     * confirmation belongs to the currently loaded item — a pin landing
     * after a queue advance belongs to the new item, not the old pick.
     * A failure after the timeout has already fired is a no-op (the
     * field is null by then).
     */
    private fun confirmPendingSubtitle(success: Boolean) {
        val pending = pendingSubtitleConfirmation ?: return
        if (pending.mediaId != activeMediaId) return
        pendingSubtitleConfirmation = null
        mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        mainHandler.post {
            engineListener?.onExternalSubtitleConfirmed(pending.mediaId, success)
        }
    }


    /**
     * Issue #332: whether a resolved subtitle URI is one mpv can actually
     * open. A `content://` URI that survived resolution unchanged means
     * [MpvFdOpener] could not open it (permission revoked, file gone) —
     * mpv cannot open `content://` at all, so sub-adding it can only
     * fail. A plain filesystem path must exist. `fd://` (resolution
     * succeeded) and http(s) (mpv tries the network) are usable; their
     * real outcome is confirmed asynchronously via the track-list pin.
     */
    private fun isUsableSubtitleUri(uri: String): Boolean = when {
        uri.startsWith("fd://") -> true
        uri.startsWith("content://") -> false
        uri.startsWith("http://") || uri.startsWith("https://") -> true
        else -> File(uri.removePrefix("file://")).exists()
    }


    // Phase 4 step 1: the core variant — takes the core explicitly
    // so the core's init can apply the saved settings before this
    // facade's `core` property is assigned (see the Host above).
    private fun applySubtitleSettingsTo(core: MpvCoreEngine, settings: AppSettings) {
        if (!core.mpvInitialized || core.isReleased) return
        try {
            val options = subtitleMpvOptions(settings)
            options.forEach { (key, value) ->
                core.setRuntimeProperty(key, value)
            }
            Log.d(TAG, "Applied subtitle settings: sub-visibility=${options["sub-visibility"]}, " +
                "sub-font=${options["sub-font"]}, sub-font-size=${options["sub-font-size"]}, " +
                "sub-color=${options["sub-color"]}, sub-border-color=${options["sub-border-color"]}, " +
                "sub-border-size=${options["sub-border-size"]}, sub-back-color=${options["sub-back-color"]}, " +
                "sub-ass-override=${options["sub-ass-override"] ?: "default"}, " +
                "sub-delay=${options["sub-delay"]}, sub-speed=${options["sub-speed"]}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying subtitle settings to mpv", e)
        }
    }

    /**
     * Applies the subtitle style options (issue #75) to a running mpv instance.
     * All sub-* options used here are runtime-changeable in mpv, so this is
     * safe to call whenever settings change mid-playback — but only through
     * the property API, since setOptionString is pre-init only. No-op before
     * init or after release.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        applySubtitleSettingsTo(core, settings)
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
     * Issue #441: set mpv's subtitle decoding for the file about to be
     * sub-added. A detected codepage ("cp1256" for Windows-1256 Arabic
     * subtitles) is set explicitly; otherwise the codepage is reset to
     * mpv's default ("auto") so a previous file's cp1256 cannot leak
     * into the next file. Must run immediately before the sub-add —
     * mpv decodes the file when it is loaded. If mpv rejects the set
     * (sub-codepage is not runtime-changeable in this build), it fails
     * silently through setRuntimeProperty and the file decodes exactly
     * as today — the fix degrades to no-op, never to a crash.
     */
    private fun applySubtitleCodepage(codepage: String?) {
        setRuntimeProperty("sub-codepage", codepage ?: "auto")
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


    /**
     * Issue #197: are two ExternalSubtitle entries the same file? The
     * display name is the stable identity — but only the stable one for
     * content:// subtitles, whose engine-side URI is a fresh `fd://<n>`
     * on every resolve (MpvFdOpener opens a new fd per call), so the URI
     * can never match across calls for them. File-path subtitles keep a
     * stable URI, so those must match too: two different files that
     * happen to share a display name (different folders) are NOT the
     * same sub. Honest limit: two same-named content:// files cannot be
     * told apart here — the engine never sees their original URI — so
     * the second one is skipped. The manager still dedupes the item's
     * list by original URI, so the list itself stays correct.
     */
    private fun isSameExternalSubtitle(a: ExternalSubtitle, b: ExternalSubtitle): Boolean {
        if (a.name != b.name) return false
        return a.uri == b.uri || a.uri.startsWith("fd://") || b.uri.startsWith("fd://")
    }


    /**
     * Issue #372: outcome of one [reapplyRememberedPick] attempt.
     * [confirms] is true when the desired end state holds — the picked
     * external subtitle is present in track-list and sid selects it —
     * so an armed #332 confirmation may resolve as success.
     * APPLIED — sid was set to the remembered track (title match) this
     * refresh. ALREADY_PINNED — the remembered track is present,
     * selected, and sid is already its id. COUNT_CONFIRMED — no title
     * match, but every external subtitle the current item carries has
     * produced a track, so the remembered one is necessarily present
     * (see [allExpectedExternalTracksPresent]) and sid was set to the
     * selected external track. FALLBACK_APPLIED — sid was set to the
     * selected external track with no title match, but the count is
     * short, so the pick is not confirmed yet. NOT_PRESENT — the
     * remembered track is not in the current track-list and no external
     * track is selected. BLOCKED — a title match exists but a different
     * external track is currently selected, so the remembered pick must
     * not override that manual pick. NOTHING_PENDING — no remembered
     * pick and no armed pin (steady state).
     */
    private enum class ReapplyResult(val confirms: Boolean) {
        APPLIED(true),
        ALREADY_PINNED(true),
        COUNT_CONFIRMED(true),
        FALLBACK_APPLIED(false),
        NOT_PRESENT(false),
        BLOCKED(false),
        NOTHING_PENDING(false),
    }


    /**
     * Issue #372: re-apply the remembered external pick — the replacement
     * for the #192 pin. `sub-add` with the "select" flag only changes the
     * current selection; it leaves `sid` at its default "auto", so mpv's
     * next track-selection re-evaluation (seek, decoder reinit, VO
     * switch) falls back to the default-flagged embedded track and the
     * external subtitle the user just picked silently disappears (user's
     * v0.3.1 log: sid=2 selected, sid=1 back two seconds later; user's
     * 2026-11-08 01:15 log: vo=null surface destroy + decoder re-init +
     * seeks). Setting `sid` to the track id explicitly makes the pick
     * sticky — but the old pin only pinned a track that was currently
     * selected AND external, so once a re-evaluation unselected the track
     * the pin could never land. This matches by identity instead: the
     * remembered track is found in track-list by title (the only identity
     * track-list carries) and `sid` is set to its id whenever `sid` is
     * not already on it — so it lands the moment the track appears after
     * sub-add AND re-lands after any later re-evaluation unselects it.
     * Safe to call on every track-list refresh: it only ever sets `sid`
     * to the remembered track's id, only when that track is present in
     * the current track-list (a failed sub-add can never resurrect a
     * non-existent track), and never while a different external track is
     * selected (BLOCKED — a manual pick of another external track wins).
     * A manual embedded/Off pick clears the remembered pick first
     * (selectSubtitleTrack), so this can never override one either.
     * Callers clear [pendingExternalPin] and resolve the #332
     * confirmation only when the result [ReapplyResult.confirms].
     *
     * Title-mismatch fallback: the v0.3.1 log showed mpv reporting the
     * added track's title as "srt" for fd:// subs, so a title match can
     * never be assumed — when no title matches, this falls back to the
     * old #192 rule and pins the currently selected external track
     * (that track is either mpv's own "select" choice or the user's
     * manual external pick, so it cannot override anything). The
     * confirmation then resolves only via [allExpectedExternalTracksPresent].
     */
    private fun reapplyRememberedPick(): ReapplyResult {
        val remembered = rememberedExternalPick
        if (remembered == null && !pendingExternalPin) return ReapplyResult.NOTHING_PENDING
        val tracks = try {
            getSubtitleTracks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading subtitle tracks for remembered pick", e)
            return ReapplyResult.NOT_PRESENT
        }
        // Primary: match the remembered pick by title. mpv appends added
        // tracks, so the just-added one is last; prefer the selected
        // match when there is one (sub-add "select" selects it) — two
        // same-named files from different folders share a title, and the
        // selected/last one is the pick.
        if (remembered != null) {
            val candidates = tracks.filter { it.isExternal && it.label == remembered.name }
            val track = candidates.lastOrNull { it.isSelected } ?: candidates.lastOrNull()
            if (track != null) {
                if (tracks.any { it.isExternal && it.isSelected && it.trackIndex != track.trackIndex }) {
                    Log.d(TAG, "Remembered external subtitle '${remembered.name}' not re-applied: a different external track is selected")
                    return ReapplyResult.BLOCKED
                }
                val currentSid = try { mpv.getPropertyString("sid") } catch (_: Exception) { null }
                if (track.isSelected && currentSid == track.trackIndex.toString()) {
                    return ReapplyResult.ALREADY_PINNED
                }
                return try {
                    mpv.setPropertyString("sid", track.trackIndex.toString())
                    Log.d(TAG, "Remembered external subtitle '${remembered.name}': sid=${track.trackIndex} (was sid=$currentSid, selected=${track.isSelected})")
                    ReapplyResult.APPLIED
                } catch (e: Exception) {
                    Log.w(TAG, "Failed re-applying remembered external subtitle '${remembered.name}'", e)
                    ReapplyResult.NOT_PRESENT
                }
            }
            Log.d(TAG, "Remembered external subtitle '${remembered.name}' not in track-list by title yet (external tracks: ${tracks.count { it.isExternal }})")
        }
        // Fallback: no title match (or a legacy-only batch, which has no
        // identity to match — a restored queue's single subtitleUri was
        // sub-added without a title). Pin the currently selected external
        // track, if any — the old #192 rule, kept for this path only.
        val selected = tracks.firstOrNull { it.isExternal && it.isSelected }
            ?: return ReapplyResult.NOT_PRESENT
        val currentSid = try { mpv.getPropertyString("sid") } catch (_: Exception) { null }
        if (currentSid != selected.trackIndex.toString()) {
            try {
                mpv.setPropertyString("sid", selected.trackIndex.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed pinning external subtitle selection", e)
                return ReapplyResult.NOT_PRESENT
            }
            Log.d(TAG, "External subtitle pin (fallback, no title match for '${remembered?.name}'): sid=${selected.trackIndex} (was sid=$currentSid, selected=${selected.isSelected})")
        }
        val mediaId = remembered?.mediaId ?: activeMediaId
        return if (allExpectedExternalTracksPresent(tracks, mediaId)) {
            Log.d(TAG, "External subtitle confirmed by count for $mediaId: ${tracks.count { it.isExternal }} external tracks present")
            ReapplyResult.COUNT_CONFIRMED
        } else {
            ReapplyResult.FALLBACK_APPLIED
        }
    }


    /** Issue #372: have all external subtitles the item [mediaId] carries
     * produced a track in [tracks]? One track per sub-add, no dedupe — so
     * an external-track count at least the item's external-subtitle count
     * (plus one for a legacy subtitleUri) means the remembered pick is
     * necessarily present, even when its title didn't match (mpv
     * reporting "srt" for fd:// subs, v0.3.1 log). Same count the
     * manager's ALREADY_ADDED branch uses. */
    private fun allExpectedExternalTracksPresent(tracks: List<SubtitleTrackInfo>, mediaId: String?): Boolean {
        if (mediaId == null) return false
        val item = currentQueue.firstOrNull { it.id == mediaId } ?: return false
        val expected = item.externalSubtitles.size + if (item.subtitleUri != null) 1 else 0
        if (expected == 0) return false
        return tracks.count { it.isExternal } >= expected
    }


    override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult {
        if (index !in currentQueue.indices) {
            return ReplaceMediaItemResult.FAILED
        }
        // Issue #197: remember the item mpv already has, so only
        // genuinely NEW external subtitles are sub-added below.
        val previous = currentQueue.getOrNull(index)
        val updated = currentQueue.toMutableList()
        updated[index] = media
        currentQueue = updated

        val pending = pendingLoad
        if (pending != null && index == pending.second) {
            pendingLoad = Triple(updated, pending.second, pending.third)
        }

        if (index != currentMediaIndex) {
            // Not the live item: the subtitles are stored in the queue
            // and will be attempted when the item loads (issue #332:
            // same outcome as QUEUED — the FILE_LOADED dispatch arms
            // the confirmation then).
            return ReplaceMediaItemResult.QUEUED
        }

        // Issue #213/#219: the stored queue keeps the RAW item
        // (original URIs) so a later load() re-resolves it fresh.
        // Only subtitle URIs are resolved here — replaceMediaItem
        // never reloads the media (sub-add only), so resolving the
        // media URI would open an fd nobody uses. The dedupe below
        // still compares RAW lists on both sides, so name+URI
        // identity keeps working (and, unlike before, two different
        // same-named content:// files can now be told apart by URI).
        if (fileLoadedForCurrentItem) {
            // Issue #197: sub-add ONLY the external subtitles
            // that are new since the previous item. The manager
            // hands replaceMediaItem the item's FULL external
            // list (existing + newly added), and mpv creates a
            // brand-new track for every sub-add call with no
            // dedupe — so re-adding the whole list duplicated
            // the first .srt the moment a second one was added.
            // The not-loaded branch below intentionally keeps
            // the full list: pendingSubtitles is overwritten,
            // not appended, and dispatched exactly once on
            // FILE_LOADED, so it cannot duplicate. Only the new
            // subtitles' URIs are resolved — the legacy single
            // subtitleUri is never sub-added on this branch, so
            // opening an fd for it would leak it.
            val newExts = media.externalSubtitles.filter { rawExt ->
                previous?.externalSubtitles?.none { isSameExternalSubtitle(it, rawExt) } ?: true
            }
            val resolved = fdOpener.resolveExternalSubtitles(newExts)
            var issuedAny = false
            var lastIssued: Pair<ExternalSubtitle, ExternalSubtitle>? = null
            for ((i, pair) in newExts.zip(resolved).withIndex()) {
                val (rawExt, resolvedExt) = pair
                // Issue #332: skip a URI mpv provably cannot open — a
                // content:// URI whose fd resolution failed, or a
                // missing file — instead of sub-adding it and letting
                // mpv fail silently. mpv.command() never throws for a
                // rejected file, so without this the caller could never
                // know the add failed.
                if (!isUsableSubtitleUri(resolvedExt.uri)) {
                    Log.w(TAG, "Skipping unusable external subtitle ${rawExt.name} (${resolvedExt.uri})")
                    continue
                }
                try {
                    // Issue #441: decode this file with its detected
                    // codepage, right before mpv loads it.
                    applySubtitleCodepage(rawExt.codepage)
                    mpv.command("sub-add", resolvedExt.uri, "select", rawExt.name)
                    issuedAny = true
                    // Issue #372: each "select" sub-add selects that
                    // track, so the last issued one is the pick.
                    lastIssued = rawExt to resolvedExt
                } catch (e: Exception) {
                    Log.w(TAG, "Failed adding external subtitle ${rawExt.name}", e)
                    // Issue #332: the remaining resolved entries were
                    // never handed to mpv, so their descriptors are
                    // still ours — close them. The failed entry's
                    // descriptor is NOT closed: mpv.command is
                    // synchronous, so by the time it throws libmpv has
                    // already run the open attempt and owns (and
                    // closes) it; closing here would double-close.
                    fdOpener.closeFds(resolved.drop(i + 1).map { it.uri })
                    break
                }
            }
            // Issue #372: remember the picked external subtitle and pin
            // sid to it — sub-add "select" alone does not stick. Arm the
            // remembered pick first: sub-add is async, so the immediate
            // attempt usually runs before the new track is in track-list,
            // and the track-list handler re-applies it until it lands —
            // and again after any later re-evaluation unselects it. A
            // failed add (issuedAny false) never arms anything.
            if (issuedAny) {
                lastIssued?.let { (rawExt, resolvedExt) ->
                    rememberedExternalPick = RememberedExternalPick(media.id, rawExt.name, resolvedExt.uri)
                }
                pendingExternalPin = true
                // Issue #332: arm the outcome confirmation BEFORE
                // the immediate pin attempt — the track may already
                // be in track-list, and a synchronous pin landing
                // must resolve it.
                armSubtitleConfirmation(media.id)
                if (reapplyRememberedPick().confirms) {
                    pendingExternalPin = false
                    confirmPendingSubtitle(true)
                }
                return ReplaceMediaItemResult.ISSUED
            }
            // Issue #332: nothing was issued — either every new
            // subtitle was unusable, or there was nothing new at all
            // (the subtitles are already in the item).
            return if (newExts.isEmpty()) {
                ReplaceMediaItemResult.ALREADY_ADDED
            } else {
                ReplaceMediaItemResult.FAILED
            }
        } else {
            // Queue for dispatch when FILE_LOADED arrives. The
            // subtitle URIs are the resolved ones (mpv cannot open
            // content://); mediaUri is the currently-loading item's
            // resolved URI so the FILE_LOADED match succeeds, falling
            // back to the raw URI — the FILE_LOADED match also
            // accepts the raw URI of the current item, which covers
            // a load still deferred for lack of surface.
            val resolved = fdOpener.resolveSubtitlesOnly(media)
            val subs = mutableListOf<PendingSubtitle>()
            resolved.subtitleUri?.let { subUri ->
                subs.add(PendingSubtitle(uri = subUri, flag = "select"))
            }
            resolved.externalSubtitles.forEach { ext ->
                subs.add(PendingSubtitle(uri = ext.uri, flag = "select", title = ext.name, codepage = ext.codepage))
            }
            // Issue #332: close the previous pending subtitles'
            // descriptors before overwriting — they were never
            // dispatched, so they are still ours.
            pendingSubtitles?.let { fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
            pendingSubtitles = if (subs.isNotEmpty()) {
                PendingSubtitles(
                    mediaUri = activeMediaUri ?: media.uri,
                    mediaId = media.id,
                    subtitles = subs,
                )
            } else {
                null
            }
            return if (subs.isNotEmpty()) {
                ReplaceMediaItemResult.QUEUED
            } else {
                ReplaceMediaItemResult.FAILED
            }
        }
    }


    override fun stop() {
        // Issue #332: the pending subtitles were never dispatched, so
        // their descriptors are still ours — close them instead of
        // leaking one fd per abandoned subtitle. The confirmation is
        // dropped too: the stop itself is the outcome, and no banner
        // should arrive for an item that no longer plays.
        pendingSubtitles?.let { fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
        pendingSubtitles = null
        pendingExternalPin = false
        rememberedExternalPick = null
        pendingSubtitleConfirmation = null
        mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        core.stop()
    }

    override fun release() {
        // Issue #332: same as stop() — never-dispatched pending
        // subtitles still own their descriptors; close them, and drop
        // the confirmation (the timeout runnable is removed with every
        // other callback below).
        pendingSubtitles?.let { fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
        pendingSubtitles = null
        pendingExternalPin = false
        rememberedExternalPick = null
        pendingSubtitleConfirmation = null
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
