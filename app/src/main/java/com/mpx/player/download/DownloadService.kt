package com.mpx.player.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service for active downloads (issue #343). Started by
 * [MpxDownloader.doYourWork] when a download job actually launches,
 * it shows one progress notification for the running queue and posts
 * a completion/failure notification per finished download, then stops
 * itself when the queue empties. Tied to the downloader singleton,
 * not to the Download tab — downloads keep running (and the
 * notification keeps showing) after the user leaves the tab, and the
 * service never runs while no download is active.
 *
 * Shape follows [com.mpx.player.playback.PlaybackService]: a started
 * (not bound) service that observes a StateFlow and rebuilds its
 * notification only when something displayed actually changed
 * (issue #284 pattern). Seal's `DownloadService` is a bound service
 * whose only job is `startForeground` with a static notification;
 * MPx's version owns the progress/completion logic instead, because
 * MPx has no bound-service plumbing for downloads.
 *
 * Honest limits: the downloads themselves run in the app process
 * (yt-dlp is a child process of it), so if Android kills the process
 * the downloads die with it — the foreground service only raises the
 * process priority and gives the user a visible signal, it is not a
 * separate-process guarantee. On API 34+ a dataSync foreground
 * service is also time-boxed by the platform (6h per 24h); a
 * download queue is far shorter than that.
 */
class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var downloader: MpxDownloader
    private lateinit var settingsRepository: AppSettingsRepository
    private var isForegroundActive = false

    // Issue #284 pattern: the values the notification actually
    // displays. Progress ticks arrive on every yt-dlp progress line;
    // rebuilding the notification (and its PendingIntents) on each one
    // would churn for nothing — a rebuild happens only when the
    // title, text, whole-percent progress, or cancel target changes.
    private var lastSnapshot: ProgressSnapshot? = null

    /** Statuses at the last emission, for terminal-transition diffing. */
    private var previousStatuses: Map<String, DownloadStatus> = emptyMap()
    private var baselineTaken = false

    override fun onCreate() {
        super.onCreate()
        downloader = MpxDownloader.getInstance(applicationContext)
        settingsRepository = SharedPrefsAppSettingsRepository(applicationContext)
        DownloadNotificationUtil.createNotificationChannel(this)
        serviceScope.launch {
            downloader.activeDownloads.collectLatest { items ->
                onDownloadsChanged(items)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Go foreground immediately — the observer refines the
                // notification a moment later, but startForeground must
                // not wait for a flow emission.
                // Issue #404: with notifications off, the foreground
                // notification is the static one — Android requires a
                // notification for the service to run, so it cannot be
                // removed entirely; it just never shows progress.
                val snapshot = progressSnapshot(downloader.activeDownloads.value)
                val notification = if (snapshot != null && notificationsEnabled()) {
                    lastSnapshot = snapshot
                    DownloadNotificationUtil.buildProgressNotification(
                        this,
                        snapshot.title,
                        snapshot.text,
                        snapshot.progressPercent,
                        snapshot.cancelId,
                    )
                } else {
                    DownloadNotificationUtil.buildProgressNotification(
                        this,
                        getString(R.string.download_notification_channel_name),
                        getString(R.string.download_notification_downloading),
                        -1,
                        null,
                    )
                }
                ensureForeground(notification)
            }
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let { downloader.cancelDownload(it) }
            }
            ACTION_STOP -> {
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Issue #404: read fresh — the toggle takes effect without
     * restarting a download.
     */
    private fun notificationsEnabled(): Boolean =
        settingsRepository.load().downloadNotifications

    private fun onDownloadsChanged(items: List<ActiveDownload>) {
        val notificationsOn = notificationsEnabled()
        // Terminal transitions since the last emission. The first
        // emission is the baseline — restored FAILED rows (issue #342,
        // "interrupted" after a kill) must not re-notify on restart.
        if (!baselineTaken) {
            baselineTaken = true
        } else if (notificationsOn) {
            for (item in items) {
                val prev = previousStatuses[item.id]
                if (prev != null && prev != item.status) {
                    val id = DownloadNotificationUtil.notificationIdFor(item.id)
                    when (item.status) {
                        DownloadStatus.DONE ->
                            DownloadNotificationUtil.notifyFinished(this, id, item.title)
                        DownloadStatus.FAILED ->
                            DownloadNotificationUtil.notifyFailed(this, id, item.title)
                        else -> {}
                    }
                }
            }
        }
        previousStatuses = items.associate { it.id to it.status }

        val snapshot = progressSnapshot(items)
        if (snapshot == null) {
            // Queue empty: the completion/failure notifications above
            // are posted under their own ids, so they survive the
            // foreground notification being removed here.
            lastSnapshot = null
            if (isForegroundActive) {
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
            return
        }
        // Issue #404: with notifications off, the static foreground
        // notification stays as-is and nothing else is posted — but
        // the empty-queue stop above still runs, so the service does
        // not linger after the queue drains.
        if (!notificationsOn) return
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        val notification = DownloadNotificationUtil.buildProgressNotification(
            this,
            snapshot.title,
            snapshot.text,
            snapshot.progressPercent,
            snapshot.cancelId,
        )
        if (isForegroundActive) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(
                    DownloadNotificationUtil.SERVICE_NOTIFICATION_ID,
                    notification,
                )
        } else {
            ensureForeground(notification)
        }
    }

    /**
     * One row for the whole queue: a single download shows its title
     * and percent; several show "Downloading N files" with the first
     * running title as the text and the mean of the known percents.
     * Null when nothing is queued or running.
     */
    private fun progressSnapshot(items: List<ActiveDownload>): ProgressSnapshot? {
        val active = items.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
        if (active.isEmpty()) return null
        val first = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
            ?: active.first()
        val known = active.mapNotNull { if (it.progress >= 0f) it.progress else null }
        val percent = if (known.isEmpty()) {
            -1
        } else {
            (known.sum() / known.size).roundToInt().coerceIn(0, 100)
        }
        return if (active.size == 1) {
            ProgressSnapshot(
                title = first.title,
                text = if (percent < 0) {
                    getString(R.string.download_notification_downloading)
                } else {
                    getString(R.string.download_notification_downloading_percent, percent)
                },
                progressPercent = percent,
                cancelId = first.id,
            )
        } else {
            ProgressSnapshot(
                title = getString(
                    R.string.download_notification_downloading_files,
                    active.size,
                ),
                text = first.title,
                progressPercent = percent,
                cancelId = first.id,
            )
        }
    }

    private fun ensureForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadNotificationUtil.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(
                    DownloadNotificationUtil.SERVICE_NOTIFICATION_ID,
                    notification,
                )
            }
            isForegroundActive = true
        } catch (_: Exception) {
        }
    }

    private fun stopForegroundInternal(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH,
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
        if (removeNotification) {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(DownloadNotificationUtil.SERVICE_NOTIFICATION_ID)
        }
        isForegroundActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        stopForegroundInternal(removeNotification = true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** The values the progress notification actually displays. */
    private data class ProgressSnapshot(
        val title: String,
        val text: String,
        /** 0..100, -1 while every active download is still sizing. */
        val progressPercent: Int,
        /** The download the notification's Cancel action cancels. */
        val cancelId: String?,
    )

    companion object {
        const val ACTION_START = "com.mpx.player.download.ACTION_START"
        const val ACTION_CANCEL = "com.mpx.player.download.ACTION_CANCEL"
        const val ACTION_STOP = "com.mpx.player.download.ACTION_STOP"
        const val ACTION_OPEN_DOWNLOADS = "com.mpx.player.download.ACTION_OPEN_DOWNLOADS"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        /**
         * Starts the service; a no-op delivery when it is already
         * running (onStartCommand just re-asserts foreground). The
         * service stops itself when the queue empties, so the caller
         * never needs a matching stop.
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
