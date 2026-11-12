package com.mpx.player.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

/**
 * Bottom transport chrome for [com.mpx.player.ui.screens.player.PlayerScreen] (§6).
 * Slider, time stamps, transport row (Lock, Prev, Play/Pause, Next, Rotate),
 * and active sleep-timer chip.
 */
@Composable
fun PlayerBottomChrome(
    displayedCurrentTime: Long,
    currentDuration: Long,
    sliderProgress: Float,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    state: PlaybackState,
    isLandscapeOrientation: Boolean,
    onOpenSleepSheet: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MpxTheme.colors.black.copy(alpha = 0.8f)),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Time & Slider Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimeMs(displayedCurrentTime),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )

                Slider(
                    value = sliderProgress,
                    onValueChange = onSliderChange,
                    onValueChangeFinished = onSliderChangeFinished,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MpxTheme.colors.primary,
                        activeTrackColor = MpxTheme.colors.primary,
                        inactiveTrackColor = MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                    ),
                )

                Text(
                    text = formatTimeMs(currentDuration),
                    color = MpxTheme.colors.textPrimary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Controls Row: Lock, Prev, Play/Pause Hero, Next, Rotation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.setLocked(true) },
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = stringResource(R.string.player_cd_lock),
                        tint = MpxTheme.colors.textPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    enabled = state.hasPrevious,
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.player_cd_previous),
                        tint = if (state.hasPrevious) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) stringResource(R.string.player_cd_pause) else stringResource(R.string.player_cd_play),
                        tint = MpxTheme.colors.textPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    enabled = state.hasNext,
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.player_cd_next),
                        tint = if (state.hasNext) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRotation() },
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenRotation,
                        contentDescription = stringResource(R.string.playback_sheet_rotation),
                        tint = if (isLandscapeOrientation) MpxTheme.colors.primary else MpxTheme.colors.textPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Sleep Timer active chip near controls
            if (state.sleepTimerRemainingSeconds != null) {
                val remaining = state.sleepTimerRemainingSeconds ?: 0L
                val hours = remaining / 3600
                val mins = (remaining % 3600) / 60
                val secs = remaining % 60
                val timeFormatted = if (hours > 0) {
                    String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(MpxShapes.medium)
                            .clickable { onOpenSleepSheet() },
                        color = MpxTheme.colors.primaryVariant.copy(alpha = 0.9f),
                        shape = MpxShapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MpxTheme.colors.textPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.player_sleep_timer_chip, timeFormatted),
                                color = MpxTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.cancelSleepTimer()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.player_sleep_timer_canceled_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .padding(horizontal = 17.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.player_sleep_timer_cancel),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
