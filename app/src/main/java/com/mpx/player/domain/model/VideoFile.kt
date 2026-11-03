package com.mpx.player.domain.model

/** A real video file from MediaStore, used to build a playback queue. */
data class VideoFile(
    val id: String,
    val title: String,
    val path: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "",
    val dateModified: Long = 0L,
    val bucketId: String = "",
    val folderName: String = "",
    val formattedDuration: String = "",
    val formattedSize: String = "",
    val isRemovable: Boolean = false,
    val isNew: Boolean = false,
    // From MediaStore WIDTH/HEIGHT. Frame rate has no MediaStore column, so
    // frameRateFps stays 0 until a metadata pass populates it — never faked.
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val frameRateFps: Float = 0f,
)
