package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

@Composable
internal fun PlaybackSheetDecoderPage(
    state: PlaybackState,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    playbackController: PlaybackController,
    onDismiss: () -> Unit,
) {
    Column {
        DecoderMode.values().forEach { mode ->
            PlaybackSheetRadioRow(
                title = stringResource(mode.titleRes),
                subtitle = stringResource(mode.descriptionRes),
                selected = state.decoderMode == mode,
                onClick = {
                    playbackController.setDecoderMode(mode)
                    onSettingsChanged(currentSettings.copy(decoderMode = mode.label))
                    onDismiss()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.player_active_codec,
                state.activeDecoderName ?: stringResource(R.string.player_codec_initializing),
            ),
            color = MpxTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        state.decoderFallbackNote?.let { note ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0x33FFB74D),
                shape = MpxShapes.small,
                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = note,
                        color = Color(0xFFFFE082),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
