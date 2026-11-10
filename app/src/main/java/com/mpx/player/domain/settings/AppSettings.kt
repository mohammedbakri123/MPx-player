package com.mpx.player.domain.settings

/**
 * Real, user-facing app settings. Persisted via [AppSettingsRepository].
 * Room-persistence worktree may later swap the implementation; the
 * interface and defaults here are the contract.
 */

/** Default Player Menu layout (#158): visible row ids in display order. */
const val DEFAULT_PLAYER_MENU_LAYOUT =
    "decoder,audio,subtitles,favourite,shuffle,repeat,pip,background,speed,aspect,rotation,sleep,playlist"

data class AppSettings(
    val showHiddenFiles: Boolean = false,
    val showThumbnails: Boolean = true,
    val resumePlayback: Boolean = true,
    val backgroundPlay: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f, // Persisted default speed (issue #76); new sessions start here
    val gesturesEnabled: Boolean = true,
    val playerTutorialSeen: Boolean = false, // First-run gesture tutorial (#151) already shown
    val decoderMode: String = "HW", // HW, HW+, SW
    val subtitleSize: String = "Normal", // Small, Normal, Large
    val subtitleStyle: String = "White", // White, Yellow, Cyan, Default (legacy preset; kept working)
    // Subtitle depth (issue #75): each option is applied to rendered subtitles.
    val subtitleTypeface: String = "Default", // Default, Sans, Serif, Mono
    val subtitleTextColor: String = "Preset", // Preset (follow subtitleStyle), White, Yellow, Cyan, Green, Magenta, Red, Black
    val subtitleBackgroundColor: String = "Transparent", // Transparent, Black, SemiBlack, White
    val subtitleBorderColor: String = "Black", // Black, White, Yellow, Transparent (no border)
    val subtitleBorderThickness: String = "Medium", // None, Thin, Medium, Thick (thickness granularity is mpv-only)
    val subtitleBold: Boolean = true,
    val subtitleShadow: Boolean = false,
    val subtitleAlignment: String = "Bottom", // Bottom, Center, Top
    val subtitleBottomPadding: String = "Medium", // Small, Medium, Large (used when alignment is Bottom)
    val subtitleScale: String = "100", // Percent: 75, 100, 125, 150
    val subtitleAssOverride: String = "Default", // Default, Scale, Force, Strip
    // Subtitle timing (issue #79, MX menu_subtitle sync/speed rows): applied
    // on mpv (sub-delay/sub-speed).
    val subtitleDelayMs: Int = 0, // -10000..10000, 100 ms steps; positive = subtitles later
    val subtitleSpeed: Float = 1.0f, // 0.5..2.0, 0.1 steps; multiplies subtitle timing rate
    // Audio delay + video zoom (issue #153, Playback sheet): mpv-only, same
    // pattern as subtitle timing — persisted, applied live on mpv via
    // applyPlaybackTweaks; other engines ignore them (the sheet shows a note).
    val audioDelayMs: Int = 0, // -10000..10000, 100 ms steps; positive = audio later
    val videoZoom: Float = 1.0f, // 0.5..2.0 plain multiplier (1.0 = no zoom); the engine converts it to mpv's log2 video-zoom (#172)
    val appTheme: String = "System", // System, Dark, Light (legacy Midnight/Dark/Classic read as Dark)
    val colorPalette: String = "Blue", // Accent palette (#147, #366): Blue, Emerald, Violet, Amber, Rose, Teal, Sky, Orange, Pink, Lime, Indigo, Cyan — accent only, surfaces stay per-theme
    val themeColor: String = "TrueBlack", // One theme color (#374): TrueBlack, Charcoal, Graphite, Midnight, Forest, Plum — shared by Dark and Light; the mode only picks which brightness of it renders. Accent stays colorPalette.
    val popupPlayEnabled: Boolean = true,
    val popupPlayControls: String = "PrevNext", // PrevNext, Seek10, PlayPauseOnly
    val popupPlaySize: String = "Medium", // Small, Medium, Large, Auto
    val popupPlayPosition: String = "BottomRight", // BottomRight, BottomLeft, TopRight, TopLeft, Center, RememberLast
    // Issue #292: the last explicitly chosen PiP position, used when
    // popupPlayPosition is RememberLast. Updated whenever the user
    // picks an explicit position in Overlay settings. Android offers
    // no API to read where the user dragged the PiP window, so the
    // last position the app requested is the closest "last" it can
    // know; picking RememberLast itself leaves this untouched.
    val lastPopupPlayPosition: String = "BottomRight",
    val popupPlaySnapToEdge: Boolean = true,
    val popupPlayCloseOnDone: Boolean = true,
    val appLanguage: String = "system", // system, en, ar, es, fr, hi, zh, ru, de, pt
    val showAllFolders: Boolean = false,
    val localViewMode: String = "LIST", // Shared Home + folder contents view mode (#186): LIST, GRID
    // Tree view (#164): Local root shows the folder hierarchy inline
    // (Folder / Tree in the Sort & View popup) instead of a flat list.
    // Applies to the folders LIST layout; the GRID layout is unchanged.
    val localTreeMode: Boolean = false,
    // Tile size (#141) was removed by #156: the mpvEx-style Sort & View
    // popup uses grid-column counts instead (folderGridColumns /
    // videoGridColumns, 1-4). List rows are unaffected.
    val folderGridColumns: Int = 2,
    val videoGridColumns: Int = 2,
    val showFullName: Boolean = false,
    val separateViewLayout: Boolean = true,
    val musicViewMode: String = "LIST", // LIST, GRID
    // Tracks sort (#185): persisted like Local's primarySortField, so the
    // Music Tracks sort sticks across launches instead of resetting to NAME.
    val musicPrimarySortField: String = "NAME",
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
    val lastTabRoute: String = "local", // Last dock tab (#157): the app reopens on it
    // Folder blacklist (#158, mpvEx parity): folder paths hidden from
    // Local. Applied in LocalScreen when folders/videos are loaded.
    val blacklistedFolders: Set<String> = emptySet(),
    // Player Menu layout (#158, mpvEx parity): visible menu row ids in
    // display order, comma-separated; see PlayerMenuLayout.kt.
    val playerMenuLayout: String = DEFAULT_PLAYER_MENU_LAYOUT,
    // Player Menu hidden rows (#201): ids the user explicitly hid, comma-
    // separated. Kept apart from playerMenuLayout so the parse can tell a
    // deliberately hidden id from an id that was never stored (a new id
    // added in a later version stays visible by default). Empty = nothing hidden.
    val hiddenPlayerMenuIds: String = "",
    // Double-tap seek amount in seconds (#158, mpvEx parity): 5/10/15/30.
    val doubleTapSeekSeconds: Int = 10,
    // Issue #430-433: Advanced settings (mpvEx parity) — the picked
    // mpv config folder URI, the cached mpv.conf/input.conf editor
    // text, and the Lua scripts toggle (on = mpv default).
    val mpvConfStorageUri: String = "",
    val mpvConf: String = "",
    val inputConf: String = "",
    val enableLuaScripts: Boolean = true,
    // Issue #418: check for app updates automatically on launch.
    // Renamed from downloadAutoUpdateCheck in #434 — the downloader
    // is gone; the check belongs to the #414 self-update.
    val appUpdateAutoCheck: Boolean = false,
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

    /**
     * Persists the whole settings blob. By default the write is queued
     * with [android.content.SharedPreferences.Editor.apply] — the
     * in-memory map updates at once, the disk write lands later.
     *
     * Layout settings (#191) must not use the queued write: if the app
     * is swiped away right after the user picks List/Grid or a sort, the
     * process can be killed before the queued write flushes and the
     * choice is lost on the next launch. Pass [commit] = true for
     * layout saves so the value is on disk before the change returns.
     * Layout changes are user taps (a few per session at most), so the
     * synchronous write is cheap; high-frequency saves (pinch zoom,
     * playback tweaks) stay on apply(). Issue #228: the last-tab save on
     * a dock switch commits too — it is a discrete user tap, not a
     * high-frequency write, and losing it reopens the wrong tab.
     */
    fun save(settings: AppSettings, commit: Boolean = false)
}
