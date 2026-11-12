package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.PipUtils

@Composable
fun PipCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onOpenOverlaySettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        if (onOpenOverlaySettings != null) {
            TextButton(onClick = onOpenOverlaySettings) {
                Text(stringResource(R.string.overlay_settings_title), color = MpxTheme.colors.primary)
            }
        }
        val isSupported = PipUtils.isPipSupported(context)
        if (!isSupported) {
            Text(
                text = stringResource(R.string.pip_not_supported),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.destructive,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        ToggleRow(stringResource(R.string.pip_enable_toggle), settings.popupPlayEnabled, enabled = isSupported) {
            onUpdate(settings.copy(popupPlayEnabled = it))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pip_window_controls_header),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
        )
        val pipControlsEnabled = isSupported && settings.popupPlayEnabled
        ChoiceRow(
            stringResource(R.string.pip_controls_prev_next),
            settings.popupPlayControls == "PrevNext",
            enabled = pipControlsEnabled,
        ) {
            onUpdate(settings.copy(popupPlayControls = "PrevNext"))
        }
        ChoiceRow(
            stringResource(R.string.pip_controls_seek10),
            settings.popupPlayControls == "Seek10",
            enabled = pipControlsEnabled,
        ) {
            onUpdate(settings.copy(popupPlayControls = "Seek10"))
        }
        ChoiceRow(
            stringResource(R.string.pip_controls_play_pause_only),
            settings.popupPlayControls == "PlayPauseOnly",
            enabled = pipControlsEnabled,
        ) {
            onUpdate(settings.copy(popupPlayControls = "PlayPauseOnly"))
        }
    }
}
