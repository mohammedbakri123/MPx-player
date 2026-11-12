package com.mpx.player.domain.model

/** A real video file from MediaStore, used to build a playback queue. */
data class VideoFile(
    val id: String,
    val title: String,
    val path: String,
    // Local playback fix: the MediaStore content:// URI for this row
    // (content://media/<volume>/video/media/<id>), the URI mpv must be
    // handed via MpvFdOpener. The raw DATA path in [path] can no longer
    // be opened by native code under scoped storage (Android 11+):
    // mpv gets "Permission denied" on it even though the app may list
    // it. [path] stays as the filesystem path — folder grouping,
    // thumbnails, favourite identity and subtitle-sibling lookup still
    // use it. Blank when the row has no _ID (callers fall back to [path]).
    val contentUri: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val bucketId: String = "",
    val folderName: String = "",
    val isRemovable: Boolean = false,
    val isNew: Boolean = false,
    // From MediaStore WIDTH/HEIGHT. Frame rate has no MediaStore column, so
    // frameRateFps stays 0 until a metadata pass populates it — never faked.
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val frameRateFps: Float = 0f,
)
