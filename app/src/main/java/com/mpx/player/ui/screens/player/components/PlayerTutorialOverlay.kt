package com.mpx.player.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * First-run gesture tutorial overlay (#151). One overlay, four tappable
 * steps: double-tap zones, horizontal seek, left-side brightness,
 * right-side volume. Tapping the card advances a step; Skip and the X
 * dismiss at any time; the last step's button finishes. Dismissing in
 * any way marks the tutorial seen (the caller persists the flag).
 * Playback underneath is untouched: the scrim consumes all touches so
 * the root gesture detector never fires while the tutorial is up.
 */
@Composable
fun PlayerTutorialOverlay(
    step: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastStep = 3
    val isLast = step >= lastStep
    val advance = { if (isLast) onDismiss() else onStepChange(step + 1) }

    val titleRes = when (step) {
        0 -> R.string.player_tutorial_step_double_tap_title
        1 -> R.string.player_tutorial_step_seek_title
        2 -> R.string.player_tutorial_step_brightness_title
        else -> R.string.player_tutorial_step_volume_title
    }
    val descRes = when (step) {
        0 -> R.string.player_tutorial_step_double_tap_desc
        1 -> R.string.player_tutorial_step_seek_desc
        2 -> R.string.player_tutorial_step_brightness_desc
        else -> R.string.player_tutorial_step_volume_desc
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(MpxShapes.large)
                .background(MpxTheme.colors.surfaceVariant)
                .border(MpxHairline, MpxTheme.colors.border, MpxShapes.large)
                .clickable { advance() }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_tutorial_title),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.player_tutorial_skip),
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(titleRes),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            when (step) {
                0 -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(36.dp))
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MpxTheme.colors.primary, modifier = Modifier.size(44.dp))
                    Icon(Icons.Filled.Forward10, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(36.dp))
                }
                1 -> Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(MpxShapes.extraSmall)
                        .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MpxTheme.colors.primary),
                    )
                }
                2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BrightnessHigh, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(72.dp)
                            .clip(MpxShapes.extraSmall)
                            .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MpxTheme.colors.primary),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Filled.BrightnessLow, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
                }
                else -> Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(104.dp)
                        .clip(MpxShapes.extraSmall)
                        .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MpxTheme.colors.primary),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(descRes),
                color = MpxTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == step) MpxTheme.colors.primary
                                else MpxTheme.colors.textMuted.copy(alpha = 0.4f),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.player_tutorial_skip), color = MpxTheme.colors.textSecondary)
                }
                TextButton(onClick = advance) {
                    Text(
                        stringResource(if (isLast) R.string.player_tutorial_got_it else R.string.player_tutorial_next),
                        color = MpxTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
