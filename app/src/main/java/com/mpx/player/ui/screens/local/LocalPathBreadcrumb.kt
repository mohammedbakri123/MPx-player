package com.mpx.player.ui.screens.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.components.MpxBadge
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxTheme

/**
 * One level of the Local path visualizer (#146). [onClick] is null for the
 * current level (rendered as plain text); a non-null [onClick] makes the
 * segment a blue, tappable link back to that level.
 */
data class PathSegment(
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * Local's path visualizer / breadcrumb (#146, root label renamed to
 * "Home" by #170). Replaces the old "FOLDERS n + Sort:" section-header
 * row: at the root it shows just "Home"; inside a folder it shows
 * "Home / FolderName". Every ancestor segment is tappable and navigates
 * is plain text and ellipsizes instead of pushing the row off-screen.
 *
 * Segments must be ordered root-first with the current level last. The
 * list form is deliberate: today Local navigation is one level deep
 * (root -> folder), but the row already renders deeper stacks, so a
 * future folder stack only has to pass more segments.
 *
 * Built from #105 tokens — no hardcoded colours. Segments get 14dp of
 * vertical padding so each tappable segment is a 48dp touch target.
 */
@Composable
fun LocalPathBreadcrumb(
    segments: List<PathSegment>,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MpxSpaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.titleSmall,
                    color = MpxTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = MpxSpaceS),
                )
            }
            val onClick = segment.onClick
            if (onClick != null) {
                Box(
                    modifier = Modifier
                        .clip(MpxShapes.small)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 6.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MpxTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                // Current level: plain text, flexes (without filling) so a
                // long folder name ellipsizes instead of pushing the badge
                // — or the row — off-screen.
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 6.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MpxTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (badgeText != null) {
            Spacer(modifier = Modifier.weight(1f))
            MpxBadge(text = badgeText, highlighted = true)
        }
    }
}
