package com.mpx.player.ui.screens.player.state

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureStateTest {

    private fun newState(): PlayerGestureState = PlayerGestureState(
        initialBrightness = 0.5f,
        initialVolume = 7,
        initialMinVolume = 0,
        initialMaxVolume = 15,
    )

    @Test
    fun `isInEdgeExclusionZone blocks touches inside outer edge margin`() {
        val margin = 24f
        assertTrue(isInEdgeExclusionZone(Offset(10f, 200f), 1000f, 500f, margin))
        assertTrue(isInEdgeExclusionZone(Offset(980f, 200f), 1000f, 500f, margin))
        assertTrue(isInEdgeExclusionZone(Offset(500f, 12f), 1000f, 500f, margin))
        assertTrue(isInEdgeExclusionZone(Offset(500f, 488f), 1000f, 500f, margin))
        assertFalse(isInEdgeExclusionZone(Offset(50f, 50f), 1000f, 500f, margin))
        assertFalse(isInEdgeExclusionZone(Offset(950f, 450f), 1000f, 500f, margin))
    }

    @Test
    fun `classifyDragGestureMode rejects diagonal or small horizontal brushes`() {
        val start = Offset(500f, 300f)
        // Small horizontal jitter past standard touchSlop (e.g. 40px < 72px seek slop) stays NONE
        assertEquals(
            DragGestureMode.NONE,
            classifyDragGestureMode(
                totalOffset = Offset(40f, 0f),
                startOffset = start,
                scopeWidth = 1000f,
                canHorizontalSeek = true,
                verticalSlopPx = 36f,
                horizontalSeekSlopPx = 72f,
            ),
        )
        // 45-degree diagonal drag stays NONE (fails 1.5x horizontal and 1.25x vertical dominance)
        assertEquals(
            DragGestureMode.NONE,
            classifyDragGestureMode(
                totalOffset = Offset(100f, 100f),
                startOffset = start,
                scopeWidth = 1000f,
                canHorizontalSeek = true,
                verticalSlopPx = 36f,
                horizontalSeekSlopPx = 72f,
            ),
        )
    }

    @Test
    fun `classifyDragGestureMode locks horizontal seek only when clearly horizontal past slop`() {
        val mode = classifyDragGestureMode(
            totalOffset = Offset(90f, 20f),
            startOffset = Offset(500f, 300f),
            scopeWidth = 1000f,
            canHorizontalSeek = true,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
        )
        assertEquals(DragGestureMode.HORIZONTAL_SEEK, mode)

        // Suppressed when duration is 0 / preparing
        assertEquals(
            DragGestureMode.NONE,
            classifyDragGestureMode(
                totalOffset = Offset(90f, 20f),
                startOffset = Offset(500f, 300f),
                scopeWidth = 1000f,
                canHorizontalSeek = false,
                verticalSlopPx = 36f,
                horizontalSeekSlopPx = 72f,
            ),
        )
    }

    @Test
    fun `classifyDragGestureMode selects brightness on left and volume on right for vertical drags`() {
        assertEquals(
            DragGestureMode.VERTICAL_BRIGHTNESS,
            classifyDragGestureMode(
                totalOffset = Offset(5f, -50f),
                startOffset = Offset(200f, 300f),
                scopeWidth = 1000f,
                canHorizontalSeek = true,
                verticalSlopPx = 36f,
                horizontalSeekSlopPx = 72f,
            ),
        )
        assertEquals(
            DragGestureMode.VERTICAL_VOLUME,
            classifyDragGestureMode(
                totalOffset = Offset(-5f, -50f),
                startOffset = Offset(800f, 300f),
                scopeWidth = 1000f,
                canHorizontalSeek = true,
                verticalSlopPx = 36f,
                horizontalSeekSlopPx = 72f,
            ),
        )
    }

    @Test
    fun `tryLockDragMode anchors seek at live position without initial jump and ignores edge zone`() {
        val state = newState()
        state.dragStartOffset = Offset(10f, 300f)
        var paused = 0

        // Edge zone drag must not lock horizontal seek or pause playback
        val edgeLocked = state.tryLockDragMode(
            totalOffset = Offset(120f, 0f),
            scopeWidth = 1000f,
            currentDuration = 600_000L,
            isPreparing = false,
            startedInEdgeZone = true,
            isPlaying = true,
            currentPositionMs = 300_000L,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
            onPause = { paused++ },
        )
        assertEquals(DragGestureMode.NONE, edgeLocked)
        assertFalse(state.isDraggingSeek)
        assertEquals(0, paused)

        // Valid drag inside safe area anchors at live 300_000ms with 0 delta
        state.dragStartOffset = Offset(400f, 300f)
        val locked = state.tryLockDragMode(
            totalOffset = Offset(120f, 0f),
            scopeWidth = 1000f,
            currentDuration = 600_000L,
            isPreparing = false,
            startedInEdgeZone = false,
            isPlaying = true,
            currentPositionMs = 300_000L,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
            onPause = { paused++ },
        )
        assertEquals(DragGestureMode.HORIZONTAL_SEEK, locked)
        assertTrue(state.isDraggingSeek)
        assertEquals(300_000L, state.seekStartMs)
        assertEquals(300_000L, state.seekTargetMs)
        assertEquals(0L, state.seekDeltaMs)
        assertTrue(state.wasPlayingBeforeSeekDrag)
        assertEquals(1, paused)
    }

    @Test
    fun `applyLockedDragDelta scales horizontal seek consistently by dp and clamps bounds`() {
        val state = newState()
        state.dragStartOffset = Offset(400f, 300f)
        state.tryLockDragMode(
            totalOffset = Offset(100f, 0f),
            scopeWidth = 1000f,
            currentDuration = 60_000L,
            isPreparing = false,
            startedInEdgeZone = false,
            isPlaying = false,
            currentPositionMs = 30_000L,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
            onPause = {},
        )

        // 50dp forward (150px at density=3f) => 50 * 200ms = +10_000ms
        state.applyLockedDragDelta(
            dragAmount = Offset(150f, 0f),
            scopeHeight = 500f,
            density = 3f,
            currentDuration = 60_000L,
            activity = null,
            audioManager = null,
        )
        assertEquals(40_000L, state.seekTargetMs)
        assertEquals(10_000L, state.seekDeltaMs)
        assertTrue(state.shouldCommitSeek())

        // Dragging far beyond end clamps cleanly and reverses immediately
        state.applyLockedDragDelta(
            dragAmount = Offset(1_500f, 0f),
            scopeHeight = 500f,
            density = 3f,
            currentDuration = 60_000L,
            activity = null,
            audioManager = null,
        )
        assertEquals(60_000L, state.seekTargetMs)
        state.applyLockedDragDelta(
            dragAmount = Offset(-75f, 0f), // -25dp => -5_000ms immediately from 60_000
            scopeHeight = 500f,
            density = 3f,
            currentDuration = 60_000L,
            activity = null,
            audioManager = null,
        )
        assertEquals(55_000L, state.seekTargetMs)
    }

    @Test
    fun `shouldCommitSeek suppresses micro-jitter drags back near start`() {
        val state = newState()
        state.dragStartOffset = Offset(400f, 300f)
        state.tryLockDragMode(
            totalOffset = Offset(100f, 0f),
            scopeWidth = 1000f,
            currentDuration = 120_000L,
            isPreparing = false,
            startedInEdgeZone = false,
            isPlaying = false,
            currentPositionMs = 50_000L,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
            onPause = {},
        )
        assertFalse(state.shouldCommitSeek())

        // Micro move of 0.5dp = 100ms < 250ms commit threshold
        state.applyLockedDragDelta(
            dragAmount = Offset(1.5f, 0f),
            scopeHeight = 500f,
            density = 3f,
            currentDuration = 120_000L,
            activity = null,
            audioManager = null,
        )
        assertFalse(state.shouldCommitSeek())
    }

    @Test
    fun `cancelActiveDrag clears dragging flag and resumes playback`() {
        val state = newState()
        state.dragStartOffset = Offset(400f, 300f)
        state.tryLockDragMode(
            totalOffset = Offset(100f, 0f),
            scopeWidth = 1000f,
            currentDuration = 120_000L,
            isPreparing = false,
            startedInEdgeZone = false,
            isPlaying = true,
            currentPositionMs = 50_000L,
            verticalSlopPx = 36f,
            horizontalSeekSlopPx = 72f,
            onPause = {},
        )
        var resumed = 0
        state.cancelActiveDrag(allowResume = true) { resumed++ }

        assertFalse(state.isDraggingSeek)
        assertFalse(state.wasPlayingBeforeSeekDrag)
        assertEquals(DragGestureMode.NONE, state.currentDragMode)
        assertEquals(1, resumed)
    }

    @Test
    fun `isValidDoubleTapPair requires same zone, proximity within slop, and non-edge taps`() {
        val width = 900f
        val height = 500f
        val edge = 36f
        val slop = 300f

        // Valid pair on right third
        assertTrue(
            isValidDoubleTapPair(
                firstPos = Offset(750f, 250f),
                secondPos = Offset(780f, 260f),
                width = width,
                height = height,
                edgeMarginPx = edge,
                doubleTapSlopPx = slop,
            ),
        )
        // Cross-zone taps (center -> right) must be rejected
        assertFalse(
            isValidDoubleTapPair(
                firstPos = Offset(550f, 250f),
                secondPos = Offset(700f, 250f),
                width = width,
                height = height,
                edgeMarginPx = edge,
                doubleTapSlopPx = slop,
            ),
        )
        // Too far apart even in center/vertical distance
        assertFalse(
            isValidDoubleTapPair(
                firstPos = Offset(750f, 60f),
                secondPos = Offset(750f, 440f),
                width = width,
                height = height,
                edgeMarginPx = edge,
                doubleTapSlopPx = slop,
            ),
        )
        // Inside bezel edge
        assertFalse(
            isValidDoubleTapPair(
                firstPos = Offset(10f, 250f),
                secondPos = Offset(20f, 250f),
                width = width,
                height = height,
                edgeMarginPx = edge,
                doubleTapSlopPx = slop,
            ),
        )
    }

    @Test
    fun `recordSideSeek chains consecutive skips cleanly`() {
        val state = newState()
        state.recordSideSeek(SideSeekDirection.FORWARD, 10_000L, 40_000L, isChained = false)
        assertEquals(SideSeekDirection.FORWARD, state.sideSeekDirection)
        assertEquals(10_000L, state.sideSeekDeltaMs)
        assertEquals(40_000L, state.sideSeekTargetMs)

        state.recordSideSeek(SideSeekDirection.FORWARD, 10_000L, 50_000L, isChained = true)
        assertEquals(20_000L, state.sideSeekDeltaMs)
        assertEquals(50_000L, state.sideSeekTargetMs)

        state.clearSideSeek()
        assertEquals(null, state.sideSeekDirection)
        assertEquals(0L, state.sideSeekDeltaMs)
        assertEquals(0L, state.sideSeekTargetMs)
    }
}
