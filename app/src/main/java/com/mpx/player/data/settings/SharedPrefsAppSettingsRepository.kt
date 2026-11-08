package com.mpx.player.data.settings

import android.content.Context
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.domain.settings.DEFAULT_DOWNLOAD_USER_AGENT
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

        val settings = AppSettings(

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
        // #374: one theme color. New installs read "theme_color";
        // existing installs fall back to the legacy "dark_surface"
        // key, so the user's color survives the update. The old
        // "light_surface" key is ignored from here on.
        themeColor = prefs.getString("theme_color", null)
            ?: prefs.getString("dark_surface", "TrueBlack") ?: "TrueBlack",
        popupPlayEnabled = prefs.getBoolean("popup_play_enabled", true),
        popupPlayControls = prefs.getString("popup_play_controls", "PrevNext") ?: "PrevNext",
        popupPlaySize = prefs.getString("popup_play_size", "Medium") ?: "Medium",
        popupPlayPosition = prefs.getString("popup_play_position", "BottomRight") ?: "BottomRight",
        // Issue #292: only an explicit position is a valid "last" —
        // a stale/corrupt value (including "RememberLast" itself)
        // falls back to BottomRight.
        lastPopupPlayPosition = prefs.getString("last_popup_play_position", "BottomRight")
            ?.takeIf { it in setOf("TopLeft", "TopRight", "BottomLeft", "BottomRight", "Center") }
            ?: "BottomRight",
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
            // Issue #333: yt-dlp update prefs. Channel is validated —
            // a stale/corrupt value falls back to STABLE.
            ytdlpAutoUpdate = prefs.getBoolean("ytdlp_auto_update", true),
            ytdlpUpdateChannel = prefs.getString("ytdlp_update_channel", "STABLE")
                ?.takeIf { it == "STABLE" || it == "NIGHTLY" } ?: "STABLE",
            ytdlpUpdateIntervalMs = prefs.getLong("ytdlp_update_interval_ms", 7L * 24 * 60 * 60 * 1000),
            ytdlpLastUpdateTimeMs = prefs.getLong("ytdlp_last_update_time_ms", 0L),
            ytdlpVersion = prefs.getString("ytdlp_version", "") ?: "",
            // Issue #337: download User-Agent; a stored blank stays blank
            // (no header), a missing key takes the desktop-Chrome default.
            downloadUserAgent = prefs.getString("download_user_agent", DEFAULT_DOWNLOAD_USER_AGENT)
                ?: DEFAULT_DOWNLOAD_USER_AGENT,
            // Issue #338: download quality. Codec and resolution are
            // validated — a stale/corrupt value falls back to the
            // defaults (Auto / no cap) instead of breaking the sorter.
            downloadCodec = prefs.getString("download_codec", "Auto")
                ?.takeIf { it in setOf("Auto", "VP9", "AV1", "H264") } ?: "Auto",
            downloadResolution = prefs.getInt("download_resolution", 0)
                .takeIf { it in 0..6 } ?: 0,
            downloadCustomSorter = prefs.getString("download_custom_sorter", "") ?: "",
            // Issue #347: small network/filename flags. Stored raw —
            // a blank rate limit / proxy means "off", and the downloader
            // normalises a bare rate number to KB/s at request time.
            downloadRateLimit = prefs.getString("download_rate_limit", "") ?: "",
            downloadProxy = prefs.getString("download_proxy", "") ?: "",
            downloadForceIpv4 = prefs.getBoolean("download_force_ipv4", false),
            downloadRestrictFilenames = prefs.getBoolean("download_restrict_filenames", false),
            // Issue #348: SponsorBlock. Stored raw — the categories
            // string is passed to `--sponsorblock-remove` as-is.
            downloadSponsorBlock = prefs.getBoolean("download_sponsorblock", false),
            downloadSponsorBlockCategories = prefs.getString(
                "download_sponsorblock_categories",
                "sponsor,intro,outro,selfpromo",
            ) ?: "sponsor,intro,outro,selfpromo",
            // Issue #379: persisted downloader defaults (were per-download
            // switches on the Download page) + download folder.
            downloadAudioOnly = prefs.getBoolean("download_audio_only", false),
            downloadSubtitles = prefs.getBoolean("download_subtitles", false),
            downloadAudioFormat = prefs.getString("download_audio_format", "mp3") ?: "mp3",
            downloadUseCustomAudioPreset = prefs.getBoolean("download_use_custom_audio_preset", false),
            downloadAudioPresetFormat = prefs.getString("download_audio_preset_format", "any") ?: "any",
            downloadAudioPresetQuality = prefs.getString("download_audio_preset_quality", "best") ?: "best",
            downloadAllowMetered = prefs.getBoolean("download_allow_metered", false),
            downloadDirectory = prefs.getString("download_directory", "MPx") ?: "MPx",
            downloadGroupPlaylists = prefs.getBoolean("download_group_playlists", false),
            downloadGroupBySite = prefs.getBoolean("download_group_by_site", false),
            downloadPrivateDirectory = prefs.getBoolean("download_private_directory", false),
            downloadOutputTemplate = prefs.getString("download_output_template", "%(title).200B.%(ext)s") ?: "%(title).200B.%(ext)s",
            downloadPrivateMode = prefs.getBoolean("download_private_mode", false),
            downloadToSdCard = prefs.getBoolean("download_to_sd_card", false),
            downloadSdCardUri = prefs.getString("download_sd_card_uri", "") ?: "",
            downloadAudioDirectory = prefs.getString("download_audio_directory", "") ?: "",
            customCommandEnabled = prefs.getBoolean("custom_command_enabled", false),
            customCommand = prefs.getString("custom_command", "--no-mtime -S \"ext\"") ?: "--no-mtime -S \"ext\"",
            downloadSubtitleLanguages = prefs.getString("download_subtitle_languages", "en,ar") ?: "en,ar",
            downloadAutoSubtitles = prefs.getBoolean("download_auto_subtitles", false),
            downloadConvertSubtitles = prefs.getString("download_convert_subtitles", "none") ?: "none",
            downloadEmbedSubtitles = prefs.getBoolean("download_embed_subtitles", true),
            downloadKeepSubtitles = prefs.getBoolean("download_keep_subtitles", true),
            downloadMergeToMkv = prefs.getBoolean("download_merge_to_mkv", false),
            downloadAudioMultistreams = prefs.getBoolean("download_audio_multistreams", false),
            downloadWriteThumbnail = prefs.getBoolean("download_write_thumbnail", false),
            downloadEmbedThumbnail = prefs.getBoolean("download_embed_thumbnail", false),
        )

        // Issue #257: one-time migration of the legacy "sensor"
        // rotation value. Old builds persisted screenOrientation =
        // "sensor" (meaning Auto); the current UI never writes it, the
        // player treats it as Auto, but Settings' auto-rotation toggle
        // reads only autoScreenRotation — false for a legacy value — so
        // Settings showed auto-rotation OFF while the player was in
        // sensor mode. Normalize to the canonical Auto representation
        // (autoScreenRotation = true, screenOrientation = "auto") and
        // persist it, so the stale value is gone for good. apply(), not
        // commit(): load() runs on the main thread inside compositions,
        // and the write is idempotent — after it the stored value is
        // "auto" and this branch never runs again. save() never calls
        // load(), and nothing listens for preference changes, so no
        // loop is possible.
        if (settings.screenOrientation.equals("sensor", ignoreCase = true)) {
            val migrated = settings.copy(
                autoScreenRotation = true,
                screenOrientation = "auto",
            )
            save(migrated, commit = false)
            return migrated
        }
        return settings
    }

    // The commit default lives on the interface only: Kotlin forbids
    // defaults on an overriding function, and MainActivity holds this
    // repo as the concrete type, so its one-arg call passes commit
    // explicitly (see MainActivity language change).
    override fun save(settings: AppSettings, commit: Boolean) {
        val editor = prefs.edit()
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
            .putString("theme_color", settings.themeColor)
            .putBoolean("popup_play_enabled", settings.popupPlayEnabled)
            .putString("popup_play_controls", settings.popupPlayControls)
            .putString("popup_play_size", settings.popupPlaySize)
            .putString("popup_play_position", settings.popupPlayPosition)
            .putString("last_popup_play_position", settings.lastPopupPlayPosition)
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
            .putBoolean("ytdlp_auto_update", settings.ytdlpAutoUpdate)
            .putString("ytdlp_update_channel", settings.ytdlpUpdateChannel)
            .putLong("ytdlp_update_interval_ms", settings.ytdlpUpdateIntervalMs)
            .putLong("ytdlp_last_update_time_ms", settings.ytdlpLastUpdateTimeMs)
            .putString("ytdlp_version", settings.ytdlpVersion)
            .putString("download_user_agent", settings.downloadUserAgent)
            .putString("download_codec", settings.downloadCodec)
            .putInt("download_resolution", settings.downloadResolution)
            .putString("download_custom_sorter", settings.downloadCustomSorter)
            .putString("download_rate_limit", settings.downloadRateLimit)
            .putString("download_proxy", settings.downloadProxy)
            .putBoolean("download_force_ipv4", settings.downloadForceIpv4)
            .putBoolean("download_restrict_filenames", settings.downloadRestrictFilenames)
            .putBoolean("download_sponsorblock", settings.downloadSponsorBlock)
            .putString("download_sponsorblock_categories", settings.downloadSponsorBlockCategories)
            .putBoolean("download_audio_only", settings.downloadAudioOnly)
            .putBoolean("download_subtitles", settings.downloadSubtitles)
            .putString("download_audio_format", settings.downloadAudioFormat)
            .putBoolean("download_use_custom_audio_preset", settings.downloadUseCustomAudioPreset)
            .putString("download_audio_preset_format", settings.downloadAudioPresetFormat)
            .putString("download_audio_preset_quality", settings.downloadAudioPresetQuality)
            .putBoolean("download_allow_metered", settings.downloadAllowMetered)
            .putString("download_directory", settings.downloadDirectory)
            .putBoolean("download_group_playlists", settings.downloadGroupPlaylists)
            .putBoolean("download_group_by_site", settings.downloadGroupBySite)
            .putBoolean("download_private_directory", settings.downloadPrivateDirectory)
            .putString("download_output_template", settings.downloadOutputTemplate)
            .putBoolean("download_private_mode", settings.downloadPrivateMode)
            .putBoolean("download_to_sd_card", settings.downloadToSdCard)
            .putString("download_sd_card_uri", settings.downloadSdCardUri)
            .putString("download_audio_directory", settings.downloadAudioDirectory)
            .putBoolean("custom_command_enabled", settings.customCommandEnabled)
            .putString("custom_command", settings.customCommand)
            .putString("download_subtitle_languages", settings.downloadSubtitleLanguages)
            .putBoolean("download_auto_subtitles", settings.downloadAutoSubtitles)
            .putString("download_convert_subtitles", settings.downloadConvertSubtitles)
            .putBoolean("download_embed_subtitles", settings.downloadEmbedSubtitles)
            .putBoolean("download_keep_subtitles", settings.downloadKeepSubtitles)
            .putBoolean("download_merge_to_mkv", settings.downloadMergeToMkv)
            .putBoolean("download_audio_multistreams", settings.downloadAudioMultistreams)
            .putBoolean("download_write_thumbnail", settings.downloadWriteThumbnail)
            .putBoolean("download_embed_thumbnail", settings.downloadEmbedThumbnail)
        // Issue #191: commit = true writes synchronously (layout saves
        // only — see the interface KDoc). apply() only queues the disk
        // write, and a swipe-away right after a layout change could kill
        // the process before it flushed, losing the choice.
        if (commit) editor.commit() else editor.apply()
    }
}
