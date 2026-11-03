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
        subtitleStyle = prefs.getString("subtitle_style", "White") ?: "White",
        appTheme = prefs.getString("app_theme", "Midnight") ?: "Midnight",
        popupPlayEnabled = prefs.getBoolean("popup_play_enabled", true),
        popupPlayControls = prefs.getString("popup_play_controls", "PrevNext") ?: "PrevNext",
        popupPlaySize = prefs.getString("popup_play_size", "Medium") ?: "Medium",
        popupPlayPosition = prefs.getString("popup_play_position", "BottomRight") ?: "BottomRight",
        popupPlaySnapToEdge = prefs.getBoolean("popup_play_snap_to_edge", true),
        popupPlayCloseOnDone = prefs.getBoolean("popup_play_close_on_done", true),
        appLanguage = prefs.getString("app_language", "system") ?: "system",
        showAllFolders = prefs.getBoolean("show_all_folders", false),
        folderViewMode = prefs.getString("folder_view_mode", "LIST") ?: "LIST",
        localViewMode = prefs.getString("local_view_mode", "LIST") ?: "LIST",
        separateViewLayout = prefs.getBoolean("separate_view_layout", true),
        musicViewMode = prefs.getString("music_view_mode", "LIST") ?: "LIST",
    )

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("show_hidden_files", settings.showHiddenFiles)
            .putBoolean("show_thumbnails", settings.showThumbnails)
            .putBoolean("resume_playback", settings.resumePlayback)
            .putBoolean("backgroundPlay", settings.backgroundPlay)
            .putBoolean("background_play", settings.backgroundPlay)
            .putBoolean("gestures_enabled", settings.gesturesEnabled)
            .putString("decoder_mode", settings.decoderMode)
            .putBoolean("audio_boost", settings.audioBoost)
            .putString("subtitle_size", settings.subtitleSize)
            .putString("subtitle_style", settings.subtitleStyle)
            .putString("app_theme", settings.appTheme)
            .putBoolean("popup_play_enabled", settings.popupPlayEnabled)
            .putString("popup_play_controls", settings.popupPlayControls)
            .putString("popup_play_size", settings.popupPlaySize)
            .putString("popup_play_position", settings.popupPlayPosition)
            .putBoolean("popup_play_snap_to_edge", settings.popupPlaySnapToEdge)
            .putBoolean("popup_play_close_on_done", settings.popupPlayCloseOnDone)
            .putString("app_language", settings.appLanguage)
            .putBoolean("show_all_folders", settings.showAllFolders)
            .putString("folder_view_mode", settings.folderViewMode)
            .putString("local_view_mode", settings.localViewMode)
            .putBoolean("separate_view_layout", settings.separateViewLayout)
            .putString("music_view_mode", settings.musicViewMode)
            .apply()
    }

    override fun getFolderViewMode(folderId: String): String {
        val overrideKey = "folder_view_mode_$folderId"
        val override = prefs.getString(overrideKey, null)
        if (!override.isNullOrBlank()) {
            return override
        }
        return prefs.getString("folder_view_mode", "LIST") ?: "LIST"
    }

    override fun setFolderViewMode(folderId: String, mode: String) {
        val overrideKey = "folder_view_mode_$folderId"
        prefs.edit().putString(overrideKey, mode).apply()
    }

    override fun clearFolderViewModeOverride(folderId: String) {
        val overrideKey = "folder_view_mode_$folderId"
        prefs.edit().remove(overrideKey).apply()
    }

    override fun hasFolderViewModeOverride(folderId: String): Boolean {
        val overrideKey = "folder_view_mode_$folderId"
        return prefs.contains(overrideKey)
    }
}
