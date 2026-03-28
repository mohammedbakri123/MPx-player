package com.example.mpx

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class MainActivity : FlutterActivity() {
    private val methodChannelName = "mpx/downloader/methods"
    private val eventChannelName = "mpx/downloader/events"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeTasks = ConcurrentHashMap<String, CancelToken>()
    private var eventSink: EventChannel.EventSink? = null
    private var pendingSharedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSharedUrl = extractSharedUrl(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sharedUrl = extractSharedUrl(intent)
        if (!sharedUrl.isNullOrBlank()) {
            pendingSharedUrl = sharedUrl
            emitEvent(
                mapOf(
                    "event" to "shared_url",
                    "url" to sharedUrl,
                )
            )
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getDownloaderStatus" -> handleGetDownloaderStatus(result)
                    "ensureBinariesAvailable" -> handleEnsureBinaries(result)
                    "checkForUpdates" -> handleCheckForUpdates(call, result)
                    "consumeSharedUrl" -> handleConsumeSharedUrl(result)
                    "exportDownload" -> handleExportDownload(call, result)
                    "fetchVideoInfo" -> handleFetchVideoInfo(call, result)
                    "startDownload" -> handleStartDownload(call, result)
                    "cancelDownload" -> handleCancelDownload(call, result)
                    else -> result.notImplemented()
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannelName)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })
    }

    private fun handleGetDownloaderStatus(result: MethodChannel.Result) {
        try {
            ensurePythonStarted()
            result.success(
                parseJsonObject(
                    ytDlpModule().callAttr("get_runtime_status_json").toString()
                )
            )
        } catch (error: Exception) {
            result.success(
                mapOf(
                    "ytDlpAvailable" to false,
                    "ffmpegAvailable" to false,
                    "ytDlpPath" to null,
                    "ffmpegPath" to null,
                    "version" to null,
                    "error" to (error.message ?: error.toString()),
                )
            )
        }
    }

    private fun handleEnsureBinaries(result: MethodChannel.Result) {
        handleGetDownloaderStatus(result)
    }

    private fun handleCheckForUpdates(call: MethodCall, result: MethodChannel.Result) {
        val installIfAvailable = call.argument<Boolean>("installIfAvailable") ?: true

        Thread {
            try {
                ensurePythonStarted()
                val payload = ytDlpModule().callAttr(
                    "install_or_update_yt_dlp_json",
                    installIfAvailable,
                ).toString()
                mainHandler.post {
                    result.success(parseJsonObject(payload))
                }
            } catch (error: Exception) {
                mainHandler.post {
                    result.error("update_failed", error.message ?: error.toString(), null)
                }
            }
        }.start()
    }

    private fun handleConsumeSharedUrl(result: MethodChannel.Result) {
        val url = pendingSharedUrl
        pendingSharedUrl = null
        result.success(url)
    }

    private fun handleExportDownload(call: MethodCall, result: MethodChannel.Result) {
        val sourcePath = call.argument<String>("sourcePath")
        if (sourcePath.isNullOrBlank()) {
            result.error("invalid_args", "sourcePath is required", null)
            return
        }

        Thread {
            try {
                val exportedPath = PublicMediaExporter.exportToPublicPath(
                    this,
                    sourcePath,
                    DownloaderPythonBridge.downloadPath(this),
                )
                mainHandler.post {
                    result.success(
                        mapOf(
                            "path" to exportedPath,
                        )
                    )
                }
            } catch (error: Exception) {
                mainHandler.post {
                    result.error("export_failed", error.message ?: error.toString(), null)
                }
            }
        }.start()
    }

    private fun handleFetchVideoInfo(call: MethodCall, result: MethodChannel.Result) {
        val url = call.argument<String>("url")
        val cookiesPath = call.argument<String>("cookiesPath")
        if (url.isNullOrBlank()) {
            result.error("invalid_args", "URL is required", null)
            return
        }

        Thread {
            try {
                ensurePythonStarted()
                val json = ytDlpModule().callAttr(
                    "fetch_video_info",
                    url,
                    cookiesPath,
                ).toString()
                mainHandler.post {
                    result.success(mapOf("json" to json))
                }
            } catch (error: Exception) {
                mainHandler.post {
                    result.error("fetch_failed", error.message ?: error.toString(), null)
                }
            }
        }.start()
    }

    private fun handleStartDownload(call: MethodCall, result: MethodChannel.Result) {
        val taskId = call.argument<String>("taskId")
        val url = call.argument<String>("url")
        val outputPath = call.argument<String>("outputPath")
        val formatSelector = call.argument<String>("formatSelector")
        val cookiesPath = call.argument<String>("cookiesPath")

        if (taskId.isNullOrBlank() || url.isNullOrBlank() || outputPath.isNullOrBlank()) {
            result.error("invalid_args", "Missing required download arguments", null)
            return
        }

        val cancelToken = CancelToken()
        activeTasks[taskId] = cancelToken

        Thread {
            try {
                ensurePythonStarted()
                val emitter = ProgressEmitter(taskId)
                ytDlpModule().callAttr(
                    "download_video",
                    taskId,
                    url,
                    outputPath,
                    formatSelector ?: "best[ext=mp4]/best",
                    cookiesPath,
                    emitter,
                    cancelToken,
                )
            } catch (error: PyException) {
                if (cancelToken.isCancelled()) {
                    emitEvent(
                        mapOf(
                            "taskId" to taskId,
                            "status" to "cancelled",
                            "progress" to 0.0,
                            "logLine" to "Download cancelled",
                        )
                    )
                } else {
                    emitEvent(
                        mapOf(
                            "taskId" to taskId,
                            "status" to "failed",
                            "progress" to 0.0,
                            "logLine" to (error.message ?: error.toString()),
                        )
                    )
                }
            } catch (error: Exception) {
                emitEvent(
                    mapOf(
                        "taskId" to taskId,
                        "status" to "failed",
                        "progress" to 0.0,
                        "logLine" to (error.message ?: error.toString()),
                    )
                )
            } finally {
                activeTasks.remove(taskId)
            }
        }.start()

        result.success(null)
    }

    private fun handleCancelDownload(call: MethodCall, result: MethodChannel.Result) {
        val taskId = call.argument<String>("taskId")
        if (taskId.isNullOrBlank()) {
            result.success(null)
            return
        }

        activeTasks[taskId]?.cancel()
        result.success(null)
    }

    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
    }

    private fun ytDlpModule() = Python.getInstance().getModule("downloader_bridge")

    private fun emitEvent(event: Map<String, Any?>) {
        mainHandler.post {
            eventSink?.success(event)
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) {
            return null
        }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val matcher = Patterns.WEB_URL.matcher(sharedText)
        return if (matcher.find()) matcher.group() else null
    }

    private fun parseJsonObject(json: String): Map<String, Any?> {
        val jsonObject = JSONObject(json)
        val map = mutableMapOf<String, Any?>()
        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = when (val value = jsonObject.get(key)) {
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    inner class ProgressEmitter(private val taskId: String) {
        fun emit(
            status: String,
            progress: Double?,
            speedText: String?,
            etaText: String?,
            logLine: String?,
            filePath: String?,
        ) {
            emitEvent(
                mapOf(
                    "taskId" to taskId,
                    "status" to status,
                    "progress" to (progress ?: 0.0),
                    "speedText" to speedText,
                    "etaText" to etaText,
                    "logLine" to logLine,
                    "filePath" to filePath,
                )
            )
        }
    }
}

class CancelToken {
    @Volatile
    private var cancelled: Boolean = false

    fun cancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean {
        return cancelled
    }
}
