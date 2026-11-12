package com.mpx.player.ui.screens.player.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.player.state.DragGestureMode
import com.mpx.player.ui.screens.player.state.PlayerGestureState
import com.mpx.player.ui.screens.player.state.SideSeekDirection
import com.mpx.player.ui.screens.player.state.formatMultiplier
import com.mpx.player.ui.screens.player.state.getScreenBrightness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single gesture detector for the video area (issue #166).
 *
 * NOTE: Keys [isLocked] and [currentDuration] MUST BE PRESERVED:
 * changing keys resets in-flight gestures.
 */
fun Modifier.playerGestureDetector(
    isLocked: Boolean,
    currentDuration: Long,
    state: PlaybackState,
    currentSettings: AppSettings,
    gestureState: PlayerGestureState,
    viewModel: PlayerViewModel,
    scope: CoroutineScope,
    context: Context,
    activity: Activity?,
    audioManager: AudioManager?,
    onToggleControls: () -> Unit,
): Modifier = this.pointerInput(isLocked, currentDuration) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        gestureState.dragStartOffset = down.position
        gestureState.hideSideSeekJob?.cancel()
        gestureState.sideSeekDirection = null
        gestureState.currentDragMode = DragGestureMode.NONE
        gestureState.totalDragX = 0f
        gestureState.totalDragY = 0f
        gestureState.accumulatedDragX = 0f
        gestureState.seekStartMs = state.currentPositionMs
        gestureState.seekTargetMs = gestureState.seekStartMs
        gestureState.seekDeltaMs = 0L

        if (isLocked) {
            val up = waitForUpOrCancellation()
            if (up != null) {
                onToggleControls()
                if (gestureState.sideSeekDirection != null) {
                    gestureState.hideSideSeekJob?.cancel()
                    gestureState.sideSeekDirection = null
                }
            }
            return@awaitEachGesture
        }

        var slopChange: PointerInputChange? = null
        var pinchFromStandstill = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2 && viewModel.settings.value.gesturesEnabled) {
                    pinchFromStandstill = true
                    gestureState.pinchStartDistance = (pressed[0].position - pressed[1].position).getDistance()
                    gestureState.pinchStartZoom = currentSettings.videoZoom
                    gestureState.pinchCurrentZoom = gestureState.pinchStartZoom
                    gestureState.isPinching = true
                    gestureState.hideZoomJob?.cancel()
                    gestureState.showZoomIndicator = true
                    gestureState.zoomDisplayText = formatMultiplier(gestureState.pinchStartZoom)
                    pressed.forEach { it.consume() }
                    break
                }
                val first = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!first.pressed && first.previousPressed) {
                    break
                }
                if ((first.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    first.consume()
                    slopChange = first
                    break
                }
            }
        }
        if (slopChange == null && !pinchFromStandstill) {
            val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
            if (stillDown && viewModel.settings.value.gesturesEnabled) {
                val previousSpeed = state.speed
                val heldItemUri = state.currentItem?.uri
                viewModel.setSpeed(2f)
                gestureState.isLongPress2x = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    if (state.currentItem?.uri == heldItemUri) {
                        viewModel.setSpeed(previousSpeed)
                    }
                    gestureState.isLongPress2x = false
                }
            } else {
                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = true)
                }
                if (secondDown == null) {
                    onToggleControls()
                    if (gestureState.sideSeekDirection != null) {
                        gestureState.hideSideSeekJob?.cancel()
                        gestureState.sideSeekDirection = null
                    }
                } else {
                    waitForUpOrCancellation()
                    if (viewModel.settings.value.gesturesEnabled) {
                        val oneThird = size.width / 3f
                        val twoThirds = size.width * (2f / 3f)
                        when {
                            secondDown.position.x < oneThird -> {
                                viewModel.skipBackward(viewModel.settings.value.doubleTapSeekSeconds * 1000L)
                                gestureState.sideSeekDirection = SideSeekDirection.BACKWARD
                                gestureState.hideBrightnessJob?.cancel()
                                gestureState.showBrightnessIndicator = false
                                gestureState.hideVolumeJob?.cancel()
                                gestureState.showVolumeIndicator = false
                                gestureState.hideSideSeekJob?.cancel()
                                gestureState.hideSideSeekJob = scope.launch {
                                    delay(1000)
                                    gestureState.sideSeekDirection = null
                                }
                            }
                            secondDown.position.x > twoThirds -> {
                                viewModel.skipForward(viewModel.settings.value.doubleTapSeekSeconds * 1000L)
                                gestureState.sideSeekDirection = SideSeekDirection.FORWARD
                                gestureState.hideBrightnessJob?.cancel()
                                gestureState.showBrightnessIndicator = false
                                gestureState.hideVolumeJob?.cancel()
                                gestureState.showVolumeIndicator = false
                                gestureState.hideSideSeekJob?.cancel()
                                gestureState.hideSideSeekJob = scope.launch {
                                    delay(1000)
                                    gestureState.sideSeekDirection = null
                                }
                            }
                            else -> {
                                viewModel.togglePlayPause()
                                if (gestureState.sideSeekDirection != null) {
                                    gestureState.hideSideSeekJob?.cancel()
                                    gestureState.sideSeekDirection = null
                                }
                            }
                        }
                    } else {
                        onToggleControls()
                        if (gestureState.sideSeekDirection != null) {
                            gestureState.hideSideSeekJob?.cancel()
                            gestureState.sideSeekDirection = null
                        }
                    }
                }
            }
            return@awaitEachGesture
        }

        gestureState.dragStartBrightness = getScreenBrightness(context, activity)
        gestureState.dragStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        gestureState.volumeMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
        gestureState.volumeMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        if (!viewModel.settings.value.gesturesEnabled) {
            return@awaitEachGesture
        }
        if (slopChange != null) {
            gestureState.applyDragDelta(
                dragAmount = slopChange.position - slopChange.previousPosition,
                scopeWidth = size.width.toFloat(),
                scopeHeight = size.height.toFloat(),
                currentDuration = currentDuration,
                isPlaying = state.isPlaying,
                currentPositionMs = state.currentPositionMs,
                onPause = { viewModel.pause() },
                activity = activity,
                audioManager = audioManager,
            )
        }
        var endedCleanly = false
        var dragLoopDone = false
        while (!dragLoopDone) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (!gestureState.isPinching) {
                    if (gestureState.isDraggingSeek) {
                        gestureState.isDraggingSeek = false
                        gestureState.resumeAfterSeekDrag { viewModel.play() }
                    }
                    gestureState.currentDragMode = DragGestureMode.NONE
                    gestureState.pinchStartDistance = (pressed[0].position - pressed[1].position).getDistance()
                    gestureState.pinchStartZoom = currentSettings.videoZoom
                    gestureState.pinchCurrentZoom = gestureState.pinchStartZoom
                    gestureState.isPinching = true
                    gestureState.hideZoomJob?.cancel()
                    gestureState.showZoomIndicator = true
                    gestureState.zoomDisplayText = formatMultiplier(gestureState.pinchStartZoom)
                } else {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (gestureState.pinchStartDistance > 0f && distance > 0f) {
                        val newZoom = (gestureState.pinchStartZoom * (distance / gestureState.pinchStartDistance)).coerceIn(0.5f, 2.0f)
                        if (newZoom != gestureState.pinchCurrentZoom) {
                            gestureState.pinchCurrentZoom = newZoom
                            viewModel.setVideoZoom(newZoom)
                            gestureState.zoomDisplayText = formatMultiplier(newZoom)
                        }
                    }
                }
                pressed.forEach { it.consume() }
                continue
            }
            if (gestureState.isPinching) {
                gestureState.isPinching = false
                val updated = currentSettings.copy(videoZoom = gestureState.pinchCurrentZoom)
                viewModel.updateSettings(updated)
                gestureState.hideZoomJob?.cancel()
                gestureState.hideZoomJob = scope.launch {
                    delay(1000)
                    gestureState.showZoomIndicator = false
                }
                dragLoopDone = true
                continue
            }
            val dragChange = event.changes.firstOrNull { it.id == down.id }
            if (dragChange == null) {
                dragLoopDone = true
            } else if (!dragChange.pressed && dragChange.previousPressed) {
                endedCleanly = true
                dragLoopDone = true
            } else {
                val delta = dragChange.position - dragChange.previousPosition
                if (delta != Offset.Zero) {
                    dragChange.consume()
                    gestureState.applyDragDelta(
                        dragAmount = delta,
                        scopeWidth = size.width.toFloat(),
                        scopeHeight = size.height.toFloat(),
                        currentDuration = currentDuration,
                        isPlaying = state.isPlaying,
                        currentPositionMs = state.currentPositionMs,
                        onPause = { viewModel.pause() },
                        activity = activity,
                        audioManager = audioManager,
                    )
                }
            }
        }
        if (endedCleanly) {
            when (gestureState.currentDragMode) {
                DragGestureMode.HORIZONTAL_SEEK -> {
                    if (gestureState.isDraggingSeek) {
                        viewModel.seekTo(gestureState.seekTargetMs)
                        gestureState.isDraggingSeek = false
                        gestureState.resumeAfterSeekDrag { viewModel.play() }
                    }
                }
                DragGestureMode.VERTICAL_BRIGHTNESS -> {
                    gestureState.hideBrightnessJob?.cancel()
                    gestureState.hideBrightnessJob = scope.launch {
                        delay(1000)
                        gestureState.showBrightnessIndicator = false
                    }
                }
                DragGestureMode.VERTICAL_VOLUME -> {
                    gestureState.hideVolumeJob?.cancel()
                    gestureState.hideVolumeJob = scope.launch {
                        delay(1000)
                        gestureState.showVolumeIndicator = false
                    }
                }
                DragGestureMode.NONE -> {}
            }
        } else {
            if (gestureState.isDraggingSeek) {
                gestureState.isDraggingSeek = false
                gestureState.resumeAfterSeekDrag { viewModel.play() }
            }
            if (gestureState.showBrightnessIndicator) {
                gestureState.hideBrightnessJob?.cancel()
                gestureState.hideBrightnessJob = scope.launch {
                    delay(1000)
                    gestureState.showBrightnessIndicator = false
                }
            }
            if (gestureState.showVolumeIndicator) {
                gestureState.hideVolumeJob?.cancel()
                gestureState.hideVolumeJob = scope.launch {
                    delay(1000)
                    gestureState.showVolumeIndicator = false
                }
            }
        }
        gestureState.currentDragMode = DragGestureMode.NONE
    }
}
