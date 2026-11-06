package com.mpx.player.ui.screens.local
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
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

/**
 * #144: isSelected tints the row and shows a check on the thumbnail;
 * onLongClick enters/toggles multi-select. While selected the 3-dot menu
 * is hidden — the multi-select action bar owns the actions then.
 */
@Composable
fun VideoItemRow(
    video: VideoFile,
    showSize: Boolean = true,
    showDate: Boolean = false,
    showDuration: Boolean = true,
    showLocation: Boolean = true,
    showThumbnails: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showFullName: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MpxTheme.colors.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MpxTheme.colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            // #315: durationMs 0 means unknown (MediaStore reports 0 for it,
            // and a genuinely 0-length file has nothing worth showing) —
            // no badge, not "0:00". formattedDuration is "0:00" for 0 ms.
            if (showDuration && video.durationMs > 0 && video.formattedDuration.isNotBlank()) {
                VideoDurationBadge(
                    duration = video.formattedDuration,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
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
                    maxLines = if (showFullName) Int.MAX_VALUE else 1,
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
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showFullName: Boolean = false,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MpxShapes.large,
        // #144: a selected card tints with the identity-blue accent.
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MpxTheme.colors.primary.copy(alpha = 0.12f)
            else MpxTheme.colors.surfaceVariant
        ),
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

                // #315: durationMs 0 means unknown — no badge, not "0:00".
                if (showDuration && video.durationMs > 0 && video.formattedDuration.isNotBlank()) {
                    VideoDurationBadge(
                        duration = video.formattedDuration,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }

                if (isSelected) {
                    // #144: check badge (TopEnd — the SD badge owns TopStart).
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(MpxTheme.colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
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
                maxLines = if (showFullName) Int.MAX_VALUE else 1,
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
                // #144: the favourite heart hides on a selected card — the
                // multi-select action bar owns the actions while selecting.
                // #155: the 3-dot menu is gone; the heart stays.
                if (!isSelected) {
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
}

/**
 * The video duration badge shown on thumbnails (issue #163).
 *
 * One shared size for every thumbnail — list rows, grid cards, and (once the
 * search tiles adopt it) search results — so all tiles look uniform. Small
 * and quiet on purpose: 8sp semibold white text on a soft black scrim,
 * tucked 2dp into the bottom-right corner. Fixed black/white (not theme
 * colors) so it reads identically in the dark and light themes, matching
 * the search results' badge.
 */
@Composable
fun VideoDurationBadge(
    duration: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .background(Color.Black.copy(alpha = 0.65f), MpxShapes.extraSmall)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = duration,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
