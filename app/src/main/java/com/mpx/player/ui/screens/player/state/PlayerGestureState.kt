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
import kotlin.math.abs
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

enum class PlayerTapZone {
    LEFT,
    CENTER,
    RIGHT,
}

internal object PlayerGestureThresholds {
    const val DRAG_EDGE_MARGIN_DP = 24f
    const val TAP_EDGE_MARGIN_DP = 12f
    const val VERTICAL_SLOP_MIN_DP = 12f
    const val HORIZONTAL_SEEK_SLOP_MIN_DP = 24f
    const val DOUBLE_TAP_SLOP_DP = 100f
    const val HORIZONTAL_DOMINANCE_RATIO = 1.5f
    const val VERTICAL_DOMINANCE_RATIO = 1.25f
    const val HORIZONTAL_SEEK_MS_PER_DP = 200f
    const val MIN_COMMIT_SEEK_DELTA_MS = 250L
}

fun resolvePlayerTapZone(x: Float, width: Float): PlayerTapZone {
    if (width <= 0f) return PlayerTapZone.CENTER
    val oneThird = width / 3f
    val twoThirds = width * (2f / 3f)
    return when {
        x < oneThird -> PlayerTapZone.LEFT
        x > twoThirds -> PlayerTapZone.RIGHT
        else -> PlayerTapZone.CENTER
    }
}

fun isInEdgeExclusionZone(
    offset: Offset,
    width: Float,
    height: Float,
    edgeMarginPx: Float,
): Boolean {
    if (width <= 0f || height <= 0f) return true
    if (edgeMarginPx <= 0f) return false
    return offset.x < edgeMarginPx ||
        offset.x > width - edgeMarginPx ||
        offset.y < edgeMarginPx ||
        offset.y > height - edgeMarginPx
}

fun isValidDoubleTapPair(
    firstPos: Offset,
    secondPos: Offset,
    width: Float,
    height: Float,
    edgeMarginPx: Float,
    doubleTapSlopPx: Float,
): Boolean {
    if (width <= 0f || height <= 0f) return false
    if (isInEdgeExclusionZone(firstPos, width, height, edgeMarginPx)) return false
    if (isInEdgeExclusionZone(secondPos, width, height, edgeMarginPx)) return false
    if (resolvePlayerTapZone(firstPos.x, width) != resolvePlayerTapZone(secondPos.x, width)) {
        return false
    }
    return (secondPos - firstPos).getDistance() <= doubleTapSlopPx
}

/**
 * Classifies an un-locked drag from its total displacement relative to the
 * touch-down anchor, preventing single-packet jitter and accidental diagonal
 * brushes from locking horizontal seek.
 */
