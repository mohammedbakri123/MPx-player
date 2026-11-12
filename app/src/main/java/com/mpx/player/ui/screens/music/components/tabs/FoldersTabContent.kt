package com.mpx.player.ui.screens.music.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.ui.screens.local.AudioFolderGridCard
import com.mpx.player.ui.screens.local.AudioFolderRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun FoldersTabContent(
    folders: List<AudioFolder>,
    viewMode: ViewMode = ViewMode.LIST,
    selectedFolderIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleFolderSelection: (AudioFolder) -> Unit = {},
    onFolderClick: (AudioFolder) -> Unit,
) {
    // #185: the view toggle was already honoured (the old tab had both
    // branches); both now use the shared AudioFolderRow / GridCard.
    // Scrollbar counts: each branch emits exactly one lazy item per
    // folder (the list divider lives inside the item lambda, not as its
    // own item), so itemCount = folders.size in both branches. The tab
    // branch never composes this content with an empty list — the empty
    // and no-search-matches states are EmptyStateViews outside it — so
    // there is no empty-state item to count, unlike Playlists (#209).
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(
                    items = folders,
                    key = { folder -> folder.id.ifBlank { folder.path } },
                    contentType = { "audio_folder_row" },
                ) { folder ->
                    val isSelected = selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                    AudioFolderRow(
                        folder = folder,
                        isSelected = isSelected,
                        onLongClick = { onToggleFolderSelection(folder) },
                        onClick = {
                            if (isSelectionMode) onToggleFolderSelection(folder)
                            else onFolderClick(folder)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
        }
    } else {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = folders,
                    key = { folder -> folder.id.ifBlank { folder.path } },
                    contentType = { "audio_folder_card" },
                ) { folder ->
                    val isSelected = selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                    AudioFolderGridCard(
                        folder = folder,
                        isSelected = isSelected,
                        onLongClick = { onToggleFolderSelection(folder) },
                        onClick = {
                            if (isSelectionMode) onToggleFolderSelection(folder)
                            else onFolderClick(folder)
                        },
                    )
                }
            }
        }
    }
}
