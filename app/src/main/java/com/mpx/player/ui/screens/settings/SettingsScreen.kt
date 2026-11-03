package com.mpx.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf<SettingCategory?>(null) }

    val categories = listOf(
        SettingCategory(
            id = "list",
            title = "List",
            subtitle = "Folder view, file extensions, hidden files, thumbnail cache",
            icon = Icons.Filled.FormatListBulleted,
        ),
        SettingCategory(
            id = "player",
            title = "Player",
            subtitle = "Screen orientation, gestures, controls, resume playback, background play",
            icon = Icons.Filled.PlayCircleOutline,
        ),
        SettingCategory(
            id = "decoder",
            title = "Decoder",
            subtitle = "Hardware acceleration (HW / HW+), Software (SW), audio passthrough",
            icon = Icons.Filled.Memory,
        ),
        SettingCategory(
            id = "audio",
            title = "Audio",
            subtitle = "Equalizer, audio output, volume boost, system audio balance",
            icon = Icons.Filled.VolumeUp,
        ),
        SettingCategory(
            id = "subtitle",
            title = "Subtitle",
            subtitle = "Character encoding, font size, color, border, shadow, position",
            icon = Icons.Filled.Subtitles,
        ),
        SettingCategory(
            id = "general",
            title = "General",
            subtitle = "App language, clear history, clear cache, reset all preferences",
            icon = Icons.Filled.Tune,
        ),
        SettingCategory(
            id = "about",
            title = "About",
            subtitle = "Version 0.1.0-native, open source licenses, legal disclaimer",
            icon = Icons.Filled.Info,
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MpxTextPrimary,
                        )
                    }
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
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                HonestStatusBanner(
                    text = "MX Player-style settings structure. Individual option toggles persist with Room in Phase 4.",
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MpxSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = MpxPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                            color = MpxTextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTextSecondary,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MpxTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp),
                    color = MpxBorder,
                    thickness = 0.5.dp,
                )
            }
        }
    }

    if (selectedCategory != null) {
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            containerColor = MpxSurface,
            title = {
                Text(
                    text = "${selectedCategory?.title} Settings",
                    color = MpxTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Configurable options: ${selectedCategory?.subtitle}",
                        color = MpxTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Settings persistence will be wired to Room database in Phase 4.",
                        color = MpxPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategory = null }) {
                    Text("Close", color = MpxPrimary)
                }
            },
        )
    }
}
