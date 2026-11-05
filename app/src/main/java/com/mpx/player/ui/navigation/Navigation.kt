package com.mpx.player.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.mpx.player.R

sealed class Screen(val route: String) {
    data object Local : Screen("local")
    data object Music : Screen("music")
    data object Me : Screen("me")
    data object History : Screen("history")
    data object Playlists : Screen("playlists")
    data object Player : Screen("player")
    data object Settings : Screen("settings")
    data object OverlaySettings : Screen("overlay_settings")
    data object AboutBeta : Screen("about_beta")
}

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
)

val navigationTabs = listOf(
    BottomNavItem(
        route = Screen.Local.route,
        labelRes = R.string.nav_local,
        // Issue #174: the Home tab showed a folder icon — it must show
        // a home icon. (Folder icons elsewhere — folder tiles and rows —
        // are correct and untouched.)
        unselectedIcon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    ),
    BottomNavItem(
        route = Screen.Music.route,
        labelRes = R.string.nav_music,
        unselectedIcon = Icons.Outlined.MusicNote,
        selectedIcon = Icons.Filled.MusicNote,
    ),
    BottomNavItem(
        route = Screen.Me.route,
        labelRes = R.string.nav_me,
        unselectedIcon = Icons.Outlined.Person,
        selectedIcon = Icons.Filled.Person,
    ),
)
