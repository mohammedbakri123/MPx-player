package com.mpx.player.ui.screens.player.state

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

enum class DragGestureMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
}

enum class SideSeekDirection {
    BACKWARD,
    FORWARD,
}

fun getScreenBrightness(context: Context, activity: Activity?): Float {
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

fun setScreenBrightness(activity: Activity?, brightness: Float): Float {
    val target = brightness.coerceIn(0f, 1f)
    activity?.window?.let { window ->
        val lp = window.attributes
        lp.screenBrightness = target
        window.attributes = lp
    }
    return activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: target
}

fun setMediaVolume(audioManager: AudioManager?, targetVolume: Int, minVolume: Int, maxVolume: Int): Int {
    if (audioManager == null) return 0
    val clamped = targetVolume.coerceIn(minVolume, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
}

fun formatMultiplier(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    return rounded.toString().removeSuffix(".0") + "×"
}

/**
 * State holder for player touch, drag, and pinch gestures (§6).
 * Manages per-gesture resets, delta math, and indicator auto-hide jobs.
 */
class PlayerGestureState(
    initialBrightness: Float,
    initialVolume: Int,
    initialMinVolume: Int,
    initialMaxVolume: Int,
) {
    var isDraggingSeek by mutableStateOf(false)
    var seekStartMs by mutableLongStateOf(0L)
    var seekTargetMs by mutableLongStateOf(0L)
    var seekDeltaMs by mutableLongStateOf(0L)
    var wasPlayingBeforeSeekDrag by mutableStateOf(false)
    var accumulatedDragX by mutableFloatStateOf(0f)

    var currentDragMode by mutableStateOf(DragGestureMode.NONE)
    var dragStartOffset by mutableStateOf(Offset.Zero)
    var totalDragX by mutableFloatStateOf(0f)
    var totalDragY by mutableFloatStateOf(0f)

    var showBrightnessIndicator by mutableStateOf(false)
    var appliedBrightness by mutableFloatStateOf(initialBrightness)
    var dragStartBrightness by mutableFloatStateOf(0.5f)
    var hideBrightnessJob by mutableStateOf<Job?>(null)

    var showVolumeIndicator by mutableStateOf(false)
    var appliedVolume by mutableIntStateOf(initialVolume)
    var dragStartVolume by mutableIntStateOf(0)
    var volumeMin by mutableIntStateOf(initialMinVolume)
    var volumeMax by mutableIntStateOf(initialMaxVolume)
    var hideVolumeJob by mutableStateOf<Job?>(null)

    var isLongPress2x by mutableStateOf(false)

    var showZoomIndicator by mutableStateOf(false)
    var zoomDisplayText by mutableStateOf("")
    var hideZoomJob by mutableStateOf<Job?>(null)
    var isPinching by mutableStateOf(false)
    var pinchStartDistance by mutableFloatStateOf(0f)
    var pinchStartZoom by mutableFloatStateOf(1f)
    var pinchCurrentZoom by mutableFloatStateOf(1f)

    var sideSeekDirection by mutableStateOf<SideSeekDirection?>(null)
    var hideSideSeekJob by mutableStateOf<Job?>(null)

    fun resumeAfterSeekDrag(onPlay: () -> Unit) {
        if (wasPlayingBeforeSeekDrag) {
            wasPlayingBeforeSeekDrag = false
            onPlay()
        }
    }

    fun applyDragDelta(
        dragAmount: Offset,
        scopeWidth: Float,
        scopeHeight: Float,
        currentDuration: Long,
        isPlaying: Boolean,
        currentPositionMs: Long,
        onPause: () -> Unit,
        activity: Activity?,
        audioManager: AudioManager?,
    ) {
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
                        if (isPlaying) {
                            wasPlayingBeforeSeekDrag = true
                            onPause()
                        }
                        seekStartMs = currentPositionMs
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
}

@Composable
fun rememberPlayerGestureState(
    context: Context,
    activity: Activity?,
    audioManager: AudioManager?,
): PlayerGestureState {
    val initialBrightness = remember { getScreenBrightness(context, activity) }
    val initialVolume = remember { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 }
    val initialMinVolume = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
    }
    val initialMaxVolume = remember {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }
    return remember {
        PlayerGestureState(
            initialBrightness = initialBrightness,
            initialVolume = initialVolume,
            initialMinVolume = initialMinVolume,
            initialMaxVolume = initialMaxVolume,
        )
    }
}
