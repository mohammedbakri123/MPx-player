package com.mpx.player.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Helpers for detecting Android TV / Leanback runtime environments.
 */
object TvUtils {

    /**
     * Returns true when running on an Android TV / Leanback device.
     */
    fun isTelevisionDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val currentModeType = uiModeManager?.currentModeType
        val configUiMode = context.resources.configuration.uiMode
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTelevisionConfig(
            uiMode = configUiMode,
            currentModeType = currentModeType,
            hasLeanbackFeature = hasLeanback,
        )
    }

    /**
     * Pure predicate separated from Android services for deterministic unit testing.
     */
    internal fun isTelevisionConfig(
        uiMode: Int,
        currentModeType: Int?,
        hasLeanbackFeature: Boolean,
    ): Boolean {
        if (currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if ((uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return hasLeanbackFeature
    }
}
