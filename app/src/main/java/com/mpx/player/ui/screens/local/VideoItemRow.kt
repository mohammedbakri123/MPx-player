package com.mpx.player.ui.screens.local
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.AsyncVideoThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

private fun formatVideoDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val millis = epochSeconds * 1000L
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
fun VideoItemRow(
    video: VideoFile,
    showSize: Boolean = true,
    showDate: Boolean = false,
    showDuration: Boolean = true,
    showLocation: Boolean = true,
    showThumbnails: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Video thumbnail with duration overlay
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 48.dp)
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncVideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxSize(),
                iconSize = 24.dp,
                showThumbnails = showThumbnails,
            )

            if (showDuration && video.formattedDuration.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(MpxTheme.colors.black.copy(alpha = 0.75f), MpxShapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = MpxTheme.colors.textPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        // Title, folder name, and size
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (video.isRemovable) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MpxTheme.colors.primary.copy(alpha = 0.18f), MpxShapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_sd_card),
                            color = MpxTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                        )
                    }
                }

            }

            val metaParts = mutableListOf<String>()
            if (showSize && video.formattedSize.isNotBlank()) {
                metaParts.add(video.formattedSize)
            }
            if (showDate) {
                val dateStr = formatVideoDate(video.dateModified)
                if (dateStr.isNotBlank()) {
                    metaParts.add(dateStr)
                }
            }

            if (showLocation || metaParts.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showLocation) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = video.folderName.ifBlank { "Videos" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    metaParts.forEachIndexed { idx, part ->
                        val prefix = if (showLocation || idx > 0) " • " else ""
                        Text(
                            text = "$prefix$part",
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItemGridCard(
    video: VideoFile,
    isFavourite: Boolean = false,
    showSize: Boolean = true,
    showDate: Boolean = false,
    showDuration: Boolean = true,
    showLocation: Boolean = true,
    showThumbnails: Boolean = true,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MpxShapes.large,
        colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(MpxShapes.small)
                    .background(MpxTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncVideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 36.dp,
                    showThumbnails = showThumbnails,
                )

                if (showDuration && video.formattedDuration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(MpxTheme.colors.black.copy(alpha = 0.8f), MpxShapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (video.isRemovable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.85f), MpxShapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_sd_card),
                            color = MpxTheme.colors.black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                        )
                    }
                }


            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val metaParts = mutableListOf<String>()
            if (showSize && video.formattedSize.isNotBlank()) {
                metaParts.add(video.formattedSize)
            }
            if (showDate) {
                val dateStr = formatVideoDate(video.dateModified)
                if (dateStr.isNotBlank()) {
                    metaParts.add(dateStr)
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLocation) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = video.folderName.ifBlank { "Videos" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                metaParts.forEachIndexed { idx, part ->
                    val prefix = if (showLocation || idx > 0) " • " else ""
                    Text(
                        text = "$prefix$part",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleFavourite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.music_menu_toggle_favourite),
                        tint = if (isFavourite) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
