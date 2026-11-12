package com.mpx.player.ui.screens.local.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mpx.player.R
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.screens.local.CreatePlaylistOutcome
import com.mpx.player.ui.screens.local.LocalSortPopup
import com.mpx.player.ui.screens.local.LocalViewModel
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.screens.local.SortViewDialog
import com.mpx.player.ui.screens.local.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LocalDialogs(
    showSortDialog: Boolean,
    onDismissSortDialog: () -> Unit,
    showFolderSortDialog: Boolean,
    onDismissFolderSortDialog: () -> Unit,
    showSortPopup: Boolean,
    onDismissSortPopup: () -> Unit,
    popupFolderScope: Boolean,
    currentFolder: MediaFolder?,
    showAddToPlaylistDialog: Boolean,
    videosForPlaylistAdd: List<VideoFile>,
    customPlaylists: List<CustomPlaylist>,
    showCreatePlaylistDialog: Boolean,
    viewModel: LocalViewModel,
    appSettings: AppSettings,
    sortField: SortField,
    secondarySortField: SortField,
    folderSortField: SortField,
    folderSecondarySortField: SortField,
    viewMode: ViewMode,
    isTreeMode: Boolean,
    onViewModeChange: (ViewMode) -> Unit,
    onTreeModeChange: (Boolean) -> Unit,
    onOpenFolderSortDialog: () -> Unit,
    onOpenRootSortDialog: () -> Unit,
    onRefreshFolders: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current

    // SortViewDialog for Root Folders List
    if (showSortDialog && currentFolder == null) {
        SortViewDialog(
            currentSort = sortField,
            currentSecondarySort = secondarySortField,
            currentView = viewMode,
            showAllFolders = appSettings.showAllFolders,
            separateViewLayout = appSettings.separateViewLayout,
            isFileView = appSettings.fileView,
            onFileViewChange = { isChecked ->
                viewModel.setFileView(isChecked)
            },
            onSortChange = { newSort ->
                viewModel.setRootSortField(newSort)
            },
            onSecondarySortChange = { newSecSort ->
                viewModel.setRootSecondarySortField(newSecSort)
            },
            onViewChange = {
                onViewModeChange(it)
            },
            onSeparateViewLayoutChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(separateViewLayout = isChecked) }
                scope.launch {
                    val msg = if (isChecked) {
                        context.getString(R.string.snackbar_separate_view_enabled)
                    } else {
                        context.getString(R.string.snackbar_separate_view_disabled)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            },
            onShowAllFoldersChange = { isChecked ->
                viewModel.updateSettings { it.copy(showAllFolders = isChecked) }
                onRefreshFolders(isChecked)
                scope.launch {
                    val msg = if (isChecked) {
                        context.getString(R.string.local_snackbar_show_all)
                    } else {
                        context.getString(R.string.local_snackbar_filtered)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldSize = isChecked) }
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDate = isChecked) }
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = isChecked) }
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = isChecked) }
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortReverse = isChecked) }
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortSecondaryReverse = isChecked) }
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortNumeric = isChecked) }
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortCaseSensitive = isChecked) }
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortIgnoreExtension = isChecked) }
            },
            onDismiss = onDismissSortDialog,
        )
    }

    // SortViewDialog for Folder Contents View
    if (showFolderSortDialog && currentFolder != null) {
        SortViewDialog(
            currentSort = folderSortField,
            currentView = viewMode,
            isFileView = true,
            onViewChange = { newMode ->
                onViewModeChange(newMode)
            },
            onSortChange = { newSort ->
                viewModel.setFolderSortField(newSort)
            },
            currentSecondarySort = folderSecondarySortField,
            onSecondarySortChange = { newSecSort ->
                viewModel.setFolderSecondarySortField(newSecSort)
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldSize = isChecked) }
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDate = isChecked) }
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = isChecked) }
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = isChecked) }
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortReverse = isChecked) }
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortSecondaryReverse = isChecked) }
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortNumeric = isChecked) }
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortCaseSensitive = isChecked) }
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortIgnoreExtension = isChecked) }
            },
            onDismiss = onDismissFolderSortDialog,
        )
    }

    // Local Sort & View popup
    if (showSortPopup) {
        if (popupFolderScope && currentFolder != null) {
            LocalSortPopup(
                isFolderScope = true,
                currentSort = folderSortField,
                onSortChange = { newSort ->
                    viewModel.setFolderSortField(newSort)
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    viewModel.updateSettings(commit = true) { it.copy(sortReverse = reversed) }
                },
                currentView = viewMode,
                onViewChange = { newMode ->
                    onViewModeChange(newMode)
                    scope.launch {
                        val msg = if (newMode == ViewMode.GRID) {
                            context.getString(R.string.view_mode_switched_grid)
                        } else {
                            context.getString(R.string.view_mode_switched_list)
                        }
                        snackbarHostState.showSnackbar(msg)
                    }
                },
                isTreeMode = isTreeMode,
                onTreeModeChange = { onTreeModeChange(it) },
                showTreeMode = false,
                folderGridColumns = appSettings.folderGridColumns,
                onFolderGridColumnsChange = { columns ->
                    if (columns != appSettings.folderGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(folderGridColumns = columns) }
                    }
                },
                showFolderGridColumns = false,
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    if (columns != appSettings.videoGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(videoGridColumns = columns) }
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFullName = show) }
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = show) }
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldCount = show) }
                },
                showFieldCountChip = false,
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = show) }
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldSize = show) }
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDate = show) }
                },
                onDismiss = onDismissSortPopup,
                onOpenFullSortDialog = onOpenFolderSortDialog,
            )
        } else {
            LocalSortPopup(
                isFolderScope = false,
                currentSort = sortField,
                onSortChange = { newSort ->
                    viewModel.setRootSortField(newSort)
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    viewModel.updateSettings(commit = true) { it.copy(sortReverse = reversed) }
                },
                currentView = viewMode,
                onViewChange = { onViewModeChange(it) },
                isTreeMode = isTreeMode,
                onTreeModeChange = { onTreeModeChange(it) },
                folderGridColumns = appSettings.folderGridColumns,
                onFolderGridColumnsChange = { columns ->
                    if (columns != appSettings.folderGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(folderGridColumns = columns) }
                    }
                },
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    if (columns != appSettings.videoGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(videoGridColumns = columns) }
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFullName = show) }
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = show) }
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldCount = show) }
                },
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = show) }
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldSize = show) }
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDate = show) }
                },
                onDismiss = onDismissSortPopup,
                onOpenFullSortDialog = onOpenRootSortDialog,
            )
        }
    }

    // Add Videos to Playlist Dialog
    if (showAddToPlaylistDialog && videosForPlaylistAdd.isNotEmpty()) {
        val vids = videosForPlaylistAdd
        val videoPlaylists = customPlaylists.filter { it.isVideoPlaylist }
        AddToPlaylistDialog(
            playlists = videoPlaylists,
            mediaTitle = if (vids.size == 1) vids.first().title
            else stringResource(R.string.playlist_items_count, vids.size),
            onDismiss = {
                viewModel.dismissAddToPlaylist()
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    val result = viewModel.addPendingToPlaylist(pl.id)
                    when {
                        result.added == result.total -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_added_to_playlist_msg, pl.name),
                        )
                        result.added == 0 -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_already_in_playlist_msg, pl.name),
                        )
                        else -> snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.music_added_some_to_playlist_msg,
                                result.added, pl.name, result.total - result.added,
                            ),
                        )
                    }
                }
            },
            onCreateNewPlaylist = {
                viewModel.openCreatePlaylist()
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = {
                viewModel.dismissCreatePlaylist()
            },
            initialIsVideoPlaylist = true,
            onConfirm = { name, isVideo ->
                scope.launch {
                    when (val outcome = viewModel.createPlaylistAndAddPending(name, isVideo)) {
                        is CreatePlaylistOutcome.Created -> snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_playlist_created, outcome.name),
                        )
                        is CreatePlaylistOutcome.Added -> when {
                            outcome.added == outcome.total -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_added_to_playlist_msg, outcome.name),
                            )
                            outcome.added == 0 -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_already_in_playlist_msg, outcome.name),
                            )
                            else -> snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.music_added_some_to_playlist_msg,
                                    outcome.added, outcome.name, outcome.total - outcome.added,
                                ),
                            )
                        }
                        CreatePlaylistOutcome.Failed -> Unit
                    }
                }
            },
        )
    }
}
