package com.mpx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.ui.components.AsyncFolderThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatFolderDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val millis = if (epochMillis < 10_000_000_000L) epochMillis * 1000L else epochMillis
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
fun FolderItemRow(
    folder: MediaFolder,
    showCount: Boolean = true,
    showSize: Boolean = true,
    showDate: Boolean = false,
    showThumbnails: Boolean = true,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
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
        // Red numeric count badge anchored at the icon's top-end, matching the
        // badge slot confirmed in MX's decoded folder row (20dp high, value set
        // in code from the real video count; hidden when the folder is empty).
        // The badge overflows the corner slightly, so it sits in an unclipped
        // outer box around the clipped thumbnail.
        val badgeText = if (folder.videoCount > 99) "99+" else folder.videoCount.toString()
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncFolderThumbnail(
                    folder = folder,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 28.dp,
                    showThumbnails = showThumbnails,
                )
            }
            if (showCount && folder.videoCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Title and Video Count
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (folder.isStorageRemovable) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_sd_card),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                        )
                    }
                }

            }

            // Subtitle is a single line: the real folder size in a small
            // rounded box, matching MX's one-subtitle-slot row shape
            // (list_grid_media: one title, one subtitle). The count is NOT
            // repeated here — the red badge on the thumbnail already shows
            // it (gated by the same showCount setting), and the exact count
            // for 99+ folders stays one tap away in the folder menu dialog.
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
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(4.dp),
                                )
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = folder.totalSizeFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp), // 48dp Android touch target standard
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.folder_item_options_cd, folder.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun FolderItemGridCard(
    folder: MediaFolder,
    showCount: Boolean = true,
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_sd_card),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                        )
                    }
                }



                // Same red count badge as the list row, at the thumbnail's
                // bottom-end to avoid colliding with the SD/NEW badges.
                // Gated by showCount, like the list row badge.
                if (showCount && folder.videoCount > 0) {
                    val gridBadgeText = if (folder.videoCount > 99) "99+" else folder.videoCount.toString()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = gridBadgeText,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Subtitle is a single line: the real size in a small rounded
            // box, as in the list row. The count lives on the thumbnail
            // badge only, not repeated here. No subfolder counts (no such
            // field exists).
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
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(4.dp),
                                )
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = folder.totalSizeFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
