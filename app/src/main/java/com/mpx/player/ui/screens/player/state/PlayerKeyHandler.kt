package com.mpx.player.ui.screens.player.state

import android.view.KeyEvent
import com.mpx.player.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Semantic player actions resolved from Android TV remote D-pad and hardware media keys.
 */
enum class PlayerKeyCommand {
    PASS_THROUGH,
    CONSUME_NO_OP,
    TOGGLE_PLAY_PAUSE,
    TOGGLE_PLAY_PAUSE_AND_SHOW_CONTROLS,
    PLAY,
    PAUSE,
    STOP_AND_CLOSE,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    SHOW_CONTROLS,
    OPEN_PLAYBACK_MENU,
    OPEN_SUBTITLES,
    OPEN_AUDIO_TRACKS,
    ADVANCE_TUTORIAL,
    DISMISS_TUTORIAL,
}

/**
 * Pure key-event mapper for [com.mpx.player.ui.screens.player.PlayerScreen].
 * Keeps D-pad navigation functional when player chrome or dialogs are open while
 * enabling single-press seek, play/pause, and menu shortcuts during playback.
 */
fun resolvePlayerKeyCommand(
    keyCode: Int,
    isKeyDown: Boolean,
    repeatCount: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    isSheetOrDialogOpen: Boolean,
    isTutorialOpen: Boolean,
): PlayerKeyCommand {
    // Hardware media keys work regardless of overlay visibility (except locked seek).
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        -> return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.TOGGLE_PLAY_PAUSE else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_PLAY ->
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.PLAY else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_PAUSE ->
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.PAUSE else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_STOP ->
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.STOP_AND_CLOSE else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_NEXT ->
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.SKIP_NEXT else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.SKIP_PREVIOUS else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        -> return if (isKeyDown && !isLocked) PlayerKeyCommand.SEEK_FORWARD else PlayerKeyCommand.CONSUME_NO_OP

        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        -> return if (isKeyDown && !isLocked) PlayerKeyCommand.SEEK_BACKWARD else PlayerKeyCommand.CONSUME_NO_OP
    }

    if (isSheetOrDialogOpen) {
        return PlayerKeyCommand.PASS_THROUGH
    }

    if (isTutorialOpen) {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> if (isKeyDown && repeatCount == 0) PlayerKeyCommand.ADVANCE_TUTORIAL else PlayerKeyCommand.CONSUME_NO_OP

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            -> if (isKeyDown && repeatCount == 0) PlayerKeyCommand.DISMISS_TUTORIAL else PlayerKeyCommand.CONSUME_NO_OP

            else -> PlayerKeyCommand.PASS_THROUGH
        }
    }

    if (!isLocked) {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_GUIDE,
            -> return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.OPEN_PLAYBACK_MENU else PlayerKeyCommand.CONSUME_NO_OP

            KeyEvent.KEYCODE_CAPTIONS ->
                return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.OPEN_SUBTITLES else PlayerKeyCommand.CONSUME_NO_OP

            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK ->
                return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.OPEN_AUDIO_TRACKS else PlayerKeyCommand.CONSUME_NO_OP
        }
    }

    if (isLocked) {
        if (!controlsVisible && isDpadOrConfirmKey(keyCode)) {
            return if (isKeyDown && repeatCount == 0) PlayerKeyCommand.SHOW_CONTROLS else PlayerKeyCommand.CONSUME_NO_OP
        }
        return PlayerKeyCommand.PASS_THROUGH
    }

    if (!controlsVisible) {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            -> if (isKeyDown && repeatCount == 0) {
                PlayerKeyCommand.TOGGLE_PLAY_PAUSE_AND_SHOW_CONTROLS
            } else {
                PlayerKeyCommand.CONSUME_NO_OP
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> if (isKeyDown && repeatCount == 0) PlayerKeyCommand.SHOW_CONTROLS else PlayerKeyCommand.CONSUME_NO_OP

            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (isKeyDown) PlayerKeyCommand.SEEK_BACKWARD else PlayerKeyCommand.CONSUME_NO_OP

            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (isKeyDown) PlayerKeyCommand.SEEK_FORWARD else PlayerKeyCommand.CONSUME_NO_OP

            else -> PlayerKeyCommand.PASS_THROUGH
        }
    }

    // Controls are visible and unlocked: Space toggles play/pause while D-pad navigates buttons.
    return when (keyCode) {
        KeyEvent.KEYCODE_SPACE ->
            if (isKeyDown && repeatCount == 0) PlayerKeyCommand.TOGGLE_PLAY_PAUSE else PlayerKeyCommand.CONSUME_NO_OP
        else -> PlayerKeyCommand.PASS_THROUGH
    }
}

private fun isDpadOrConfirmKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * Executes a side seek (for both double-tap gestures and D-pad/media skip keys)
 * and shows the transient side-seek HUD for 1000 ms.
 */
internal fun triggerSideSeek(
    direction: SideSeekDirection,
    chained: Boolean,
    fallbackDurationMs: Long,
    gestureState: PlayerGestureState,
    viewModel: PlayerViewModel,
    scope: CoroutineScope,
) {
    val seekSeconds = viewModel.settings.value.doubleTapSeekSeconds
    val stepDeltaMs = seekSeconds * 1000L * if (direction == SideSeekDirection.BACKWARD) -1L else 1L
    val liveState = viewModel.playbackState.value
    val liveDuration = if (liveState.durationMs > 0L) liveState.durationMs else fallbackDurationMs
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
    if (scope.isActive) {
        gestureState.hideSideSeekJob = scope.launch {
            delay(1000)
            gestureState.clearSideSeek()
        }
    }
}
