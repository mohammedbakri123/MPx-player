package com.mpx.player.ui.screens.local.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.screens.local.MultiSelectAction
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun LocalSelectionBar(
    onPlaySelection: () -> Unit,
    onPlaySelectionFolder: () -> Unit,
    onAddSelectionToPlaylist: () -> Unit,
    onToggleSelectionFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                onClick = onPlaySelection,
                modifier = Modifier.weight(1f),
            )
            MultiSelectAction(
                icon = Icons.Filled.Folder,
                label = stringResource(R.string.multiselect_play_folder),
                onClick = onPlaySelectionFolder,
                modifier = Modifier.weight(1f),
            )
            MultiSelectAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.music_menu_add_to_playlist),
                onClick = onAddSelectionToPlaylist,
                modifier = Modifier.weight(1f),
            )
            MultiSelectAction(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.music_menu_toggle_favourite),
                onClick = onToggleSelectionFavourite,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun LocalStatusStrip(
    isFileView: Boolean,
    filteredVideosCount: Int,
    filteredFolders: List<MediaFolder>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MpxTheme.colors.black)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isFileView) {
                    stringResource(
                        R.string.folder_item_videos_count,
                        filteredVideosCount,
                    )
                } else {
                    stringResource(
                        R.string.local_status_strip,
                        filteredFolders.size,
                        filteredFolders.sumOf { it.videoCount },
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }
}
