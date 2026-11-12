package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.theme.MpxTheme
import kotlin.math.abs

@Composable
internal fun PlaybackSheetSpeedPage(
    state: PlaybackState,
    playbackController: PlaybackController,
) {
    Text(
        text = formatMultiplier(state.speed),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MpxTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    // Presets, two rows of three (the old menu only cycled 1.0/1.25/1.5/2.0/0.75).
    listOf(
        listOf(0.5f, 0.75f, 1.0f),
        listOf(1.25f, 1.5f, 2.0f),
    ).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { speed ->
                FilterChip(
                    // Issue #249: epsilon, not Float == — the slider
                    // (0.5..2.0, 29 steps = 0.05 per step) produces
                    // inexact floats, so a slider-set 1.25x may not equal
                    // the 1.25f preset exactly. 0.01f is far above float
                    // rounding (~1e-7) and far below half the smallest
                    // preset gap (0.125), so no two chips can match at
                    // once. Display-only: tapping a chip still sets the
                    // exact preset speed via setSpeed below.
                    selected = abs(state.speed - speed) < 0.01f,
                    onClick = { playbackController.setSpeed(speed) },
                    label = { Text(formatMultiplier(speed)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Slider(
        value = state.speed,
        onValueChange = { playbackController.setSpeed(it) },
        valueRange = 0.5f..2.0f,
        steps = 29,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("0.5×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
        Text("2×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
    }
}
