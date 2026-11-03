package com.mpx.player.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Domain model representing a tool item on the Me screen.
 */
data class MeTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String? = null,
    val isAvailable: Boolean = false,
)

/**
 * Domain model representing a category in Settings.
 */
data class SettingCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)
