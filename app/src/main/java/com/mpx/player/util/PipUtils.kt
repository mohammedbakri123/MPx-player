package com.mpx.player.util

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Rational

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
     * Attempts to enter Picture-in-Picture mode on supported Android 8.0+ devices.
     * Returns true if PiP mode was successfully entered, or false otherwise.
     */
    fun enterPip(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? Activity ?: return false
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                return try {
                    val rational = Rational(16, 9)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build()
                    activity.enterPictureInPictureMode(params)
                } catch (_: Exception) {
                    false
                }
            }
        }
        return false
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
