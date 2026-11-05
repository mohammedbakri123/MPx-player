package com.mpx.player.data.settings

import android.content.Context
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.domain.settings.DEFAULT_PLAYER_MENU_LAYOUT

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
        defaultPlaybackSpeed = prefs.getFloat("default_playback_speed", 1.0f),
        backgroundPlay = if (prefs.contains("background_play")) {
            prefs.getBoolean("background_play", false)
        } else {
            prefs.getBoolean("backgroundPlay", false)
        },
        gesturesEnabled = prefs.getBoolean("gestures_enabled", true),
        playerTutorialSeen = prefs.getBoolean("player_tutorial_seen", false),
        decoderMode = prefs.getString("decoder_mode", "HW") ?: "HW",
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
        subtitleDelayMs = prefs.getInt("subtitle_delay_ms", 0),
        subtitleSpeed = prefs.getFloat("subtitle_speed", 1.0f),
        audioDelayMs = prefs.getInt("audio_delay_ms", 0),
        videoZoom = prefs.getFloat("video_zoom", 1.0f),
        // #139: legacy stored values Midnight/Dark/Classic all mean the
        // true-black theme, now called Dark; unknown values follow System.
        appTheme = when (prefs.getString("app_theme", "System")) {
            "Light" -> "Light"
            "Dark", "Midnight", "Classic" -> "Dark"
            else -> "System"
        },
        colorPalette = prefs.getString("color_palette", "Blue") ?: "Blue",
        darkSurface = prefs.getString("dark_surface", "TrueBlack") ?: "TrueBlack",
        popupPlayEnabled = prefs.getBoolean("popup_play_enabled", true),
        popupPlayControls = prefs.getString("popup_play_controls", "PrevNext") ?: "PrevNext",
        popupPlaySize = prefs.getString("popup_play_size", "Medium") ?: "Medium",
        popupPlayPosition = prefs.getString("popup_play_position", "BottomRight") ?: "BottomRight",
        popupPlaySnapToEdge = prefs.getBoolean("popup_play_snap_to_edge", true),
        popupPlayCloseOnDone = prefs.getBoolean("popup_play_close_on_done", true),
        appLanguage = prefs.getString("app_language", "system") ?: "system",
        showAllFolders = prefs.getBoolean("show_all_folders", false),
        localViewMode = prefs.getString("local_view_mode", "LIST") ?: "LIST",
        localTreeMode = prefs.getBoolean("local_tree_mode", false),
        folderGridColumns = prefs.getInt("folder_grid_columns", 2),
        videoGridColumns = prefs.getInt("video_grid_columns", 2),
        showFullName = prefs.getBoolean("show_full_name", false),
        separateViewLayout = prefs.getBoolean("separate_view_layout", true),
        musicViewMode = prefs.getString("music_view_mode", "LIST") ?: "LIST",
        musicPrimarySortField = prefs.getString("music_primary_sort_field", "NAME") ?: "NAME",
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
        lastTabRoute = prefs.getString("last_tab_route", "local") ?: "local",
            blacklistedFolders = prefs.getStringSet("blacklisted_folders", emptySet())?.toSet() ?: emptySet(),
            playerMenuLayout = prefs.getString("player_menu_layout", DEFAULT_PLAYER_MENU_LAYOUT) ?: DEFAULT_PLAYER_MENU_LAYOUT,
            hiddenPlayerMenuIds = prefs.getString("player_menu_layout_hidden", "") ?: "",
            doubleTapSeekSeconds = prefs.getInt("double_tap_seek_seconds", 10),
            lastSmbHost = prefs.getString("last_smb_host", "") ?: "",
        )
    }

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("show_hidden_files", settings.showHiddenFiles)
            .putBoolean("show_thumbnails", settings.showThumbnails)
            .putBoolean("resume_playback", settings.resumePlayback)
            .putFloat("default_playback_speed", settings.defaultPlaybackSpeed)
            .putBoolean("backgroundPlay", settings.backgroundPlay)
            .putBoolean("background_play", settings.backgroundPlay)
            .putBoolean("gestures_enabled", settings.gesturesEnabled)
            .putBoolean("player_tutorial_seen", settings.playerTutorialSeen)
            .putString("decoder_mode", settings.decoderMode)
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
            .putInt("subtitle_delay_ms", settings.subtitleDelayMs)
            .putFloat("subtitle_speed", settings.subtitleSpeed)
            .putInt("audio_delay_ms", settings.audioDelayMs)
            .putFloat("video_zoom", settings.videoZoom)
            .putString("app_theme", settings.appTheme)
            .putString("color_palette", settings.colorPalette)
            .putString("dark_surface", settings.darkSurface)
            .putBoolean("popup_play_enabled", settings.popupPlayEnabled)
            .putString("popup_play_controls", settings.popupPlayControls)
            .putString("popup_play_size", settings.popupPlaySize)
            .putString("popup_play_position", settings.popupPlayPosition)
            .putBoolean("popup_play_snap_to_edge", settings.popupPlaySnapToEdge)
            .putBoolean("popup_play_close_on_done", settings.popupPlayCloseOnDone)
            .putString("last_smb_host", settings.lastSmbHost)
            .putString("app_language", settings.appLanguage)
            .putBoolean("show_all_folders", settings.showAllFolders)
            .putString("local_view_mode", settings.localViewMode)
            .putBoolean("local_tree_mode", settings.localTreeMode)
            .putInt("folder_grid_columns", settings.folderGridColumns)
            .putInt("video_grid_columns", settings.videoGridColumns)
            .putBoolean("show_full_name", settings.showFullName)
            .putBoolean("separate_view_layout", settings.separateViewLayout)
            .putString("music_view_mode", settings.musicViewMode)
            .putString("music_primary_sort_field", settings.musicPrimarySortField)
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
            .putString("last_tab_route", settings.lastTabRoute)
            .putStringSet("blacklisted_folders", settings.blacklistedFolders)
            .putString("player_menu_layout", settings.playerMenuLayout)
            .putString("player_menu_layout_hidden", settings.hiddenPlayerMenuIds)
            .putInt("double_tap_seek_seconds", settings.doubleTapSeekSeconds)
            .apply()
    }
}
