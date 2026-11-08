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
 * Issue #382: subtitle conversion choice — the `--convert-subs` format
 * (Seal's `CONVERT_SUBTITLE`). Same save-on-tap pattern as the quality
 * dialog (#338): every tap persists at once, so there is nothing to
 * lose on dismiss — Done just closes the dialog. "None" stores "none"
 * and passes no flag. The downloader reads the setting at request time,
 * so a change applies to the next download.
 */
@Composable
fun SubtitleConvertDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val noneLabel = stringResource(R.string.download_convert_subtitles_none)
    val options = listOf(
        "none" to noneLabel,
        "srt" to "SRT",
        "vtt" to "VTT",
        "ass" to "ASS",
    )

    MpxDialog(
        title = stringResource(R.string.download_convert_subtitles),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(settings.copy(downloadConvertSubtitles = value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.downloadConvertSubtitles == value,
                        onClick = { update(settings.copy(downloadConvertSubtitles = value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
