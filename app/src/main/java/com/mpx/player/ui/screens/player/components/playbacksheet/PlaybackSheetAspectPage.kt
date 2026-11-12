package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState

@Composable
internal fun PlaybackSheetAspectPage(
    state: PlaybackState,
    playbackController: PlaybackController,
    onDismiss: () -> Unit,
) {
    Column {
        AspectRatioMode.values().forEach { mode ->
            PlaybackSheetRadioRow(
                title = stringResource(mode.labelRes),
                subtitle = null,
                selected = state.aspectRatio == mode,
                onClick = {
                    playbackController.setAspectRatio(mode)
                    onDismiss()
                },
            )
        }
    }
}
