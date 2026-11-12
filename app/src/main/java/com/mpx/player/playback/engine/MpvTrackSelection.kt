package com.mpx.player.playback.engine

import android.util.Log
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.ChapterInfo
import com.mpx.player.playback.MediaAudioTrackInfo
import com.mpx.player.playback.MediaInfo
import com.mpx.player.playback.MediaVideoTrackInfo
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo

/**
 * Audio, video, and subtitle track listing and switching under libmpv.
 *
 * Owns the mpv `track-list` property queries ([getAudioTracks], [getSubtitleTracks],
 * [getVideoTrackIds]) and track selection writes ([selectTrack], [selectAudioTrack],
 * [selectSubtitleTrack]) driving mpv's `aid`, `sid`, and `vid` properties via [MpvCoreEngine.port].
 *
 * Collaborators:
 * - [MpvCoreEngine]: provides access to the native abstraction [MpvCoreEngine.port] ([MpvPort]).
 * - [Host]: communication seam to [MpvSubtitleRenderer] (implemented by the engine facade).
 *
 * Invariants:
 * - Embedded track or "Off" selection: selecting an embedded subtitle track or disabling subtitles
 *   ("Off" / `sid="no"`) clears any remembered external subtitle pick via [Host.clearRememberedExternalPick]
 *   (issue #372), preventing stale external subtitle reapplications.
 * - External track selection: selecting an external subtitle track updates `sid` while preserving
 *   the remembered external subtitle pick.
 * - Unit testing: fully unit-testable without native mpv binaries via [MpvPort] test doubles
 *   (see `MpvTrackSelectionTest`).
 */
