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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.player.state.DragGestureMode
import com.mpx.player.ui.screens.player.state.PlayerGestureState
import com.mpx.player.ui.screens.player.state.PlayerGestureThresholds
import com.mpx.player.ui.screens.player.state.PlayerTapZone
import com.mpx.player.ui.screens.player.state.SideSeekDirection
import com.mpx.player.ui.screens.player.state.formatMultiplier
import com.mpx.player.ui.screens.player.state.getScreenBrightness
import com.mpx.player.ui.screens.player.state.isInEdgeExclusionZone
import com.mpx.player.ui.screens.player.state.isValidDoubleTapPair
import com.mpx.player.ui.screens.player.state.resolvePlayerTapZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private sealed interface InitialPointerOutcome {
    data class QuickTap(val upPosition: Offset) : InitialPointerOutcome
    data class MovedPastSlop(val change: PointerInputChange) : InitialPointerOutcome
    data class PinchStart(val pressed: List<PointerInputChange>) : InitialPointerOutcome
    data object LongPressTimeout : InitialPointerOutcome
    data object Cancelled : InitialPointerOutcome
}

private data class GestureEnv(
    val currentDuration: Long,
    val gestureState: PlayerGestureState,
    val viewModel: PlayerViewModel,
    val scope: CoroutineScope,
    val context: Context,
    val activity: Activity?,
    val audioManager: AudioManager?,
    val onToggleControls: () -> Unit,
    val dragEdgeMarginPx: Float,
    val tapEdgeMarginPx: Float,
    val verticalSlopPx: Float,
    val horizontalSeekSlopPx: Float,
    val doubleTapSlopPx: Float,
)

/**
 * Single gesture detector for the video area (issue #166).
 *
 * NOTE: Keys [isLocked] and [currentDuration] MUST BE PRESERVED:
 * changing keys resets in-flight gestures. Live playback state and
 * settings are read from [viewModel] rather than captured Compose
 * snapshots so gestures never operate on stale positions or flags.
 */
