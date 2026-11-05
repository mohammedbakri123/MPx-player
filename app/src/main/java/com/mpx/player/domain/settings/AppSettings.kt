package com.mpx.player.domain.settings

/**
 * Real, user-facing app settings. Persisted via [AppSettingsRepository].
 * Room-persistence worktree may later swap the implementation; the
 * interface and defaults here are the contract.
 */
data class AppSettings(
    val showHiddenFiles: Boolean = false,
    val showThumbnails: Boolean = true,
    val resumePlayback: Boolean = true,
    val backgroundPlay: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f, // Persisted default speed (issue #76); new sessions start here
    val gesturesEnabled: Boolean = true,
    val playerTutorialSeen: Boolean = false, // First-run gesture tutorial (#151) already shown
    val decoderMode: String = "HW", // HW, HW+, SW
    val playerEngine: String = "MEDIA3", // MEDIA3, MPV
    val subtitleSize: String = "Normal", // Small, Normal, Large
    val subtitleStyle: String = "White", // White, Yellow, Cyan, Default (legacy preset; kept working)
    // Subtitle depth (issue #75): each option is applied to rendered subtitles.
    val subtitleTypeface: String = "Default", // Default, Sans, Serif, Mono
    val subtitleTextColor: String = "Preset", // Preset (follow subtitleStyle), White, Yellow, Cyan, Green, Magenta, Red, Black
    val subtitleBackgroundColor: String = "Transparent", // Transparent, Black, SemiBlack, White
    val subtitleBorderColor: String = "Black", // Black, White, Yellow, Transparent (no border)
    val subtitleBorderThickness: String = "Medium", // None, Thin, Medium, Thick (thickness granularity is mpv-only; Media3 renders an outline)
    val subtitleBold: Boolean = true,
    val subtitleShadow: Boolean = false, // Media3: single edge type, so shadow wins over outline when both are set
    val subtitleAlignment: String = "Bottom", // Bottom, Center, Top (Media3 approximates via bottom padding fraction)
    val subtitleBottomPadding: String = "Medium", // Small, Medium, Large (used when alignment is Bottom)
    val subtitleScale: String = "100", // Percent: 75, 100, 125, 150
    val subtitleAssOverride: String = "Default", // Default, Scale, Force, Strip (mpv-only; Media3 always applies embedded styles)
    // Subtitle timing (issue #79, MX menu_subtitle sync/speed rows): mpv-only.
    // Media3 (1.3.1) exposes no subtitle timing API — cue timestamps come
    // straight from the decoder — so these values are persisted and shown
    // in both UIs but only applied on mpv (sub-delay/sub-speed). The UIs
    // carry an honest mpv-only note; nothing is faked on Media3.
    val subtitleDelayMs: Int = 0, // -10000..10000, 100 ms steps; positive = subtitles later
    val subtitleSpeed: Float = 1.0f, // 0.5..2.0, 0.1 steps; multiplies subtitle timing rate
    // Audio delay + video zoom (issue #153, Playback sheet): mpv-only, same
    // pattern as subtitle timing — persisted, applied live on mpv via
    // applyPlaybackTweaks; other engines ignore them (the sheet shows a note).
    val audioDelayMs: Int = 0, // -10000..10000, 100 ms steps; positive = audio later
    val videoZoom: Float = 1.0f, // 0.5..2.0; mpv video-zoom multiplier
    val appTheme: String = "System", // System, Dark, Light (legacy Midnight/Dark/Classic read as Dark)
    val popupPlayEnabled: Boolean = true,
    val popupPlayControls: String = "PrevNext", // PrevNext, Seek10, PlayPauseOnly
    val popupPlaySize: String = "Medium", // Small, Medium, Large, Auto
    val popupPlayPosition: String = "BottomRight", // BottomRight, BottomLeft, TopRight, TopLeft, Center, RememberLast
    val popupPlaySnapToEdge: Boolean = true,
    val popupPlayCloseOnDone: Boolean = true,
    val appLanguage: String = "system", // system, en, ar, es, fr, hi, zh, ru, de, pt
    val showAllFolders: Boolean = false,
    val folderViewMode: String = "LIST", // Global default folder contents view mode: LIST, GRID
    val localViewMode: String = "LIST", // Local root folders list view mode: LIST, GRID
    val separateViewLayout: Boolean = true,
    val musicViewMode: String = "LIST", // LIST, GRID
    val fileView: Boolean = false, // false = folder view, true = individual files view
    val autoScreenRotation: Boolean = true, // true = follows sensor, false = locked orientation
    val screenOrientation: String = "auto", // auto (sensor), landscape, portrait

    // Sort and View Fields (Issue 59)
    val primarySortField: String = "DATE",
    val secondarySortField: String = "NAME",
    val folderPrimarySortField: String = "DATE",
    val folderSecondarySortField: String = "NAME",
    val sortReverse: Boolean = false,
    val sortSecondaryReverse: Boolean = false,
    val sortNumeric: Boolean = true,
    val sortCaseSensitive: Boolean = false,
    val sortIgnoreExtension: Boolean = true,
    val showFieldSize: Boolean = true,
    val showFieldDate: Boolean = false,
    val showFieldDuration: Boolean = true,
    val showFieldLocation: Boolean = true,
    val showFieldCount: Boolean = true,

    val showMusicTracksTab: Boolean = true,
    val showMusicPlaylistsTab: Boolean = true,
    val showMusicAlbumsTab: Boolean = true,
    val showMusicArtistsTab: Boolean = true,
    val showMusicFoldersTab: Boolean = true,
) {
    /**
     * Total number of currently visible music tabs.
     */
    val visibleMusicTabsCount: Int
        get() = (if (showMusicTracksTab) 1 else 0) +
                (if (showMusicPlaylistsTab) 1 else 0) +
                (if (showMusicAlbumsTab) 1 else 0) +
                (if (showMusicArtistsTab) 1 else 0) +
                (if (showMusicFoldersTab) 1 else 0)

    /**
     * Returns true if the specified tab can be toggled off.
     * Enforces that at least one music tab must always remain visible.
     */
    fun canDisableMusicTab(tabId: String): Boolean {
        if (visibleMusicTabsCount > 1) return true
        // Only 1 tab is visible. If this tab is the one currently visible, it cannot be disabled.
        return when (tabId) {
            "tracks" -> !showMusicTracksTab
            "playlists" -> !showMusicPlaylistsTab
            "albums" -> !showMusicAlbumsTab
            "artists" -> !showMusicArtistsTab
            "folders" -> !showMusicFoldersTab
            else -> true
        }
    }

    /**
     * Safely updates visibility of a music tab, honestly enforcing the minimum of 1 visible tab.
     */
    fun withMusicTabToggled(tabId: String, enabled: Boolean): AppSettings {
        if (!enabled && !canDisableMusicTab(tabId)) {
            // Refuse to disable the last remaining visible tab
            return this
        }
        return when (tabId) {
            "tracks" -> copy(showMusicTracksTab = enabled)
            "playlists" -> copy(showMusicPlaylistsTab = enabled)
            "albums" -> copy(showMusicAlbumsTab = enabled)
            "artists" -> copy(showMusicArtistsTab = enabled)
            "folders" -> copy(showMusicFoldersTab = enabled)
            else -> this
        }
    }
}

interface AppSettingsRepository {
    fun load(): AppSettings
    fun save(settings: AppSettings)
    fun getFolderViewMode(folderId: String): String = "LIST"
    fun setFolderViewMode(folderId: String, mode: String) {}
    fun clearFolderViewModeOverride(folderId: String) {}
    fun hasFolderViewModeOverride(folderId: String): Boolean = false
}
