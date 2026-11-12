package com.mpx.player.ui.components.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.domain.model.MeTool
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun ToolGridItem(
    tool: MeTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MpxShapes.small)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon + badge: no tile background or border behind the glyph (issue
        // #162) — the icon sits bare in the 52dp zone, matching the quiet
        // treatment of the preference rows. The badge sits on the unclipped
        // outer box, overlapping the icon zone's top-end corner cleanly.
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (tool.isAvailable) MpxTheme.colors.primary else MpxTheme.colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
            if (tool.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .heightIn(min = 14.dp)
                        .background(
                            if (tool.isAvailable) MpxTheme.colors.primary else MpxTheme.colors.textMuted,
                            MpxShapes.small,
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tool.badge,
                        color = if (tool.isAvailable) MpxTheme.colors.textPrimary else MpxTheme.colors.black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            // Single-line labels truncated mid-word in the 4-column grid
            // ("Network Stre…"); two centered lines fit every tool name.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
        )
    }
}

@Composable
fun PreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bare 24dp icon, no tile background or border (issue #162) — the
        // same quiet treatment as the Preferences rows. 12dp gap matches
        // the shared PreferenceRow; dividers start at 52dp (16 + 24 + 12).
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MpxTheme.colors.primary,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}