fun Modifier.playerGestureDetector(
    isLocked: Boolean,
    currentDuration: Long,
    gestureState: PlayerGestureState,
    viewModel: PlayerViewModel,
    scope: CoroutineScope,
    context: Context,
    activity: Activity?,
    audioManager: AudioManager?,
    onToggleControls: () -> Unit,
): Modifier = this.pointerInput(isLocked, currentDuration) {
    val env = GestureEnv(
        currentDuration = currentDuration,
        gestureState = gestureState,
        viewModel = viewModel,
        scope = scope,
        context = context,
        activity = activity,
        audioManager = audioManager,
        onToggleControls = onToggleControls,
        dragEdgeMarginPx = PlayerGestureThresholds.DRAG_EDGE_MARGIN_DP.dp.toPx(),
        tapEdgeMarginPx = PlayerGestureThresholds.TAP_EDGE_MARGIN_DP.dp.toPx(),
        verticalSlopPx = maxOf(
            viewConfiguration.touchSlop,
            PlayerGestureThresholds.VERTICAL_SLOP_MIN_DP.dp.toPx(),
        ),
        horizontalSeekSlopPx = maxOf(
            viewConfiguration.touchSlop * 2.5f,
            PlayerGestureThresholds.HORIZONTAL_SEEK_SLOP_MIN_DP.dp.toPx(),
        ),
        doubleTapSlopPx = PlayerGestureThresholds.DOUBLE_TAP_SLOP_DP.dp.toPx(),
    )

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        gestureState.dragStartOffset = down.position
        gestureState.currentDragMode = DragGestureMode.NONE
        gestureState.totalDragX = 0f
        gestureState.totalDragY = 0f
        val liveAtDown = viewModel.playbackState.value
        gestureState.seekStartMs = liveAtDown.currentPositionMs
        gestureState.seekTargetMs = liveAtDown.currentPositionMs
        gestureState.seekDeltaMs = 0L

        if (isLocked) {
            val up = waitForUpOrCancellation()
            if (up != null &&
                !up.isConsumed &&
                (up.position - down.position).getDistance() <= viewConfiguration.touchSlop
            ) {
                onToggleControls()
                gestureState.clearSideSeek()
            }
            return@awaitEachGesture
        }

        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val firstInDragEdge = isInEdgeExclusionZone(down.position, width, height, env.dragEdgeMarginPx)

        when (val firstOutcome = awaitInitialOutcome(down, viewConfiguration.touchSlop)) {
            InitialPointerOutcome.Cancelled -> return@awaitEachGesture
            InitialPointerOutcome.LongPressTimeout -> {
                if (!firstInDragEdge) {
                    runLongPress2x(anchorDown = down, env = env)
                } else {
                    waitForUpOrCancellation()
                }
                return@awaitEachGesture
            }
            is InitialPointerOutcome.PinchStart -> {
                runDragAndPinchLoop(anchorDown = down, env = env, initialPinchPressed = firstOutcome.pressed)
                return@awaitEachGesture
            }
            is InitialPointerOutcome.MovedPastSlop -> {
                runDragAndPinchLoop(anchorDown = down, env = env, initialSlopChange = firstOutcome.change)
                return@awaitEachGesture
            }
            is InitialPointerOutcome.QuickTap -> {
                if (!viewModel.settings.value.gesturesEnabled) {
                    onToggleControls()
                    gestureState.clearSideSeek()
                    return@awaitEachGesture
                }

                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = true)
                }
                if (secondDown == null) {
                    onToggleControls()
                    gestureState.clearSideSeek()
                    return@awaitEachGesture
                }

                when (val secondOutcome = awaitInitialOutcome(secondDown, viewConfiguration.touchSlop)) {
                    InitialPointerOutcome.Cancelled -> return@awaitEachGesture
                    InitialPointerOutcome.LongPressTimeout -> {
                        runLongPress2x(anchorDown = secondDown, env = env)
                    }
                    is InitialPointerOutcome.PinchStart -> {
                        runDragAndPinchLoop(anchorDown = secondDown, env = env, initialPinchPressed = secondOutcome.pressed)
                    }
                    is InitialPointerOutcome.MovedPastSlop -> {
                        // Second touch became a drag/swipe (e.g. tap to wake then immediate scrub):
                        // transition directly to drag instead of falsely firing double-tap seek on lift.
                        runDragAndPinchLoop(anchorDown = secondDown, env = env, initialSlopChange = secondOutcome.change)
                    }
                    is InitialPointerOutcome.QuickTap -> {
                        val liveState = viewModel.playbackState.value
                        val validDoubleTap = isValidDoubleTapPair(
                            firstPos = down.position,
                            secondPos = secondDown.position,
                            width = width,
                            height = height,
                            edgeMarginPx = env.tapEdgeMarginPx,
                            doubleTapSlopPx = env.doubleTapSlopPx,
                        )
                        if (!validDoubleTap || liveState.currentItem == null || liveState.isPreparing) {
                            onToggleControls()
                            gestureState.clearSideSeek()
                            return@awaitEachGesture
                        }

                        when (resolvePlayerTapZone(secondDown.position.x, width)) {
                            PlayerTapZone.LEFT -> {
                                performSideSeek(SideSeekDirection.BACKWARD, chained = false, env = env)
                                runChainedSideSeek(PlayerTapZone.LEFT, secondDown.position, env = env)
                            }
                            PlayerTapZone.RIGHT -> {
                                performSideSeek(SideSeekDirection.FORWARD, chained = false, env = env)
                                runChainedSideSeek(PlayerTapZone.RIGHT, secondDown.position, env = env)
                            }
                            PlayerTapZone.CENTER -> {
                                viewModel.togglePlayPause()
                                gestureState.clearSideSeek()
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.runLongPress2x(
    anchorDown: PointerInputChange,
    env: GestureEnv,
) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val liveState = env.viewModel.playbackState.value
    val inDragEdge = isInEdgeExclusionZone(anchorDown.position, width, height, env.dragEdgeMarginPx)
    if (!env.viewModel.settings.value.gesturesEnabled ||
        inDragEdge ||
        liveState.currentItem == null ||
        liveState.isPreparing
    ) {
        waitForUpOrCancellation()
        return
    }
    env.gestureState.clearSideSeek()
    val previousSpeed = liveState.speed
    val heldItemUri = liveState.currentItem?.uri
    env.viewModel.setSpeed(2f)
    env.gestureState.isLongPress2x = true
    try {
        waitForUpOrCancellation()
    } finally {
        if (env.viewModel.playbackState.value.currentItem?.uri == heldItemUri) {
            env.viewModel.setSpeed(previousSpeed)
        }
        env.gestureState.isLongPress2x = false
    }
}

private fun scheduleIndicatorHides(env: GestureEnv) {
    val gestureState = env.gestureState
    if (!env.scope.isActive) {
        gestureState.showBrightnessIndicator = false
        gestureState.showVolumeIndicator = false
        gestureState.showZoomIndicator = false
        return
    }
    if (gestureState.showBrightnessIndicator) {
        gestureState.hideBrightnessJob?.cancel()
        gestureState.hideBrightnessJob = env.scope.launch {
            delay(1000)
            gestureState.showBrightnessIndicator = false
        }
    }
    if (gestureState.showVolumeIndicator) {
        gestureState.hideVolumeJob?.cancel()
        gestureState.hideVolumeJob = env.scope.launch {
            delay(1000)
            gestureState.showVolumeIndicator = false
        }
    }
    if (gestureState.showZoomIndicator) {
        gestureState.hideZoomJob?.cancel()
        gestureState.hideZoomJob = env.scope.launch {
            delay(1000)
            gestureState.showZoomIndicator = false
        }
    }
}

private suspend fun AwaitPointerEventScope.runDragAndPinchLoop(
    anchorDown: PointerInputChange,
    env: GestureEnv,
    initialSlopChange: PointerInputChange? = null,
    initialPinchPressed: List<PointerInputChange>? = null,
) {
    val viewModel = env.viewModel
    val gestureState = env.gestureState
    if (!viewModel.settings.value.gesturesEnabled) return

    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val anchorInDragEdge = isInEdgeExclusionZone(anchorDown.position, width, height, env.dragEdgeMarginPx)
    gestureState.dragStartOffset = anchorDown.position
    gestureState.currentDragMode = DragGestureMode.NONE
    gestureState.totalDragX = 0f
    gestureState.totalDragY = 0f
    gestureState.dragStartBrightness = getScreenBrightness(env.context, env.activity)
    gestureState.dragStartVolume = env.audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    gestureState.volumeMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && env.audioManager != null) {
        env.audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    } else {
        0
    }
    gestureState.volumeMax = env.audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

    fun beginPinch(pressed: List<PointerInputChange>) {
        if (gestureState.isDraggingSeek) {
            gestureState.isDraggingSeek = false
            gestureState.resumeAfterSeekDrag { viewModel.play() }
        }
        gestureState.clearSideSeek()
        gestureState.currentDragMode = DragGestureMode.NONE
        gestureState.pinchStartDistance = (pressed[0].position - pressed[1].position).getDistance()
        gestureState.pinchStartZoom = viewModel.settings.value.videoZoom
        gestureState.pinchCurrentZoom = gestureState.pinchStartZoom
        gestureState.isPinching = true
        gestureState.hideZoomJob?.cancel()
        gestureState.showZoomIndicator = true
        gestureState.zoomDisplayText = formatMultiplier(gestureState.pinchStartZoom)
        pressed.forEach { it.consume() }
    }

    fun finishPinch() {
        gestureState.isPinching = false
        val updated = viewModel.settings.value.copy(videoZoom = gestureState.pinchCurrentZoom)
        viewModel.updateSettings(updated)
        gestureState.hideZoomJob?.cancel()
        if (env.scope.isActive) {
            gestureState.hideZoomJob = env.scope.launch {
                delay(1000)
                gestureState.showZoomIndicator = false
            }
        } else {
            gestureState.showZoomIndicator = false
        }
    }

    fun processSinglePointerMove(change: PointerInputChange) {
        val liveState = viewModel.playbackState.value
        val liveDuration = if (liveState.durationMs > 0L) liveState.durationMs else env.currentDuration
        if (gestureState.currentDragMode == DragGestureMode.NONE) {
            val locked = gestureState.tryLockDragMode(
                totalOffset = change.position - anchorDown.position,
                scopeWidth = width,
                currentDuration = liveDuration,
                isPreparing = liveState.isPreparing || liveState.currentItem == null,
                startedInEdgeZone = anchorInDragEdge,
                isPlaying = liveState.isPlaying,
                currentPositionMs = liveState.currentPositionMs,
                verticalSlopPx = env.verticalSlopPx,
                horizontalSeekSlopPx = env.horizontalSeekSlopPx,
                onPause = { viewModel.pause() },
            )
            if (locked != DragGestureMode.NONE) {
                gestureState.clearSideSeek()
                change.consume()
            }
        } else {
            val delta = change.position - change.previousPosition
            change.consume()
            if (delta != Offset.Zero) {
                gestureState.applyLockedDragDelta(
                    dragAmount = delta,
                    scopeHeight = height,
                    density = density,
                    currentDuration = liveDuration,
                    activity = env.activity,
                    audioManager = env.audioManager,
                )
            }
        }
    }

    var endedCleanly = false
    try {
        if (initialPinchPressed != null && initialPinchPressed.size >= 2) {
            beginPinch(initialPinchPressed)
        } else if (initialSlopChange != null && !initialSlopChange.isConsumed) {
            processSinglePointerMove(initialSlopChange)
        }

        var dragLoopDone = false
        while (!dragLoopDone) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (pressed.any { it.isConsumed }) {
                    if (gestureState.isPinching) finishPinch()
                    dragLoopDone = true
                    continue
                }
                if (!gestureState.isPinching) {
                    beginPinch(pressed)
                } else {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (gestureState.pinchStartDistance > 0f && distance > 0f) {
                        val newZoom = (gestureState.pinchStartZoom * (distance / gestureState.pinchStartDistance))
                            .coerceIn(0.5f, 2.0f)
                        if (newZoom != gestureState.pinchCurrentZoom) {
                            gestureState.pinchCurrentZoom = newZoom
                            viewModel.setVideoZoom(newZoom)
                            gestureState.zoomDisplayText = formatMultiplier(newZoom)
                        }
                    }
                    pressed.forEach { it.consume() }
                }
                continue
            }

            if (gestureState.isPinching) {
                finishPinch()
                dragLoopDone = true
                continue
            }

            val dragChange = event.changes.firstOrNull { it.id == anchorDown.id }
            when {
                dragChange == null || dragChange.isConsumed -> {
                    dragLoopDone = true
                }
                !dragChange.pressed && dragChange.previousPressed -> {
                    endedCleanly = true
                    dragLoopDone = true
                }
                else -> {
                    processSinglePointerMove(dragChange)
                }
            }
        }

        if (endedCleanly) {
            when (gestureState.currentDragMode) {
                DragGestureMode.HORIZONTAL_SEEK -> {
                    if (gestureState.shouldCommitSeek()) {
                        viewModel.seekTo(gestureState.seekTargetMs)
                    }
                    gestureState.isDraggingSeek = false
                    gestureState.resumeAfterSeekDrag { viewModel.play() }
                }
                DragGestureMode.VERTICAL_BRIGHTNESS,
                DragGestureMode.VERTICAL_VOLUME,
                -> scheduleIndicatorHides(env)
                DragGestureMode.NONE -> {}
            }
            gestureState.currentDragMode = DragGestureMode.NONE
        }
    } finally {
        if (!endedCleanly) {
            gestureState.cancelActiveDrag(
                allowResume = env.scope.isActive && !viewModel.playbackState.value.isLocked,
                onResumePlay = { viewModel.play() },
            )
            scheduleIndicatorHides(env)
        }
    }
}

private fun performSideSeek(
    direction: SideSeekDirection,
    chained: Boolean,
    env: GestureEnv,
) {
    val viewModel = env.viewModel
    val gestureState = env.gestureState
    val seekSeconds = viewModel.settings.value.doubleTapSeekSeconds
    val stepDeltaMs = seekSeconds * 1000L * if (direction == SideSeekDirection.BACKWARD) -1L else 1L
    val liveState = viewModel.playbackState.value
    val liveDuration = if (liveState.durationMs > 0L) liveState.durationMs else env.currentDuration
    val isSameSideChain = chained && gestureState.sideSeekDirection == direction

    val targetMs = if (liveDuration > 0L) {
        val baseMs = if (isSameSideChain) gestureState.sideSeekTargetMs else liveState.currentPositionMs
        (baseMs + stepDeltaMs).coerceIn(0L, liveDuration)
    } else {
        0L
    }

    if (liveDuration > 0L) {
        viewModel.seekTo(targetMs)
    } else if (direction == SideSeekDirection.BACKWARD) {
        viewModel.skipBackward(seekSeconds * 1000L)
    } else {
        viewModel.skipForward(seekSeconds * 1000L)
    }

    gestureState.recordSideSeek(
        direction = direction,
        stepDeltaMs = stepDeltaMs,
        targetMs = targetMs,
        isChained = isSameSideChain,
    )
    gestureState.hideBrightnessJob?.cancel()
    gestureState.showBrightnessIndicator = false
    gestureState.hideVolumeJob?.cancel()
    gestureState.showVolumeIndicator = false
    gestureState.hideSideSeekJob?.cancel()
    if (env.scope.isActive) {
        gestureState.hideSideSeekJob = env.scope.launch {
            delay(1000)
            gestureState.clearSideSeek()
        }
    }
}

private suspend fun AwaitPointerEventScope.runChainedSideSeek(
    initialZone: PlayerTapZone,
    initialPos: Offset,
    env: GestureEnv,
) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    var lastPos = initialPos
    while (true) {
        val nextDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = true)
        } ?: return

        when (val nextOutcome = awaitInitialOutcome(nextDown, viewConfiguration.touchSlop)) {
            is InitialPointerOutcome.QuickTap -> {
                val liveState = env.viewModel.playbackState.value
                val sameZone = resolvePlayerTapZone(nextDown.position.x, width) == initialZone
                val validPair = isValidDoubleTapPair(
                    firstPos = lastPos,
                    secondPos = nextDown.position,
                    width = width,
                    height = height,
                    edgeMarginPx = env.tapEdgeMarginPx,
                    doubleTapSlopPx = env.doubleTapSlopPx,
                )
                if (sameZone && validPair && liveState.currentItem != null && !liveState.isPreparing) {
                    val dir = if (initialZone == PlayerTapZone.LEFT) {
                        SideSeekDirection.BACKWARD
                    } else {
                        SideSeekDirection.FORWARD
                    }
                    performSideSeek(dir, chained = true, env = env)
                    lastPos = nextDown.position
                } else {
                    env.onToggleControls()
                    env.gestureState.clearSideSeek()
                    return
                }
            }
            is InitialPointerOutcome.MovedPastSlop -> {
                runDragAndPinchLoop(anchorDown = nextDown, env = env, initialSlopChange = nextOutcome.change)
                return
            }
            is InitialPointerOutcome.PinchStart -> {
                runDragAndPinchLoop(anchorDown = nextDown, env = env, initialPinchPressed = nextOutcome.pressed)
                return
            }
            InitialPointerOutcome.LongPressTimeout -> {
                runLongPress2x(anchorDown = nextDown, env = env)
                return
            }
            InitialPointerOutcome.Cancelled -> return
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitInitialOutcome(
    down: PointerInputChange,
    touchSlop: Float,
): InitialPointerOutcome {
    var outcome: InitialPointerOutcome = InitialPointerOutcome.Cancelled
    var timedOut = true
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                outcome = if (pressed.any { it.isConsumed }) {
                    InitialPointerOutcome.Cancelled
                } else {
                    InitialPointerOutcome.PinchStart(pressed)
                }
                timedOut = false
                break
            }
            val first = event.changes.firstOrNull { it.id == down.id }
            if (first == null || first.isConsumed) {
                outcome = InitialPointerOutcome.Cancelled
                timedOut = false
                break
            }
            if (!first.pressed && first.previousPressed) {
                outcome = InitialPointerOutcome.QuickTap(first.position)
                timedOut = false
                break
            }
            if ((first.position - down.position).getDistance() > touchSlop) {
                outcome = InitialPointerOutcome.MovedPastSlop(first)
                timedOut = false
                break
            }
        }
    }
    if (timedOut) {
        val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed && !it.isConsumed }
        outcome = if (stillDown) InitialPointerOutcome.LongPressTimeout else InitialPointerOutcome.Cancelled
    }
    return outcome
}
