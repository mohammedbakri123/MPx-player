package com.mpx.player.domain.repository

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist

/**
 * Interface contract for media library data operations.
 * Will be backed by MediaStore and Room in subsequent phases.
 */
interface MediaRepository {
    fun getMediaFolders(): List<MediaFolder>
    fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> = getMediaFolders()
    fun getVideosInFolder(bucketId: String): List<VideoFile> = emptyList()
    fun getAllVideos(): List<VideoFile> = emptyList()
    fun getAllVideos(showAll: Boolean): List<VideoFile> = getAllVideos()
    fun getAudioTracks(): List<AudioTrack>
    fun getPlaylists(): List<Playlist>
    /**
     * Returns the library tracks that belong to a device (MediaStore) playlist,
     * in the playlist's own member order. Returns an empty list for an unknown
     * id or a playlist with no resolvable members. Custom (Room) playlists are
     * not covered here; they go through MediaPersistenceRepository.
     */
    fun getPlaylistTracks(playlistId: String): List<AudioTrack> = emptyList()
    fun getAudioAlbums(): List<AudioAlbum>
    fun getAudioArtists(): List<AudioArtist>
    fun getAudioFolders(): List<AudioFolder>
}
