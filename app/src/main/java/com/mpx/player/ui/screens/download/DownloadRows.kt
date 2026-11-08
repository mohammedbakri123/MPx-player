package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.download.DownloadedFile
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

/**
 * Issue #379: rows shared by the Download page and the Downloaded screen.
 * Moved here verbatim from DownloadScreen — the Downloaded screen (Seal's
 * task-list page model) shows the same finished files and history the
 * Download page used to list inline.
 */

@Composable
internal fun DownloadedFileRow(
    file: DownloadedFile,
    dateText: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isAudio = file.extension in AUDIO_EXTENSIONS
        Icon(
            imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.nameWithoutExtension,
                color = MpxTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatSize(file.size)} · $dateText",
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.download_delete),
                tint = MpxTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * One history row (issue #346): the recorded title, the uploader when
 * known + the completion date, play and forget-entry buttons. Same row
 * shape as DownloadedFileRow above; the delete button only removes the
 * history entry, never the file.
 */
@Composable
internal fun DownloadHistoryRow(
    entry: DownloadHistoryEntity,
    dateText: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isAudio = entry.fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
        Icon(
            imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = MpxTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (entry.uploader != null) "${entry.uploader} · $dateText" else dateText,
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.download_delete),
                tint = MpxTheme.colors.textSecondary,
            )
        }
    }
}

internal val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

/** Mirrors LocalScreen.formatSize exactly (same thresholds, same units). */
internal fun formatSize(bytes: Long): String {
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
