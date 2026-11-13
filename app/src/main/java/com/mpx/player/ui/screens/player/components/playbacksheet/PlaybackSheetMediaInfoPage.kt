package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.playback.MediaInfo
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

@Composable
internal fun PlaybackSheetMediaInfoPage(
    getMediaInfo: () -> MediaInfo?,
) {
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mediaInfo = getMediaInfo()
        loaded = true
    }

    if (!loaded) {
        return
    }

    MediaInfoContent(mediaInfo)
}

/**
 * Shared Media Information content: the General / Video / Audio /
 * Subtitles sections for a [MediaInfo]. Used by the playback sheet
 * page (issue #468, mpv track-list) AND the standalone file-list
 * screen (MediaExtractor, before playback) — both render exactly the
 * same sections/rows. Null or fully empty info shows "No media loaded".
 */
@Composable
internal fun MediaInfoContent(
    mediaInfo: MediaInfo?,
) {
    val info = mediaInfo
    if (info == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.media_info_no_media),
                color = MpxTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val unknown = stringResource(R.string.media_info_unknown)

    val hasGeneral = info.title != null || info.format != null || info.fileSize != null || (info.durationMs != null && info.durationMs > 0L)
    val hasVideo = info.videoTrack != null
    val hasAudio = info.audioTracks.isNotEmpty()
    val hasSubtitles = info.subtitleTracks.isNotEmpty()

    if (!hasGeneral && !hasVideo && !hasAudio && !hasSubtitles) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.media_info_no_media),
                color = MpxTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // --- General Section ---
        if (hasGeneral) {
            PlaybackSheetSectionTitle(stringResource(R.string.media_info_section_general))
            MediaInfoRow(
                label = stringResource(R.string.media_info_title_label),
                value = info.title ?: unknown,
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_format),
                value = info.format?.lowercase() ?: unknown,
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_file_size),
                value = formatFileSize(info.fileSize, unknown),
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_duration),
                value = formatDuration(info.durationMs, unknown),
            )
        }

        // --- Video Section ---
        val video = info.videoTrack
        if (video != null) {
            PlaybackSheetSectionTitle(stringResource(R.string.media_info_section_video))
            MediaInfoRow(
                label = stringResource(R.string.media_info_codec),
                value = formatCodec(video.codec, unknown),
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_resolution),
                value = formatResolution(video.width, video.height, unknown),
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_frame_rate),
                value = formatFps(video.fps, unknown),
            )
            MediaInfoRow(
                label = stringResource(R.string.media_info_bitrate),
                value = formatBitrate(video.bitrate, unknown),
            )
        }

        // --- Audio Section ---
        if (hasAudio) {
            info.audioTracks.forEachIndexed { index, audio ->
                val sectionHeader = if (info.audioTracks.size == 1) {
                    stringResource(R.string.media_info_section_audio)
                } else {
                    val trackNum = index + 1
                    if (audio.isSelected) {
                        stringResource(R.string.media_info_audio_track_selected, trackNum)
                    } else {
                        stringResource(R.string.media_info_audio_track, trackNum)
                    }
                }
                PlaybackSheetSectionTitle(sectionHeader)
                MediaInfoRow(
                    label = stringResource(R.string.media_info_codec),
                    value = formatCodec(audio.codec, unknown),
                )
                MediaInfoRow(
                    label = stringResource(R.string.media_info_channels),
                    value = formatChannels(audio.channels, unknown),
                )
                MediaInfoRow(
                    label = stringResource(R.string.media_info_bitrate),
                    value = formatBitrate(audio.bitrate, unknown),
                )
                MediaInfoRow(
                    label = stringResource(R.string.media_info_sample_rate),
                    value = formatSampleRate(audio.sampleRate, unknown),
                )
                MediaInfoRow(
                    label = stringResource(R.string.media_info_language),
                    value = audio.language?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
                        ?: unknown,
                )
            }
        }

        // --- Subtitles Section ---
        if (hasSubtitles) {
            PlaybackSheetSectionTitle(stringResource(R.string.media_info_section_subtitles))
            info.subtitleTracks.forEach { sub ->
                val statusParts = mutableListOf<String>()
                if (sub.isSelected) {
                    statusParts.add(stringResource(R.string.media_info_status_selected))
                }
                if (sub.isExternal) {
                    statusParts.add(stringResource(R.string.media_info_status_external))
                }
                val status = if (statusParts.isNotEmpty()) {
                    statusParts.joinToString(" • ")
                } else {
                    stringResource(R.string.media_info_status_embedded)
                }

                val trackLabel = if (sub.language.isNotBlank() &&
                    !sub.language.equals("und", ignoreCase = true) &&
                    !sub.label.contains(sub.language, ignoreCase = true)
                ) {
                    "${sub.label} (${sub.language})"
                } else {
                    sub.label
                }

                MediaInfoRow(
                    label = trackLabel,
                    value = status,
                )
            }
        }
    }
}

