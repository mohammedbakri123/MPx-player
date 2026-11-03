package com.mpx.player.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.mpx.player.R

sealed class Screen(val route: String) {
    data object Local : Screen("local")
    data object Music : Screen("music")
    data object Transfer : Screen("transfer")
    data object Me : Screen("me")
    data object Player : Screen("player")
    data object Settings : Screen("settings")
    data object AboutBeta : Screen("about_beta")
}

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val label: String = "",
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
)

val navigationTabs = listOf(
    BottomNavItem(
        route = Screen.Local.route,
        labelRes = R.string.nav_local,
        label = "Local",
        unselectedIcon = Icons.Outlined.Folder,
        selectedIcon = Icons.Filled.Folder,
    ),
    BottomNavItem(
        route = Screen.Music.route,
        labelRes = R.string.nav_music,
        label = "Music",
        unselectedIcon = Icons.Outlined.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
    ),
    BottomNavItem(
        route = Screen.Transfer.route,
        labelRes = R.string.nav_transfer,
        label = "Transfer",
        unselectedIcon = Icons.Outlined.SwapHoriz,
        selectedIcon = Icons.Filled.SwapHoriz,
    ),
    BottomNavItem(
        route = Screen.Me.route,
        labelRes = R.string.nav_me,
        label = "Me",
        unselectedIcon = Icons.Outlined.Person,
        selectedIcon = Icons.Filled.Person,
    ),
)
