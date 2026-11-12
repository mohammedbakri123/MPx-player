package com.mpx.player.ui.screens.music.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.ui.screens.local.AudioTrackGridCard
import com.mpx.player.ui.screens.local.AudioTrackRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun TracksTabContent(
    tracks: List<AudioTrack>,
    viewMode: ViewMode = ViewMode.LIST,
    showThumbnails: Boolean = true,
    selectedTrackIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    // Issue #321: the favourite heart state + toggle for track rows
    // and grid cards (audio had no heart in either layout).
    favourites: List<FavouriteItem> = emptyList(),
    onToggleTrackFavourite: (AudioTrack) -> Unit = {},
    onToggleTrackSelection: (AudioTrack) -> Unit = {},
    onTrackClick: (Int) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
) {
    // Issue #360: the favourite URIs as a Set, computed once per
    // favourites change — the rows below used to run
    // favourites.any { it.mediaUri == track.path } per row, a full
    // list scan on every row composition while scrolling.
    val favouriteUris = remember(favourites) { favourites.map { it.mediaUri }.toSet() }
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> track.id.ifBlank { "${track.path}_$index" } },
                    contentType = { _, _ -> "track_row" },
                ) { index, track ->
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackRow(
                        track = track,
                        // Issue #321: the heart, wired to the same
                        // favourites Flow + toggle as the video rows.
                        isFavourite = track.path in favouriteUris,
                        onToggleFavourite = { onToggleTrackFavourite(track) },
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleTrackSelection(track) },
                        onOptionsClick = { onTrackOptionsClick(track) },
                        onClick = {
                            if (isSelectionMode) onToggleTrackSelection(track)
                            else onTrackClick(index)
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
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> track.id.ifBlank { "${track.path}_$index" } },
                    contentType = { _, _ -> "track_grid_card" },
                ) { index, track ->
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackGridCard(
                        track = track,
                        // Issue #321: the heart — the audio grid card
                        // had none either.
                        isFavourite = track.path in favouriteUris,
                        onToggleFavourite = { onToggleTrackFavourite(track) },
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleTrackSelection(track) },
                        onOptionsClick = { onTrackOptionsClick(track) },
                        onClick = {
                            if (isSelectionMode) onToggleTrackSelection(track)
                            else onTrackClick(index)
                        },
                    )
                }
            }
        }
    }
}
