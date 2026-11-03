package com.mpx.player.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility for determining required media permissions across Android versions.
 * Android 13+ (API 33+) requires granular READ_MEDIA_VIDEO and READ_MEDIA_AUDIO.
 * Android 12 and below (API 24..32) requires READ_EXTERNAL_STORAGE.
 */
object MediaPermissions {

    fun videoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun audioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasVideoPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            videoPermission(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            audioPermission(),
        ) == PackageManager.PERMISSION_GRANTED
    }
}
