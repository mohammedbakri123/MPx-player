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
 * Issue #408: format selection mode — Auto downloads immediately
 * with the saved quality/codec/sorter preset (Seal's
 * FORMAT_SELECTION = false), Custom opens the per-video format
 * list for every video (Seal's FORMAT_SELECTION = true, today's
 * behavior). Same save-on-tap pattern as the audio-format dialog
 * (#390): every tap persists at once, so there is nothing to lose
 * on dismiss — Done just closes the dialog. Named
 * FormatSelectionModeDialog because FormatSelectionDialog is
 * already the per-video format sheet (#383). The downloader reads
 * the setting at start time, so a change applies to the next
 * download.
 */
@Composable
fun FormatSelectionModeDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val options = listOf("auto", "custom")

    MpxDialog(
        title = stringResource(R.string.download_format_selection_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(settings.copy(downloadFormatSelection = value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.downloadFormatSelection == value,
                        onClick = { update(settings.copy(downloadFormatSelection = value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatSelectionLabel(value), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Display label for a format-selection setting value ("auto" -> "Auto"). */
internal fun formatSelectionLabel(value: String): String = when (value) {
    "auto" -> "Auto"
    else -> "Custom"
}
