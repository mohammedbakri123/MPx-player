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
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.ui.screens.local.AudioAlbumGridCard
import com.mpx.player.ui.screens.local.AudioAlbumRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun AlbumsTabContent(
    albums: List<AudioAlbum>,
    viewMode: ViewMode = ViewMode.GRID,
    showThumbnails: Boolean = true,
    selectedAlbumIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleAlbumSelection: (AudioAlbum) -> Unit = {},
    onAlbumClick: (AudioAlbum) -> Unit,
) {
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(
                    items = albums,
                    key = { album -> album.id.ifBlank { album.title } },
                    contentType = { "album_row" },
                ) { album ->
                    val isSelected = selectedAlbumIds.contains(album.id.ifBlank { album.title })
                    AudioAlbumRow(
                        album = album,
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleAlbumSelection(album) },
                        onClick = {
                            if (isSelectionMode) onToggleAlbumSelection(album)
                            else onAlbumClick(album)
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
                    items = albums,
                    key = { album -> album.id.ifBlank { album.title } },
                    contentType = { "album_card" },
                ) { album ->
                    val isSelected = selectedAlbumIds.contains(album.id.ifBlank { album.title })
                    AudioAlbumGridCard(
                        album = album,
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleAlbumSelection(album) },
                        onClick = {
                            if (isSelectionMode) onToggleAlbumSelection(album)
                            else onAlbumClick(album)
                        },
                    )
                }
            }
        }
    }
}
