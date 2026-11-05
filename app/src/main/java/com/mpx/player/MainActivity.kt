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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.mpx.player.ui.theme.MpxTheme
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.mpx.player.ui.screens.me.MeScreen
import com.mpx.player.ui.screens.music.MusicScreen
import com.mpx.player.ui.screens.player.PlayerScreen
import com.mpx.player.ui.screens.settings.SettingsScreen
import com.mpx.player.ui.screens.settings.OverlaySettingsScreen
import com.mpx.player.ui.screens.transfer.TransferScreen
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils
import java.util.Locale
import com.mpx.player.ui.components.MpxBottomBar
import com.mpx.player.ui.components.MpxDockTab
import com.mpx.player.ui.theme.MpxEasingStandard
import com.mpx.player.ui.theme.MpxMotionStandard

class MainActivity : ComponentActivity() {
    private var isInPipMode by mutableStateOf(false)
    private lateinit var settingsRepository: SharedPrefsAppSettingsRepository
    private var currentLanguage by mutableStateOf(LocaleHelper.LANGUAGE_SYSTEM)
    private var currentTheme by mutableStateOf("System")
    private var openPlayerOnStart by mutableStateOf(false)

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
        super.onCreate(savedInstanceState)
        settingsRepository = SharedPrefsAppSettingsRepository(this)
        val initialSettings = settingsRepository.load()
        currentLanguage = initialSettings.appLanguage
        currentTheme = initialSettings.appTheme

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
                MpxTheme(theme = currentTheme) {
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
                        openPlayerRequested = openPlayerOnStart,
                        onResetOpenPlayer = { openPlayerOnStart = false },
                        onLanguageChanged = { newLang ->
                            currentLanguage = newLang
                            val s = settingsRepository.load()
                            settingsRepository.save(s.copy(appLanguage = newLang))
                            LocaleHelper.applyAppLocale(this@MainActivity, newLang)
                        },
                        onThemeChanged = { currentTheme = it },
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

        if (!settings.backgroundPlay || isFinishing) {
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
        if (!settings.backgroundPlay || isFinishing) {
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
        if (settings.popupPlayEnabled) {
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
    openPlayerRequested: Boolean = false,
    onResetOpenPlayer: (() -> Unit)? = null,
    onLanguageChanged: ((String) -> Unit)? = null,
    onThemeChanged: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playbackManager = remember { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    val navController = rememberNavController()


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
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            // Hide bottom navigation in PiP mode or on full-screen routes (Player, Settings, AboutBeta)
            if (isInPipMode || currentDestination?.route in listOf(Screen.Player.route, Screen.Settings.route, Screen.OverlaySettings.route, Screen.AboutBeta.route)) {
                return@Scaffold
            }

            // MX-style bottom bar: full-width navigation bar in the theme's
            // surface colour (defined in every theme, unlike the old
            // surfaceContainerHigh dock, which fell back to a foreign grey
            // in Dark Slate / Classic Navy). The mini-player sits above it
            // as its own full-width strip, separated by a hairline — the
            // same two-bar shape MX uses — instead of one floating
            // rounded container.
            Column {
                // Persistent MiniPlayer bar
                MiniPlayer(
                    playbackManager = playbackManager,
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                )
                // #106 dock atoms: full-width 64dp bar, hairline top edge
                // (replaces the old divider), tap-only tabs, colour-only
                // selection. Deliberately NOT the old M3 NavigationBar
                // (80dp, pill indicator, container grey) and NOT the #84
                // floating rounded dock. No swipe between tabs (UX law).
                MpxBottomBar {
                    navigationTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        MpxDockTab(
                            label = stringResource(tab.labelRes),
                            icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Local.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard)) },
            exitTransition = { fadeOut(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard)) },
            popEnterTransition = { fadeIn(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard)) },
            popExitTransition = { fadeOut(animationSpec = tween(MpxMotionStandard, easing = MpxEasingStandard)) },
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
                    onOpenTransfer = {
                        navController.navigate(Screen.Transfer.route) {
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
            composable(Screen.Transfer.route) {
                TransferScreen()
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
                    onOpenTransfer = {
                        navController.navigate(Screen.Transfer.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    onOpenAboutBeta = { navController.navigate(Screen.AboutBeta.route) },
                    playbackManager = playbackManager,
                    settingsRepository = realSettingsRepo,
                    currentLanguage = currentLanguage,
                    onLanguageChanged = onLanguageChanged,
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
                MpxTheme(theme = "Dark") {
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
    }
}
