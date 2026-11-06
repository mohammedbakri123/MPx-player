package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Download quality settings (issue #338): resolution cap, codec
 * preference, and an optional custom `-S` sorter string, wired to
 * #336's FormatSorter via AppSettings. Same save-on-tap pattern as
 * OverlaySettingsScreen: every choice persists at once, so there is
 * nothing to lose on dismiss — Done just closes the dialog. The
 * downloader reads the settings at request time, so a change here
 * applies to the next download.
 *
 * Defaults keep #336's behavior exactly: no resolution cap, codec
 * Auto (vp9.2, or av01 when the device hardware-decodes AV1).
 */
@Composable
fun DownloadQualityDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val noLimitLabel = stringResource(R.string.download_quality_resolution_none)
    val autoLabel = stringResource(R.string.download_quality_codec_auto)
    val resolutionOptions = listOf(
        0 to noLimitLabel,
        1 to "2160p",
        2 to "1440p",
        3 to "1080p",
        4 to "720p",
        5 to "480p",
        6 to "360p",
    )
    val codecOptions = listOf(
        "Auto" to autoLabel,
        "VP9" to "VP9",
        "AV1" to "AV1",
        "H264" to "H.264",
    )

    MpxDialog(
        title = stringResource(R.string.download_quality_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            QualitySectionLabel(stringResource(R.string.download_quality_resolution))
            resolutionOptions.forEach { (code, label) ->
                QualityOptionRow(
                    label = label,
                    selected = settings.downloadResolution == code,
                    onClick = { update(settings.copy(downloadResolution = code)) },
                )
            }
            QualitySectionLabel(stringResource(R.string.download_quality_codec))
            codecOptions.forEach { (codec, label) ->
                QualityOptionRow(
                    label = label,
                    selected = settings.downloadCodec == codec,
                    onClick = { update(settings.copy(downloadCodec = codec)) },
                )
            }
            QualitySectionLabel(stringResource(R.string.download_quality_custom_sorter))
            OutlinedTextField(
                value = settings.downloadCustomSorter,
                onValueChange = { update(settings.copy(downloadCustomSorter = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "res,ext",
                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
                shape = MpxShapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                    focusedBorderColor = MpxTheme.colors.primary,
                    unfocusedBorderColor = MpxTheme.colors.border,
                    focusedTextColor = MpxTheme.colors.textPrimary,
                    unfocusedTextColor = MpxTheme.colors.textPrimary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_quality_custom_sorter_hint),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

/** Section header inside the dialog: accent colour, like PreferenceSectionHeader. */
@Composable
private fun QualitySectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MpxTheme.colors.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** One radio row (OverlaySettingsScreen pattern): whole row tappable. */
@Composable
private fun QualityOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MpxTheme.colors.textPrimary,
            fontSize = 15.sp,
        )
    }
}

/** Display label for a stored resolution code (shared with DownloadScreen's summary row). */
@Composable
internal fun downloadResolutionLabel(code: Int): String {
    return when (code) {
        1 -> "2160p"
        2 -> "1440p"
        3 -> "1080p"
        4 -> "720p"
        5 -> "480p"
        6 -> "360p"
        else -> stringResource(R.string.download_quality_resolution_none)
    }
}

/** Display label for a stored codec code (shared with DownloadScreen's summary row). */
@Composable
internal fun downloadCodecLabel(codec: String): String {
    return when (codec) {
        "VP9" -> "VP9"
        "AV1" -> "AV1"
        "H264" -> "H.264"
        else -> stringResource(R.string.download_quality_codec_auto)
    }
}