@Composable
internal fun MediaInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MpxTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MpxTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatResolution(width: Int?, height: Int?, unknown: String): String {
    if (width == null || height == null || width <= 0 || height <= 0) return unknown
    val pSuffix = when (height) {
        720 -> " (720p)"
        1080 -> " (1080p)"
        2160 -> " (2160p)"
        else -> ""
    }
    return "$width \u00D7 $height$pSuffix"
}

internal fun formatFps(fps: Double?, unknown: String): String {
    if (fps == null || fps <= 0.0) return unknown
    val formatted = String.format(Locale.US, "%.2f", fps)
    return formatted.removeSuffix(".00").trimEnd('0').removeSuffix(".")
}

internal fun formatBitrate(bitrate: Long?, unknown: String): String {
    if (bitrate == null || bitrate <= 0L) return unknown
    return if (bitrate >= 1_000_000L) {
        String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000.0)
    } else {
        String.format(Locale.US, "%.1f kbps", bitrate / 1_000.0)
    }
}

internal fun formatFileSize(size: Long?, unknown: String): String {
    if (size == null || size <= 0L) return unknown
    return if (size >= 1024L * 1024L * 1024L) {
        String.format(Locale.US, "%.1f GB", size.toDouble() / (1024.0 * 1024.0 * 1024.0))
    } else if (size >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f MB", size.toDouble() / (1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%.1f KB", size.toDouble() / 1024.0)
    }
}

internal fun formatDuration(durationMs: Long?, unknown: String): String {
    if (durationMs == null || durationMs <= 0L) return unknown
    return formatTimeMs(durationMs)
}

internal fun formatChannels(channels: Int?, unknown: String): String {
    if (channels == null || channels <= 0) return unknown
    return when (channels) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> channels.toString()
    }
}

internal fun formatSampleRate(sampleRate: Int?, unknown: String): String {
    if (sampleRate == null || sampleRate <= 0) return unknown
    return if (sampleRate > 1000) {
        if (sampleRate % 1000 == 0) {
            "${sampleRate / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0)
        }
    } else {
        "$sampleRate kHz"
    }
}

internal fun formatCodec(codec: String?, unknown: String): String {
    if (codec.isNullOrBlank()) return unknown
    return when (codec.lowercase().trim()) {
        "h264", "avc", "avc1" -> "H.264"
        "hevc", "h265", "hev1", "hvc1" -> "HEVC"
        "av1", "av01" -> "AV1"
        "vp8" -> "VP8"
        "vp9" -> "VP9"
        "aac" -> "AAC"
        "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        "dts", "dca" -> "DTS"
        "dtshd" -> "DTS-HD"
        "opus" -> "Opus"
        "mp3" -> "MP3"
        "flac" -> "FLAC"
        "vorbis" -> "Vorbis"
        "truehd", "truhd" -> "TrueHD"
        else -> codec.uppercase()
    }
}
