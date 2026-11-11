package com.mpx.player.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.EnginePlaybackMetrics
import com.mpx.player.playback.MpvFdOpener
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.MpvLogFile
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.subtitleMpvOptions
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.io.FileOutputStream

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
 * What is NOT here yet (later Phase 4 steps, reached through [Host]):
 * track listing/switching (step 2), subtitle rendering/confirmation
 * (step 3) and settings-to-mpv configuration (step 4). The host
 * callbacks below are the seams for those concerns: they run at exactly
 * the points the old monolith ran that code, so behaviour is unchanged.
 * The only mpv knowledge the facade still touches directly is the
 * not-yet-moved code, through [mpv] and the small internal helpers.
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
) {

    /**
     * Seams for the concerns that stay in [MpvPlayerEngine] until their
     * Phase 4 step moves them. Every method is called at exactly the
     * point the old monolith ran the equivalent code.
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

    internal val mainHandler = Handler(Looper.getMainLooper())

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
    internal var currentQueue: List<PlayableMedia> = emptyList()

    internal var currentMediaIndex: Int = 0

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

    internal var isReleased: Boolean = false

    internal var mpvInitialized: Boolean = false

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
    internal var fallbackHwdecStage: DecoderStage? = null

    /**
     * Issue #212: a fallback reload is in flight (loadfile issued,
     * FILE_LOADED not yet arrived). Duplicate triggers for the same
     * failure — several mediacodec log lines, or a log line plus a
     * premature END_FILE — are ignored while this is set, so one
     * failure steps down once. Cleared on FILE_LOADED, or by a fresh
     * non-fallback load().
     */
    internal var fallbackReloadPending: Boolean = false

    internal var decoderMode: DecoderMode = initialDecoderMode
    // Cached playback state
    internal var cachedPositionMs: Long = 0L

    internal var cachedDurationMs: Long = 0L

    internal var cachedBufferedMs: Long = 0L

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
    internal var playRequested: Boolean = false

    internal var cachedPlaybackState: Int = PlayerEngine.STATE_IDLE

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

    /** mpv options libmpv rejected (setOptionString returns non-zero). Previously every
     * setOptionString result was ignored, so a rejected vo/gpu-context/hwdec/ao option
     * was completely invisible — the phone banner now names them (see END_FILE detail). */
    private val rejectedOptions: ArrayDeque<String> = ArrayDeque()

    /** A video load deferred because no surface exists yet (see load()). Flushed by
     * attachSurface(); audio loads are never deferred (no video output needed). */
    @Volatile
    internal var pendingLoad: Triple<List<PlayableMedia>, Int, Long>? = null

    /** A seek requested before the current file loaded (e.g. resume position fired
     * on item transition while the load was still deferred for lack of surface).
     * Applied the moment FILE_LOADED arrives, so a seek is never silently lost.
     * Written on Main, read/cleared on the event thread: volatile. */
    @Volatile
    private var pendingSeekMs: Long? = null

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
    private var loadIssued: Boolean = false

    @Volatile
    private var lastMpvEvent: String = "none"

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

    /** Init failure captured instead of thrown, so the manager can report it instead of crashing. */
    private var pendingInitError: Exception? = null

    internal val mpv: MPV = MPV()

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
    internal fun setRuntimeProperty(key: String, value: String) {
        try {
            mpv.setPropertyString(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "setProperty $key failed", e)
        }
    }


    private fun rejectedOptionsText(): String =
        synchronized(rejectedOptions) {
            if (rejectedOptions.isEmpty()) "" else "; rejected options: " + rejectedOptions.joinToString(", ")
        }


    private fun initializeMpv(mode: DecoderMode) {
        val configDir = File(appContext.filesDir, "mpv").apply { mkdirs() }
        val cacheDir = File(appContext.cacheDir, "mpv_cache").apply { mkdirs() }

        // Configure bundled subfont.ttf and network certificates
        setupSubtitleFontAndCertificates(appContext, configDir)

        // Issue #432: sync the picked mpv config folder before mpv
        // reads its config — mpv.conf / input.conf / fonts / scripts
        // from the user's folder land in configDir. A missing file
        // leaves the current local one alone, and the sync never
        // deletes, so subfont.ttf / cacert.pem above are untouched.
        val savedSettings = loadSavedSubtitleSettings()
        MpvConfigSync.sync(appContext, configDir, savedSettings.mpvConfStorageUri)

        mpv.create(appContext)

        // Set configuration options before init()
        setOption("config", "yes")
        // Issue #430: Lua scripts (mpv default is on). OFF sets
        // load-scripts=no pre-init so scripts in config-dir/scripts/
        // never load; ON sets nothing — zero behaviour change for
        // existing users.
        if (!savedSettings.enableLuaScripts) setOption("load-scripts", "no")
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
        // and eof-reached signals to PlaybackCoordinator. PlaybackCoordinator owns queue
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

        // Subtitle configuration: use bundled subfont.ttf fallback font.
        // mpv on Android has no fontconfig; libass uses subfont.ttf from config-dir.
        setOption("sub-font-provider", "auto")
        setOption("sub-font", subtitleMpvOptions(savedSettings)["sub-font"] ?: "sans-serif")
        setOption("sub-visibility", "yes")
        setOption("sub-fix-timing", "yes")
        setOption("sub-auto", "fuzzy")

        val cacertFile = File(configDir, "cacert.pem").takeIf { it.exists() }
            ?: File(appContext.filesDir, "cacert.pem").takeIf { it.exists() }
        if (cacertFile != null) {
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
        setOption("hwdec", host.hwdecValueFor(mode))

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

        // Phase 4 step 1: subtitle style + playback tweaks still live in
        // MpvPlayerEngine (steps 3-4) — applied here, at the original
        // point, through Host.applyPostInitSettings.
        host.applyPostInitSettings(this)

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


    internal fun loadSavedSubtitleSettings(): AppSettings {
        return try {
            SharedPrefsAppSettingsRepository(appContext).load()
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading saved subtitle settings, using defaults", e)
            AppSettings()
        }
    }


    /**
     * Subtitle fallback font and TLS certificates configuration:
     * mpv's libass on Android is compiled without Fontconfig. libass relies on the bundled
     * fallback font `subfont.ttf` (Droid Sans Fallback, covering Latin, Arabic, CJK, Cyrillic,
     * Greek, Hebrew, etc.) placed in mpv's config directory (configDir) and filesDir.
     * Without subfont.ttf, any unmatched font query (such as "sans-serif") fails with
     * `fontselect: failed to find any fallback with glyph 0x0`, rendering subtitles invisible.
     * We ensure subfont.ttf and cacert.pem are copied from assets to configDir and filesDir.
     */
    private fun setupSubtitleFontAndCertificates(context: Context, configDir: File) {
        copyAssetIfDifferent(context, "subfont.ttf", File(configDir, "subfont.ttf"))
        copyAssetIfDifferent(context, "subfont.ttf", File(context.filesDir, "subfont.ttf"))

        copyAssetIfDifferent(context, "cacert.pem", File(configDir, "cacert.pem"))
        copyAssetIfDifferent(context, "cacert.pem", File(context.filesDir, "cacert.pem"))

        // Clean up any stale fonts.conf from earlier broken fontconfig attempts
        val fontsConf = File(configDir, "fonts.conf")
        if (fontsConf.exists()) {
            fontsConf.delete()
        }

        val subfontConfig = File(configDir, "subfont.ttf")
        Log.i(TAG, "Subtitle assets verified: subfont.ttf exists=${subfontConfig.exists()} " +
                "(${subfontConfig.length()} bytes) at ${subfontConfig.absolutePath}")
    }


    private fun copyAssetIfDifferent(context: Context, assetName: String, destFile: File) {
        try {
            destFile.parentFile?.mkdirs()
            val assetLength = context.assets.open(assetName).use { it.available().toLong() }
            if (!destFile.exists() || destFile.length() == 0L || (assetLength > 0L && destFile.length() != assetLength)) {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied asset $assetName to ${destFile.absolutePath} (${destFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed copying asset $assetName to ${destFile.absolutePath}", e)
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
                // Phase 4 step 1: the remembered-external-pick re-apply
                // and the #332 confirmation still live in MpvPlayerEngine
                // (step 3) — see Host.onTrackListChanged.
                host.onTrackListChanged()
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
                // Phase 4 step 1: the pending-subtitle dispatch and
                // the embedded auto-select still live in MpvPlayerEngine
                // (steps 2-3) — see Host.onFileLoaded.
                host.onFileLoaded()
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


    internal fun setListener(listener: PlayerEngine.Listener?) {
        this.engineListener = listener
        // Flush a captured init failure as soon as anyone is listening.
        pendingInitError?.let { err ->
            pendingInitError = null
            mainHandler.post { engineListener?.onError(err) }
        }
    }


    internal fun attachSurface(surface: Surface) {
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
    internal fun onSurfaceChanged(width: Int, height: Int) {
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
                mpv.setPropertyString("vo", "null")
                mpv.setPropertyString("force-window", "no")
                mpv.detachSurface()
            } catch (e: Exception) {
                Log.w(TAG, "Error in detachSurface barrier", e)
            }
        }
    }


    internal fun load(media: PlayableMedia, resumeMs: Long) {
        load(listOf(media), 0, resumeMs)
    }


    internal fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
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
        host.onLoadStarted()
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
                mpv.setPropertyString("hwdec", hwdecVal)
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


    internal fun play() {
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


    internal fun pause() {
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


    internal fun seekTo(positionMs: Long) {
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


    internal fun seekTo(index: Int, positionMs: Long) {
        if (index in currentQueue.indices) {
            load(currentQueue, index, positionMs)
        }
    }


    internal fun setSpeed(speed: Float) {
        try {
            mpv.setPropertyDouble("speed", speed.toDouble())
            cachedSpeed = speed
            mainHandler.post { engineListener?.onPlaybackParametersChanged(speed) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed in MPV", e)
        }
    }


    internal fun stop() {
        pendingLoad = null
        pendingSeekMs = null
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


    internal fun release() {
        pendingLoad = null
        pendingSeekMs = null
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


    internal fun seekToNextMediaItem() {
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


    internal fun seekToPreviousMediaItem() {
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
                mpv.getPropertyInt("dwidth")?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }


    internal val videoHeight: Int
        get() = if (!mpvInitialized || isReleased) {
            0
        } else {
            try {
                mpv.getPropertyInt("dheight")?.coerceAtLeast(0) ?: 0
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
        get() = currentMediaIndex


    internal val hasNextMediaItem: Boolean
        get() = currentMediaIndex < currentQueue.size - 1


    internal val hasPreviousMediaItem: Boolean
        get() = currentMediaIndex > 0


    internal fun getLoadStage(): MpvLoadStage {
        val surfaceAttached = synchronized(surfaceLock) { currentSurface != null }
        return MpvLoadStage(
            initialized = mpvInitialized && pendingInitError == null,
            surfaceAttached = surfaceAttached,
            loadIssued = loadIssued,
            lastEvent = lastMpvEvent,
        )
    }


    internal fun getPlaybackMetrics(): EnginePlaybackMetrics =
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

}

/**
 * Phase 4 step 1: queued external subtitles for the item being loaded,
 * dispatched on FILE_LOADED. Moved here with load() (which builds them);
 * the dispatch itself still lives in MpvPlayerEngine (step 3) and these
 * classes move with it.
 */
internal data class PendingSubtitle(
    val uri: String,
    val flag: String,
    val title: String? = null,
    // Issue #441: the detected mpv sub-codepage for this file
    // ("cp1256" for Windows-1256 Arabic subtitles); null means
    // mpv's default. Copied from the ExternalSubtitle it came from.
    val codepage: String? = null,
)

internal data class PendingSubtitles(
    val mediaUri: String,
    val mediaId: String,
    val subtitles: List<PendingSubtitle>,
)
