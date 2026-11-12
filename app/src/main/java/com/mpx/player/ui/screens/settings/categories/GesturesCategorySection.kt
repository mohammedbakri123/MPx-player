package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun GesturesCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ToggleRow(stringResource(R.string.player_gestures), settings.gesturesEnabled) {
            onUpdate(settings.copy(gesturesEnabled = it))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pref_gesture_seek_title),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        listOf(5, 10, 15, 30).forEach { seconds ->
            ChoiceRow(
                stringResource(R.string.pref_gesture_seek_seconds, seconds),
                settings.doubleTapSeekSeconds == seconds,
            ) {
                onUpdate(settings.copy(doubleTapSeekSeconds = seconds))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pref_gesture_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
