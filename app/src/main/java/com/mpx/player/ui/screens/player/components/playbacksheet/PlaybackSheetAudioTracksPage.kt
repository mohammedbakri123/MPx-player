package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
internal fun PlaybackSheetAudioTracksPage(
    state: PlaybackState,
    playbackController: PlaybackController,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        if (state.audioTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.player_audio_no_tracks),
                color = MpxTheme.colors.textSecondary,
            )
        } else {
            state.audioTracks.forEach { track ->
                PlaybackSheetRadioRow(
                    title = track.label,
                    subtitle = stringResource(R.string.player_track_language, track.language),
                    selected = track.isSelected,
                    onClick = {
                        playbackController.selectAudioTrack(track.groupIndex, track.trackIndex)
                        onDismiss()
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        AudioDelayStepper(
            currentSettings = currentSettings,
            onSettingsChanged = onSettingsChanged,
        )
    }
}
