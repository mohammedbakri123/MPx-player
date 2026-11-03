package com.mpx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.AsyncVideoThumbnail
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import java.util.Locale

fun formatVideoDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatVideoSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun VideoItemRow(
    video: VideoFile,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 16:9 Thumbnail box with duration badge overlay
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 58.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MpxSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            AsyncVideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxSize(),
                iconSize = 24.dp,
            )

            val durationStr = formatVideoDuration(video.durationMs)
            if (durationStr.isNotBlank() && durationStr != "0:00") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = durationStr,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        // Title and video metadata
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = MpxTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(3.dp))

            val sizeStr = video.sizeFormatted.ifBlank { formatVideoSize(video.sizeBytes) }
            val durationStr = formatVideoDuration(video.durationMs)
            val metadataStr = when {
                durationStr != "0:00" && sizeStr.isNotBlank() -> "$durationStr • $sizeStr"
                durationStr != "0:00" -> durationStr
                sizeStr.isNotBlank() -> sizeStr
                else -> ""
            }

            if (metadataStr.isNotBlank()) {
                Text(
                    text = metadataStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.video_item_options_cd, video.title),
                tint = MpxTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun VideoItemGridCard(
    video: VideoFile,
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MpxSurfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MpxSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncVideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 32.dp,
                )

                val durationStr = formatVideoDuration(video.durationMs)
                if (durationStr.isNotBlank() && durationStr != "0:00") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = MpxTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(2.dp))

                    val sizeStr = video.sizeFormatted.ifBlank { formatVideoSize(video.sizeBytes) }
                    val durationStr = formatVideoDuration(video.durationMs)
                    val metadataStr = when {
                        durationStr != "0:00" && sizeStr.isNotBlank() -> "$durationStr • $sizeStr"
                        durationStr != "0:00" -> durationStr
                        sizeStr.isNotBlank() -> sizeStr
                        else -> ""
                    }

                    if (metadataStr.isNotBlank()) {
                        Text(
                            text = metadataStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.video_item_options_cd, video.title),
                        tint = MpxTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
