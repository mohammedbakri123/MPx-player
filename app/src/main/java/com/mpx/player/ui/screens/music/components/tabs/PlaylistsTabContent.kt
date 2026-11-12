package com.mpx.player.ui.screens.music.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.Playlist
import com.mpx.player.ui.screens.local.AudioPlaylistGridCard
import com.mpx.player.ui.screens.local.AudioPlaylistRow
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun PlaylistsTabContent(
    playlists: List<Playlist>,
    viewMode: ViewMode = ViewMode.LIST,
    selectedPlaylistIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onTogglePlaylistSelection: (Playlist) -> Unit = {},
    favouritesCount: Int,
    historyCount: Int,
    onCreatePlaylist: () -> Unit,
    onFavouritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    // #209: the count must match what the list / grid actually emits:
    // 3 header rows + the playlist rows + the empty-state item when the
    // list is empty (the old `playlists.size + 3` missed the empty item).
    // List and grid emit the same number of items, so one count serves
    // both scrollbars.
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item(key = "new_playlist_header", contentType = "new_playlist_action") {
                    NewPlaylistActionRow(onCreatePlaylist)
                }
                item(key = "favourites_row", contentType = "favourites_action") {
                    FavouritesActionRow(favouritesCount, onFavouritesClick)
                }
                item(key = "history_row", contentType = "history_action") {
                    HistoryActionRow(historyCount, onHistoryClick)
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_playlists", contentType = "empty_state") {
                        EmptyPlaylistsState()
                    }
                }
                items(items = playlists, key = { it.id.ifBlank { it.title } }, contentType = { "playlist_row" }) { playlist ->
                    val isSelected = selectedPlaylistIds.contains(playlist.id.ifBlank { playlist.title })
                    AudioPlaylistRow(
                        playlist = playlist,
                        isSelected = isSelected,
                        onLongClick = { onTogglePlaylistSelection(playlist) },
                        onClick = {
                            if (isSelectionMode) onTogglePlaylistSelection(playlist)
                            else onPlaylistClick(playlist)
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
                // #185: the 3 header rows are actions, not playlists —
                // they stay visible in grid mode as full-span items.
                item(key = "new_playlist_header", contentType = "new_playlist_action", span = { GridItemSpan(maxLineSpan) }) {
                    NewPlaylistActionRow(onCreatePlaylist)
                }
                item(key = "favourites_row", contentType = "favourites_action", span = { GridItemSpan(maxLineSpan) }) {
                    FavouritesActionRow(favouritesCount, onFavouritesClick)
                }
                item(key = "history_row", contentType = "history_action", span = { GridItemSpan(maxLineSpan) }) {
                    HistoryActionRow(historyCount, onHistoryClick)
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_playlists", contentType = "empty_state", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyPlaylistsState()
                    }
                }
                items(
                    items = playlists,
                    key = { playlist -> playlist.id.ifBlank { playlist.title } },
                    contentType = { "playlist_grid_card" },
                ) { playlist ->
                    val isSelected = selectedPlaylistIds.contains(playlist.id.ifBlank { playlist.title })
                    AudioPlaylistGridCard(
                        playlist = playlist,
                        isSelected = isSelected,
                        onLongClick = { onTogglePlaylistSelection(playlist) },
                        onClick = {
                            if (isSelectionMode) onTogglePlaylistSelection(playlist)
                            else onPlaylistClick(playlist)
                        },
                    )
                }
            }
        }
    }
}

// #185: the Playlists tab's 3 header rows are actions, not playlists —
// extracted so the list and grid branches share them (the grid shows
// them as full-span items). Each row keeps the divider it had inline.
@Composable
private fun NewPlaylistActionRow(onCreatePlaylist: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCreatePlaylist() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = stringResource(R.string.music_new_playlist_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MpxTheme.colors.primary))
            Text(text = stringResource(R.string.music_new_playlist_subtitle), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun FavouritesActionRow(favouritesCount: Int, onFavouritesClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onFavouritesClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp).clip(MpxShapes.small).background(MpxTheme.colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.music_favourites_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTheme.colors.textPrimary)
            Text(text = stringResource(R.string.music_songs_count, favouritesCount), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun HistoryActionRow(historyCount: Int, onHistoryClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onHistoryClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp).clip(MpxShapes.small).background(MpxTheme.colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.music_recently_played_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTheme.colors.textPrimary)
            Text(text = stringResource(R.string.music_songs_count, historyCount), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun EmptyPlaylistsState() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.music_empty_playlists_title), style = MaterialTheme.typography.titleMedium, color = MpxTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = stringResource(R.string.music_empty_playlists_desc), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
}
