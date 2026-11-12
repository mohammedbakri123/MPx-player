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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.ui.components.AsyncAlbumThumbnail
import com.mpx.player.ui.theme.MpxIconSize
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.ui.theme.MpxTouchTarget

/**
 * The shared audio album tiles (#185): the Albums tab's rows and grid
 * cards, built to the same spec as [AudioTrackRow] / [AudioTrackGridCard]
 * (same 16dp/10dp padding, 14dp thumb gap, selection tint + check badge)
 * so Music Albums looks and behaves like the Home page. The subtitle is
 * always "artist • N tracks". Albums have no per-album options dialog,
 * so there is no options button — while selecting, the multi-select
 * action bar owns the actions.
 *
 * #144: isSelected tints the tile and shows a check on the thumbnail;
 * onLongClick enters/toggles multi-select.
 */
@Composable
fun AudioAlbumRow(
    album: AudioAlbum,
    showThumbnails: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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
            .padding(horizontal = MpxSpaceL, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square cover thumb with selected check.
        Box(
            modifier = Modifier
                .size(MpxTouchTarget)
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncAlbumThumbnail(
                album = album,
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
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.music_album_tracks_format, album.artist, album.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AudioAlbumGridCard(
    album: AudioAlbum,
    showThumbnails: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
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
                    .height(110.dp)
                    .clip(MpxShapes.small)
                    .background(MpxTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncAlbumThumbnail(
                    album = album,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = MpxTouchTarget,
                    showThumbnails = showThumbnails,
                )

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
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.music_album_tracks_format, album.artist, album.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
