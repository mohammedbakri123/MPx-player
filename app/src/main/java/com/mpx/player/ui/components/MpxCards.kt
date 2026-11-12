package com.mpx.player.ui.components
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxIconSize
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxSpaceXxs
import com.mpx.player.ui.theme.MpxTouchTarget

/**
 * M2.5 card + row atoms (#106). Tonal black surfaces, no decorative
 * borders. Rows are full-width, 48dp minimum touch height.
 */

@Composable
fun MpxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MpxShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceRaised),
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            shape = MpxShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceRaised),
        ) { content() }
    }
}

/** One-line section header: small, muted, uppercase. */
@Composable
fun MpxSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(horizontal = MpxSpaceL, vertical = MpxSpaceS),
        style = MaterialTheme.typography.labelSmall,
        color = MpxTheme.colors.textMuted,
    )
}

/**
 * Standard list row: optional leading icon, title + subtitle, optional
 * trailing slot. Title is single-line ellipsized; subtitle muted.
 */
@Composable
fun MpxListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MpxTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = MpxSpaceL, vertical = MpxSpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MpxTheme.colors.textSecondary,
                modifier = Modifier.size(MpxIconSize),
            )
            Spacer(modifier = Modifier.size(MpxSpaceM))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(MpxSpaceXxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.size(MpxSpaceM))
            trailing()
        }
    }
}
