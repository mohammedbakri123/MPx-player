package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun PlaybackSheetSubtitleSettingsPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.player_subtitle_text_size),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Small" to R.string.player_subtitle_size_small,
                "Normal" to R.string.player_subtitle_size_normal,
                "Large" to R.string.player_subtitle_size_large,
            ).forEach { (size, sizeRes) ->
                val isSelected = currentSettings.subtitleSize.equals(size, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSettingsChanged(currentSettings.copy(subtitleSize = size)) },
                    label = { Text(stringResource(sizeRes), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val speed = currentSettings.subtitleSpeed
        StepperRow(
            label = stringResource(R.string.subtitle_speed_header),
            valueText = String.format(Locale.US, "%.1f×", speed),
            minusContentDescription = stringResource(R.string.subtitle_speed_decrease),
            plusContentDescription = stringResource(R.string.subtitle_speed_increase),
            onMinus = {
                val stepped = ((speed * 10).roundToInt() - 1) / 10f
                onSettingsChanged(currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onPlus = {
                val stepped = ((speed * 10).roundToInt() + 1) / 10f
                onSettingsChanged(currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onReset = if (speed != 1.0f) {
                { onSettingsChanged(currentSettings.copy(subtitleSpeed = 1.0f)) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.player_subtitle_style_color),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "White" to R.string.player_subtitle_style_white,
                "Yellow" to R.string.player_subtitle_style_yellow,
                "Cyan" to R.string.player_subtitle_style_cyan,
                "Default" to R.string.player_subtitle_style_default,
            ).forEach { (styleKey, styleRes) ->
                val isSelected = currentSettings.subtitleStyle.equals(styleKey, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSettingsChanged(currentSettings.copy(subtitleStyle = styleKey)) },
                    label = { Text(stringResource(styleRes), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                )
            }
        }
        // Same unconditional note the old dialog carried (see Subtitle Delay).
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.subtitle_timing_mpv_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
