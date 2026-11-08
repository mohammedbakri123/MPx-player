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
 * Issue #395: the preferred audio quality for the custom audio preset
 * (Seal's AUDIO_QUALITY) — the `-S abr~...` half of the sorter.
 * Same save-on-tap pattern as the audio-format dialog (#390). "Best"
 * leaves the bitrate unconstrained.
 */
@Composable
fun AudioPresetQualityDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val options = listOf("best", "192", "128", "64")

    MpxDialog(
        title = stringResource(R.string.download_audio_preset_quality_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(settings.copy(downloadAudioPresetQuality = value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.downloadAudioPresetQuality == value,
                        onClick = { update(settings.copy(downloadAudioPresetQuality = value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(audioPresetQualityLabel(value), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Display label for an audio-preset quality value ("192" -> "192 kbps"). */
internal fun audioPresetQualityLabel(value: String): String = when (value) {
    "192" -> "192 kbps"
    "128" -> "128 kbps"
    "64" -> "64 kbps"
    else -> "Best"
}
