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
