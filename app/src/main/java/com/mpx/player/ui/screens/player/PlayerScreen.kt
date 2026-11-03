package com.mpx.player.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import com.mpx.player.util.PipUtils
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = PlaybackManager.getInstance(LocalContext.current),
    settingsRepository: AppSettingsRepository? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    var currentSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    val state by playbackManager.playbackState.collectAsState()

    val targetOrientation = remember(currentSettings.autoScreenRotation, currentSettings.screenOrientation) {
        if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto" || currentSettings.screenOrientation == "sensor") {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else if (currentSettings.screenOrientation.equals("portrait", ignoreCase = true)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    DisposableEffect(targetOrientation, activity) {
        activity?.requestedOrientation = targetOrientation
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var subtitleErrorDialogText by remember { mutableStateOf<String?>(null) }

    val openSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }

            when (val validation = SubtitleUtils.validateSubtitleFile(context, uri)) {
                is SubtitleValidationResult.Supported -> {
                    val loaded = playbackManager.loadExternalSubtitle(
                        uri = validation.uri,
                        displayName = validation.displayName,
                        mimeType = validation.mimeType,
                    )
                    if (loaded) {
                        Toast.makeText(context, "Loaded subtitle: ${validation.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
                is SubtitleValidationResult.Unsupported -> {
                    subtitleErrorDialogText = "Cannot load '${validation.fileName}':\n\n${validation.reason}"
                }
            }
        }
    }

    // Initialize default video queue if opened empty
    LaunchedEffect(Unit) {
        if (state.currentItem == null) {
            playbackManager.playVideoQueue("camera")
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var showGestureHint by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val customPlaylists by playbackManager.persistenceRepository.observePlaylists().collectAsState(initial = emptyList())
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val currentUri = state.currentItem?.uri.orEmpty()
    val isFavourite by produceState(initialValue = false, currentUri) {
        if (currentUri.isNotBlank()) {
            playbackManager.persistenceRepository.observeIsFavourite(currentUri).collect {
                value = it
            }
        } else {
            value = false
        }
    }

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
                    applySubtitleSettings(this, currentSettings)
                }
            },
            update = { playerView ->
                if (playerView.player !== playbackManager.player) {
                    playerView.player = playbackManager.player
                }
                playerView.resizeMode = when (state.aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.SIXTEEN_NINE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                applySubtitleSettings(playerView, currentSettings)
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

        // Platform Decoder Fallback Notice Banner
        state.decoderFallbackNote?.let { fallbackMsg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (state.resumedFromMs != null) 124.dp else 72.dp, start = 16.dp, end = 16.dp),
                color = Color.Black.copy(alpha = 0.90f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = fallbackMsg,
                        color = Color(0xFFFFE082),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MpxTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { playbackManager.clearDecoderFallbackNote() },
                    )
                }
            }
        }

        // Honest User Notification Banner (Background play status, external subtitle notice, etc.)
        state.userNotification?.let { userMsg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = if (state.decoderFallbackNote != null) 160.dp else if (state.resumedFromMs != null) 124.dp else 72.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                color = Color.Black.copy(alpha = 0.90f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MpxPrimary),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MpxPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = userMsg,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MpxTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { playbackManager.clearUserNotification() },
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
                                    realSettingsRepo.save(realSettingsRepo.load().copy(decoderMode = nextDecoder.label))
                                    currentSettings = realSettingsRepo.load()
                                    Toast.makeText(context, "Decoder: ${nextDecoder.title}", Toast.LENGTH_SHORT).show()
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

                        IconButton(onClick = {
                            state.currentItem?.let { item ->
                                scope.launch {
                                    playbackManager.persistenceRepository.toggleFavourite(
                                        FavouriteItem(
                                            mediaUri = item.uri,
                                            mediaId = item.id,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            isVideo = item.isVideo,
                                            durationMs = item.durationMs,
                                        )
                                    )
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavourite) "Remove favourite" else "Add favourite",
                                tint = if (isFavourite) Color(0xFFFF5252) else Color.White,
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

                        IconButton(onClick = {
                            val entered = PipUtils.enterPip(context)
                            if (!entered) {
                                val msg = if (!PipUtils.isPipSupported(context)) {
                                    "Pop-up Play (PiP) is not supported on this device"
                                } else {
                                    "Unable to enter Picture-in-Picture mode"
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Pop-up Play (PiP)",
                                tint = Color.White,
                            )
                        }

                        IconButton(onClick = {
                            val nextMode = when {
                                currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto" -> "landscape"
                                currentSettings.screenOrientation.equals("landscape", ignoreCase = true) -> "portrait"
                                else -> "auto"
                            }
                            val isAuto = nextMode == "auto"
                            val updated = currentSettings.copy(
                                autoScreenRotation = isAuto,
                                screenOrientation = nextMode,
                            )
                            currentSettings = updated
                            realSettingsRepo.save(updated)
                            val msg = when (nextMode) {
                                "landscape" -> context.getString(R.string.toast_rotation_landscape)
                                "portrait" -> context.getString(R.string.toast_rotation_portrait)
                                else -> context.getString(R.string.toast_rotation_auto)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ScreenRotation,
                                contentDescription = stringResource(R.string.cd_screen_rotation),
                                tint = if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto") MpxPrimary else Color.White,
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

    // Subtitles Selection Dialog (Real Media3 Subtitle Tracks & SAF External Loading)
    if (activeDialog == "Subtitles") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MpxPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Subtitle Selection",
                        color = MpxTextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Button to launch SAF File Picker for external SRT/VTT
                    OutlinedButton(
                        onClick = {
                            activeDialog = null
                            openSubtitleLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MpxPrimary),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = MpxPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open Subtitle File (SRT / VTT)...",
                            color = MpxPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Available Tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MpxTextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))

                    // Turn Off option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackManager.selectSubtitleTrack(null, null)
                                activeDialog = null
                            }
                            .padding(vertical = 6.dp),
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
                        Text(
                            text = "No subtitle tracks found in media. Use 'Open Subtitle File' above to load .srt or .vtt.",
                            color = MpxTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 6.dp),
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
                                    .padding(vertical = 6.dp),
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.label,
                                            color = MpxTextPrimary,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (track.isExternal) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MpxPrimary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = "External",
                                                    color = MpxPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Language: ${track.language}",
                                        color = MpxTextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    // Subtitle Size Controls (from settings)
                    Text("Subtitle Text Size", style = MaterialTheme.typography.labelMedium, color = MpxTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Small", "Normal", "Large").forEach { size ->
                            val isSelected = currentSettings.subtitleSize.equals(size, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = currentSettings.copy(subtitleSize = size)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                },
                                label = { Text(size, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MpxPrimary,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    // Subtitle Style Controls (from settings)
                    Text("Subtitle Style / Color", style = MaterialTheme.typography.labelMedium, color = MpxTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "White" to "White",
                            "Yellow" to "Yellow",
                            "Cyan" to "Cyan",
                            "Default" to "Default",
                        ).forEach { (styleKey, styleLabel) ->
                            val isSelected = currentSettings.subtitleStyle.equals(styleKey, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = currentSettings.copy(subtitleStyle = styleKey)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                },
                                label = { Text(styleLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MpxPrimary,
                                    selectedLabelColor = Color.White,
                                ),
                            )
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

    // Decoder Selection Dialog
    if (activeDialog == "Decoder") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MpxPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Decoder Selection",
                        color = MpxTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column {
                    DecoderMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackManager.setDecoderMode(mode)
                                    val updated = realSettingsRepo.load().copy(decoderMode = mode.label)
                                    realSettingsRepo.save(updated)
                                    currentSettings = updated
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.decoderMode == mode,
                                onClick = {
                                    playbackManager.setDecoderMode(mode)
                                    val updated = realSettingsRepo.load().copy(decoderMode = mode.label)
                                    realSettingsRepo.save(updated)
                                    currentSettings = updated
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    color = MpxTextPrimary,
                                    fontWeight = if (state.decoderMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = mode.description,
                                    color = MpxTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Active Video Codec: ${state.activeDecoderName ?: "Initializing / waiting for video frame..."}",
                        color = MpxPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    state.decoderFallbackNote?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0x33FFB74D),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = note,
                                    color = Color(0xFFFFE082),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
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

    // Unsupported Subtitle Format Dialog
    if (subtitleErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = { subtitleErrorDialogText = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Text(
                    text = "Unsupported Subtitle Format",
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = subtitleErrorDialogText!!,
                    color = MpxTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { subtitleErrorDialogText = null }) {
                    Text("OK", color = MpxPrimary)
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
                            .clickable { activeDialog = "Decoder" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Decoder Mode (${state.decoderMode.label})", color = MpxTextPrimary)
                    }
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
                                activeDialog = null
                                val entered = PipUtils.enterPip(context)
                                if (!entered) {
                                    val msg = if (!PipUtils.isPipSupported(context)) {
                                        "Pop-up Play (PiP) is not supported on this device"
                                    } else {
                                        "Unable to enter Picture-in-Picture mode"
                                    }
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Pop-up Play (PiP Mode)", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextBackgroundPlay = !currentSettings.backgroundPlay
                                val updated = currentSettings.copy(backgroundPlay = nextBackgroundPlay)
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                val note = if (nextBackgroundPlay) {
                                    if (state.currentItem?.isVideo == true) {
                                        "Background play enabled: audio continues in background (video rendering pauses)."
                                    } else {
                                        "Background play enabled: audio continues when leaving app."
                                    }
                                } else {
                                    "Background play disabled: playback pauses when leaving app."
                                }
                                Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (currentSettings.backgroundPlay) MpxPrimary else Color.Gray,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Background Play: ${if (currentSettings.backgroundPlay) "On" else "Off"}",
                                color = MpxTextPrimary,
                            )
                            Text(
                                if (state.currentItem?.isVideo == true) {
                                    "Audio continues when app minimized; video rendering pauses"
                                } else {
                                    "Audio continues when screen is off or app minimized"
                                },
                                color = MpxTextSecondary,
                                fontSize = 11.sp,
                            )
                        }
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
                        Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Aspect Ratio: ${state.aspectRatio.label}", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Screen Rotation" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        val rotationLabel = if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto") {
                            stringResource(R.string.player_rotation_auto)
                        } else if (currentSettings.screenOrientation.equals("portrait", ignoreCase = true)) {
                            stringResource(R.string.player_orientation_portrait)
                        } else {
                            stringResource(R.string.player_orientation_landscape)
                        }
                        Text("Screen Rotation ($rotationLabel)", color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeDialog = null
                                showAddToPlaylistDialog = true
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Playlist...", color = MpxTextPrimary)
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

    if (activeDialog == "Screen Rotation") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MpxPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_rotation_dialog_title),
                        color = MpxTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column {
                    val isAuto = currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = true,
                                    screenOrientation = "auto",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_auto), Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isAuto,
                            onClick = {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = true,
                                    screenOrientation = "auto",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_auto), Toast.LENGTH_SHORT).show()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_rotation_auto), color = MpxTextPrimary, fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_rotation_auto_desc), color = MpxTextSecondary, fontSize = 12.sp)
                        }
                    }

                    val isLandscape = !currentSettings.autoScreenRotation && currentSettings.screenOrientation.equals("landscape", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = false,
                                    screenOrientation = "landscape",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_landscape), Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isLandscape,
                            onClick = {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = false,
                                    screenOrientation = "landscape",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_landscape), Toast.LENGTH_SHORT).show()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_landscape), color = MpxTextPrimary, fontWeight = if (isLandscape) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_landscape_desc), color = MpxTextSecondary, fontSize = 12.sp)
                        }
                    }

                    val isPortrait = !currentSettings.autoScreenRotation && currentSettings.screenOrientation.equals("portrait", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = false,
                                    screenOrientation = "portrait",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_portrait), Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isPortrait,
                            onClick = {
                                val updated = currentSettings.copy(
                                    autoScreenRotation = false,
                                    screenOrientation = "portrait",
                                )
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                activeDialog = null
                                Toast.makeText(context, context.getString(R.string.toast_rotation_portrait), Toast.LENGTH_SHORT).show()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_portrait), color = MpxTextPrimary, fontWeight = if (isPortrait) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_portrait_desc), color = MpxTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_close), color = MpxPrimary)
                }
            },
        )
    }

    if (showAddToPlaylistDialog && state.currentItem != null) {
        val current = state.currentItem!!
        AddToPlaylistDialog(
            playlists = customPlaylists,
            mediaTitle = current.title,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { pl ->
                scope.launch {
                    playbackManager.persistenceRepository.addItemToPlaylist(
                        PlaylistItem(
                            playlistId = pl.id,
                            mediaUri = current.uri,
                            mediaId = current.id,
                            title = current.title,
                            subtitle = current.subtitle,
                            isVideo = current.isVideo,
                            durationMs = current.durationMs,
                        )
                    )
                    showAddToPlaylistDialog = false
                }
            },
            onCreateNewPlaylist = {
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val newId = playbackManager.persistenceRepository.createPlaylist(
                        name = name,
                        isVideoPlaylist = state.currentItem?.isVideo ?: false,
                    )
                    state.currentItem?.let { current ->
                        playbackManager.persistenceRepository.addItemToPlaylist(
                            PlaylistItem(
                                playlistId = newId,
                                mediaUri = current.uri,
                                mediaId = current.id,
                                title = current.title,
                                subtitle = current.subtitle,
                                isVideo = current.isVideo,
                                durationMs = current.durationMs,
                            )
                        )
                    }
                    showCreatePlaylistDialog = false
                }
            },
        )
    }
}

private fun applySubtitleSettings(playerView: PlayerView, settings: AppSettings) {
    val subtitleView = playerView.subtitleView ?: return
    when (settings.subtitleSize.lowercase()) {
        "small" -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        "large" -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        else -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    }

    when (settings.subtitleStyle.lowercase()) {
        "yellow" -> {
            subtitleView.setStyle(
                CaptionStyleCompat(
                    android.graphics.Color.YELLOW,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    Typeface.DEFAULT_BOLD,
                )
            )
        }
        "cyan" -> {
            subtitleView.setStyle(
                CaptionStyleCompat(
                    android.graphics.Color.CYAN,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    Typeface.DEFAULT_BOLD,
                )
            )
        }
        "default" -> {
            subtitleView.setUserDefaultStyle()
        }
        else -> { // "white"
            subtitleView.setStyle(
                CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    Typeface.DEFAULT_BOLD,
                )
            )
        }
    }
    subtitleView.setApplyEmbeddedStyles(true)
}
