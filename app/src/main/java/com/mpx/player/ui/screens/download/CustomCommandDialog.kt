package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #384: custom command dialog — one line of yt-dlp options
 * (Seal's CommandTemplate, one template instead of Seal's named
 * list). One multi-line text field; the switch in the settings list
 * decides whether it runs, so the text can be edited while off.
 * Pre-filled with Seal's sample `--no-mtime -S "ext"`. The hint
 * says what the template replaces, because when it is on, the
 * format sorter/-f, subtitles, SponsorBlock, metadata/chapters,
 * advanced flags, clips and split-by-chapter are all skipped —
 * the template IS the command.
 */
@Composable
fun CustomCommandDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var text by remember { mutableStateOf(settings.customCommand) }

    MpxDialog(
        title = stringResource(R.string.download_custom_command_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            settingsRepository.save(settings.copy(customCommand = text))
            onDismiss()
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "--no-mtime -S \"ext\"",
                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                shape = MpxShapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                    focusedBorderColor = MpxTheme.colors.primary,
                    unfocusedBorderColor = MpxTheme.colors.border,
                    focusedTextColor = MpxTheme.colors.textPrimary,
                    unfocusedTextColor = MpxTheme.colors.textPrimary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_custom_command_hint),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
