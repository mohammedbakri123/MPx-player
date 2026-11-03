package com.mpx.player.data.repository

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.repository.MediaRepository

/**
 * Baseline repository providing realistic UI layout fixtures for the shell.
 * This ensures the MX Player layout, typography, badges, and row metrics
 * match real device proportions without faking background scan operations.
 * Real MediaStore scanning replaces this in Phase 1.
 */
class MediaRepositoryStub : MediaRepository {

    override fun getMediaFolders(): List<MediaFolder> = listOf(
        MediaFolder(
            id = "camera",
            name = "Camera",
            path = "/storage/emulated/0/DCIM/Camera",
            videoCount = 14,
            totalSizeFormatted = "4.2 GB",
            isNew = true,
        ),
        MediaFolder(
            id = "download",
            name = "Download",
            path = "/storage/emulated/0/Download",
            videoCount = 8,
            totalSizeFormatted = "2.8 GB",
            isNew = true,
        ),
        MediaFolder(
            id = "movies",
            name = "Movies",
            path = "/storage/emulated/0/Movies",
            videoCount = 5,
            totalSizeFormatted = "8.6 GB",
            isNew = false,
        ),
        MediaFolder(
            id = "whatsapp",
            name = "WhatsApp Video",
            path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            videoCount = 27,
            totalSizeFormatted = "1.1 GB",
            isNew = false,
        ),
        MediaFolder(
            id = "screen_records",
            name = "Screen Records",
            path = "/storage/emulated/0/Movies/Screenrecords",
            videoCount = 3,
            totalSizeFormatted = "340 MB",
            isNew = false,
        ),
    )

    override fun getAudioTracks(): List<AudioTrack> = listOf(
        AudioTrack("1", "Midnight Drive", "Synthetix", "Neon Horizon", "3:42"),
        AudioTrack("2", "Electric Echoes", "CyberPulse", "Retrowave Vol. 1", "4:15"),
        AudioTrack("3", "Acoustic Morning", "Clara Brooks", "Simple Days", "2:58"),
        AudioTrack("4", "Deep Horizon", "Nordic Waves", "Ambient Sounds", "5:20"),
        AudioTrack("5", "Solar Flares", "Astral Unit", "Galactic Run", "3:34"),
    )

    override fun getPlaylists(): List<Playlist> = listOf(
        Playlist("fav", "Favorites", 24, "Default Playlist"),
        Playlist("recent", "Recently Added", 12, "Auto Generated"),
        Playlist("roadtrip", "Night Drives", 18, "Created Oct 2026"),
    )

    override fun getAudioAlbums(): List<AudioAlbum> = listOf(
        AudioAlbum("alb1", "Neon Horizon", "Synthetix", 10),
        AudioAlbum("alb2", "Retrowave Vol. 1", "CyberPulse", 8),
        AudioAlbum("alb3", "Simple Days", "Clara Brooks", 12),
    )

    override fun getAudioArtists(): List<AudioArtist> = listOf(
        AudioArtist("art1", "Synthetix", 10, 1),
        AudioArtist("art2", "CyberPulse", 8, 1),
        AudioArtist("art3", "Clara Brooks", 12, 1),
        AudioArtist("art4", "Nordic Waves", 6, 1),
    )

    override fun getAudioFolders(): List<AudioFolder> = listOf(
        AudioFolder("f1", "Music", "/storage/emulated/0/Music", 35),
        AudioFolder("f2", "Podcasts", "/storage/emulated/0/Podcasts", 8),
        AudioFolder("f3", "Recordings", "/storage/emulated/0/Recordings", 4),
    )
}
