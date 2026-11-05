package com.mpx.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxDockHeight
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.mpx.player.R

/**
 * M2.5 bar atoms (#106). The dock is full-width, 64dp, tap-only —
 * tabs NEVER switch by swipe (UX law). The mini-player is a full-width
 * strip above the dock. The blur surface uses Haze 1.6.0: callers own
 * the [HazeState] and apply `Modifier.haze(state)` to the content
 * behind the bar.
 */

/** One dock tab: icon + 11sp label, colour-only selection. */
@Composable
fun RowScope.MpxDockTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MpxPrimary else MpxTextMuted,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MpxPrimary else MpxTextMuted,
            maxLines = 1,
        )
    }
}

/** Full-width dock bar: black, hairline top edge, tap-only tabs. */
@Composable
fun MpxBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MpxDockHeight)
            .background(MpxBlack)
            .border(MpxHairline, MpxBorder),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Mini-player strip above the dock: thumbnail slot, title, play/pause.
 * Progress is the blue bar at the strip's bottom edge.
 */
@Composable
fun MpxMiniPlayerStrip(
    title: String,
    isPlaying: Boolean,
    playIcon: ImageVector,
    pauseIcon: ImageVector,
    progress: Float,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MpxBlack)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnail != null) {
                Box(modifier = Modifier.size(56.dp)) { thumbnail() }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) pauseIcon else playIcon,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    tint = MpxTextSecondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MpxPrimary.copy(alpha = 0.25f)),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MpxPrimary),
            )
        }
    }
}

/**
 * Haze blur surface: blurred backdrop + content on top. Blur radius
 * 20dp, black tint — subtle, standard, never decorative colour.
 * The caller passes the shared [HazeState] and marks the scrolling
 * content behind this surface with `Modifier.haze(state)`.
 */
@Composable
fun MpxBlurSurface(
    state: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.hazeEffect(
            state = state,
            style = HazeDefaults.style(
                blurRadius = 20.dp,
                backgroundColor = MpxBlack,
                tint = HazeDefaults.tint(Color.Black.copy(alpha = 0.6f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
