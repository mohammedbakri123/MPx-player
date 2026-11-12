package com.mpx.player.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvFdOpener
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.MpvLogFile
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlayableMedia

/**
 * Phase 4 step 1: the core mpv engine, extracted from [MpvPlayerEngine].
 *
 * Owns the mpv lifecycle (MPV creation/init, release), the load/play
 * commands (load, play, pause, seek, setSpeed, stop, next/previous),
 * position/state observation (cached position/duration/buffered/playing/
 * state, property getters, mpv event + property observation) and the
 * decoder fallback chain. [MpvPlayerEngine] stays the [PlayerEngine]
 * implementation and is a thin facade delegating to this class.
 *
 * What is NOT here yet (a later Phase 4 step, reached through [Host]):
 * settings-to-mpv configuration (step 4). Subtitle
 * rendering/confirmation moved to [MpvSubtitleRenderer] in step 3, and
 * track listing/switching moved to [MpvTrackSelection] in step 2. The
 * host callbacks below are the seams for those concerns: they run at
 * exactly the points the old monolith ran that code, so behaviour is
 * unchanged. The only mpv knowledge the facade still touches directly
 * is the not-yet-moved code, through [mpv] and the small internal helpers.
 *
 * Verifiable by CI build + device only: constructing this class creates
 * a real MPV instance (native lib), so there are no unit tests for the
 * engine itself — except [isPathMatch], which lives in the companion
 * object precisely so it can be tested without mpv (see
 * MpvCoreEngineTest).
 */
