package com.mpx.player.ui.components
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mpx.player.ui.theme.MpxShapes

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
    thumbColor: Color = MpxTheme.colors.primary,
    trackColor: Color = Color.Transparent,
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

        // Issue #182: the thumb jumped in steps because the fraction used
        // only the discrete firstVisibleItemIndex. Include the pixel
        // offset inside the first visible item so the thumb glides
        // continuously with the scroll instead of jumping item by item.
        val scrollFraction by remember {
            derivedStateOf {
                val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                val itemProgress = if (firstItemSize > 0) {
                    listState.firstVisibleItemScrollOffset.toFloat() / firstItemSize.toFloat()
                } else {
                    0f
                }
                ((listState.firstVisibleItemIndex.toFloat() + itemProgress) / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }

        val thumbOffsetPx = if (isDragging) {
            (dragY - thumbHeightPx / 2f).coerceIn(0f, availableDragSpace)
        } else {
            scrollFraction * availableDragSpace
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
                shape = MpxShapes.small,
                color = MpxTheme.colors.primary,
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
                        color = MpxTheme.colors.textPrimary,
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
                    .background(trackColor, MpxShapes.extraSmall),
            )

            // Draggable Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .width(6.dp)
                    .size(width = 6.dp, height = with(density) { thumbHeightPx.toDp() })
                    .clip(MpxShapes.extraSmall)
                    .background(if (isDragging) thumbColor else thumbColor.copy(alpha = 0.85f)),
            )
        }
    }
}

/**
 * Fast draggable scrollbar with real-time section preview bubble for long Compose LazyVerticalGrid lists.
 *
 * Issue #236: the thumb position and the drag target are computed from the
 * grid's PIXEL scroll range (scroll offset / max scroll offset), not from
 * item indices — a grid scrolls in rows, so an item-index fraction tops
 * out at (N - columns) / (N - 1) and the thumb never reaches the bottom.
 * [columns] is the grid's real column count and [headerItems] the number
 * of leading full-span items (the Playlists grid's 3 action rows); both
 * feed the row count and the row -> item-index mapping, so the drag target
 * is the exact inverse of the thumb position.
 */
