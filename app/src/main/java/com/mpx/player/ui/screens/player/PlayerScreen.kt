package com.mpx.player.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.mpx.player.R
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackService
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.util.PipUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import java.util.Locale
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private enum class DragGestureMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME
}

private enum class SideSeekDirection {
    BACKWARD,
    FORWARD
}

private fun getScreenBrightness(context: Context, activity: Activity?): Float {
    val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (windowBrightness in 0f..1f) {
        windowBrightness
    } else {
        try {
            val systemBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
            (systemBrightness / 255f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0.5f
        }
    }
}

private fun setScreenBrightness(activity: Activity?, brightness: Float): Float {
    val target = brightness.coerceIn(0f, 1f)
    activity?.window?.let { window ->
        val lp = window.attributes
        lp.screenBrightness = target
        window.attributes = lp
    }
    return activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: target
}

private fun setMediaVolume(audioManager: AudioManager?, targetVolume: Int, minVolume: Int, maxVolume: Int): Int {
    if (audioManager == null) return 0
    val clamped = targetVolume.coerceIn(minVolume, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
}

@Composable
private fun VerticalIndicatorBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    width: Dp = 6.dp,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
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
fun PlayerScreen(
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    subtitleResultHandler: ActivityResultHandler<android.net.Uri?>,
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
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    var subtitleErrorDialogText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(subtitleResultHandler) {
        onDispose { subtitleResultHandler.callback = null }
    }
    SideEffect {
        subtitleResultHandler.callback = { uri: Uri? ->
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
    }

    // Empty-player state: no auto-play when opened empty (issue #57)

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

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Horizontal drag seek gesture state
    var isDraggingSeek by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    // Gesture states for seek, brightness, and volume
    var currentDragMode by remember { mutableStateOf(DragGestureMode.NONE) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    // Brightness gesture state
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var appliedBrightness by remember {
        mutableFloatStateOf(getScreenBrightness(context, activity))
    }
    var dragStartBrightness by remember { mutableFloatStateOf(0.5f) }
    var hideBrightnessJob by remember { mutableStateOf<Job?>(null) }

    // Volume gesture state
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var appliedVolume by remember {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }
    var dragStartVolume by remember { mutableIntStateOf(0) }
    var volumeMin by remember {
        mutableIntStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else {
                0
            }
        )
    }
    var volumeMax by remember {
        mutableIntStateOf(audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15)
    }
    var hideVolumeJob by remember { mutableStateOf<Job?>(null) }

    // Double-tap side seek gesture state
    var sideSeekDirection by remember { mutableStateOf<SideSeekDirection?>(null) }
    var hideSideSeekJob by remember { mutableStateOf<Job?>(null) }

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
            .background(MaterialTheme.colorScheme.background)
            // Tap gestures: single tap toggles controls, double tap zones (left: -10s, center: play/pause, right: +10s)
            .pointerInput(state.isLocked) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        if (sideSeekDirection != null) {
                            hideSideSeekJob?.cancel()
                            sideSeekDirection = null
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!state.isLocked) {
                            val oneThird = size.width / 3f
                            val twoThirds = size.width * (2f / 3f)
                            when {
                                offset.x < oneThird -> {
                                    playbackManager.skipBackward(10_000L)
                                    sideSeekDirection = SideSeekDirection.BACKWARD
                                    hideBrightnessJob?.cancel()
                                    showBrightnessIndicator = false
                                    hideVolumeJob?.cancel()
                                    showVolumeIndicator = false
                                    hideSideSeekJob?.cancel()
                                    hideSideSeekJob = scope.launch {
                                        delay(1000)
                                        sideSeekDirection = null
                                    }
                                }
                                offset.x > twoThirds -> {
                                    playbackManager.skipForward(10_000L)
                                    sideSeekDirection = SideSeekDirection.FORWARD
                                    hideBrightnessJob?.cancel()
                                    showBrightnessIndicator = false
                                    hideVolumeJob?.cancel()
                                    showVolumeIndicator = false
                                    hideSideSeekJob?.cancel()
                                    hideSideSeekJob = scope.launch {
                                        delay(1000)
                                        sideSeekDirection = null
                                    }
                                }
                                else -> {
                                    playbackManager.togglePlayPause()
                                    if (sideSeekDirection != null) {
                                        hideSideSeekJob?.cancel()
                                        sideSeekDirection = null
                                    }
                                }
                            }
                        }
                    },
                )
            }
            // Drag gestures: horizontal seek, left-vertical brightness, right-vertical volume
            .pointerInput(state.isLocked, currentDuration) {
                if (state.isLocked) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        hideSideSeekJob?.cancel()
                        sideSeekDirection = null
                        currentDragMode = DragGestureMode.NONE
                        dragStartOffset = offset
                        totalDragX = 0f
                        totalDragY = 0f
                        accumulatedDragX = 0f
                        seekStartMs = state.currentPositionMs
                        seekTargetMs = seekStartMs
                        seekDeltaMs = 0L
                        dragStartBrightness = getScreenBrightness(context, activity)
                        dragStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                        volumeMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                        } else {
                            0
                        }
                        volumeMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    },
                    onDragEnd = {
                        when (currentDragMode) {
                            DragGestureMode.HORIZONTAL_SEEK -> {
                                if (isDraggingSeek) {
                                    playbackManager.seekTo(seekTargetMs)
                                    isDraggingSeek = false
                                }
                            }
                            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                                hideBrightnessJob?.cancel()
                                hideBrightnessJob = scope.launch {
                                    delay(1000)
                                    showBrightnessIndicator = false
                                }
                            }
                            DragGestureMode.VERTICAL_VOLUME -> {
                                hideVolumeJob?.cancel()
                                hideVolumeJob = scope.launch {
                                    delay(1000)
                                    showVolumeIndicator = false
                                }
                            }
                            DragGestureMode.NONE -> {}
                        }
                        currentDragMode = DragGestureMode.NONE
                    },
                    onDragCancel = {
                        if (isDraggingSeek) {
                            isDraggingSeek = false
                        }
                        if (showBrightnessIndicator) {
                            hideBrightnessJob?.cancel()
                            hideBrightnessJob = scope.launch {
                                delay(1000)
                                showBrightnessIndicator = false
                            }
                        }
                        if (showVolumeIndicator) {
                            hideVolumeJob?.cancel()
                            hideVolumeJob = scope.launch {
                                delay(1000)
                                showVolumeIndicator = false
                            }
                        }
                        currentDragMode = DragGestureMode.NONE
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (currentDragMode == DragGestureMode.NONE) {
                            val absX = kotlin.math.abs(totalDragX)
                            val absY = kotlin.math.abs(totalDragY)
                            if (absX > 0f || absY > 0f) {
                                if (absX >= absY) {
                                    if (currentDuration > 0) {
                                        currentDragMode = DragGestureMode.HORIZONTAL_SEEK
                                        isDraggingSeek = true
                                    }
                                } else {
                                    val isLeftHalf = dragStartOffset.x < size.width / 2f
                                    if (isLeftHalf) {
                                        currentDragMode = DragGestureMode.VERTICAL_BRIGHTNESS
                                        hideBrightnessJob?.cancel()
                                        showBrightnessIndicator = true
                                    } else {
                                        currentDragMode = DragGestureMode.VERTICAL_VOLUME
                                        hideVolumeJob?.cancel()
                                        showVolumeIndicator = true
                                    }
                                }
                            }
                        }

                        when (currentDragMode) {
                            DragGestureMode.HORIZONTAL_SEEK -> {
                                val minTotalX = -(seekStartMs.toFloat() / 120f)
                                val maxTotalX = ((currentDuration - seekStartMs).toFloat() / 120f)
                                totalDragX = totalDragX.coerceIn(minTotalX, maxTotalX)
                                val delta = (totalDragX * 120f).toLong()
                                val target = (seekStartMs + delta).coerceIn(0L, currentDuration)
                                seekTargetMs = target
                                seekDeltaMs = target - seekStartMs
                            }
                            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                                val sensitivity = (size.height.toFloat() * 0.75f).coerceAtLeast(200f)
                                totalDragY = totalDragY.coerceIn(
                                    -sensitivity * (1.0f - dragStartBrightness),
                                    sensitivity * dragStartBrightness,
                                )
                                val deltaFraction = -totalDragY / sensitivity
                                val target = (dragStartBrightness + deltaFraction).coerceIn(0f, 1f)
                                appliedBrightness = setScreenBrightness(activity, target)
                                hideBrightnessJob?.cancel()
                                showBrightnessIndicator = true
                            }
                            DragGestureMode.VERTICAL_VOLUME -> {
                                val sensitivity = (size.height.toFloat() * 0.75f).coerceAtLeast(200f)
                                val volumeRange = (volumeMax - volumeMin).coerceAtLeast(1)
                                val minTotalY = -sensitivity * ((volumeMax - dragStartVolume).toFloat() / volumeRange.toFloat())
                                val maxTotalY = sensitivity * ((dragStartVolume - volumeMin).toFloat() / volumeRange.toFloat())
                                totalDragY = totalDragY.coerceIn(minTotalY, maxTotalY)
                                val deltaSteps = (-totalDragY / sensitivity * volumeRange).toInt()
                                val target = (dragStartVolume + deltaSteps).coerceIn(volumeMin, volumeMax)
                                appliedVolume = setMediaVolume(audioManager, target, volumeMin, volumeMax)
                                hideVolumeJob?.cancel()
                                showVolumeIndicator = true
                            }
                            DragGestureMode.NONE -> {}
                        }
                    },
                )
            },
    ) {
        // Video Surface via Media3 PlayerView or MPV SurfaceView
        if (playbackManager.isMedia3) {
            val exoPlayer = playbackManager.exoPlayer
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
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
                        if (playerView.player !== exoPlayer) {
                            playerView.player = exoPlayer
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
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                playbackManager.attachSurface(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                // SYNCHRONOUS barrier:
                                // Set vo=null and detach before surfaceDestroyed returns.
                                playbackManager.detachSurface()
                            }
                        })
                    }
                },
                update = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Empty-player state when opened without a queued video (issue #57)
        val currentMedia = state.currentItem
        if (currentMedia == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No video queued",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Select a video from Local to start playback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!currentMedia.isVideo) {
            // Audio-only mode visual representation
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Audio track",
                        tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Horizontal Scrubbing HUD Indicator
        if (isDraggingSeek) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
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
                        color = MaterialTheme.colorScheme.primary,
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

        // Brightness HUD Indicator (Left half vertical drag)
        if (showBrightnessIndicator) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val brightnessPercent = (appliedBrightness * 100).roundToInt().coerceIn(0, 100)
                    val icon = when {
                        brightnessPercent < 33 -> Icons.Filled.BrightnessLow
                        brightnessPercent < 67 -> Icons.Filled.BrightnessMedium
                        else -> Icons.Filled.BrightnessHigh
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Brightness",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    VerticalIndicatorBar(
                        fraction = appliedBrightness,
                        height = 96.dp,
                        width = 6.dp,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        progressColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "$brightnessPercent%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Volume HUD Indicator (Right half vertical drag)
        if (showVolumeIndicator) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val volumeRange = (volumeMax - volumeMin).coerceAtLeast(1)
                    val volumePercent = (((appliedVolume - volumeMin).toFloat() / volumeRange.toFloat()) * 100).roundToInt().coerceIn(0, 100)
                    val icon = when {
                        appliedVolume <= volumeMin -> Icons.AutoMirrored.Filled.VolumeMute
                        volumePercent < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    VerticalIndicatorBar(
                        fraction = (appliedVolume - volumeMin).toFloat() / volumeRange.toFloat(),
                        height = 96.dp,
                        width = 6.dp,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        progressColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "$volumePercent%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Side Seek HUD Indicator (Double-tap left -10s / right +10s feedback)
        sideSeekDirection?.let { direction ->
            val isBackward = direction == SideSeekDirection.BACKWARD
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(if (isBackward) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(start = if (isBackward) 48.dp else 0.dp, end = if (!isBackward) 48.dp else 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (isBackward) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                        contentDescription = if (isBackward) "Back 10 seconds" else "Forward 10 seconds",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isBackward) "-10s" else "+10s",
                        color = MaterialTheme.colorScheme.primary,
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
                        color = Color.White,
                        fontSize = 12.sp,
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
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
                        color = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
                                tint = if (state.audioTracks.size > 1) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }

                        IconButton(onClick = { activeDialog = "Subtitles" }) {
                            Icon(
                                imageVector = Icons.Filled.Subtitles,
                                contentDescription = "Subtitles",
                                tint = if (state.selectedSubtitleTrackIndex != -1) MaterialTheme.colorScheme.primary else Color.White,
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
                                tint = if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto") MaterialTheme.colorScheme.primary else Color.White,
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
                                    text = "Double-tap: -10s (L) / Play-Pause / +10s (R) • Swipe H: Seek • Swipe V: Brightness (L) / Volume (R)",
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
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
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
                                    .background(MaterialTheme.colorScheme.primary),
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { activeDialog = "Sleep Timer" },
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.player_sleep_timer_chip, timeFormatted),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.player_sleep_timer_cancel),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    playbackManager.cancelSleepTimer()
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.player_sleep_timer_canceled_toast),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                        )
                                    }
                                }
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
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            title = {
                Text(
                    text = "Select Audio Track",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (state.audioTracks.isEmpty()) {
                    Text(
                        text = "No discrete audio tracks available in this media stream (default audio output).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = track.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "Language: ${track.language}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Subtitles Selection Dialog (Real Media3 Subtitle Tracks & SAF External Loading)
    if (activeDialog == "Subtitles") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Subtitle Selection",
                        color = MaterialTheme.colorScheme.onSurface,
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
                            subtitleLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open Subtitle File (SRT / VTT)...",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Available Tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Off (Disable subtitles)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (state.selectedSubtitleTrackIndex == -1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    if (state.subtitleTracks.isEmpty()) {
                        Text(
                            text = "No subtitle tracks found in media. Use 'Open Subtitle File' above to load .srt or .vtt.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.label,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (track.isExternal) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = "External",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Language: ${track.language}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("Subtitle Text Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    // Subtitle Style Controls (from settings)
                    Text("Subtitle Style / Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Decoder Selection Dialog
    if (activeDialog == "Decoder") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Decoder Selection",
                        color = MaterialTheme.colorScheme.onSurface,
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
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (state.decoderMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = mode.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.primary,
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
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Unsupported Subtitle Format Dialog
    if (subtitleErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = { subtitleErrorDialogText = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { subtitleErrorDialogText = null }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Player Options Menu
    if (activeDialog == "Player Menu") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            title = {
                Text(
                    text = "Playback Settings",
                    color = MaterialTheme.colorScheme.onSurface,
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
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Decoder Mode (${state.decoderMode.label})", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Audio Tracks" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Audio Tracks (${state.audioTracks.size})", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Subtitles" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Subtitles (${if (state.selectedSubtitleTrackIndex == -1) "Off" else "Active"})", color = MaterialTheme.colorScheme.onSurface)
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
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Pop-up Play (PiP Mode)", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextBackgroundPlay = !currentSettings.backgroundPlay
                                val updated = currentSettings.copy(backgroundPlay = nextBackgroundPlay)
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                                if (!nextBackgroundPlay) {
                                    PlaybackService.stop(context)
                                    playbackManager.setVideoBackgroundActive(false)
                                    playbackManager.setBackgroundPlaying(false)
                                }
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
                            tint = if (currentSettings.backgroundPlay) MaterialTheme.colorScheme.primary else Color.Gray,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Background Play: ${if (currentSettings.backgroundPlay) "On" else "Off"}",
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (state.currentItem?.isVideo == true) {
                                    "Audio continues when app minimized; video rendering pauses"
                                } else {
                                    "Audio continues when screen is off or app minimized"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Playback Speed: ${state.speed}x", color = MaterialTheme.colorScheme.onSurface)
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
                        Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Aspect Ratio: ${state.aspectRatio.label}", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Screen Rotation" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        val rotationLabel = if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto") {
                            stringResource(R.string.player_rotation_auto)
                        } else if (currentSettings.screenOrientation.equals("portrait", ignoreCase = true)) {
                            stringResource(R.string.player_orientation_portrait)
                        } else {
                            stringResource(R.string.player_orientation_landscape)
                        }
                        Text("Screen Rotation ($rotationLabel)", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDialog = "Sleep Timer" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        val sleepTimerSubtext = if (state.sleepTimerRemainingSeconds != null) {
                            val remaining = state.sleepTimerRemainingSeconds ?: 0L
                            val hours = remaining / 3600
                            val mins = (remaining % 3600) / 60
                            val secs = remaining % 60
                            if (hours > 0) {
                                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
                            } else {
                                String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                            }
                        } else {
                            stringResource(R.string.player_sleep_timer_off)
                        }
                        Text("${stringResource(R.string.player_sleep_timer)} ($sleepTimerSubtext)", color = MaterialTheme.colorScheme.onSurface)
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
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Playlist...", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    if (activeDialog == "Screen Rotation") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_rotation_dialog_title),
                        color = MaterialTheme.colorScheme.onSurface,
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
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_rotation_auto), color = MaterialTheme.colorScheme.onSurface, fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_rotation_auto_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_landscape), color = MaterialTheme.colorScheme.onSurface, fontWeight = if (isLandscape) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_landscape_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_portrait), color = MaterialTheme.colorScheme.onSurface, fontWeight = if (isPortrait) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_portrait_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    if (activeDialog == "Sleep Timer") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_sleep_timer_dialog_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column {
                    val choices = listOf(
                        15 to stringResource(R.string.player_sleep_timer_15),
                        30 to stringResource(R.string.player_sleep_timer_30),
                        45 to stringResource(R.string.player_sleep_timer_45),
                        60 to stringResource(R.string.player_sleep_timer_60),
                        0 to stringResource(R.string.player_sleep_timer_off),
                    )

                    choices.forEach { (mins, label) ->
                        val isSelected = if (mins == 0) {
                            state.sleepTimerMinutes == null
                        } else {
                            state.sleepTimerMinutes == mins
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (mins == 0) {
                                        playbackManager.cancelSleepTimer()
                                        Toast.makeText(context, context.getString(R.string.player_sleep_timer_canceled_toast), Toast.LENGTH_SHORT).show()
                                    } else {
                                        playbackManager.setSleepTimer(mins)
                                        Toast.makeText(context, context.getString(R.string.player_sleep_timer_set_toast, mins), Toast.LENGTH_SHORT).show()
                                    }
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (mins == 0) {
                                        playbackManager.cancelSleepTimer()
                                        Toast.makeText(context, context.getString(R.string.player_sleep_timer_canceled_toast), Toast.LENGTH_SHORT).show()
                                    } else {
                                        playbackManager.setSleepTimer(mins)
                                        Toast.makeText(context, context.getString(R.string.player_sleep_timer_set_toast, mins), Toast.LENGTH_SHORT).show()
                                    }
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
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
