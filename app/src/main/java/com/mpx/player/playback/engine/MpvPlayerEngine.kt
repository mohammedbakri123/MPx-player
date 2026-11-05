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
import com.mpx.player.playback.MpvLoadStage
import com.mpx.player.playback.MpvLogFile
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.subtitleMpvOptions
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.io.FileOutputStream

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

    private var engineListener: PlayerEngine.Listener? = null
    private var currentQueue: List<PlayableMedia> = emptyList()
    private var currentMediaIndex: Int = 0

    private var currentSurface: Surface? = null
    private var isReleased: Boolean = false
    private var mpvInitialized: Boolean = false

    private enum class DecoderStage { HW, HW_PLUS, SW }
    private var currentDecoderStage: DecoderStage = DecoderStage.HW

    var decoderMode: DecoderMode = initialDecoderMode
        private set

    // Cached playback state
    private var cachedPositionMs: Long = 0L
    private var cachedDurationMs: Long = 0L
    private var cachedBufferedMs: Long = 0L
    private var cachedIsPlaying: Boolean = false
    private var cachedPlaybackState: Int = PlayerEngine.STATE_IDLE
    private var cachedSpeed: Float = 1.0f

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

    private fun rejectedOptionsText(): String =
        synchronized(rejectedOptions) {
            if (rejectedOptions.isEmpty()) "" else "; rejected options: " + rejectedOptions.joinToString(", ")
        }

    private fun hwdecValueFor(mode: DecoderMode): String = when (mode) {
        DecoderMode.HW -> "mediacodec"
        DecoderMode.HW_PLUS -> "mediacodec-copy"
        DecoderMode.SW -> "no"
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
     */
    private fun attemptDecoderFallback(reason: String) {
        val codec = mpv.getPropertyString("video-codec") ?: activeVideoCodec.ifBlank { "unknown" }
        when (currentDecoderStage) {
            DecoderStage.HW -> {
                synchronized(metricsLock) { decoderFallbackCount++ }
                Log.w(TAG, "HW mediacodec failed for $codec ($reason). Stepping down to HW+ (mediacodec-copy)...")
                currentDecoderStage = DecoderStage.HW_PLUS
                try {
                    mpv.setPropertyString("hwdec", "mediacodec-copy")
                    notifyDecoderChanged()
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

    private fun handlePropertyUpdate(property: String) {
        if (isReleased) return

        when (property) {
            "time-pos" -> {
                val sec = mpv.getPropertyDouble("time-pos") ?: 0.0
                cachedPositionMs = (sec * 1000).toLong().coerceAtLeast(0L)
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
            "hwdec-current", "video-codec" -> {
                notifyDecoderChanged()
            }
            "track-list" -> {
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
        val codecLabel = if (codec.isNotBlank()) codec else "unknown"
        return when {
            hwdec == "mediacodec" -> "mediacodec ($codecLabel)"
            hwdec == "mediacodec-copy" -> "mediacodec-copy ($codecLabel)"
            hwdec == "no" || hwdec.isBlank() -> "software ($codecLabel)"
            else -> "$hwdec ($codecLabel)"
        }
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
                    val matches = activeMediaUri == null || isPathMatch(pending.mediaUri, activeMediaUri)
                    if (matches) {
                        pending.subtitles.forEach { sub ->
                            try {
                                if (sub.title != null) {
                                    mpv.command("sub-add", sub.uri, sub.flag, sub.title)
                                } else {
                                    mpv.command("sub-add", sub.uri, sub.flag)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed adding pending subtitle ${sub.uri}", e)
                            }
                        }
                    } else {
                        Log.w(TAG, "Dropping stale pending subtitles for ${pending.mediaUri} (active: $activeMediaUri)")
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
                if (!fileLoadedForCurrentItem) {
                    val reason = try { data.get("reason")?.asString() } catch (_: Exception) { null }
                    val errCode = try { data.get("error")?.asInt() } catch (_: Exception) { null }
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
                if (willLoad) {
                    // A load is about to run: loadfile itself initialises
                    // VO against the attached surface; do not touch "vo".
                } else {
                    mpv.setPropertyString("vo", "gpu")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in attachSurface", e)
            }
        }
        // Flush a load deferred for lack of surface (see load()). This is the
        // only path by which a video loadfile now runs: with a real surface
        // attached, so VO init cannot fail for want of an ANativeWindow.
        // Returns early so the legacy reload below cannot double-load.
        val pending = pendingLoad
        if (pending != null) {
            pendingLoad = null
            load(pending.first, pending.second, pending.third)
            play()
            return
        }
        // Legacy load-before-surface recovery (kept as a safety net for loads
        // that ran surfaceless before this fix, e.g. engine switches): if the
        // current file never produced FILE_LOADED, retry it now that a
        // surface exists. No-op when the file already loaded (rotation, etc.).
        if (mpvInitialized && !isReleased && !fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            load(currentQueue, currentMediaIndex, cachedPositionMs)
            play()
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
    }

    /**
     * SYNCHRONOUS detach barrier:
     * Set vo=null and detach the surface before returning so that libmpv terminates its
     * native rendering window handle before surfaceDestroyed returns.
     * Background audio continues playing with vo=null.
     */
    override fun detachSurface() {
        synchronized(surfaceLock) {
            currentSurface = null
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
        if (items.isEmpty()) return
        fileLoadedForCurrentItem = false
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
                engineListener?.onMediaItemTransition(targetIndex, false)
            }
            return
        }

        applyHwdecForMode(decoderMode)

        activeMediaId = targetItem.id
        activeMediaUri = targetItem.uri

        // Issue #102: Queue pending external subtitles to dispatch only on MPV_EVENT_FILE_LOADED
        val subs = mutableListOf<PendingSubtitle>()
        targetItem.subtitleUri?.let { subUri ->
            subs.add(PendingSubtitle(uri = subUri, flag = "select"))
        }
        targetItem.externalSubtitles.forEach { ext ->
            subs.add(PendingSubtitle(uri = ext.uri, flag = "auto", title = ext.name))
        }
        pendingSubtitles = if (subs.isNotEmpty()) {
            PendingSubtitles(mediaUri = targetItem.uri, mediaId = targetItem.id, subtitles = subs)
        } else {
            null
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
                mpv.command("loadfile", targetItem.uri, "replace", "-1", "start=$startSec")
            } else {
                mpv.command("loadfile", targetItem.uri, "replace")
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
            engineListener?.onMediaItemTransition(targetIndex, false)
        }
    }

    override fun play() {
        try {
            mpv.setPropertyBoolean("pause", false)
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

    fun setAspectRatio(mode: AspectRatioMode) {
        try {
            when (mode) {
                AspectRatioMode.FIT -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.FILL, AspectRatioMode.ZOOM -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 1.0)
                }
                AspectRatioMode.SIXTEEN_NINE -> {
                    mpv.setPropertyString("video-aspect-override", "16:9")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.FOUR_THREE -> {
                    mpv.setPropertyString("video-aspect-override", "4:3")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.ORIGINAL -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyDouble("panscan", 0.0)
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
                            videoIds.getOrNull(groupIndex) ?: videoIds.getOrNull(trackIndex)
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
        val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            ?: tracks.getOrNull(groupIndex)
        if (target != null) {
            try { mpv.setPropertyString("aid", target.trackIndex.toString()) } catch (_: Exception) {}
        } else {
            try { mpv.setPropertyString("aid", "no") } catch (_: Exception) {}
        }
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        if (groupIndex == null || trackIndex == null) {
            try { mpv.setPropertyString("sid", "no") } catch (_: Exception) {}
        } else {
            val tracks = getSubtitleTracks()
            val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
                ?: tracks.getOrNull(groupIndex)
            if (target != null) {
                try { mpv.setPropertyString("sid", target.trackIndex.toString()) } catch (_: Exception) {}
            } else {
                try { mpv.setPropertyString("sid", "no") } catch (_: Exception) {}
            }
        }
    }

    override fun replaceMediaItem(index: Int, media: PlayableMedia) {
        if (index in currentQueue.indices) {
            val updated = currentQueue.toMutableList()
            updated[index] = media
            currentQueue = updated

            val pending = pendingLoad
            if (pending != null && index == pending.second) {
                pendingLoad = Triple(updated, pending.second, pending.third)
            }

            if (index == currentMediaIndex) {
                if (fileLoadedForCurrentItem) {
                    media.externalSubtitles.forEach { ext ->
                        try {
                            mpv.command("sub-add", ext.uri, "select", ext.name)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed adding external subtitle ${ext.name}", e)
                        }
                    }
                } else {
                    // Queue for dispatch when FILE_LOADED arrives
                    val subs = mutableListOf<PendingSubtitle>()
                    media.subtitleUri?.let { subUri ->
                        subs.add(PendingSubtitle(uri = subUri, flag = "select"))
                    }
                    media.externalSubtitles.forEach { ext ->
                        subs.add(PendingSubtitle(uri = ext.uri, flag = "select", title = ext.name))
                    }
                    pendingSubtitles = if (subs.isNotEmpty()) {
                        PendingSubtitles(
                            mediaUri = media.uri,
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
        activeMediaId = null
        activeMediaUri = null
        loadIssued = false
        lastMpvEvent = "none"
        try {
            mpv.command("stop")
            cachedIsPlaying = false
            cachedPlaybackState = PlayerEngine.STATE_IDLE
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MPV", e)
        }
    }

    override fun release() {
        pendingLoad = null
        pendingSeekMs = null
        pendingSubtitles = null
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
    }

    override fun setDecoderMode(mode: DecoderMode) {
        if (decoderMode == mode) return
        applyHwdecForMode(mode)
        // A live hwdec property change only affects the NEXT decoder init, so
        // the current stream would keep its old decoder and the pill would go
        // stale. MX restarts playback with the newly chosen decoder — do the
        // same: reload the current item at its position. No-op before any load.
        if (mpvInitialized && !isReleased && fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            val pos = cachedPositionMs
            load(currentQueue, currentMediaIndex, pos)
            play()
        }
    }

    override val currentPosition: Long
        get() = cachedPositionMs

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
            val next = currentMediaIndex + 1
            load(currentQueue, next, 0L)
            play()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem) {
            val prev = currentMediaIndex - 1
            load(currentQueue, prev, 0L)
            play()
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
                val label = title?.ifBlank { null } ?: "Subtitle $id ($lang)"
                subtitleList.add(
                    SubtitleTrackInfo(
                        groupIndex = subGroup,
                        trackIndex = id,
                        label = label,
                        language = lang,
                        isSelected = selected,
                        isExternal = external,
                    )
                )
                subGroup++
            }
        }
        return subtitleList
    }
}
