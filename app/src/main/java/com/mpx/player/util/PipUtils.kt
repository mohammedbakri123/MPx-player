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
     * - Aspect ratio follows the VIDEO's real display size whenever it is
     *   known (issue #225) — a portrait video must not get a 16:9 window
     *   just because the size setting is Medium (the default). The size
     *   setting only controls the source-rect hint width below, not the
     *   aspect. Unknown dims (audio, or no video loaded yet) fall back
     *   to 16:9.
     * - [AppSettings.popupPlaySize] mapped to initial source rect hints
     * - [AppSettings.popupPlayControls] mapped to [RemoteAction] sets (Prev/Next, Seek10, or Play/Pause only)
     * - Auto-enter on Android 12+ (API 31+) only for a video that is
     *   actually playing (issue #225)
     */
    fun buildPipParams(
        context: Context,
        settings: AppSettings = SharedPrefsAppSettingsRepository(context).load(),
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        isVideo: Boolean = false,
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()

        // 1. Aspect Ratio: the video's real display size whenever known.
        // Android enforces PiP aspect ratio between 1:2.39 (0.418) and 2.39:1 (2.39)
        val rational = if (videoWidth > 0 && videoHeight > 0) {
            val ratio = (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(1f / 2.39f, 2.39f)
            Rational((ratio * 1000).toInt(), 1000)
        } else {
            Rational(16, 9)
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

        // 4. Android 12+ (API 31+) platform integration.
        // Issue #225: arm system auto-enter ONLY for a video that is
        // actually playing. The old unconditional `popupPlayEnabled`
        // armed it for paused/audio sessions too, so pressing Home
        // auto-entered PiP past MainActivity's onUserLeaveHint gate
        // (#206: playing + video + Player route) — the gate alone was
        // not enough, because the armed params outlived the session
        // that set them. The Player route refreshes these params on
        // every play/pause/item change, so the armed flag never goes
        // stale; callers that cannot know the playback state (settings
        // saves, test entries) default to disarmed, and the explicit
        // onUserLeaveHint entry still handles Home-press PiP (#98).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(settings.popupPlayEnabled && isPlaying && isVideo)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    /**
     * Attempts to enter Picture-in-Picture mode on supported Android 8.0+ devices.
     * Returns true if PiP mode was successfully entered, or false otherwise.
     *
     * Takes the [Activity] itself, not a [Context]: PipUtils cannot resolve
     * an Activity from a Context here — MainActivity provides a detached
     * localized configuration context as LocalContext, so the old
     * context.findActivity() walk returned null and PiP entry silently
     * returned false (issue #198, same pattern as #179). Callers resolve
     * the Activity the way #179 does (LocalView.current.context as?
     * Activity) and pass it in; null means genuinely no Activity and
     * entry fails the same way it did before.
     *
     * Issue #225: pass the REAL playback state — [isPlaying], the video
     * display size, and [isVideo]. The old defaults (playing, 0x0) lied:
     * a paused entry showed a "Pause" action and a portrait video got a
     * 16:9 window. [isVideo] also gates system auto-enter (see
     * [buildPipParams]); it defaults to false so callers that cannot know
     * the playback state never arm auto-enter for a paused/audio session.
     */
    fun enterPip(
        activity: Activity?,
        settings: AppSettings? = null,
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        isVideo: Boolean = false,
    ): Boolean {
        if (activity == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                return try {
                    val realSettings = settings ?: SharedPrefsAppSettingsRepository(activity).load()
                    val params = buildPipParams(activity, realSettings, isPlaying, videoWidth, videoHeight, isVideo)
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
     * Issue #225: pass the real playback state (see [enterPip]) — this is
     * how the Play/Pause action label follows play/pause changes while in
     * PiP, and how the armed auto-enter flag is kept in sync with reality.
     */
    fun updatePipParams(
        activity: Activity,
        settings: AppSettings? = null,
        isPlaying: Boolean = true,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        isVideo: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val realSettings = settings ?: SharedPrefsAppSettingsRepository(activity).load()
                val params = buildPipParams(activity, realSettings, isPlaying, videoWidth, videoHeight, isVideo)
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
