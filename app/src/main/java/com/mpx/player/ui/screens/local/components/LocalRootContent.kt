package com.mpx.player.ui.screens.local.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.screens.local.FolderItemGridCard
import com.mpx.player.ui.screens.local.FolderItemRow
import com.mpx.player.ui.screens.local.LocalFolderTree
import com.mpx.player.ui.screens.local.LocalPathBreadcrumb
import com.mpx.player.ui.screens.local.LocalSectionHeader
import com.mpx.player.ui.screens.local.PathSegment
import com.mpx.player.ui.screens.local.VideoItemGridCard
import com.mpx.player.ui.screens.local.VideoItemRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun LocalRootContent(
    hasPermission: Boolean,
    isLoading: Boolean,
    rootEmpty: Boolean,
    appSettings: AppSettings,
    viewMode: ViewMode,
    isTreeMode: Boolean,
    sortField: SortField,
    secondarySortField: SortField,
    filteredFolders: List<MediaFolder>,
    filteredVideos: List<VideoFile>,
    expandedTreeFolders: Set<String>,
    selectedFolderIds: Set<String>,
    selectedVideoIds: Set<String>,
    isSelectionMode: Boolean,
    favouriteUris: Set<String>,
    onRequestPermission: () -> Unit,
    onShowAllFoldersToggle: () -> Unit,
    onRefreshFolders: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onToggleTreeFolder: (MediaFolder) -> Unit,
    onToggleFolderSelection: (MediaFolder) -> Unit,
    onToggleVideoSelection: (VideoFile) -> Unit,
    onToggleVideoFavourite: (VideoFile) -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onPlayVideoWithFolder: (VideoFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!hasPermission) {
            EmptyStateView(
                title = stringResource(R.string.empty_permission_title),
                message = stringResource(R.string.empty_permission_desc),
                icon = Icons.Filled.Folder,
                actionLabel = stringResource(R.string.empty_permission_action),
                onActionClick = onRequestPermission,
            )
        } else if (isLoading && rootEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                MpxLoadingIndicator()
            }
        } else if (rootEmpty) {
            if (!appSettings.showAllFolders) {
                EmptyStateView(
                    title = stringResource(R.string.empty_videos_title),
                    message = stringResource(R.string.empty_videos_desc),
                    icon = Icons.Filled.Folder,
                    actionLabel = stringResource(R.string.list_show_all_folders),
                    onActionClick = onShowAllFoldersToggle,
                )
            } else {
                EmptyStateView(
                    title = stringResource(R.string.empty_folders_title),
                    message = stringResource(R.string.empty_folders_desc),
                    icon = Icons.Filled.Folder,
                    actionLabel = stringResource(R.string.empty_videos_action),
                    onActionClick = onRefreshFolders,
                )
            }
        } else if (appSettings.fileView) {
            val sortLabelText = if (secondarySortField != SortField.NONE && secondarySortField != sortField) {
                "${stringResource(sortField.labelRes)} / ${stringResource(secondarySortField.labelRes)}"
            } else {
                stringResource(sortField.labelRes)
            }
            LocalSectionHeader(
                title = stringResource(R.string.section_files_header),
                count = filteredVideos.size,
                sortLabel = stringResource(R.string.local_sort_label, sortLabelText),
                onSortClick = onOpenSortDialog,
            )
            val listState = rememberLazyListState()
            val gridState = rememberLazyGridState()
            if (viewMode == ViewMode.LIST) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(
                            items = filteredVideos,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "video_item_row" },
                        ) { video ->
                            VideoItemRow(
                                video = video,
                                showThumbnails = appSettings.showThumbnails,
                                isFavourite = video.path in favouriteUris,
                                onToggleFavourite = { onToggleVideoFavourite(video) },
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                showDuration = appSettings.showFieldDuration,
                                showLocation = appSettings.showFieldLocation,
                                isSelected = selectedVideoIds.contains(
                                    video.id.ifBlank { video.path }
                                ),
                                onLongClick = { onToggleVideoSelection(video) },
                                showFullName = appSettings.showFullName,
                                onClick = {
                                    if (isSelectionMode) onToggleVideoSelection(video)
                                    else onPlayVideoWithFolder(video)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 128.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(appSettings.videoGridColumns.coerceIn(1, 4)),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filteredVideos,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "video_item_grid" },
                        ) { video ->
                            val isFav = video.path in favouriteUris
                            VideoItemGridCard(
                                video = video,
                                showThumbnails = appSettings.showThumbnails,
                                isFavourite = isFav,
                                onToggleFavourite = { onToggleVideoFavourite(video) },
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                showDuration = appSettings.showFieldDuration,
                                showLocation = appSettings.showFieldLocation,
                                isSelected = selectedVideoIds.contains(
                                    video.id.ifBlank { video.path }
                                ),
                                onLongClick = { onToggleVideoSelection(video) },
                                showFullName = appSettings.showFullName,
                                onClick = {
                                    if (isSelectionMode) onToggleVideoSelection(video)
                                    else onPlayVideoWithFolder(video)
                                },
                            )
                        }
                    }
                }
            }
        } else {
            LocalPathBreadcrumb(
                segments = listOf(
                    PathSegment(label = stringResource(R.string.nav_local)),
                ),
                badgeText = if (appSettings.showAllFolders) stringResource(R.string.local_badge_show_all) else null,
            )

            val treeListState = rememberLazyListState()
            val listState = rememberLazyListState()
            val gridState = rememberLazyGridState()
            if (viewMode == ViewMode.LIST && isTreeMode) {
                LocalFolderTree(
                    folders = filteredFolders,
                    listState = treeListState,
                    expandedKeys = expandedTreeFolders,
                    onToggleExpand = onToggleTreeFolder,
                    showThumbnails = appSettings.showThumbnails,
                    showSize = appSettings.showFieldSize,
                    showDate = appSettings.showFieldDate,
                    showCount = appSettings.showFieldCount,
                    showFullName = appSettings.showFullName,
                    isSelected = { folder ->
                        selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                    },
                    onLongClick = onToggleFolderSelection,
                    onClick = { folder ->
                        if (isSelectionMode) onToggleFolderSelection(folder)
                        else onOpenFolder(folder)
                    },
                )
            } else if (viewMode == ViewMode.LIST) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(
                            items = filteredFolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "folder_item_row" },
                        ) { folder ->
                            FolderItemRow(
                                folder = folder,
                                showThumbnails = appSettings.showThumbnails,
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                isSelected = selectedFolderIds.contains(
                                    folder.id.ifBlank { folder.path }
                                ),
                                onLongClick = { onToggleFolderSelection(folder) },
                                showCount = appSettings.showFieldCount,
                                showFullName = appSettings.showFullName,
                                onClick = {
                                    if (isSelectionMode) onToggleFolderSelection(folder)
                                    else onOpenFolder(folder)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(appSettings.folderGridColumns.coerceIn(1, 4)),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filteredFolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "folder_item_grid" },
                        ) { folder ->
                            FolderItemGridCard(
                                folder = folder,
                                showThumbnails = appSettings.showThumbnails,
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                isSelected = selectedFolderIds.contains(
                                    folder.id.ifBlank { folder.path }
                                ),
                                onLongClick = { onToggleFolderSelection(folder) },
                                showCount = appSettings.showFieldCount,
                                showFullName = appSettings.showFullName,
                                onClick = {
                                    if (isSelectionMode) onToggleFolderSelection(folder)
                                    else onOpenFolder(folder)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
