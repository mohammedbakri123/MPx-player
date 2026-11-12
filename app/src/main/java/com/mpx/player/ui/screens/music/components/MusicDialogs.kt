package com.mpx.player.ui.screens.music.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.ui.screens.local.CreatePlaylistOutcome
import com.mpx.player.ui.screens.local.LocalSortPopup
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.screens.music.MusicViewModel
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val HISTORY_PAGE_SIZE = 100

@Composable
fun MusicDialogs(
    viewModel: MusicViewModel,
    appSettings: AppSettings,
    customPlaylists: List<CustomPlaylist>,
    showAddToPlaylistDialog: Boolean,
    tracksForPlaylistAdd: List<AudioTrack>,
    showCreatePlaylistDialog: Boolean,
    trackForOptions: AudioTrack?,
    onDismissTrackOptions: () -> Unit,
    sortedTracks: List<AudioTrack>,
    selectedCustomPlaylist: CustomPlaylist?,
    onDismissCustomPlaylist: () -> Unit,
    onUpdateCustomPlaylist: (CustomPlaylist) -> Unit,
    playlistItemsForDialog: List<PlaylistItem>,
    onPlaylistItemsChange: (List<PlaylistItem>) -> Unit,
    showFavouritesDialog: Boolean,
    onDismissFavouritesDialog: () -> Unit,
    favourites: List<FavouriteItem>,
    showHistoryDialog: Boolean,
    onDismissHistoryDialog: () -> Unit,
    history: List<PlaybackHistoryItem>,
    historyTotal: Int,
    historyLimit: Int,
    showSortPopup: Boolean,
    onDismissSortPopup: () -> Unit,
    sortField: SortField,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    showManageTabsDialog: Boolean,
    onDismissManageTabsDialog: () -> Unit,
    playbackCoordinator: PlaybackCoordinator?,
    onOpenPlayer: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current

    // Issue #310: Create playlist dialog — carries pending tracks through creation
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            initialIsVideoPlaylist = false,
            onDismiss = {
                viewModel.dismissCreatePlaylist()
            },
            onConfirm = { name, isVideo ->
                scope.launch {
                    when (val outcome = viewModel.createPlaylistAndAddPending(name, isVideo)) {
                        is CreatePlaylistOutcome.Created -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_playlist_created_msg, outcome.name)
                        )
                        is CreatePlaylistOutcome.Added -> when {
                            outcome.added == outcome.total -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_added_to_playlist_msg, outcome.name)
                            )
                            outcome.added == 0 -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_already_in_playlist_msg, outcome.name)
                            )
                            else -> snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.music_added_some_to_playlist_msg,
                                    outcome.added, outcome.name, outcome.total - outcome.added,
                                )
                            )
                        }
                        CreatePlaylistOutcome.Failed -> { /* Creation failed; add dialog restored by VM */ }
                    }
                }
            },
        )
    }

    // Add Tracks to Playlist Dialog (single track or multi-selected tracks)
    if (showAddToPlaylistDialog && tracksForPlaylistAdd.isNotEmpty()) {
        val tracks = tracksForPlaylistAdd
        val audioPlaylists = customPlaylists.filter { !it.isVideoPlaylist }
        AddToPlaylistDialog(
            playlists = audioPlaylists,
            mediaTitle = if (tracks.size == 1) tracks.first().title
            else stringResource(R.string.playlist_items_count, tracks.size),
            onDismiss = {
                viewModel.dismissAddToPlaylist()
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    val result = viewModel.addPendingToPlaylist(pl.id)
                    when {
                        result.added == result.total -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_added_to_playlist_msg, pl.name)
                        )
                        result.added == 0 -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_already_in_playlist_msg, pl.name)
                        )
                        else -> snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.music_added_some_to_playlist_msg,
                                result.added, pl.name, result.total - result.added,
                            )
                        )
                    }
                }
            },
            onCreateNewPlaylist = {
                viewModel.openCreatePlaylist()
            },
        )
    }

    // Track Options Dialog
    if (trackForOptions != null) {
        val targetTrack = trackForOptions
        AlertDialog(
            onDismissRequest = onDismissTrackOptions,
            containerColor = MpxTheme.colors.black,
            title = {
                Text(
                    text = targetTrack.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Text(
                        text = "${targetTrack.artist} • ${targetTrack.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                onDismissTrackOptions()
                                val idx = sortedTracks.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
                                playbackCoordinator?.playAudioQueue(sortedTracks, startIndex = idx)
                                onOpenPlayer()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_play_track), color = MpxTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                onDismissTrackOptions()
                                scope.launch {
                                    val added = viewModel.toggleFavourite(
                                        FavouriteItem(
                                            mediaUri = targetTrack.path,
                                            mediaId = targetTrack.id,
                                            title = targetTrack.title,
                                            subtitle = "${targetTrack.artist} • ${targetTrack.album}",
                                            isVideo = false,
                                        )
                                    )
                                    snackbarHostState.showSnackbar(
                                        if (added) {
                                            context.getString(R.string.music_added_to_favourites)
                                        } else {
                                            context.getString(R.string.music_removed_from_favourites)
                                        }
                                    )
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_toggle_favourite), color = MpxTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                onDismissTrackOptions()
                                viewModel.showAddToPlaylist(targetTrack)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_add_to_playlist), color = MpxTheme.colors.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissTrackOptions) {
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Custom Playlist Details Dialog
    if (selectedCustomPlaylist != null) {
        val pl = selectedCustomPlaylist
        PlaylistDetailsDialog(
            playlist = pl,
            items = playlistItemsForDialog,
            onDismiss = onDismissCustomPlaylist,
            onPlayAll = {
                val playable = playlistItemsForDialog.map { item ->
                    PlayableMedia(
                        id = item.mediaId,
                        title = item.title,
                        subtitle = item.subtitle,
                        uri = item.mediaUri,
                        isVideo = item.isVideo,
                    )
                }
                if (playable.isNotEmpty()) {
                    playbackCoordinator?.playQueue(playable, startIndex = 0)
                    onDismissCustomPlaylist()
                    onOpenPlayer()
                }
            },
            onPlayItem = { item ->
                playbackCoordinator?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                        )
                    )
                )
                onDismissCustomPlaylist()
                onOpenPlayer()
            },
            onRemoveItem = { itemId ->
                scope.launch {
                    viewModel.removeItemFromPlaylist(itemId)
                    onPlaylistItemsChange(viewModel.getPlaylistItems(pl.id))
                }
            },
            onDeletePlaylist = {
                scope.launch {
                    viewModel.deletePlaylist(pl.id)
                    onDismissCustomPlaylist()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_deleted_playlist_msg, pl.name))
                }
            },
            onRenamePlaylist = { newName ->
                scope.launch {
                    viewModel.renamePlaylist(pl.id, newName)
                    onUpdateCustomPlaylist(pl.copy(name = newName))
                }
            },
        )
    }

    // Favourites Dialog
    if (showFavouritesDialog) {
        FavouritesDialog(
            favourites = favourites,
            onDismiss = onDismissFavouritesDialog,
            onItemClick = { item ->
                onDismissFavouritesDialog()
                playbackCoordinator?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        )
                    )
                )
                onOpenPlayer()
            },
            onRemoveFavourite = { uri ->
                scope.launch {
                    viewModel.removeFavourite(uri)
                    snackbarHostState.showSnackbar(context.getString(R.string.music_removed_from_favourites))
                }
            },
            onClearAll = {
                scope.launch {
                    viewModel.clearFavourites()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_favourites_msg))
                }
            },
        )
    }

    // History Dialog
    if (showHistoryDialog) {
        PlaybackHistoryDialog(
            history = history,
            historyTotal = historyTotal,
            onLoadMore = {
                if (history.size < historyTotal) viewModel.setHistoryLimit(historyLimit + HISTORY_PAGE_SIZE)
            },
            playbackCoordinator = playbackCoordinator,
            onDismiss = onDismissHistoryDialog,
            onItemClick = { item ->
                onDismissHistoryDialog()
                playbackCoordinator?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        )
                    )
                )
                onOpenPlayer()
            },
            onClearHistory = {
                scope.launch {
                    viewModel.clearHistory()
                    playbackCoordinator?.clearResumePositions() ?: viewModel.clearAllResumePositions()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_history_msg))
                }
            },
        )
    }

    // Sort & View Popup
    if (showSortPopup) {
        LocalSortPopup(
            isFolderScope = false,
            currentSort = sortField,
            onSortChange = { newSort -> viewModel.setSortField(newSort) },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { reversed -> viewModel.setSortReverse(reversed) },
            currentView = viewMode,
            onViewChange = { onViewModeChange(it) },
            isTreeMode = false,
            onTreeModeChange = {},
            folderGridColumns = 2,
            onFolderGridColumnsChange = {},
            videoGridColumns = 2,
            onVideoGridColumnsChange = {},
            showFullName = false,
            onShowFullNameChange = {},
            showFieldLocation = false,
            onShowFieldLocationChange = {},
            showFieldCount = false,
            onShowFieldCountChange = {},
            showFieldDuration = false,
            onShowFieldDurationChange = {},
            showFieldSize = false,
            onShowFieldSizeChange = {},
            showFieldDate = false,
            onShowFieldDateChange = {},
            showTreeMode = false,
            showGridColumns = false,
            showFieldsSection = false,
            onDismiss = onDismissSortPopup,
        )
    }

    // Manage Tabs Dialog
    if (showManageTabsDialog) {
        ManageMusicTabsDialog(
            settings = appSettings,
            onDismiss = onDismissManageTabsDialog,
            onToggleTab = { tabId, currentVal ->
                if (!viewModel.toggleMusicTab(tabId, currentVal)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_tab_min_one_required))
                    }
                }
            },
        )
    }
}

@Composable
fun ManageMusicTabsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
) {
    val isOnlyOneVisible = settings.visibleMusicTabsCount <= 1
    val lastActiveSub = stringResource(R.string.settings_tab_last_active_sub)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        title = {
            Text(
                text = stringResource(R.string.music_dialog_tabs_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.music_dialog_tabs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))

                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_tracks),
                    checked = settings.showMusicTracksTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicTracksTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("tracks", settings.showMusicTracksTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_playlists),
                    checked = settings.showMusicPlaylistsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicPlaylistsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("playlists", settings.showMusicPlaylistsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_albums),
                    checked = settings.showMusicAlbumsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicAlbumsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("albums", settings.showMusicAlbumsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_artists),
                    checked = settings.showMusicArtistsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicArtistsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("artists", settings.showMusicArtistsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_folders),
                    checked = settings.showMusicFoldersTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicFoldersTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("folders", settings.showMusicFoldersTab) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done), color = MpxTheme.colors.primary)
            }
        },
    )
}

@Composable
fun MusicTabDialogToggleRow(
    label: String,
    checked: Boolean,
    isLastVisible: Boolean = false,
    subtitle: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}
