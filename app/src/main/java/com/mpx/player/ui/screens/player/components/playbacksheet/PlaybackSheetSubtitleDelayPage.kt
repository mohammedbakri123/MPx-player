package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.theme.MpxTheme

@Composable
internal fun PlaybackSheetSubtitleDelayPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val delayMs = currentSettings.subtitleDelayMs
    Column {
        StepperRow(
            label = stringResource(R.string.subtitle_sync_header),
            valueText = formatDelaySeconds(delayMs),
            minusContentDescription = stringResource(R.string.subtitle_sync_decrease),
            plusContentDescription = stringResource(R.string.subtitle_sync_increase),
            onMinus = {
                onSettingsChanged(
                    currentSettings.copy(subtitleDelayMs = (delayMs - 100).coerceIn(-10000, 10000)),
                )
            },
            onPlus = {
                onSettingsChanged(
                    currentSettings.copy(subtitleDelayMs = (delayMs + 100).coerceIn(-10000, 10000)),
                )
            },
            onReset = if (delayMs != 0) {
                { onSettingsChanged(currentSettings.copy(subtitleDelayMs = 0)) }
            } else {
                null
            },
        )
        // Same unconditional note the old dialog carried: sync/speed are
        // mpv-only, so the disclosure stays even when mpv is active.
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.subtitle_timing_mpv_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
