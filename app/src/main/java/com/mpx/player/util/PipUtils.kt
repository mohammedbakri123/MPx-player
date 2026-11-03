package com.mpx.player.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Rational
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackService

/**
 * Utility functions for system Picture-in-Picture (PiP) handling,
 * device capability detection, and system equalizer intent delegation.
 */
object PipUtils {

    /**
     * Checks whether the current device and operating system support
     * Android Picture-in-Picture mode.
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * Builds [PictureInPictureParams] honoring user preferences:
     * - [AppSettings.popupPlaySize] mapped to sanctioned aspect ratios (within Android's 1:2.39 to 2.39:1 boundary)
     * - [AppSettings.popupPlayPosition] mapped to initial source rect hints
     * - [AppSettings.popupPlayControls] mapped to [RemoteAction] sets (Prev/Next, Seek10, or Play/Pause only)
     * - Auto-enter enabled on Android 12+ (API 31+)
     */
    fun buildPipParams(
        context: Context,
        settings: AppSettings = SharedPrefsAppSettingsRepository(context).load(),
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()

        // 1. Aspect Ratio based on popupPlaySize
        // Android enforces PiP aspect ratio between 1:2.39 (0.418) and 2.39:1 (2.39)
        val rational = when (settings.popupPlaySize) {
            "Small" -> Rational(16, 9)
            "Large" -> Rational(16, 9)
            "Auto" -> {
                if (videoWidth > 0 && videoHeight > 0) {
                    val ratio = (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(1f / 2.39f, 2.39f)
                    Rational((ratio * 1000).toInt(), 1000)
                } else {
                    Rational(16, 9)
                }
            }
            else -> Rational(16, 9) // "Medium" (Default)
        }
        try {
            builder.setAspectRatio(rational)
        } catch (_: Exception) {}

        // 2. Source Rect Hint based on popupPlayPosition preference
        try {
            val displayMetrics = context.resources.displayMetrics
            val screenW = displayMetrics.widthPixels
            val screenH = displayMetrics.heightPixels
            val targetW = when (settings.popupPlaySize) {
                "Small" -> (screenW * 0.45f).toInt()
                "Large" -> (screenW * 0.85f).toInt()
                else -> (screenW * 0.65f).toInt() // "Medium"
            }
            val targetH = (targetW * 9) / 16

            val rect = when (settings.popupPlayPosition) {
                "TopLeft" -> Rect(16, 64, 16 + targetW, 64 + targetH)
                "TopRight" -> Rect(screenW - targetW - 16, 64, screenW - 16, 64 + targetH)
                "BottomLeft" -> Rect(16, screenH - targetH - 128, 16 + targetW, screenH - 128)
                "Center" -> Rect((screenW - targetW) / 2, (screenH - targetH) / 2, (screenW + targetW) / 2, (screenH + targetH) / 2)
                else -> Rect(screenW - targetW - 16, screenH - targetH - 128, screenW - 16, screenH - 128) // "BottomRight" / "RememberLast"
            }
            builder.setSourceRectHint(rect)
        } catch (_: Exception) {}

        // 3. RemoteActions based on popupPlayControls
        val actions = mutableListOf<RemoteAction>()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        when (settings.popupPlayControls) {
            "Seek10" -> {
                // Seek back 10s
                val rewIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_SEEK_BACKWARD_10
                }
                val rewPending = PendingIntent.getService(context, 101, rewIntent, flags)
                val rewIcon = Icon.createWithResource(context, R.drawable.ic_notification_prev)
                actions.add(RemoteAction(rewIcon, "Rewind 10s", "Rewind 10s", rewPending))

                // Play / Pause
                val toggleIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_TOGGLE
                }
                val togglePending = PendingIntent.getService(context, 102, toggleIntent, flags)
                val toggleIcon = Icon.createWithResource(
                    context,
                    if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                )
                actions.add(RemoteAction(toggleIcon, if (isPlaying) "Pause" else "Play", if (isPlaying) "Pause" else "Play", togglePending))

                // Fast Forward 10s
                val ffIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_SEEK_FORWARD_10
                }
                val ffPending = PendingIntent.getService(context, 103, ffIntent, flags)
                val ffIcon = Icon.createWithResource(context, R.drawable.ic_notification_next)
                actions.add(RemoteAction(ffIcon, "Forward 10s", "Forward 10s", ffPending))
            }
            "PlayPauseOnly" -> {
                val toggleIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_TOGGLE
                }
                val togglePending = PendingIntent.getService(context, 102, toggleIntent, flags)
                val toggleIcon = Icon.createWithResource(
                    context,
                    if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                )
                actions.add(RemoteAction(toggleIcon, if (isPlaying) "Pause" else "Play", if (isPlaying) "Pause" else "Play", togglePending))
            }
            else -> { // "PrevNext" (Default)
                val prevIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_PREVIOUS
                }
                val prevPending = PendingIntent.getService(context, 104, prevIntent, flags)
                val prevIcon = Icon.createWithResource(context, R.drawable.ic_notification_prev)
                actions.add(RemoteAction(prevIcon, "Previous", "Previous", prevPending))

                val toggleIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_TOGGLE
                }
                val togglePending = PendingIntent.getService(context, 102, toggleIntent, flags)
                val toggleIcon = Icon.createWithResource(
                    context,
                    if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                )
                actions.add(RemoteAction(toggleIcon, if (isPlaying) "Pause" else "Play", if (isPlaying) "Pause" else "Play", togglePending))

                val nextIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_NEXT
                }
                val nextPending = PendingIntent.getService(context, 105, nextIntent, flags)
                val nextIcon = Icon.createWithResource(context, R.drawable.ic_notification_next)
                actions.add(RemoteAction(nextIcon, "Next", "Next", nextPending))
            }
        }
        builder.setActions(actions)

        // 4. Android 12+ (API 31+) platform integration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(settings.backgroundPlay && settings.popupPlayEnabled)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    /**
     * Attempts to enter Picture-in-Picture mode on supported Android 8.0+ devices.
     * Returns true if PiP mode was successfully entered, or false otherwise.
     */
    fun enterPip(
        context: Context,
        settings: AppSettings? = null,
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? Activity ?: return false
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                return try {
                    val realSettings = settings ?: SharedPrefsAppSettingsRepository(context).load()
                    val params = buildPipParams(activity, realSettings, isPlaying, videoWidth, videoHeight)
                    if (params != null) {
                        activity.enterPictureInPictureMode(params)
                    } else {
                        val rational = Rational(16, 9)
                        val fallbackParams = PictureInPictureParams.Builder()
                            .setAspectRatio(rational)
                            .build()
                        activity.enterPictureInPictureMode(fallbackParams)
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
        return false
    }

    /**
     * Updates ongoing Picture-in-Picture parameters on the given [Activity].
     */
    fun updatePipParams(
        activity: Activity,
        settings: AppSettings? = null,
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val realSettings = settings ?: SharedPrefsAppSettingsRepository(activity).load()
                val params = buildPipParams(activity, realSettings, isPlaying, videoWidth, videoHeight)
                if (params != null) {
                    activity.setPictureInPictureParams(params)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Attempts to launch the platform equalizer control panel (such as MusicFX)
     * attached to the given audio session ID.
     */
    fun launchSystemEqualizer(context: Context, audioSessionId: Int): Boolean {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
