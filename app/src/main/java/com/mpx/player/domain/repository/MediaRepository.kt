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
     * Phase 2b: the process-lifetime cache of the last scan, keyed by
     * the (show-all-folders, show-hidden-files) flags that shape the
     * lists (#100). The default is no cache; [MediaStoreRepository]
     * returns its last scan so a fresh LocalViewModel can render the
     * previous content instantly instead of flashing an empty page
     * while the rescan runs. It dies with the process, so a fresh
     * launch always scans.
     */
    fun cachedFolders(showAllFolders: Boolean, showHiddenFiles: Boolean): List<MediaFolder>? = null
    fun cachedVideos(showAll: Boolean, showHiddenFiles: Boolean): List<VideoFile>? = null
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

    /**
     * Phase 2e: the external storage volume names (MediaStore +
     * StorageManager), for the Development diagnostics section. The
     * default is no volumes; [MediaStoreRepository] returns its
     * scanned set.
     */
    fun getExternalVolumeNames(): Set<String> = emptySet()
}
