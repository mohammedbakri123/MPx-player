package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

@Composable
internal fun PlaybackSheetSubtitleTracksPage(
    state: PlaybackState,
    playbackController: PlaybackController,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    onDismiss: () -> Unit,
    onOpenBrowse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                // MX-style: a local video opens the in-app browser at
                // its own folder; anything without a folder (a network
                // stream) keeps the system picker.
                if (state.currentItem?.filePath?.isNotBlank() == true) {
                    onOpenBrowse()
                } else {
                    onDismiss()
                    subtitleLauncher.launch(arrayOf("*/*"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MpxTheme.colors.primary),
        ) {
            Icon(
                imageVector = Icons.Filled.FileUpload,
                contentDescription = null,
                tint = MpxTheme.colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_subtitle_open_file),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_subtitle_available_tracks),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        PlaybackSheetRadioRow(
            title = stringResource(R.string.player_subtitle_off),
            subtitle = null,
            selected = state.selectedSubtitleTrackIndex == -1,
            onClick = {
                playbackController.selectSubtitleTrack(null, null)
                onDismiss()
            },
        )
        if (state.subtitleTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.player_subtitle_no_tracks),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 6.dp),
            )
        } else {
            state.subtitleTracks.forEach { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playbackController.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = track.isSelected,
                        onClick = {
                            playbackController.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.label,
                                color = MpxTheme.colors.textPrimary,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (track.isExternal) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = MpxTheme.colors.primary.copy(alpha = 0.2f),
                                    shape = MpxShapes.extraSmall,
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_subtitle_external),
                                        color = MpxTheme.colors.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.player_track_language, track.language),
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
