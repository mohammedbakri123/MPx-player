package com.mpx.player.ui.screens.player.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxTheme

/**
 * Consumes remaining pointer downs on the [PointerEventPass.Main] pass after
 * child controls (sliders, buttons) run, preventing missed taps or drags on
 * floating chrome pills from leaking through to the root video gesture detector.
 *
 * Only tracks pointers whose initial `DOWN` was not claimed by a child control:
 * consuming pre-slop `MOVE` events belonging to a child `Slider` would abort
 * its drag-slop detector on [PointerEventPass.Final].
 */
internal fun Modifier.consumeUnconsumedPointerEvents(): Modifier = this.pointerInput(Unit) {
    val claimedEmptyIds = HashSet<PointerId>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                if (change.changedToDown() && !change.isConsumed) {
                    claimedEmptyIds.add(change.id)
                    change.consume()
                } else if (change.id in claimedEmptyIds) {
                    if (!change.isConsumed) {
                        change.consume()
                    }
                    if (!change.pressed) {
                        claimedEmptyIds.remove(change.id)
                    }
                }
            }
        }
    }
}

/**
 * Shared island material for floating pills/islands in PlayerScreen.
 *
 * Requirements:
 * - Background: dark #1A1A1A at 78% opacity (MpxTheme.colors.surfaceElevated.copy(alpha = 0.78f))
 * - Shape: 24dp rounded corners (or CircleShape for circular pills)
 * - Border: 1dp white hairline border at alpha 0.12
 * - Shadow: 8dp drop shadow
 * - Frosted blur: RenderEffect blur 16dp on Android 12+ (API 31+)
 */
@Composable
fun FloatingIsland(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = MpxTheme.colors.surfaceElevated.copy(alpha = 0.78f),
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp,
    contentAlignment: Alignment = Alignment.Center,
    consumeTouches: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = (if (consumeTouches) Modifier.consumeUnconsumedPointerEvents() else Modifier)
            .then(modifier)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clip(shape),
        contentAlignment = contentAlignment,
    ) {
        // Frosted blur backdrop layer on API 31+
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            val blurPx = 16.dp.toPx()
                            renderEffect = RenderEffect
                                .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    }
                )
                .background(backgroundColor),
        )

        // Island content on top (sharp and responsive)
        content()
    }
}
