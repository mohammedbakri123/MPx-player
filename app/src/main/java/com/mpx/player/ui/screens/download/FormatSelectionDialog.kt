package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.util.Locale

/**
 * Per-video format sheet (issue #383): Seal's format-selection page
 * (FormatPage, JunkFood02/Seal, GPL-3.0) adapted to MPx's dialog
 * shape. The header shows the video (title, uploader, duration —
 * no thumbnail: MPx has no image-loading library and the build
 * runs offline). Below it, two radio groups list the video's real
 * formats from yt-dlp info: video formats (best per resolution,
 * highest first) and audio formats (best per codec, highest bitrate
 * first), each with a "Best" row that means the global settings.
 * The confirm builds Seal's format-id string (`TaskFactory` shape:
 * the chosen format ids joined with "+", "bestvideo"/"bestaudio"
 * filling a side left on Best) — both Best means null, i.e. no
 * `-f`, the global format sorter applies. Single videos only: the
 * caller never opens this for playlists or audio-only downloads.
 */
@Composable
fun FormatSelectionDialog(
    videoInfo: VideoInfo,
    onDismiss: () -> Unit,
    onDownload: (formatId: String?) -> Unit,
) {
    val formats = videoInfo.formats.orEmpty()
    val videoFormats = remember(formats) {
        formats
            .filter { it.vcodec?.takeIf { codec -> codec != "none" } != null }
            .groupBy { it.height ?: 0 }
            .filterKeys { it > 0 }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.tbr ?: 0 } }
            .sortedByDescending { it.height ?: 0 }
    }
    val audioFormats = remember(formats) {
        formats
            .filter {
                (it.vcodec == null || it.vcodec == "none") &&
                    it.acodec?.takeIf { codec -> codec != "none" } != null
            }
            .groupBy { it.acodec?.substringBefore(".") ?: "" }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.abr ?: 0 } }
            .sortedByDescending { it.abr ?: 0 }
    }
    var selectedVideo by remember { mutableStateOf<VideoFormat?>(null) }
    var selectedAudio by remember { mutableStateOf<VideoFormat?>(null) }

    MpxDialog(
        title = stringResource(R.string.download_format_title),
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.download_button),
        onConfirm = {
            val videoPart = selectedVideo?.formatId?.takeIf { it.isNotBlank() } ?: "bestvideo"
            val audioPart = selectedAudio?.formatId?.takeIf { it.isNotBlank() } ?: "bestaudio"
            onDownload(
                if (selectedVideo == null && selectedAudio == null) null
                else "$videoPart+$audioPart",
            )
        },
    ) {
        LazyColumn {
            item {
                Text(
                    videoInfo.title?.takeIf { it.isNotBlank() } ?: "",
                    color = MpxTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    videoInfo.uploader?.takeIf { it.isNotBlank() },
                    videoInfo.duration.takeIf { it > 0 }?.let { formatVideoDuration(it.toLong()) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        color = MpxTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Text(
                    stringResource(R.string.download_format_video),
                    color = MpxTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }
            item {
                FormatRow(
                    label = stringResource(R.string.download_format_best),
                    detail = stringResource(R.string.download_format_best_detail),
                    selected = selectedVideo == null,
                    onClick = { selectedVideo = null },
                )
            }
            items(videoFormats, key = { it.formatId ?: "${it.height}" }) { format ->
                FormatRow(
                    label = formatLabel(format),
                    detail = formatDetail(format),
                    selected = selectedVideo == format,
                    onClick = { selectedVideo = format },
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.download_format_audio),
                    color = MpxTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }
            item {
                FormatRow(
                    label = stringResource(R.string.download_format_best),
                    detail = stringResource(R.string.download_format_best_detail),
                    selected = selectedAudio == null,
                    onClick = { selectedAudio = null },
                )
            }
            items(audioFormats, key = { it.formatId ?: "${it.acodec}" }) { format ->
                FormatRow(
                    label = formatLabel(format),
                    detail = formatDetail(format),
                    selected = selectedAudio == format,
                    onClick = { selectedAudio = format },
                )
            }
        }
    }
}

@Composable
private fun FormatRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                label,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = MpxTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "1080p" for video, the codec name for audio. */
private fun formatLabel(format: VideoFormat): String {
    val height = format.height ?: 0
    return if (height > 0) {
        "${height}p"
    } else {
        format.acodec?.substringBefore(".")?.takeIf { it.isNotBlank() } ?: ""
    }
}

/**
 * "vp9 · mp4 · 60 fps · 5.2 Mbps · 84 MB" — only the parts the
 * format actually carries, same shape as Seal's FormatItem
 * (codec before the ".", bitrate in Kbps/Mbps). Sizes come from
 * fileSize, falling back to the approximate size.
 */
private fun formatDetail(format: VideoFormat): String {
    val parts = mutableListOf<String>()
    format.vcodec?.substringBefore(".")?.takeIf { it.isNotBlank() && it != "none" }?.let { parts += it }
    format.acodec?.substringBefore(".")?.takeIf { it.isNotBlank() && it != "none" }?.let { parts += it }
    format.ext?.takeIf { it.isNotBlank() }?.let { parts += it }
    format.fps?.takeIf { it > 0 }?.let { parts += "$it fps" }
    val bitrate = (format.tbr ?: 0).takeIf { it > 0 } ?: (format.abr ?: 0).takeIf { it > 0 }
    bitrate?.let {
        parts += if (it < 1024) "%.0f Kbps".format(it.toFloat()) else "%.2f Mbps".format(it / 1024f)
    }
    val size = (format.fileSize ?: 0L).takeIf { it > 0 }
        ?: (format.fileSizeApproximate ?: 0L).takeIf { it > 0 }
    size?.let { parts += formatSize(it) }
    return parts.joinToString(" · ")
}

/** mm:ss, or h:mm:ss for videos over an hour — mirrors DownloadScreen.formatEta. */
private fun formatVideoDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
