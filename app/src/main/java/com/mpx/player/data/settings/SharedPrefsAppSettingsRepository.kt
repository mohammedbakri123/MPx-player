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
            backgroundPlay = prefs.getBoolean("background_play", false),
            gesturesEnabled = prefs.getBoolean("gestures_enabled", true),
            decoderMode = prefs.getString("decoder_mode", "HW") ?: "HW",
            audioBoost = prefs.getBoolean("audio_boost", false),
            subtitleSize = prefs.getString("subtitle_size", "Normal") ?: "Normal",
            subtitleStyle = prefs.getString("subtitle_style", "White") ?: "White",
            appTheme = prefs.getString("app_theme", "Midnight") ?: "Midnight",
            popupPlayEnabled = prefs.getBoolean("popup_play_enabled", true),
            popupPlayControls = prefs.getString("popup_play_controls", "PrevNext") ?: "PrevNext",
            appLanguage = prefs.getString("app_language", "system") ?: "system",
            showAllFolders = prefs.getBoolean("show_all_folders", false),
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
            .putBoolean("audio_boost", settings.audioBoost)
            .putString("subtitle_size", settings.subtitleSize)
            .putString("subtitle_style", settings.subtitleStyle)
            .putString("app_theme", settings.appTheme)
            .putBoolean("popup_play_enabled", settings.popupPlayEnabled)
            .putString("popup_play_controls", settings.popupPlayControls)
            .putString("app_language", settings.appLanguage)
            .putBoolean("show_all_folders", settings.showAllFolders)
            .putBoolean("show_music_tracks_tab", settings.showMusicTracksTab)
            .putBoolean("show_music_playlists_tab", settings.showMusicPlaylistsTab)
            .putBoolean("show_music_albums_tab", settings.showMusicAlbumsTab)
            .putBoolean("show_music_artists_tab", settings.showMusicArtistsTab)
            .putBoolean("show_music_folders_tab", settings.showMusicFoldersTab)
            .apply()
    }
}
