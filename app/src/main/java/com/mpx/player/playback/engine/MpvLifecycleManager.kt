package com.mpx.player.playback.engine

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.MpvLogFile
import com.mpx.player.playback.subtitleMpvOptions
import java.io.File
import java.io.FileOutputStream

/**
 * Manages native libmpv lifecycle: pre-init option configuration, directory setup,
 * bundled assets/fonts/certs, observer attachment, post-init runtime configuration,
 * initialization error capture, and clean teardown (R-7).
 */
internal class MpvLifecycleManager(
    private val appContext: Context,
    private val port: MpvPort,
    private val host: MpvCoreEngine.Host,
    private val eventObserver: MpvPort.EventObserver,
    private val logObserver: MpvPort.LogObserver,
) {
    @Volatile
    var isReleased: Boolean = false
        internal set

    @Volatile
    var mpvInitialized: Boolean = false
        internal set

    internal var pendingInitError: Exception? = null

    internal val eventLock = Any()
    internal val rejectedOptions = ArrayDeque<String>()

    /**
     * Initializes libmpv native instance with configuration and options.
     * Captures init errors into [pendingInitError] instead of throwing out of constructor.
     */
    fun initialize(
        initialDecoderMode: DecoderMode,
        loadSavedSettings: () -> AppSettings,
        applyPostInit: () -> Unit,
        onInitFailed: () -> Unit
    ) {
        try {
            val configDir = File(appContext.filesDir, "mpv").apply { mkdirs() }
            val cacheDir = File(appContext.cacheDir, "mpv_cache").apply { mkdirs() }

            // Configure bundled subfont.ttf and network certificates
            setupSubtitleFontAndCertificates(appContext, configDir)

            // Issue #432: sync the picked mpv config folder before mpv reads its config
            val savedSettings = loadSavedSettings()
            MpvConfigSync.sync(appContext, configDir, savedSettings.mpvConfStorageUri)

            port.create(appContext)

            // Set configuration options before init()
            setOption("config", "yes")
            if (!savedSettings.enableLuaScripts) setOption("load-scripts", "no")
            setOption("config-dir", configDir.absolutePath)
            setOption("gpu-shader-cache-dir", cacheDir.absolutePath)
            setOption("icc-cache-dir", cacheDir.absolutePath)
            setOption("vo", "gpu")
            setOption("gpu-context", "android")
            setOption("opengl-es", "yes")
            setOption("ao", "audiotrack,opensles")
            setOption("audio-buffer", "0.5")
            setOption("keep-open", "yes")
            setOption("ytdl", "no")
            setOption("idle", "yes")

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

            setOption("hwdec", host.hwdecValueFor(initialDecoderMode))

            port.init()
            mpvInitialized = true

            // Post-init options must use the property API
            setRuntimeProperty("force-window", "no")

            applyPostInit()

            observeDefaultProperties()

            port.addObserver(eventObserver)
            port.addLogObserver(logObserver)
        } catch (e: Exception) {
            Log.e(TAG, "MPV init failed", e)
            pendingInitError = e
            onInitFailed()
        } catch (e: Throwable) {
            Log.e(TAG, "MPV init failed (native)", e)
            pendingInitError = Exception("MPV native init failed: ${e.message}", e)
            onInitFailed()
        }
    }

    private fun observeDefaultProperties() {
        port.observeProperty("time-pos", MpvPort.PropertyFormat.DOUBLE)
        port.observeProperty("duration", MpvPort.PropertyFormat.DOUBLE)
        port.observeProperty("demuxer-cache-time", MpvPort.PropertyFormat.DOUBLE)
        port.observeProperty("pause", MpvPort.PropertyFormat.FLAG)
        port.observeProperty("idle-active", MpvPort.PropertyFormat.FLAG)
        port.observeProperty("eof-reached", MpvPort.PropertyFormat.FLAG)
        port.observeProperty("speed", MpvPort.PropertyFormat.DOUBLE)
        port.observeProperty("hwdec-current", MpvPort.PropertyFormat.STRING)
        port.observeProperty("video-codec", MpvPort.PropertyFormat.STRING)
        port.observeProperty("vo", MpvPort.PropertyFormat.STRING)
        port.observeProperty("dwidth", MpvPort.PropertyFormat.INT64)
        port.observeProperty("dheight", MpvPort.PropertyFormat.INT64)
        port.observeProperty("track-list", MpvPort.PropertyFormat.NONE)
    }

    fun setOption(key: String, value: String): Int {
        val rc = try {
            port.setOptionString(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "setOption $key failed", e)
            return -1
        }
        if (rc != 0) {
            val msg = "mpv option rejected: $key=$value (rc=$rc)"
            Log.w(TAG, msg)
            synchronized(rejectedOptions) {
                rejectedOptions.addLast(msg)
                while (rejectedOptions.size > 5) rejectedOptions.removeFirst()
            }
        }
        return rc
    }

    fun setRuntimeProperty(key: String, value: String) {
        try {
            port.setPropertyString(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "setProperty $key failed", e)
        }
    }

    fun rejectedOptionsText(): String =
        synchronized(rejectedOptions) {
            if (rejectedOptions.isEmpty()) "" else "; rejected options: " + rejectedOptions.joinToString(", ")
        }

    /**
     * Releases native mpv resources under [eventLock].
     * Idempotent (D-11, double-release safety).
     */
    @VisibleForTesting
    fun release(onTeardown: () -> Unit = {}) {
        if (isReleased) return
        isReleased = true
        onTeardown()
        synchronized(eventLock) {
            try {
                port.removeObserver(eventObserver)
                port.removeLogObserver(logObserver)
                port.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MPV", e)
            }
        }
        MpvLogFile.close()
    }

    private fun setupSubtitleFontAndCertificates(context: Context, configDir: File) {
        copyAssetIfDifferent(context, "subfont.ttf", File(configDir, "subfont.ttf"))
        copyAssetIfDifferent(context, "subfont.ttf", File(context.filesDir, "subfont.ttf"))

        copyAssetIfDifferent(context, "cacert.pem", File(configDir, "cacert.pem"))
        copyAssetIfDifferent(context, "cacert.pem", File(context.filesDir, "cacert.pem"))

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

    companion object {
        private const val TAG = "MpvLifecycleManager"
    }
}
