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

    override fun load(): AppSettings {
        var tracks = prefs.getBoolean("show_music_tracks_tab", true)
        val playlists = prefs.getBoolean("show_music_playlists_tab", true)
        val albums = prefs.getBoolean("show_music_albums_tab", true)
        val artists = prefs.getBoolean("show_music_artists_tab", true)
        val folders = prefs.getBoolean("show_music_folders_tab", true)

        // Honest fallback: at least one tab must always be kept visible
        if (!tracks && !playlists && !albums && !artists && !folders) {
            tracks = true
        }

        return AppSettings(

        showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
        showThumbnails = prefs.getBoolean("show_thumbnails", true),
        resumePlayback = prefs.getBoolean("resume_playback", true),
        backgroundPlay = if (prefs.contains("background_play")) {
            prefs.getBoolean("background_play", false)
        } else {
            prefs.getBoolean("backgroundPlay", false)
        },
        gesturesEnabled = prefs.getBoolean("gestures_enabled", true),
        decoderMode = prefs.getString("decoder_mode", "HW") ?: "HW",
        playerEngine = prefs.getString("player_engine", "MEDIA3") ?: "MEDIA3",
        subtitleSize = prefs.getString("subtitle_size", "Normal") ?: "Normal",
        subtitleStyle = prefs.getString("subtitle_style", "White") ?: "White",
        subtitleTypeface = prefs.getString("subtitle_typeface", "Default") ?: "Default",
        subtitleTextColor = prefs.getString("subtitle_text_color", "Preset") ?: "Preset",
        subtitleBackgroundColor = prefs.getString("subtitle_bg_color", "Transparent") ?: "Transparent",
        subtitleBorderColor = prefs.getString("subtitle_border_color", "Black") ?: "Black",
        subtitleBorderThickness = prefs.getString("subtitle_border_thickness", "Medium") ?: "Medium",
        subtitleBold = prefs.getBoolean("subtitle_bold", true),
        subtitleShadow = prefs.getBoolean("subtitle_shadow", false),
        subtitleAlignment = prefs.getString("subtitle_alignment", "Bottom") ?: "Bottom",
        subtitleBottomPadding = prefs.getString("subtitle_bottom_padding", "Medium") ?: "Medium",
        subtitleScale = prefs.getString("subtitle_scale", "100") ?: "100",
        subtitleAssOverride = prefs.getString("subtitle_ass_override", "Default") ?: "Default",
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
        fileView = prefs.getBoolean("file_view", false),
        autoScreenRotation = prefs.getBoolean("auto_screen_rotation", true),
        screenOrientation = prefs.getString("screen_orientation", "auto") ?: "auto",
        primarySortField = prefs.getString("primary_sort_field", "DATE") ?: "DATE",
        secondarySortField = prefs.getString("secondary_sort_field", "NAME") ?: "NAME",
        folderPrimarySortField = prefs.getString("folder_primary_sort_field", "DATE") ?: "DATE",
        folderSecondarySortField = prefs.getString("folder_secondary_sort_field", "NAME") ?: "NAME",
        sortReverse = prefs.getBoolean("sort_reverse", false),
        sortSecondaryReverse = prefs.getBoolean("sort_secondary_reverse", false),
        sortNumeric = prefs.getBoolean("sort_numeric", true),
        sortCaseSensitive = prefs.getBoolean("sort_case_sensitive", false),
        sortIgnoreExtension = prefs.getBoolean("sort_ignore_extension", true),
        showFieldSize = prefs.getBoolean("show_field_size", true),
        showFieldDate = prefs.getBoolean("show_field_date", false),
        showFieldDuration = prefs.getBoolean("show_field_duration", true),
        showFieldLocation = prefs.getBoolean("show_field_location", true),
        showFieldCount = prefs.getBoolean("show_field_count", true),
        showMusicTracksTab = tracks,
            showMusicPlaylistsTab = playlists,
            showMusicAlbumsTab = albums,
            showMusicArtistsTab = artists,
            showMusicFoldersTab = folders,
        )
    }

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("show_hidden_files", settings.showHiddenFiles)
            .putBoolean("show_thumbnails", settings.showThumbnails)
            .putBoolean("resume_playback", settings.resumePlayback)
            .putBoolean("backgroundPlay", settings.backgroundPlay)
            .putBoolean("background_play", settings.backgroundPlay)
            .putBoolean("gestures_enabled", settings.gesturesEnabled)
            .putString("decoder_mode", settings.decoderMode)
            .putString("player_engine", settings.playerEngine)
            .putString("subtitle_size", settings.subtitleSize)
            .putString("subtitle_style", settings.subtitleStyle)
            .putString("subtitle_typeface", settings.subtitleTypeface)
            .putString("subtitle_text_color", settings.subtitleTextColor)
            .putString("subtitle_bg_color", settings.subtitleBackgroundColor)
            .putString("subtitle_border_color", settings.subtitleBorderColor)
            .putString("subtitle_border_thickness", settings.subtitleBorderThickness)
            .putBoolean("subtitle_bold", settings.subtitleBold)
            .putBoolean("subtitle_shadow", settings.subtitleShadow)
            .putString("subtitle_alignment", settings.subtitleAlignment)
            .putString("subtitle_bottom_padding", settings.subtitleBottomPadding)
            .putString("subtitle_scale", settings.subtitleScale)
            .putString("subtitle_ass_override", settings.subtitleAssOverride)
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
            .putBoolean("file_view", settings.fileView)
            .putBoolean("auto_screen_rotation", settings.autoScreenRotation)
            .putString("screen_orientation", settings.screenOrientation)
            .putString("primary_sort_field", settings.primarySortField)
            .putString("secondary_sort_field", settings.secondarySortField)
            .putString("folder_primary_sort_field", settings.folderPrimarySortField)
            .putString("folder_secondary_sort_field", settings.folderSecondarySortField)
            .putBoolean("sort_reverse", settings.sortReverse)
            .putBoolean("sort_secondary_reverse", settings.sortSecondaryReverse)
            .putBoolean("sort_numeric", settings.sortNumeric)
            .putBoolean("sort_case_sensitive", settings.sortCaseSensitive)
            .putBoolean("sort_ignore_extension", settings.sortIgnoreExtension)
            .putBoolean("show_field_size", settings.showFieldSize)
            .putBoolean("show_field_date", settings.showFieldDate)
            .putBoolean("show_field_duration", settings.showFieldDuration)
            .putBoolean("show_field_location", settings.showFieldLocation)
            .putBoolean("show_field_count", settings.showFieldCount)
            .putBoolean("show_music_tracks_tab", settings.showMusicTracksTab)
            .putBoolean("show_music_playlists_tab", settings.showMusicPlaylistsTab)
            .putBoolean("show_music_albums_tab", settings.showMusicAlbumsTab)
            .putBoolean("show_music_artists_tab", settings.showMusicArtistsTab)
            .putBoolean("show_music_folders_tab", settings.showMusicFoldersTab)
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
