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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
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
    settingsRepository: AppSettingsRepository,
    modifier: Modifier = Modifier,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val categories = listOf(
        SettingCategory("list", "List", "Folder view, hidden files, thumbnails", Icons.Filled.FormatListBulleted),
        SettingCategory("player", "Player", "Resume playback, background play, gestures", Icons.Filled.PlayCircleOutline),
        SettingCategory("decoder", "Decoder", "Hardware acceleration (HW / HW+), Software (SW)", Icons.Filled.Memory),
        SettingCategory("audio", "Audio", "Volume boost", Icons.Filled.VolumeUp),
        SettingCategory("subtitle", "Subtitle", "Subtitle size", Icons.Filled.Subtitles),
        SettingCategory("general", "General", "Reset all preferences to defaults", Icons.Filled.Tune),
        SettingCategory("about", "About", "Version 0.1.0-native", Icons.Filled.Info),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MpxTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = MpxTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxSurface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedId = if (expandedId == category.id) null else category.id }
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
                            Icon(category.icon, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = MpxTextPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = MpxTextMuted, modifier = Modifier.size(20.dp))
                    }

                    if (expandedId == category.id) {
                        Column(modifier = Modifier.padding(start = 74.dp, end = 16.dp, bottom = 12.dp)) {
                            when (category.id) {
                                "list" -> {
                                    ToggleRow("Show hidden files", settings.showHiddenFiles) { update(settings.copy(showHiddenFiles = it)) }
                                    ToggleRow("Show thumbnails", settings.showThumbnails) { update(settings.copy(showThumbnails = it)) }
                                }
                                "player" -> {
                                    ToggleRow("Resume playback", settings.resumePlayback) { update(settings.copy(resumePlayback = it)) }
                                    ToggleRow("Background play", settings.backgroundPlay) { update(settings.copy(backgroundPlay = it)) }
                                    ToggleRow("Gestures (double-tap, drag seek)", settings.gesturesEnabled) { update(settings.copy(gesturesEnabled = it)) }
                                }
                                "decoder" -> {
                                    listOf("HW", "HW+", "SW").forEach { mode ->
                                        ChoiceRow(mode, settings.decoderMode == mode) { update(settings.copy(decoderMode = mode)) }
                                    }
                                    Text(
                                        "Note: this selects the decoder preference. Real decoder wiring arrives with the playback worktree.",
                                        style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary,
                                    )
                                }
                                "audio" -> {
                                    ToggleRow("Volume boost", settings.audioBoost) { update(settings.copy(audioBoost = it)) }
                                }
                                "subtitle" -> {
                                    listOf("Small", "Normal", "Large").forEach { size ->
                                        ChoiceRow(size, settings.subtitleSize == size) { update(settings.copy(subtitleSize = size)) }
                                    }
                                }
                                "general" -> {
                                    OutlinedButton(onClick = { update(AppSettings()) }) {
                                        Text("Reset all to defaults", color = MpxPrimary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "App language follows the system language for now.",
                                        style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary,
                                    )
                                }
                                "about" -> {
                                    Text(
                                        "MPx Player 0.1.0-native. Native Kotlin rewrite, MX-inspired layout built in MPx's own code.",
                                        style = MaterialTheme.typography.bodyMedium, color = MpxTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 74.dp), color = MpxBorder, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MpxTextPrimary, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MpxPrimary, checkedTrackColor = MpxPrimary.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = MpxTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
