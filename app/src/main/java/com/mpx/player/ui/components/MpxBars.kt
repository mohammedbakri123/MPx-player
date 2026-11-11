package com.mpx.player.ui.components
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.mpx.player.R

/**
 * M2.5 bar atoms (#106), dock restyled mpvEx-style (#157). The dock is a
 * Material3 NavigationBar: 28dp rounded top corners, labels always shown,
 * tap-only tabs — tabs NEVER switch by swipe (UX law). The selected tab
 * gets an identity-blue pill (primary at 15% alpha) instead of a grey
 * container, and the bar sits on the raised surface (#364) so it reads
 * as its own bar against the page. The mini-player is a full-width
 * strip above the dock. The blur surface uses Haze 1.6.0: callers own
 * the [HazeState] and apply `Modifier.haze(state)` to the content
 * behind the bar.
 */

/** One dock tab: icon + label in the NavigationBar, accent-only selection. */
@Composable
fun RowScope.MpxDockTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MpxTheme.colors.primary,
            unselectedIconColor = MpxTheme.colors.textMuted,
            selectedTextColor = MpxTheme.colors.primary,
            unselectedTextColor = MpxTheme.colors.textMuted,
            indicatorColor = MpxTheme.colors.primary.copy(alpha = 0.15f),
        ),
    )
}

/**
 * Dock bar, mpvEx-style (#157): Material3 NavigationBar with 28dp rounded
 * top corners on a slightly raised container (#364: surfaceRaised, not
 * black — the dock must read as its own bar against the page, in every
 * theme). Height is the standard NavigationBar
 * height (the bar's own 80dp, not the old custom 64dp dock) — the tab switch
 * animation (slide + fade) lives on the NavHost in MainActivity, and the
 * show/hide slide lives on the Scaffold bottomBar slot there.
 */
@Composable
fun MpxBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        containerColor = MpxTheme.colors.surfaceRaised,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
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
            .background(MpxTheme.colors.black)
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
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) pauseIcon else playIcon,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    tint = MpxTheme.colors.textSecondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MpxTheme.colors.primary.copy(alpha = 0.25f)),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MpxTheme.colors.primary),
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
                backgroundColor = MpxTheme.colors.black,
                tint = HazeDefaults.tint(Color.Black.copy(alpha = 0.6f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Compact top bar for the main tabs (Local, Music, Me). MX's toolbar
 * height (?actionBarSize = 56dp) instead of Material 3's 64dp TopAppBar,
 * so the title sits closer to the top edge. Same slots as TopAppBar
 * (title / navigationIcon / actions) and the same horizontal spacing
 * (title 16dp from the left edge, actions 4dp from the right), so the
 * swap is mechanical. The status-bar inset is kept (statusBarsPadding)
 * so the title never sits under the clock / battery icons.
 */
@Composable
fun MpxCompactTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MpxTheme.colors.black)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon()
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            title()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}
