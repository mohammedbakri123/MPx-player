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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.ui.components.AsyncFolderThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mpx.player.ui.theme.MpxShapes

private fun formatFolderDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val millis = if (epochMillis < 10_000_000_000L) epochMillis * 1000L else epochMillis
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
fun FolderItemRow(
    folder: MediaFolder,
    showSize: Boolean = true,
    showDate: Boolean = false,
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
        // Real MediaStore folder thumbnail with honest vector fallback and caching.
        // Borderless tile (#143): no thumbnail border, no count badge.
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MpxShapes.small)
                    .background(MpxTheme.colors.black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncFolderThumbnail(
                    folder = folder,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 28.dp,
                    showThumbnails = showThumbnails,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Title and subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (folder.isStorageRemovable) {
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

            // Subtitle is a single line: the real folder size in a small
            // rounded box, matching MX's one-subtitle-slot row shape
            // (list_grid_media: one title, one subtitle). The count is not
            // shown on the tile (#143); the exact count stays one tap away
            // in the folder view subtitle (#155: the tile menu is gone).
            // No subfolder counts: MediaFolder has no such field and a
            // recursive scan is out of scope.
            val folderDateStr = formatFolderDate(folder.lastModified)
            val hasSubtitle = (showSize && folder.totalSizeFormatted.isNotBlank()) ||
                (showDate && folderDateStr.isNotBlank())
            if (hasSubtitle) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSize && folder.totalSizeFormatted.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MpxTheme.colors.surfaceVariant,
                                    MpxShapes.extraSmall,
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = folder.totalSizeFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (showDate && folderDateStr.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = folderDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun FolderItemGridCard(
    folder: MediaFolder,
    showSize: Boolean = true,
    showDate: Boolean = false,
    showThumbnails: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MpxShapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(MpxShapes.small)
                    .background(MpxTheme.colors.black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncFolderThumbnail(
                    folder = folder,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 40.dp,
                    showThumbnails = showThumbnails,
                )
                if (folder.isStorageRemovable) {
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
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Subtitle is a single line: the real size in a small rounded
            // box, as in the list row. The count is not shown on the tile
            // (#143). No subfolder counts (no such field exists).
            val gridDateStr = formatFolderDate(folder.lastModified)
            val gridHasSubtitle = (showSize && folder.totalSizeFormatted.isNotBlank()) ||
                (showDate && gridDateStr.isNotBlank())
            if (gridHasSubtitle) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSize && folder.totalSizeFormatted.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MpxTheme.colors.surfaceVariant,
                                    MpxShapes.extraSmall,
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = folder.totalSizeFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (showDate && gridDateStr.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = gridDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
