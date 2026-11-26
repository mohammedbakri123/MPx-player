package com.mpx.player.ui.screens.player.state

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerKeyHandlerTest {

    @Test
    fun `hidden controls map dpad center and space to toggle play pause and show controls`() {
        assertEquals(
            PlayerKeyCommand.TOGGLE_PLAY_PAUSE_AND_SHOW_CONTROLS,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.TOGGLE_PLAY_PAUSE_AND_SHOW_CONTROLS,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_SPACE,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
    }

    @Test
    fun `hidden controls map dpad left and right to seek and up down to show controls`() {
        assertEquals(
            PlayerKeyCommand.SEEK_BACKWARD,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.SEEK_FORWARD,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                isKeyDown = true,
                repeatCount = 2,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.SHOW_CONTROLS,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
    }

    @Test
    fun `visible controls pass dpad keys through for focus navigation`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
        ).forEach { code ->
            assertEquals(
                PlayerKeyCommand.PASS_THROUGH,
                resolvePlayerKeyCommand(
                    keyCode = code,
                    isKeyDown = true,
                    repeatCount = 0,
                    controlsVisible = true,
                    isLocked = false,
                    isSheetOrDialogOpen = false,
                    isTutorialOpen = false,
                ),
            )
        }
    }

    @Test
    fun `hardware media keys work regardless of controls visibility or sheets`() {
        assertEquals(
            PlayerKeyCommand.TOGGLE_PLAY_PAUSE,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = true,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.SKIP_NEXT,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = true,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.SEEK_FORWARD,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = true,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
    }

    @Test
    fun `locked state ignores seeks and reveals unlock control on dpad when hidden`() {
        assertEquals(
            PlayerKeyCommand.CONSUME_NO_OP,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = true,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.SHOW_CONTROLS,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = true,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.PASS_THROUGH,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = true,
                isLocked = true,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
    }

    @Test
    fun `menu and track shortcuts open corresponding sheets when unlocked`() {
        assertEquals(
            PlayerKeyCommand.OPEN_PLAYBACK_MENU,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MENU,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.OPEN_SUBTITLES,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_CAPTIONS,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
        assertEquals(
            PlayerKeyCommand.OPEN_AUDIO_TRACKS,
            resolvePlayerKeyCommand(
                keyCode = KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                isKeyDown = true,
                repeatCount = 0,
                controlsVisible = false,
                isLocked = false,
                isSheetOrDialogOpen = false,
                isTutorialOpen = false,
            ),
        )
    }
}
