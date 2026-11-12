package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun PlayerCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onStopBackgroundPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ToggleRow(stringResource(R.string.player_resume_playback), settings.resumePlayback) {
            onUpdate(settings.copy(resumePlayback = it))
        }
        ToggleRow(stringResource(R.string.player_show_strip_menu), settings.showPlayerStripMenu) {
            onUpdate(settings.copy(showPlayerStripMenu = it))
        }
        Text(
            stringResource(R.string.player_show_strip_menu_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        ToggleRow(stringResource(R.string.player_background_play), settings.backgroundPlay) { enabled ->
            onUpdate(settings.copy(backgroundPlay = enabled))
            if (!enabled) {
                onStopBackgroundPlayback()
            }
        }
        Text(
            stringResource(R.string.player_background_play_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Text(
            stringResource(R.string.player_default_speed_header),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
            ChoiceRow("${speed}x", settings.defaultPlaybackSpeed == speed) {
                onUpdate(settings.copy(defaultPlaybackSpeed = speed))
            }
        }
        ToggleRow(stringResource(R.string.player_auto_rotation), settings.autoScreenRotation) {
            onUpdate(settings.copy(autoScreenRotation = it))
        }
        Text(
            stringResource(R.string.player_auto_rotation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        if (!settings.autoScreenRotation) {
            Text(
                stringResource(R.string.player_locked_orientation_header),
                style = MaterialTheme.typography.labelMedium,
                color = MpxTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            ChoiceRow(stringResource(R.string.player_orientation_landscape), settings.screenOrientation.equals("landscape", ignoreCase = true)) {
                onUpdate(settings.copy(screenOrientation = "landscape"))
            }
            ChoiceRow(stringResource(R.string.player_orientation_portrait), settings.screenOrientation.equals("portrait", ignoreCase = true)) {
                onUpdate(settings.copy(screenOrientation = "portrait"))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
