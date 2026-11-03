package com.mpx.player.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = PlaybackManager.getInstance(LocalContext.current),
) {
    val state by playbackManager.playbackState.collectAsState()

    // Initialize default video queue if opened empty
    LaunchedEffect(Unit) {
        if (state.currentItem == null) {
            playbackManager.playVideoQueue("camera")
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var showGestureHint by remember { mutableStateOf(true) }

    // Slider scrubbing state
    var isScrubbingSlider by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Horizontal drag seek gesture state
    var isDraggingSeek by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    val currentDuration = state.durationMs.coerceAtLeast(0L)
    val displayedCurrentTime = if (isScrubbingSlider && currentDuration > 0) {
        (scrubFraction * currentDuration).toLong()
    } else {
        state.currentPositionMs
    }

    val sliderProgress = if (isScrubbingSlider) {
        scrubFraction
    } else if (currentDuration > 0) {
        (state.currentPositionMs.toFloat() / currentDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MpxBlack)
            // Tap gestures: single tap toggles controls, double tap toggles play/pause
            .pointerInput(state.isLocked) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = {
                        if (!state.isLocked) {
                            playbackManager.togglePlayPause()
                        }
                    },
                )
            }
            // Horizontal drag seek gesture
            .pointerInput(state.isLocked, currentDuration) {
                if (state.isLocked || currentDuration <= 0) return@pointerInput

                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDragX = 0f
                        seekStartMs = state.currentPositionMs
                        seekTargetMs = seekStartMs
                        seekDeltaMs = 0L
                        isDraggingSeek = true
                    },
                    onDragEnd = {
                        if (isDraggingSeek) {
                            playbackManager.seekTo(seekTargetMs)
                            isDraggingSeek = false
                        }
                    },
                    onDragCancel = {
                        isDraggingSeek = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount
                        val delta = (accumulatedDragX * 120f).toLong()
                        val target = (seekStartMs + delta).coerceIn(0L, currentDuration)
                        seekTargetMs = target
                        seekDeltaMs = target - seekStartMs
                    },
                )
            },
    ) {
        // Video Surface via Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = playbackManager.player
                    useController = false
                    resizeMode = when (state.aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.SIXTEEN_NINE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { playerView ->
                playerView.player = playbackManager.player
                playerView.resizeMode = when (state.aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.SIXTEEN_NINE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Audio-only mode visual representation
        val currentMedia = state.currentItem
        if (currentMedia != null && !currentMedia.isVideo) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MpxPrimary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Audio track",
                        tint = MpxPrimary,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = currentMedia.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentMedia.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTextSecondary,
                )
            }
        }

        // Horizontal Scrubbing HUD Indicator
        if (isDraggingSeek) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MpxPrimary.copy(alpha = 0.6f)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sign = if (seekDeltaMs >= 0) "+" else "-"
                    val absSeconds = (kotlin.math.abs(seekDeltaMs) / 1000).toInt()
                    val deltaStr = String.format("%s%02d:%02d", sign, absSeconds / 60, absSeconds % 60)

                    Text(
                        text = "Scrub $deltaStr",
                        color = MpxPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatTimeMs(seekTargetMs)} / ${formatTimeMs(currentDuration)}",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // In-memory Resume Notice Banner
        state.resumedFromMs?.let { resumeMs ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MpxPrimary.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Resumed from ${formatTimeMs(resumeMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Start Over",
                        color = MpxPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            playbackManager.seekTo(0L)
                            playbackManager.clearResumedNotification()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MpxTextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { playbackManager.clearResumedNotification() },
                    )
                }
            }
        }

        // Single floating unlock button when screen is locked
        if (state.isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            ) {
                IconButton(
                    onClick = { playbackManager.setLocked(false) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Unlock controls",
                        tint = MpxPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            return@Box
        }

        // Full Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Overlay Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                            ),
                        )
                        .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Text(
                            text = state.currentItem?.title ?: "MPx Player",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(8.dp))

                        // Decoder Pill Badge
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val nextDecoder = when (state.decoderMode) {
                                        DecoderMode.HW -> DecoderMode.HW_PLUS
                                        DecoderMode.HW_PLUS -> DecoderMode.SW
                                        DecoderMode.SW -> DecoderMode.HW
                                    }
                                    playbackManager.setDecoderMode(nextDecoder)
                                },
                            color = MpxPrimary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MpxPrimary),
                        ) {
                            Text(
                                text = state.decoderMode.label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        IconButton(onClick = { activeDialog = "Audio Tracks" }) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = "Audio track",
                                tint = if (state.audioTracks.size > 1) MpxPrimary else Color.White,
                            )
                        }

                        IconButton(onClick = { activeDialog = "Subtitles" }) {
                            Icon(
                                imageVector = Icons.Filled.Subtitles,
                                contentDescription = "Subtitles",
                                tint = if (state.selectedSubtitleTrackIndex != -1) MpxPrimary else Color.White,
                            )
                        }

                        IconButton(onClick = { activeDialog = "Player Menu" }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                            )
                        }
                    }
                }

                // Left Side Screen Lock Button
                IconButton(
                    onClick = { playbackManager.setLocked(true) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Lock controls",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Bottom Overlay Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Gesture hint banner
                        if (showGestureHint) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Double-tap: Play/Pause • Horizontal drag: Seek",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                )
                                IconButton(
                                    onClick = { showGestureHint = false },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss hint",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Time & Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTimeMs(displayedCurrentTime),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Slider(
                                value = sliderProgress,
                                onValueChange = {
                                    isScrubbingSlider = true
                                    scrubFraction = it
                                },
                                onValueChangeFinished = {
                                    val target = (scrubFraction * currentDuration).toLong()
                                    playbackManager.seekTo(target)
                                    isScrubbingSlider = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MpxPrimary,
                                    activeTrackColor = MpxPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                            )

                            Text(
                                text = formatTimeMs(currentDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Controls Row: Aspect Ratio, Prev, 10s Replay, Play/Pause Hero, 10s Forward, Next, Speed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Aspect Ratio Toggle
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val nextMode = when (state.aspectRatio) {
                                            AspectRatioMode.FIT -> AspectRatioMode.FILL
                                            AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                            AspectRatioMode.ZOOM -> AspectRatioMode.SIXTEEN_NINE
                                            AspectRatioMode.SIXTEEN_NINE -> AspectRatioMode.FOUR_THREE
                                            AspectRatioMode.FOUR_THREE -> AspectRatioMode.ORIGINAL
                                            AspectRatioMode.ORIGINAL -> AspectRatioMode.FIT
                                        }
                                        playbackManager.setAspectRatio(nextMode)
                                    },
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = state.aspectRatio.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }

                            // Previous Track
                            IconButton(
                                onClick = { playbackManager.skipPrevious() },
                                enabled = state.hasPrevious,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous track",
                                    tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            // Replay 10s
                            IconButton(onClick = { playbackManager.skipBackward(10_000L) }) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Back 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Hero Play/Pause Button
                            IconButton(
                                onClick = { playbackManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MpxPrimary),
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            // Forward 10s
                            IconButton(onClick = { playbackManager.skipForward(10_000L) }) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Next Track
                            IconButton(
                                onClick = { playbackManager.skipNext() },
                                enabled = state.hasNext,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next track",
                                    tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            // Speed Toggle
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val nextSpeed = when (state.speed) {
                                            0.5f -> 0.75f
                                            0.75f -> 1.0f
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        playbackManager.setSpeed(nextSpeed)
                                    },
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "${state.speed}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Audio Tracks Selection Dialog (Real Media3 Tracks)
    if (activeDialog == "Audio Tracks") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Text(
                    text = "Select Audio Track",
                    color = MpxTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (state.audioTracks.isEmpty()) {
                    Text(
                        text = "No discrete audio tracks available in this media stream (default audio output).",
                        color = MpxTextSecondary,
                    )
                } else {
                    LazyColumn {
                        items(state.audioTracks) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = track.isSelected,
                                    onClick = {
                                        playbackManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = track.label,
                                        color = MpxTextPrimary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "Language: ${track.language}",
                                        color = MpxTextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close", color = MpxPrimary)
                }
            },
        )
    }

    // Subtitles Selection Dialog (Real Media3 Subtitle Tracks)
    if (activeDialog == "Subtitles") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Text(
                    text = "Subtitle Selection",
                    color = MpxTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    // Turn Off option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackManager.selectSubtitleTrack(null, null)
                                activeDialog = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.selectedSubtitleTrackIndex == -1,
                            onClick = {
                                playbackManager.selectSubtitleTrack(null, null)
                                activeDialog = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Off (Disable subtitles)",
                            color = MpxTextPrimary,
                            fontWeight = if (state.selectedSubtitleTrackIndex == -1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    if (state.subtitleTracks.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No subtitle tracks available in this media.",
                            color = MpxTextSecondary,
                            fontSize = 13.sp,
                        )
                    } else {
                        state.subtitleTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = track.isSelected,
                                    onClick = {
                                        playbackManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = track.label,
                                        color = MpxTextPrimary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "Language: ${track.language}",
                                        color = MpxTextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close", color = MpxPrimary)
                }
            },
        )
    }

    // Player Options Menu
    if (activeDialog == "Player Menu") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Text(
                    text = "Playback Settings",
                    color = MpxTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Audio Tracks" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Audio Tracks (${state.audioTracks.size})", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Subtitles" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Subtitles (${if (state.selectedSubtitleTrackIndex == -1) "Off" else "Active"})", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextSpeed = when (state.speed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.75f
                                    else -> 1.0f
                                }
                                playbackManager.setSpeed(nextSpeed)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Playback Speed: ${state.speed}x", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextMode = when (state.aspectRatio) {
                                    AspectRatioMode.FIT -> AspectRatioMode.FILL
                                    AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                    AspectRatioMode.ZOOM -> AspectRatioMode.SIXTEEN_NINE
                                    AspectRatioMode.SIXTEEN_NINE -> AspectRatioMode.FOUR_THREE
                                    AspectRatioMode.FOUR_THREE -> AspectRatioMode.ORIGINAL
                                    AspectRatioMode.ORIGINAL -> AspectRatioMode.FIT
                                }
                                playbackManager.setAspectRatio(nextMode)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Aspect Ratio: ${state.aspectRatio.label}", color = MpxTextPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("OK", color = MpxPrimary)
                }
            },
        )
    }
}
