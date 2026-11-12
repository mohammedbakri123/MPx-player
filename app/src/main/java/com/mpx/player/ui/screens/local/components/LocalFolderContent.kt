package com.mpx.player.ui.screens.local.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.screens.local.FolderItemRow
import com.mpx.player.ui.screens.local.LocalPathBreadcrumb
import com.mpx.player.ui.screens.local.PathSegment
import com.mpx.player.ui.screens.local.VideoItemGridCard
import com.mpx.player.ui.screens.local.VideoItemRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun LocalFolderContent(
    folder: MediaFolder,
    currentSubfolders: List<MediaFolder>,
    filteredFolderVideos: List<VideoFile>,
    isFolderLoading: Boolean,
    folderSearchQuery: String,
    viewMode: ViewMode,
    appSettings: AppSettings,
    selectedVideoIds: Set<String>,
    isSelectionMode: Boolean,
    favouriteUris: Set<String>,
    onGoToRoot: () -> Unit,
    onClearFolderSearch: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onToggleVideoFavourite: (VideoFile) -> Unit,
    onToggleVideoSelection: (VideoFile) -> Unit,
    onPlayVideo: (List<VideoFile>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LocalPathBreadcrumb(
            segments = listOf(
                PathSegment(
                    label = stringResource(R.string.nav_local),
                    onClick = onGoToRoot,
                ),
                PathSegment(label = folder.name),
            ),
        )
        if (isFolderLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                MpxLoadingIndicator()
            }
        } else if (filteredFolderVideos.isEmpty() && folderSearchQuery.isNotBlank()) {
            EmptyStateView(
                title = stringResource(R.string.folder_view_search_empty_title),
                message = stringResource(R.string.folder_view_search_empty_desc, folderSearchQuery),
                icon = Icons.Filled.PlayArrow,
                actionLabel = stringResource(R.string.empty_search_action),
                onActionClick = onClearFolderSearch,
            )
        } else if (filteredFolderVideos.isEmpty() && currentSubfolders.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.folder_view_empty_title),
                message = stringResource(R.string.folder_view_empty_desc),
                icon = Icons.Filled.PlayArrow,
                actionLabel = stringResource(R.string.action_back_to_folders),
                onActionClick = onGoToRoot,
            )
        } else {
            val folderKey = folder.id.ifBlank { folder.path }
            val videoListState = remember(folderKey) { LazyListState() }
            val videoGridState = remember(folderKey) { LazyGridState() }
            if (viewMode == ViewMode.LIST) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = videoListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(
                            items = currentSubfolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "folder_subfolder_row" },
                        ) { subfolder ->
                            FolderItemRow(
                                folder = subfolder,
                                showThumbnails = appSettings.showThumbnails,
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                isSelected = false,
                                onLongClick = {},
                                showCount = appSettings.showFieldCount,
                                showFullName = appSettings.showFullName,
                                onClick = { onOpenFolder(subfolder) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                        itemsIndexed(
                            items = filteredFolderVideos,
                            key = { _, video -> video.id.ifBlank { video.path } },
                            contentType = { _, _ -> "video_item_row" },
                        ) { index, video ->
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
                                    if (isSelectionMode) {
                                        onToggleVideoSelection(video)
                                    } else {
                                        onPlayVideo(filteredFolderVideos, index)
                                    }
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 130.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = videoGridState,
                        columns = GridCells.Fixed(appSettings.videoGridColumns.coerceIn(1, 4)),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = currentSubfolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "folder_subfolder_row" },
                            span = { GridItemSpan(maxLineSpan) },
                        ) { subfolder ->
                            FolderItemRow(
                                folder = subfolder,
                                showThumbnails = appSettings.showThumbnails,
                                showSize = appSettings.showFieldSize,
                                showDate = appSettings.showFieldDate,
                                isSelected = false,
                                onLongClick = {},
                                showCount = appSettings.showFieldCount,
                                showFullName = appSettings.showFullName,
                                onClick = { onOpenFolder(subfolder) },
                            )
                        }
                        itemsIndexed(
                            items = filteredFolderVideos,
                            key = { _, video -> video.id.ifBlank { video.path } },
                            contentType = { _, _ -> "video_item_grid" },
                        ) { index, video ->
                            VideoItemGridCard(
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
                                    if (isSelectionMode) {
                                        onToggleVideoSelection(video)
                                    } else {
                                        onPlayVideo(filteredFolderVideos, index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
