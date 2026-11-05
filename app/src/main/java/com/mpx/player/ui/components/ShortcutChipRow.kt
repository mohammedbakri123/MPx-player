package com.mpx.player.ui.components
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxShapes

data class ShortcutChipData(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isPrimary: Boolean = false,
    val badge: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun ShortcutChipRow(
    onOpenMusic: () -> Unit,
    onOpenTransfer: () -> Unit,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        ShortcutChipData(
            id = "music",
            label = stringResource(R.string.nav_music),
            icon = Icons.Outlined.MusicNote,
            isPrimary = true,
            onClick = onOpenMusic,
        ),
        ShortcutChipData(
            id = "transfer",
            label = stringResource(R.string.nav_transfer),
            icon = Icons.Outlined.SwapHoriz,
            onClick = onOpenTransfer,
        ),
        ShortcutChipData(
            id = "private",
            label = stringResource(R.string.chip_private),
            icon = Icons.Outlined.Lock,
            onClick = { onChipClick("Private Folder") },
        ),
        ShortcutChipData(
            id = "favourites",
            label = stringResource(R.string.tool_favourites_title),
            icon = Icons.Outlined.FavoriteBorder,
            onClick = { onChipClick("Favourites") },
        ),
        ShortcutChipData(
            id = "history",
            label = stringResource(R.string.tool_history_title),
            icon = Icons.Outlined.History,
            onClick = { onChipClick("History") },
        ),
        ShortcutChipData(
            id = "playlists",
            label = stringResource(R.string.tool_playlists_title),
            icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
            onClick = { onChipClick("Video Playlists") },
        ),
        ShortcutChipData(
            id = "status",
            label = stringResource(R.string.tool_status_title),
            icon = Icons.Outlined.SaveAlt,
            onClick = { onChipClick("Status Saver") },
        ),
        ShortcutChipData(
            id = "network",
            label = stringResource(R.string.chip_network),
            icon = Icons.Outlined.Cloud,
            onClick = { onChipClick("Network Stream") },
        ),
        // Cleaner / Privacy complete the MX shortcut set. Both are honest
        // placeholders (same standing as the Issue 32 tools): tapping one
        // shows the "scheduled for subsequent phases" dialog, never a fake
        // feature. Pills are kept as-is — the paged circular-icon control
        // is screenshot-only (unverified in the decoded layouts), so it is
        // not rebuilt here.
        ShortcutChipData(
            id = "cleaner",
            label = stringResource(R.string.chip_cleaner),
            icon = Icons.Outlined.DeleteSweep,
            onClick = { onChipClick("Cleaner") },
        ),
        ShortcutChipData(
            id = "privacy",
            label = stringResource(R.string.chip_privacy),
            icon = Icons.Outlined.PrivacyTip,
            onClick = { onChipClick("Privacy") },
        ),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val bg = if (chip.isPrimary) MpxTheme.colors.primary.copy(alpha = 0.2f) else MpxTheme.colors.surfaceVariant
            val fg = if (chip.isPrimary) MpxTheme.colors.primary else MpxTheme.colors.textPrimary
            val border = if (chip.isPrimary) MpxTheme.colors.primary.copy(alpha = 0.6f) else MpxTheme.colors.border

            Surface(
                modifier = Modifier
                    .clip(MpxShapes.extraLarge)
                    .clickable { chip.onClick() },
                color = bg,
                tonalElevation = 0.dp,
                shape = MpxShapes.extraLarge,
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = chip.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = fg,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        fontSize = 13.sp,
                    )
                    if (chip.badge != null) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(MpxTheme.colors.primary, MpxShapes.extraSmall)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = chip.badge,
                                color = Color.Black,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
