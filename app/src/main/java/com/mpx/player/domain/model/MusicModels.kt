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
    val path: String = "",
)

/**
 * Domain model representing an audio album.
 */
data class AudioAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
)

/**
 * Domain model representing an audio artist.
 */
data class AudioArtist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)

/**
 * Domain model representing a music folder.
 */
data class AudioFolder(
    val id: String,
    val name: String,
    val path: String,
    val trackCount: Int,
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
