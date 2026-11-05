package com.mpx.player.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxTheme.colors.primary
import com.mpx.player.ui.theme.MpxTheme.colors.surfaceElevated
import com.mpx.player.ui.theme.MpxTheme.colors.surfaceVariant

/**
 * M2.5 slider + progress atoms (#106). Progress is blue, sparingly;
 * tracks stay black/grey. No screen logic.
 */

@Composable
fun MpxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = MpxTheme.colors.primary,
            activeTrackColor = MpxTheme.colors.primary,
            inactiveTrackColor = MpxTheme.colors.surfaceElevated,
        ),
    )
}

@Composable
fun MpxLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth(),
        color = MpxTheme.colors.primary,
        trackColor = MpxTheme.colors.surfaceVariant,
    )
}

@Composable
fun MpxLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = MpxTheme.colors.primary,
        strokeWidth = 3.dp,
    )
}
