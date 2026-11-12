package com.mpx.player.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mpx.player.R
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.Playlist

/**
 * Scanner for device (MediaStore) playlists and playlist track resolution.
 *
 * Collaborators:
 * - [Context]: provides [android.content.ContentResolver] for querying MediaStore.
 * - [MediaStoreScannerHelper]: resolves playlist content URIs and volume names.
 * - [audioTrackProvider]: supplier of all active audio tracks, used to resolve playlist track references.
 *
 * Invariants:
 * - Playlist deduplication (#308): deduplicates playlists using a composite `(volume, id)` key
 *   (e.g. `"$volume:$id"`), ensuring identical playlist names across different storage volumes
 *   are preserved with distinct identities.
 * - Single library scan: tracks are resolved against a single shared audio track scan so displayed
 *   counts match playable members on tap.
 */
internal class PlaylistMediaScanner(
    private val context: Context,
    private val helper: MediaStoreScannerHelper,
    private val audioTrackProvider: () -> List<AudioTrack>,
) {
    fun getPlaylists(): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        // Issue #308: dedupe by (volume, id), not name. MediaStore
        // playlist _IDs are unique per volume only, so an internal "Mix"
        // and an SD-card "Mix" are different playlists — the old
        // seenNames set dropped the second one entirely and its tracks
        // became unreachable. The UI-facing id is "volume:id" (the same
        // colon-namespacing precedent as #217's "custom:" ids), so the
        // id itself is the (volume, id) key downstream: two same-named
        // playlists on different volumes get different ids, and lazy-row
        // keys / selection / opening all distinguish them.
        val seenIds = mutableSetOf<String>()
        // One library scan shared by every playlist below, so the displayed
        // count is exactly the number of member tracks that will play on tap.
        val tracksById = audioTrackProvider().associateBy { it.id }
        val projection = arrayOf(
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME,
        )
        // (volume, uri) pairs — getPlaylistContentUris() drops the volume
        // name, and the volume is part of the identity. The no-volume
        // fallback (API < 29, or no volumes discovered) keeps the bare
        // numeric id, as before.
        val volumeUris: List<Pair<String?, Uri>> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val names = helper.getExternalVolumeNames()
                if (names.isNotEmpty()) {
                    names.mapNotNull { name ->
                        try {
                            name to MediaStore.Audio.Playlists.getContentUri(name)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    listOf(null to MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)
                }
            } else {
                listOf(null to MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)
            }
        for ((volume, uri) in volumeUris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Playlists.NAME} ASC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME)
                    while (cursor.moveToNext()) {
                        val rawId = if (idCol >= 0) cursor.getLong(idCol).toString() else ""
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: context.getString(R.string.media_playlist) else context.getString(R.string.media_playlist)
                        val id = if (volume != null) "$volume:$rawId" else rawId
                        if (seenIds.add(id)) {
                            val memberTracks = rawId.toLongOrNull()?.let { playlistId ->
                                resolvePlaylistTracks(playlistId, tracksById, volume)
                            }.orEmpty()
                            playlists.add(
                                Playlist(
                                    id = id,
                                    title = name,
                                    itemCount = memberTracks.size,
                                    subtitle = context.getString(R.string.media_playlist_device),
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Playlists may be restricted or unsupported on newer Android targets
            }
        }
        return playlists
    }

    fun getPlaylistTracks(playlistId: String): List<AudioTrack> {
        // Issue #308: a namespaced "volume:id" resolves members from
        // that volume only — numeric ids repeat across volumes, so the
        // old try-each-volume scan could return the other volume's
        // members for the same numeric id. A bare numeric id (the
        // pre-#308 shape, and the API < 29 fallback) keeps the old scan.
        // "custom:…" ids never reach here — MusicScreen branches on the
        // custom namespace first — and would fail the parse below anyway.
        val colon = playlistId.indexOf(':')
        val volume: String?
        val numericId: Long?
        if (colon > 0) {
            volume = playlistId.substring(0, colon)
            numericId = playlistId.substring(colon + 1).toLongOrNull()
        } else {
            volume = null
            numericId = playlistId.toLongOrNull()
        }
        val id = numericId ?: return emptyList()
        return resolvePlaylistTracks(id, audioTrackProvider().associateBy { it.id }, volume)
    }

    /**
     * Resolves a device playlist's members to library tracks, preserving the
     * playlist's own member order. Members whose audio id is not in the
     * library (removed file, non-music entry, path dedupe) are skipped, so
     * the result is exactly what tapping the playlist will queue.
     */
    private fun resolvePlaylistTracks(
        playlistId: Long,
        tracksById: Map<String, AudioTrack>,
        volumeName: String? = null,
    ): List<AudioTrack> {
        return getPlaylistMemberAudioIds(playlistId, volumeName).mapNotNull { audioId ->
            tracksById[audioId.toString()]
        }
    }

    /**
     * Reads the MediaStore members table for one device playlist, in
     * PLAY_ORDER. Issue #308: with a volume, queries only that volume's
     * members table — playlist ids are unique per volume, so scanning
     * volumes in turn and returning the first non-empty list could
     * return another volume's members when two volumes reuse the same
     * numeric id. Without a volume (a bare pre-#308 id, or API < 29)
     * the old first-non-empty scan is kept.
     */
    private fun getPlaylistMemberAudioIds(playlistId: Long, volumeName: String? = null): List<Long> {
        val memberUris = mutableListOf<Uri>()
        if (volumeName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                memberUris.add(
                    MediaStore.Audio.Playlists.Members.getContentUri(volumeName, playlistId)
                )
            } catch (_: Exception) {
                return emptyList()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (volume in helper.getExternalVolumeNames()) {
                    try {
                        memberUris.add(
                            MediaStore.Audio.Playlists.Members.getContentUri(volume, playlistId)
                        )
                    } catch (_: Exception) {
                        // Skip volumes that cannot build a members URI
                    }
                }
            }
            if (memberUris.isEmpty()) {
                try {
                    memberUris.add(
                        MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
                    )
                } catch (_: Exception) {
                    return emptyList()
                }
            }
        }
        val projection = arrayOf(
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.PLAY_ORDER,
        )
        for (uri in memberUris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC",
                )?.use { cursor ->
                    val audioIdCol = cursor.getColumnIndex(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID
                    )
                    if (audioIdCol < 0) return@use
                    val ids = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        ids.add(cursor.getLong(audioIdCol))
                    }
                    if (ids.isNotEmpty()) return ids
                }
            } catch (_: Exception) {
                // This volume has no such playlist; try the next one
            }
        }
        return emptyList()
    }
}
