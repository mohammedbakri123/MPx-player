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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mpx.player.download.PlaylistEntry
import com.mpx.player.download.PlaylistInfo
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

/**
 * Playlist entry picker (issue #345): the pasted URL resolved to a
 * playlist — list its entries with checkboxes (title + duration when
 * known), Select all / none, and a Download (N) confirm that enqueues
 * the selected entries. All entries start selected. Same MpxDialog
 * shape as DownloadQualityDialog (#338); the confirm does nothing
 * while nothing is selected (the dialog has no disabled confirm
 * state). The entries list is a LazyColumn, not a scrolling Column,
 * so a channel URL with thousands of entries only composes what is
 * on screen. The caller starts the downloads — this dialog only
 * picks.
 */
@Composable
fun PlaylistPickerDialog(
    playlist: PlaylistInfo,
    onDismiss: () -> Unit,
    onDownload: (List<PlaylistEntry>) -> Unit,
) {
    var selected by remember(playlist) { mutableStateOf(playlist.entries.indices.toSet()) }

    MpxDialog(
        title = playlist.title.ifBlank { stringResource(R.string.download_playlist_title) },
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.download_playlist_download, selected.size),
        onConfirm = {
            if (selected.isNotEmpty()) {
                onDownload(playlist.entries.filterIndexed { index, _ -> index in selected })
            }
        },
    ) {
        LazyColumn {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { selected = playlist.entries.indices.toSet() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.download_playlist_select_all),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MpxTheme.colors.primary,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = { selected = emptySet() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.download_playlist_select_none),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MpxTheme.colors.primary,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(playlist.entries, key = { index, _ -> index }) { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (index in selected) selected - index else selected + index
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = index in selected,
                        onCheckedChange = {
                            selected = if (index in selected) selected - index else selected + index
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MpxTheme.colors.primary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        entry.durationSeconds?.let { seconds ->
                            Text(
                                text = formatPlaylistDuration(seconds),
                                color = MpxTheme.colors.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** mm:ss, or h:mm:ss for entries over an hour — mirrors DownloadScreen.formatEta. */
private fun formatPlaylistDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
