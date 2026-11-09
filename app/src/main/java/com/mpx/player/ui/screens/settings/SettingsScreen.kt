package com.mpx.player.ui.screens.settings
import com.mpx.player.ui.theme.MpxTheme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.mpx.player.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.download.DownloadBackup
import com.mpx.player.download.MpxDownloader
import com.mpx.player.download.YtDlpUpdateOutcome
import com.mpx.player.download.YtDlpUpdateState
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.screens.download.AudioDirectoryDialog
import com.mpx.player.ui.screens.download.AudioFormatDialog
import com.mpx.player.ui.screens.download.FormatSelectionModeDialog
import com.mpx.player.ui.screens.download.formatSelectionLabel
import com.mpx.player.ui.screens.download.YtDlpUpdateChannelDialog
import com.mpx.player.ui.screens.download.YtDlpUpdateIntervalDialog
import com.mpx.player.ui.screens.download.ytdlpUpdateChannelLabel
import com.mpx.player.ui.screens.download.ytdlpUpdateIntervalLabel
import com.mpx.player.ui.screens.download.OutputTemplateDialog
import com.mpx.player.ui.screens.download.AudioPresetFormatDialog
import com.mpx.player.ui.screens.download.AudioPresetQualityDialog
import com.mpx.player.ui.screens.download.audioPresetFormatLabel
import com.mpx.player.ui.screens.download.audioPresetQualityLabel
import com.mpx.player.ui.screens.download.CustomCommandDialog
import com.mpx.player.ui.screens.download.CustomCommandDirectoryDialog
import com.mpx.player.ui.screens.download.DownloadDirectoryDialog
import com.mpx.player.ui.screens.download.DownloadOptionsDialog
import com.mpx.player.ui.screens.download.DownloadQualityDialog
import com.mpx.player.ui.screens.download.DownloadSponsorBlockDialog
import com.mpx.player.ui.screens.download.SubtitleConvertDialog
import com.mpx.player.ui.screens.download.SubtitleLanguagesDialog
import com.mpx.player.ui.screens.download.audioFormatLabel
import com.mpx.player.ui.screens.download.downloadCodecLabel
import com.mpx.player.ui.screens.download.downloadOptionsSummary
import com.mpx.player.ui.screens.download.downloadResolutionLabel
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.buildDiagnosticsReport
import com.mpx.player.playback.buildDiagnosticsSummary
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.ui.components.MpvLogShareButton
import com.mpx.player.playback.PlaybackService
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.MediaPermissions
import com.mpx.player.util.PipUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.components.MpxPalettePicker
import com.mpx.player.ui.theme.mpxPaletteFor
import com.mpx.player.update.AppUpdateChecker
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable

