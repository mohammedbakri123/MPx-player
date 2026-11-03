package com.mpx.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import com.mpx.player.ui.theme.MpxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MpxTheme {
                MpxApp()
            }
        }
    }
}

@Composable
fun MpxApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            // Hide bottom navigation on full-screen routes (Player, Settings)
            if (currentDestination?.route in listOf(Screen.Player.route, Screen.Settings.route)) {
                return@Scaffold
            }

            androidx.compose.foundation.layout.Column {
                HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MpxSurface,
                    contentColor = MpxTextPrimary,
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
                                unselectedIconColor = MpxTextSecondary,
                                unselectedTextColor = MpxTextSecondary,
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
                    onOpenMusic = { navController.navigate(Screen.Music.route) },
                    onOpenTransfer = { navController.navigate(Screen.Transfer.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.Music.route) {
                MusicScreen(
                    onOpenPlayer = { navController.navigate(Screen.Player.route) },
                )
            }
            composable(Screen.Transfer.route) {
                TransferScreen()
            }
            composable(Screen.Me.route) {
                MeScreen(
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenMusic = { navController.navigate(Screen.Music.route) },
                    onOpenTransfer = { navController.navigate(Screen.Transfer.route) },
                )
            }
            composable(Screen.Settings.route) {
                val context = LocalContext.current
                val settingsRepository = remember { SharedPrefsAppSettingsRepository(context) }
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    settingsRepository = settingsRepository,
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
