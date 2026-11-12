package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.screens.settings.DiagnosticsMediaCounts
import com.mpx.player.ui.screens.settings.PreferenceRow
import com.mpx.player.ui.screens.settings.components.DevDiagnosticsData
import com.mpx.player.ui.screens.settings.components.DevelopmentSettingsSection
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AdvancedCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings, Boolean) -> Unit,
    onStopBackgroundPlayback: () -> Unit,
    onOpenAdvancedSettings: (() -> Unit)?,
    playbackState: PlaybackState,
    cachedDiagnostics: DevDiagnosticsData?,
    onDiagnosticsLoaded: (DevDiagnosticsData) -> Unit,
    onLoadRecordCounts: suspend () -> List<Int>,
    onLoadMediaCounts: suspend (Boolean, Boolean) -> DiagnosticsMediaCounts,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (onOpenAdvancedSettings != null) {
            PreferenceRow(
                title = stringResource(R.string.adv_open_title),
                subtitle = stringResource(R.string.adv_open_summary),
                icon = Icons.Filled.Tune,
                onClick = onOpenAdvancedSettings,
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedButton(onClick = { showResetDialog = true }) {
            Text(stringResource(R.string.general_reset_button), color = MpxTheme.colors.primary)
        }
        if (showResetDialog) {
            MpxDialog(
                title = stringResource(R.string.general_reset_button),
                onDismiss = { showResetDialog = false },
                confirmText = stringResource(R.string.general_reset_button),
                onConfirm = {
                    val wasBackgroundPlay = settings.backgroundPlay
                    onUpdate(AppSettings(), true)
                    if (wasBackgroundPlay) {
                        onStopBackgroundPlayback()
                    }
                    showResetDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.general_reset_done))
                    }
                },
                dismissText = stringResource(R.string.action_cancel),
                body = {
                    Text(
                        stringResource(R.string.reset_confirm_message),
                        color = MpxTheme.colors.textSecondary,
                    )
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.general_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        DevelopmentSettingsSection(
            playbackState = playbackState,
            settings = settings,
            cachedDiagnostics = cachedDiagnostics,
            onDiagnosticsLoaded = onDiagnosticsLoaded,
            onLoadRecordCounts = onLoadRecordCounts,
            onLoadMediaCounts = onLoadMediaCounts,
        )
    }
}
