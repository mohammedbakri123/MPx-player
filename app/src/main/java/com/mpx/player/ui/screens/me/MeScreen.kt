package com.mpx.player.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.domain.model.MeTool
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.theme.MpxAccentAmber
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogTool by remember { mutableStateOf<MeTool?>(null) }

    val tools = listOf(
        MeTool("transfer", "File Transfer", "P2P sharing", Icons.Filled.SwapHoriz, badge = null, isAvailable = true),
        MeTool("private", "Private Folder", "PIN protected", Icons.Filled.Lock, badge = "Soon"),
        MeTool("playlists", "Playlists", "Video collections", Icons.Filled.PlaylistPlay, badge = "Soon"),
        MeTool("equalizer", "Equalizer", "Sound tuner", Icons.Filled.Equalizer, badge = "Soon"),
        MeTool("status", "Status Saver", "Saved statuses", Icons.Filled.SaveAlt, badge = "Soon"),
        MeTool("network", "Network Stream", "Play URL", Icons.Filled.Cloud, badge = "Soon"),
        MeTool("manager", "Media Manager", "Storage manager", Icons.Filled.FolderSpecial, badge = "Soon"),
        MeTool("bin", "Recycle Bin", "Deleted files", Icons.Filled.Delete, badge = "Soon"),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Me",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Profile Card Header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MpxSurfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MpxPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "MPx Local User",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MpxTextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Private on-device media player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTextSecondary,
                            )
                        }
                    }
                }
            }

            item {
                HonestStatusBanner(
                    text = "Me tools match MX Player layout. Unwired items show honest status dialogs.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Tools Section Header
            item {
                Text(
                    text = "TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            // Tools 4-column Grid (MX Player signature tools section)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MpxSurfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val row1 = tools.subList(0, 4)
                        val row2 = tools.subList(4, 8)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            row1.forEach { tool ->
                                ToolGridItem(
                                    tool = tool,
                                    onClick = {
                                        if (tool.id == "transfer") {
                                            onOpenTransfer()
                                        } else {
                                            dialogTool = tool
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            row2.forEach { tool ->
                                ToolGridItem(
                                    tool = tool,
                                    onClick = {
                                        dialogTool = tool
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Preferences Section
            item {
                Text(
                    text = "PREFERENCES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MpxSurfaceVariant),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.MusicNote,
                            title = "Music Player",
                            subtitle = "Open local audio library",
                            onClick = onOpenMusic,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MpxBorder,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.ColorLens,
                            title = "App Theme",
                            subtitle = "Midnight OLED (Pure Black)",
                            onClick = {},
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MpxBorder,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Settings,
                            title = "Settings",
                            subtitle = "Player, decoder, list, and subtitle options",
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // About & Legal Section
            item {
                Text(
                    text = "ABOUT & LEGAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MpxSurfaceVariant),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.Security,
                            title = "Privacy & Security",
                            subtitle = "100% on-device. No telemetry, no ads, no trackers.",
                            onClick = {},
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MpxBorder,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.HelpOutline,
                            title = "Help & Feedback",
                            subtitle = "Documentation and support",
                            onClick = {},
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MpxBorder,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Info,
                            title = "MPx Player Native",
                            subtitle = "Version 0.1.0-native • Jetpack Compose",
                            onClick = {},
                        )
                    }
                }
            }
        }
    }

    if (dialogTool != null) {
        AlertDialog(
            onDismissRequest = { dialogTool = null },
            containerColor = MpxSurface,
            title = { Text(dialogTool?.title ?: "", color = MpxTextPrimary) },
            text = {
                Text(
                    "${dialogTool?.title} (${dialogTool?.description}) is scheduled for subsequent phases. MPx is keeping feature development honest.",
                    color = MpxTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { dialogTool = null }) {
                    Text("OK", color = MpxPrimary)
                }
            },
        )
    }
}

@Composable
private fun ToolGridItem(
    tool: MeTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MpxSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = MpxPrimary,
                modifier = Modifier.size(24.dp),
            )
            if (tool.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MpxAccentAmber, RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = tool.badge,
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall,
            color = MpxTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreferenceRow(
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MpxSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MpxPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MpxTextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTextSecondary,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MpxTextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
