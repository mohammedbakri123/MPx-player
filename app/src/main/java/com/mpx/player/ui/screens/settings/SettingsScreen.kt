package com.mpx.player.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.BuildConfig
import com.mpx.player.R
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.playback.PlaybackService
import com.mpx.player.ui.screens.settings.categories.AboutCategorySection
import com.mpx.player.ui.screens.settings.categories.AdvancedCategorySection
import com.mpx.player.ui.screens.settings.categories.AppearanceCategorySection
import com.mpx.player.ui.screens.settings.categories.DecoderCategorySection
import com.mpx.player.ui.screens.settings.categories.GesturesCategorySection
import com.mpx.player.ui.screens.settings.categories.LanguageCategorySection
import com.mpx.player.ui.screens.settings.categories.ListCategorySection
import com.mpx.player.ui.screens.settings.categories.PipCategorySection
import com.mpx.player.ui.screens.settings.categories.PlayerCategorySection
import com.mpx.player.ui.screens.settings.categories.SubtitleCategorySection
import com.mpx.player.ui.screens.settings.categories.TabsCategorySection
import com.mpx.player.ui.screens.settings.components.DevDiagnosticsData
import com.mpx.player.ui.screens.settings.components.SettingsCategoryList
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.LocaleHelper
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenOverlaySettings: (() -> Unit)? = null,
    onOpenAdvancedSettings: (() -> Unit)? = null,
    onOpenAboutBeta: (() -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    onPaletteChanged: ((String) -> Unit)? = null,
    onThemeColorChanged: ((String) -> Unit)? = null,
    playbackCoordinator: PlaybackCoordinator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Migration Phase 2e: settings, search, and drill-down state live
    // in SettingsViewModel now — the screen holds no repository and
    // builds none. PlaybackCoordinator arrives as a parameter from
    // MainActivity (Koin), never via getInstance.
    val viewModel: SettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsState()

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
                viewModel.reloadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Drill-down navigation (#77): null shows the category list, otherwise
    // the selected category's own sub-screen, like MX's per-header fragments.
    val selectedId by viewModel.selectedCategoryId.collectAsState()
    // mpvEx-style search (#158): true shows the working settings search
    // page; back closes search before leaving the category list.
    val isSearching by viewModel.isSearching.collectAsState()
    // Issue #303: the search query lives in the ViewModel, not in
    // PreferencesSearchScreen — opening a result sets isSearching =
    // false, which removes the search screen from the composition and
    // discards any state it owns (even rememberSaveable, cf. #255's
    // MusicScreen query, which stays composed across tab switches), so
    // a query kept inside it would reset every time a result category
    // is opened. Hoisted to the ViewModel next to isSearching /
    // selectedId, it survives rotation and returning from a category,
    // and resets when Settings itself is left, like isSearching /
    // selectedId.
    val settingsSearchQuery by viewModel.searchQuery.collectAsState()

    // System back mirrors the TopAppBar icon: search closes first, then a
    // drilled-down category returns to the category list (#124); only at
    // the root list does it leave Settings.
    BackHandler(enabled = isSearching || selectedId != null) {
        viewModel.handleBack()
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
    //
    // The language / theme / palette / theme-color callbacks and the
    // decoder-mode application stay here, in this thin wrapper around
    // viewModel.updateSettings(): they are MainActivity wiring and a
    // PlaybackCoordinator call, not settings state.
    fun update(next: AppSettings, commit: Boolean = false) {
        val oldLanguage = settings.appLanguage
        val oldTheme = settings.appTheme
        val oldPalette = settings.colorPalette
        val oldThemeColor = settings.themeColor
        val oldDecoder = settings.decoderMode
        viewModel.updateSettings(next, commit = commit)
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
            val decMode = DecoderMode.values().find { it.label == next.decoderMode } ?: DecoderMode.HW
            playbackCoordinator.setDecoderMode(decMode)
        }
    }

    // Issue #240: turning background play off must stop any background
    // session — the same three calls the background-play toggle and the
    // player toggle run when disabling. Shared here so Reset and the
    // toggle can never drift apart again.
    fun stopBackgroundPlayback() {
        PlaybackService.stop(context)
        playbackCoordinator.setVideoBackgroundActive(false)
        playbackCoordinator.setBackgroundPlaying(false)
    }

    fun toggleMusicTab(tabId: String, currentVal: Boolean) {
        if (!viewModel.toggleMusicTab(tabId, currentVal)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_tab_min_one_required),
                )
            }
        }
    }

    val activeLanguage = viewModel.activeLanguage(currentLanguage)
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
                        if (!viewModel.handleBack()) onNavigateBack()
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
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onOpenCategory = { id ->
                        viewModel.setSearching(false)
                        // Issue #431: the Advanced settings screen is a
                        // separate destination, not a drill-down category.
                        if (id == "advanced_settings") onOpenAdvancedSettings?.invoke()
                        else viewModel.selectCategory(id)
                    },
                )
            }
        } else if (selectedCategory == null) {
            SettingsCategoryList(
                categories = categories,
                onSelectCategory = viewModel::selectCategory,
                onOpenSearch = { viewModel.setSearching(true) },
                modifier = Modifier.padding(padding),
            )
        } else if (selectedId == "folders") {
            // #158: hosted at Scaffold level, not inside the drill-down
            // LazyColumn — the screen scrolls itself.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                FoldersBlacklistScreen(viewModel = viewModel)
            }
        } else if (selectedId == "layout") {
            // #158: same as above — the editor scrolls itself.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                PlayerLayoutScreen(viewModel = viewModel)
            }
        } else {
            // Dedicated sub-screen for one category: only its own options.
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    when (selectedCategory.id) {
                        "language" -> LanguageCategorySection(
                            activeLanguage = activeLanguage,
                            settings = settings,
                            onUpdate = ::update,
                        )
                        "appearance" -> AppearanceCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                        )
                        "gestures" -> GesturesCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                        )
                        "pip" -> PipCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                        )
                        "tabs" -> TabsCategorySection(
                            settings = settings,
                            onToggleTab = ::toggleMusicTab,
                        )
                        "list" -> ListCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                            onToggleTab = ::toggleMusicTab,
                        )
                        "player" -> PlayerCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                            onStopBackgroundPlayback = ::stopBackgroundPlayback,
                        )
                        "decoder" -> DecoderCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                        )
                        "subtitle" -> SubtitleCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                        )
                        "advanced" -> {
                            val playbackState by playbackCoordinator.playbackState.collectAsState()
                            AdvancedCategorySection(
                                settings = settings,
                                onUpdate = ::update,
                                onStopBackgroundPlayback = ::stopBackgroundPlayback,
                                onOpenAdvancedSettings = onOpenAdvancedSettings,
                                playbackState = playbackState,
                                cachedDiagnostics = devDiagnostics,
                                onDiagnosticsLoaded = { devDiagnostics = it },
                                onLoadRecordCounts = viewModel::loadRecordCounts,
                                onLoadMediaCounts = viewModel::loadDiagnosticsMediaCounts,
                                snackbarHostState = snackbarHostState,
                                scope = scope,
                            )
                        }
                        "about" -> AboutCategorySection(
                            settings = settings,
                            onUpdate = ::update,
                            viewModel = viewModel,
                            onOpenAboutBeta = onOpenAboutBeta,
                        )
                    }
                }
            }
        }
    }
}