@Composable
fun GridFastScrollbar(
    gridState: LazyGridState,
    itemCount: Int,
    columns: Int,
    headerItems: Int = 0,
    modifier: Modifier = Modifier,
    getSectionText: ((Int) -> String)? = null,
    thumbColor: Color = MpxTheme.colors.primary,
    trackColor: Color = Color.Transparent,
) {
    if (itemCount <= 4) return

    val safeColumns = columns.coerceAtLeast(1)
    val safeHeaders = headerItems.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    // Rows: each leading full-span item takes one row; the rest pack
    // [safeColumns] per row.
    val gridRows = (itemCount - safeHeaders + safeColumns - 1) / safeColumns
    val rowCount = safeHeaders + gridRows

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

    // Issue #236: pixel height of the leading full-span header block.
    // Header rows have their own heights, so they cannot be treated as
    // grid rows. Measured once while the grid sits at scroll 0 — it always
    // starts there — as (first grid item's offset - top padding); until the
    // measurement lands, the geometry below estimates one grid row pitch
    // per header. (Grids without headers never need this: safeHeaders is 0.)
    var headerBlockPx by remember { mutableFloatStateOf(-1f) }
    SideEffect {
        if (headerBlockPx < 0f && safeHeaders > 0) {
            val info = gridState.layoutInfo
            if (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
                val firstGrid = info.visibleItemsInfo.firstOrNull { it.index >= safeHeaders }
                if (firstGrid != null) {
                    headerBlockPx = firstGrid.offset.y.toFloat() - info.beforeContentPadding.toFloat()
                }
            }
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

        // Issue #236: pixel-exact scroll geometry — the fraction is the
        // scroll offset over the grid's total scroll range, so it is 0.0
        // at the top and exactly 1.0 at max scroll. Worked numbers (8
        // items, 2 columns, 4 rows, row 200px + 12px spacing, 8px top /
        // 200px bottom padding, 500px viewport): total height 1044px, max
        // scroll 544px; at max scroll the first visible item is #4 (row 2)
        // with offset -112px, so scroll = 8 + 2*212 + 112 = 544px and the
        // fraction is 1.0 (the old item formula gave (4 + 0) / 7 = 0.57).
        // For 100 items the old formula stopped at 0.975; the new one
        // reaches 1.0 as well. The geometry is a State object (not a `by`
        // value) so the drag handler below always reads it live.
        val scrollGeometryState = remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                val visible = info.visibleItemsInfo
                if (visible.isEmpty()) return@derivedStateOf null
                val firstGrid = visible.firstOrNull { it.index >= safeHeaders }
                val rowHeightPx = firstGrid?.size?.height
                    ?: visible.firstOrNull()?.size?.height ?: 0
                if (rowHeightPx <= 0) return@derivedStateOf null
                val spacingPx = info.mainAxisItemSpacing.coerceAtLeast(0)
                val pitchPx = rowHeightPx + spacingPx
                val headerPx = if (headerBlockPx >= 0f) headerBlockPx
                    else safeHeaders * pitchPx.toFloat()
                // Total content height: top padding + header block + grid
                // rows + the gaps between rows (one after every row except
                // the last; the header block already holds the gaps after
                // the header rows) + bottom padding.
                val totalHeightPx = info.beforeContentPadding + headerPx +
                    gridRows * rowHeightPx +
                    (rowCount - 1 - safeHeaders).coerceAtLeast(0) * spacingPx +
                    info.afterContentPadding
                val maxScrollPx = (totalHeightPx - info.viewportSize.height)
                    .coerceAtLeast(0f)
                // Scroll offset from the first visible grid item: its
                // content-top minus its viewport offset. (When only header
                // rows are visible — impossible while the header block is
                // shorter than the viewport — fall back to the item-based
                // position, which is exact for the first header row.)
                val scrollPx: Float = if (firstGrid != null) {
                    val gridRow = (firstGrid.index - safeHeaders) / safeColumns
                    info.beforeContentPadding + headerPx + gridRow * pitchPx -
                        firstGrid.offset.y
                } else {
                    (info.beforeContentPadding +
                        gridState.firstVisibleItemIndex * pitchPx +
                        gridState.firstVisibleItemScrollOffset).toFloat()
                }
                GridScrollGeometry(
                    maxScrollPx = maxScrollPx,
                    beforePaddingPx = info.beforeContentPadding,
                    headerPx = headerPx,
                    pitchPx = pitchPx,
                    gridRows = gridRows,
                    scrollPx = scrollPx,
                )
            }
        }
        val scrollFraction = scrollGeometryState.value?.let { geo ->
            if (geo.maxScrollPx > 0f) (geo.scrollPx / geo.maxScrollPx).coerceIn(0f, 1f) else 0f
        } ?: 0f

        val thumbOffsetPx = if (isDragging) {
            (dragY - thumbHeightPx / 2f).coerceIn(0f, availableDragSpace)
        } else {
            scrollFraction * availableDragSpace
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
                shape = MpxShapes.small,
                color = MpxTheme.colors.primary,
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
                        color = MpxTheme.colors.textPrimary,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount, safeColumns, safeHeaders, trackHeightPx, thumbHeightPx) {
                    // Issue #236: the drag target is the exact inverse of
                    // the thumb fraction — an absolute scroll position,
                    // converted back to (item index, scroll offset).
                    fun dragTo(y: Float) {
                        val geo = scrollGeometryState.value ?: return
                        dragY = y
                        val fraction = ((y - thumbHeightPx / 2f) / availableDragSpace).coerceIn(0f, 1f)
                        val targetScrollPx = (fraction * geo.maxScrollPx).roundToInt()
                        val (targetIndex, targetOffset) = geo.targetForScroll(
                            targetScrollPx, safeHeaders, safeColumns, itemCount,
                        )
                        currentTargetIndex = targetIndex
                        scope.launch {
                            gridState.scrollToItem(targetIndex, targetOffset)
                        }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragTo(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            dragTo(change.position.y)
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
                    .background(trackColor, MpxShapes.extraSmall),
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .width(6.dp)
                    .size(width = 6.dp, height = with(density) { thumbHeightPx.toDp() })
                    .clip(MpxShapes.extraSmall)
                    .background(if (isDragging) thumbColor else thumbColor.copy(alpha = 0.85f)),
            )
        }
    }
}

/**
 * Issue #236: pixel-exact scroll geometry for [GridFastScrollbar].
 * [scrollPx] is the viewport top's distance from the content top and
 * [maxScrollPx] the distance at max scroll, so `scrollPx / maxScrollPx`
 * is 0.0 at the top and exactly 1.0 at the bottom. All values are px.
 */
private data class GridScrollGeometry(
    val maxScrollPx: Float,
    val beforePaddingPx: Int,
    val headerPx: Float,
    val pitchPx: Int,
    val gridRows: Int,
    val scrollPx: Float,
) {
    /**
     * Inverse of the thumb fraction: the (item index, scroll offset) that
     * puts the viewport top exactly at [targetScrollPx]. Grid rows map to
     * their first item; inside the header block the headers share the
     * block height evenly (they are uniform action rows in practice).
     */
    fun targetForScroll(
        targetScrollPx: Int,
        headerItems: Int,
        columns: Int,
        itemCount: Int,
    ): Pair<Int, Int> {
        val headerTop = beforePaddingPx + headerPx
        if (targetScrollPx < headerTop) {
            if (headerItems <= 0) return 0 to 0
            val perHeader = headerPx / headerItems
            if (perHeader <= 0f) return 0 to 0
            val headerIndex = ((targetScrollPx - beforePaddingPx) / perHeader)
                .toInt().coerceIn(0, headerItems - 1)
            val offset = (targetScrollPx - (beforePaddingPx + headerIndex * perHeader))
                .roundToInt().coerceAtLeast(0)
            return headerIndex to offset
        }
        if (gridRows <= 0) return (itemCount - 1).coerceAtLeast(0) to 0
        val gridRow = ((targetScrollPx - headerTop) / pitchPx)
            .coerceIn(0f, (gridRows - 1).toFloat()).toInt()
        val targetIndex = (headerItems + gridRow * columns).coerceIn(0, itemCount - 1)
        val offset = (targetScrollPx - (headerTop + gridRow * pitchPx))
            .roundToInt().coerceAtLeast(0)
        return targetIndex to offset
    }
}
