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
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitUpOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.withTimeoutOrNull
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
@OptIn(ExperimentalMaterial3Api::class)
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

    // Issue #149: after a process death in the background, Android restores
    // this destination but the PlaybackManager singleton is fresh with an
    // empty queue, which used to render the "No video queued" empty state.
    // Restore the last persisted queue instead (paused, at the saved
    // position). Deliberate stops clear the persisted queue, so this only
    // fires for sessions the user never ended; with nothing persisted it is
    // a no-op and the empty state stays.
    LaunchedEffect(Unit) {
        if (playbackManager.playbackState.value.queue.isEmpty()) {
            playbackManager.restorePersistedQueueIfEmpty()
        }
    }

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
    // Issue #153: the Player Menu dialog and the whole activeDialog chain
    // (Sleep Timer / Audio Tracks / Subtitles / Decoder / Screen Rotation)
    // are replaced by one MpxBottomSheet (PlayerPlaybackSheet). Null = closed.
    var playbackSheetPage by remember { mutableStateOf<PlaybackSheetPage?>(null) }
    val playbackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // First-run gesture tutorial (#151): shown once, the first time the
    // player opens with gestures enabled; replayable from the Playback sheet.
    var showTutorial by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val settings = realSettingsRepo.load()
        if (!settings.playerTutorialSeen && settings.gesturesEnabled) {
            showTutorial = true
        }
    }

    // Dismissing (Skip, X, or finishing) marks the tutorial seen, so it
    // never auto-shows again; the Playback sheet entry can still replay it.
    val dismissTutorial = {
        val updated = currentSettings.copy(playerTutorialSeen = true)
        currentSettings = updated
        realSettingsRepo.save(updated)
        showTutorial = false
    }

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

    // Long-press 2x state (issue #166): while the user holds the video
    // area, playback runs at 2x; releasing restores the previous speed.
    var isLongPress2x by remember { mutableStateOf(false) }

    // Double-tap side seek gesture state
    var sideSeekDirection by remember { mutableStateOf<SideSeekDirection?>(null) }
    var hideSideSeekJob by remember { mutableStateOf<Job?>(null) }

    // MX-style auto-hide (issue #85): MX's bottom controller in screen.xml
    // is visibility=gone by default and only appears on interaction. Mirror
    // that here: while playing, hide the chrome a few seconds after it
    // appears, so the video is not permanently covered. Stay visible while
    // paused, while scrubbing/dragging, or while a dialog is open, so no
    // interaction is ever cut off mid-use. Tap the video to bring it back.
    LaunchedEffect(controlsVisible, state.isPlaying, playbackSheetPage, isScrubbingSlider, isDraggingSeek, showBrightnessIndicator, showVolumeIndicator) {
        if (controlsVisible && state.isPlaying && playbackSheetPage == null && !isScrubbingSlider && !isDraggingSeek && !showBrightnessIndicator && !showVolumeIndicator) {
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

    // Shared drag-delta handling for the single gesture detector below
    // (issue #166): mode detection (horizontal seek vs left-vertical
    // brightness vs right-vertical volume) plus the per-mode delta math.
    // Kept in one place so the touch-slop change and every later drag
    // change go through exactly the same path. scopeWidth/scopeHeight are
    // the PointerInputScope size, passed in because this runs outside it.
    fun applyDragDelta(dragAmount: Offset, scopeWidth: Float, scopeHeight: Float) {
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
                    val isLeftHalf = dragStartOffset.x < scopeWidth / 2f
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
                val sensitivity = (scopeHeight * 0.75f).coerceAtLeast(200f)
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
                val sensitivity = (scopeHeight * 0.75f).coerceAtLeast(200f)
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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MpxTheme.colors.black)
            // One gesture detector for the whole video area (issue #166):
            // tap, double-tap, long-press 2x and drags share a single
            // pointerInput block. Two separate blocks (detectTapGestures +
            // detectDragGestures) cannot coexist on the same Box:
            // detectTapGestures consumes the initial down (down.consume()),
            // while detectDragGestures only accepts an UNCONSUMED down
            // (awaitFirstDown(requireUnconsumed = true)), so the two
            // detectors race for the same down — and on this screen the tap
            // detector wins, which is why no drag ever started and the
            // brightness/volume/seek previews never showed even though the
            // HUD code itself was correct. A single awaitEachGesture loop
            // removes the race: one down, one decision (tap / double-tap /
            // long-press 2x / drag).
            .pointerInput(state.isLocked, currentDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    dragStartOffset = down.position
                    // Per-gesture reset (was detectDragGestures' onDragStart).
                    hideSideSeekJob?.cancel()
                    sideSeekDirection = null
                    currentDragMode = DragGestureMode.NONE
                    totalDragX = 0f
                    totalDragY = 0f
                    accumulatedDragX = 0f
                    seekStartMs = state.currentPositionMs
                    seekTargetMs = seekStartMs
                    seekDeltaMs = 0L

                    if (state.isLocked) {
                        // Locked: only a tap may toggle the chrome.
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            controlsVisible = !controlsVisible
                            if (sideSeekDirection != null) {
                                hideSideSeekJob?.cancel()
                                sideSeekDirection = null
                            }
                        }
                        return@awaitEachGesture
                    }

                    // Race the three outcomes: touch slop (drag), finger up
                    // (tap / double-tap), long-press timeout (hold for 2x).
                    // awaitTouchSlopOrCancellation returns null both on up
                    // and on timeout, so tell them apart by whether the
                    // pointer is still down in the latest event.
                    val slopChange = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    }
                    if (slopChange == null) {
                        val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
                        if (stillDown && realSettingsRepo.load().gesturesEnabled) {
                            // Long-press 2x: hold at 2x until release, then
                            // restore the speed that was playing before.
                            // The finally also covers the detector restarting
                            // mid-hold, so 2x can never get stuck on.
                            val previousSpeed = state.speed
                            playbackManager.setSpeed(2f)
                            isLongPress2x = true
                            try {
                                awaitUpOrCancellation()
                            } finally {
                                playbackManager.setSpeed(previousSpeed)
                                isLongPress2x = false
                            }
                        } else {
                            // Tap candidate: wait briefly for a second tap.
                            val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                awaitFirstDown(requireUnconsumed = true)
                            }
                            if (secondDown == null) {
                                controlsVisible = !controlsVisible
                                if (sideSeekDirection != null) {
                                    hideSideSeekJob?.cancel()
                                    sideSeekDirection = null
                                }
                            } else {
                                waitForUpOrCancellation()
                                if (realSettingsRepo.load().gesturesEnabled) {
                                    val oneThird = size.width / 3f
                                    val twoThirds = size.width * (2f / 3f)
                                    when {
                                        secondDown.position.x < oneThird -> {
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
                                        secondDown.position.x > twoThirds -> {
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
                            }
                        }
                        return@awaitEachGesture
                    }

                    // Drag: capture the drag-start values (was onDragStart),
                    // then feed the slop change and every later move through
                    // the shared delta path.
                    dragStartBrightness = getScreenBrightness(context, activity)
                    dragStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                    volumeMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                        audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    } else {
                        0
                    }
                    volumeMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    if (!realSettingsRepo.load().gesturesEnabled) {
                        // Gestures off: ignore the drag entirely. (Before,
                        // the unconsumed moves fell through to the tap
                        // detector and a swipe toggled the chrome.)
                        return@awaitEachGesture
                    }
                    applyDragDelta(slopChange.positionChange(), size.width.toFloat(), size.height.toFloat())
                    val endedCleanly = awaitDragOrCancellation(down.id) { change, dragAmount ->
                        change.consume()
                        applyDragDelta(dragAmount, size.width.toFloat(), size.height.toFloat())
                    }
                    if (endedCleanly != null) {
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
                    } else {
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
                    }
                    currentDragMode = DragGestureMode.NONE
                }
            }
    ) {
        // Video Surface: mpv renders into a plain SurfaceView (issue #150:
        // Media3 PlayerView is gone with Media3).
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
                // Push subtitle style changes to a running mpv engine (issue #75),
                // plus audio delay + video zoom (issue #153).
                if (lastMpvSubtitleSettings[0] != currentSettings) {
                    playbackManager.applySubtitleSettings(currentSettings)
                    playbackManager.applyPlaybackTweaks(currentSettings)
                    lastMpvSubtitleSettings[0] = currentSettings
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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

        // Brightness HUD (Left half vertical drag) — slim MX-brightbar strip.
        // Shown on the RIGHT, opposite the dragging hand, so the finger never
        // covers the preview (issue #166). The gesture side itself is unchanged.
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
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            )
        }

        // Volume HUD (Right half vertical drag) — slim MX-brightbar strip.
        // Shown on the LEFT, opposite the dragging hand (issue #166).
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
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp),
            )
        }

        // Long-press 2x indicator — small centered pill while the hold
        // lasts (issue #166). Same gradient-pill language as the scrub HUD.
        if (!isInPipMode && isLongPress2x) {
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
                        // all live in the Playback sheet now, MX-overflow
                        // style, so the top bar is just back + title + decoder
                        // pill + menu. See PlayerPlaybackSheet.)
                        IconButton(onClick = { playbackSheetPage = PlaybackSheetPage.Main }) {
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
                        // Aspect and speed live in the Playback sheet now;
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
                        // (Speed lives in the Playback sheet now.)

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
                                        .clickable { playbackSheetPage = PlaybackSheetPage.SleepTimer },
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

    // Playback sheet (issue #153): one bottom sheet replacing the Player Menu
    // dialog and the whole activeDialog chain. Always dark (issue #152).
    playbackSheetPage?.let { sheetPage ->
        PlayerPlaybackSheet(
            page = sheetPage,
            onPageChange = { playbackSheetPage = it },
            state = state,
            currentSettings = currentSettings,
            onSettingsChanged = { updated ->
                currentSettings = updated
                realSettingsRepo.save(updated)
            },
            playbackManager = playbackManager,
            subtitleLauncher = subtitleLauncher,
            isFavourite = isFavourite,
            onToggleFavourite = {
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
                            ),
                        )
                    }
                }
            },
            onEnterPip = {
                val entered = PipUtils.enterPip(context)
                if (!entered) {
                    val msg = if (!PipUtils.isPipSupported(context)) {
                        context.getString(R.string.player_pip_unsupported)
                    } else {
                        context.getString(R.string.player_pip_unable)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onToggleBackgroundPlay = {
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
            },
            onReplayTutorial = {
                tutorialStep = 0
                showTutorial = true
            },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            sheetState = playbackSheetState,
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

    // First-run gesture tutorial overlay (#151): drawn last so it sits
    // above every other player layer, and independent of controlsVisible.
    // Hidden in PiP like every other overlay.
    if (showTutorial && !isInPipMode) {
        PlayerTutorialOverlay(
            step = tutorialStep,
            onStepChange = { tutorialStep = it },
            onDismiss = dismissTutorial,
        )
    }
}

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
private fun PlayerTutorialOverlay(
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
