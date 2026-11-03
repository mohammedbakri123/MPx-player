package com.mpx.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextPrimary
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fast draggable scrollbar with real-time section preview bubble for long Compose LazyColumn lists.
 * Meets UI/UX Pro Max guidelines with a 48dp touchable hit area, auto-fading after inactivity,
 * smooth animations, and high-contrast typography.
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    getSectionText: ((Int) -> String)? = null,
    thumbColor: Color = MpxPrimary,
    trackColor: Color = MpxSurfaceElevated.copy(alpha = 0.4f),
) {
    if (itemCount <= 3) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }

    val isScrollInProgress = listState.isScrollInProgress

    // Auto-fade timeout: shows on scroll/drag, fades out smoothly after 1.5s idle
    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "fastScrollAlpha",
    )

    if (alpha <= 0.01f && !isDragging) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp) // Minimum 48dp touch target width
            .alpha(alpha),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val minThumbHeightPx = with(density) { 48.dp.toPx() }
        val maxThumbHeightPx = (trackHeightPx / 3f).coerceAtLeast(minThumbHeightPx)

        val visibleItemsCount = listState.layoutInfo.visibleItemsInfo.size
        val thumbHeightPx = if (itemCount > 0) {
            val ratio = visibleItemsCount.toFloat() / itemCount.toFloat()
            (trackHeightPx * ratio).coerceIn(minThumbHeightPx, maxThumbHeightPx)
        } else {
            minThumbHeightPx
        }

        val availableDragSpace = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val firstVisibleItemIndex by remember {
            derivedStateOf { listState.firstVisibleItemIndex }
        }

        val thumbOffsetPx = if (isDragging) {
            (dragY - thumbHeightPx / 2f).coerceIn(0f, availableDragSpace)
        } else {
            val fraction = (firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            fraction * availableDragSpace
        }

        // Section bubble preview text
        val sectionText = remember(currentTargetIndex, isDragging) {
            if (isDragging && getSectionText != null) {
                getSectionText(currentTargetIndex)
            } else {
                ""
            }
        }

        // Floating Section Bubble Indicator
        AnimatedVisibility(
            visible = isDragging && sectionText.isNotBlank(),
            enter = fadeIn(tween(100)) + scaleIn(tween(100)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = with(density) { (-56).dp.roundToPx() },
                        y = (thumbOffsetPx + thumbHeightPx / 2f - with(density) { 24.dp.toPx() })
                            .roundToInt()
                            .coerceIn(0, (trackHeightPx - with(density) { 48.dp.toPx() }).roundToInt()),
                    )
                },
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MpxPrimary,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 46.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sectionText.take(2),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                }
            }
        }

        // Draggable Hit Target & Visual Scrollbar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount, trackHeightPx, thumbHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragY = offset.y
                            val fraction = ((dragY - thumbHeightPx / 2f) / availableDragSpace).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentTargetIndex = targetIndex
                            scope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            dragY = change.position.y
                            val fraction = ((dragY - thumbHeightPx / 2f) / availableDragSpace).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentTargetIndex = targetIndex
                            scope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                    )
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            // Background subtle track line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .padding(end = 2.dp)
                    .background(trackColor, RoundedCornerShape(2.dp)),
            )

            // Draggable Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .width(6.dp)
                    .size(width = 6.dp, height = with(density) { thumbHeightPx.toDp() })
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDragging) thumbColor else thumbColor.copy(alpha = 0.85f)),
            )
        }
    }
}

/**
 * Fast draggable scrollbar with real-time section preview bubble for long Compose LazyVerticalGrid lists.
 */
@Composable
fun GridFastScrollbar(
    gridState: LazyGridState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    getSectionText: ((Int) -> String)? = null,
    thumbColor: Color = MpxPrimary,
    trackColor: Color = MpxSurfaceElevated.copy(alpha = 0.4f),
) {
    if (itemCount <= 4) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }

    val isScrollInProgress = gridState.isScrollInProgress

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "gridFastScrollAlpha",
    )

    if (alpha <= 0.01f && !isDragging) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .alpha(alpha),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val minThumbHeightPx = with(density) { 48.dp.toPx() }
        val maxThumbHeightPx = (trackHeightPx / 3f).coerceAtLeast(minThumbHeightPx)

        val visibleItemsCount = gridState.layoutInfo.visibleItemsInfo.size
        val thumbHeightPx = if (itemCount > 0) {
            val ratio = visibleItemsCount.toFloat() / itemCount.toFloat()
            (trackHeightPx * ratio).coerceIn(minThumbHeightPx, maxThumbHeightPx)
        } else {
            minThumbHeightPx
        }

        val availableDragSpace = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val firstVisibleItemIndex by remember {
            derivedStateOf { gridState.firstVisibleItemIndex }
        }

        val thumbOffsetPx = if (isDragging) {
            (dragY - thumbHeightPx / 2f).coerceIn(0f, availableDragSpace)
        } else {
            val fraction = (firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            fraction * availableDragSpace
        }

        val sectionText = remember(currentTargetIndex, isDragging) {
            if (isDragging && getSectionText != null) {
                getSectionText(currentTargetIndex)
            } else {
                ""
            }
        }

        AnimatedVisibility(
            visible = isDragging && sectionText.isNotBlank(),
            enter = fadeIn(tween(100)) + scaleIn(tween(100)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = with(density) { (-56).dp.roundToPx() },
                        y = (thumbOffsetPx + thumbHeightPx / 2f - with(density) { 24.dp.toPx() })
                            .roundToInt()
                            .coerceIn(0, (trackHeightPx - with(density) { 48.dp.toPx() }).roundToInt()),
                    )
                },
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MpxPrimary,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 46.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sectionText.take(2),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount, trackHeightPx, thumbHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragY = offset.y
                            val fraction = ((dragY - thumbHeightPx / 2f) / availableDragSpace).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentTargetIndex = targetIndex
                            scope.launch {
                                gridState.scrollToItem(targetIndex)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            dragY = change.position.y
                            val fraction = ((dragY - thumbHeightPx / 2f) / availableDragSpace).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentTargetIndex = targetIndex
                            scope.launch {
                                gridState.scrollToItem(targetIndex)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                    )
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .padding(end = 2.dp)
                    .background(trackColor, RoundedCornerShape(2.dp)),
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .width(6.dp)
                    .size(width = 6.dp, height = with(density) { thumbHeightPx.toDp() })
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDragging) thumbColor else thumbColor.copy(alpha = 0.85f)),
            )
        }
    }
}