/** Issue #414: the manual update check's row state. */
private enum class UpdateCheckStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenOverlaySettings: (() -> Unit)? = null,
    onOpenCookieProfiles: (() -> Unit)? = null,
    onOpenCommandTemplates: (() -> Unit)? = null,
    settingsRepository: AppSettingsRepository,
    onOpenAboutBeta: (() -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    onPaletteChanged: ((String) -> Unit)? = null,
    onThemeColorChanged: ((String) -> Unit)? = null,
    playbackManager: PlaybackManager? = null,
    // Issue #393: the SD-card folder picker — the launcher lives on
    // the Activity (see MainActivity), this screen only supplies the
    // callback. Null in previews, which never open the picker.
    sdCardFolderLauncher: ActivityResultLauncher<Uri?>? = null,
    sdCardFolderHandler: ActivityResultHandler<Uri?>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actualPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    // Issue #270: reload settings on resume (same pattern as MeScreen,
    // #231) — a PiP change made in Overlay Settings, a separate NavHost
    // destination editing popupPlayEnabled / controls / size / position
    // through the same repo, must show up here when coming back, without
    // the screen being recreated. The PiP drill-down below reads the same
    // `settings` state, so reloading the whole object covers all four
    // fields; language/theme callbacks stay in update(), which is the
    // only path that writes from this screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settings = settingsRepository.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Drill-down navigation (#77): null shows the category list, otherwise
    // the selected category's own sub-screen, like MX's per-header fragments.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // mpvEx-style search (#158): true shows the working settings search
    // page; back closes search before leaving the category list.
    var isSearching by rememberSaveable { mutableStateOf(false) }
    // Issue #303: the search query lives here, not in PreferencesSearchScreen
    // — opening a result sets isSearching = false, which removes the search
    // screen from the composition and discards any state it owns (even
    // rememberSaveable, cf. #255's MusicScreen query, which stays composed
    // across tab switches), so a query kept inside it would reset every
    // time a result category is opened. Hoisted here next to
    // isSearching/selectedId, it survives rotation (rememberSaveable) and
    // returning from a category, and resets when Settings itself is left,
    // like isSearching/selectedId.
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    // System back mirrors the TopAppBar icon: search closes first, then a
    // drilled-down category returns to the category list (#124); only at
    // the root list does it leave Settings.
    BackHandler(enabled = isSearching || selectedId != null) {
        if (isSearching) isSearching = false else selectedId = null
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Issue #299: diagnostics cache, hoisted out of
    // DevelopmentSettingsSection (a LazyColumn item) — the section's own
    // remember was dropped whenever it scrolled out of composition or the
    // user switched categories, and its LaunchedEffect(Unit) then re-ran
    // all five MediaStore scans. The cache lives for this Settings visit
    // only: leaving Settings drops it, so the next visit loads fresh
    // stats; switching categories inside Settings reuses it. The
    // explicit Refresh button in the section still re-runs the scans.
    var devDiagnostics by remember { mutableStateOf<DevDiagnosticsData?>(null) }

    // Issue #191: commit = true writes synchronously — the layout rows
    // below pass it so a swipe-away right after the tap cannot lose the
    // choice. Issue #228: the two hosted sub-screens (Player Layout,
    // folder blacklist) and Reset commit too, via their own wiring below.
    // Every other row keeps the queued apply() write.
    fun update(next: AppSettings, commit: Boolean = false) {
        val oldLanguage = settings.appLanguage
        val oldTheme = settings.appTheme
        val oldPalette = settings.colorPalette
        val oldThemeColor = settings.themeColor
        val oldDecoder = settings.decoderMode
        settings = next
        settingsRepository.save(next, commit = commit)
        if (next.appLanguage != oldLanguage) {
            onLanguageChanged?.invoke(next.appLanguage)
        }
        if (next.appTheme != oldTheme) {
            onThemeChanged?.invoke(next.appTheme)
        }
        if (next.colorPalette != oldPalette) {
            onPaletteChanged?.invoke(next.colorPalette)
        }
        if (next.themeColor != oldThemeColor) {
            onThemeColorChanged?.invoke(next.themeColor)
        }
        if (next.decoderMode != oldDecoder) {
            val decMode = com.mpx.player.playback.DecoderMode.values().find { it.label == next.decoderMode } ?: com.mpx.player.playback.DecoderMode.HW
            actualPlaybackManager.setDecoderMode(decMode)
        }
    }

    // Issue #393: the SD-card folder picker result — same handler
    // pattern as the subtitle picker (the launcher lives on the
    // Activity; this screen only supplies the callback). A picked
    // folder takes the persistable read/write permission, saves the
    // tree URI, and turns the toggle on. A cancelled pick (null)
    // changes nothing. Placed after update() — local functions are
    // only visible after their declaration.
    SideEffect {
        sdCardFolderHandler?.callback = { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                update(settings.copy(downloadSdCardUri = uri.toString(), downloadToSdCard = true))
            }
        }
    }

    // Issue #240: turning background play off must stop any background
    // session — the same three calls the background-play toggle and the
    // player toggle run when disabling. Shared here so Reset and the
    // toggle can never drift apart again. (actualPlaybackManager is the
    // same PlaybackManager singleton getInstance(context) returns.)
    fun stopBackgroundPlayback() {
        PlaybackService.stop(context)
        actualPlaybackManager.setVideoBackgroundActive(false)
        actualPlaybackManager.setBackgroundPlaying(false)
    }

    fun toggleMusicTab(tabId: String, currentVal: Boolean) {
        if (currentVal && settings.visibleMusicTabsCount <= 1) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_tab_min_one_required)
                )
            }
            return
        }
        val next = settings.withMusicTabToggled(tabId, !currentVal)
        update(next)
    }

    val activeLanguage = currentLanguage.ifBlank { settings.appLanguage }
    // Issue #231: one shared language list — the display name comes from
    // LocaleHelper.languageTitleRes, the same mapping the Me screen uses.
    val languageSubtitle = stringResource(LocaleHelper.languageTitleRes(activeLanguage))

    val themeSubtitle = when (settings.appTheme) {
        "Light" -> stringResource(R.string.theme_option_light)
        "Dark" -> stringResource(R.string.theme_option_dark)
        else -> stringResource(R.string.theme_option_system)
    }

    // mpvEx Preferences page shape (#158): categories grouped into
    // sections on the root list (UI & Appearance, Playback & Controls,
    // File Management, Media Settings, Advanced & About). Theme (+ the
    // #147 palette) is Appearance; gestures live in their own Gestures
    // category; Development + the reset button are Advanced. Audio was
    // removed in #66 and stays out — there is no real audio setting, so
    // no dead Audio row. Language and Theme keep subtitles, since they
    // carry real state at a glance.
    val categories = listOf(
        SettingCategory("appearance", stringResource(R.string.pref_appearance_title), themeSubtitle, Icons.Filled.ColorLens),
        SettingCategory("layout", stringResource(R.string.pref_layout_title), stringResource(R.string.pref_layout_summary), Icons.Filled.DashboardCustomize),
        SettingCategory("language", stringResource(R.string.settings_category_language), languageSubtitle, Icons.Filled.Language),
        SettingCategory("tabs", stringResource(R.string.settings_category_tabs), stringResource(R.string.settings_category_tabs_sub), Icons.Filled.Tab),
        SettingCategory("player", stringResource(R.string.settings_category_player), stringResource(R.string.pref_player_summary), Icons.Filled.PlayCircleOutline),
        SettingCategory("gestures", stringResource(R.string.pref_gestures_title), stringResource(R.string.pref_gestures_summary), Icons.Filled.TouchApp),
        SettingCategory("pip", stringResource(R.string.settings_category_pip), stringResource(R.string.settings_category_pip_sub), Icons.Filled.PictureInPictureAlt),
        SettingCategory("folders", stringResource(R.string.pref_folders_title), stringResource(R.string.pref_folders_summary), Icons.Filled.Folder),
        SettingCategory("list", stringResource(R.string.settings_category_list), stringResource(R.string.settings_category_list_sub), Icons.AutoMirrored.Filled.FormatListBulleted),
        SettingCategory("decoder", stringResource(R.string.settings_category_decoder), stringResource(R.string.settings_category_decoder_sub), Icons.Filled.Memory),
        SettingCategory("subtitle", stringResource(R.string.settings_category_subtitle), stringResource(R.string.settings_category_subtitle_sub), Icons.Filled.Subtitles),
        SettingCategory("downloader", stringResource(R.string.settings_category_downloader), stringResource(R.string.settings_category_downloader_sub), Icons.Filled.Download),
        SettingCategory("advanced", stringResource(R.string.pref_advanced_title), stringResource(R.string.settings_category_development_sub), Icons.Filled.DeveloperMode),
        SettingCategory("about", stringResource(R.string.settings_category_about), stringResource(R.string.settings_category_about_sub, BuildConfig.VERSION_NAME), Icons.Filled.Info),
    )

    val selectedCategory = categories.find { it.id == selectedId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedCategory?.title ?: stringResource(R.string.pref_preferences),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            isSearching -> isSearching = false
                            selectedCategory != null -> selectedId = null
                            else -> onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = MpxTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxTheme.colors.black),
            )
        },
    ) { padding ->
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                PreferencesSearchScreen(
                    query = settingsSearchQuery,
                    onQueryChange = { settingsSearchQuery = it },
                    onOpenCategory = { id ->
                        isSearching = false
                        selectedId = id
                    },
                )
            }
        } else if (selectedCategory == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // 28dp rounded search bar opening the working search page.
                item {
                    PreferenceCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSearching = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, null, tint = MpxTheme.colors.textMuted, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.pref_search_hint), style = MaterialTheme.typography.bodyLarge, color = MpxTheme.colors.textMuted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_appearance)) }
                item {
                    PreferenceCategoryCard(
                        categories = listOf("appearance", "layout", "language", "tabs").mapNotNull { id -> categories.find { it.id == id } },
                        onSelect = { selectedId = it },
                    )
                }
                item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_playback)) }
                item {
                    PreferenceCategoryCard(
                        categories = listOf("player", "gestures", "pip").mapNotNull { id -> categories.find { it.id == id } },
                        onSelect = { selectedId = it },
                    )
                }
                item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_files)) }
                item {
                    PreferenceCategoryCard(
                        categories = listOf("folders", "list").mapNotNull { id -> categories.find { it.id == id } },
                        onSelect = { selectedId = it },
                    )
                }
                item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_media)) }
                item {
                    PreferenceCategoryCard(
                        categories = listOf("decoder", "subtitle", "downloader").mapNotNull { id -> categories.find { it.id == id } },
                        onSelect = { selectedId = it },
                    )
                }
                item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_advanced)) }
                item {
                    PreferenceCategoryCard(
                        categories = listOf("advanced", "about").mapNotNull { id -> categories.find { it.id == id } },
                        onSelect = { selectedId = it },
                    )
                }
            }
        } else if (selectedId == "folders") {
            // #158: hosted at Scaffold level, not inside the drill-down
            // LazyColumn — the screen scrolls itself.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                FoldersBlacklistScreen(settings = settings, onSettingsChanged = { update(it, commit = true) })
            }
        } else if (selectedId == "layout") {
            // #158: same as above — the editor scrolls itself.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                PlayerLayoutScreen(settings = settings, onSettingsChanged = { update(it, commit = true) })
            }
        } else {
            // Dedicated sub-screen for one category: only its own options.
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Column {
                        when (selectedCategory.id) {
                            "language" -> {
                                // Issue #231: one shared language list —
                                // Settings and the Me dialog offer the same
                                // 10 options, in the same order.
                                val languageOptions = LocaleHelper.SUPPORTED_LANGUAGES
                                languageOptions.forEach { option ->
                                    ChoiceRowWithSubtitle(
                                        title = stringResource(option.titleRes),
                                        subtitle = stringResource(option.descRes),
                                        selected = activeLanguage.equals(option.code, ignoreCase = true),
                                    ) {
                                        update(settings.copy(appLanguage = option.code))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.lang_settings_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                )
                            }
                            "appearance" -> {
                                // #139: System / Dark / Light, like
                                // Instagram/Facebook. Dark is the true-black
                                // palette; Light is its white mirror.
                                val themeOptions = listOf(
                                    "System" to stringResource(R.string.theme_option_system),
                                    "Dark" to stringResource(R.string.theme_option_dark),
                                    "Light" to stringResource(R.string.theme_option_light),
                                )
                                themeOptions.forEach { (themeCode, themeLabel) ->
                                    ChoiceRow(themeLabel, settings.appTheme == themeCode) {
                                        update(settings.copy(appTheme = themeCode))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.theme_applied_snackbar, themeLabel)
                                            )
                                        }
                                    }
                                }
                                // #374: one theme color, shared by Dark
                                // and Light — the mode only picks which
                                // brightness of it renders. The whole
                                // surface scale shifts together, accent
                                // and text untouched.
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.theme_color_section_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MpxTheme.colors.textPrimary,
                                )
                                Spacer(Modifier.height(8.dp))
                                val surfaceOptions = listOf(
                                    "TrueBlack" to stringResource(R.string.theme_surface_true_black),
                                    "Charcoal" to stringResource(R.string.theme_surface_charcoal),
                                    "Graphite" to stringResource(R.string.theme_surface_graphite),
                                    "Midnight" to stringResource(R.string.theme_surface_midnight),
                                    "Forest" to stringResource(R.string.theme_surface_forest),
                                    "Plum" to stringResource(R.string.theme_surface_plum),
                                )
                                surfaceOptions.forEach { (surfaceCode, surfaceLabel) ->
                                    ChoiceRow(surfaceLabel, settings.themeColor == surfaceCode) {
                                        update(settings.copy(themeColor = surfaceCode))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.theme_applied_snackbar, surfaceLabel)
                                            )
                                        }
                                    }
                                }
                                // #147: accent palette picker, Appearance
                                // page only. Changes only the accent over
                                // the Dark / Light surfaces above; the
                                // System / Dark / Light choice is untouched.
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.palette_section_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MpxTheme.colors.textPrimary,
                                )
                                Spacer(Modifier.height(8.dp))
                                MpxPalettePicker(
                                    selectedId = settings.colorPalette,
                                    onPaletteSelected = { paletteId ->
                                        update(settings.copy(colorPalette = paletteId))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.palette_applied_snackbar,
                                                    context.getString(mpxPaletteFor(paletteId).nameRes),
                                                )
                                            )
                                        }
                                    },
                                )
                            }
                            // #158: gestures live here now, not in Player.
                            "gestures" -> {
                                ToggleRow(stringResource(R.string.player_gestures), settings.gesturesEnabled) { update(settings.copy(gesturesEnabled = it)) }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.pref_gesture_seek_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                listOf(5, 10, 15, 30).forEach { seconds ->
                                    ChoiceRow(
                                        stringResource(R.string.pref_gesture_seek_seconds, seconds),
                                        settings.doubleTapSeekSeconds == seconds,
                                    ) {
                                        update(settings.copy(doubleTapSeekSeconds = seconds))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.pref_gesture_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                )
                            }
                            "pip" -> {
                                if (onOpenOverlaySettings != null) {
                                    TextButton(onClick = onOpenOverlaySettings) {
                                        Text(stringResource(R.string.overlay_settings_title), color = MpxTheme.colors.primary)
                                    }
                                }
                                val isSupported = PipUtils.isPipSupported(context)
                                if (!isSupported) {
                                    Text(
                                        text = stringResource(R.string.pip_not_supported),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.destructive,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                                ToggleRow(stringResource(R.string.pip_enable_toggle), settings.popupPlayEnabled, enabled = isSupported) {
                                    update(settings.copy(popupPlayEnabled = it))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.pip_window_controls_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                val pipControlsEnabled = isSupported && settings.popupPlayEnabled
                                ChoiceRow(stringResource(R.string.pip_controls_prev_next), settings.popupPlayControls == "PrevNext", enabled = pipControlsEnabled) {
                                    update(settings.copy(popupPlayControls = "PrevNext"))
                                }
                                ChoiceRow(stringResource(R.string.pip_controls_seek10), settings.popupPlayControls == "Seek10", enabled = pipControlsEnabled) {
                                    update(settings.copy(popupPlayControls = "Seek10"))
                                }
                                ChoiceRow(stringResource(R.string.pip_controls_play_pause_only), settings.popupPlayControls == "PlayPauseOnly", enabled = pipControlsEnabled) {
                                    update(settings.copy(popupPlayControls = "PlayPauseOnly"))
                                }
                            }
                            "tabs" -> {
                                MusicTabsSettingsSection(settings = settings, onToggleTab = ::toggleMusicTab)
                            }
                            "list" -> {
                                Text(
                                    stringResource(R.string.pref_default_folder_view_mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                                ChoiceRow(stringResource(R.string.view_mode_list), settings.localViewMode.equals("LIST", ignoreCase = true)) {
                                    update(settings.copy(localViewMode = "LIST"), commit = true)
                                }
                                ChoiceRow(stringResource(R.string.view_mode_grid), settings.localViewMode.equals("GRID", ignoreCase = true)) {
                                    update(settings.copy(localViewMode = "GRID"), commit = true)
                                }
                                Spacer(Modifier.height(8.dp))
                                ToggleRow(stringResource(R.string.settings_separate_view_title), settings.separateViewLayout) {
                                    update(settings.copy(separateViewLayout = it), commit = true)
                                }
                                Text(
                                    stringResource(R.string.settings_separate_view_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                ToggleRow(stringResource(R.string.list_file_view), settings.fileView) { update(settings.copy(fileView = it), commit = true) }
                                Text(
                                    stringResource(R.string.list_file_view_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                                ToggleRow(stringResource(R.string.list_show_all_folders), settings.showAllFolders) { update(settings.copy(showAllFolders = it)) }
                                ToggleRow(stringResource(R.string.list_show_hidden), settings.showHiddenFiles) { update(settings.copy(showHiddenFiles = it)) }
                                ToggleRow(stringResource(R.string.list_show_thumbnails), settings.showThumbnails) { update(settings.copy(showThumbnails = it)) }
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_tabs_header),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                MusicTabsSettingsSection(settings = settings, onToggleTab = ::toggleMusicTab)
                            }
                            "player" -> {
                                ToggleRow(stringResource(R.string.player_resume_playback), settings.resumePlayback) { update(settings.copy(resumePlayback = it)) }
                                ToggleRow(stringResource(R.string.player_background_play), settings.backgroundPlay) { enabled ->
                                    update(settings.copy(backgroundPlay = enabled))
                                    if (!enabled) {
                                        stopBackgroundPlayback()
                                    }
                                }
                                Text(
                                    stringResource(R.string.player_background_play_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                                // Default playback speed (issue #76): persisted;
                                // every new playback session starts at this speed.
                                // The in-player speed toggle stays session-only.
                                Text(
                                    stringResource(R.string.player_default_speed_header),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    ChoiceRow("${speed}x", settings.defaultPlaybackSpeed == speed) {
                                        update(settings.copy(defaultPlaybackSpeed = speed))
                                    }
                                }
                                ToggleRow(stringResource(R.string.player_auto_rotation), settings.autoScreenRotation) {
                                    // Issue #240: turning auto-rotation on
                                    // must NOT overwrite the stored lock —
                                    // every consumer treats
                                    // autoScreenRotation=true as "ignore the
                                    // lock" (PlayerScreen targetOrientation,
                                    // the sheet radios), so leaving
                                    // screenOrientation alone means turning
                                    // auto off later restores the previous
                                    // lock instead of forcing landscape.
                                    // Turning auto off keeps whatever lock is
                                    // stored (portrait/landscape, or "auto"
                                    // when there never was one — the
                                    // consumer falls through to landscape
                                    // for anything that isn't portrait).
                                    update(settings.copy(autoScreenRotation = it))
                                }
                                Text(
                                    stringResource(R.string.player_auto_rotation_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                                if (!settings.autoScreenRotation) {
                                    Text(
                                        stringResource(R.string.player_locked_orientation_header),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MpxTheme.colors.textPrimary,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                    )
                                    ChoiceRow(stringResource(R.string.player_orientation_landscape), settings.screenOrientation.equals("landscape", ignoreCase = true)) {
                                        update(settings.copy(screenOrientation = "landscape"))
                                    }
                                    ChoiceRow(stringResource(R.string.player_orientation_portrait), settings.screenOrientation.equals("portrait", ignoreCase = true)) {
                                        update(settings.copy(screenOrientation = "portrait"))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                            "decoder" -> {
                                Text(
                                    stringResource(R.string.decoder_mode_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MpxTheme.colors.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                listOf(
                                    "HW" to stringResource(R.string.decoder_hw),
                                    "HW+" to stringResource(R.string.decoder_hw_plus),
                                    "SW" to stringResource(R.string.decoder_sw),
                                ).forEach { (mode, label) ->
                                    ChoiceRow(label, settings.decoderMode == mode) { update(settings.copy(decoderMode = mode)) }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.decoder_explanation),
                                    style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary,
                                )
                            }
                            "subtitle" -> {
                                Text(stringResource(R.string.subtitle_size_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Small" to stringResource(R.string.subtitle_size_small),
                                    "Normal" to stringResource(R.string.subtitle_size_normal),
                                    "Large" to stringResource(R.string.subtitle_size_large),
                                ).forEach { (size, label) ->
                                    ChoiceRow(label, settings.subtitleSize == size) { update(settings.copy(subtitleSize = size)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_style_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "White" to stringResource(R.string.subtitle_style_white),
                                    "Yellow" to stringResource(R.string.subtitle_style_yellow),
                                    "Cyan" to stringResource(R.string.subtitle_style_cyan),
                                    "Default" to stringResource(R.string.subtitle_style_default),
                                ).forEach { (styleKey, styleLabel) ->
                                    ChoiceRow(styleLabel, settings.subtitleStyle == styleKey) { update(settings.copy(subtitleStyle = styleKey)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_typeface_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Default" to stringResource(R.string.subtitle_typeface_default),
                                    "Sans" to stringResource(R.string.subtitle_typeface_sans),
                                    "Serif" to stringResource(R.string.subtitle_typeface_serif),
                                    "Mono" to stringResource(R.string.subtitle_typeface_mono),
                                ).forEach { (face, label) ->
                                    ChoiceRow(label, settings.subtitleTypeface == face) { update(settings.copy(subtitleTypeface = face)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_text_color_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Preset" to stringResource(R.string.subtitle_text_preset),
                                    "White" to stringResource(R.string.player_subtitle_style_white),
                                    "Yellow" to stringResource(R.string.player_subtitle_style_yellow),
                                    "Cyan" to stringResource(R.string.player_subtitle_style_cyan),
                                    "Green" to stringResource(R.string.subtitle_color_green),
                                    "Magenta" to stringResource(R.string.subtitle_color_magenta),
                                    "Red" to stringResource(R.string.subtitle_color_red),
                                    "Black" to stringResource(R.string.subtitle_color_black),
                                ).forEach { (color, label) ->
                                    ChoiceRow(label, settings.subtitleTextColor == color) { update(settings.copy(subtitleTextColor = color)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_bg_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Transparent" to stringResource(R.string.subtitle_bg_transparent),
                                    "Black" to stringResource(R.string.subtitle_bg_black),
                                    "SemiBlack" to stringResource(R.string.subtitle_bg_semi),
                                    "White" to stringResource(R.string.subtitle_bg_white),
                                ).forEach { (bg, label) ->
                                    ChoiceRow(label, settings.subtitleBackgroundColor == bg) { update(settings.copy(subtitleBackgroundColor = bg)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_border_color_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Black" to stringResource(R.string.subtitle_border_black),
                                    "White" to stringResource(R.string.subtitle_border_white),
                                    "Yellow" to stringResource(R.string.subtitle_border_yellow),
                                    "Transparent" to stringResource(R.string.subtitle_border_transparent),
                                ).forEach { (border, label) ->
                                    ChoiceRow(label, settings.subtitleBorderColor == border) { update(settings.copy(subtitleBorderColor = border)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_border_thickness_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "None" to stringResource(R.string.sort_field_none),
                                    "Thin" to stringResource(R.string.subtitle_thickness_thin),
                                    "Medium" to stringResource(R.string.subtitle_thickness_medium),
                                    "Thick" to stringResource(R.string.subtitle_thickness_thick),
                                ).forEach { (thickness, label) ->
                                    ChoiceRow(label, settings.subtitleBorderThickness == thickness) { update(settings.copy(subtitleBorderThickness = thickness)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                ToggleRow(stringResource(R.string.subtitle_bold_label), settings.subtitleBold) { update(settings.copy(subtitleBold = it)) }
                                ToggleRow(stringResource(R.string.subtitle_shadow_label), settings.subtitleShadow) { update(settings.copy(subtitleShadow = it)) }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_alignment_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Bottom" to stringResource(R.string.subtitle_align_bottom),
                                    "Center" to stringResource(R.string.subtitle_align_center),
                                    "Top" to stringResource(R.string.subtitle_align_top),
                                ).forEach { (align, label) ->
                                    ChoiceRow(label, settings.subtitleAlignment == align) { update(settings.copy(subtitleAlignment = align)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_padding_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Small" to stringResource(R.string.subtitle_padding_small),
                                    "Medium" to stringResource(R.string.subtitle_padding_medium),
                                    "Large" to stringResource(R.string.subtitle_padding_large),
                                ).forEach { (padding, label) ->
                                    ChoiceRow(label, settings.subtitleBottomPadding == padding) { update(settings.copy(subtitleBottomPadding = padding)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_scale_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "75" to stringResource(R.string.subtitle_scale_75),
                                    "100" to stringResource(R.string.subtitle_scale_100),
                                    "125" to stringResource(R.string.subtitle_scale_125),
                                    "150" to stringResource(R.string.subtitle_scale_150),
                                ).forEach { (scale, label) ->
                                    ChoiceRow(label, settings.subtitleScale == scale) { update(settings.copy(subtitleScale = scale)) }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.subtitle_ass_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                listOf(
                                    "Default" to stringResource(R.string.subtitle_ass_default),
                                    "Scale" to stringResource(R.string.subtitle_ass_scale),
                                    "Force" to stringResource(R.string.subtitle_ass_force),
                                    "Strip" to stringResource(R.string.subtitle_ass_strip),
                                ).forEach { (mode, label) ->
                                    ChoiceRow(label, settings.subtitleAssOverride == mode) { update(settings.copy(subtitleAssOverride = mode)) }
                                }

                                // Subtitle timing (issue #79, MX menu_subtitle sync/speed):
                                // persisted; applied live on mpv.
                                Spacer(Modifier.height(8.dp))
                                StepperRow(
                                    label = stringResource(R.string.subtitle_sync_header),
                                    valueText = if (settings.subtitleDelayMs == 0) "0.00s" else String.format(Locale.US, "%+.2fs", settings.subtitleDelayMs / 1000.0),
                                    minusContentDescription = stringResource(R.string.subtitle_sync_decrease),
                                    plusContentDescription = stringResource(R.string.subtitle_sync_increase),
                                    onMinus = { update(settings.copy(subtitleDelayMs = (settings.subtitleDelayMs - 100).coerceIn(-10000, 10000))) },
                                    onPlus = { update(settings.copy(subtitleDelayMs = (settings.subtitleDelayMs + 100).coerceIn(-10000, 10000))) },
                                    onReset = if (settings.subtitleDelayMs != 0) {
                                        { update(settings.copy(subtitleDelayMs = 0)) }
                                    } else null,
                                )
                                StepperRow(
                                    label = stringResource(R.string.subtitle_speed_header),
                                    valueText = String.format(Locale.US, "%.1f×", settings.subtitleSpeed),
                                    minusContentDescription = stringResource(R.string.subtitle_speed_decrease),
                                    plusContentDescription = stringResource(R.string.subtitle_speed_increase),
                                    onMinus = {
                                        val stepped = ((settings.subtitleSpeed * 10).roundToInt() - 1) / 10f
                                        update(settings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
                                    },
                                    onPlus = {
                                        val stepped = ((settings.subtitleSpeed * 10).roundToInt() + 1) / 10f
                                        update(settings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
                                    },
                                    onReset = if (settings.subtitleSpeed != 1.0f) {
                                        { update(settings.copy(subtitleSpeed = 1.0f)) }
                                    } else null,
                                )
                            }
                            // Issue #379: downloader settings, grouped like
                            // Seal's settings page — General, Format,
                            // Network, SponsorBlock. These rows moved here
                            // from the Download page; the dialogs are the
                            // same ones, so a change here and a change
                            // there were never two code paths. Summaries
                            // re-read on dismiss (the dialogs save through
                            // the repo).
                            "downloader" -> {
                                val downloader = remember(context) { MpxDownloader.getInstance(context) }
                                val updateState by downloader.updateState.collectAsState()
                                val ytdlpVersion by downloader.ytdlpVersion.collectAsState()
                                var showQuality by remember { mutableStateOf(false) }
                                var showOptions by remember { mutableStateOf(false) }
                                var showSponsorBlock by remember { mutableStateOf(false) }
                                var showDirectory by remember { mutableStateOf(false) }
                                var showAudioDirectory by remember { mutableStateOf(false) }
                                var showOutputTemplate by remember { mutableStateOf(false) }
                                var showCustomCommand by remember { mutableStateOf(false) }
                                var showCustomCommandDirectory by remember { mutableStateOf(false) }
                                // Issue #419: the download archive — the
                                // count loads once off the main thread
                                // and refreshes after clearing.
                                var showClearArchive by remember { mutableStateOf(false) }
                                var archiveCount by remember { mutableStateOf(0) }
                                LaunchedEffect(Unit) {
                                    archiveCount = withContext(Dispatchers.IO) {
                                        downloader.archiveEntryCount()
                                    }
                                }
                                // Issue #413: backup / restore — the
                                // export picker writes straight away;
                                // the import picker only stages the
                                // URI, the confirm dialog below asks
                                // first (import replaces the custom
                                // command + recent links).
                                var showImportConfirm by remember { mutableStateOf(false) }
                                var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
                                val exportLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.CreateDocument("application/json"),
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    scope.launch {
                                        val ok = DownloadBackup.writeBackup(
                                            context, uri, downloader, settingsRepository,
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                if (ok) R.string.download_backup_exported
                                                else R.string.download_backup_export_failed,
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                val importLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.OpenDocument(),
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    pendingImportUri = uri
                                    showImportConfirm = true
                                }
                                var showSubtitleLanguages by remember { mutableStateOf(false) }
                                var showSubtitleConvert by remember { mutableStateOf(false) }
                                var showAudioFormat by remember { mutableStateOf(false) }
                                var showFormatSelection by remember { mutableStateOf(false) }
                                var showYtDlpChannel by remember { mutableStateOf(false) }
                                var showYtDlpInterval by remember { mutableStateOf(false) }
                                var showAudioPresetFormat by remember { mutableStateOf(false) }
                                var showAudioPresetQuality by remember { mutableStateOf(false) }
                                val ytdlpUpdatedLabel = stringResource(R.string.download_ytdlp_updated)
                                val ytdlpUpToDateLabel = stringResource(R.string.download_ytdlp_up_to_date)
                                val ytdlpUpdateFailedLabel = stringResource(R.string.download_ytdlp_update_failed)
                                val ytdlpUpdateBusyLabel = stringResource(R.string.download_ytdlp_update_busy)

                                Text(stringResource(R.string.download_settings_general), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                PreferenceRow(
                                    title = stringResource(R.string.download_directory_title),
                                    subtitle = stringResource(R.string.download_directory_summary, MpxDownloader.sanitizeDownloadFolderName(settings.downloadDirectory)),
                                    icon = Icons.Filled.Folder,
                                    onClick = { showDirectory = true },
                                )
                                // Issue #394: a separate folder for
                                // audio-only downloads. Blank means
                                // the video folder above, so the
                                // subtitle shows the effective folder.
                                PreferenceRow(
                                    title = stringResource(R.string.download_audio_directory_title),
                                    subtitle = stringResource(
                                        R.string.download_directory_summary,
                                        MpxDownloader.sanitizeAudioFolderName(settings.downloadAudioDirectory).ifBlank {
                                            MpxDownloader.sanitizeDownloadFolderName(settings.downloadDirectory)
                                        },
                                    ),
                                    icon = Icons.Filled.Folder,
                                    onClick = { showAudioDirectory = true },
                                )
                                // Issue #398: custom output filename
                                // template — the -o template for plain
                                // downloads. The subtitle shows the
                                // template in use (blank = default).
                                PreferenceRow(
                                    title = stringResource(R.string.download_output_template_title),
                                    subtitle = settings.downloadOutputTemplate.trim().ifBlank { "%(title).200B.%(ext)s" },
                                    icon = Icons.Filled.Folder,
                                    onClick = { showOutputTemplate = true },
                                )
                                // Issue #389: group playlist downloads in
                                // a folder named after the playlist.
                                // Off by default — playlists land flat in
                                // the download folder, as today.
                                ToggleRow(stringResource(R.string.download_group_playlists), settings.downloadGroupPlaylists) { update(settings.copy(downloadGroupPlaylists = it)) }
                                // Issue #396: group downloads by site —
                                // a subfolder per extractor ("youtube",
                                // "soundcloud") under the download
                                // folder, above any playlist folder.
                                ToggleRow(stringResource(R.string.download_group_by_site), settings.downloadGroupBySite) { update(settings.copy(downloadGroupBySite = it)) }
                                // Issue #397: private download folder —
                                // finished files go to Download/.Seal/
                                // (hidden from galleries) instead of
                                // the video/audio folder. Distinct from
                                // private mode (#392, History only).
                                ToggleRow(stringResource(R.string.download_private_directory), settings.downloadPrivateDirectory) { update(settings.copy(downloadPrivateDirectory = it)) }
                                // Issue #392: private mode — downloads
                                // skip the History record. The file
                                // still downloads and still shows in
                                // Downloaded.
                                ToggleRow(stringResource(R.string.download_private_mode), settings.downloadPrivateMode) { update(settings.copy(downloadPrivateMode = it)) }
                                // Issue #393: SD-card downloads — the
                                // toggle flips the mode; flipping it
                                // ON with no folder picked opens the
                                // system folder picker instead, and
                                // picking a folder turns it on.
                                ToggleRow(stringResource(R.string.download_to_sd_card), settings.downloadToSdCard) {
                                    if (it && settings.downloadSdCardUri.isBlank()) {
                                        sdCardFolderLauncher?.launch(null)
                                    } else {
                                        update(settings.copy(downloadToSdCard = it))
                                    }
                                }
                                PreferenceRow(
                                    title = stringResource(R.string.download_sd_card_folder_title),
                                    subtitle = sdCardFolderDisplayName(context, settings.downloadSdCardUri)
                                        ?: stringResource(R.string.download_sd_card_folder_none),
                                    icon = Icons.Filled.Folder,
                                    onClick = { sdCardFolderLauncher?.launch(null) },
                                )
                                PreferenceRow(
                                    title = if (ytdlpVersion.isNotBlank()) stringResource(R.string.download_ytdlp_version, ytdlpVersion) else "yt-dlp",
                                    subtitle = null,
                                    icon = Icons.Filled.Update,
                                    onClick = {
                                        scope.launch {
                                            val message = when (downloader.updateYtDlpNow()) {
                                                YtDlpUpdateOutcome.UPDATED -> ytdlpUpdatedLabel
                                                YtDlpUpdateOutcome.UP_TO_DATE -> ytdlpUpToDateLabel
                                                YtDlpUpdateOutcome.FAILED -> ytdlpUpdateFailedLabel
                                                YtDlpUpdateOutcome.BUSY -> ytdlpUpdateBusyLabel
                                            }
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    },
                                    trailing = {
                                        Text(
                                            text = if (updateState == YtDlpUpdateState.UPDATING) stringResource(R.string.download_updating_ytdlp) else stringResource(R.string.download_update_ytdlp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MpxTheme.colors.primary,
                                        )
                                    },
                                )

                                // Issue #384: custom command — one line
                                // of yt-dlp options that replaces the
                                // format/subtitle/chapter options. The
                                // switch is the quick on/off; the row
                                // opens the editor (Seal's sample
                                // pre-filled, off by default).
                                ToggleRow(stringResource(R.string.download_custom_command_enabled), settings.customCommandEnabled) { update(settings.copy(customCommandEnabled = it)) }
                                PreferenceRow(
                                    title = stringResource(R.string.download_custom_command_title),
                                    subtitle = if (settings.customCommandEnabled) {
                                        settings.customCommand.ifBlank { stringResource(R.string.player_state_off) }
                                    } else {
                                        stringResource(R.string.player_state_off)
                                    },
                                    icon = Icons.Filled.Code,
                                    onClick = { showCustomCommand = true },
                                )
                                // Issue #415: the custom-command output
                                // folder — blank means the video folder.
                                // Shown even when the command is off, so
                                // the folder can be picked before the
                                // command is turned on.
                                PreferenceRow(
                                    title = stringResource(R.string.download_custom_command_directory_title),
                                    subtitle = if (settings.customCommandDirectory.isBlank()) {
                                        stringResource(R.string.download_custom_command_directory_default)
                                    } else {
                                        stringResource(R.string.download_custom_command_directory_summary, settings.customCommandDirectory)
                                    },
                                    icon = Icons.Filled.Folder,
                                    onClick = { showCustomCommandDirectory = true },
                                )
                                // Issue #410: saved command templates —
                                // named custom commands, managed on
                                // their own page. Picking one fills the
                                // single command slot above.
                                if (onOpenCommandTemplates != null) {
                                    PreferenceRow(
                                        title = stringResource(R.string.download_command_templates_title),
                                        subtitle = stringResource(R.string.download_command_templates_sub),
                                        icon = Icons.Filled.Code,
                                        onClick = onOpenCommandTemplates,
                                    )
                                }
                                // Issue #413: backup / restore of the
                                // downloader's data (history, recent
                                // links, custom command, templates) as
                                // one JSON file through the SAF pickers.
                                PreferenceRow(
                                    title = stringResource(R.string.download_backup_export_title),
                                    subtitle = stringResource(R.string.download_backup_export_sub),
                                    icon = Icons.Filled.Download,
                                    onClick = {
                                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        exportLauncher.launch("mpx-backup-$stamp.json")
                                    },
                                )
                                PreferenceRow(
                                    title = stringResource(R.string.download_backup_import_title),
                                    subtitle = stringResource(R.string.download_backup_import_sub),
                                    icon = Icons.Filled.Upload,
                                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                                )
                                // Issue #419: the download archive — which
                                // videos yt-dlp already recorded. Clearing
                                // it forgets them, so the next download of
                                // one runs for real instead of skipping as
                                // already downloaded.
                                PreferenceRow(
                                    title = stringResource(R.string.download_archive_title),
                                    subtitle = if (archiveCount == 0) {
                                        stringResource(R.string.download_archive_empty)
                                    } else {
                                        stringResource(R.string.download_archive_count, archiveCount)
                                    },
                                    icon = Icons.Filled.Archive,
                                    onClick = {
                                        if (archiveCount == 0) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.download_archive_empty),
                                                )
                                            }
                                        } else {
                                            showClearArchive = true
                                        }
                                    },
                                )

                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.download_settings_format), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                PreferenceRow(
                                    title = stringResource(R.string.download_quality_title),
                                    subtitle = if (settings.downloadCustomSorter.isNotBlank()) {
                                        settings.downloadCustomSorter
                                    } else {
                                        "${downloadResolutionLabel(settings.downloadResolution)} · ${downloadCodecLabel(settings.downloadCodec)}"
                                    },
                                    icon = Icons.Filled.HighQuality,
                                    onClick = { showQuality = true },
                                )
                                // Issue #408: format selection — Auto
                                // downloads immediately with the preset
                                // above, Custom opens the per-video
                                // format list for every video (today's
                                // behavior, the default).
                                PreferenceRow(
                                    title = stringResource(R.string.download_format_selection_title),
                                    subtitle = formatSelectionLabel(settings.downloadFormatSelection),
                                    icon = Icons.Filled.Tune,
                                    onClick = { showFormatSelection = true },
                                )
                                ToggleRow(stringResource(R.string.download_audio_only), settings.downloadAudioOnly) { update(settings.copy(downloadAudioOnly = it)) }
                                // Issue #390: audio format for audio-only
                                // downloads — mp3 by default, the old
                                // hardcoded value.
                                PreferenceRow(
                                    title = stringResource(R.string.download_audio_format_title),
                                    subtitle = audioFormatLabel(settings.downloadAudioFormat),
                                    icon = Icons.Filled.MusicNote,
                                    onClick = { showAudioFormat = true },
                                )
                                // Issue #395: keep the original audio
                                // format — no conversion. When on, the
                                // audio branch passes -S with the
                                // preset codec/bitrate instead of
                                // --audio-format; the two rows below
                                // only show while it is on.
                                ToggleRow(stringResource(R.string.download_audio_preset_title), settings.downloadUseCustomAudioPreset) { update(settings.copy(downloadUseCustomAudioPreset = it)) }
                                if (settings.downloadUseCustomAudioPreset) {
                                    PreferenceRow(
                                        title = stringResource(R.string.download_audio_preset_format_title),
                                        subtitle = audioPresetFormatLabel(settings.downloadAudioPresetFormat),
                                        icon = Icons.Filled.MusicNote,
                                        onClick = { showAudioPresetFormat = true },
                                    )
                                    PreferenceRow(
                                        title = stringResource(R.string.download_audio_preset_quality_title),
                                        subtitle = audioPresetQualityLabel(settings.downloadAudioPresetQuality),
                                        icon = Icons.Filled.MusicNote,
                                        onClick = { showAudioPresetQuality = true },
                                    )
                                }
                                ToggleRow(stringResource(R.string.download_subtitles), settings.downloadSubtitles) { update(settings.copy(downloadSubtitles = it)) }
                                // Issue #382: subtitle options — languages,
                                // auto-subs, convert format, embed/keep.
                                PreferenceRow(
                                    title = stringResource(R.string.download_subtitle_languages),
                                    subtitle = settings.downloadSubtitleLanguages,
                                    icon = Icons.Filled.Language,
                                    onClick = { showSubtitleLanguages = true },
                                )
                                ToggleRow(stringResource(R.string.download_auto_subtitles), settings.downloadAutoSubtitles) { update(settings.copy(downloadAutoSubtitles = it)) }
                                PreferenceRow(
                                    title = stringResource(R.string.download_convert_subtitles),
                                    subtitle = when (settings.downloadConvertSubtitles) {
                                        "srt" -> "SRT"
                                        "vtt" -> "VTT"
                                        "ass" -> "ASS"
                                        else -> stringResource(R.string.download_convert_subtitles_none)
                                    },
                                    icon = Icons.Filled.Subtitles,
                                    onClick = { showSubtitleConvert = true },
                                )
                                ToggleRow(stringResource(R.string.download_embed_subtitles), settings.downloadEmbedSubtitles) { update(settings.copy(downloadEmbedSubtitles = it)) }
                                ToggleRow(stringResource(R.string.download_keep_subtitles), settings.downloadKeepSubtitles, enabled = settings.downloadEmbedSubtitles) { update(settings.copy(downloadKeepSubtitles = it)) }
                                // Issue #385: advanced video flags — merge
                                // to MKV, audio multistreams, thumbnail file.
                                ToggleRow(stringResource(R.string.download_merge_to_mkv), settings.downloadMergeToMkv) { update(settings.copy(downloadMergeToMkv = it)) }
                                ToggleRow(stringResource(R.string.download_audio_multistreams), settings.downloadAudioMultistreams) { update(settings.copy(downloadAudioMultistreams = it)) }
                                ToggleRow(stringResource(R.string.download_write_thumbnail), settings.downloadWriteThumbnail) { update(settings.copy(downloadWriteThumbnail = it)) }
                                // Issue #391: embed the cover art into
                                // the file itself. Lives next to "Save
                                // thumbnail file" — one embeds, one
                                // keeps the sidecar.
                                ToggleRow(stringResource(R.string.download_embed_thumbnail), settings.downloadEmbedThumbnail) { update(settings.copy(downloadEmbedThumbnail = it)) }
                                // Issue #400: crop the embedded cover
                                // art to a square (Seal's CROP_ARTWORK).
                                // Only meaningful with embed-thumbnail
                                // on — the toggle is disabled otherwise.
                                ToggleRow(stringResource(R.string.download_crop_artwork_title), settings.downloadCropArtwork, enabled = settings.downloadEmbedThumbnail) { update(settings.copy(downloadCropArtwork = it)) }

                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.download_settings_network), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
                                PreferenceRow(
                                    title = stringResource(R.string.download_options_title),
                                    subtitle = downloadOptionsSummary(settings),
                                    icon = Icons.Filled.Tune,
                                    onClick = { showOptions = true },
                                )
                                // Issue #380: metered gate — off by
                                // default, like Seal's CELLULAR_DOWNLOAD.
                                ToggleRow(stringResource(R.string.download_allow_metered), settings.downloadAllowMetered) { update(settings.copy(downloadAllowMetered = it)) }
                                // Issue #402: verbose debug logging — a
                                // diagnostics knob, off by default; the
                                // logs get very noisy when it is on.
                                ToggleRow(stringResource(R.string.download_verbose_logging_title), settings.downloadVerboseLogging) { update(settings.copy(downloadVerboseLogging = it)) }
                                // Issue #404: download notifications —
                                // on by default; off leaves only the
                                // static foreground notification.
                                ToggleRow(stringResource(R.string.download_notifications_title), settings.downloadNotifications) { update(settings.copy(downloadNotifications = it)) }
                                // Issue #409: saved cookie profiles — the
                                // sign-ins kept for restricted videos.
                                // Opens the profiles page (newest first,
                                // delete per row); profiles are created
                                // from the Download tab after a sign-in.
                                if (onOpenCookieProfiles != null) {
                                    PreferenceRow(
                                        title = stringResource(R.string.download_cookie_profiles_title),
                                        subtitle = stringResource(R.string.download_cookie_profiles_sub),
                                        icon = Icons.Filled.Key,
                                        onClick = onOpenCookieProfiles,
                                    )
                                }
                                // Issue #405: yt-dlp update channel +
                                // check interval — the settings existed
                                // from #333 but had no UI. The channel
                                // is read by updateYtDlp(); the interval
                                // gates maybeAutoUpdateYtDlp().
                                PreferenceRow(
                                    title = stringResource(R.string.download_update_channel_title),
                                    subtitle = ytdlpUpdateChannelLabel(settings.ytdlpUpdateChannel),
                                    icon = Icons.Filled.Refresh,
                                    onClick = { showYtDlpChannel = true },
                                )
                                PreferenceRow(
                                    title = stringResource(R.string.download_update_interval_title),
                                    subtitle = ytdlpUpdateIntervalLabel(settings),
                                    icon = Icons.Filled.Refresh,
                                    onClick = { showYtDlpInterval = true },
                                )

                                Spacer(Modifier.height(16.dp))
                                PreferenceRow(
                                    title = stringResource(R.string.download_sponsorblock_title),
                                    subtitle = if (settings.downloadSponsorBlock && settings.downloadSponsorBlockCategories.isNotBlank()) {
                                        settings.downloadSponsorBlockCategories
                                    } else {
                                        stringResource(R.string.player_state_off)
                                    },
                                    icon = Icons.Filled.ContentCut,
                                    onClick = { showSponsorBlock = true },
                                )

                                if (showQuality) {
                                    DownloadQualityDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showQuality = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showOptions) {
                                    DownloadOptionsDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showOptions = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showSponsorBlock) {
                                    DownloadSponsorBlockDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showSponsorBlock = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showDirectory) {
                                    DownloadDirectoryDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showDirectory = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showAudioDirectory) {
                                    AudioDirectoryDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showAudioDirectory = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showOutputTemplate) {
                                    OutputTemplateDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showOutputTemplate = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showCustomCommand) {
                                    CustomCommandDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showCustomCommand = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showCustomCommandDirectory) {
                                    CustomCommandDirectoryDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showCustomCommandDirectory = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showClearArchive) {
                                    MpxDialog(
                                        title = stringResource(R.string.download_archive_clear_title),
                                        onDismiss = { showClearArchive = false },
                                        confirmText = stringResource(R.string.download_archive_clear),
                                        onConfirm = {
                                            showClearArchive = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    downloader.clearArchive()
                                                }
                                                archiveCount = withContext(Dispatchers.IO) {
                                                    downloader.archiveEntryCount()
                                                }
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.download_archive_cleared),
                                                )
                                            }
                                        },
                                        dismissText = stringResource(R.string.action_cancel),
                                        body = {
                                            Text(
                                                stringResource(R.string.download_archive_clear_body),
                                                color = MpxTheme.colors.textSecondary,
                                            )
                                        },
                                    )
                                }
                                if (showImportConfirm) {
                                    MpxDialog(
                                        title = stringResource(R.string.download_backup_import_confirm_title),
                                        onDismiss = {
                                            showImportConfirm = false
                                            pendingImportUri = null
                                        },
                                        confirmText = stringResource(R.string.download_backup_import_title),
                                        onConfirm = {
                                            val uri = pendingImportUri
                                            showImportConfirm = false
                                            pendingImportUri = null
                                            if (uri != null) {
                                                scope.launch {
                                                    when (val result = DownloadBackup.readBackup(
                                                        context, uri, downloader, settingsRepository,
                                                    )) {
                                                        is DownloadBackup.ImportResult.Success -> {
                                                            settings = settingsRepository.load()
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(
                                                                    R.string.download_backup_imported,
                                                                    result.historyImported,
                                                                    result.templatesImported,
                                                                ),
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                        DownloadBackup.ImportResult.InvalidFile -> {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.download_backup_import_failed),
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        dismissText = stringResource(R.string.action_cancel),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.download_backup_import_confirm_body),
                                            color = MpxTheme.colors.textSecondary,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                                if (showSubtitleLanguages) {
                                    SubtitleLanguagesDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showSubtitleLanguages = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showSubtitleConvert) {
                                    SubtitleConvertDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showSubtitleConvert = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showAudioFormat) {
                                    AudioFormatDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showAudioFormat = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showFormatSelection) {
                                    FormatSelectionModeDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showFormatSelection = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showYtDlpChannel) {
                                    YtDlpUpdateChannelDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showYtDlpChannel = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showYtDlpInterval) {
                                    YtDlpUpdateIntervalDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showYtDlpInterval = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showAudioPresetFormat) {
                                    AudioPresetFormatDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showAudioPresetFormat = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                                if (showAudioPresetQuality) {
                                    AudioPresetQualityDialog(
                                        settingsRepository = settingsRepository,
                                        onDismiss = {
                                            showAudioPresetQuality = false
                                            settings = settingsRepository.load()
                                        },
                                    )
                                }
                            }
                            // #158: Advanced = the old Development section
                            // plus the reset button from the old General
                            // category (its only real setting).
                            "advanced" -> {
                                OutlinedButton(onClick = { showResetDialog = true }) {
                                    Text(stringResource(R.string.general_reset_button), color = MpxTheme.colors.primary)
                                }
                                if (showResetDialog) {
                                    MpxDialog(
                                        title = stringResource(R.string.general_reset_button),
                                        onDismiss = { showResetDialog = false },
                                        confirmText = stringResource(R.string.general_reset_button),
                                        onConfirm = {
                                            // Issue #240: Reset turns
                                            // backgroundPlay off — run the
                                            // same stop side effect the
                                            // toggle runs, or a background
                                            // session keeps playing while
                                            // the setting reads off. Capture
                                            // the pre-reset value first:
                                            // update() replaces `settings`.
                                            // Issue #228: commit the reset
                                            // synchronously, as before.
                                            val wasBackgroundPlay = settings.backgroundPlay
                                            update(AppSettings(), commit = true)
                                            if (wasBackgroundPlay) {
                                                stopBackgroundPlayback()
                                            }
                                            showResetDialog = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.general_reset_done))
                                            }
                                        },
                                        dismissText = stringResource(R.string.action_cancel),
                                        body = {
                                            Text(
                                                stringResource(R.string.reset_confirm_message),
                                                color = MpxTheme.colors.textSecondary,
                                            )
                                        },
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.general_language_hint),
                                    style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary,
                                )
                                Spacer(Modifier.height(16.dp))
                                DevelopmentSettingsSection(
                                    context = context,
                                    playbackManager = actualPlaybackManager,
                                    settings = settings,
                                    cachedDiagnostics = devDiagnostics,
                                    onDiagnosticsLoaded = { devDiagnostics = it },
                                )
                            }
                            "about" -> {
                                Text(
                                    stringResource(R.string.about_app_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MpxTheme.colors.textSecondary,
                                )
                                Spacer(Modifier.height(10.dp))
                                if (onOpenAboutBeta != null) {
                                    OutlinedButton(
                                        onClick = onOpenAboutBeta,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MpxShapes.small,
                                    ) {
                                        Icon(
                                            Icons.Filled.Info,
                                            null,
                                            tint = MpxTheme.colors.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.about_beta_open_page_button),
                                            color = MpxTheme.colors.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                // Issue #414: manual in-app self-update
                                // — the row shows the installed version
                                // until a check runs; an available update
                                // opens the download/install dialog.
                                var updateStatus by remember { mutableStateOf(UpdateCheckStatus.IDLE) }
                                var availableRelease by remember { mutableStateOf<AppUpdateChecker.Release?>(null) }
                                var showUpdateDialog by remember { mutableStateOf(false) }
                                Spacer(Modifier.height(16.dp))
                                PreferenceRow(
                                    title = stringResource(R.string.app_update_check_title),
                                    subtitle = when (updateStatus) {
                                        UpdateCheckStatus.IDLE -> stringResource(
                                            R.string.app_update_current_version,
                                            AppUpdateChecker.currentVersion(),
                                        )
                                        UpdateCheckStatus.CHECKING -> stringResource(R.string.app_update_checking)
                                        UpdateCheckStatus.UP_TO_DATE -> stringResource(R.string.app_update_up_to_date)
                                        UpdateCheckStatus.AVAILABLE -> stringResource(
                                            R.string.app_update_available,
                                            availableRelease?.tag.orEmpty(),
                                        )
                                        UpdateCheckStatus.FAILED -> stringResource(R.string.app_update_check_failed)
                                    },
                                    icon = Icons.Filled.Update,
                                    onClick = {
                                        if (updateStatus == UpdateCheckStatus.CHECKING) return@PreferenceRow
                                        updateStatus = UpdateCheckStatus.CHECKING
                                        scope.launch {
                                            when (val result = AppUpdateChecker.checkForUpdate()) {
                                                is AppUpdateChecker.CheckResult.UpToDate -> {
                                                    updateStatus = UpdateCheckStatus.UP_TO_DATE
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.app_update_up_to_date),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                is AppUpdateChecker.CheckResult.Available -> {
                                                    availableRelease = result.release
                                                    updateStatus = UpdateCheckStatus.AVAILABLE
                                                    showUpdateDialog = true
                                                }
                                                AppUpdateChecker.CheckResult.Failed -> {
                                                    updateStatus = UpdateCheckStatus.FAILED
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.app_update_check_failed),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                )
                                if (showUpdateDialog) {
                                    availableRelease?.let { release ->
                                        AppUpdateDialog(
                                            release = release,
                                            onDismiss = { showUpdateDialog = false },
                                        )
                                    }
                                }
                                // Issue #418: automatic update check on
                                // launch — off by default, like Seal.
                                // The check itself is silent; an
                                // available release opens the same
                                // dialog as the manual check above.
                                ToggleRow(stringResource(R.string.app_update_auto_check), settings.downloadAutoUpdateCheck) { update(settings.copy(downloadAutoUpdateCheck = it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) MpxTheme.colors.textPrimary else MpxTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = MpxTheme.colors.primary, checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (enabled) MpxTheme.colors.textPrimary else MpxTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChoiceRowWithSubtitle(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, color = MpxTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MusicTabsSettingsSection(
    settings: AppSettings,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_tabs_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MpxTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val lastActiveSub = stringResource(R.string.settings_tab_last_active_sub)
    val isOnlyOneVisible = settings.visibleMusicTabsCount <= 1

    TabToggleRow(
        label = stringResource(R.string.music_tab_tracks),
        checked = settings.showMusicTracksTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicTracksTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("tracks", settings.showMusicTracksTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_playlists),
        checked = settings.showMusicPlaylistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicPlaylistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("playlists", settings.showMusicPlaylistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_albums),
        checked = settings.showMusicAlbumsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicAlbumsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("albums", settings.showMusicAlbumsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_artists),
        checked = settings.showMusicArtistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicArtistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("artists", settings.showMusicArtistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_folders),
        checked = settings.showMusicFoldersTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicFoldersTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("folders", settings.showMusicFoldersTab) },
    )
}

@Composable
private fun TabToggleRow(
    label: String,
    checked: Boolean,
    isLastVisible: Boolean = false,
    subtitle: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}

private data class DevDiagnosticsData(
    val appId: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val buildType: String = "",
    val targetSdk: String = "",
    val minSdk: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val dbPath: String = "",
    val dbSizeFormatted: String = "",
    val dbWalFormatted: String = "",
    val dbRecordStats: String = "",
    val hasVideoPerm: Boolean = false,
    val hasAudioPerm: Boolean = false,
    val storageVolumes: String = "",
    val videoFoldersCount: Int = 0,
    val videosCount: Int = 0,
    val audioTracksCount: Int = 0,
    val audioFoldersCount: Int = 0,
    val audioAlbumsCount: Int = 0,
    val audioArtistsCount: Int = 0,
    val playlistsCount: Int = 0,
    val lastScannedTime: String = "",
)

@Composable
private fun DevelopmentSettingsSection(
    context: Context,
    playbackManager: PlaybackManager,
    settings: AppSettings,
    // Issue #299: the loaded stats live in SettingsScreen, not here —
    // this section is a LazyColumn item whose remember was dropped
    // whenever it left composition, re-running the five MediaStore scans
    // on every return. The cache is null until the first load (or a
    // fresh Settings visit), non-null afterwards.
    cachedDiagnostics: DevDiagnosticsData?,
    onDiagnosticsLoaded: (DevDiagnosticsData) -> Unit,
) {
    val playbackState by playbackManager.playbackState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshStats() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            val stats = loadDevDiagnostics(context)
            onDiagnosticsLoaded(stats)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        // Issue #299: only load when there is nothing cached — scrolling
        // the section out of composition and back, or switching categories
        // inside Settings, reuses the cached stats instead of re-scanning.
        // The Refresh button below still calls refreshStats() directly.
        if (cachedDiagnostics == null) refreshStats()
    }

    val stats = cachedDiagnostics

    // Section 1: App & Build Information
    DiagnosticSection(title = stringResource(R.string.dev_section_app_build)) {
        DiagnosticRow(
            label = stringResource(R.string.dev_label_package),
            value = stats?.appId ?: context.packageName,
            isMonospace = true,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_version),
            value = stats?.versionName ?: BuildConfig.VERSION_NAME,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_build_code),
            value = stats?.versionCode ?: BuildConfig.VERSION_CODE.toString(),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_build_type),
            value = stats?.buildType ?: "Debug",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_target_sdk),
            value = stats?.targetSdk ?: "34",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_min_sdk),
            value = stats?.minSdk ?: "24",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_device),
            value = stats?.deviceModel ?: "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_os_version),
            value = stats?.osVersion ?: "Android ${Build.VERSION.RELEASE}",
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 2: Room SQLite Database
    DiagnosticSection(title = stringResource(R.string.dev_section_database)) {
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_name),
            value = "mpx_player.db (Room v1)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_path),
            value = stats?.dbPath ?: context.getDatabasePath("mpx_player.db").absolutePath,
            isMonospace = true,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_size),
            value = stats?.dbSizeFormatted ?: stringResource(R.string.dev_val_refreshing),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_wal_size),
            value = stats?.dbWalFormatted ?: stringResource(R.string.dev_val_refreshing),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_records),
            value = stats?.dbRecordStats ?: stringResource(R.string.dev_val_refreshing),
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 3: MediaStore Library Scan
    DiagnosticSection(title = stringResource(R.string.dev_section_media_scan)) {
        val permVideo = if (stats?.hasVideoPerm == true) {
            stringResource(R.string.dev_val_perm_granted)
        } else {
            stringResource(R.string.dev_val_perm_denied)
        }
        val permAudio = if (stats?.hasAudioPerm == true) {
            stringResource(R.string.dev_val_perm_granted)
        } else {
            stringResource(R.string.dev_val_perm_denied)
        }
        DiagnosticRow(
            label = stringResource(R.string.dev_label_permissions),
            value = stringResource(R.string.dev_val_perm_video, permVideo, permAudio),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_scan_volumes),
            value = stats?.storageVolumes ?: "primary",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_video_folders),
            value = stringResource(R.string.dev_count_folders, stats?.videoFoldersCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_scanned_videos),
            value = stringResource(R.string.dev_count_videos, stats?.videosCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_tracks),
            value = stringResource(R.string.dev_count_tracks, stats?.audioTracksCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_folders),
            value = stringResource(R.string.dev_count_folders, stats?.audioFoldersCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_albums),
            value = stringResource(R.string.dev_count_albums, stats?.audioAlbumsCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_artists),
            value = stringResource(R.string.dev_count_artists, stats?.audioArtistsCount ?: 0),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_playlists),
            value = stringResource(R.string.dev_count_playlists, stats?.playlistsCount ?: 0),
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 4: Decoder & Playback Engine
    DiagnosticSection(title = stringResource(R.string.dev_section_playback_engine)) {
        val configuredDecoderDesc = when (settings.decoderMode) {
            "HW" -> stringResource(R.string.dev_decoder_hw)
            "HW+" -> stringResource(R.string.dev_decoder_hwplus)
            "SW" -> stringResource(R.string.dev_decoder_sw)
            else -> settings.decoderMode
        }
        val playerStateText = when (playbackState.playbackState) {
            PlayerEngine.STATE_READY -> if (playbackState.isPlaying) stringResource(R.string.dev_state_playing) else stringResource(R.string.dev_state_paused)
            PlayerEngine.STATE_BUFFERING -> stringResource(R.string.dev_state_buffering)
            PlayerEngine.STATE_ENDED -> stringResource(R.string.dev_state_ended)
            PlayerEngine.STATE_IDLE -> stringResource(R.string.dev_state_idle)
            else -> stringResource(R.string.dev_state_idle)
        }
        val activeMediaText = playbackState.currentItem?.let {
            "${it.title} (${if (it.isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_audio)})"
        } ?: stringResource(R.string.dev_val_none_idle)

        val activeCodec = playbackState.activeDecoderName ?: stringResource(R.string.dev_val_none_idle)
        val isSwCodec = playbackState.activeDecoderName?.let { MpxMediaCodecSelector.isSoftwareCodecName(it) }
        val codecType = when {
            playbackState.activeDecoderName == null -> stringResource(R.string.dev_configured_for, settings.decoderMode)
            isSwCodec == true -> stringResource(R.string.dev_val_codec_sw)
            else -> stringResource(R.string.dev_val_codec_hw)
        }
        val fallbackNote = playbackState.decoderFallbackNote ?: stringResource(R.string.dev_val_direct_codec)

        DiagnosticRow(
            label = stringResource(R.string.dev_label_engine_name),
            value = "libmpv (mpv-android-lib 0.1.12)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_engine_type),
            value = "C/C++ Native libmpv Core",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_decoder_mode),
            value = "${settings.decoderMode} ($configuredDecoderDesc)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_player_state),
            value = playerStateText,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_active_media),
            value = activeMediaText,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_active_decoder),
            value = activeCodec,
            isMonospace = playbackState.activeDecoderName != null,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_codec_type),
            value = codecType,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_decoder_fallback),
            value = fallbackNote,
        )
        // Playback metrics: measured values for the mpv engine, refreshed
        // by the position ticker. "—" means not observed yet (e.g. no video
        // played).
        val metrics = playbackState.playbackMetrics
        DiagnosticRow(
            label = stringResource(R.string.dev_label_metrics_ready),
            value = metrics?.lastTimeToReadyMs?.let { "$it ms" } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_metrics_first_frame),
            value = metrics?.lastTimeToFirstFrameMs?.let { "$it ms" } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_metrics_seek),
            value = metrics?.lastSeekLatencyMs?.let { "$it ms" } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_metrics_fallbacks),
            value = metrics?.let { "${it.decoderFallbackCount}" } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_metrics_errors),
            value = metrics?.let { "${it.hardErrorCount}" } ?: "—",
        )
        // mpv load stage (issue #96 follow-up): step-level facts for the
        // current mpv load, refreshed by the same ticker. "—" before any
        // load. How to read a stuck mpv load: initialized=no → libmpv never
        // started (init failure, see the banner); surface=no → PlayerScreen
        // never attached a surface, so the load is still deferred; load=no
        // → loadfile was never issued; last event=none with load=yes →
        // libmpv swallowed the load without any event (the infinite-spinner
        // case) — that is when the 20s watchdog fires and the banner names
        // this stage.
        val stage = playbackState.mpvLoadStage
        val yes = stringResource(R.string.dev_val_yes)
        val no = stringResource(R.string.dev_val_no)
        DiagnosticRow(
            label = stringResource(R.string.dev_label_mpv_initialized),
            value = stage?.let { if (it.initialized) yes else no } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_mpv_surface),
            value = stage?.let { if (it.surfaceAttached) yes else no } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_mpv_load_issued),
            value = stage?.let { if (it.loadIssued) yes else no } ?: "—",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_mpv_last_event),
            value = stage?.lastEvent ?: "—",
            isMonospace = stage != null,
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 5: Diagnostics export — copy a short summary, share a
    // detailed diagnostics file (playback/DiagnosticsReport.kt), or share
    // the mpv engine log file. Values are a snapshot of the current
    // playback state; nulls render as "not observed".
    DiagnosticSection(title = stringResource(R.string.dev_section_diagnostics_export)) {
        OutlinedButton(
            onClick = {
                val text = buildDiagnosticsSummary(playbackState, settings)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("MPx diagnostics", text))
                Toast.makeText(context, context.getString(R.string.dev_val_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MpxShapes.small,
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MpxTheme.colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dev_btn_copy_summary),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                try {
                    val text = buildDiagnosticsReport(playbackState, settings, context.packageName)
                    val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val file = File(dir, "mpx-diagnostics-$stamp.txt")
                    file.writeText(text)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "MPx diagnostics")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.dev_val_share_failed), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MpxShapes.small,
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MpxTheme.colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dev_btn_share_file),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    MpvLogShareButton(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.dev_btn_share_mpv_log),
    )

    Spacer(Modifier.height(10.dp))

    // Refresh Diagnostics Button
    OutlinedButton(
        onClick = { refreshStats() },
        enabled = !isRefreshing,
        modifier = Modifier.fillMaxWidth(),
        shape = MpxShapes.small,
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MpxTheme.colors.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isRefreshing) stringResource(R.string.dev_val_refreshing) else stringResource(R.string.dev_btn_refresh_diagnostics),
            color = MpxTheme.colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    stats?.lastScannedTime?.takeIf { it.isNotBlank() }?.let { time ->
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dev_label_last_updated, time),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MpxTheme.colors.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.surfaceElevated)
                .border(0.5.dp, MpxTheme.colors.border.copy(alpha = 0.5f), MpxShapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = if (isMonospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private suspend fun loadDevDiagnostics(context: Context): DevDiagnosticsData = withContext(Dispatchers.IO) {
    val pInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Exception) {
        null
    }
    val versionName = pInfo?.versionName ?: BuildConfig.VERSION_NAME
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pInfo?.longVersionCode?.toString() ?: BuildConfig.VERSION_CODE.toString()
    } else {
        @Suppress("DEPRECATION")
        pInfo?.versionCode?.toString() ?: BuildConfig.VERSION_CODE.toString()
    }
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val buildType = if (isDebug) "Debug" else "Release"
    val targetSdk = context.applicationInfo.targetSdkVersion.toString()
    val minSdk = "24 (Android 7.0 Nougat)"
    val device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
    val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    val dbFile = context.getDatabasePath("mpx_player.db")
    val walFile = File(dbFile.path + "-wal")
    val shmFile = File(dbFile.path + "-shm")
    val dbExists = dbFile.exists()
    val dbSizeBytes = if (dbExists) dbFile.length() else 0L
    val walSizeBytes = if (walFile.exists()) walFile.length() else 0L
    val shmSizeBytes = if (shmFile.exists()) shmFile.length() else 0L
    val totalDbBytes = dbSizeBytes + walSizeBytes + shmSizeBytes
    val formattedSize = if (dbExists) {
        val formatted = Formatter.formatFileSize(context, totalDbBytes)
        "$formatted ($totalDbBytes bytes)"
    } else {
        context.getString(R.string.dev_label_db_not_created)
    }
    val walFormatted = if (walFile.exists()) {
        val formatted = Formatter.formatFileSize(context, walSizeBytes)
        "$formatted ($walSizeBytes bytes)"
    } else {
        "0 B (no active WAL log)"
    }

    // Built inside loadDevDiagnostics (a plain function, not a
    // @Composable): context.getString, not stringResource.
    val recordCounts = try {
        val db = MpxDatabase.getInstance(context)
        listOf(
            db.favouriteDao().getCount(),
            db.playbackHistoryDao().getCount(),
            db.playlistDao().getPlaylistsWithCount().size,
            db.playbackResumeDao().getAllResumePositions().size,
        )
    } catch (_: Exception) {
        listOf(0, 0, 0, 0)
    }
    val recordStats = context.getString(R.string.dev_db_records_summary, recordCounts[0], recordCounts[1], recordCounts[2], recordCounts[3])

    val hasVideoPerm = MediaPermissions.hasVideoPermission(context)
    val hasAudioPerm = MediaPermissions.hasAudioPermission(context)
    val mediaRepo = MediaStoreRepository(context)

    val volumes = try {
        val volNames = mediaRepo.getExternalVolumeNames()
        if (volNames.isNotEmpty()) volNames.joinToString(", ") else "primary (internal)"
    } catch (_: Exception) {
        "primary (internal)"
    }

    var videoFolders = 0
    var videoCount = 0
    var audioTracks = 0
    var audioFolders = 0
    var audioAlbums = 0
    var audioArtists = 0
    var playlists = 0

    if (hasVideoPerm) {
        try {
            val folders = mediaRepo.getMediaFolders(showAllFolders = true)
            videoFolders = folders.size
            videoCount = folders.sumOf { it.videoCount }
        } catch (_: Exception) {}
    }

    if (hasAudioPerm) {
        try {
            audioTracks = mediaRepo.getAudioTracks().size
            audioFolders = mediaRepo.getAudioFolders().size
            audioAlbums = mediaRepo.getAudioAlbums().size
            audioArtists = mediaRepo.getAudioArtists().size
            playlists = mediaRepo.getPlaylists().size
        } catch (_: Exception) {}
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    DevDiagnosticsData(
        appId = context.packageName,
        versionName = versionName,
        versionCode = versionCode,
        buildType = buildType,
        targetSdk = targetSdk,
        minSdk = minSdk,
        deviceModel = device,
        osVersion = osVersion,
        dbPath = dbFile.absolutePath,
        dbSizeFormatted = formattedSize,
        dbWalFormatted = walFormatted,
        dbRecordStats = recordStats,
        hasVideoPerm = hasVideoPerm,
        hasAudioPerm = hasAudioPerm,
        storageVolumes = volumes,
        videoFoldersCount = videoFolders,
        videosCount = videoCount,
        audioTracksCount = audioTracks,
        audioFoldersCount = audioFolders,
        audioAlbumsCount = audioAlbums,
        audioArtistsCount = audioArtists,
        playlistsCount = playlists,
        lastScannedTime = timeFormat.format(Date()),
    )
}

/**
 * Issue #393: the picked SD-card folder's display name, for the
 * folder row's subtitle — the tree document's own name, not the
 * raw tree URI. Null when no folder is picked or the URI can no
 * longer be read (permission revoked).
 */
private fun sdCardFolderDisplayName(context: Context, treeUriString: String): String? {
    if (treeUriString.isBlank()) return null
    return try {
        val treeUri = Uri.parse(treeUriString)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (col < 0) null else cursor.getString(col)?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}