internal class MpvTrackSelection(
    core: MpvCoreEngine,
    private val host: Host,
) {

    /**
     * Seam for the subtitle state owned by [MpvSubtitleRenderer]
     * (Phase 4 step 3). Called at exactly the points the old monolith
     * cleared that state.
     */
    interface Host {
        /**
         * The user picked Off or an embedded subtitle track: the
         * remembered external pick is superseded, and the armed pin
         * must be cleared too, or the track-list handler could
         * re-select the external track over the explicit pick
         * (issue #372).
         */
        fun clearRememberedExternalPick()
    }

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    private val port: MpvPort = core.port

    fun selectTrack(type: TrackType, id: String?) {
        when (type) {
            TrackType.AUDIO -> {
                if (id == null || id == "-1" || id.isBlank()) {
                    try { port.setPropertyString("aid", "no") } catch (_: Exception) {}
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull() ?: return
                        val trackIndex = parts[1].toIntOrNull() ?: return
                        selectAudioTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull() ?: return
                        val tracks = getAudioTracks()
                        val target = tracks.getOrNull(trackIdx) ?: return
                        selectAudioTrack(target.groupIndex, target.trackIndex)
                    }
                }
            }
            TrackType.SUBTITLE -> {
                if (id == null || id == "-1" || id.isBlank()) {
                    selectSubtitleTrack(null, null)
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull()
                        val trackIndex = parts[1].toIntOrNull()
                        selectSubtitleTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull()
                        if (trackIdx == null || trackIdx < 0) {
                            selectSubtitleTrack(null, null)
                        } else {
                            val tracks = getSubtitleTracks()
                            val target = tracks.getOrNull(trackIdx)
                            if (target != null) {
                                selectSubtitleTrack(target.groupIndex, target.trackIndex)
                            } else {
                                selectSubtitleTrack(null, null)
                            }
                        }
                    }
                }
            }
            TrackType.VIDEO -> {
                // Video track selection (multi-angle / multi-quality files):
                // same id scheme as audio — "group:track" or a plain ordinal
                // among video tracks — driving mpv's "vid" property.
                if (id == null || id == "-1" || id.isBlank()) {
                    try { port.setPropertyString("vid", "auto") } catch (_: Exception) {}
                } else {
                    val videoIds = getVideoTrackIds()
                    val targetId = run {
                        val parts = id.split(":")
                        if (parts.size == 2) {
                            val groupIndex = parts[0].toIntOrNull() ?: return
                            val trackIndex = parts[1].toIntOrNull() ?: return
                            // Issue #247: validate, don't fall back by
                            // position — groupIndex is the ordinal among
                            // video tracks and trackIndex is the mpv id,
                            // so the track at that ordinal must carry
                            // that id or the tap is stale and this is a
                            // no-op (the ?: return below skips the vid
                            // write). The old getOrNull(groupIndex) ?:
                            // getOrNull(trackIndex) picked whatever id
                            // sat at those positions once the list changed.
                            videoIds.getOrNull(groupIndex)?.takeIf { it == trackIndex }
                        } else {
                            val ordinal = id.toIntOrNull() ?: return
                            videoIds.getOrNull(ordinal)
                        }
                    } ?: return
                    try { port.setPropertyString("vid", targetId.toString()) } catch (_: Exception) {}
                }
            }
        }
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = getAudioTracks()
        // Issue #247: no positional fallback — groupIndex is the ordinal
        // among audio tracks, so getOrNull(groupIndex) picks whatever
        // track sits at that position now, which is a different track
        // when the list changed between render and tap. A stale tap is
        // a no-op (the current selection stays, and aid is left alone —
        // NOT set to "no"); the UI list refreshes via updateTracksState()
        // in the manager and on every track-list change.
        val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
        if (target != null) {
            try { port.setPropertyString("aid", target.trackIndex.toString()) } catch (_: Exception) {}
        } else {
            Log.w(TAG, "Ignoring stale audio track pick ($groupIndex:$trackIndex): no such track")
        }
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        if (groupIndex == null || trackIndex == null) {
            // Issue #372: the user turned subtitles Off — the remembered
            // external pick is superseded. Clear the armed pin too, or
            // the track-list handler's fallback could re-select the
            // external track over the user's explicit Off.
            host.clearRememberedExternalPick()
            try { port.setPropertyString("sid", "no") } catch (_: Exception) {}
        } else {
            val tracks = getSubtitleTracks()
            // Issue #247: same as selectAudioTrack — no positional
            // fallback; a stale tap is a no-op and sid is left alone
            // (NOT set to "no", which would drop subtitles).
            val target = tracks.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            if (target != null) {
                if (!target.isExternal) {
                    // Issue #372: the user picked an embedded track — it
                    // supersedes the remembered external pick, same as
                    // Off above. An external pick leaves the remembered
                    // pick alone (the re-apply must not override a manual
                    // pick of a different external track — see
                    // reapplyRememberedPick's BLOCKED guard).
                    host.clearRememberedExternalPick()
                }
                try { port.setPropertyString("sid", target.trackIndex.toString()) } catch (_: Exception) {}
            } else {
                Log.w(TAG, "Ignoring stale subtitle track pick ($groupIndex:$trackIndex): no such track")
            }
        }
    }

    /** mpv track-list ids of video tracks, in order — used by VIDEO selection. */
    fun getVideoTrackIds(): List<Int> {
        val count = port.getPropertyInt("track-list/count") ?: 0
        val ids = mutableListOf<Int>()
        for (i in 0 until count) {
            val type = port.getPropertyString("track-list/$i/type")
            if (type.equals("video", ignoreCase = true)) {
                ids.add(port.getPropertyInt("track-list/$i/id") ?: (ids.size + 1))
            }
        }
        return ids
    }

    fun getAudioTracks(): List<AudioTrackInfo> {
        val count = port.getPropertyInt("track-list/count") ?: 0
        val audioList = mutableListOf<AudioTrackInfo>()
        var audioGroup = 0
        for (i in 0 until count) {
            val type = port.getPropertyString("track-list/$i/type")
            if (type.equals("audio", ignoreCase = true)) {
                val id = port.getPropertyInt("track-list/$i/id") ?: (audioGroup + 1)
                val title = port.getPropertyString("track-list/$i/title")
                val lang = port.getPropertyString("track-list/$i/lang") ?: "und"
                val selected = port.getPropertyBoolean("track-list/$i/selected") ?: false
                val label = title?.ifBlank { null } ?: "Audio $id ($lang)"
                audioList.add(
                    AudioTrackInfo(
                        groupIndex = audioGroup,
                        trackIndex = id,
                        label = label,
                        language = lang,
                        isSelected = selected,
                        mimeType = port.getPropertyString("track-list/$i/codec"),
                    )
                )
                audioGroup++
            }
        }
        return audioList
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val count = port.getPropertyInt("track-list/count") ?: 0
        val subtitleList = mutableListOf<SubtitleTrackInfo>()
        var subGroup = 0
        for (i in 0 until count) {
            val type = port.getPropertyString("track-list/$i/type")
            if (type.equals("sub", ignoreCase = true)) {
                val id = port.getPropertyInt("track-list/$i/id") ?: (subGroup + 1)
                val title = port.getPropertyString("track-list/$i/title")
                val lang = port.getPropertyString("track-list/$i/lang") ?: "und"
                val selected = port.getPropertyBoolean("track-list/$i/selected") ?: false
                val external = port.getPropertyBoolean("track-list/$i/external") ?: false
                val isDefault = port.getPropertyBoolean("track-list/$i/default") ?: false
                val label = title?.ifBlank { null } ?: "Subtitle $id ($lang)"
                subtitleList.add(
                    SubtitleTrackInfo(
                        groupIndex = subGroup,
                        trackIndex = id,
                        label = label,
                        language = lang,
                        isSelected = selected,
                        isExternal = external,
                        isDefault = isDefault,
                    )
                )
                subGroup++
            }
        }
        return subtitleList
    }

    /**
     * Chapter markers for the current video (issue #178), read from
     * mpv's chapter-list: `chapter-list/count`, then per chapter
     * `chapter-list/<i>/time` (seconds, double) and
     * `chapter-list/<i>/title`. Sorted by time; a chapter with no
     * title gets an empty title, and one with no/invalid time is
     * skipped. Empty when the file has no chapters. Never throws —
     * a failed read returns an empty list.
     */
    fun getChapters(): List<ChapterInfo> {
        return try {
            val count = port.getPropertyInt("chapter-list/count") ?: 0
            val chapters = mutableListOf<ChapterInfo>()
            for (i in 0 until count) {
                val timeSec = port.getPropertyDouble("chapter-list/$i/time") ?: continue
                if (timeSec < 0.0 || !timeSec.isFinite()) continue
                val title = port.getPropertyString("chapter-list/$i/title") ?: ""
                chapters.add(ChapterInfo(title = title, timeMs = (timeSec * 1000.0).toLong()))
            }
            chapters.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Technical media information about the current file and its tracks (issue #468).
     * Reads directly from mpv track-list properties and the media item.
     */
    fun getMediaInfo(currentMedia: PlayableMedia?, durationMs: Long?): MediaInfo {
        val count = port.getPropertyInt("track-list/count") ?: 0
        var selectedVideo: MediaVideoTrackInfo? = null
        var fallbackVideo: MediaVideoTrackInfo? = null
        val audioList = mutableListOf<MediaAudioTrackInfo>()
        var audioGroup = 0

        for (i in 0 until count) {
            val type = port.getPropertyString("track-list/$i/type")
            if (type.equals("video", ignoreCase = true)) {
                val codec = port.getPropertyString("track-list/$i/codec")?.takeIf { it.isNotBlank() }
                val width = port.getPropertyInt("track-list/$i/demux-w")?.takeIf { it > 0 }
                val height = port.getPropertyInt("track-list/$i/demux-h")?.takeIf { it > 0 }
                val fps = port.getPropertyDouble("track-list/$i/demux-fps")?.takeIf { it > 0.0 }
                val bitrate = (port.getPropertyInt("track-list/$i/demux-bitrate")?.takeIf { it > 0 }?.toLong()
                    ?: port.getPropertyDouble("track-list/$i/demux-bitrate")?.takeIf { it > 0.0 }?.toLong())
                val selected = port.getPropertyBoolean("track-list/$i/selected") ?: false
                val videoTrack = MediaVideoTrackInfo(
                    codec = codec,
                    width = width,
                    height = height,
                    fps = fps,
                    bitrate = bitrate,
                )
                if (fallbackVideo == null) {
                    fallbackVideo = videoTrack
                }
                if (selected) {
                    selectedVideo = videoTrack
                }
            } else if (type.equals("audio", ignoreCase = true)) {
                val id = port.getPropertyInt("track-list/$i/id") ?: (audioGroup + 1)
                val codec = port.getPropertyString("track-list/$i/codec")?.takeIf { it.isNotBlank() }
                val channels = port.getPropertyInt("track-list/$i/audio-channels")?.takeIf { it > 0 }
                val bitrate = (port.getPropertyInt("track-list/$i/demux-bitrate")?.takeIf { it > 0 }?.toLong()
                    ?: port.getPropertyDouble("track-list/$i/demux-bitrate")?.takeIf { it > 0.0 }?.toLong())
                val sampleRate = port.getPropertyInt("track-list/$i/demux-samplerate")?.takeIf { it > 0 }
                val lang = port.getPropertyString("track-list/$i/lang")?.takeIf { it.isNotBlank() }
                val selected = port.getPropertyBoolean("track-list/$i/selected") ?: false
                val title = port.getPropertyString("track-list/$i/title")?.takeIf { it.isNotBlank() }
                audioList.add(
                    MediaAudioTrackInfo(
                        id = id,
                        name = title,
                        codec = codec,
                        channels = channels,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        language = lang,
                        isSelected = selected,
                    )
                )
                audioGroup++
            }
        }

        val videoTrack = selectedVideo ?: fallbackVideo
        val subtitleTracks = getSubtitleTracks()

        val pathExt = currentMedia?.filePath?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && !it.contains('/') }
        val titleExt = currentMedia?.title?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && !it.contains('/') }
        val uriExt = try {
            currentMedia?.uri?.let { android.net.Uri.parse(it).path?.substringAfterLast('.', "") }?.takeIf { it.isNotEmpty() && !it.contains('/') }
        } catch (_: Exception) {
            null
        }
        val format = (pathExt ?: titleExt ?: uriExt)?.lowercase()?.takeIf { it.isNotBlank() }

        val fileSize: Long? = currentMedia?.filePath?.let { path ->
            try {
                val f = java.io.File(path)
                if (f.exists() && f.isFile && f.length() > 0L) f.length() else null
            } catch (_: Exception) {
                null
            }
        }

        return MediaInfo(
            title = currentMedia?.title,
            format = format,
            fileSize = fileSize,
            durationMs = durationMs,
            videoTrack = if (currentMedia?.isVideo != false) videoTrack else null,
            audioTracks = audioList,
            subtitleTracks = subtitleTracks,
        )
    }
}
