package com.mpx.player.domain.model

/**
 * Domain model representing a local media folder holding video content.
 * Matches MX Player's folder-first browsing model.
 */
data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSizeFormatted: String,
    val isNew: Boolean = false,
    val lastModified: Long = 0L,
)
