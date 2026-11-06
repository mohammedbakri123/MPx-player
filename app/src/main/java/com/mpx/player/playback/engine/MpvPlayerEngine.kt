package com.mpx.player.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import android.util.Log
import android.view.Surface
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.ExternalSubtitle
import com.mpx.player.playback.MpvFdOpener
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.MpvLogFile
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.subtitleMpvOptions
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.io.FileOutputStream
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
 * Implements the contract defined in [PlayerEngine]:
 * - Synchronous surface detach barrier (vo=null and detachSurface before surfaceDestroyed returns)
 * - Background audio continuation when vo=null
 * - Progressive decoder fallback chain:
 *   mediacodec (HW) -> mediacodec-copy (HW+) -> hwdec=no (SW) -> hard failure reported as a real error
 * - Active decoder reporting via hwdec-current and video-codec properties
 * - Arabic-first subtitles pointing fontconfig at Android system fonts (/system/fonts) without bundled subfont.ttf
 */
class MpvPlayerEngine(
    private val context: Context,
    initialDecoderMode: DecoderMode = DecoderMode.HW,
) : PlayerEngine {

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val surfaceLock = Any()
    // Issue #213/#219: the engine resolves content:// URIs itself, lazily,
    // only for the item it is about to loadfile (see load()). The stored
    // queue always keeps the original URIs — an fd is single-use, so a
    // stored fd:// URI would be dead the second time it is loaded.
    private val fdOpener = MpvFdOpener(appContext)

    private var engineListener: PlayerEngine.Listener? = null
    private var currentQueue: List<PlayableMedia> = emptyList()
    private var currentMediaIndex: Int = 0

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
    private var voIsNull: Boolean = false
    private var isReleased: Boolean = false
    private var mpvInitialized: Boolean = false

    private enum class DecoderStage { HW, HW_PLUS, SW }
    private var currentDecoderStage: DecoderStage = DecoderStage.HW

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
    private var fallbackHwdecStage: DecoderStage? = null
    /**
     * Issue #212: a fallback reload is in flight (loadfile issued,
     * FILE_LOADED not yet arrived). Duplicate triggers for the same
     * failure — several mediacodec log lines, or a log line plus a
     * premature END_FILE — are ignored while this is set, so one
     * failure steps down once. Cleared on FILE_LOADED, or by a fresh
     * non-fallback load().
     */
    private var fallbackReloadPending: Boolean = false

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

    var decoderMode: DecoderMode = initialDecoderMode
        private set

    // Cached playback state
    private var cachedPositionMs: Long = 0L
    private var cachedDurationMs: Long = 0L
    private var cachedBufferedMs: Long = 0L
    private var cachedIsPlaying: Boolean = false
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
    private var playRequested: Boolean = false
    private var cachedPlaybackState: Int = PlayerEngine.STATE_IDLE
    private var cachedSpeed: Float = 1.0f
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
    private var fileLoadedForCurrentItem: Boolean = false
    /** Last error/warning log lines from libmpv (capped); surfaced when a file fails to load. */
    private val lastErrorLogs: ArrayDeque<String> = ArrayDeque()
    /** mpv options libmpv rejected (setOptionString returns non-zero). Previously every
     * setOptionString result was ignored, so a rejected vo/gpu-context/hwdec/ao option
     * was completely invisible — the phone banner now names them (see END_FILE detail). */
    private val rejectedOptions: ArrayDeque<String> = ArrayDeque()
    /** A video load deferred because no surface exists yet (see load()). Flushed by
     * attachSurface(); audio loads are never deferred (no video output needed). */
    @Volatile
    private var pendingLoad: Triple<List<PlayableMedia>, Int, Long>? = null
    /** A seek requested before the current file loaded (e.g. resume position fired
     * on item transition while the load was still deferred for lack of surface).
     * Applied the moment FILE_LOADED arrives, so a seek is never silently lost.
     * Written on Main, read/cleared on the event thread: volatile. */
    @Volatile
    private var pendingSeekMs: Long? = null

    private data class PendingSubtitle(
        val uri: String,
        val flag: String,
        val title: String? = null,
    )

    private data class PendingSubtitles(
        val mediaUri: String,
        val mediaId: String,
        val subtitles: List<PendingSubtitle>,
    )

    @Volatile
    private var activeMediaId: String? = null
    @Volatile
    private var activeMediaUri: String? = null
    /** External subtitles queued before FILE_LOADED (issue #102). Dispatched once libmpv
     * confirms the file is loaded and active, avoiding "no file loaded" sub-add rejections.
     * Written on Main, read/cleared on the event thread: volatile. */
    @Volatile
    private var pendingSubtitles: PendingSubtitles? = null
    /** Issue #199: an external subtitle was sub-added with "select" but the
     * one-shot #192 pin ran before mpv's async sub-add put the new track in
     * track-list, so it silently no-op'd — and if the file was already
     * loaded, FILE_LOADED never fired again to give it a second chance.
     * Kept set until a track-list refresh shows the just-added track
     * selected and sid is pinned to it (see attemptExternalPin). Only
     * ever pins a track that is currently selected AND external, so a
     * failed add (track never appears) can never freeze a wrong
     * selection, and a stale flag is harmless: it no-ops whenever no
     * external track is selected (e.g. the user picked an embedded
     * track instead). Armed on Main (replaceMediaItem) and on the event
     * thread (FILE_LOADED dispatch); read/cleared on the event thread:
     * volatile. Cleared on load/stop/release so a pick for one item can
     * never pin a track on another item. */
    @Volatile
    private var pendingExternalPin: Boolean = false

    private fun isPathMatch(path1: String?, path2: String?): Boolean {
        if (path1 == null || path2 == null) return false
        if (path1 == path2) return true
        val p1 = path1.removePrefix("file://")
        val p2 = path2.removePrefix("file://")
        return p1 == p2
    }

    // --- Step-level load diagnostics (shown on-phone in Settings → Development) ---
    @Volatile
    private var loadIssued: Boolean = false
    @Volatile
    private var lastMpvEvent: String = "none"

    override fun getLoadStage(): MpvLoadStage {
        val surfaceAttached = synchronized(surfaceLock) { currentSurface != null }
        return MpvLoadStage(
            initialized = mpvInitialized && pendingInitError == null,
            surfaceAttached = surfaceAttached,
            loadIssued = loadIssued,
            lastEvent = lastMpvEvent,
        )
    }
    private val metricsLock = Any()
    private var loadStartMs: Long = 0L
    private var firstFrameRecorded: Boolean = false
    private var lastTimeToReadyMs: Long? = null
    private var lastTimeToFirstFrameMs: Long? = null
    private var lastSeekLatencyMs: Long? = null
    private var seekStartMs: Long? = null
    private var seekTargetMs: Long? = null
    private var decoderFallbackCount: Int = 0
    private var hardErrorCount: Int = 0

    override fun getPlaybackMetrics(): EnginePlaybackMetrics =
        synchronized(metricsLock) {
            EnginePlaybackMetrics(
                lastTimeToReadyMs = lastTimeToReadyMs,
                lastTimeToFirstFrameMs = lastTimeToFirstFrameMs,
                lastSeekLatencyMs = lastSeekLatencyMs,
                decoderFallbackCount = decoderFallbackCount,
                hardErrorCount = hardErrorCount,
            )
        }

    private fun logMetrics(tag: String, detail: String) {
        Log.i("MpxMetrics", "$tag $detail")
    }
    /** Init failure captured instead of thrown, so the manager can report it instead of crashing. */
    private var pendingInitError: Exception? = null

    private val mpv: MPV = MPV()

    private val eventObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) {
            handlePropertyUpdate(property)
        }

        override fun eventProperty(property: String, value: Long) {
            handlePropertyUpdate(property)
        }

        override fun eventProperty(property: String, value: Boolean) {
            handlePropertyUpdate(property)
        }

        override fun eventProperty(property: String, value: String) {
            handlePropertyUpdate(property)
        }

        override fun eventProperty(property: String, value: Double) {
            handlePropertyUpdate(property)
        }

        override fun eventProperty(property: String, value: MPVNode) {
            handlePropertyUpdate(property)
        }

        override fun event(eventId: Int, data: MPVNode) {
            handleMpvEvent(eventId, data)
        }
    }

    private val logObserver = object : MPV.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
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
                        attemptDecoderFallback("Mediacodec error: $text")
                    }
                }
            }
        }
    }

    init {
        // Init failure must NOT throw out of the constructor: previously a
        // throw here made the manager report a confusing error while the
        // setting still said MPV. Capture it and report it via the listener.
        try {
            initializeMpv(initialDecoderMode)
        } catch (e: Exception) {
            Log.e(TAG, "MPV init failed", e)
            pendingInitError = e
            lastMpvEvent = "init-failed"
        } catch (e: Throwable) {
            Log.e(TAG, "MPV init failed (native)", e)
            pendingInitError = Exception("MPV native init failed: ${e.message}", e)
            lastMpvEvent = "init-failed"
        }
    }

    /**
     * Checked setOptionString: the wrapper returns 0 on success and a negative
     * MPV_ERROR_* code on failure, and never throws — so the old code, which
     * ignored every return value, could never tell a rejected option from an
     * applied one. Rejections are logged and kept for the phone error banner.
     */
    private fun setOption(key: String, value: String) {
        val rc = try {
            mpv.setOptionString(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "setOption $key failed", e)
            return
        }
        if (rc != 0) {
            val msg = "mpv option rejected: $key=$value (rc=$rc)"
            Log.w(TAG, msg)
            synchronized(rejectedOptions) {
                rejectedOptions.addLast(msg)
                while (rejectedOptions.size > 5) rejectedOptions.removeFirst()
            }
        }
    }

    /**
     * Runtime property set for use AFTER mpv.init(). mpv_set_option_string is
     * pre-init only and returns MPV_ERROR_OPTION_CHANGE_UNAVAILABLE (-13)
     * after init — that silently broke post-init force-window flips and live
     * subtitle styling. Runtime changes must go through the property API.
     */
    private fun setRuntimeProperty(key: String, value: String) {
        try {
            mpv.setPropertyString(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "setProperty $key failed", e)
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

    private fun rejectedOptionsText(): String =
        synchronized(rejectedOptions) {
            if (rejectedOptions.isEmpty()) "" else "; rejected options: " + rejectedOptions.joinToString(", ")
        }

    private fun hwdecValueFor(mode: DecoderMode): String = hwdecValueForStage(
        when (mode) {
            DecoderMode.HW -> DecoderStage.HW
            DecoderMode.HW_PLUS -> DecoderStage.HW_PLUS
            DecoderMode.SW -> DecoderStage.SW
        }
    )

    private fun hwdecValueForStage(stage: DecoderStage): String = when (stage) {
        DecoderStage.HW -> "mediacodec"
        DecoderStage.HW_PLUS -> "mediacodec-copy"
        DecoderStage.SW -> "no"
    }

    private fun initializeMpv(mode: DecoderMode) {
        val configDir = File(appContext.filesDir, "mpv").apply { mkdirs() }
        val cacheDir = File(appContext.cacheDir, "mpv_cache").apply { mkdirs() }

        // Configure fontconfig and clean up bundled subfont.ttf (Arabic subtitles support)
        setupArabicSubtitlesAndFontconfig(appContext, configDir)

        mpv.create(appContext)

        // Set configuration options before init()
        setOption("config", "yes")
        setOption("config-dir", configDir.absolutePath)
        setOption("gpu-shader-cache-dir", cacheDir.absolutePath)
        setOption("icc-cache-dir", cacheDir.absolutePath)
        setOption("vo", "gpu")
        setOption("gpu-context", "android")
        setOption("opengl-es", "yes")
        setOption("ao", "audiotrack,opensles")
        // Issue #181: audio device underruns during heavy software decode
        // (HEVC software fallback on this device starves the audio thread
        // after restarts/seeks). mpv's default audio buffer is 0.2s; 0.5s
        // gives the audio output 2.5x the slack. Trade-off is slightly
        // higher audio latency after seeks/pauses, negligible for a player.
        setOption("audio-buffer", "0.5")
        // Issue #103: keep-open=yes pauses on EOF so the last frame stays visible
        // and eof-reached signals to PlaybackManager. PlaybackManager owns queue
        // transitions; mpv holds only the active item.
        setOption("keep-open", "yes")
        setOption("ytdl", "no")

        // ROOT-CAUSE FIX (mpv "keeps loading forever", issue #96): mpv quits
        // when there is no file to play unless idle is set (manual default:
        // no). mpv-android's own BaseMPVView sets idle=once for exactly this
        // reason ("need to idle at least once for playFile() logic to work"),
        // and libmpv's core thread runs mp_play_files() right after init,
        // which breaks out immediately on an empty playlist when idle < yes
        // and then shuts the core down. MPx never set idle at all, so the
        // core died at app start (the engine is constructed in
        // MainActivity.onStart, long before the first tap) and every later
        // loadfile went to a dead core: no events, no error, spinner
        // forever. MPx uses "yes", not upstream's "once", because this
        // engine is a long-lived singleton: "once" would let the core quit
        // after the first queue finishes and break the NEXT session. Set
        // pre-init (upstream sets it post-init, but between init() and the
        // post-init block MPx loads subtitle settings, a window in which
        // the core could already quit).
        setOption("idle", "yes")

        // Subtitle configuration: use system fonts via fontconfig.
        // The font itself comes from saved settings so the first frame already
        // matches; the full option set is applied post-init below.
        setOption("sub-font-provider", "fontconfig")
        setOption("sub-fonts-dir", "/system/fonts")
        setOption("sub-font", subtitleMpvOptions(loadSavedSubtitleSettings())["sub-font"] ?: "sans-serif")
        setOption("sub-fix-timing", "yes")
        setOption("sub-auto", "fuzzy")

        val cacertFile = File(appContext.filesDir, "cacert.pem")
        if (cacertFile.exists()) {
            setOption("tls-ca-file", cacertFile.absolutePath)
        }

        // Hardware decoding mode, as a pre-init OPTION (not a property: the old
        // code called setPropertyString("hwdec") here, before mpv_initialize(),
        // which libmpv silently drops — the value only took effect later via
        // load()'s post-init property set). Record the stage for the fallback chain.
        decoderMode = mode
        currentDecoderStage = when (mode) {
            DecoderMode.HW -> DecoderStage.HW
            DecoderMode.HW_PLUS -> DecoderStage.HW_PLUS
            DecoderMode.SW -> DecoderStage.SW
        }
        setOption("hwdec", hwdecValueFor(mode))

        mpv.init()
        mpvInitialized = true

        // Post-init options must use the property API: setOptionString after
        // init() returns -13. force-window stays NO here on purpose (mpv-android's
        // own BaseMPVView carries the comment "could mess up VO init before
        // surfaceCreated() is called"): forcing a window with no surface yet
        // makes the first loadfile attempt VO init with no ANativeWindow, which
        // aborts the file before the player screen's SurfaceView even exists.
        // attachSurface() flips it to yes once a real surface is attached.
        setRuntimeProperty("force-window", "no")

        // Apply the full saved subtitle style (issue #75) now that mpv accepts live options.
        applySubtitleSettings(loadSavedSubtitleSettings())
        // Apply the saved audio delay + video zoom (issue #153) the same way.
        applyPlaybackTweaks(loadSavedSubtitleSettings())

        // Observe critical playback and decoder properties
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("demuxer-cache-time", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("idle-active", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("hwdec-current", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("video-codec", MPV.mpvFormat.MPV_FORMAT_STRING)
        // Issue #210: observe "vo" so mpv nulling the VO itself (a
        // reconfigure failure after the android-surface-size set, while
        // paused) is seen at all — no other restore site can fire then,
        // because time-pos does not advance while paused and a pure
        // resize is a surfaceChanged, not a surfaceDestroyed, so the
        // voIsNull flag is never set on that path. "vo" changes rarely
        // (gpu -> null -> gpu), so this adds no event spam; the branch
        // reads the value back and only acts when it is actually "null".
        mpv.observeProperty("vo", MPV.mpvFormat.MPV_FORMAT_STRING)
        // Issue #260: observe the display size so the PiP aspect ratio
        // refreshes the moment the dimensions become known — they are
        // still 0 at item-change time, when the PlayerScreen effect arms
        // the params with the 16:9 fallback. dwidth/dheight change
        // rarely (0 -> WxH at load, 0 on unload), so this adds no spam.
        mpv.observeProperty("dwidth", MPV.mpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("dheight", MPV.mpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NONE)

        mpv.addObserver(eventObserver)
        mpv.addLogObserver(logObserver)
    }

    private fun loadSavedSubtitleSettings(): AppSettings {
        return try {
            SharedPrefsAppSettingsRepository(appContext).load()
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading saved subtitle settings, using defaults", e)
            AppSettings()
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
        if (!mpvInitialized || isReleased) return
        try {
            subtitleMpvOptions(settings).forEach { (key, value) ->
                setRuntimeProperty(key, value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying subtitle settings to mpv", e)
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
        if (!mpvInitialized || isReleased) return
        try {
            setRuntimeProperty("audio-delay", (settings.audioDelayMs / 1000.0).toString())
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
                    setRuntimeProperty("video-zoom", zoom.toString())
                    zoomBeforeAspectZoom = zoom
                    videoZoomAtAspectZoomEntry = settings.videoZoom
                }
            } else {
                setRuntimeProperty("video-zoom", mpvVideoZoom(settings.videoZoom).toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying playback tweaks to mpv", e)
        }
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
     * Arabic subtitles configuration:
     * MPx is Arabic-first: prevents collision with the library's bundled subfont.ttf (known Shadda
     * diacritic collision bug) by explicitly deleting it if present and configuring fontconfig
     * to resolve system Arabic fonts (Noto Sans Arabic, Noto Naskh Arabic).
     */
    private fun setupArabicSubtitlesAndFontconfig(context: Context, configDir: File) {
        // 1. Delete bundled subfont.ttf if copied anywhere
        val subfontFilesDir = File(context.filesDir, "subfont.ttf")
        if (subfontFilesDir.exists()) {
            subfontFilesDir.delete()
        }
        val subfontConfigDir = File(configDir, "subfont.ttf")
        if (subfontConfigDir.exists()) {
            subfontConfigDir.delete()
        }

        // 2. Copy network certificate bundle only (skip subfont.ttf)
        val cacertFile = File(context.filesDir, "cacert.pem")
        if (!cacertFile.exists() || cacertFile.length() == 0L) {
            try {
                context.assets.open("cacert.pem").use { input ->
                    FileOutputStream(cacertFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "cacert.pem asset not copied", e)
            }
        }

        // 3. Point fontconfig at Android system fonts
        val fontsConf = File(configDir, "fonts.conf")
        val fontsXml = """
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
            <fontconfig>
                <dir>/system/fonts</dir>
                <dir>/system/font</dir>
                <dir>/data/fonts</dir>
                <match target="pattern">
                    <test qual="any" name="family">
                        <string>sans-serif</string>
                    </test>
                    <edit name="family" mode="prepend" binding="strong">
                        <string>Noto Sans Arabic</string>
                        <string>Noto Naskh Arabic</string>
                    </edit>
                </match>
            </fontconfig>
        """.trimIndent()

        try {
            fontsConf.writeText(fontsXml)
            Os.setenv("FONTCONFIG_FILE", fontsConf.absolutePath, true)
            Os.setenv("FONTCONFIG_PATH", configDir.absolutePath, true)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to export fontconfig env", e)
        }
    }

    private fun applyHwdecForMode(mode: DecoderMode) {
        decoderMode = mode
        currentDecoderStage = when (mode) {
            DecoderMode.HW -> DecoderStage.HW
            DecoderMode.HW_PLUS -> DecoderStage.HW_PLUS
            DecoderMode.SW -> DecoderStage.SW
        }
        val hwdecVal = hwdecValueFor(mode)
        try {
            mpv.setPropertyString("hwdec", hwdecVal)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set hwdec option to $hwdecVal", e)
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
        if (!fileLoadedForCurrentItem || currentQueue.isEmpty()) {
            Log.d(TAG, "Decoder fallback ignored; nothing loaded ($reason)")
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
        val codec = mpv.getPropertyString("video-codec") ?: activeVideoCodec.ifBlank { "unknown" }
        when (currentDecoderStage) {
            DecoderStage.HW -> {
                synchronized(metricsLock) { decoderFallbackCount++ }
                Log.w(TAG, "HW mediacodec failed for $codec ($reason). Stepping down to HW+ (mediacodec-copy)...")
                currentDecoderStage = DecoderStage.HW_PLUS
                try {
                    mpv.setPropertyString("hwdec", "mediacodec-copy")
                    notifyDecoderChanged()
                    reloadForDecoderFallback()
                } catch (e: Exception) {
                    attemptDecoderFallback("Failed to switch to mediacodec-copy: ${e.message}")
                }
            }
            DecoderStage.HW_PLUS -> {
                synchronized(metricsLock) { decoderFallbackCount++ }
                Log.w(TAG, "HW+ mediacodec-copy failed for $codec ($reason). Stepping down to SW (hwdec=no)...")
                currentDecoderStage = DecoderStage.SW
                try {
                    mpv.setPropertyString("hwdec", "no")
                    notifyDecoderChanged()
                    reloadForDecoderFallback()
                } catch (e: Exception) {
                    attemptDecoderFallback("Failed to switch to SW: ${e.message}")
                }
            }
            DecoderStage.SW -> {
                synchronized(metricsLock) { hardErrorCount++ }
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
        if (!mpvInitialized || isReleased || !fileLoadedForCurrentItem || currentQueue.isEmpty()) return
        val wasPlaying = cachedIsPlaying
        val pos = cachedPositionMs
        fallbackHwdecStage = currentDecoderStage
        fallbackReloadPending = true
        try {
            load(currentQueue, currentMediaIndex, pos)
            if (wasPlaying) play()
        } catch (e: Exception) {
            Log.w(TAG, "Decoder fallback reload failed", e)
            fallbackReloadPending = false
        }
    }

    private fun handlePropertyUpdate(property: String) {
        if (isReleased) return

        when (property) {
            "time-pos" -> {
                val sec = mpv.getPropertyDouble("time-pos") ?: 0.0
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
                                mpv.setPropertyString("vo", "gpu")
                                voIsNull = false
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed restoring vo=gpu on time-pos", e)
                            }
                        }
                    }
                }
                // Seek latency: first position update landing within 500 ms of
                // the seek target after a direct seek command.
                synchronized(metricsLock) {
                    val start = seekStartMs
                    val target = seekTargetMs
                    if (start != null && target != null &&
                        kotlin.math.abs(cachedPositionMs - target) <= 500L
                    ) {
                        lastSeekLatencyMs = SystemClock.elapsedRealtime() - start
                        logMetrics("seek", "seek_ms=$lastSeekLatencyMs target_ms=$target")
                        seekStartMs = null
                        seekTargetMs = null
                    }
                }
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
                        mpv.getPropertyString("vo")
                    } catch (_: Exception) {
                        null
                    }
                    if (vo == "null") {
                        voIsNull = true
                        if (currentSurface != null) {
                            try {
                                mpv.setPropertyString("vo", "gpu")
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
                val sec = mpv.getPropertyDouble("duration") ?: 0.0
                cachedDurationMs = (sec * 1000).toLong().coerceAtLeast(0L)
            }
            "demuxer-cache-time" -> {
                val sec = mpv.getPropertyDouble("demuxer-cache-time") ?: 0.0
                cachedBufferedMs = (sec * 1000).toLong().coerceAtLeast(0L)
            }
            "pause", "idle-active" -> {
                val isPaused = mpv.getPropertyBoolean("pause") ?: false
                val isIdle = mpv.getPropertyBoolean("idle-active") ?: false
                val playing = !isPaused && !isIdle
                if (cachedIsPlaying != playing) {
                    cachedIsPlaying = playing
                    mainHandler.post {
                        engineListener?.onIsPlayingChanged(playing)
                    }
                }
            }
            "speed" -> {
                val speed = mpv.getPropertyDouble("speed")?.toFloat() ?: 1.0f
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
                // Issue #199: durable retry for the #192 pin — sub-add is
                // async, so the pin right after sub-add usually runs
                // before the new track is in track-list. Re-attempt on
                // every track-list refresh until the just-added external
                // track is visible and sid is pinned to it.
                attemptExternalPin()
                mainHandler.post {
                    engineListener?.onTracksChanged()
                }
            }
            "eof-reached" -> {
                val eof = mpv.getPropertyBoolean("eof-reached") ?: false
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

    private fun notifyDecoderChanged() {
        val hwdec = mpv.getPropertyString("hwdec-current") ?: ""
        val codec = mpv.getPropertyString("video-codec") ?: ""
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

    private fun handleMpvEvent(eventId: Int, data: MPVNode) {
        if (isReleased) return

        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                // Demuxer opened the file: the load got past issue into
                // libmpv. If the stage sticks here, the stall is inside
                // demux/decode/VO init, not in load issue or event delivery.
                lastMpvEvent = "START_FILE"
            }
            MPV.mpvEvent.MPV_EVENT_SHUTDOWN -> {
                // Previously IGNORED: a core shutdown produced no event the
                // app acted on, so a dead core looked exactly like infinite
                // loading. Surface it as a real error so the manager clears
                // the splash (release() sets isReleased first, so normal
                // teardown never reaches here).
                lastMpvEvent = "SHUTDOWN"
                synchronized(metricsLock) { hardErrorCount++ }
                mainHandler.post {
                    engineListener?.onError(
                        MpvPlaybackException("MPV player shut down unexpectedly${rejectedOptionsText()}")
                    )
                }
            }
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                // Issue #258: no active load means this FILE_LOADED
                // belongs to a load the user deliberately stopped —
                // stop() clears activeMediaUri, and a FILE_LOADED
                // generated before the stop command was processed can
                // still be delivered after it. Dropping it keeps the
                // stop from re-marking the file loaded and posting READY
                // for a session that no longer exists.
                if (activeMediaUri.isNullOrBlank()) {
                    Log.w(TAG, "Ignoring FILE_LOADED with no active load (stopped?)")
                    return
                }
                // Drop stale FILE_LOADED event if the file changed before FILE_LOADED arrived
                val loadedPath = try { mpv.getPropertyString("path") } catch (_: Exception) { null }
                val isStale = if (!loadedPath.isNullOrBlank() && !activeMediaUri.isNullOrBlank()) {
                    !isPathMatch(loadedPath, activeMediaUri)
                } else {
                    false
                }
                if (isStale) {
                    Log.w(TAG, "Ignoring stale FILE_LOADED for $loadedPath; currently loading $activeMediaUri")
                    return
                }

                fileLoadedForCurrentItem = true
                // Issue #212: the fallback reload landed — duplicate
                // triggers for the old failure are behind us; a fresh
                // failure may fall back again.
                fallbackReloadPending = false
                lastMpvEvent = "FILE_LOADED"
                cachedPlaybackState = PlayerEngine.STATE_READY
                synchronized(metricsLock) {
                    if (loadStartMs != 0L && lastTimeToReadyMs == null) {
                        lastTimeToReadyMs = SystemClock.elapsedRealtime() - loadStartMs
                        logMetrics("ready", "ready_ms=$lastTimeToReadyMs")
                    }
                }
                // Apply a seek that arrived before the file loaded (resume or
                // scrub during prepare) instead of dropping it.
                pendingSeekMs?.let { target ->
                    pendingSeekMs = null
                    try {
                        val dur = cachedDurationMs
                        val clamped = if (dur > 0L) target.coerceIn(0L, dur) else target.coerceAtLeast(0L)
                        mpv.command("seek", (clamped / 1000.0).toString(), "absolute+exact")
                        cachedPositionMs = clamped
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed applying pending seek $target", e)
                    }
                }
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
                        var addedAny = false
                        pending.subtitles.forEach { sub ->
                            try {
                                if (sub.title != null) {
                                    mpv.command("sub-add", sub.uri, sub.flag, sub.title)
                                } else {
                                    mpv.command("sub-add", sub.uri, sub.flag)
                                }
                                addedAny = true
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed adding pending subtitle ${sub.uri}", e)
                            }
                        }
                        // Issue #192/#199: pin sid to the just-added
                        // external track — sub-add "select" alone does not
                        // stick. Arm the durable pin first: sub-add is
                        // async, so the immediate attempt usually runs
                        // before the new track is in track-list, and the
                        // track-list handler re-attempts it until it is
                        // pinned. A failed add (addedAny false) never
                        // arms the pin.
                        if (addedAny) {
                            pendingExternalPin = true
                            attemptExternalPin()
                        }
                    } else {
                        Log.w(TAG, "Dropping stale pending subtitles for ${pending.mediaUri} (active: $activeMediaUri)")
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
                mainHandler.post {
                    engineListener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    engineListener?.onTracksChanged()
                    notifyDecoderChanged()
                }
            }
            MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                // First video reconfig after a load is the first presented video
                // frame — the honest "first frame" signal on the mpv path.
                lastMpvEvent = "VIDEO_RECONFIG"
                synchronized(metricsLock) {
                    if (loadStartMs != 0L && !firstFrameRecorded) {
                        firstFrameRecorded = true
                        lastTimeToFirstFrameMs = SystemClock.elapsedRealtime() - loadStartMs
                        logMetrics("first_frame", "first_frame_ms=$lastTimeToFirstFrameMs")
                    }
                }
                notifyDecoderChanged()
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                lastMpvEvent = "END_FILE"
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
                    val reason = try { data.get("reason")?.asString() } catch (_: Exception) { null }
                    val errCode = try { data.get("error")?.asInt() } catch (_: Exception) { null }
                    val isBenignStop = reason in setOf("stop", "quit", "eof", "redirect")
                    val isRealFailure = !isBenignStop || (errCode != null && errCode != 0L)
                    if (!isRealFailure) return
                    val lastLog = synchronized(lastErrorLogs) { lastErrorLogs.lastOrNull() }
                    val detail = listOfNotNull(
                        reason?.let { "reason=$it" },
                        errCode?.let { "error=$it" },
                        lastLog,
                    ).joinToString("; ").ifBlank { "unknown libmpv error" } + rejectedOptionsText()
                    synchronized(metricsLock) { hardErrorCount++ }
                    mainHandler.post {
                        engineListener?.onError(
                            MpvPlaybackException("MPV could not play this file ($detail)")
                        )
                    }
                    return
                }
                // Check if end of file was an error
                val eofReached = mpv.getPropertyBoolean("eof-reached") ?: false
                if (!eofReached && cachedPositionMs < cachedDurationMs - 1000L && cachedDurationMs > 0L) {
                    // Premature end-file implies decoder or playback error
                    mainHandler.post {
                        attemptDecoderFallback("Premature END_FILE event received from libmpv")
                    }
                }
            }
        }
    }

    override fun setListener(listener: PlayerEngine.Listener?) {
        this.engineListener = listener
        // Flush a captured init failure as soon as anyone is listening.
        pendingInitError?.let { err ->
            pendingInitError = null
            mainHandler.post { engineListener?.onError(err) }
        }
    }

    override fun attachSurface(surface: Surface) {
        // Reference-pattern alignment (mpv-android's own BaseMPVView.surfaceCreated):
        // when a file load is about to run, it attaches the surface, sets
        // force-window=yes, and issues loadfile WITHOUT touching the "vo"
        // property — the load itself initialises VO against the attached
        // surface. It only (re)sets "vo" when no file is pending. MPx
        // previously set "vo" unconditionally on every attach, forcing a VO
        // teardown/recreation immediately before the loadfile — a needless
        // race on the exact path that was hanging with no error. Match the
        // reference: skip the vo set whenever a load is about to run.
        val willLoad = pendingLoad != null ||
            (mpvInitialized && !isReleased && !fileLoadedForCurrentItem && currentQueue.isNotEmpty())
        synchronized(surfaceLock) {
            currentSurface = surface
            try {
                mpv.attachSurface(surface)
                setRuntimeProperty("force-window", "yes")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface", e)
            }
            // Issue #181: restore vo=gpu in its own try/catch, so a throwing
            // native attach cannot skip it. A detach mid-session left vo=null,
            // and mpv.attachSurface() binds to the CURRENT vo — with vo=null
            // there is no VO, so the next loadfile decodes but renders to the
            // null VO (sound, no video, no error: the phone log shows
            // "VO: [null]" on every load after the first detach). Restore
            // vo=gpu even when a load is about to run. The Nov 4 race fix
            // only forbids a GRATUITOUS vo set when vo is already gpu;
            // restoring a nulled vo cannot tear down a live VO, because
            // there is none.
            if (voIsNull) {
                try {
                    mpv.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu on attach", e)
                }
            } else if (willLoad) {
                // A load is about to run: loadfile itself initialises
                // VO against the attached surface; do not touch "vo".
            } else {
                try {
                    mpv.setPropertyString("vo", "gpu")
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
        // surface exists. No-op when the file already loaded (rotation, etc.).
        if (mpvInitialized && !isReleased && !fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            load(currentQueue, currentMediaIndex, cachedPositionMs)
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
    override fun onSurfaceChanged(width: Int, height: Int) {
        if (!mpvInitialized || isReleased || width <= 0 || height <= 0) return
        try {
            mpv.setPropertyString("android-surface-size", "${width}x${height}")
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
                    mpv.setPropertyString("vo", "gpu")
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
     * (via PlaybackManager.detachSurface()); the AndroidView has no key() and no
     * conditional, so the SurfaceView stays in composition while the player is
     * open. surfaceDestroyed is a framework callback — the surface is torn down
     * when the view leaves the window / the window is hidden — and the phone log
     * shows it firing between loads mid-session (vo=null, then an immediate
     * re-attach + reload with VO still null). The exact framework trigger on
     * this device at EOF/seek/replay is not visible in code; what matters is
     * that a detach can happen at any point mid-session, so attachSurface()
     * must restore vo=gpu whenever vo is null instead of assuming vo survived.
     */
    override fun detachSurface() {
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
                mpv.setPropertyString("vo", "null")
                mpv.setPropertyString("force-window", "no")
                mpv.detachSurface()
            } catch (e: Exception) {
                Log.w(TAG, "Error in detachSurface barrier", e)
            }
        }
    }

    override fun load(media: PlayableMedia, resumeMs: Long) {
        load(listOf(media), 0, resumeMs)
    }

    override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
        currentQueue = items
        if (items.isEmpty()) {
            // Issue #212: an empty queue is a fresh context — no current
            // item, so no fallback override or in-flight reload survives.
            fallbackHwdecStage = null
            fallbackReloadPending = false
            return
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
        fileLoadedForCurrentItem = false
        // Issue #199: a pending pin belongs to the item it was picked
        // for — a new load must never let it pin a track on this item.
        pendingExternalPin = false
        pendingSeekMs = null
        loadIssued = false
        lastMpvEvent = "none"
        synchronized(metricsLock) {
            // Tap time for tap-to-first-frame: load() is called at tap time
            // even when the actual loadfile is deferred for lack of surface.
            loadStartMs = SystemClock.elapsedRealtime()
            firstFrameRecorded = false
            lastTimeToReadyMs = null
            lastTimeToFirstFrameMs = null
        }

        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        currentMediaIndex = targetIndex
        val targetItem = items[targetIndex]

        // ROOT-CAUSE FIX (mpv "never works at all"): playback is normally
        // started from the library BEFORE the player screen (and its
        // SurfaceView) exists. The old code fired loadfile immediately, and
        // with force-window=yes libmpv attempted VO init with no ANativeWindow
        // attached: the video chain failed, the file aborted with END_FILE,
        // and PlaybackManager reported a failure before surfaceCreated ever
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
            val hwdecVal = hwdecValueForStage(fallbackStage)
            try {
                mpv.setPropertyString("hwdec", hwdecVal)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set fallback hwdec to $hwdecVal", e)
            }
        } else {
            applyHwdecForMode(decoderMode)
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
                    mpv.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu before loadfile", e)
                }
            }
        }

        try {
            mpv.command("playlist-clear")

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
                mpv.command("loadfile", resolvedTarget.uri, "replace", "-1", "start=$startSec")
            } else {
                mpv.command("loadfile", resolvedTarget.uri, "replace")
            }

            // Issue #103: PlaybackManager alone owns queue transitions. mpv holds
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
            synchronized(metricsLock) { hardErrorCount++ }
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

    override fun play() {
        try {
            mpv.setPropertyBoolean("pause", false)
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

    override fun pause() {
        try {
            mpv.setPropertyBoolean("pause", true)
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

    override fun seekTo(positionMs: Long) {
        val dur = duration
        val target = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        // A seek before FILE_LOADED (resume fired on transition, scrub during
        // prepare, or a seek while the load is still deferred for lack of
        // surface) must not vanish: park it and apply it on FILE_LOADED.
        if (!fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            pendingSeekMs = target
            cachedPositionMs = target
            return
        }
        synchronized(metricsLock) {
            seekStartMs = SystemClock.elapsedRealtime()
            seekTargetMs = target
        }
        try {
            mpv.command("seek", (target / 1000.0).toString(), "absolute+exact")
            cachedPositionMs = target
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking in MPV", e)
        }
    }

    override fun seekTo(index: Int, positionMs: Long) {
        if (index in currentQueue.indices) {
            load(currentQueue, index, positionMs)
        }
    }

    override fun setSpeed(speed: Float) {
        try {
            mpv.setPropertyDouble("speed", speed.toDouble())
            cachedSpeed = speed
            mainHandler.post { engineListener?.onPlaybackParametersChanged(speed) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed in MPV", e)
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

    override fun selectTrack(type: TrackType, id: String?) {
        when (type) {
            TrackType.AUDIO -> {
                if (id == null || id == "-1" || id.isBlank()) {
                    try { mpv.setPropertyString("aid", "no") } catch (_: Exception) {}
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull() ?: return
                        val trackIndex = parts[1].toIntOrNull() ?: return
                        selectAudioTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull() ?: return
                        val tracks = getAudioTracks()
                        val target = tracks.getOrNull(trackIdx) ?: return
                        selectAudioTrack(target.groupIndex, target.trackIndex)
                    }
                }
            }
            TrackType.SUBTITLE -> {
                if (id == null || id == "-1" || id.isBlank()) {
                    selectSubtitleTrack(null, null)
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull()
                        val trackIndex = parts[1].toIntOrNull()
                        selectSubtitleTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull()
                        if (trackIdx == null || trackIdx < 0) {
                            selectSubtitleTrack(null, null)
                        } else {
                            val tracks = getSubtitleTracks()
                            val target = tracks.getOrNull(trackIdx)
                            if (target != null) {
                                selectSubtitleTrack(target.groupIndex, target.trackIndex)
                            } else {
                                selectSubtitleTrack(null, null)
                            }
                        }
                    }
                }
            }
            TrackType.VIDEO -> {
                // Video track selection (multi-angle / multi-quality files):
                // same id scheme as audio — "group:track" or a plain ordinal
                // among video tracks — driving mpv's "vid" property.
                if (id == null || id == "-1" || id.isBlank()) {
                    try { mpv.setPropertyString("vid", "auto") } catch (_: Exception) {}
                } else {
                    val videoIds = getVideoTrackIds()
                    val targetId = run {
                        val parts = id.split(":")
                        if (parts.size == 2) {
                            val groupIndex = parts[0].toIntOrNull() ?: return
                            val trackIndex = parts[1].toIntOrNull() ?: return
                            // Issue #247: validate, don't fall back by
                            // position — groupIndex is the ordinal among
                            // video tracks and trackIndex is the mpv id,
                            // so the track at that ordinal must carry
                            // that id or the tap is stale and this is a
                            // no-op (the ?: return below skips the vid
                            // write). The old getOrNull(groupIndex) ?:
                            // getOrNull(trackIndex) picked whatever id
                            // sat at those positions once the list changed.
                            videoIds.getOrNull(groupIndex)?.takeIf { it == trackIndex }
                        } else {
                            val ordinal = id.toIntOrNull() ?: return
                            videoIds.getOrNull(ordinal)
                        }
                    } ?: return
                    try { mpv.setPropertyString("vid", targetId.toString()) } catch (_: Exception) {}
                }
            }
        }
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = getAudioTracks()
        // Issue #247: no positional fallback — groupIndex is the ordinal
        // among audio tracks, so getOrNull(groupIndex) picks whatever
        // track sits at that position now, which is a different track
        // when the list changed between render and tap. A stale tap is
        // a no-op (the current selection stays, and aid is left alone —
        // NOT set to "no"); the UI list refreshes via updateTracksState()
        // in the manager and on every track-list change.
        val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
        if (target != null) {
            try { mpv.setPropertyString("aid", target.trackIndex.toString()) } catch (_: Exception) {}
        } else {
            Log.w(TAG, "Ignoring stale audio track pick ($groupIndex:$trackIndex): no such track")
        }
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        if (groupIndex == null || trackIndex == null) {
            try { mpv.setPropertyString("sid", "no") } catch (_: Exception) {}
        } else {
            val tracks = getSubtitleTracks()
            // Issue #247: same as selectAudioTrack — no positional
            // fallback; a stale tap is a no-op and sid is left alone
            // (NOT set to "no", which would drop subtitles).
            val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            if (target != null) {
                try { mpv.setPropertyString("sid", target.trackIndex.toString()) } catch (_: Exception) {}
            } else {
                Log.w(TAG, "Ignoring stale subtitle track pick ($groupIndex:$trackIndex): no such track")
            }
        }
    }

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
     * Issue #192: pin mpv's `sid` option to the currently selected
     * EXTERNAL subtitle track. `sub-add` with the "select" flag only
     * changes the current selection — it leaves `sid` at its default
     * "auto", so mpv's next track-selection re-evaluation (seek, decoder
     * reinit) falls back to the default-flagged embedded track and the
     * external subtitle the user just picked silently disappears (user's
     * v0.3.1 log: sid=2 selected at 19:25:31, sid=1 back at 19:25:33).
     * Setting `sid` to the track id explicitly makes the pick sticky, so
     * it survives track-list refreshes and seeks.
     *
     * Returns true when sid was pinned, false when no external track is
     * selected (e.g. the sub-add failed or the track is not in track-list
     * yet) — a failed add never freezes a wrong selection. Issue #199:
     * the pin is one-shot and sub-add is async, so callers arm
     * [pendingExternalPin] and go through [attemptExternalPin] instead of
     * calling this directly; the track-list handler retries it until it
     * succeeds.
     */
    private fun pinSelectedExternalSubtitleTrack(): Boolean {
        try {
            val selected = getSubtitleTracks().firstOrNull { it.isSelected && it.isExternal }
                ?: return false
            mpv.setPropertyString("sid", selected.trackIndex.toString())
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed pinning external subtitle selection", e)
            return false
        }
    }

    /**
     * Issue #199: one attempt at the #192 pin. Clears [pendingExternalPin]
     * only once sid is actually pinned; a miss leaves it set so the next
     * track-list refresh retries. No-op when nothing is pending. Safe to
     * call from the track-list property-change handler: it only ever pins
     * a track that is currently selected AND external, so it can never
     * override a manual pick (embedded or a different external track)
     * the user made after the sub-add.
     */
    private fun attemptExternalPin(): Boolean {
        if (!pendingExternalPin) return true
        if (pinSelectedExternalSubtitleTrack()) {
            pendingExternalPin = false
            return true
        }
        return false
    }

    override fun replaceMediaItem(index: Int, media: PlayableMedia) {
        if (index in currentQueue.indices) {
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

            if (index == currentMediaIndex) {
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
                    var addedAny = false
                    val newExts = media.externalSubtitles.filter { rawExt ->
                        previous?.externalSubtitles?.none { isSameExternalSubtitle(it, rawExt) } ?: true
                    }
                    newExts.zip(fdOpener.resolveExternalSubtitles(newExts))
                        .forEach { (rawExt, resolvedExt) ->
                            try {
                                mpv.command("sub-add", resolvedExt.uri, "select", rawExt.name)
                                addedAny = true
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed adding external subtitle ${rawExt.name}", e)
                            }
                        }
                    // Issue #192/#199: pin sid to the just-added external
                    // track — sub-add "select" alone does not stick. Arm
                    // the durable pin first: sub-add is async, so the
                    // immediate attempt usually runs before the new track
                    // is in track-list, and the track-list handler
                    // re-attempts it until it is pinned. A failed add
                    // (addedAny false) never arms the pin.
                    if (addedAny) {
                        pendingExternalPin = true
                        attemptExternalPin()
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
                        subs.add(PendingSubtitle(uri = ext.uri, flag = "select", title = ext.name))
                    }
                    pendingSubtitles = if (subs.isNotEmpty()) {
                        PendingSubtitles(
                            mediaUri = activeMediaUri ?: media.uri,
                            mediaId = media.id,
                            subtitles = subs,
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun stop() {
        pendingLoad = null
        pendingSeekMs = null
        pendingSubtitles = null
        pendingExternalPin = false
        activeMediaId = null
        activeMediaUri = null
        loadIssued = false
        lastMpvEvent = "none"
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
        currentQueue = emptyList()
        currentMediaIndex = 0
        cachedPositionMs = 0L
        cachedDurationMs = 0L
        cachedBufferedMs = 0L
        fallbackReloadPending = false
        fallbackHwdecStage = null
        try {
            mpv.command("stop")
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

    override fun release() {
        pendingLoad = null
        pendingSeekMs = null
        pendingSubtitles = null
        pendingExternalPin = false
        // Issue #246: the engine is dead; no stale play intent survives it.
        playRequested = false
        activeMediaId = null
        activeMediaUri = null
        isReleased = true
        mainHandler.removeCallbacksAndMessages(null)
        detachSurface()
        try {
            mpv.removeObserver(eventObserver)
            mpv.removeLogObserver(logObserver)
            mpv.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MPV", e)
        }
        // Issue #306: the log observer is gone, so no more appends can arrive —
        // flush any lines still buffered in MpvLogFile and close its writer.
        MpvLogFile.close()
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

    override val currentPosition: Long
        get() = cachedPositionMs

    /**
     * Issue #225: display size of the current video, read live from mpv's
     * `dwidth`/`dheight` (aspect-corrected display size, not raw coded
     * size) — the PiP aspect-ratio source of truth. 0 when mpv is not
     * initialized or no video is loaded; never a guessed size.
     */
    override val videoWidth: Int
        get() = if (!mpvInitialized || isReleased) {
            0
        } else {
            try {
                mpv.getPropertyInt("dwidth")?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }

    override val videoHeight: Int
        get() = if (!mpvInitialized || isReleased) {
            0
        } else {
            try {
                mpv.getPropertyInt("dheight")?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }

    override val duration: Long
        get() = cachedDurationMs

    override val bufferedPosition: Long
        get() = cachedBufferedMs

    override val isPlaying: Boolean
        get() = cachedIsPlaying

    override val playbackState: Int
        get() = cachedPlaybackState

    override val currentMediaItemIndex: Int
        get() = currentMediaIndex

    override val hasNextMediaItem: Boolean
        get() = currentMediaIndex < currentQueue.size - 1

    override val hasPreviousMediaItem: Boolean
        get() = currentMediaIndex > 0

    override fun seekToNextMediaItem() {
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
            val next = currentMediaIndex + 1
            load(currentQueue, next, 0L)
            if (wasPlaying) play()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem) {
            // Issue #305: same paused-state preservation as
            // seekToNextMediaItem above.
            val wasPlaying = cachedIsPlaying
            val prev = currentMediaIndex - 1
            load(currentQueue, prev, 0L)
            if (wasPlaying) play()
        } else {
            seekTo(0L)
        }
    }

    /** mpv track-list ids of video tracks, in order — used by VIDEO selection. */
    private fun getVideoTrackIds(): List<Int> {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val ids = mutableListOf<Int>()
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type")
            if (type.equals("video", ignoreCase = true)) {
                ids.add(mpv.getPropertyInt("track-list/$i/id") ?: (ids.size + 1))
            }
        }
        return ids
    }

    override fun getAudioTracks(): List<AudioTrackInfo> {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val audioList = mutableListOf<AudioTrackInfo>()
        var audioGroup = 0
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type")
            if (type.equals("audio", ignoreCase = true)) {
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (audioGroup + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang") ?: "und"
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                val label = title?.ifBlank { null } ?: "Audio $id ($lang)"
                audioList.add(
                    AudioTrackInfo(
                        groupIndex = audioGroup,
                        trackIndex = id,
                        label = label,
                        language = lang,
                        isSelected = selected,
                        mimeType = mpv.getPropertyString("track-list/$i/codec"),
                    )
                )
                audioGroup++
            }
        }
        return audioList
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val subtitleList = mutableListOf<SubtitleTrackInfo>()
        var subGroup = 0
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type")
            if (type.equals("sub", ignoreCase = true)) {
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (subGroup + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang") ?: "und"
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                val external = mpv.getPropertyBoolean("track-list/$i/external") ?: false
                val isDefault = mpv.getPropertyBoolean("track-list/$i/default") ?: false
                val label = title?.ifBlank { null } ?: "Subtitle $id ($lang)"
                subtitleList.add(
                    SubtitleTrackInfo(
                        groupIndex = subGroup,
                        trackIndex = id,
                        label = label,
                        language = lang,
                        isSelected = selected,
                        isExternal = external,
                        isDefault = isDefault,
                    )
                )
                subGroup++
            }
        }
        return subtitleList
    }
}
