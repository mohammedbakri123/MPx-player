package com.mpx.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxTheme.colors.primary
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxTheme.colors.surfaceRaised
import com.mpx.player.ui.theme.MpxTheme.colors.textMuted
import com.mpx.player.ui.theme.MpxTheme.colors.textPrimary

/**
 * M2.5 tile atoms (#106). Data-free: callers pass strings and a
 * thumbnail slot. Folder icon follows the app identity (blue, #101).
 */

/** Folder tile: blue folder glyph in a raised rounded box + name + count. */
@Composable
fun MpxFolderTile(
    name: String,
    countText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MpxShapes.small,
            color = MpxTheme.colors.surfaceRaised,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MpxTheme.colors.primary)
            }
        }
        Spacer(modifier = Modifier.size(MpxSpaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Video tile: 16:9 thumbnail slot on top, title + meta below.
 * The thumbnail is a caller slot so this atom stays engine-free.
 */
@Composable
fun MpxVideoTile(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MpxShapes.small),
        ) {
            thumbnail()
        }
        Spacer(modifier = Modifier.height(MpxSpaceS))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MpxTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
