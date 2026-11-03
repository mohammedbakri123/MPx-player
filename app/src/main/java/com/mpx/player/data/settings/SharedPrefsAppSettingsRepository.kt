package com.mpx.player.data.settings

import android.content.Context
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository

/**
 * SharedPreferences implementation. Simple, real persistence with no
 * database dependency, so it does not conflict with the Room worktree.
 */
class SharedPrefsAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val prefs = context.applicationContext
        .getSharedPreferences("mpx_settings", Context.MODE_PRIVATE)

    override fun load(): AppSettings = AppSettings(
        showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
        showThumbnails = prefs.getBoolean("show_thumbnails", true),
        resumePlayback = prefs.getBoolean("resume_playback", true),
        backgroundPlay = prefs.getBoolean("background_play", false),
        gesturesEnabled = prefs.getBoolean("gestures_enabled", true),
        decoderMode = prefs.getString("decoder_mode", "HW") ?: "HW",
        audioBoost = prefs.getBoolean("audio_boost", false),
        subtitleSize = prefs.getString("subtitle_size", "Normal") ?: "Normal",
    )

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("show_hidden_files", settings.showHiddenFiles)
            .putBoolean("show_thumbnails", settings.showThumbnails)
            .putBoolean("resume_playback", settings.resumePlayback)
            .putBoolean("background_play", settings.backgroundPlay)
            .putBoolean("gestures_enabled", settings.gesturesEnabled)
            .putString("decoder_mode", settings.decoderMode)
            .putBoolean("audio_boost", settings.audioBoost)
            .putString("subtitle_size", settings.subtitleSize)
            .apply()
    }
}
