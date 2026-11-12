package com.mpx.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.mpx.player.R
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import java.io.File
import java.util.Locale

/**
 * Scanner for audio tracks, albums, artists, and audio folders backed by MediaStore.
 */
internal class AudioMediaScanner(
    private val context: Context,
    private val helper: MediaStoreScannerHelper,
) {
    fun getAudioTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val showHidden = helper.showHiddenFiles()
        val seenPaths = mutableSetOf<String>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            // Issue #311: one extra column in this existing scan (no
            // new scan) — the albums / artists / folders DATE sort
            // orders by each group's newest track date.
            MediaStore.Audio.Media.DATE_MODIFIED,
            // Issue #317: BUCKET_ID, so a content://-pathed track can
            // be resolved to its real folder (see bucketId below).
            MediaStore.Audio.Media.BUCKET_ID,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val uris = helper.getAudioContentUris()

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
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
                    val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                    val bucketIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)

                    while (cursor.moveToNext()) {
                        val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        if (path.isNotBlank() && !seenPaths.add(path)) continue

                        val id = if (idCol >= 0) cursor.getLong(idCol).toString() else path
                        val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                        val title = rawTitle?.ifBlank { context.getString(R.string.media_unknown_track) } ?: context.getString(R.string.media_unknown_track)
                        // Issue #281: same always-excluded check as videos
                        // (#218) — audio under /Android/data or /Android/obb
                        // is another app's private audio and stays out of the
                        // library regardless of "Show hidden files". Applied
                        // here, at the scan, so Albums / Artists / Folders /
                        // Playlists (all re-scan via getAudioTracks()) are
                        // fixed by this one change.
                        if ((!showHidden && helper.isHiddenPath(path, title)) ||
                            helper.isSystemCachePath(path)
                        ) continue

                        val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                        val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                            context.getString(R.string.media_unknown_artist)
                        } else {
                            rawArtist
                        }

                        val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                        val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                            context.getString(R.string.media_unknown_album)
                        } else {
                            rawAlbum
                        }

                        val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                        // Issue #311: seconds since epoch, as MediaStore
                        // reports it (same units as VideoFile.dateModified).
                        val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol).coerceAtLeast(0L) else 0L
                        // Issue #317: same fallback scheme as the video
                        // scans (#253) — MediaStore bucket ids are the
                        // hash of the LOWER-CASED parent path, so the
                        // fallback must hash the lower-cased path too.
                        // A blank-DATA track has no parent to hash and
                        // falls back to "unknown".
                        val parentPath = if (path.isNotBlank()) File(path).parent.orEmpty() else ""
                        val rawBucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) else null
                        val bucketId = rawBucketId?.ifBlank { null }
                            ?: parentPath.ifBlank { "unknown" }.lowercase(Locale.US).hashCode().toString()
                        val effectivePath = path.ifBlank {
                            if (idCol >= 0) {
                                ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                            } else ""
                        }
                        // Local playback fix: always carry the content:// URI for
                        // this row, even when DATA is non-blank — mpv must be
                        // handed it (via MpvFdOpener -> fd://), never the raw path.
                        val contentUri = if (idCol >= 0) {
                            ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                        } else ""

                        tracks.add(
                            AudioTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                path = effectivePath,
                                contentUri = contentUri,
                                dateModified = dateModified,
                                bucketId = bucketId,
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Continue to next volume
            } catch (_: Exception) {
                // Continue to next volume
            }
        }

        return tracks.sortedBy { it.title.lowercase(Locale.US) }
    }

    fun getAudioAlbums(): List<AudioAlbum> {
        val tracks = getAudioTracks()
        // #243: group by (album, artist) — two artists with a same-named
        // album ("Greatest Hits") are two rows, each credited to its own
        // artist with its own track count, instead of one merged row
        // credited to whichever artist came first. The id is a hash of
        // the pair (NUL-separated so ("AB", "C") and ("A", "BC") cannot
        // collide), still a String hash like before. Issue #311: the
        // DATE sort no longer orders by that hash — it orders by the
        // newest track date in the album (latestTrackDateModified).
        // Artists are untouched: an artist's name is its identity by
        // definition, so getAudioArtists() has no symmetric problem.
        return tracks.groupBy { it.album to it.artist }
            .map { (pair, albumTracks) ->
                val (albumName, artistName) = pair
                AudioAlbum(
                    id = "$albumName\u0000$artistName".hashCode().toString(),
                    title = albumName,
                    artist = artistName,
                    trackCount = albumTracks.size,
                    latestTrackDateModified = albumTracks.maxOfOrNull { it.dateModified } ?: 0L,
                )
            }
            .sortedBy { it.title.lowercase(Locale.US) }
    }

    fun getAudioArtists(): List<AudioArtist> {
        val tracks = getAudioTracks()
        return tracks.groupBy { it.artist }
            .map { (artistName, artistTracks) ->
                AudioArtist(
                    id = artistName.hashCode().toString(),
                    name = artistName,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.map { it.album }.distinct().size,
                    latestTrackDateModified = artistTracks.maxOfOrNull { it.dateModified } ?: 0L,
                )
            }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    fun getAudioFolders(): List<AudioFolder> {
        val tracks = getAudioTracks()
        val folderMap = mutableMapOf<String, MutableList<AudioTrack>>()
        for (track in tracks) {
            // Issue #253: getAudioTracks substitutes a content:// URI
            // for a blank DATA path, and File(content://…).parent is
            // meaningless ("content://media/external/audio/media") —
            // every such track landed in one bogus folder. A content://
            // path has no filesystem parent, so it groups under the
            // Music fallback exactly like a blank path.
            val folderPath = if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
                File(track.path).parent ?: "/storage/emulated/0/Music"
            } else {
                "/storage/emulated/0/Music"
            }
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(track)
        }
        return folderMap.map { (path, folderTracks) ->
            val name = File(path).name.ifBlank { context.getString(R.string.media_music) }
            AudioFolder(
                id = path.hashCode().toString(),
                name = name,
                path = path,
                trackCount = folderTracks.size,
                isStorageRemovable = helper.isRemovablePath(path),
                latestTrackDateModified = folderTracks.maxOfOrNull { it.dateModified } ?: 0L,
            )
        }.sortedBy { it.name.lowercase(Locale.US) }
    }
}
