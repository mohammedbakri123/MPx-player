package com.mpx.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.mpx.player.ui.theme.MpxTheme
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackService
import com.mpx.player.ui.components.player.MiniPlayer
import com.mpx.player.ui.navigation.Screen
import com.mpx.player.ui.navigation.navigationTabs
import com.mpx.player.ui.screens.about.AboutBetaScreen
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.screens.local.LocalScreen
import com.mpx.player.ui.screens.me.HistoryScreen
import com.mpx.player.ui.screens.me.MeScreen
import com.mpx.player.ui.screens.me.PlaylistsScreen
import com.mpx.player.ui.screens.music.MusicScreen
import com.mpx.player.ui.screens.player.PlayerScreen
import com.mpx.player.ui.screens.settings.SettingsScreen
import com.mpx.player.ui.screens.settings.OverlaySettingsScreen
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils
import java.util.Locale
import com.mpx.player.ui.components.MpxBottomBar
import com.mpx.player.ui.components.MpxDockTab
import com.mpx.player.ui.theme.MpxEasingStandard
import com.mpx.player.ui.theme.MpxMotionStandard

/**
 * mpvEx-style tab transitions (#157): between dock tabs the new page slides
 * in 48dp from the direction of travel and fades (250ms, standard easing),
 * the old page slides out the other way and fades faster; any other
 * navigation is a plain 250ms fade. mpvEx's MainScreen does the same with
 * AnimatedContent (250ms slide + fade, 48dp).
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.mpxTabEnter(
    tabOrder: List<String>,
    density: Density,
): EnterTransition {
    val from = tabOrder.indexOf(initialState.destination.route)
    val to = tabOrder.indexOf(targetState.destination.route)
    return if (from >= 0 && to >= 0 && from != to) {
        val direction = if (to > from) 1 else -1
        val slidePx = with(density) { 48.dp.roundToPx() } * direction
        slideInHorizontally(
            animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard),
        ) { slidePx } +
            fadeIn(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard))
    } else {
        fadeIn(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mpxTabExit(
    tabOrder: List<String>,
    density: Density,
): ExitTransition {
    val from = tabOrder.indexOf(initialState.destination.route)
    val to = tabOrder.indexOf(targetState.destination.route)
    return if (from >= 0 && to >= 0 && from != to) {
        val direction = if (to > from) 1 else -1
        val slidePx = with(density) { 48.dp.roundToPx() } * direction
        slideOutHorizontally(
            animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard),
        ) { -slidePx } +
            fadeOut(animationSpec = tween(MpxMotionStandard / 2, easing = MpxEasingStandard))
    } else {
        fadeOut(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard))
    }
}

class MainActivity : ComponentActivity() {
    private var isInPipMode by mutableStateOf(false)
    private lateinit var settingsRepository: SharedPrefsAppSettingsRepository
    private var currentLanguage by mutableStateOf(LocaleHelper.LANGUAGE_SYSTEM)
    private var currentTheme by mutableStateOf("System")
    private var currentPalette by mutableStateOf("Blue")
    private var currentDarkSurface by mutableStateOf("TrueBlack")
    private var openPlayerOnStart by mutableStateOf(false)
    // Issue #206: the NavHost's current route, mirrored from MpxApp via
    // onDestinationChanged. onUserLeaveHint gates Home-press PiP on it —
    // without this the activity cannot tell whether the Player is visible.
    // Null until the first composition reports; null means "not Player".
    private var currentRoute by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    // Activity-result launchers MUST be Activity properties (registerForActivityResult).
    // rememberLauncherForActivityResult crashes in this app even above the NavHost:
    // LocalActivityResultRegistryOwner is not provided in this composition.
    val localPermissionHandler = ActivityResultHandler<Boolean>()
    val musicPermissionHandler = ActivityResultHandler<Boolean>()
    val subtitleHandler = ActivityResultHandler<android.net.Uri?>()
    val localPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> localPermissionHandler.invoke(granted) }
    val musicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> musicPermissionHandler.invoke(granted) }
    val subtitleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> subtitleHandler.invoke(uri) }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        val locale = LocaleHelper.getLocale(lang)
        Locale.setDefault(locale)
        val localized = LocaleHelper.createLocalizedContext(newBase, locale)
        super.attachBaseContext(localized)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // #171: the manifest launch theme is Theme.MPx.Splash so cold start
        // shows the branded splash; switch back to the normal theme before
        // the window is created so the running UI is unaffected.
        setTheme(R.style.Theme_MPx)
        super.onCreate(savedInstanceState)
        settingsRepository = SharedPrefsAppSettingsRepository(this)
        val initialSettings = settingsRepository.load()
        currentLanguage = initialSettings.appLanguage
        currentTheme = initialSettings.appTheme
        currentPalette = initialSettings.colorPalette
        currentDarkSurface = initialSettings.darkSurface

        checkOpenPlayerIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val targetLocale = remember(currentLanguage) {
                LocaleHelper.getLocale(currentLanguage)
            }
            val isRtl = remember(targetLocale) {
                LocaleHelper.isRtl(targetLocale)
            }
            val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val localizedContext = remember(this, targetLocale) {
                LocaleHelper.createLocalizedContext(this, targetLocale)
            }
            val localizedConfig = remember(this, targetLocale) {
                Configuration(resources.configuration).apply {
                    setLocale(targetLocale)
                    setLayoutDirection(targetLocale)
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides layoutDirection,
            ) {
                MpxTheme(theme = currentTheme, palette = currentPalette, darkSurface = currentDarkSurface) {
                    MpxApp(
                        localPermissionLauncher = localPermissionLauncher,
                        localPermissionHandler = localPermissionHandler,
                        musicPermissionLauncher = musicPermissionLauncher,
                        musicPermissionHandler = musicPermissionHandler,
                        subtitleLauncher = subtitleLauncher,
                        subtitleHandler = subtitleHandler,
                        isInPipMode = isInPipMode,
                        settingsRepository = settingsRepository,
                        currentLanguage = currentLanguage,
                        currentPalette = currentPalette,
                        currentDarkSurface = currentDarkSurface,
                        openPlayerRequested = openPlayerOnStart,
                        onResetOpenPlayer = { openPlayerOnStart = false },
                        onLanguageChanged = { newLang ->
                            currentLanguage = newLang
                            val s = settingsRepository.load()
                            settingsRepository.save(s.copy(appLanguage = newLang), commit = false)
                            LocaleHelper.applyAppLocale(this@MainActivity, newLang)
                        },
                        onThemeChanged = { currentTheme = it },
                        onPaletteChanged = { currentPalette = it },
                        onDarkSurfaceChanged = { currentDarkSurface = it },
                        onDestinationChanged = { currentRoute = it },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkOpenPlayerIntent(intent)
    }

    private fun checkOpenPlayerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true ||
            intent?.action == PlaybackService.ACTION_OPEN_PLAYER
        ) {
            openPlayerOnStart = true
        }
    }

    override fun onStart() {
        super.onStart()
        val playbackManager = PlaybackManager.getInstance(this)
        val state = playbackManager.playbackState.value
        if (state.isVideoBackgroundActive || state.isBackgroundPlaying) {
            playbackManager.setVideoBackgroundActive(false)
            playbackManager.setBackgroundPlaying(false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            val settings = settingsRepository.load()
            if (!settings.backgroundPlay) {
                val playbackManager = PlaybackManager.getInstance(this)
                if (playbackManager.playbackState.value.isPlaying) {
                    playbackManager.pause()
                }
                playbackManager.setVideoBackgroundActive(false)
                playbackManager.setBackgroundPlaying(false)
                PlaybackService.stop(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Never pause/stop for PiP: entering PiP pauses the activity but
        // keeps it visible, and some OEMs also deliver onStop on PiP entry
        // (issue #98). The original logic below is untouched for non-PiP.
        if (isInPipMode) return
        val settings = settingsRepository.load()
        val playbackManager = PlaybackManager.getInstance(this)
        val state = playbackManager.playbackState.value

        // Issue #205: the stop decision is backgroundPlay alone.
        // isFinishing must NOT stop playback: swiping the app from
        // recents finishes the activity, and so does back-press from
        // Home — both are exactly the situations background play
        // exists for. The service (not the activity) owns playback
        // when background play is on, and its own onTaskRemoved
        // already keeps it alive on swipe-away. When background
        // play is off, stop unconditionally — the same rule the
        // #92 stop-on-leave path applies when leaving the player.
        if (!settings.backgroundPlay) {
            // off = playback MUST stop when leaving the foreground
            if (state.isPlaying) {
                playbackManager.pause()
            }
            playbackManager.setVideoBackgroundActive(false)
            playbackManager.setBackgroundPlaying(false)
            PlaybackService.stop(this)
        } else {
            // on = service keeps it alive
            if (state.isPlaying) {
                if (state.currentItem?.isVideo == true) {
                    playbackManager.setVideoBackgroundActive(true)
                } else {
                    playbackManager.setBackgroundPlaying(true)
                }
                PlaybackService.start(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val settings = settingsRepository.load()
        // Issue #205: see onStop — isFinishing must not stop the
        // service. When background play is on, the foreground
        // service keeps playback alive after the activity is gone.
        // (Rotation never reaches here: orientation is in
        // configChanges, so it only recomposes.)
        if (!settings.backgroundPlay) {
            val playbackManager = PlaybackManager.getInstance(this)
            if (playbackManager.playbackState.value.isPlaying) {
                playbackManager.pause()
            }
            playbackManager.setVideoBackgroundActive(false)
            playbackManager.setBackgroundPlaying(false)
            PlaybackService.stop(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            val settings = settingsRepository.load()
            if (!settings.backgroundPlay && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                val playbackManager = PlaybackManager.getInstance(this)
                if (playbackManager.playbackState.value.isPlaying) {
                    playbackManager.pause()
                }
                playbackManager.setVideoBackgroundActive(false)
                playbackManager.setBackgroundPlaying(false)
                PlaybackService.stop(this)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val settings = settingsRepository.load()
        // Home-button PiP follows the PiP setting alone (issue #98): with
        // the old backgroundPlay requirement, the default backgroundPlay=false
        // meant pressing Home just stopped playback and never entered PiP.
        // PiP keeps the video visible, so it is not "background play".
        // Issue #206: only when the Player route is the visible
        // destination. With background play on, a video keeps playing
        // while the user browses Local/Music/Me — pressing Home then
        // must NOT pop a PiP window over the launcher; playback just
        // keeps going in the background (issue #205). The Playback
        // sheet is a bottom sheet inside the Player route, not a
        // separate destination, so Home over an open sheet still PiPs.
        // Already-in-PiP is a no-op re-entry, guarded for clarity.
        if (settings.popupPlayEnabled && !isInPipMode && currentRoute == Screen.Player.route) {
            val playbackManager = PlaybackManager.getInstance(this)
            val state = playbackManager.playbackState.value
            if (state.isPlaying && state.currentItem?.isVideo == true) {
                PipUtils.enterPip(this)
            }
        }
    }
}

@Composable
fun MpxApp(
    localPermissionLauncher: ActivityResultLauncher<String>,
    localPermissionHandler: ActivityResultHandler<Boolean>,
    musicPermissionLauncher: ActivityResultLauncher<String>,
    musicPermissionHandler: ActivityResultHandler<Boolean>,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    subtitleHandler: ActivityResultHandler<android.net.Uri?>,
    isInPipMode: Boolean = false,
    settingsRepository: AppSettingsRepository? = null,
    currentLanguage: String = LocaleHelper.LANGUAGE_SYSTEM,
    currentPalette: String = "Blue",
    currentDarkSurface: String = "TrueBlack",
    openPlayerRequested: Boolean = false,
    onResetOpenPlayer: (() -> Unit)? = null,
    onLanguageChanged: ((String) -> Unit)? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    onPaletteChanged: ((String) -> Unit)? = null,
    onDarkSurfaceChanged: ((String) -> Unit)? = null,
    // Issue #206: reports the NavHost's current route to the activity,
    // which gates Home-press PiP on the Player being visible.
    onDestinationChanged: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playbackManager = remember { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Issue #206: mirror the route to the activity on every change.
    LaunchedEffect(currentDestination?.route) {
        onDestinationChanged?.invoke(currentDestination?.route)
    }

    // Issue #176: swipe on the page switches dock tabs. The dock tap
    // and the swipe share this one navigate block (persist lastTabRoute,
    // pop up to the start destination with state saved, single top,
    // restore state), so a swipe lands exactly where a tap would.
    fun navigateToTab(route: String) {
        realSettingsRepo.save(
            realSettingsRepo.load().copy(lastTabRoute = route),
        )
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }


    LaunchedEffect(openPlayerRequested) {
        if (openPlayerRequested) {
            navController.navigate(Screen.Player.route) {
                launchSingleTop = true
            }
            onResetOpenPlayer?.invoke()
        }
    }

    Scaffold(
        bottomBar = {
            // Hide bottom navigation in PiP mode or on full-screen routes
            // (Player, Settings, AboutBeta, History, Playlists). mpvEx-style
            // (#157): the bar slides in/out over 300ms instead of popping.
            val showDock = !isInPipMode && currentDestination?.route !in listOf(
                Screen.Player.route,
                Screen.Settings.route,
                Screen.OverlaySettings.route,
                Screen.AboutBeta.route,
                Screen.History.route,
                Screen.Playlists.route,
            )
            AnimatedVisibility(
                visible = showDock,
                enter = slideInVertically(
                    animationSpec = tween(300, easing = MpxEasingStandard),
                ) { it },
                exit = slideOutVertically(
                    animationSpec = tween(300, easing = MpxEasingStandard),
                ) { it },
            ) {
                Column {
                    // Real Media3 persistent MiniPlayer bar
                    MiniPlayer(
                        playbackManager = playbackManager,
                        onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    )
                    // #157 dock: mpvEx-style NavigationBar (28dp rounded top
                    // corners, accent pill on the selected tab), same four
                    // tabs, tap-only. The selected tab is persisted
                    // (AppSettings.lastTabRoute) so the app reopens on it.
                    MpxBottomBar {
                        navigationTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            MpxDockTab(
                                label = stringResource(tab.labelRes),
                                icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        // #157: reopen on the last dock tab (persisted on every tab tap).
        val tabOrder = navigationTabs.map { it.route }
        val startTabRoute = remember {
            realSettingsRepo.load().lastTabRoute
        }.takeIf { it in tabOrder } ?: Screen.Local.route

        // #157: density is captured here (composable scope) because
        // AnimatedContentTransitionScope has no density in this Compose
        // version — mpvEx's identical with(density) usage relies on a
        // newer animation artifact. The transition lambdas below are
        // not composable, so they take the captured value.
        val density = LocalDensity.current
        // Issue #176: swipe left/right on the page switches dock tabs
        // (this reverses the old tap-only dock rule, per the user). The
        // detector lives on a Box around the NavHost — the MiniPlayer
        // and dock sit in the bottomBar, outside it, so swipes there do
        // nothing. Active only on the four tabs; no wrap at the ends.
        // A horizontal drag on a horizontally scrolling child (a chips
        // row) is consumed by that child, not the page. The in-tab
        // search page is still the Local route, so a swipe there also
        // switches tabs. Direction follows the tab order, and the
        // mpxTabEnter/mpxTabExit transitions slide the right way.
        val swipeThresholdPx = with(density) { 100.dp.toPx() }
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(tabOrder, currentDestination?.route) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            val currentIndex = tabOrder.indexOf(currentDestination?.route)
                            if (currentIndex >= 0) {
                                when {
                                    totalDrag < -swipeThresholdPx && currentIndex < tabOrder.lastIndex ->
                                        navigateToTab(tabOrder[currentIndex + 1])
                                    totalDrag > swipeThresholdPx && currentIndex > 0 ->
                                        navigateToTab(tabOrder[currentIndex - 1])
                                }
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                    )
                },
        ) {
            NavHost(
                navController = navController,
                startDestination = startTabRoute,
                modifier = Modifier.fillMaxSize(),
            enterTransition = { mpxTabEnter(tabOrder, density) },
            exitTransition = { mpxTabExit(tabOrder, density) },
            popEnterTransition = { mpxTabEnter(tabOrder, density) },
            popExitTransition = { mpxTabExit(tabOrder, density) },
        ) {
            composable(Screen.Local.route) {
                LocalScreen(
                    permissionLauncher = localPermissionLauncher,
                    permissionResultHandler = localPermissionHandler,
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    onOpenMusic = {
                        navController.navigate(Screen.Music.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    playbackManager = playbackManager,
                    settingsRepository = realSettingsRepo,
                )
            }
            composable(Screen.Music.route) {
                MusicScreen(
                    permissionLauncher = musicPermissionLauncher,
                    permissionResultHandler = musicPermissionHandler,
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    playbackManager = playbackManager,
                    settingsRepository = realSettingsRepo,
                )
            }
            composable(Screen.Me.route) {
                MeScreen(
                    onOpenLocal = {
                        navController.navigate(Screen.Local.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenMusic = {
                        navController.navigate(Screen.Music.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    onOpenAboutBeta = { navController.navigate(Screen.AboutBeta.route) },
                    onOpenHistory = { navController.navigate(Screen.History.route) },
                    onOpenPlaylists = { navController.navigate(Screen.Playlists.route) },
                    playbackManager = playbackManager,
                    settingsRepository = realSettingsRepo,
                    currentLanguage = currentLanguage,
                    onLanguageChanged = onLanguageChanged,
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    playbackManager = playbackManager,
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    playbackManager = playbackManager,
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenOverlaySettings = { navController.navigate(Screen.OverlaySettings.route) },
                    onOpenAboutBeta = { navController.navigate(Screen.AboutBeta.route) },
                    settingsRepository = realSettingsRepo,
                    currentLanguage = currentLanguage,
                    onLanguageChanged = onLanguageChanged,
                    onThemeChanged = onThemeChanged,
                    onPaletteChanged = onPaletteChanged,
                    onDarkSurfaceChanged = onDarkSurfaceChanged,
                    playbackManager = playbackManager,
                )
            }
            composable(Screen.OverlaySettings.route) {
                OverlaySettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    settingsRepository = realSettingsRepo,
                )
            }
            composable(Screen.AboutBeta.route) {
                AboutBetaScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Player.route) {
                // #152: the player ignores the Light theme and always renders
                // dark. MpxTheme provides the palette via CompositionLocal, so
                // wrapping the route re-themes the whole PlayerScreen subtree
                // (including its dialogs, which inherit CompositionLocals)
                // without touching the theme-following mini-player on the
                // tab pages. PiP renders this same route, so it stays dark.
                // #168: pass the current palette too — without it the
                // player (slider, accents) always stayed default Blue
                // whatever palette the user picked on Appearance.
                MpxTheme(theme = "Dark", palette = currentPalette, darkSurface = currentDarkSurface) {
                    PlayerScreen(
                        subtitleLauncher = subtitleLauncher,
                        subtitleResultHandler = subtitleHandler,
                        onClose = { navController.popBackStack() },
                        playbackManager = playbackManager,
                        settingsRepository = realSettingsRepo,
                        isInPipMode = isInPipMode,
                    )
                }
            }
        }
        } // closes the #176 swipe Box around the NavHost
    }
}
