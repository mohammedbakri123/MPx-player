package com.mpx.player.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
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
 *   mediacodec (HW) -> mediacodec-copy (HW+) -> hwdec=no (SW) -> hard failure fallback to Media3
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
    private var cachedPlaybackState: Int = Player.STATE_IDLE
    private var cachedSpeed: Float = 1.0f

    private var activeHwdec: String = ""
    private var activeVideoCodec: String = ""

    /** True once libmpv confirms the current item actually loaded (FILE_LOADED).
     * Stays false when loadfile fails outright — the key signal that distinguishes
     * "mpv never started this file" from a mid-playback decoder error. */
    private var fileLoadedForCurrentItem: Boolean = false
    /** Last error/warning log lines from libmpv (capped); surfaced when a file fails to load. */
    private val lastErrorLogs: ArrayDeque<String> = ArrayDeque()
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
        // throw here made the manager silently fall back to Media3 while the
        // setting still said MPV. Capture it and report it via the listener.
        try {
            initializeMpv(initialDecoderMode)
        } catch (e: Exception) {
            Log.e(TAG, "MPV init failed", e)
            pendingInitError = e
        } catch (e: Throwable) {
            Log.e(TAG, "MPV init failed (native)", e)
            pendingInitError = Exception("MPV native init failed: ${e.message}", e)
        }
    }

    private fun initializeMpv(mode: DecoderMode) {
        val configDir = File(appContext.filesDir, "mpv").apply { mkdirs() }
        val cacheDir = File(appContext.cacheDir, "mpv_cache").apply { mkdirs() }

        // Configure fontconfig and clean up bundled subfont.ttf (Arabic subtitles support)
        setupArabicSubtitlesAndFontconfig(appContext, configDir)

        mpv.create(appContext)

        // Set configuration options before init()
        mpv.setOptionString("config", "yes")
        mpv.setOptionString("config-dir", configDir.absolutePath)
        mpv.setOptionString("gpu-shader-cache-dir", cacheDir.absolutePath)
        mpv.setOptionString("icc-cache-dir", cacheDir.absolutePath)
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("ytdl", "no")

        // Subtitle configuration: use system fonts via fontconfig.
        // The font itself comes from saved settings so the first frame already
        // matches; the full option set is applied post-init below.
        mpv.setOptionString("sub-font-provider", "fontconfig")
        mpv.setOptionString("sub-fonts-dir", "/system/fonts")
        mpv.setOptionString("sub-font", subtitleMpvOptions(loadSavedSubtitleSettings())["sub-font"] ?: "sans-serif")
        mpv.setOptionString("sub-fix-timing", "yes")
        mpv.setOptionString("sub-auto", "fuzzy")

        val cacertFile = File(appContext.filesDir, "cacert.pem")
        if (cacertFile.exists()) {
            mpv.setOptionString("tls-ca-file", cacertFile.absolutePath)
        }

        // Apply hardware decoding mode
        applyHwdecForMode(mode)

        mpv.init()
        mpvInitialized = true

        // Post-init options
        mpv.setOptionString("force-window", "yes")

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
     * safe to call whenever settings change mid-playback. No-op before init
     * or after release.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        if (!mpvInitialized || isReleased) return
        try {
            subtitleMpvOptions(settings).forEach { (key, value) ->
                mpv.setOptionString(key, value)
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
        val hwdecVal = when (mode) {
            DecoderMode.HW -> "mediacodec"
            DecoderMode.HW_PLUS -> "mediacodec-copy"
            DecoderMode.SW -> "no"
        }
        try {
            mpv.setPropertyString("hwdec", hwdecVal)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set hwdec option to $hwdecVal", e)
        }
    }

    /**
     * Decoder chain fallback:
     * hwdec mediacodec (HW) -> mediacodec-copy (HW+) -> hwdec=no (SW) -> fall back to Media3PlayerEngine
     * on hard failure, surfacing a real error naming the codec (never a silent stall).
     */
    private fun attemptDecoderFallback(reason: String) {
        val codec = mpv.getPropertyString("video-codec") ?: activeVideoCodec.ifBlank { "unknown" }
        when (currentDecoderStage) {
            DecoderStage.HW -> {
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
                    cachedPlaybackState = Player.STATE_ENDED
                    mainHandler.post {
                        engineListener?.onPlaybackStateChanged(Player.STATE_ENDED)
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
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                fileLoadedForCurrentItem = true
                cachedPlaybackState = Player.STATE_READY
                mainHandler.post {
                    engineListener?.onPlaybackStateChanged(Player.STATE_READY)
                    engineListener?.onTracksChanged()
                    notifyDecoderChanged()
                }
            }
            MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                notifyDecoderChanged()
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
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
                    ).joinToString("; ").ifBlank { "unknown libmpv error" }
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
        synchronized(surfaceLock) {
            currentSurface = surface
            try {
                mpv.attachSurface(surface)
                mpv.setOptionString("force-window", "yes")
                mpv.setPropertyString("vo", "gpu")
            } catch (e: Exception) {
                Log.e(TAG, "Error in attachSurface", e)
            }
        }
        // Load-before-surface recovery: playback is normally started from the
        // library BEFORE the player screen (and its SurfaceView) exists, so
        // the first loadfile can run with no video output attached and die.
        // If that load never produced FILE_LOADED, retry it now that a
        // surface exists. No-op when the file already loaded (rotation, etc.).
        if (mpvInitialized && !isReleased && !fileLoadedForCurrentItem && currentQueue.isNotEmpty()) {
            load(currentQueue, currentMediaIndex, cachedPositionMs)
            play()
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

        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        currentMediaIndex = targetIndex
        val targetItem = items[targetIndex]

        applyHwdecForMode(decoderMode)

        try {
            mpv.command("playlist-clear")

            val startSec = if (resumeMs > 0L) (resumeMs / 1000.0).toString() else "0"
            mpv.command("loadfile", targetItem.uri, "replace", "start=$startSec")

            // Append subsequent items to playlist
            for (i in (targetIndex + 1) until items.size) {
                mpv.command("loadfile", items[i].uri, "append")
            }

            // Append prior items to playlist
            for (i in 0 until targetIndex) {
                mpv.command("loadfile", items[i].uri, "append")
            }

            // Load external subtitles
            targetItem.subtitleUri?.let { subUri ->
                mpv.command("sub-add", subUri, "select")
            }
            targetItem.externalSubtitles.forEach { ext ->
                mpv.command("sub-add", ext.uri, "auto", ext.name)
            }
        } catch (e: Exception) {
            // A loadfile command failure is NOT a decoder-stage failure: stepping
            // HW->HW+->SW here only delayed the error. Report it directly so the
            // manager falls back to Media3 with a real message.
            Log.e(TAG, "Error loading media items in MPV", e)
            mainHandler.post {
                engineListener?.onError(
                    MpvPlaybackException("MPV could not load this file: ${e.message}")
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
                    mpv.setPropertyString("video-aspect-override", "-1")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.FILL, AspectRatioMode.ZOOM -> {
                    mpv.setPropertyString("video-aspect-override", "-1")
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
                    mpv.setPropertyString("video-aspect-override", "-1")
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
                // Video track selection placeholder
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

            if (index == currentMediaIndex) {
                media.externalSubtitles.forEach { ext ->
                    try {
                        mpv.command("sub-add", ext.uri, "select", ext.name)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed adding external subtitle ${ext.name}", e)
                    }
                }
            }
        }
    }

    override fun stop() {
        try {
            mpv.command("stop")
            cachedIsPlaying = false
            cachedPlaybackState = Player.STATE_IDLE
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MPV", e)
        }
    }

    override fun release() {
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
                        isSupported = true,
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
