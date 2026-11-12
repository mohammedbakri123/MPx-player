package com.mpx.player.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.player.state.PlayerGestureState
import com.mpx.player.ui.screens.player.state.SideSeekDirection
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VerticalIndicatorBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    width: Dp = 6.dp,
    trackColor: Color = MpxTheme.colors.textPrimary.copy(alpha = 0.25f),
    progressColor: Color = MpxTheme.colors.primary,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height)
            .width(width)
            .clip(RoundedCornerShape(width / 2))
            .background(trackColor),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height * clamped)
                    .clip(RoundedCornerShape(width / 2))
                    .background(progressColor),
            )
        }
    }
}

@Composable
fun GestureHud(
    percentText: String,
    fraction: Float,
    icon: ImageVector,
    iconDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .clip(MpxShapes.large)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MpxTheme.colors.black.copy(alpha = 0.45f),
                        MpxTheme.colors.black.copy(alpha = 0.65f),
                    ),
                ),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                color = MpxTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            VerticalIndicatorBar(
                fraction = fraction,
                height = 64.dp,
                width = 4.dp,
                trackColor = MpxTheme.colors.textPrimary.copy(alpha = 0.25f),
                progressColor = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MpxTheme.colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Player overlays (§6): empty state, audio-only presentation,
 * loading spinner, gesture HUDs, lock button, and notification banners.
 */
@Composable
fun BoxScope.PlayerOverlays(
    state: PlaybackState,
    isInPipMode: Boolean,
    controlsVisible: Boolean,
    currentDuration: Long,
    gestureState: PlayerGestureState,
    viewModel: PlayerViewModel,
) {
    val currentMedia = state.currentItem

    // Empty-player state when opened without a queued video (issue #57)
    if (currentMedia == null) {
        if (!isInPipMode) Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MpxTheme.colors.textPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MpxTheme.colors.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.player_empty_no_video),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.player_empty_select_video),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    } else if (!currentMedia.isVideo) {
        if (!isInPipMode) Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MpxTheme.colors.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = stringResource(R.string.player_cd_audio_track),
                    tint = MpxTheme.colors.primary,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = currentMedia.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentMedia.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }

    if (state.isPreparing && currentMedia?.isVideo == true) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MpxLoadingIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.player_loading),
                color = MpxTheme.colors.textPrimary,
                fontSize = 14.sp,
            )
        }
    }

    if (!isInPipMode && gestureState.isDraggingSeek) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(MpxShapes.large)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MpxTheme.colors.black.copy(alpha = 0.45f),
                            MpxTheme.colors.black.copy(alpha = 0.65f),
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sign = if (gestureState.seekDeltaMs >= 0) "+" else "-"
                val absSeconds = (kotlin.math.abs(gestureState.seekDeltaMs) / 1000).toInt()
                val deltaStr = String.format(Locale.US, "%s%02d:%02d", sign, absSeconds / 60, absSeconds % 60)

                Text(
                    text = stringResource(R.string.player_hud_scrub, deltaStr),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatTimeMs(gestureState.seekTargetMs)} / ${formatTimeMs(currentDuration)}",
                    color = MpxTheme.colors.textPrimary.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (!isInPipMode && gestureState.showBrightnessIndicator) {
        val brightnessPercent = (gestureState.appliedBrightness * 100).roundToInt().coerceIn(0, 100)
        val icon = when {
            brightnessPercent < 33 -> Icons.Filled.BrightnessLow
            brightnessPercent < 67 -> Icons.Filled.BrightnessMedium
            else -> Icons.Filled.BrightnessHigh
        }
        GestureHud(
            percentText = "$brightnessPercent%",
            fraction = gestureState.appliedBrightness,
            icon = icon,
            iconDescription = stringResource(R.string.player_cd_brightness),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
        )
    }

    if (!isInPipMode && gestureState.showVolumeIndicator) {
        val volumeRange = (gestureState.volumeMax - gestureState.volumeMin).coerceAtLeast(1)
        val volumePercent = (((gestureState.appliedVolume - gestureState.volumeMin).toFloat() / volumeRange.toFloat()) * 100).roundToInt().coerceIn(0, 100)
        val icon = when {
            gestureState.appliedVolume <= gestureState.volumeMin -> Icons.AutoMirrored.Filled.VolumeMute
            volumePercent < 50 -> Icons.AutoMirrored.Filled.VolumeDown
            else -> Icons.AutoMirrored.Filled.VolumeUp
        }
        GestureHud(
            percentText = "$volumePercent%",
            fraction = ((gestureState.appliedVolume - gestureState.volumeMin).toFloat() / volumeRange.toFloat()).coerceIn(0f, 1f),
            icon = icon,
            iconDescription = stringResource(R.string.player_cd_volume),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp),
        )
    }

    if (!isInPipMode && gestureState.isLongPress2x) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(MpxShapes.large)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MpxTheme.colors.black.copy(alpha = 0.45f),
                            MpxTheme.colors.black.copy(alpha = 0.65f),
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "2×",
                color = MpxTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }

    if (!isInPipMode && gestureState.showZoomIndicator) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(MpxShapes.large)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MpxTheme.colors.black.copy(alpha = 0.45f),
                            MpxTheme.colors.black.copy(alpha = 0.65f),
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = gestureState.zoomDisplayText,
                color = MpxTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }

    if (!isInPipMode) gestureState.sideSeekDirection?.let { direction ->
        val isBackward = direction == SideSeekDirection.BACKWARD
        val seekSeconds = viewModel.settings.value.doubleTapSeekSeconds
        Box(
            modifier = Modifier
                .align(if (isBackward) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(start = if (isBackward) 32.dp else 0.dp, end = if (!isBackward) 32.dp else 0.dp)
                .clip(MpxShapes.large)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MpxTheme.colors.black.copy(alpha = 0.45f),
                            MpxTheme.colors.black.copy(alpha = 0.65f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isBackward) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                    contentDescription = if (isBackward) stringResource(R.string.player_cd_seek_back) else stringResource(R.string.player_cd_seek_forward),
                    tint = MpxTheme.colors.textPrimary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isBackward) "-${seekSeconds}s" else "+${seekSeconds}s",
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (currentDuration > 0) {
                        "${formatTimeMs(state.currentPositionMs)} / ${formatTimeMs(currentDuration)}"
                    } else {
                        formatTimeMs(state.currentPositionMs)
                    },
                    color = MpxTheme.colors.textPrimary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (!isInPipMode && (state.resumedFromMs != null || state.decoderFallbackNote != null || state.userNotification != null)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 128.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.resumedFromMs?.let { resumeMs ->
                Surface(
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.extraLarge,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_resumed_from, formatTimeMs(resumeMs)),
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_start_over),
                            color = MpxTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                viewModel.seekTo(0L)
                                viewModel.clearResumedNotification()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearResumedNotification() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            state.decoderFallbackNote?.let { fallbackMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = fallbackMsg,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearDecoderFallbackNote() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            state.userNotification?.let { userMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = userMsg,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearUserNotification() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (!isInPipMode && state.isLocked) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp),
        ) {
            IconButton(
                onClick = { viewModel.setLocked(false) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MpxTheme.colors.black.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.player_cd_unlock),
                    tint = MpxTheme.colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
