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
 * Issue #390: audio format choice — the `--audio-format` value for
 * audio-only downloads (Seal's `AUDIO_CONVERT_FORMAT`). Same
 * save-on-tap pattern as the subtitle-convert dialog (#382): every
 * tap persists at once, so there is nothing to lose on dismiss —
 * Done just closes the dialog. Seal itself only offers mp3 and m4a;
 * yt-dlp's `--audio-format` accepts more, so MPx offers the four
 * common ones. The downloader reads the setting at request time, so
 * a change applies to the next download.
 */
@Composable
fun AudioFormatDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val options = listOf("mp3", "m4a", "opus", "flac")

    MpxDialog(
        title = stringResource(R.string.download_audio_format_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(settings.copy(downloadAudioFormat = value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.downloadAudioFormat == value,
                        onClick = { update(settings.copy(downloadAudioFormat = value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(audioFormatLabel(value), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Display label for an audio-format setting value ("mp3" -> "MP3"). */
internal fun audioFormatLabel(value: String): String = when (value) {
    "m4a" -> "M4A"
    "opus" -> "Opus"
    "flac" -> "FLAC"
    else -> "MP3"
}
