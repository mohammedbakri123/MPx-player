package com.mpx.player.domain.settings

/**
 * Real, user-facing app settings. Persisted via [AppSettingsRepository].
 * Room-persistence worktree may later swap the implementation; the
 * interface and defaults here are the contract.
 */

/** Default Player Menu layout (#158): visible row ids in display order. */
const val DEFAULT_PLAYER_MENU_LAYOUT =
    "decoder,audio,subtitles,favourite,shuffle,repeat,pip,background,speed,aspect,rotation,sleep,playlist"

/**
 * Default download User-Agent (issue #337): Seal captures the in-app
 * WebView's UA at login and sends it with every request. MPx sends
 * this UA both from the in-app sign-in WebView and from yt-dlp, so
 * the site sees one consistent identity — a current desktop Chrome
 * UA is the string restricted videos expect to see.
 */
const val DEFAULT_DOWNLOAD_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

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
    val lastSmbHost: String = "", // Last SMB server typed in Local Network; username/password are never persisted

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
    // yt-dlp auto-update (issue #333, Seal UpdateUtil/YtdlpUpdater):
    // the bundled yt-dlp rots as extractors change, so MPx checks for
    // a newer one on launch. Channel STABLE (default) or NIGHTLY;
    // lastUpdateTimeMs is the last check that returned (DONE or
    // ALREADY_UP_TO_DATE — failures do not reset the interval, so a
    // broken network retries on the next launch instead of waiting a
    // week); ytdlpVersion is the installed version string once known.
    val ytdlpAutoUpdate: Boolean = true,
    val ytdlpUpdateChannel: String = "STABLE",
    val ytdlpUpdateIntervalMs: Long = 7L * 24 * 60 * 60 * 1000,
    val ytdlpLastUpdateTimeMs: Long = 0L,
    val ytdlpVersion: String = "",
    // Cookies + User-Agent (issue #337): sent on every yt-dlp request
    // (info + download) so age-gated / logged-in videos can download.
    // Blank = send no User-Agent header at all.
    val downloadUserAgent: String = DEFAULT_DOWNLOAD_USER_AGENT,
    // Download quality (issue #338): wires #336's FormatSorter to real
    // user settings. downloadCodec: "Auto" (Seal's default — vp9.2, or
    // av01 when the device hardware-decodes AV1), "VP9", "AV1", "H264"
    // (Seal's compatibility format proto,vcodec:h264,ext).
    // downloadResolution is Seal's videoResolution code (0 = no cap,
    // 1 = 2160, 2 = 1440, 3 = 1080, 4 = 720, 5 = 480, 6 = 360).
    // downloadCustomSorter is the advanced override — when non-blank
    // it is passed to `-S` as-is, replacing the built sorter.
    val downloadCodec: String = "Auto",
    val downloadResolution: Int = 0,
    val downloadCustomSorter: String = "",
    // Small network/filename flags (issue #347, Seal's downloadVideo,
    // DownloadUtil.kt:~700-730, JunkFood02/Seal, GPL-3.0): rate limit,
    // proxy, force IPv4, restrict filenames. Rate limit is the raw
    // yt-dlp `-r` value (e.g. "500K"); a bare number is read as KB/s,
    // like Seal's "${maxDownloadRate}K". Blank = no limit / no proxy.
    val downloadRateLimit: String = "",
    val downloadProxy: String = "",
    val downloadForceIpv4: Boolean = false,
    val downloadRestrictFilenames: Boolean = false,
    // SponsorBlock (issue #348, Seal's DownloadUtil.kt:751-752,
    // JunkFood02/Seal, GPL-3.0): cut sponsor segments, intros and
    // outros from downloaded videos with the bundled ffmpeg (#336).
    // Off by default. Categories are the raw `--sponsorblock-remove`
    // value; Seal stores yt-dlp's "default" set, MPx stores the
    // explicit four — sponsor, intro, outro, selfpromo — so the
    // dialog's checkboxes have concrete state to reflect. Blank
    // categories with the switch on passes no flag.
    val downloadSponsorBlock: Boolean = false,
    val downloadSponsorBlockCategories: String = "sponsor,intro,outro,selfpromo",
    // Issue #379: downloader defaults that used to be per-download switches
    // on the Download page — now persisted settings under Downloader
    // settings, like Seal's download defaults.
    val downloadAudioOnly: Boolean = false,
    val downloadSubtitles: Boolean = false,
    /**
     * Issue #390: audio format for audio-only downloads (Seal's
     * `AUDIO_CONVERT_FORMAT`). "mp3" is today's behavior, so
     * existing installs keep it. The downloader passes it as
     * `--audio-format`; conversion needs the bundled ffmpeg,
     * same as mp3 does today.
     */
    val downloadAudioFormat: String = "mp3",
    /**
     * Issue #395: keep the original audio format — no conversion.
     * Seal's USE_CUSTOM_AUDIO_PRESET + AUDIO_FORMAT + AUDIO_QUALITY.
     * Off by default (today's convert behavior unchanged). When on,
     * the audio branch passes -S <audioFormatSorter(...)> instead of
     * --audio-format; "any" format + "best" quality = no constraint
     * at all, just the original best audio.
     */
    val downloadUseCustomAudioPreset: Boolean = false,
    val downloadAudioPresetFormat: String = "any",
    val downloadAudioPresetQuality: String = "best",
    /**
     * Issue #380: allow downloads on a metered network (mobile data or a
     * metered WiFi). Off by default, like Seal's `CELLULAR_DOWNLOAD` —
     * starting a download on a metered network asks first (see
     * MeteredDownloadDialog) instead of silently burning mobile data.
     */
    val downloadAllowMetered: Boolean = false,
    // Issue #379: download folder — the subfolder of the public Downloads
    // folder downloads land in (MediaStore RELATIVE_PATH "Download/<name>").
    // "MPx" is the old fixed folder, so existing installs keep their files.
    val downloadDirectory: String = "MPx",
    /**
     * Issue #389: group playlist downloads in a folder named after
     * the playlist (Seal's `SUBDIRECTORY_PLAYLIST_TITLE`). Off by
     * default — playlists land flat in the download folder, as today.
     */
    val downloadGroupPlaylists: Boolean = false,
    /**
     * Issue #396: group downloads by site (Seal's
     * SUBDIRECTORY_EXTRACTOR) — when on, finished files publish into
     * a subfolder named after the extractor ("youtube",
     * "soundcloud"), under the download folder and above any
     * playlist folder. Off by default.
     */
    val downloadGroupBySite: Boolean = false,
    /**
     * Issue #392: private mode (Seal's PRIVATE_MODE). When on, a
     * finished download skips the History row entirely — the file
     * still downloads, still publishes to Downloads, and still shows
     * in Downloaded; only the record is skipped. Off by default.
     */
    val downloadPrivateMode: Boolean = false,
    /**
     * Issue #393: download to the SD card (Seal's SDCARD_DOWNLOAD +
     * SDCARD_URI). When on, finished files are moved into the folder
     * the user picked through the system folder picker, instead of
     * the public Downloads folder. The folder is the SAF tree URI
     * string below; empty means no folder picked yet. Off by default
     * — the picker flow must complete before anything changes.
     */
    val downloadToSdCard: Boolean = false,
    val downloadSdCardUri: String = "",
    /**
     * Issue #394: a separate download folder for audio-only
     * downloads (Seal's AUDIO_DIRECTORY). Blank by default —
     * blank means the video folder, so today's behavior is
     * unchanged unless you set it. A set value is one folder
     * name, sanitized the same way as the video folder.
     */
    val downloadAudioDirectory: String = "",
    /**
     * Issue #384: custom command (Seal's CommandTemplate, one line of
     * yt-dlp options). The text is pre-filled with Seal's sample
     * `--no-mtime -S "ext"`; the switch decides whether it runs.
     * Off by default — a half-typed command must never break
     * downloads. When on, the template IS the command: it replaces
     * the format sorter/-f, subtitles, SponsorBlock, metadata/
     * chapters, advanced flags, clips and split-by-chapter, while
     * MPx keeps the output filename, temp folder, archive, cookies
     * and network flags.
     */
    val customCommandEnabled: Boolean = false,
    val customCommand: String = "--no-mtime -S \"ext\"",
    /**
     * Issue #382: subtitle options, Seal's subtitle block
     * (DownloadUtil.kt:452-468, JunkFood02/Seal, GPL-3.0). Languages are
     * the `--sub-langs` value (default keeps MPx's "en,ar"); auto
     * subtitles add `--write-auto-subs` with translated subs skipped;
     * convert is "none"/"srt"/"vtt"/"ass" (`--convert-subs`); embed +
     * keep default to true, which is exactly #341's old behavior
     * (`--embed-subs` + `--write-subs` together) — turning embed off
     * leaves the sidecar only, and turning keep off while embedding
     * drops the sidecar.
     */
    val downloadSubtitleLanguages: String = "en,ar",
    val downloadAutoSubtitles: Boolean = false,
    val downloadConvertSubtitles: String = "none",
    val downloadEmbedSubtitles: Boolean = true,
    val downloadKeepSubtitles: Boolean = true,
    /**
     * Issue #385: Seal's advanced video flags (DownloadUtil.kt,
     * JunkFood02/Seal, GPL-3.0) — the settings-shaped ones. Merge to
     * MKV remuxes + merges to .mkv; audio multistreams keeps every
     * audio track instead of the best one (Seal gates it on a chosen
     * format id, which MPx has no UI for, so it applies whenever it
     * is on); write thumbnail saves the cover as a .png next to the
     * video (published with it, see publishSiblingThumbnail). All
     * three need the bundled ffmpeg (#336) for the mux/convert step.
     * Clips, split-by-chapter and rename are per-download features,
     * not settings — they stay open in #385 for their own issues.
     */
    val downloadMergeToMkv: Boolean = false,
    val downloadAudioMultistreams: Boolean = false,
    val downloadWriteThumbnail: Boolean = false,
    // Issue #391: embed the cover art into the downloaded file
    // itself (`--embed-thumbnail`, Seal's EMBED_THUMBNAIL), so it
    // travels with the file instead of living only in the png
    // sidecar that #385 writes. Needs the bundled ffmpeg (#336).
    val downloadEmbedThumbnail: Boolean = false,
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
