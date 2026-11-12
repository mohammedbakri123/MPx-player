package com.mpx.player.ui.screens.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.ui.screens.local.MultiSelectAction
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun MusicSelectionBottomBar(
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // #185: multi-select action bar — exactly the track-menu
    // actions: Play, Add to playlist, Favourite. No delete.
    // On Albums / Artists / Playlists / Folders the same three actions
    // run over the selected items' tracks.
    Column(modifier = modifier) {
        HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MpxTheme.colors.black)
                .padding(vertical = 4.dp),
        ) {
            MultiSelectAction(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(R.string.video_item_play),
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            MultiSelectAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.music_menu_add_to_playlist),
                onClick = onAddToPlaylist,
                modifier = Modifier.weight(1f),
            )
            MultiSelectAction(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.music_menu_toggle_favourite),
                onClick = onToggleFavourite,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
