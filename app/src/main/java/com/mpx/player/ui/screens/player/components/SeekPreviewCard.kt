package com.mpx.player.ui.screens.player.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxTheme

internal fun seekPreviewCardWidthDp(videoWidth: Int, videoHeight: Int, heightDp: Float = 90f): Dp {
    val ratio = if (videoWidth > 0 && videoHeight > 0) {
        (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.65f, 2.15f)
    } else {
        16f / 9f
    }
    return (heightDp * ratio).coerceIn(68f, 194f).dp
}

/**
 * YouTube / MX Player style floating preview shown while scrubbing the seek bar.
 * Displays the decoded video frame with a timestamp badge when available, and falls
 * back gracefully to a compact timestamp pill while loading or on streams/audio.
 */
@Composable
fun SeekPreviewCard(
    targetTimeMs: Long,
    previewBitmap: Bitmap?,
    chapterTitle: String?,
    videoWidth: Int,
    videoHeight: Int,
    modifier: Modifier = Modifier,
) {
    val cardWidth = seekPreviewCardWidthDp(videoWidth, videoHeight)
    val imageBitmap = remember(previewBitmap) { previewBitmap?.asImageBitmap() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (imageBitmap != null) {
            val cardShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(90.dp)
                    .shadow(elevation = 8.dp, shape = cardShape, clip = false)
                    .clip(cardShape)
                    .background(MpxTheme.colors.black)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), cardShape),
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatTimeMs(targetTimeMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!chapterTitle.isNullOrBlank()) {
                Text(
                    text = chapterTitle,
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .widthIn(max = cardWidth)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            val pillShape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .shadow(elevation = 6.dp, shape = pillShape, clip = false)
                    .clip(pillShape)
                    .background(MpxTheme.colors.surfaceElevated.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), pillShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatTimeMs(targetTimeMs),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!chapterTitle.isNullOrBlank()) {
                    Text(
                        text = chapterTitle,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }
        }
    }
}