fun classifyDragGestureMode(
    totalOffset: Offset,
    startOffset: Offset,
    scopeWidth: Float,
    canHorizontalSeek: Boolean,
    verticalSlopPx: Float,
    horizontalSeekSlopPx: Float,
): DragGestureMode {
    val absX = abs(totalOffset.x)
    val absY = abs(totalOffset.y)
    if (canHorizontalSeek &&
        absX > horizontalSeekSlopPx &&
        absX > absY * PlayerGestureThresholds.HORIZONTAL_DOMINANCE_RATIO
    ) {
        return DragGestureMode.HORIZONTAL_SEEK
    }
    if (absY > verticalSlopPx &&
        absY > absX * PlayerGestureThresholds.VERTICAL_DOMINANCE_RATIO
    ) {
        return if (startOffset.x < scopeWidth / 2f) {
            DragGestureMode.VERTICAL_BRIGHTNESS
        } else {
            DragGestureMode.VERTICAL_VOLUME
        }
    }
    return DragGestureMode.NONE
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
    var sideSeekDeltaMs by mutableLongStateOf(0L)
    var sideSeekTargetMs by mutableLongStateOf(0L)
    var hideSideSeekJob by mutableStateOf<Job?>(null)

    fun resumeAfterSeekDrag(onPlay: () -> Unit) {
        if (wasPlayingBeforeSeekDrag) {
            wasPlayingBeforeSeekDrag = false
            onPlay()
        }
    }

    fun clearSideSeek() {
        hideSideSeekJob?.cancel()
        sideSeekDirection = null
        sideSeekDeltaMs = 0L
        sideSeekTargetMs = 0L
    }

    fun recordSideSeek(
        direction: SideSeekDirection,
        stepDeltaMs: Long,
        targetMs: Long,
        isChained: Boolean,
    ) {
        sideSeekDirection = direction
        sideSeekDeltaMs = if (isChained) sideSeekDeltaMs + stepDeltaMs else stepDeltaMs
        sideSeekTargetMs = targetMs
    }

    fun tryLockDragMode(
        totalOffset: Offset,
        scopeWidth: Float,
        currentDuration: Long,
        isPreparing: Boolean,
        startedInEdgeZone: Boolean,
        isPlaying: Boolean,
        currentPositionMs: Long,
        verticalSlopPx: Float,
        horizontalSeekSlopPx: Float,
        onPause: () -> Unit,
    ): DragGestureMode {
        if (currentDragMode != DragGestureMode.NONE) return currentDragMode
        val canHorizontalSeek = !startedInEdgeZone && currentDuration > 0L && !isPreparing
        val candidate = if (startedInEdgeZone) {
            DragGestureMode.NONE
        } else {
            classifyDragGestureMode(
                totalOffset = totalOffset,
                startOffset = dragStartOffset,
                scopeWidth = scopeWidth,
                canHorizontalSeek = canHorizontalSeek,
                verticalSlopPx = verticalSlopPx,
                horizontalSeekSlopPx = horizontalSeekSlopPx,
            )
        }
        when (candidate) {
            DragGestureMode.HORIZONTAL_SEEK -> {
                val safeDuration = currentDuration.coerceAtLeast(0L)
                val anchor = currentPositionMs.coerceIn(0L, safeDuration)
                currentDragMode = DragGestureMode.HORIZONTAL_SEEK
                isDraggingSeek = true
                totalDragX = 0f
                seekStartMs = anchor
                seekTargetMs = anchor
                seekDeltaMs = 0L
                if (isPlaying) {
                    wasPlayingBeforeSeekDrag = true
                    onPause()
                }
            }
            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                currentDragMode = DragGestureMode.VERTICAL_BRIGHTNESS
                totalDragY = 0f
                hideBrightnessJob?.cancel()
                showBrightnessIndicator = true
            }
            DragGestureMode.VERTICAL_VOLUME -> {
                currentDragMode = DragGestureMode.VERTICAL_VOLUME
                totalDragY = 0f
                hideVolumeJob?.cancel()
                showVolumeIndicator = true
            }
            DragGestureMode.NONE -> {}
        }
        return candidate
    }

    fun applyLockedDragDelta(
        dragAmount: Offset,
        scopeHeight: Float,
        density: Float,
        currentDuration: Long,
        activity: Activity?,
        audioManager: AudioManager?,
    ) {
        when (currentDragMode) {
            DragGestureMode.HORIZONTAL_SEEK -> {
                val safeDuration = currentDuration.coerceAtLeast(0L)
                val safeDensity = density.coerceAtLeast(0.5f)
                val minTotalX = -(seekStartMs.toFloat() / PlayerGestureThresholds.HORIZONTAL_SEEK_MS_PER_DP) * safeDensity
                val maxTotalX = ((safeDuration - seekStartMs).toFloat() / PlayerGestureThresholds.HORIZONTAL_SEEK_MS_PER_DP) * safeDensity
                totalDragX = (totalDragX + dragAmount.x).coerceIn(minTotalX, maxTotalX)
                val delta = ((totalDragX / safeDensity) * PlayerGestureThresholds.HORIZONTAL_SEEK_MS_PER_DP).toLong()
                val target = (seekStartMs + delta).coerceIn(0L, safeDuration)
                seekTargetMs = target
                seekDeltaMs = target - seekStartMs
            }
            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                val sensitivity = (scopeHeight * 0.75f).coerceAtLeast(200f)
                totalDragY = (totalDragY + dragAmount.y).coerceIn(
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
                totalDragY = (totalDragY + dragAmount.y).coerceIn(minTotalY, maxTotalY)
                val deltaSteps = (-totalDragY / sensitivity * volumeRange).toInt()
                val target = (dragStartVolume + deltaSteps).coerceIn(volumeMin, volumeMax)
                appliedVolume = setMediaVolume(audioManager, target, volumeMin, volumeMax)
                hideVolumeJob?.cancel()
                showVolumeIndicator = true
            }
            DragGestureMode.NONE -> {}
        }
    }

    fun shouldCommitSeek(): Boolean {
        if (!isDraggingSeek) return false
        if (seekTargetMs == 0L && seekStartMs > 0L) return true
        return abs(seekTargetMs - seekStartMs) >= PlayerGestureThresholds.MIN_COMMIT_SEEK_DELTA_MS
    }

    fun cancelActiveDrag(
        allowResume: Boolean,
        onResumePlay: () -> Unit,
    ) {
        if (isDraggingSeek) {
            isDraggingSeek = false
            if (allowResume) {
                resumeAfterSeekDrag(onResumePlay)
            } else {
                wasPlayingBeforeSeekDrag = false
            }
        }
        isPinching = false
        currentDragMode = DragGestureMode.NONE
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
