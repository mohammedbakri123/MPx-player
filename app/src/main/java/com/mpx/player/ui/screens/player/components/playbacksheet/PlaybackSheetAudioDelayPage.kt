package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.mpx.player.domain.settings.AppSettings

@Composable
internal fun PlaybackSheetAudioDelayPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        AudioDelayStepper(
            currentSettings = currentSettings,
            onSettingsChanged = onSettingsChanged,
        )
    }
}
