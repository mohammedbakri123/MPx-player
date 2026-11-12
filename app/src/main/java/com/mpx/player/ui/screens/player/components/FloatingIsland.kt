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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxTheme

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
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
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
