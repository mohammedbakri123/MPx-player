package com.example.mpx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class ShareDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val formatSelector = intent?.getStringExtra(EXTRA_FORMAT_SELECTOR)
        if (url.isNullOrBlank() || formatSelector.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        NotificationHelper.ensureChannel(this)
        startForeground(
            NOTIFICATION_ID,
            NotificationHelper.buildProgressNotification(
                context = this,
                title = "Starting download",
                text = "Preparing MPx share download...",
                progress = 0,
                indeterminate = true,
            ),
        )

        val appContext = applicationContext
        Thread {
            val tempDir = File(appContext.cacheDir, "shared_downloads")
            tempDir.mkdirs()
            val tempOutput = File(tempDir, "shared_${System.currentTimeMillis()}.%(ext)s")
            val token = CancelToken()

            try {
                DownloaderPythonBridge.ensureStarted(appContext)
                val notifier = ServiceProgressEmitter(appContext)
                val finalPath = DownloaderPythonBridge.module().callAttr(
                    "download_video",
                    "shared-${System.currentTimeMillis()}",
                    url,
                    tempOutput.absolutePath,
                    formatSelector,
                    DownloaderPythonBridge.cookiesPath(appContext),
                    notifier,
                    token,
                ).toString()

                val exportedPath = PublicMediaExporter.exportToMovies(appContext, finalPath)
                NotificationHelper.showFinishedNotification(
                    context = appContext,
                    title = "Saved to Movies/mpxReels",
                    text = exportedPath,
                    success = true,
                )
            } catch (error: Exception) {
                NotificationHelper.showFinishedNotification(
                    context = appContext,
                    title = "Download failed",
                    text = error.message ?: "Unable to download shared video",
                    success = false,
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }.start()

        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_FORMAT_SELECTOR = "extra_format_selector"
        private const val NOTIFICATION_ID = 44041

        fun createIntent(context: Context, url: String, formatSelector: String): Intent {
            return Intent(context, ShareDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT_SELECTOR, formatSelector)
            }
        }
    }
}

private class ServiceProgressEmitter(private val context: Context) {
    fun emit(
        status: String,
        progress: Double?,
        speedText: String?,
        etaText: String?,
        logLine: String?,
        filePath: String?,
    ) {
        if (status != "downloading") {
            return
        }
        val value = ((progress ?: 0.0) * 100).toInt().coerceIn(0, 100)
        val secondary = listOfNotNull(speedText, etaText).joinToString(" - ")
        NotificationHelper.updateProgressNotification(
            context = context,
            title = if (value >= 100) "Finalizing download" else "Downloading shared video",
            text = secondary.ifEmpty { logLine ?: "Working..." },
            progress = value,
            indeterminate = value == 0,
        )
    }
}

private object NotificationHelper {
    private const val CHANNEL_ID = "share_downloads"
    private const val CHANNEL_NAME = "MPx Downloads"
    private const val NOTIFICATION_ID = 44041

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows MPx shared download progress"
        }
        manager.createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    fun updateProgressNotification(
        context: Context,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(context, title, text, progress, indeterminate),
        )
    }

    fun showFinishedNotification(
        context: Context,
        title: String,
        text: String,
        success: Boolean,
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notification)
    }
}
