package com.mpx.player.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal indicating whether the host window is running on Android TV / Leanback.
 */
val LocalIsTv = compositionLocalOf { false }

/**
 * Draws a crisp D-pad / keyboard focus ring (and optional subtle accent tint) on top of
 * the composable when focused. Must appear before `.clickable` / `.combinedClickable` or
 * interactive Material components in the modifier chain so `onFocusChanged` observes
 * the component's focus target.
 */
fun Modifier.mpxFocusHighlight(
    shape: Shape = MpxShapes.medium,
    borderWidth: Dp = 2.dp,
    showBackgroundTint: Boolean = true,
    focusedColor: Color? = null,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val primaryColor = focusedColor ?: MpxTheme.colors.primary

    this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .drawWithContent {
            drawContent()
            if (isFocused) {
                val outline = shape.createOutline(size, layoutDirection, this)
                if (showBackgroundTint) {
                    drawOutline(
                        outline = outline,
                        color = primaryColor.copy(alpha = 0.12f),
                    )
                }
                drawOutline(
                    outline = outline,
                    color = primaryColor,
                    style = Stroke(width = borderWidth.toPx()),
                )
            }
        }
}
