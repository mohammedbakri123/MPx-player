package com.mpx.player.domain.model

/** A real video file from MediaStore, used to build a playback queue. */
data class VideoFile(
    val id: String,
    val title: String,
    val path: String,
    val durationMs: Long = 0L,
)
