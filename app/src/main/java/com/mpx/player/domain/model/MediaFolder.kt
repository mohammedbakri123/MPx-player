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
    // Real summed size of the folder's videos (bytes), accumulated at scan
    // time. Powers the folder SIZE sort (issue #307): sorting on the
    // rounded display string above tied folders that format alike
    // (e.g. 1.04 GB and 1.05 GB both read "1.0 GB").
    val totalSizeBytes: Long = 0L,
    val isNew: Boolean = false,
    val lastModified: Long = 0L,
    val isStorageRemovable: Boolean = false,
    // Real summed duration of the folder's videos (ms), accumulated at scan
    // time. Powers the folder Length sort (issue #70).
    val totalDurationMs: Long = 0L,
)
