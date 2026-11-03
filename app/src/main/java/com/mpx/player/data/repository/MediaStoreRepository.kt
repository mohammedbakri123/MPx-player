package com.mpx.player.data.repository

import android.content.Context
import android.provider.MediaStore
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.repository.MediaRepository
import java.io.File
import java.util.Locale

/**
 * Real repository backed by the Android MediaStore content provider.
 * Scans video folders with actual video counts and aggregate byte sizes,
 * and audio tracks with accurate title, artist, album, and duration metadata.
 */
class MediaStoreRepository(
    private val context: Context,
) : MediaRepository {

    override fun getMediaFolders(): List<MediaFolder> {
        val folderMap = mutableMapOf<String, FolderAccumulator>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                val sevenDaysAgoSec = (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L) / 1000L

                while (cursor.moveToNext()) {
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val parentFile = if (dataPath.isNotBlank()) File(dataPath).parentFile else null
                    val parentPath = parentFile?.absolutePath.orEmpty()

                    val rawBucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) else null
                    val bucketId = rawBucketId?.ifBlank { null }
                        ?: parentPath.ifBlank { "unknown" }.hashCode().toString()

                    val rawBucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                    val folderName = rawBucketName?.ifBlank { null }
                        ?: parentFile?.name?.ifBlank { "Videos" }
                        ?: "Videos"

                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                    val isRecent = dateAdded > sevenDaysAgoSec || dateModified > sevenDaysAgoSec

                    val acc = folderMap.getOrPut(bucketId) {
                        FolderAccumulator(
                            id = bucketId,
                            name = folderName,
                            path = parentPath,
                        )
                    }
                    acc.count += 1
                    acc.totalSizeBytes += size
                    if (dateModified > acc.lastModified) {
                        acc.lastModified = dateModified
                    }
                    if (isRecent) {
                        acc.isNew = true
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return folderMap.values.map { acc ->
            MediaFolder(
                id = acc.id,
                name = acc.name,
                path = acc.path,
                videoCount = acc.count,
                totalSizeFormatted = formatSize(acc.totalSizeBytes),
                isNew = acc.isNew,
                lastModified = acc.lastModified,
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getAudioTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val title = rawTitle?.ifBlank { "Unknown Track" } ?: "Unknown Track"

                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                        "Unknown Album"
                    } else {
                        rawAlbum
                    }

                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""

                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationFormatted = formatDuration(durationMs),
                            path = path,
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return tracks
    }

    override fun getAudioAlbums(): List<AudioAlbum> {
        val tracks = getAudioTracks()
        return tracks.groupBy { it.album }
            .map { (albumName, albumTracks) ->
                AudioAlbum(
                    id = albumName.hashCode().toString(),
                    title = albumName,
                    artist = albumTracks.firstOrNull()?.artist ?: "Unknown Artist",
                    trackCount = albumTracks.size,
                )
            }
            .sortedBy { it.title.lowercase(Locale.US) }
    }

    override fun getAudioArtists(): List<AudioArtist> {
        val tracks = getAudioTracks()
        return tracks.groupBy { it.artist }
            .map { (artistName, artistTracks) ->
                AudioArtist(
                    id = artistName.hashCode().toString(),
                    name = artistName,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.map { it.album }.distinct().size,
                )
            }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getAudioFolders(): List<AudioFolder> {
        val tracks = getAudioTracks()
        val folderMap = mutableMapOf<String, MutableList<AudioTrack>>()
        for (track in tracks) {
            val folderPath = if (track.path.isNotBlank()) {
                File(track.path).parent ?: "/storage/emulated/0/Music"
            } else {
                "/storage/emulated/0/Music"
            }
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(track)
        }
        return folderMap.map { (path, folderTracks) ->
            val name = File(path).name.ifBlank { "Music" }
            AudioFolder(
                id = path.hashCode().toString(),
                name = name,
                path = path,
                trackCount = folderTracks.size,
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getPlaylists(): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME,
            )
            context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Playlists.NAME} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol).toString()
                    val name = cursor.getString(nameCol) ?: "Playlist"
                    playlists.add(
                        Playlist(
                            id = id,
                            title = name,
                            itemCount = 0,
                            subtitle = "Device Playlist",
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Playlists may be restricted or unsupported on newer Android targets
        }
        return playlists
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private class FolderAccumulator(
        val id: String,
        val name: String,
        val path: String,
    ) {
        var count: Int = 0
        var totalSizeBytes: Long = 0L
        var lastModified: Long = 0L
        var isNew: Boolean = false
    }
}
