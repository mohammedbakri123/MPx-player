package com.mpx.player.ui.components.persistence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Dialog displaying contents of a custom playlist with Play All, rename
 * and delete actions. Issue #230: rename calls the existing repo method
 * via [onRenamePlaylist]; delete asks for confirmation first — the trash
 * tap alone never deletes anything.
 */
@Composable
fun PlaylistDetailsDialog(
    playlist: CustomPlaylist,
    items: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayItem: (PlaylistItem) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRenamePlaylist: (String) -> Unit,
) {
    // Issue #230: both sub-dialogs are hosted here so both callers
    // (Playlists page, Music screen) get rename + delete-confirm for free.
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MpxTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.playlist_items_subtitle, items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                }
                Row {
                    IconButton(onClick = { showRename = true }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.playlist_cd_rename),
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.playlist_cd_delete),
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (items.isNotEmpty()) {
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MpxTheme.colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.playlist_play_all))
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f).copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MpxTheme.colors.textPrimary,
                            )
                            Text(
                                text = stringResource(R.string.playlist_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayItem(item) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(MpxShapes.small)
                                        .background(MpxTheme.colors.black)
                                        .border(MpxHairline, MpxTheme.colors.border, MpxShapes.small),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MpxTheme.colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MpxTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveItem(item.id) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.playlist_cd_remove_item),
                                        tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
            }
        },
    )

    // Issue #230: rename reuses the create dialog's text-field + save
    // pattern, pre-filled with the current name.
    if (showRename) {
        CreatePlaylistDialog(
            initialName = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { newName, _ ->
                showRename = false
                onRenamePlaylist(newName)
            },
            titleRes = R.string.playlist_rename_dialog_title,
            confirmRes = R.string.action_save,
        )
    }

    // Issue #230: one accidental trash tap must not delete the playlist —
    // only the confirm button here runs onDeletePlaylist.
    if (showDeleteConfirm) {
        MpxDialog(
            title = stringResource(R.string.playlist_delete_confirm_title, playlist.name),
            onDismiss = { showDeleteConfirm = false },
            confirmText = stringResource(R.string.playlist_cd_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDeletePlaylist()
            },
            dismissText = stringResource(R.string.action_cancel),
            body = {
                Text(stringResource(R.string.playlist_delete_confirm_desc))
            },
        )
    }
}
