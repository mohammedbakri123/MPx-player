package com.mpx.player.ui.screens.local

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
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.ui.components.AsyncAudioTrackThumbnail
import com.mpx.player.ui.screens.local.components.formatDuration
import com.mpx.player.ui.theme.MpxIconSize
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.ui.theme.MpxTouchTarget

/**
 * The shared audio track tiles (#185): the Tracks tab's rows and grid
 * cards, built to the same spec as [VideoItemRow] / [VideoItemGridCard]
 * (same 16dp/10dp padding, 14dp thumb gap, selection tint + check badge,
 * duration badge on the thumbnail) so Music Tracks looks and behaves
 * like the Home page. The subtitle is always "artist • album" — the
 * point of a track row — and the duration lives on the thumbnail, not
 * as a separate trailing text.
 *
 * #144: isSelected tints the tile and shows a check on the thumbnail;
 * onLongClick enters/toggles multi-select. While selected the options
 * button is hidden — the multi-select action bar owns the actions then.
 */
@Composable
fun AudioTrackRow(
    track: AudioTrack,
    isFavourite: Boolean = false,
    showDuration: Boolean = true,
    showThumbnails: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showFullName: Boolean = false,
    onOptionsClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit = {},
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
            .padding(horizontal = MpxSpaceL, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square cover thumb with duration badge and selected check.
        Box(
            modifier = Modifier
                .size(MpxTouchTarget)
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncAudioTrackThumbnail(
                track = track,
                modifier = Modifier.fillMaxSize(),
                iconSize = MpxIconSize,
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
            // no badge, not "0:00". durationMs is 0 for unknown duration.
            if (showDuration && track.durationMs > 0) {
                VideoDurationBadge(
                    duration = formatDuration(track.durationMs),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = MpxTheme.colors.textPrimary,
                maxLines = if (showFullName) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Issue #321: the favourite heart — audio rows had none at all
        // (neither list nor grid), so list-view users could not
        // favourite a track. Same icon, size, tint and content
        // description as the video grid card's heart (#235). It sits
        // before the options button; both hide while selected (#144).
        if (!isSelected) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onToggleFavourite,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.music_menu_toggle_favourite),
                    tint = if (isFavourite) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // The track options button stays (it opens the track dialog);
        // hidden while selected — the action bar owns the actions then.
        if (!isSelected && onOptionsClick != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(MpxTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.music_cd_track_options, track.title),
                    tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun AudioTrackGridCard(
    track: AudioTrack,
    isFavourite: Boolean = false,
    showDuration: Boolean = true,
    showThumbnails: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showFullName: Boolean = false,
    onOptionsClick: (() -> Unit)? = null,
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
                AsyncAudioTrackThumbnail(
                    track = track,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 36.dp,
                    showThumbnails = showThumbnails,
                )

                // #315: durationMs 0 means unknown — no badge, not "0:00".
                if (showDuration && track.durationMs > 0) {
                    VideoDurationBadge(
                        duration = formatDuration(track.durationMs),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }

                if (isSelected) {
                    // #144: check badge (TopEnd — the same spot as the video card).
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
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MpxTheme.colors.textPrimary,
                maxLines = if (showFullName) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                // Issue #321: the favourite heart — the audio grid card
                // had none either. Same icon, size, tint and content
                // description as the video grid card's heart (#235).
                // #144: the options button hides on a selected card — the
                // multi-select action bar owns the actions while selecting.
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
                if (!isSelected && onOptionsClick != null) {
                    IconButton(onClick = onOptionsClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.music_cd_track_options, track.title),
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
