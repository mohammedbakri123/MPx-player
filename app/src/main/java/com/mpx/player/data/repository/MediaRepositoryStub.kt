package com.mpx.player.data.repository

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.repository.MediaRepository

/**
 * Stub repository retained for legacy compatibility only.
 * Replaced by [MediaStoreRepository] for real MediaStore device scanning.
 * Returns empty lists so no fake folders or invented counts appear in the UI.
 */
@Deprecated(
    message = "Replaced by MediaStoreRepository for real MediaStore scanning.",
    replaceWith = ReplaceWith("MediaStoreRepository(context)"),
)
class MediaRepositoryStub : MediaRepository {

    override fun getMediaFolders(): List<MediaFolder> = emptyList()

    override fun getAudioTracks(): List<AudioTrack> = emptyList()

    override fun getPlaylists(): List<Playlist> = emptyList()

    override fun getAudioAlbums(): List<AudioAlbum> = emptyList()

    override fun getAudioArtists(): List<AudioArtist> = emptyList()

    override fun getAudioFolders(): List<AudioFolder> = emptyList()
}
