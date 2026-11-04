package com.mpx.player.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant

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
            thumbColor = MpxPrimary,
            activeTrackColor = MpxPrimary,
            inactiveTrackColor = MpxSurfaceElevated,
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
        color = MpxPrimary,
        trackColor = MpxSurfaceVariant,
    )
}

@Composable
fun MpxLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = MpxPrimary,
        strokeWidth = 3.dp,
    )
}
