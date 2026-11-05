package com.mpx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.AsyncVideoThumbnail
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

/**
 * Domain model representing an item in the Continue Watching row.
 */
data class ContinueWatchingItem(
    val mediaUri: String,
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val positionMs: Long,
    val durationMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * Reusable horizontal Continue Watching row displaying up to 10 videos with saved resume positions.
 * Rendered on the Local root view above the folder list and below the shortcut chips / status strip.
 */
@Composable
fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onDismissItem: (ContinueWatchingItem) -> Unit,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Section Header with count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.continue_watching_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MpxTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(MpxShapes.small)
                    .background(MpxSurfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${items.size}",
                    color = MpxTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Horizontal scrolling row of continue watching cards
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = items,
                key = { it.mediaUri.ifBlank { it.mediaId } },
                contentType = { "continue_watching_item" },
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onDismiss = { onDismissItem(item) },
                    showThumbnails = showThumbnails,
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(MpxShapes.small)
            .clickable(onClick = onClick),
    ) {
        // Video thumbnail container with progress bar, play icon, and dismiss affordance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(MpxShapes.small)
                .background(MpxSurfaceVariant)
                .border(MpxHairline, MpxBorder, MpxShapes.small),
        ) {
            AsyncVideoThumbnail(
                mediaUri = item.mediaUri,
                mediaId = item.mediaId,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                iconSize = 26.dp,
                showThumbnails = showThumbnails,
            )

            // Centered subtle play icon overlay
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Small remove / dismiss affordance in top-end corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.continue_watching_dismiss),
                    ) {
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.continue_watching_dismiss),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }

            // Watched fraction progress bar along the bottom of the thumbnail
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .align(Alignment.BottomCenter),
                color = MpxPrimary,
                trackColor = Color.Black.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.height(5.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MpxTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
        )

        // Resume position time label
        Text(
            text = if (item.durationMs > 0L) {
                "${formatTimeMs(item.positionMs)} / ${formatTimeMs(item.durationMs)}"
            } else {
                stringResource(R.string.continue_watching_resume_at, formatTimeMs(item.positionMs))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MpxPrimary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
