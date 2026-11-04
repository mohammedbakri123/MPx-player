package com.mpx.player.ui.screens.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.components.MpxBadge
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxTextMuted

/**
 * Local's shared section header (#107): uppercase muted label, count
 * badge, optional accent badge, and a blue sort action on the right.
 * Replaces the three hand-rolled header rows (folder contents, files,
 * folders) that duplicated this layout. Built from the #106 badge atom
 * and #105 tokens — no hardcoded colours or radii.
 */
@Composable
fun LocalSectionHeader(
    title: String,
    count: Int,
    sortLabel: String,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MpxSpaceL, vertical = MpxSpaceS),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MpxTextMuted,
            )
            Spacer(modifier = Modifier.size(MpxSpaceS))
            MpxBadge(text = count.toString())
            if (badgeText != null) {
                Spacer(modifier = Modifier.size(6.dp))
                MpxBadge(text = badgeText, highlighted = true)
            }
        }
        Row(
            modifier = Modifier
                .clickable(onClick = onSortClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MpxPrimary,
            )
        }
    }
}
