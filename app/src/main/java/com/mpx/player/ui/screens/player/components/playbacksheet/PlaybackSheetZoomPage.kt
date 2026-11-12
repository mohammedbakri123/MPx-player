package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.theme.MpxTheme

@Composable
internal fun PlaybackSheetZoomPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        Text(
            text = formatMultiplier(currentSettings.videoZoom),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MpxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Slider(
            value = currentSettings.videoZoom,
            onValueChange = { onSettingsChanged(currentSettings.copy(videoZoom = it)) },
            valueRange = 0.5f..2.0f,
            steps = 29,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0.5×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
            Text("2×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
        }
    }
}
