package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Uppercase labelSmall section title in the accent colour (mpvEx More sheet). */
@Composable
internal fun PlaybackSheetSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MpxTheme.colors.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/**
 * One MX-style row: leading icon, title, current value on the right,
 * chevron for drill-in rows. Rows with [onClick] null are plain info rows.
 */
@Composable
internal fun PlaybackSheetRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    showChevron: Boolean = false,
    iconTint: Color = MpxTheme.colors.primary,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 12.dp)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                color = MpxTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MpxTheme.colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Radio row used by the selection sub-sheets (decoder/aspect/rotation/tracks). */
@Composable
internal fun PlaybackSheetRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MpxTheme.colors.textPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * "1×" / "1.25×" / "0.75×" — rounds to 2 decimals (pinch produces
 * long floats) and trims the trailing ".0" off whole values, matching
 * the player HUD's formatMultiplier in PlayerScreen (#261).
 */
internal fun formatMultiplier(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    return rounded.toString().removeSuffix(".0") + "×"
}

/** "+0.50s" / "-0.20s" / "0.00s", same format the old subtitle dialog used. */
internal fun formatDelaySeconds(delayMs: Int): String =
    if (delayMs == 0) "0.00s" else String.format(Locale.US, "%+.2fs", delayMs / 1000.0)

/** Audio delay stepper, shared by the Audio Tracks page and the Audio Delay page. */
@Composable
internal fun AudioDelayStepper(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val delayMs = currentSettings.audioDelayMs
    StepperRow(
        label = stringResource(R.string.playback_sheet_audio_delay),
        valueText = formatDelaySeconds(delayMs),
        minusContentDescription = stringResource(R.string.audio_delay_decrease),
        plusContentDescription = stringResource(R.string.audio_delay_increase),
        onMinus = {
            onSettingsChanged(
                currentSettings.copy(audioDelayMs = (delayMs - 100).coerceIn(-10000, 10000)),
            )
        },
        onPlus = {
            onSettingsChanged(
                currentSettings.copy(audioDelayMs = (delayMs + 100).coerceIn(-10000, 10000)),
            )
        },
        onReset = if (delayMs != 0) {
            { onSettingsChanged(currentSettings.copy(audioDelayMs = 0)) }
        } else {
            null
        },
    )
}
