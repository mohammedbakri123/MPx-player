package com.mpx.player.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlaylistPlay
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.theme.MpxAccentAmber
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

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
            label = "Music",
            icon = Icons.Outlined.MusicNote,
            isPrimary = true,
            onClick = onOpenMusic,
        ),
        ShortcutChipData(
            id = "transfer",
            label = "Transfer",
            icon = Icons.Outlined.SwapHoriz,
            onClick = onOpenTransfer,
        ),
        ShortcutChipData(
            id = "private",
            label = "Private",
            icon = Icons.Outlined.Lock,
            onClick = { onChipClick("Private Folder") },
        ),
        ShortcutChipData(
            id = "favourites",
            label = "Favourites",
            icon = Icons.Outlined.FavoriteBorder,
            onClick = { onChipClick("Favourites") },
        ),
        ShortcutChipData(
            id = "history",
            label = "History",
            icon = Icons.Outlined.History,
            onClick = { onChipClick("History") },
        ),
        ShortcutChipData(
            id = "playlists",
            label = "Playlists",
            icon = Icons.Outlined.PlaylistPlay,
            onClick = { onChipClick("Video Playlists") },
        ),
        ShortcutChipData(
            id = "status",
            label = "Status Saver",
            icon = Icons.Outlined.SaveAlt,
            onClick = { onChipClick("Status Saver") },
        ),
        ShortcutChipData(
            id = "network",
            label = "Network",
            icon = Icons.Outlined.Cloud,
            onClick = { onChipClick("Network Stream") },
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
            val bg = if (chip.isPrimary) MpxPrimary.copy(alpha = 0.2f) else MpxSurfaceVariant
            val fg = if (chip.isPrimary) MpxPrimary else MpxTextPrimary
            val border = if (chip.isPrimary) MpxPrimary.copy(alpha = 0.6f) else Color.Transparent

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { chip.onClick() },
                color = bg,
                shape = RoundedCornerShape(20.dp),
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
                                .background(MpxAccentAmber, RoundedCornerShape(4.dp))
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
