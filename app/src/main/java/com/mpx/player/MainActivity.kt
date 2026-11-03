package com.mpx.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mpx.player.ui.screens.LocalScreen
import com.mpx.player.ui.screens.MeScreen
import com.mpx.player.ui.screens.MusicScreen
import com.mpx.player.ui.screens.PlayerScreen
import com.mpx.player.ui.screens.SettingsScreen
import com.mpx.player.ui.screens.TransferScreen
import com.mpx.player.ui.theme.MpxTheme

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab("local", "Local", Icons.Outlined.Folder, Icons.Filled.Folder),
    Tab("music", "Music", Icons.Outlined.MusicNote, Icons.Filled.MusicNote),
    Tab("transfer", "Transfer", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz),
    Tab("me", "Me", Icons.Outlined.Person, Icons.Filled.Person),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MpxTheme { MpxApp() } }
    }
}

@Composable
fun MpxApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Player and Settings are full routes, not tabs:
            // hide the bottom nav on them, as in MX.
            if (currentDestination?.route in listOf("player", "settings")) {
                return@Scaffold
            }
            NavigationBar {
                tabs.forEach { tab ->
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
                                if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "local",
            modifier = Modifier.padding(padding),
        ) {
            composable("local") {
                LocalScreen(
                    onOpenPlayer = { navController.navigate("player") },
                    onOpenMusic = { navController.navigate("music") },
                )
            }
            composable("music") {
                MusicScreen(onOpenPlayer = { navController.navigate("player") })
            }
            composable("transfer") { TransferScreen() }
            composable("me") {
                MeScreen(
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenMusic = { navController.navigate("music") },
                )
            }
            composable("settings") { SettingsScreen() }
            composable("player") {
                PlayerScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}
