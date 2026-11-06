package com.mpx.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mpx.player.MainActivity
import com.mpx.player.R

/**
 * Download notifications (issue #343), adapted from Seal's
 * `NotificationUtil` (JunkFood02/Seal, GPL-3.0,
 * `app/src/main/java/com/junkfood/seal/util/NotificationUtil.kt`):
 * `notifyProgress` (progress bar + cancel action),
 * `finishNotification` (auto-cancel + content intent) and `notifyError`
 * are the same shapes; only the wiring changed — Seal posts through a
 * bound service and a BroadcastReceiver, MPx posts from its own
 * foreground [DownloadService] and the cancel action goes straight
 * back to that service. One IMPORTANCE_DEFAULT channel (Seal has two
 * LOW ones): progress updates use `setOnlyAlertOnce`, so the sound
 * only fires for the completion/failure notification.
 *
 * All text is localised (issue #99 rule). The channel name/description
 * are read once at creation; a locale change does not rename an
 * already-created channel until the app data is cleared — same as
 * every other channel in the app.
 *
 * POST_NOTIFICATIONS is requested at app start (MainActivity); when
 * the user denies it, `notify()` silently shows nothing and downloads
 * still work — only the completion/failure signal is lost. Foreground
 * service notifications are exempt from that permission, so the
 * progress notification still shows.
 */
object DownloadNotificationUtil {

    const val CHANNEL_ID = "mpx_download_channel"

    /** The foreground service's own notification id. */
    const val SERVICE_NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    context.getString(R.string.download_notification_channel_description)
                setShowBadge(false)
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** Stable per-download id for the completion/failure notifications. */
    fun notificationIdFor(downloadId: String): Int = downloadId.hashCode()

    /**
     * Opens the app on the Download tab (MainActivity handles the
     * action the same way it handles the player's open-player intent).
     */
    fun openDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = DownloadService.ACTION_OPEN_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DownloadService.EXTRA_OPEN_DOWNLOADS, true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Seal's `notifyProgress` shape: title + progress bar + Cancel
     * action, ongoing, alerted once. [progressPercent] is 0..100, or
     * -1 while the size is still unknown (indeterminate bar).
     */
    fun buildProgressNotification(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int,
        cancelId: String?,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openDownloadsPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent < 0)
        if (cancelId != null) {
            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, cancelId)
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                notificationIdFor(cancelId),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_close,
                    context.getString(R.string.action_cancel),
                    cancelPendingIntent,
                ),
            )
        }
        return builder.build()
    }

    /** Seal's `finishNotification` shape: auto-cancel, tap opens the tab. */
    fun notifyFinished(context: Context, notificationId: Int, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.download_complete))
            .setContentIntent(openDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, notification)
    }

    /** Seal's `notifyError` shape, without the report action. */
    fun notifyFailed(context: Context, notificationId: Int, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.download_failed))
            .setContentIntent(openDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, notification)
    }
}
