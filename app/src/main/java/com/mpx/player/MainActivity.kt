package com.mpx.player

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.player.MiniPlayer
import com.mpx.player.ui.navigation.Screen
import com.mpx.player.ui.navigation.navigationTabs
import com.mpx.player.ui.screens.local.LocalScreen
import com.mpx.player.ui.screens.me.MeScreen
import com.mpx.player.ui.screens.music.MusicScreen
import com.mpx.player.ui.screens.player.PlayerScreen
import com.mpx.player.ui.screens.settings.SettingsScreen
import com.mpx.player.ui.screens.transfer.TransferScreen
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.PipUtils

class MainActivity : ComponentActivity() {
    private var isInPipMode by mutableStateOf(false)
    private lateinit var settingsRepository: SharedPrefsAppSettingsRepository
    private var currentTheme by mutableStateOf("Midnight")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SharedPrefsAppSettingsRepository(this)
        currentTheme = settingsRepository.load().appTheme

        setContent {
            MpxTheme(appTheme = currentTheme) {
                MpxApp(
                    isInPipMode = isInPipMode,
                    settingsRepository = settingsRepository,
                    onThemeChanged = { newTheme ->
                        currentTheme = newTheme
                    },
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val settings = settingsRepository.load()
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
    isInPipMode: Boolean = false,
    settingsRepository: AppSettingsRepository? = null,
    onThemeChanged: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playbackManager = remember { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            // Hide bottom navigation in PiP mode or on full-screen routes (Player, Settings)
            if (isInPipMode || currentDestination?.route in listOf(Screen.Player.route, Screen.Settings.route)) {
                return@Scaffold
            }

            Column {
                // Real Media3 persistent MiniPlayer bar
                MiniPlayer(
                    playbackManager = playbackManager,
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    navigationTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == tab.route
                        } == true

                        NavigationBarItem(
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
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MpxPrimary,
                                selectedTextColor = MpxPrimary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MpxPrimary.copy(alpha = 0.15f),
                            ),
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
        ) {
            composable(Screen.Local.route) {
                LocalScreen(
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
                )
            }
            composable(Screen.Music.route) {
                MusicScreen(
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                    playbackManager = playbackManager,
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
                    playbackManager = playbackManager,
                    settingsRepository = realSettingsRepo,
                    onThemeChanged = onThemeChanged,
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    settingsRepository = realSettingsRepo,
                    onThemeChanged = onThemeChanged,
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    onClose = { navController.popBackStack() },
                    playbackManager = playbackManager,
                )
            }
        }
    }
}