internal class MpvCoreEngine(
    private val context: Context,
    initialDecoderMode: DecoderMode = DecoderMode.HW,
    private val host: Host,
    internal val port: MpvPort = RealMpvPort(),
    internal val mainHandler: EngineHandler = RealEngineHandler(),
) {

    /**
     * Seams for the concerns that stay in [MpvPlayerEngine] until their
     * Phase 4 step moves them (step 4: settings-to-mpv) or that live in
     * the extracted classes and are reached through the facade (step 3:
     * [MpvSubtitleRenderer]; step 2: [MpvTrackSelection]). Every method
     * is called at exactly the point the old monolith ran the equivalent
     * code.
     */
    interface Host {
        /** mpv hwdec option value for a user decoder mode (step 4). Pure; safe during init. */
        fun hwdecValueFor(mode: DecoderMode): String
        /** mpv hwdec option value for a fallback stage (step 4). Pure; safe during init. */
        fun hwdecValueForStage(stage: DecoderStage): String
        /** Apply a decoder mode to the running mpv instance (step 4). */
        fun applyHwdecForMode(mode: DecoderMode)
        /** Apply the saved subtitle style + playback tweaks after init (steps 3-4). */
        fun applyPostInitSettings(core: MpvCoreEngine)
        /** A load started: clear the subtitle pin state for the old item (step 3). */
        fun onLoadStarted()
        /** The load target resolved: drop a remembered pick for another item (step 3). */
        fun onLoadTargetResolved(mediaId: String)
        /** The mpv track-list property changed: re-apply the remembered pick (step 3). */
        fun onTrackListChanged()
        /** FILE_LOADED arrived: dispatch queued subtitles or auto-select (steps 2-3). */
        fun onFileLoaded()
        /** Live queue provider for fallback reload and surface reattach (D-1 single-source-of-truth). */
        fun getCurrentQueue(): List<PlayableMedia> = emptyList()
        /** Live media index provider (D-1 single-source-of-truth). */
        fun getCurrentMediaIndex(): Int = -1
        /** Fresh settings loader for subtitle and playback configuration (D-12). */
        fun loadSettings(): AppSettings = AppSettings()
    }

    internal enum class DecoderStage { HW, HW_PLUS, SW }

    companion object {
        private const val TAG = "MpvPlayerEngine"

        /**
         * Issue #332 helper, kept here (not on the instance) so it is
         * unit-testable without an mpv instance: whether two paths name
         * the same file, ignoring a leading `file://`.
         */
        internal fun isPathMatch(path1: String?, path2: String?): Boolean {
            if (path1 == null || path2 == null) return false
            if (path1 == path2) return true
            val p1 = path1.removePrefix("file://")
            val p2 = path2.removePrefix("file://")
            return p1 == p2
        }
    }


    private val appContext: Context = context.applicationContext

    private val surfaceLock = Any()

    // Issue #213/#219: the engine resolves content:// URIs itself, lazily,
    // only for the item it is about to loadfile (see load()). The stored
    // queue always keeps the original URIs — an fd is single-use, so a
    // stored fd:// URI would be dead the second time it is loaded.
    internal val fdOpener = MpvFdOpener(appContext)

    private var engineListener: PlayerEngine.Listener? = null


    /** The listener the facade's subtitle code notifies for #332 confirmations. */
    internal val listener: PlayerEngine.Listener?
        get() = engineListener

    internal fun getCurrentQueue(): List<PlayableMedia> = host.getCurrentQueue()
    internal fun getCurrentMediaIndex(): Int = host.getCurrentMediaIndex()

    private var currentSurface: Surface? = null

    /**
     * Whether libmpv's "vo" is currently "null" (issue #161). MPx's own
     * writes null it in exactly one place — detachSurface() — but mpv can
     * also null the VO itself when a reconfigure fails (issue #210: a
     * resize while paused), so the flag is a hint, not the truth. The
     * "vo" property observation (see handlePropertyUpdate) keeps it in
     * sync with mpv's actual state: it is set when mpv reports "null"
     * and cleared when mpv reports a live VO again. The flag-gated
     * restores (attach/load/time-pos/surfaceChanged) stay as fast paths;
     * the observation is the backstop that fires when none of them can.
     * All readers/writers run under surfaceLock (see attach/detach/load).
     */
    internal var voIsNull: Boolean = false

    internal val isReleased: Boolean
        get() = lifecycleManager.isReleased

    internal val mpvInitialized: Boolean
        get() = lifecycleManager.mpvInitialized

    @Volatile
    internal var currentDecoderStage: DecoderStage = DecoderStage.HW

    /**
     * Issue #212: the hwdec stage the fallback stepped down to, carried
     * through the fallback reload. load() normally re-applies the USER's
     * decoderMode (applyHwdecForMode), which would undo the step-down —
     * so the reload consumes this override instead, without touching
     * decoderMode (the user's setting stays; the active decoder is
     * reported via notifyDecoderChanged). Consumed inside load() only
     * once it actually proceeds to apply hwdec, so a surface-deferred
     * reload still carries it when attachSurface() flushes it.
     * Cleared by an explicit setDecoderMode (the user's choice wins).
     */
    @Volatile
    internal var fallbackHwdecStage: DecoderStage? = null

    /**
     * Issue #212: a fallback reload is in flight (loadfile issued,
     * FILE_LOADED not yet arrived). Duplicate triggers for the same
     * failure — several mediacodec log lines, or a log line plus a
     * premature END_FILE — are ignored while this is set, so one
     * failure steps down once. Cleared on FILE_LOADED, or by a fresh
     * non-fallback load().
     */
    @Volatile
    internal var fallbackReloadPending: Boolean = false

    internal var decoderMode: DecoderMode = initialDecoderMode
    // Cached playback state
    @Volatile
    internal var cachedPositionMs: Long = 0L

    @Volatile
    internal var cachedDurationMs: Long = 0L

    @Volatile
    internal var cachedBufferedMs: Long = 0L

    @Volatile
    internal var cachedIsPlaying: Boolean = false

    /**
     * Issue #246: explicit play intent, recorded by play()/pause() and
     * consulted by attachSurface() when it flushes a not-yet-loaded file.
     * Neither cachedIsPlaying nor mpv's "pause" property can answer that
     * question for a file that never loaded: play() deliberately does NOT
     * force cachedIsPlaying=true (the real state arrives via the
     * pause/idle observers once libmpv starts), and with no file loaded
     * mpv is idle so the observer computes playing=false even after
     * play() ran — so a cachedIsPlaying gate would break every
     * play-from-library. And "pause" cannot tell a fresh autoPlay
     * (pause=false, play() called) from a paused restore (pause=false,
     * play() never called — autoPlay=false). Only the explicit intent
     * recorded here distinguishes them. Set by play(), cleared by
     * pause()/stop()/release(); load() deliberately never touches it
     * (startPlayback loads BEFORE it plays).
     */
    @Volatile
    internal var playRequested: Boolean = false

    @Volatile
    internal var cachedPlaybackState: Int = PlayerEngine.STATE_IDLE

    @Volatile
    internal var cachedSpeed: Float = 1.0f

    // Issue #260: last video display size notified to the listener, so
    // the dwidth/dheight branch below only fires on a real change.
    private var cachedVideoWidth: Int = 0
    private var cachedVideoHeight: Int = 0

    private var activeHwdec: String = ""

    private var activeVideoCodec: String = ""

    /** True once libmpv confirms the current item actually loaded (FILE_LOADED).
     * Stays false when loadfile fails outright — the key signal that distinguishes
     * "mpv never started this file" from a mid-playback decoder error. Written on
     * the event thread, read on Main: volatile. */
    @Volatile
    internal var fileLoadedForCurrentItem: Boolean = false

    /** Last error/warning log lines from libmpv (capped); surfaced when a file fails to load. */
    private val lastErrorLogs: ArrayDeque<String> = ArrayDeque()

    /** A video load deferred because no surface exists yet (see load()). Flushed by
     * attachSurface(); audio loads are never deferred (no video output needed). */
    @Volatile
    internal var pendingLoad: Triple<List<PlayableMedia>, Int, Long>? = null

    /** A seek requested before the current file loaded (e.g. resume position fired
     * on item transition while the load was still deferred for lack of surface).
     * Applied the moment FILE_LOADED arrives, so a seek is never silently lost.
     * Written on Main, read/cleared on the event thread: volatile. */
    @Volatile
    internal var pendingSeekMs: Long? = null

    @Volatile
    internal var activeMediaId: String? = null

    @Volatile
    internal var activeMediaUri: String? = null

    /** External subtitles queued before FILE_LOADED (issue #102). Dispatched once libmpv
     * confirms the file is loaded and active, avoiding "no file loaded" sub-add rejections.
     * Written on Main, read/cleared on the event thread: volatile. */
    @Volatile
    internal var pendingSubtitles: PendingSubtitles? = null

    // --- Step-level load diagnostics (shown on-phone in Settings → Development) ---
    @Volatile
    internal var loadIssued: Boolean = false

    @Volatile
    internal var loadGeneration: Long = 0L

    @Volatile
    internal var currentLoadGeneration: Long = 0L

    @Volatile
    private var lastMpvEvent: String = "none"

    private val metricsTracker = MpvMetricsTracker()

    internal val firstFrameRecorded: Boolean
        get() = metricsTracker.firstFrameRecorded

    internal var pendingInitError: Exception?
        get() = lifecycleManager.pendingInitError
        set(value) { lifecycleManager.pendingInitError = value }

    private val eventObserver = object : MpvPort.EventObserver {
        override fun eventProperty(property: String) {
            handlePropertyUpdate(property)
        }

        override fun event(eventId: Int, endFile: MpvPort.EndFile?) {
            handleMpvEvent(eventId, endFile)
        }
    }

    private val logObserver = MpvPort.LogObserver { prefix, level, text ->
        if (isReleased) return@LogObserver
        MpvLogFile.append(appContext, prefix, level, text)
        if (level <= 20) { // MPV_LOG_LEVEL_WARN or ERROR
            Log.w(TAG, "[$prefix] $text")
            // Keep a short ring buffer of the real libmpv diagnostics. When
            // a file fails to load, these lines (e.g. demuxer/ffmpeg errors)
            // are the only record of WHY — previously they were only in logcat.
            synchronized(lastErrorLogs) {
                lastErrorLogs.addLast("[$prefix] ${text.trim()}")
                while (lastErrorLogs.size > 10) lastErrorLogs.removeFirst()
            }
            if (prefix.equals("mediacodec", ignoreCase = true) &&
                (text.contains("failed", ignoreCase = true) || text.contains("error", ignoreCase = true))
            ) {
                mainHandler.post {
                    if (isReleased) return@post
                    attemptDecoderFallback("Mediacodec error: $text")
                }
            }
        }
    }

    private val lifecycleManager = MpvLifecycleManager(
        appContext = appContext,
        port = port,
        host = host,
        eventObserver = eventObserver,
        logObserver = logObserver,
    )

    private val eventLock get() = lifecycleManager.eventLock

    init {
        decoderMode = initialDecoderMode
        currentDecoderStage = when (initialDecoderMode) {
            DecoderMode.HW -> DecoderStage.HW
            DecoderMode.HW_PLUS -> DecoderStage.HW_PLUS
            DecoderMode.SW -> DecoderStage.SW
        }
        lifecycleManager.initialize(
            initialDecoderMode = initialDecoderMode,
            loadSavedSettings = { loadSavedSubtitleSettings() },
            applyPostInit = { host.applyPostInitSettings(this) },
            onInitFailed = { lastMpvEvent = "init-failed" }
        )
    }

    internal fun setRuntimeProperty(key: String, value: String) {
        lifecycleManager.setRuntimeProperty(key, value)
    }

    private fun setOption(key: String, value: String) {
        lifecycleManager.setOption(key, value)
    }

    private fun rejectedOptionsText(): String = lifecycleManager.rejectedOptionsText()


    internal fun loadSavedSubtitleSettings(): AppSettings {
        return try {
            host.loadSettings()
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading saved subtitle settings, using defaults", e)
            AppSettings()
        }
    }


    /**
     * Decoder chain fallback:
     * hwdec mediacodec (HW) -> mediacodec-copy (HW+) -> hwdec=no (SW) -> hard failure reported as a real error
     * on hard failure, surfacing a real error naming the codec (never a silent stall).
     * Issue #212: a live hwdec change only affects the NEXT decoder init,
     * so each step-down reloads the current item at its position (see
     * reloadForDecoderFallback) — otherwise the failed decoder keeps
     * decoding the current stream and the step-down is a no-op.
     */
    private fun attemptDecoderFallback(reason: String) {
        // Issue #258: a fallback trigger queued on mainHandler before a
        // deliberate stop still runs after it — and the premature-EOF
        // check posts exactly such a trigger. The reload guard below
        // would stop the reload, but the stage would still step down
        // (HW -> HW+) and set the hwdec property for nothing. Nothing
        // is loaded and there is no queue after a stop, so there is
        // nothing to fall back for: drop stale triggers here, at the
        // single entry point, instead of in each caller.
        val queue = getCurrentQueue()
        if (isReleased || !fileLoadedForCurrentItem || queue.isEmpty()) {
            Log.d(TAG, "Decoder fallback ignored; nothing loaded or released ($reason)")
            return
        }
        // Issue #212: coalesce duplicate triggers for the same failure
        // (several mediacodec log lines, or a log line plus a premature
        // END_FILE) — while a fallback reload is in flight, one failure
        // must step down once. Without this, stale duplicates could step
        // down again after the reload recovered, and at SW surface a hard
        // error while the file plays fine.
        if (fallbackReloadPending) {
            Log.d(TAG, "Decoder fallback already reloading; ignoring duplicate trigger ($reason)")
            return
        }
        val codec = port.getPropertyString("video-codec") ?: activeVideoCodec.ifBlank { "unknown" }
        when (currentDecoderStage) {
            DecoderStage.HW -> {
                metricsTracker.recordDecoderFallback()
                Log.w(TAG, "HW mediacodec failed for $codec ($reason). Stepping down to HW+ (mediacodec-copy)...")
                currentDecoderStage = DecoderStage.HW_PLUS
                try {
                    port.setPropertyString("hwdec", "mediacodec-copy")
                    reloadForDecoderFallback()
                } catch (e: Exception) {
                    attemptDecoderFallback("Failed to switch to mediacodec-copy: ${e.message}")
                }
            }
            DecoderStage.HW_PLUS -> {
                metricsTracker.recordDecoderFallback()
                Log.w(TAG, "HW+ mediacodec-copy failed for $codec ($reason). Stepping down to SW (hwdec=no)...")
                currentDecoderStage = DecoderStage.SW
                try {
                    port.setPropertyString("hwdec", "no")
                    reloadForDecoderFallback()
                } catch (e: Exception) {
                    attemptDecoderFallback("Failed to switch to SW: ${e.message}")
                }
            }
            DecoderStage.SW -> {
                metricsTracker.recordHardError()
                Log.e(TAG, "SW decoding failed for codec $codec ($reason). Escalating to hard engine fallback.")
                val exception = MpvPlaybackException(
                    message = "MPV playback failed for codec '$codec' after exhausting HW, HW+, and SW decoders: $reason",
                    codecName = codec,
                )
                mainHandler.post {
                    engineListener?.onError(exception)
                }
            }
        }
    }


    /**
     * Issue #212: reload the current item at its position after a decoder
     * step-down, exactly as setDecoderMode does — a live hwdec change only
     * affects the NEXT decoder init, so without this the failed decoder
     * keeps decoding the current stream. No-op when nothing is loaded.
     * The stepped-down stage is carried through load() via
     * fallbackHwdecStage (load() would otherwise re-apply the user's mode
     * and undo the step-down); decoderMode itself is untouched.
     * Playback resumes only if it was playing — a paused file stays
     * paused (unlike setDecoderMode, which always resumes; see #224).
     * Loop guard: the stage is monotonic within an item (only
     * attemptDecoderFallback advances it; load() preserves the override
     * stage instead of resetting it), so one item steps down at most
     * twice (HW -> HW+ -> SW) and then hits the SW hard-error branch.
     * A reload failure is NOT a decoder-stage failure (load() says the
     * same about loadfile errors), so it never steps down further — the
     * stage stays stepped down and the in-flight flag is cleared.
     */
    private fun reloadForDecoderFallback() {
        val queue = getCurrentQueue()
        val index = getCurrentMediaIndex()
        if (!mpvInitialized || isReleased || !fileLoadedForCurrentItem || queue.isEmpty() || index !in queue.indices) return
        val wasPlaying = cachedIsPlaying
        val pos = cachedPositionMs
        fallbackHwdecStage = currentDecoderStage
        fallbackReloadPending = true
        try {
            load(queue, index, pos)
            if (wasPlaying) play()
        } catch (e: Exception) {
            Log.w(TAG, "Decoder fallback reload failed", e)
            fallbackReloadPending = false
        }
    }


    private fun handlePropertyUpdate(property: String) {
        synchronized(eventLock) {
            if (isReleased) return

            when (property) {
            "time-pos" -> {
                val sec = port.getPropertyDouble("time-pos") ?: 0.0
                cachedPositionMs = (sec * 1000).toLong().coerceAtLeast(0L)
                // Issue #181: vo can go null MID-playback (surface destroy
                // after a resize) with no later attach/load to restore it —
                // the file keeps playing with sound but no video, and the
                // only restores so far (attachSurface, load) never run.
                // time-pos keeps updating while that happens, so restore
                // vo=gpu here whenever a surface is attached and vo is
                // known null. One-shot (voIsNull flips false on restore),
                // and safe for the same reason as the #161 fix: restoring
                // a nulled vo cannot tear down a live VO, because there
                // is none.
                if (voIsNull) {
                    synchronized(surfaceLock) {
                        if (currentSurface != null && voIsNull) {
                            try {
                                port.setPropertyString("vo", "gpu")
                                voIsNull = false
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed restoring vo=gpu on time-pos", e)
                            }
                        }
                    }
                }
                // Seek latency: first position update landing within 500 ms of
                // the seek target after a direct seek command.
                metricsTracker.recordPositionUpdate(cachedPositionMs)
            }
            "vo" -> {
                // Issue #210: mpv itself can null the VO — a reconfigure
                // failure after the android-surface-size set, while paused
                // — and nothing saw it: the #193 restore is gated on the
                // voIsNull flag, which only detachSurface() sets, and a
                // pure resize is a surfaceChanged, not a surfaceDestroyed,
                // so the flag stayed false and the restore never fired.
                // Observing "vo" makes mpv's own nulling visible the
                // moment it happens, in any state (time-pos does not
                // advance while paused, so the #181 restore is blind
                // there too). The value is read back under surfaceLock —
                // not trusted from the event — so a stale "null" event
                // processed after an attach already restored "gpu" does
                // nothing, and the read-decide-set is atomic against the
                // attach/detach/load restores. "vo" changes rarely
                // (gpu -> null -> gpu), so this adds no event spam.
                // When mpv reports a live VO again, the flag is cleared
                // so the flag-gated restores don't fire a gratuitous
                // vo set later; when it reports null with no surface
                // attached, the flag is set so the next attach/load
                // restore fires (black is correct there until then).
                synchronized(surfaceLock) {
                    val vo = try {
                        port.getPropertyString("vo")
                    } catch (_: Exception) {
                        null
                    }
                    if (vo == "null") {
                        voIsNull = true
                        if (currentSurface != null) {
                            try {
                                port.setPropertyString("vo", "gpu")
                                voIsNull = false
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed restoring vo=gpu on vo change", e)
                            }
                        }
                    } else if (vo != null) {
                        voIsNull = false
                    }
                }
            }
            "duration" -> {
                val sec = port.getPropertyDouble("duration") ?: 0.0
                cachedDurationMs = (sec * 1000).toLong().coerceAtLeast(0L)
            }
            "demuxer-cache-time" -> {
                val sec = port.getPropertyDouble("demuxer-cache-time") ?: 0.0
                cachedBufferedMs = (sec * 1000).toLong().coerceAtLeast(0L)
            }
            "pause", "idle-active" -> {
                val isPaused = port.getPropertyBoolean("pause") ?: false
                val isIdle = port.getPropertyBoolean("idle-active") ?: false
                val playing = !isPaused && !isIdle
                if (cachedIsPlaying != playing) {
                    cachedIsPlaying = playing
                    mainHandler.post {
                        engineListener?.onIsPlayingChanged(playing)
                    }
                }
            }
            "speed" -> {
                val speed = port.getPropertyDouble("speed")?.toFloat() ?: 1.0f
                if (cachedSpeed != speed) {
                    cachedSpeed = speed
                    mainHandler.post {
                        engineListener?.onPlaybackParametersChanged(speed)
                    }
                }
            }
            "dwidth", "dheight" -> {
                // Issue #260: the display size becomes known only once
                // mpv has the video track — at load, or at first frame —
                // long after the PlayerScreen effect armed the PiP params
                // with the 16:9 fallback. The values are read back here
                // (not trusted from the event) and notified only on a
                // real change, so the dimensions become observed state
                // and the params refresh the moment they are known.
                val w = videoWidth
                val h = videoHeight
                if (w != cachedVideoWidth || h != cachedVideoHeight) {
                    cachedVideoWidth = w
                    cachedVideoHeight = h
                    mainHandler.post {
                        engineListener?.onVideoSizeChanged(w, h)
                    }
                }
            }
            "hwdec-current", "video-codec" -> {
                notifyDecoderChanged()
            }
            "track-list" -> {
                // Phase 4 step 3: the remembered-external-pick re-apply
                // and the #332 confirmation live in MpvSubtitleRenderer
                // — see Host.onTrackListChanged.
                host.onTrackListChanged()
            }
            "eof-reached" -> {
                val eof = port.getPropertyBoolean("eof-reached") ?: false
                if (eof) {
                    cachedPlaybackState = PlayerEngine.STATE_ENDED
                    mainHandler.post {
                        engineListener?.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)
                    }
                } else if (cachedPlaybackState == PlayerEngine.STATE_ENDED &&
                    fileLoadedForCurrentItem
                ) {
                    // Issue #271: mpv clears eof-reached on any seek away
                    // from EOF, and an in-place restart (Repeat ONE,
                    // Play-after-EOF, Previous-at-start, or a scrub while
                    // paused) seeks instead of reloading — so no
                    // FILE_LOADED ever arrives to reset the state, and
                    // play()/togglePlayPause() kept seeing the stale ENDED
                    // and seeking to 0 again (pausing at 1:00 after a
                    // Repeat ONE restart, then pressing Play, jumped back
                    // to 0:00). Reset to READY here: the seek proves
                    // playback genuinely left the ended state.
                    // fileLoadedForCurrentItem guards the fresh-load case:
                    // load() clears it synchronously, so an eof-clear
                    // caused by unloading the old file during a queue
                    // advance (Repeat ALL / next) cannot post an early
                    // READY before FILE_LOADED — that would cancel the
                    // preparing watchdog and clear isPreparing while the
                    // next file still loads.
                    cachedPlaybackState = PlayerEngine.STATE_READY
                    mainHandler.post {
                        engineListener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    }
                }
            }
        }
    }
    }


    private fun notifyDecoderChanged() {
        val hwdec = port.getPropertyString("hwdec-current") ?: ""
        val codec = port.getPropertyString("video-codec") ?: ""
        activeHwdec = hwdec
        activeVideoCodec = codec

        val decoderName = buildActiveDecoderName(hwdec, codec)
        mainHandler.post {
            engineListener?.onVideoDecoderInitialized(decoderName)
        }
    }


    private fun buildActiveDecoderName(hwdec: String = activeHwdec, codec: String = activeVideoCodec): String {
        // Issue #188: use the SHORT codec label, not mpv's video-codec
        // long name ("H.265 / HEVC (High Efficiency Video Coding)" has
        // parens of its own, which nested inside the fallback note's
        // parens). When the codec is not known yet (video-codec blank at
        // notify time), omit the label instead of printing "(unknown)" —
        // a later notifyDecoderChanged re-reads it once the track info
        // arrives.
        val label = MpxMediaCodecSelector.shortCodecLabel(codec)
        val decoder = when {
            hwdec == "mediacodec" -> "mediacodec"
            hwdec == "mediacodec-copy" -> "mediacodec-copy"
            hwdec == "no" || hwdec.isBlank() -> "software"
            else -> hwdec
        }
        return if (label != null) "$decoder ($label)" else decoder
    }


    private fun handleMpvEvent(eventId: Int, endFile: MpvPort.EndFile?) {
        synchronized(eventLock) {
            if (isReleased) return

            when (eventId) {
            MpvPort.MPV_EVENT_START_FILE -> {
                // Demuxer opened the file: the load got past issue into
                // libmpv. If the stage sticks here, the stall is inside
                // demux/decode/VO init, not in load issue or event delivery.
                lastMpvEvent = "START_FILE"
            }
            MpvPort.MPV_EVENT_SHUTDOWN -> {
                // Previously IGNORED: a core shutdown produced no event the
                // app acted on, so a dead core looked exactly like infinite
                // loading. Surface it as a real error so the manager clears
                // the splash (release() sets isReleased first, so normal
                // teardown never reaches here).
                lastMpvEvent = "SHUTDOWN"
                metricsTracker.recordHardError()
                mainHandler.post {
                    engineListener?.onError(
                        MpvPlaybackException("MPV player shut down unexpectedly${rejectedOptionsText()}")
                    )
                }
            }
            MpvPort.MPV_EVENT_FILE_LOADED -> {
                // Issue #258 / D-7: no active load means this FILE_LOADED
                // belongs to a load the user deliberately stopped —
                // stop() clears activeMediaUri, and a FILE_LOADED
                // generated before the stop command was processed can
                // still be delivered after it. Dropping it keeps the
                // stop from re-marking the file loaded and posting READY
                // for a session that no longer exists.
                val gen = currentLoadGeneration
                if (gen == 0L || gen != loadGeneration || activeMediaUri.isNullOrBlank()) {
                    Log.w(TAG, "Ignoring FILE_LOADED with no active load or superseded generation (gen=$gen, current=$loadGeneration)")
                    return
                }
                // Drop stale FILE_LOADED event if the file changed before FILE_LOADED arrived
                val loadedPath = try { port.getPropertyString("path") } catch (_: Exception) { null }
                val isStale = if (!loadedPath.isNullOrBlank() && !activeMediaUri.isNullOrBlank()) {
                    !isPathMatch(loadedPath, activeMediaUri)
                } else {
                    false
                }
                if (isStale) {
                    Log.w(TAG, "Ignoring stale FILE_LOADED for $loadedPath; currently loading $activeMediaUri")
                    return
                }
                if (gen != loadGeneration) {
                    Log.w(TAG, "Ignoring FILE_LOADED superseded during check (gen=$gen, current=$loadGeneration)")
                    return
                }

                fileLoadedForCurrentItem = true
                loadIssued = false
                // Issue #212: the fallback reload landed — duplicate
                // triggers for the old failure are behind us; a fresh
                // failure may fall back again.
                fallbackReloadPending = false
                lastMpvEvent = "FILE_LOADED"
                cachedPlaybackState = PlayerEngine.STATE_READY
                metricsTracker.recordReady()
                // Apply a seek that arrived before the file loaded (resume or
                // scrub during prepare) instead of dropping it.
                pendingSeekMs?.let { target ->
                    pendingSeekMs = null
                    try {
                        val dur = cachedDurationMs
                        val clamped = if (dur > 0L) target.coerceIn(0L, dur) else target.coerceAtLeast(0L)
                        port.command("seek", (clamped / 1000.0).toString(), "absolute+exact")
                        cachedPositionMs = clamped
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed applying pending seek $target", e)
                    }
                }
                // Phase 4 step 3: the pending-subtitle dispatch and
                // the embedded auto-select live in MpvSubtitleRenderer
                // — see Host.onFileLoaded.
                host.onFileLoaded()
                mainHandler.post {
                    engineListener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    engineListener?.onTracksChanged()
                    notifyDecoderChanged()
                }
            }
            MpvPort.MPV_EVENT_VIDEO_RECONFIG -> {
                // First video reconfig after a load is the first presented video
                // frame — the honest "first frame" signal on the mpv path.
                lastMpvEvent = "VIDEO_RECONFIG"
                metricsTracker.recordFirstFrame()
                notifyDecoderChanged()
            }
            MpvPort.MPV_EVENT_END_FILE -> {
                lastMpvEvent = "END_FILE"
                loadIssued = false
                // A file that never loaded is a STARTUP failure, not a decoder
                // failure: previously this fell through every check (duration
                // is 0, eof flag unset) and playback died silently. Report it
                // with libmpv's own reason + last error log line when known.
                // Issue #187: only a REAL failure counts. A loadfile replace
                // ends the previous file with reason=stop before the new
                // file's FILE_LOADED arrives, and fileLoadedForCurrentItem
                // was just reset for the new load — reporting that stop as
                // 'could not play' showed a spurious error banner while the
                // new file then loaded and played fine. Benign reasons
                // (stop/quit/eof/redirect) are not startup failures.
                if (!fileLoadedForCurrentItem) {
                    val reason = endFile?.reason
                    val errCode = endFile?.error
                    // Issue D-9: reason=stop is benign regardless of errCode (e.g. stop interrupted loadfile).
                    val isBenignStop = reason == "stop" || (reason in setOf("quit", "eof", "redirect") && (errCode == null || errCode == 0L))
                    val isRealFailure = !isBenignStop
                    if (!isRealFailure) return
                    fallbackReloadPending = false
                    val lastLog = synchronized(lastErrorLogs) { lastErrorLogs.lastOrNull() }
                    val detail = listOfNotNull(
                        reason?.let { "reason=$it" },
                        errCode?.let { "error=$it" },
                        lastLog,
                    ).joinToString("; ").ifBlank { "unknown libmpv error" } + rejectedOptionsText()
                    metricsTracker.recordHardError()
                    mainHandler.post {
                        engineListener?.onError(
                            MpvPlaybackException("MPV could not play this file ($detail)")
                        )
                    }
                    return
                }
                // Check if end of file was an error
                val eofReached = port.getPropertyBoolean("eof-reached") ?: false
                if (!eofReached && cachedPositionMs < cachedDurationMs - 1000L && cachedDurationMs > 0L) {
                    // Premature end-file implies decoder or playback error
                    mainHandler.post {
                        attemptDecoderFallback("Premature END_FILE event received from libmpv")
                    }
                }
            }
        }
    }
    }


    internal fun setListener(listener: PlayerEngine.Listener?) {
        this.engineListener = listener
        // Flush a captured init failure as soon as anyone is listening.
        pendingInitError?.let { err ->
            pendingInitError = null
            mainHandler.post { engineListener?.onError(err) }
        }
    }


    internal fun attachSurface(surface: Surface) {
        if (isReleased) return
        // Reference-pattern alignment (mpv-android's own BaseMPVView.surfaceCreated):
        // when a file load is about to run, it attaches the surface, sets
        // force-window=yes, and issues loadfile WITHOUT touching the "vo"
        // property — the load itself initialises VO against the attached
        // surface. It only (re)sets "vo" when no file is pending. MPx
        // previously set "vo" unconditionally on every attach, forcing a VO
        // teardown/recreation immediately before the loadfile — a needless
        // race on the exact path that was hanging with no error. Match the
        // reference: skip the vo set whenever a load is about to run.
        val queue = getCurrentQueue()
        val willLoad = pendingLoad != null ||
            (mpvInitialized && !isReleased && !fileLoadedForCurrentItem && queue.isNotEmpty())
        synchronized(surfaceLock) {
            currentSurface = surface
            try {
                port.attachSurface(surface)
                setRuntimeProperty("force-window", "yes")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface", e)
            }
            // Issue #181: restore vo=gpu in its own try/catch, so a throwing
            // native attach cannot skip it. A detach mid-session left vo=null,
            // and port.attachSurface() binds to the CURRENT vo — with vo=null
            // there is no VO, so the next loadfile decodes but renders to the
            // null VO (sound, no video, no error: the phone log shows
            // "VO: [null]" on every load after the first detach). Restore
            // vo=gpu even when a load is about to run. The Nov 4 race fix
            // only forbids a GRATUITOUS vo set when vo is already gpu;
            // restoring a nulled vo cannot tear down a live VO, because
            // there is none.
            if (voIsNull) {
                try {
                    port.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu on attach", e)
                }
            } else if (willLoad) {
                // A load is about to run: loadfile itself initialises
                // VO against the attached surface; do not touch "vo".
            } else {
                try {
                    port.setPropertyString("vo", "gpu")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed setting vo=gpu on attach", e)
                }
            }
        }
        // Flush a load deferred for lack of surface (see load()). This is the
        // only path by which a video loadfile now runs: with a real surface
        // attached, so VO init cannot fail for want of an ANativeWindow.
        // Returns early so the legacy reload below cannot double-load.
        val pending = pendingLoad
        if (pending != null) {
            pendingLoad = null
            // Issue #273: a scrub parked by seekTo() while this load was
            // deferred belongs to this exact load — load() clears
            // pendingSeekMs at its top, so pass the parked position as
            // the resume position here, or the flush would reload at the
            // original resumeMs and the scrub would silently vanish
            // (dragging the seek bar during the loading splash did
            // nothing). pendingSeekMs is null unless parked after this
            // load was registered — a new load() clears it — so it can
            // never be a stale seek for a different item.
            load(pending.first, pending.second, pendingSeekMs ?: pending.third)
            // Issue #246: only resume if play was actually requested.
            // A pause during prepare (or a paused restore, which never
            // calls play()) must leave the file loaded but paused — the
            // old unconditional play() overrode the pause on every
            // surface recreate. cachedIsPlaying cannot gate this: it is
            // false while idle with no file loaded, even after play().
            if (playRequested) play()
            return
        }
        // Legacy load-before-surface recovery (kept as a safety net for loads
        // that ran surfaceless before this fix, e.g. engine switches): if the
        // current file never produced FILE_LOADED, retry it now that a
        // surface exists. No-op when the file already loaded (rotation, etc.)
        // or when a load is already in flight (Issue D-6).
        val index = getCurrentMediaIndex()
        if (mpvInitialized && !isReleased && !loadIssued && !fileLoadedForCurrentItem && queue.isNotEmpty() && index in queue.indices) {
            load(queue, index, cachedPositionMs)
            // Issue #246: same gate as the deferred flush above.
            if (playRequested) play()
        }
    }


    /**
     * Surface size change (SurfaceHolder.Callback.surfaceChanged). mpv-android's
     * own BaseMPVView reports this as the "android-surface-size" property on
     * every change; MPx previously left surfaceChanged empty, so the android
     * gpu-context never learned the real surface size.
     */
    internal fun onSurfaceChanged(width: Int, height: Int) {
        if (!mpvInitialized || isReleased || width <= 0 || height <= 0) return
        try {
            port.setPropertyString("android-surface-size", "${width}x${height}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed reporting android-surface-size", e)
        }
        // Issue #193: restore vo=gpu here too, not only on attach/time-pos.
        // A surface destroy around a resize (chrome appearing while paused:
        // 1080x2356 -> 1080x1934, then vo=null a second later) can leave vo
        // null with no later attach and no time-pos updates — time-pos does
        // not advance while paused, so the #181 restore never fires. The
        // framework always calls surfaceChanged after surfaceCreated, so
        // this is the last restore chance on the re-attach path, and it
        // fires in the paused state. Same guard as the #181 restore: only
        // when a surface is attached and vo is known null; restoring a
        // nulled vo cannot tear down a live VO, because there is none.
        synchronized(surfaceLock) {
            if (currentSurface != null && voIsNull) {
                try {
                    port.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu on surfaceChanged", e)
                }
            }
        }
    }


    /**
     * SYNCHRONOUS detach barrier:
     * Set vo=null and detach the surface before returning so that libmpv terminates its
     * native rendering window handle before surfaceDestroyed returns.
     * Background audio continues playing with vo=null.
     *
     * What destroys the surface (issue #161): the ONLY caller of this barrier
     * besides release() is PlayerScreen's SurfaceHolder.Callback.surfaceDestroyed
     * (via PlaybackCoordinator.detachSurface()); the AndroidView has no key() and no
     * conditional, so the SurfaceView stays in composition while the player is
     * open. surfaceDestroyed is a framework callback — the surface is torn down
     * when the view leaves the window / the window is hidden — and the phone log
     * shows it firing between loads mid-session (vo=null, then an immediate
     * re-attach + reload with VO still null). The exact framework trigger on
     * this device at EOF/seek/replay is not visible in code; what matters is
     * that a detach can happen at any point mid-session, so attachSurface()
     * must restore vo=gpu whenever vo is null instead of assuming vo survived.
     */
    internal fun detachSurface() {
        synchronized(surfaceLock) {
            currentSurface = null
            // Issue #193: record vo as null BEFORE the native calls. The
            // flag was set last, so if mpv.detachSurface() threw, vo was
            // already null while voIsNull stayed false — and the attach
            // (willLoad branch) and load() restores both trust the flag and
            // skip, leaving a later load to decode into the null VO.
            // Setting the flag first makes every restore site see the truth.
            voIsNull = true
            try {
                port.setPropertyString("vo", "null")
                port.setPropertyString("force-window", "no")
                port.detachSurface()
            } catch (e: Exception) {
                Log.w(TAG, "Error in detachSurface barrier", e)
            }
        }
    }


    internal fun load(media: PlayableMedia, resumeMs: Long) {
        if (isReleased) return
        load(listOf(media), 0, resumeMs)
    }


    internal fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
        if (isReleased) return
        if (items.isEmpty()) {
            // Issue #212: an empty queue is a fresh context — no current
            // item, so no fallback override or in-flight reload survives.
            fallbackHwdecStage = null
            fallbackReloadPending = false
            pendingLoad = null
            return
        }
        if (pendingLoad != null) {
            // Issue D-5: a fresh load overwriting an existing pendingLoad
            // drops any stale fallback override so it cannot leak to the new item.
            fallbackHwdecStage = null
            fallbackReloadPending = false
            pendingLoad = null
        }
        // Issue #221: drop the previous file's cached position/duration
        // NOW, before anything else reads them. They only update when the
        // new file's time-pos/duration property events arrive, so without
        // this the seek bar briefly shows the old file's time/duration,
        // a seek near a track change clamps to the OLD duration (seekTo
        // and the FILE_LOADED pending-seek clamp both trust
        // cachedDurationMs), and the END_FILE premature-failure check can
        // misread a stale duration. Placed here — before the
        // surface-deferral return below — so a deferred load also starts
        // clean, and before the fallback-stage block, which does not read
        // the caches. reloadForDecoderFallback reads cachedPositionMs
        // BEFORE calling load(), so this reset cannot eat its position.
        cachedPositionMs = resumeMs
        cachedDurationMs = 0L
        cachedBufferedMs = 0L
        cachedPlaybackState = PlayerEngine.STATE_IDLE
        fileLoadedForCurrentItem = false
        // Issue #199: a pending pin belongs to the item it was picked
        // for — a new load must never let it pin a track on this item.
        host.onLoadStarted()
        pendingSeekMs = null
        loadIssued = false
        lastMpvEvent = "none"
        val gen = ++loadGeneration
        currentLoadGeneration = gen
        // Tap time for tap-to-first-frame: load() is called at tap time
        // even when the actual loadfile is deferred for lack of surface.
        metricsTracker.recordLoadStarted()

        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        val targetItem = items[targetIndex]

        // ROOT-CAUSE FIX (mpv "never works at all"): playback is normally
        // started from the library BEFORE the player screen (and its
        // SurfaceView) exists. The old code fired loadfile immediately, and
        // with force-window=yes libmpv attempted VO init with no ANativeWindow
        // attached: the video chain failed, the file aborted with END_FILE,
        // and PlaybackCoordinator reported a failure before surfaceCreated ever
        // ran — so mpv never got a fair load. Now a VIDEO load with no surface
        // is deferred, not fired: attachSurface() flushes it the moment a
        // real surface exists. Audio loads are never deferred (no video
        // output is needed, so a missing surface cannot fail them).
        val hasSurface = synchronized(surfaceLock) { currentSurface != null }
        if (targetItem.isVideo && !hasSurface) {
            pendingLoad = Triple(items, targetIndex, resumeMs)
            mainHandler.post {
                // Issue #283: no isAuto flag (it was always false).
                engineListener?.onMediaItemTransition(targetIndex)
            }
            return
        }

        // Issue #212: a fallback reload carries the stepped-down stage
        // through; a fresh load re-applies the user's mode and clears
        // any in-flight fallback (a new item is a new failure context).
        // The override is consumed only here, after the surface-deferral
        // check above, so a deferred fallback reload still carries it
        // when attachSurface() flushes it.
        val fallbackStage = fallbackHwdecStage
        fallbackHwdecStage = null
        if (fallbackStage != null) {
            // Keep the stepped-down decoder; decoderMode (the user's
            // setting) is untouched.
            currentDecoderStage = fallbackStage
            val hwdecVal = host.hwdecValueForStage(fallbackStage)
            try {
                port.setPropertyString("hwdec", hwdecVal)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set fallback hwdec to $hwdecVal", e)
            }
        } else {
            host.applyHwdecForMode(decoderMode)
            fallbackReloadPending = false
        }

        // Issue #213/#219: resolve ONLY the target item here, from its
        // original URI, at load time — never the whole queue. The stored
        // queue (currentQueue) keeps the original content:// URIs, so
        // every re-load of this item (Next/Previous, seekTo(index),
        // decoder-mode reload, attach reload, auto-advance) opens a fresh
        // fd instead of reusing the dead one mpv closed when the stream
        // ended. Resolving here, after the surface check above, also means
        // a deferred load opens no fd at all until the surface exists.
        // activeMediaUri is the RESOLVED uri — the one loadfile is handed
        // — so the FILE_LOADED staleness checks keep matching.
        val resolvedTarget = fdOpener.resolveForEngine(targetItem)

        activeMediaId = resolvedTarget.id
        activeMediaUri = resolvedTarget.uri

        // Issue #372: the remembered external pick belongs to the item it
        // was picked for — a load of a different item must never let it
        // re-apply a track on this item. A same-item reload (decoder
        // fallback, Next/Previous back to it) keeps it: FILE_LOADED
        // re-dispatches the item's subtitles and re-arms it anyway.
        host.onLoadTargetResolved(resolvedTarget.id)

        // Issue #102: Queue pending external subtitles to dispatch only on MPV_EVENT_FILE_LOADED
        val subs = mutableListOf<PendingSubtitle>()
        resolvedTarget.subtitleUri?.let { subUri ->
            subs.add(PendingSubtitle(uri = subUri, flag = "select"))
        }
        resolvedTarget.externalSubtitles.forEach { ext ->
            // Issue #282: "select", not "auto" — like the legacy
            // subtitleUri above and both replaceMediaItem branches.
            // MPx never sets mpv's 'slang', so "auto" never selects the
            // track and the #189 auto-select else-branch in FILE_LOADED
            // is skipped whenever pendingSubtitles != null; the track
            // was loaded but never shown (notably on a restored queue
            // after process death).
            subs.add(PendingSubtitle(uri = ext.uri, flag = "select", title = ext.name))
        }
        // Issue #332: the previous pending subtitles were never dispatched
        // (their file never reached FILE_LOADED) — their descriptors are
        // still ours, so close them before overwriting, or they leak one
        // fd per abandoned subtitle until the process dies.
        pendingSubtitles?.let { fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
        pendingSubtitles = if (subs.isNotEmpty()) {
            PendingSubtitles(mediaUri = resolvedTarget.uri, mediaId = resolvedTarget.id, subtitles = subs)
        } else {
            null
        }

        // Issue #161 belt and braces: attachSurface() restores vo=gpu when
        // vo is null, but if a load ever runs while vo is still null (a
        // detach/attach path that skipped the restore), loadfile would decode
        // into the null VO — sound, no video, no error. A surface is attached
        // here (video loads without one were deferred above), so restoring
        // vo=gpu before loadfile is safe. Audio-only loads take this branch
        // too; the set is a no-op for them when vo is already gpu.
        synchronized(surfaceLock) {
            if (currentSurface != null && voIsNull) {
                try {
                    port.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu before loadfile", e)
                }
            }
        }

        try {
            port.command("playlist-clear")

            // Resume via libmpv's own start option, NOT via a post-load seek:
            // seeking on FILE_LOADED plays a 0:00 audio/video blip first.
            // mpv 0.38+ loadfile signature is url, flags, index, options —
            // the index slot MUST be an integer, which is exactly why the old
            // "start=..." in that slot was rejected ("must be an integer").
            // "-1" is the default index and carries the options argument.
            // pendingSeekMs stays reserved for user scrubs during prepare.
            if (resumeMs > 0L) {
                val startSec = (resumeMs / 1000.0).toString()
                cachedPositionMs = resumeMs
                port.command("loadfile", resolvedTarget.uri, "replace", "-1", "start=$startSec")
            } else {
                port.command("loadfile", resolvedTarget.uri, "replace")
            }

            // Issue #103: PlaybackCoordinator alone owns queue transitions. mpv holds
            // only the current active item; internal appends were redundant and
            // scrambled playlist order.

            // Note: mpv command() does NOT throw on a rejected command — a
            // bad loadfile fails silently in the log only (this hid the
            // "start=" rejection for a whole round). loadIssued therefore
            // means "command sent", and the preparing watchdog is the backstop
            // if no START_FILE/FILE_LOADED event follows.
            loadIssued = true
        } catch (e: Exception) {
            // A loadfile command failure is NOT a decoder-stage failure: stepping
            // HW->HW+->SW here only delayed the error. Report it directly so the
            // manager reports a real error message.
            Log.e(TAG, "Error loading media items in MPV", e)
            lastMpvEvent = "loadfile-failed"
            loadIssued = false
            metricsTracker.recordHardError()
            mainHandler.post {
                engineListener?.onError(
                    MpvPlaybackException("MPV could not load this file: ${e.message}${rejectedOptionsText()}")
                )
            }
        }

        mainHandler.post {
            // Issue #283: no isAuto flag (it was always false).
            engineListener?.onMediaItemTransition(targetIndex)
        }
    }


    internal fun play() {
        if (isReleased) return
        try {
            port.setPropertyBoolean("pause", false)
            // Issue #246: record the intent — attachSurface() consults
            // this when it flushes a not-yet-loaded file (cachedIsPlaying
            // is false while idle, so it cannot carry the intent).
            playRequested = true
            // Deliberately NOT forcing cachedIsPlaying=true here: that lie is
            // exactly the "pause button showing over a black 00:00 screen"
            // symptom — the real playing state arrives via the pause/idle
            // property observers once libmpv actually starts.
        } catch (e: Exception) {
            Log.e(TAG, "Error playing MPV", e)
        }
    }


    internal fun pause() {
        if (isReleased) return
        try {
            port.setPropertyBoolean("pause", true)
            cachedIsPlaying = false
            // Issue #246: clear the play intent, so a later surface
            // re-attach does not resume a file the user paused during
            // prepare.
            playRequested = false
            mainHandler.post { engineListener?.onIsPlayingChanged(false) }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing MPV", e)
        }
    }


    internal fun seekTo(positionMs: Long) {
        if (isReleased) return
        val dur = duration
        val target = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        // A seek before FILE_LOADED (resume fired on transition, scrub during
        // prepare, or a seek while the load is still deferred for lack of
        // surface) must not vanish: park it and apply it on FILE_LOADED.
        val queue = getCurrentQueue()
        if (!fileLoadedForCurrentItem && queue.isNotEmpty()) {
            pendingSeekMs = target
            cachedPositionMs = target
            return
        }
        metricsTracker.recordSeekStarted(target)
        try {
            port.command("seek", (target / 1000.0).toString(), "absolute+exact")
            cachedPositionMs = target
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking in MPV", e)
        }
    }


    internal fun seekTo(index: Int, positionMs: Long) {
        if (isReleased) return
        val queue = getCurrentQueue()
        if (index in queue.indices) {
            load(queue, index, positionMs)
        }
    }


    internal fun setSpeed(speed: Float) {
        if (isReleased) return
        try {
            port.setPropertyDouble("speed", speed.toDouble())
            cachedSpeed = speed
            mainHandler.post { engineListener?.onPlaybackParametersChanged(speed) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed in MPV", e)
        }
    }


    internal fun stop() {
        if (isReleased) return
        pendingLoad = null
        pendingSeekMs = null
        activeMediaId = null
        activeMediaUri = null
        loadIssued = false
        lastMpvEvent = "none"
        loadGeneration++
        currentLoadGeneration = 0L
        // Issue #258: a deliberate stop ends the loaded file AND the
        // queue — the stop itself generates an END_FILE (reason=stop,
        // eof-reached false), and with fileLoadedForCurrentItem still
        // set that falls into the premature-EOF check and fires a
        // spurious decoder fallback that reloads the stopped item at
        // the stop position. Clearing the flag here routes the
        // stop-generated END_FILE to the startup-failure branch, where
        // reason=stop is benign. Clearing the queue too: the fallback
        // reload and attachSurface()'s legacy reload both key on a
        // non-empty currentQueue, so clearing the flag alone would let
        // the next surface attach resurrect the stopped item. The
        // cached position/duration/buffer go too (same reset load()
        // does — issue #221), so the stopped item's time cannot leak
        // into a later state read, and the in-flight fallback override
        // is dropped so it cannot step a later load down a stage.
        fileLoadedForCurrentItem = false
        loadIssued = false
        cachedPositionMs = 0L
        cachedDurationMs = 0L
        cachedBufferedMs = 0L
        fallbackReloadPending = false
        fallbackHwdecStage = null
        try {
            port.command("stop")
            cachedIsPlaying = false
            // Issue #246: a deliberate stop ends the play intent too, so
            // a stale intent cannot auto-play a later paused restore if
            // a surface re-attaches while the old queue is still current
            // and the file never loaded.
            playRequested = false
            cachedPlaybackState = PlayerEngine.STATE_IDLE
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MPV", e)
        }
    }


    /**
     * Releases native mpv resources.
     *
     * **Test-only lifecycle method (D-11).**
     * In production, the engine is intentionally process-lifetime.
     */
    @VisibleForTesting
    internal fun release() {
        if (isReleased) return
        lifecycleManager.release {
            pendingLoad = null
            pendingSeekMs = null
            // Issue #246: the engine is dead; no stale play intent survives it.
            playRequested = false
            activeMediaId = null
            activeMediaUri = null
            fileLoadedForCurrentItem = false
            loadIssued = false
            loadGeneration++
            currentLoadGeneration = 0L
            mainHandler.removeCallbacksAndMessages(null)
            detachSurface()
        }
    }


    internal fun seekToNextMediaItem() {
        if (isReleased) return
        if (hasNextMediaItem) {
            // Issue #305: resume only if it was playing — a paused
            // track stays paused (mirrors setDecoderMode /
            // reloadForDecoderFallback, and the previous-at-index-0
            // branch below, which seeks without playing). mpv's pause
            // property survives loadfile, so load() alone leaves the
            // next item paused; the unconditional play() was the only
            // thing resuming it. Skipping play() also leaves
            // playRequested false (pause() cleared it), so a
            // surface-deferred load stays paused when attachSurface()
            // flushes it.
            val wasPlaying = cachedIsPlaying
            val queue = getCurrentQueue()
            val next = getCurrentMediaIndex() + 1
            load(queue, next, 0L)
            if (wasPlaying) play()
        }
    }


    internal fun seekToPreviousMediaItem() {
        if (isReleased) return
        if (hasPreviousMediaItem) {
            // Issue #305: same paused-state preservation as
            // seekToNextMediaItem above.
            val wasPlaying = cachedIsPlaying
            val queue = getCurrentQueue()
            val prev = getCurrentMediaIndex() - 1
            load(queue, prev, 0L)
            if (wasPlaying) play()
        } else {
            seekTo(0L)
        }
    }


    internal val currentPosition: Long
        get() = cachedPositionMs


    /**
     * Issue #225: display size of the current video, read live from mpv's
     * `dwidth`/`dheight` (aspect-corrected display size, not raw coded
     * size) — the PiP aspect-ratio source of truth. 0 when mpv is not
     * initialized or no video is loaded; never a guessed size.
     */
    internal val videoWidth: Int
        get() = if (!mpvInitialized || isReleased) {
            0
        } else {
            try {
                port.getPropertyInt("dwidth")?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }


    internal val videoHeight: Int
        get() = if (!mpvInitialized || isReleased) {
            0
        } else {
            try {
                port.getPropertyInt("dheight")?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }


    internal val duration: Long
        get() = cachedDurationMs


    internal val bufferedPosition: Long
        get() = cachedBufferedMs


    internal val isPlaying: Boolean
        get() = cachedIsPlaying


    internal val playbackState: Int
        get() = cachedPlaybackState


    internal val currentMediaItemIndex: Int
        get() = getCurrentMediaIndex()


    internal val hasNextMediaItem: Boolean
        get() {
            val queue = getCurrentQueue()
            val index = getCurrentMediaIndex()
            return queue.isNotEmpty() && index in 0 until queue.lastIndex
        }


    internal val hasPreviousMediaItem: Boolean
        get() {
            val queue = getCurrentQueue()
            val index = getCurrentMediaIndex()
            return queue.isNotEmpty() && index > 0
        }


    internal fun getLoadStage(): MpvLoadStage {
        val surfaceAttached = synchronized(surfaceLock) { currentSurface != null }
        return MpvLoadStage(
            initialized = mpvInitialized && pendingInitError == null,
            surfaceAttached = surfaceAttached,
            loadIssued = loadIssued,
            lastEvent = lastMpvEvent,
        )
    }


    internal fun getPlaybackMetrics(): EnginePlaybackMetrics = metricsTracker.getPlaybackMetrics()
}
