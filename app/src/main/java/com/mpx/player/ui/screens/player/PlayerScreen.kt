package com.mpx.player.ui.screens.player
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
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
import com.mpx.player.ui.screens.settings.parsePlayerMenuLayout
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.RepeatMode
import com.mpx.player.playback.PlaybackService
import com.mpx.player.playback.resolveMedia3SubtitleStyle
import com.mpx.player.ui.components.StepperRow
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
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

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

// Slim gesture HUD modelled on MX's brightbar (issue #94): a narrow vertical
// strip — percent text on top, a slim 4dp bar, a small icon at the bottom —
// over a subtle vertical gradient. No heavy black box, no colored border.
@Composable
private fun GestureHud(
    percentText: String,
    fraction: Float,
    icon: ImageVector,
    iconDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(56.dp)
            .clip(MpxShapes.large)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MpxTheme.colors.black.copy(alpha = 0.45f),
                        MpxTheme.colors.black.copy(alpha = 0.65f),
                    ),
                ),
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                color = MpxTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            VerticalIndicatorBar(
                fraction = fraction,
                height = 92.dp,
                width = 4.dp,
                trackColor = MpxTheme.colors.textPrimary.copy(alpha = 0.25f),
                progressColor = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MpxTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Modern blur for player dialogs (issue #85): on Android 12+ the dialog
// window blurs whatever is behind it, so the dialog reads as frosted
// glass instead of a flat panel. On older Android the flag is a no-op
// and the dialog keeps its solid surface, so readability never depends
// on blur. Call this from inside a dialog's content (AlertDialog title
// or text block), where LocalView resolves to the dialog's own window.
@Composable
private fun PlayerDialogBackdropBlur() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.apply {
                    blurBehindRadius = (16 * view.resources.displayMetrics.density).toInt()
                }
            }
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
    // True while the activity is in system PiP mode: hide every overlay so
    // the PiP window shows video only (issue #98).
    isInPipMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    var currentSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    // Last subtitle settings pushed to the mpv engine. Plain holder (not state)
    // so pushing options does not schedule extra recompositions.
    val lastMpvSubtitleSettings = remember { arrayOfNulls<AppSettings>(1) }
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

    // Issue #92: leaving the player screen stops playback and clears the
    // mini-player preview — unless background play is explicitly enabled.
    // This fires for both the top-bar back action and system back, because
    // both pop this destination and dispose the screen. Rotation does NOT
    // trigger it: MainActivity declares orientation in configChanges, so
    // rotation recomposes without disposing. Settings are re-read at
    // dispose time, so toggling background play in the player menu and
    // then leaving is honoured. PiP is excluded as a guard (PiP entry
    // itself already requires background play). stop() is the established
    // full-stop path (same as the mini-player close button): it stops the
    // engine and service, clears currentItem (which hides the mini-player)
    // and the queue, and cancels a running sleep timer — which is moot
    // once playback has stopped. With background play on, nothing here
    // runs, so the timer and background playback continue untouched.
    DisposableEffect(Unit) {
        onDispose {
            val activityInPip = activity?.isInPictureInPictureMode == true
            if (!activityInPip && !realSettingsRepo.load().backgroundPlay) {
                playbackManager.stop()
            }
        }
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
                        Toast.makeText(context, context.getString(R.string.player_toast_subtitle_loaded, validation.displayName), Toast.LENGTH_SHORT).show()
                    }
                }
                is SubtitleValidationResult.Unsupported -> {
                    subtitleErrorDialogText = context.getString(R.string.player_subtitle_load_error, validation.fileName, validation.reason)
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

    // MX-style auto-hide (issue #85): MX's bottom controller in screen.xml
    // is visibility=gone by default and only appears on interaction. Mirror
    // that here: while playing, hide the chrome a few seconds after it
    // appears, so the video is not permanently covered. Stay visible while
    // paused, while scrubbing/dragging, or while a dialog is open, so no
    // interaction is ever cut off mid-use. Tap the video to bring it back.
    LaunchedEffect(controlsVisible, state.isPlaying, activeDialog, isScrubbingSlider, isDraggingSeek) {
        if (controlsVisible && state.isPlaying && activeDialog == null && !isScrubbingSlider && !isDraggingSeek) {
            delay(3000)
            controlsVisible = false
        }
    }

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
            .background(MpxTheme.colors.black)
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
                        if (!state.isLocked && realSettingsRepo.load().gesturesEnabled) {
                            val oneThird = size.width / 3f
                            val twoThirds = size.width * (2f / 3f)
                            when {
                                offset.x < oneThird -> {
                                    playbackManager.skipBackward(realSettingsRepo.load().doubleTapSeekSeconds * 1000L)
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
                                    playbackManager.skipForward(realSettingsRepo.load().doubleTapSeekSeconds * 1000L)
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
                    onDrag = drag@{ change, dragAmount ->
                        // Gestures toggle off: ignore drags entirely (do not even
                        // consume the change, so taps still reach the tap handler).
                        if (!realSettingsRepo.load().gesturesEnabled) return@drag
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

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                playbackManager.surfaceChanged(width, height)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                // SYNCHRONOUS barrier:
                                // Set vo=null and detach before surfaceDestroyed returns.
                                playbackManager.detachSurface()
                            }
                        })
                    }
                },
                update = {
                    // Push subtitle style changes to a running mpv engine (issue #75).
                    // Media3 subtitles are styled by the PlayerView update above instead.
                    if (!playbackManager.isMedia3 && lastMpvSubtitleSettings[0] != currentSettings) {
                        playbackManager.applySubtitleSettings(currentSettings)
                        lastMpvSubtitleSettings[0] = currentSettings
                    }
                },
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

        // MX-style loading splash (screen.xml loadingSplash/loading): while a
        // video is preparing, show a spinner + text over black instead of dead
        // 00:00 chrome. The full controls overlay stays hidden until READY.
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

        // Horizontal Scrubbing HUD — compact gradient pill, no heavy box
        if (!isInPipMode && isDraggingSeek) {
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sign = if (seekDeltaMs >= 0) "+" else "-"
                    val absSeconds = (kotlin.math.abs(seekDeltaMs) / 1000).toInt()
                    val deltaStr = String.format("%s%02d:%02d", sign, absSeconds / 60, absSeconds % 60)

                    Text(
                        text = stringResource(R.string.player_hud_scrub, deltaStr),
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatTimeMs(seekTargetMs)} / ${formatTimeMs(currentDuration)}",
                        color = MpxTheme.colors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Brightness HUD (Left half vertical drag) — slim MX-brightbar strip
        if (!isInPipMode && showBrightnessIndicator) {
            val brightnessPercent = (appliedBrightness * 100).roundToInt().coerceIn(0, 100)
            val icon = when {
                brightnessPercent < 33 -> Icons.Filled.BrightnessLow
                brightnessPercent < 67 -> Icons.Filled.BrightnessMedium
                else -> Icons.Filled.BrightnessHigh
            }
            GestureHud(
                percentText = "$brightnessPercent%",
                fraction = appliedBrightness,
                icon = icon,
                iconDescription = stringResource(R.string.player_cd_brightness),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp),
            )
        }

        // Volume HUD (Right half vertical drag) — slim MX-brightbar strip
        if (!isInPipMode && showVolumeIndicator) {
            val volumeRange = (volumeMax - volumeMin).coerceAtLeast(1)
            val volumePercent = (((appliedVolume - volumeMin).toFloat() / volumeRange.toFloat()) * 100).roundToInt().coerceIn(0, 100)
            val icon = when {
                appliedVolume <= volumeMin -> Icons.AutoMirrored.Filled.VolumeMute
                volumePercent < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            GestureHud(
                percentText = "$volumePercent%",
                fraction = (appliedVolume - volumeMin).toFloat() / volumeRange.toFloat(),
                icon = icon,
                iconDescription = stringResource(R.string.player_cd_volume),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            )
        }

        // Side Seek HUD (Double-tap left -10s / right +10s feedback) —
        // same slim gradient-strip language as the volume/brightness HUDs
        if (!isInPipMode) sideSeekDirection?.let { direction ->
            val isBackward = direction == SideSeekDirection.BACKWARD
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (isBackward) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                        contentDescription = if (isBackward) stringResource(R.string.player_cd_seek_back) else stringResource(R.string.player_cd_seek_forward),
                        tint = MpxTheme.colors.textPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isBackward) "-10s" else "+10s",
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

        // In-memory Resume Notice Banner (theme surface, no heavy box)
        if (!isInPipMode) state.resumedFromMs?.let { resumeMs ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
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
                            playbackManager.seekTo(0L)
                            playbackManager.clearResumedNotification()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { playbackManager.clearResumedNotification() },
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

        // Platform Decoder Fallback Notice Banner (theme surface, no orange box)
        if (!isInPipMode) state.decoderFallbackNote?.let { fallbackMsg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (state.resumedFromMs != null) 124.dp else 72.dp, start = 16.dp, end = 16.dp),
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
                        onClick = { playbackManager.clearDecoderFallbackNote() },
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

        // Honest User Notification Banner (theme surface, compact)
        if (!isInPipMode) state.userNotification?.let { userMsg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = if (state.decoderFallbackNote != null) 160.dp else if (state.resumedFromMs != null) 124.dp else 72.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
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
                    // One-tap engine switch for codec failures on Media3 (issue #82/#83).
                    // Deliberately inside the existing banner, not a popup on playback.
                    if (state.showMpvSwitchOffer) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_try_mpv),
                            color = MpxTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                playbackManager.setPlayerEngine("MPV")
                                playbackManager.clearUserNotification()
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { playbackManager.clearUserNotification() },
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

        // Single floating unlock button when screen is locked
        // (hidden in PiP: the window shows video only)
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
                    onClick = { playbackManager.setLocked(false) },
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
            return@Box
        }

        // Full Controls Overlay (hidden in PiP: the window shows video only;
        // hidden while preparing: MX's controller is gone while its loading
        // splash shows).
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode && !state.isPreparing,
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
                                colors = listOf(MpxTheme.colors.black.copy(alpha = 0.75f), Color.Transparent),
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
                                contentDescription = stringResource(R.string.player_cd_back),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Text(
                            text = state.currentItem?.title ?: "MPx Player",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                            color = MpxTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(8.dp))

                        // Decoder Pill Badge: configured mode + real active codec
                        // (e.g. "HW • H.264") once the engine reports a decoder name;
                        // falls back to the mode label alone while initializing.
                        val decoderPillText = state.activeDecoderName?.let { name ->
                            val lower = name.lowercase()
                            val codec = when {
                                "avc" in lower || "h264" in lower -> "H.264"
                                "hevc" in lower || "h265" in lower -> "H.265"
                                "vp9" in lower -> "VP9"
                                "av1" in lower -> "AV1"
                                "vp8" in lower -> "VP8"
                                "mp4v" in lower || "mpeg4" in lower -> "MPEG-4"
                                else -> name.substringAfterLast('.').take(12)
                            }
                            "${state.decoderMode.label} • $codec"
                        } ?: state.decoderMode.label
                        Surface(
                            modifier = Modifier
                                .clip(MpxShapes.small)
                                .clickable {
                                    val nextDecoder = when (state.decoderMode) {
                                        DecoderMode.HW -> DecoderMode.HW_PLUS
                                        DecoderMode.HW_PLUS -> DecoderMode.SW
                                        DecoderMode.SW -> DecoderMode.HW
                                    }
                                    playbackManager.setDecoderMode(nextDecoder)
                                    realSettingsRepo.save(realSettingsRepo.load().copy(decoderMode = nextDecoder.label))
                                    currentSettings = realSettingsRepo.load()
                                    Toast.makeText(context, context.getString(R.string.player_toast_decoder, context.getString(nextDecoder.titleRes)), Toast.LENGTH_SHORT).show()
                                },
                            color = Color.Transparent,
                            shape = MpxShapes.small,
                        ) {
                            Text(
                                text = decoderPillText,
                                color = MpxTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        // (Favourite, audio tracks, subtitles, PiP and rotation
                        // all live in the Player Menu dialog now, MX-overflow
                        // style, so the top bar is just back + title + decoder
                        // pill + menu. See the Player Menu rows below.)
                        IconButton(onClick = { activeDialog = "Player Menu" }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.player_cd_more),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    }
                }

                // (Screen lock lives in the transport row now, like MX's
                // subnavibar lock, instead of floating mid-video.)
                // Bottom Overlay Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MpxTheme.colors.black.copy(alpha = 0.8f)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Gesture hint banner (hidden when gestures are off,
                        // since the legend would describe disabled controls)
                        if (showGestureHint && realSettingsRepo.load().gesturesEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MpxShapes.small)
                                    .background(MpxTheme.colors.textPrimary.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.player_gesture_hint),
                                    color = MpxTheme.colors.textPrimary.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                )
                                IconButton(
                                    onClick = { showGestureHint = false },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.player_cd_dismiss_hint),
                                        tint = MpxTheme.colors.textPrimary.copy(alpha = 0.7f),
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
                                color = MpxTheme.colors.textPrimary,
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

                        // Controls Row: Lock, Prev, 10s Replay, Play/Pause Hero,
                        // 10s Forward, Next — MX's subnavibar transport set.
                        // Aspect and speed live in the Player Menu dialog now;
                        // shuffle/repeat moved there too (rows below).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Screen lock, in-row instead of floating mid-video.
                            // MX-faithful transparent button (issue #94): no
                            // glass fill, no border — white icon on the scrim.
                            IconButton(
                                onClick = { playbackManager.setLocked(true) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = stringResource(R.string.player_cd_lock),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Previous Track
                            IconButton(
                                onClick = { playbackManager.skipPrevious() },
                                enabled = state.hasPrevious,
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_cd_previous),
                                    tint = if (state.hasPrevious) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Replay 10s
                            IconButton(
                                onClick = { playbackManager.skipBackward(10_000L) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = stringResource(R.string.player_cd_seek_back),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Hero Play/Pause Button
                            // MX-faithful: transparent play/pause, larger white
                            // icon instead of a filled hero circle.
                            IconButton(
                                onClick = { playbackManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) stringResource(R.string.player_cd_pause) else stringResource(R.string.player_cd_play),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { playbackManager.skipForward(10_000L) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = stringResource(R.string.player_cd_seek_forward),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Next Track
                            IconButton(
                                onClick = { playbackManager.skipNext() },
                                enabled = state.hasNext,
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.player_cd_next),
                                    tint = if (state.hasNext) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                        }
                        // (Speed lives in the Player Menu dialog now.)

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
                                        .clickable { activeDialog = "Sleep Timer" },
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
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.player_sleep_timer_cancel),
                                            tint = MpxTheme.colors.textPrimary,
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
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                PlayerDialogBackdropBlur()
                Text(
                    text = stringResource(R.string.player_dialog_audio_tracks),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (state.audioTracks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_audio_no_tracks),
                        color = MpxTheme.colors.textSecondary,
                    )
                } else {
                    LazyColumn {
                        items(state.audioTracks, key = { "${it.groupIndex}_${it.trackIndex}" }) { track ->
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
                                    colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = track.label,
                                        color = MpxTheme.colors.textPrimary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = stringResource(R.string.player_track_language, track.language),
                                        color = MpxTheme.colors.textSecondary,
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
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Subtitles Selection Dialog (Real Media3 Subtitle Tracks & SAF External Loading)
    if (activeDialog == "Subtitles") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                PlayerDialogBackdropBlur()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_dialog_subtitle_selection),
                        color = MpxTheme.colors.textPrimary,
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
                        border = BorderStroke(1.dp, MpxTheme.colors.primary),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = MpxTheme.colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_subtitle_open_file),
                            color = MpxTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.player_subtitle_available_tracks),
                        style = MaterialTheme.typography.labelMedium,
                        color = MpxTheme.colors.textSecondary,
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
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_subtitle_off),
                            color = MpxTheme.colors.textPrimary,
                            fontWeight = if (state.selectedSubtitleTrackIndex == -1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    if (state.subtitleTracks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.player_subtitle_no_tracks),
                            color = MpxTheme.colors.textSecondary,
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
                                    colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.label,
                                            color = MpxTheme.colors.textPrimary,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (track.isExternal) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MpxTheme.colors.primary.copy(alpha = 0.2f),
                                                shape = MpxShapes.extraSmall,
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.player_subtitle_external),
                                                    color = MpxTheme.colors.primary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.player_track_language, track.language),
                                        color = MpxTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
                    Spacer(Modifier.height(8.dp))

                    // Subtitle Size Controls (from settings)
                    Text(stringResource(R.string.player_subtitle_text_size), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "Small" to R.string.player_subtitle_size_small,
                            "Normal" to R.string.player_subtitle_size_normal,
                            "Large" to R.string.player_subtitle_size_large,
                        ).forEach { (size, sizeRes) ->
                            val isSelected = currentSettings.subtitleSize.equals(size, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = currentSettings.copy(subtitleSize = size)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                },
                                label = { Text(stringResource(sizeRes), fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MpxTheme.colors.primary,
                                    selectedLabelColor = MpxTheme.colors.textPrimary,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    // Subtitle timing (issue #79, MX menu_subtitle sync/speed):
                    // persisted; applied live on mpv only (see mpv note below).
                    run {
                        val delayMs = currentSettings.subtitleDelayMs
                        val delayText = if (delayMs == 0) "0.00s" else "%+.2fs".format(delayMs / 1000.0)
                        StepperRow(
                            label = stringResource(R.string.subtitle_sync_header),
                            valueText = delayText,
                            minusContentDescription = stringResource(R.string.subtitle_sync_header),
                            plusContentDescription = stringResource(R.string.subtitle_sync_header),
                            onMinus = {
                                val updated = currentSettings.copy(subtitleDelayMs = (delayMs - 100).coerceIn(-10000, 10000))
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                            },
                            onPlus = {
                                val updated = currentSettings.copy(subtitleDelayMs = (delayMs + 100).coerceIn(-10000, 10000))
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                            },
                            onReset = if (delayMs != 0) {
                                {
                                    val updated = currentSettings.copy(subtitleDelayMs = 0)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                }
                            } else null,
                        )
                        val speed = currentSettings.subtitleSpeed
                        StepperRow(
                            label = stringResource(R.string.subtitle_speed_header),
                            valueText = "%.1f×".format(speed),
                            minusContentDescription = stringResource(R.string.subtitle_speed_header),
                            plusContentDescription = stringResource(R.string.subtitle_speed_header),
                            onMinus = {
                                val stepped = ((speed * 10).roundToInt() - 1) / 10f
                                val updated = currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f))
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                            },
                            onPlus = {
                                val stepped = ((speed * 10).roundToInt() + 1) / 10f
                                val updated = currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f))
                                currentSettings = updated
                                realSettingsRepo.save(updated)
                            },
                            onReset = if (speed != 1.0f) {
                                {
                                    val updated = currentSettings.copy(subtitleSpeed = 1.0f)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                }
                            } else null,
                        )
                        Text(
                            stringResource(R.string.subtitle_timing_mpv_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    // Subtitle Style Controls (from settings)
                    Text(stringResource(R.string.player_subtitle_style_color), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "White" to R.string.player_subtitle_style_white,
                            "Yellow" to R.string.player_subtitle_style_yellow,
                            "Cyan" to R.string.player_subtitle_style_cyan,
                            "Default" to R.string.player_subtitle_style_default,
                        ).forEach { (styleKey, styleRes) ->
                            val isSelected = currentSettings.subtitleStyle.equals(styleKey, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = currentSettings.copy(subtitleStyle = styleKey)
                                    currentSettings = updated
                                    realSettingsRepo.save(updated)
                                },
                                label = { Text(stringResource(styleRes), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MpxTheme.colors.primary,
                                    selectedLabelColor = MpxTheme.colors.textPrimary,
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Decoder Selection Dialog
    if (activeDialog == "Decoder") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                PlayerDialogBackdropBlur()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_dialog_decoder_selection),
                        color = MpxTheme.colors.textPrimary,
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
                                colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    color = MpxTheme.colors.textPrimary,
                                    fontWeight = if (state.decoderMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = stringResource(mode.descriptionRes),
                                    color = MpxTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.player_active_codec, state.activeDecoderName ?: stringResource(R.string.player_codec_initializing)),
                        color = MpxTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    state.decoderFallbackNote?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0x33FFB74D),
                            shape = MpxShapes.small,
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
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Unsupported Subtitle Format Dialog
    if (subtitleErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = { subtitleErrorDialogText = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                Text(
                    text = stringResource(R.string.player_dialog_unsupported_subtitle),
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = subtitleErrorDialogText!!,
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { subtitleErrorDialogText = null }) {
                    Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Player Options Menu
    if (activeDialog == "Player Menu") {
        // #158: Player Menu rows, keyed by layout id; rendered in the
        // persisted Player Layout order (Preferences > Player Layout).
        // Each row's code is unchanged from before the refactor.
        val menuRows: Map<String, @Composable () -> Unit> = mapOf(
            "decoder" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeDialog = "Decoder" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_decoder_mode, state.decoderMode.label), color = MpxTheme.colors.textPrimary)
                }
            },
            "audio" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeDialog = "Audio Tracks" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_audio_tracks, state.audioTracks.size), color = MpxTheme.colors.textPrimary)
                }
            },
            "subtitles" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeDialog = "Subtitles" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_subtitles, if (state.selectedSubtitleTrackIndex == -1) stringResource(R.string.player_state_off) else stringResource(R.string.player_state_on)), color = MpxTheme.colors.textPrimary)
                }
            },
            "favourite" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
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
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavourite) Color(0xFFFF5252) else MpxTheme.colors.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isFavourite) stringResource(R.string.player_cd_favourite_remove) else stringResource(R.string.player_cd_favourite_add),
                        color = MpxTheme.colors.textPrimary,
                    )
                }
            },
            "shuffle" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playbackManager.setShuffleEnabled(!state.shuffleEnabled) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = if (state.shuffleEnabled) MpxTheme.colors.primary else Color.Gray,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${stringResource(R.string.player_cd_shuffle)} (${if (state.shuffleEnabled) stringResource(R.string.player_state_on) else stringResource(R.string.player_state_off)})",
                        color = MpxTheme.colors.textPrimary,
                    )
                }
            },
            "repeat" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playbackManager.cycleRepeatMode() }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = if (state.repeatMode == RepeatMode.OFF) Color.Gray else MpxTheme.colors.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (state.repeatMode) {
                            RepeatMode.ONE -> stringResource(R.string.player_cd_repeat_one)
                            RepeatMode.ALL -> stringResource(R.string.player_cd_repeat_all)
                            else -> stringResource(R.string.player_cd_repeat_off)
                        },
                        color = MpxTheme.colors.textPrimary,
                    )
                }
            },
            "pip" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activeDialog = null
                            val entered = PipUtils.enterPip(context)
                            if (!entered) {
                                val msg = if (!PipUtils.isPipSupported(context)) {
                                    context.getString(R.string.player_pip_unsupported)
                                } else {
                                    context.getString(R.string.player_pip_unable)
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_popup_play), color = MpxTheme.colors.textPrimary)
                }
            },
            "background" to {
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
                                    context.getString(R.string.player_bg_enabled_video)
                                } else {
                                    context.getString(R.string.player_bg_enabled_audio)
                                }
                            } else {
                                context.getString(R.string.player_bg_disabled)
                            }
                            Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (currentSettings.backgroundPlay) MpxTheme.colors.primary else Color.Gray,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.player_menu_background_play_row, if (currentSettings.backgroundPlay) stringResource(R.string.player_state_on) else stringResource(R.string.player_state_off)),
                            color = MpxTheme.colors.textPrimary,
                        )
                        Text(
                            if (state.currentItem?.isVideo == true) {
                                stringResource(R.string.player_bg_desc_video)
                            } else {
                                stringResource(R.string.player_bg_desc_audio)
                            },
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            },
            "speed" to {
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
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_speed, "${state.speed}x"), color = MpxTheme.colors.textPrimary)
                }
            },
            "aspect" to {
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
                    Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_aspect, stringResource(state.aspectRatio.labelRes)), color = MpxTheme.colors.textPrimary)
                }
            },
            "rotation" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeDialog = "Screen Rotation" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    val rotationLabel = if (currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto") {
                        stringResource(R.string.player_rotation_auto)
                    } else if (currentSettings.screenOrientation.equals("portrait", ignoreCase = true)) {
                        stringResource(R.string.player_orientation_portrait)
                    } else {
                        stringResource(R.string.player_orientation_landscape)
                    }
                    Text(stringResource(R.string.player_menu_rotation, rotationLabel), color = MpxTheme.colors.textPrimary)
                }
            },
            "sleep" to {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeDialog = "Sleep Timer" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MpxTheme.colors.primary)
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
                    Text("${stringResource(R.string.player_sleep_timer)} ($sleepTimerSubtext)", color = MpxTheme.colors.textPrimary)
                }
            },
            "playlist" to {
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
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_menu_add_playlist), color = MpxTheme.colors.textPrimary)
                }
            },
        )
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                PlayerDialogBackdropBlur()
                Text(
                    text = stringResource(R.string.player_menu_title),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    parsePlayerMenuLayout(realSettingsRepo.load().playerMenuLayout).forEach { id ->
                        menuRows[id]?.invoke()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    if (activeDialog == "Screen Rotation") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxTheme.colors.black,
            title = {
                PlayerDialogBackdropBlur()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_rotation_dialog_title),
                        color = MpxTheme.colors.textPrimary,
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
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_rotation_auto), color = MpxTheme.colors.textPrimary, fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_rotation_auto_desc), color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
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
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_landscape), color = MpxTheme.colors.textPrimary, fontWeight = if (isLandscape) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_landscape_desc), color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
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
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.player_orientation_portrait), color = MpxTheme.colors.textPrimary, fontWeight = if (isPortrait) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.player_orientation_portrait_desc), color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    if (activeDialog == "Sleep Timer") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = MpxTheme.colors.black,
            title = {
                PlayerDialogBackdropBlur()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MpxTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_sleep_timer_dialog_title),
                        color = MpxTheme.colors.textPrimary,
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
                                colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = MpxTheme.colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.primary)
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
    val resolved = resolveMedia3SubtitleStyle(settings)
    subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, resolved.textSizeSp)
    if (resolved.useSystemDefaultStyle) {
        subtitleView.setUserDefaultStyle()
    } else {
        subtitleView.setStyle(
            CaptionStyleCompat(
                resolved.foregroundColor,
                resolved.backgroundColor,
                android.graphics.Color.TRANSPARENT,
                resolved.edgeType,
                resolved.edgeColor,
                resolved.typeface,
            )
        )
    }
    subtitleView.setBottomPaddingFraction(resolved.bottomPaddingFraction)
    subtitleView.setApplyEmbeddedStyles(true)
}
