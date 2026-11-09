package com.mpx.player.domain.model

/**
 * Domain model representing an audio track on device.
 */
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationFormatted: String,
    val durationMs: Long = 0L,
    val path: String = "",
    // Local playback fix: same as VideoFile.contentUri — the MediaStore
    // content:// URI for this row (content://media/<volume>/audio/media/<id>),
    // the URI mpv must be handed via MpvFdOpener. The raw DATA path in
    // [path] is denied to native code under scoped storage (Android 11+).
    // [path] stays as the filesystem path for folder grouping, thumbnails
    // and favourite identity. Blank when the row has no _ID.
    val contentUri: String = "",
    // Issue #317: MediaStore BUCKET_ID for the track (same scheme as
    // VideoFile.bucketId, #253: the real BUCKET_ID, else the hash of
    // the lower-cased parent path, else "unknown"). Lets the blacklist
    // resolve a content://-pathed track's real folder from the folder
    // list — File(path).parent is meaningless for such tracks.
    val bucketId: String = "",
    // Issue #311: MediaStore DATE_MODIFIED, seconds since epoch, as
    // MediaStore reports it (same units as VideoFile.dateModified).
    // 0 when the column is absent. Drives the albums / artists /
    // folders DATE sort via each group's latest track date; the track
    // DATE sort itself still uses id recency (see MediaSortComparator).
    val dateModified: Long = 0L,
)

/**
 * Domain model representing an audio album.
 */
data class AudioAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    // Issue #311: the newest DATE_MODIFIED among the album's tracks
    // (seconds since epoch, 0 when unknown) — what the DATE sort
    // orders by, instead of the album id hash.
    val latestTrackDateModified: Long = 0L,
)

/**
 * Domain model representing an audio artist.
 */
data class AudioArtist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    // Issue #311: the newest DATE_MODIFIED among the artist's tracks
    // — what the DATE sort orders by, instead of the artist id hash.
    val latestTrackDateModified: Long = 0L,
)

/**
 * Domain model representing a music folder.
 */
data class AudioFolder(
    val id: String,
    val name: String,
    val path: String,
    val trackCount: Int,
    val isStorageRemovable: Boolean = false,
    // Issue #311: the newest DATE_MODIFIED among the folder's tracks
    // — what the DATE sort orders by, instead of returning 0 and
    // silently falling through to the name tie-break.
    val latestTrackDateModified: Long = 0L,
)

/**
 * Domain model representing a playlist.
 */
data class Playlist(
    val id: String,
    val title: String,
    val itemCount: Int,
    val subtitle: String = "",
)
