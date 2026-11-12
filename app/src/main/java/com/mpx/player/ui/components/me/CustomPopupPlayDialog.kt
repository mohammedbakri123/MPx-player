package com.mpx.player.ui.components.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxPrimaryButton
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.components.MpxTextButton
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.PipUtils

/**
 * Real Custom Pop-up Play configuration dialog with device PiP detection and testing.
 */
@Composable
fun CustomPopupPlayDialog(
    enabled: Boolean,
    controls: String,
    onDismiss: () -> Unit,
    onUpdateSettings: (Boolean, String) -> Unit,
    onTestPip: () -> Unit,
) {
    val context = LocalContext.current
    val isSupported = remember(context) { PipUtils.isPipSupported(context) }
    var currentEnabled by remember { mutableStateOf(enabled) }
    var currentControls by remember { mutableStateOf(controls) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureInPictureAlt, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_popup_play_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        text = {
            Column {
                // Capability Status Banner
                if (isSupported) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_supported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textPrimary,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Toggle Pop-up Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pip_auto_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MpxTheme.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.pip_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = currentEnabled,
                        onCheckedChange = { currentEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MpxTheme.colors.primary, checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f)),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pip_window_controls_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MpxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "PrevNext" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "PrevNext",
                        onClick = { currentControls = "PrevNext" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_prev_next), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "Seek10" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "Seek10",
                        onClick = { currentControls = "Seek10" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_seek10), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }

                // Issue #231: the third controls mode — Play/Pause only —
                // was a real RemoteAction set in PipUtils but had no radio.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "PlayPauseOnly" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "PlayPauseOnly",
                        onClick = { currentControls = "PlayPauseOnly" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_play_pause_only), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(10.dp))

                MpxSecondaryButton(
                    text = stringResource(R.string.pip_test_button),
                    icon = Icons.Filled.PictureInPictureAlt,
                    onClick = onTestPip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            MpxPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onUpdateSettings(currentEnabled, currentControls) },
            )
        },
        dismissButton = {
            MpxTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
        },
    )
}
