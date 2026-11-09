package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #405: yt-dlp update channel — Stable or Nightly (Seal's
 * update channel). Same save-on-tap pattern as the audio-format
 * dialog (#390): every tap persists at once, so there is nothing
 * to lose on dismiss — Done just closes the dialog. The setting
 * already existed from #333 (`ytdlpUpdateChannel`, read by
 * `updateYtDlp()`); it just had no UI. A change applies to the
 * next update check.
 */
@Composable
fun YtDlpUpdateChannelDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val options = listOf("STABLE", "NIGHTLY")

    MpxDialog(
        title = stringResource(R.string.download_update_channel_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(settings.copy(ytdlpUpdateChannel = value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.ytdlpUpdateChannel == value,
                        onClick = { update(settings.copy(ytdlpUpdateChannel = value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(ytdlpUpdateChannelLabel(value), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Display label for an update-channel setting value ("STABLE" -> "Stable"). */
internal fun ytdlpUpdateChannelLabel(value: String): String = when (value) {
    "NIGHTLY" -> "Nightly"
    else -> "Stable"
}
