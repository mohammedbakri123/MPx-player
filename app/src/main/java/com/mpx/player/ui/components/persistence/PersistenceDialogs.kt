package com.mpx.player.ui.components.persistence
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaylistItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Dialog displaying user favourites persisted in Room SQLite database.
 */
@Composable
fun FavouritesDialog(
    favourites: List<FavouriteItem>,
    onDismiss: () -> Unit,
    onItemClick: (FavouriteItem) -> Unit,
    onRemoveFavourite: (String) -> Unit,
    onClearAll: () -> Unit,
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.favourites_dialog_title, favourites.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MpxTheme.colors.textPrimary,
                    )
                }
                if (favourites.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text(stringResource(R.string.action_clear_all), color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (favourites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f).copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.favourites_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MpxTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.favourites_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                ) {
                    items(favourites, key = { it.mediaUri }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                .size(40.dp)
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
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveFavourite(item.mediaUri) },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.favourites_cd_remove),
                                    tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
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
}

/**
 * Dialog displaying playback history persisted in Room SQLite database.
 */
@Composable
fun PlaybackHistoryDialog(
    history: List<PlaybackHistoryItem>,
    // #265: the real total (history itself is one page of it) and the
    // load-more callback — the dialog pages exactly like the full History
    // page, so rows past 100 are reachable here too.
    historyTotal: Int,
    onLoadMore: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onItemClick: (PlaybackHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    // #265: load the next page when scrolled near the end. Keys on
    // history.size too, so it re-fires after each page lands while the
    // user is still sitting at the bottom; stops once all rows are in.
    val historyListState = rememberLazyListState()
    val historyNearEnd by remember {
        derivedStateOf {
            val info = historyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(historyNearEnd, history.size) {
        if (historyNearEnd && history.size < historyTotal) {
            onLoadMore?.invoke()
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.history_dialog_title, historyTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MpxTheme.colors.textPrimary,
                    )
                }
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.action_clear_all), color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f).copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MpxTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.history_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = historyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                ) {
                    items(history, key = { it.mediaUri }) { item ->
                        val progress = if (item.durationMs > 0L) {
                            (item.playbackPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
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
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MpxTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dateFormat.format(Date(item.lastPlayedAt)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                        )
                                        if (item.playbackPositionMs > 1_500L) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.history_resume_at, formatTime(item.playbackPositionMs)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MpxTheme.colors.primary,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.history_cd_resume),
                                    tint = MpxTheme.colors.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            if (progress > 0f) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp)),
                                    color = MpxTheme.colors.primary,
                                    trackColor = MpxTheme.colors.border,
                                )
                            }
                        }
                        HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
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
}

/**
 * Dialog to create a new custom playlist persisted in Room.
 * Issue #230: also reused for renaming — pass [initialName] plus the
 * rename title and Save confirm; the text-field + save pattern is the same.
 * Issue #268: [onConfirm] also receives the playlist type. When
 * [showTypeSelector] is true the dialog offers a Video/Audio choice
 * (the shared Playlists page, which used to hardcode video); otherwise
 * the fixed [initialIsVideoPlaylist] is passed through, so the Local
 * (video), Music (audio) and Player (current item) flows keep their type.
 */
@Composable
fun CreatePlaylistDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    titleRes: Int = R.string.playlist_create_dialog_title,
    confirmRes: Int = R.string.action_create,
    initialIsVideoPlaylist: Boolean = false,
    showTypeSelector: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var isVideo by rememberSaveable { mutableStateOf(initialIsVideoPlaylist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.playlist_create_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.playlist_name_placeholder)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                // #268: the type choice — same clickable-row + RadioButton
                // pattern as SortViewDialog. Audio first and default:
                // a playlist made here must be addable from Music.
                if (showTypeSelector) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.playlist_create_type_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVideo = false }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !isVideo,
                            onClick = { isVideo = false },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_type_audio), color = MpxTheme.colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVideo = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isVideo,
                            onClick = { isVideo = true },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_type_video), color = MpxTheme.colors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), isVideo)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MpxTheme.colors.primary),
            ) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.textSecondary)
            }
        },
    )
}

/**
 * Dialog to add a media track to an existing custom playlist or create a new one.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<CustomPlaylist>,
    mediaTitle: String,
    onDismiss: () -> Unit,
    onSelectPlaylist: (CustomPlaylist) -> Unit,
    onCreateNewPlaylist: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Text(
                text = stringResource(R.string.playlist_add_dialog_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.playlist_add_to_header, mediaTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MpxShapes.small)
                        .clickable { onCreateNewPlaylist() }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.playlist_create_new_action),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MpxTheme.colors.primary,
                    )
                }

                HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_no_custom_playlists),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MpxShapes.small)
                                    .clickable { onSelectPlaylist(pl) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MpxTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = pl.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MpxTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = if (pl.isVideoPlaylist) stringResource(R.string.playlist_videos_count, pl.itemCount) else stringResource(R.string.music_songs_count, pl.itemCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.textSecondary)
            }
        },
        confirmButton = {},
    )
}

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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
