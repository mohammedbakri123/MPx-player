package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.download.MpxDownloader
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val downloader = remember(context) { MpxDownloader.getInstance(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var text by remember { mutableStateOf(settings.customCommand) }
    // Issue #410: saved templates — the picker fills this field
    // from a chosen template, and "Save as template" stores the
    // current text as a new template (same editor dialog as the
    // templates page).
    val templates by downloader.getCommandTemplates().collectAsState(initial = emptyList())
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showSaveAsTemplate by remember { mutableStateOf(false) }

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
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showTemplatePicker = true }) {
                    Text(
                        text = stringResource(R.string.download_command_templates_title),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MpxTheme.colors.primary,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { showSaveAsTemplate = true },
                    enabled = text.isNotBlank(),
                ) {
                    Text(
                        text = stringResource(R.string.download_save_as_template),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MpxTheme.colors.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = templates,
            onPick = { template ->
                showTemplatePicker = false
                text = template.template
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
    if (showSaveAsTemplate) {
        TemplateEditorDialog(
            existing = null,
            onSave = { name, template ->
                showSaveAsTemplate = false
                scope.launch { downloader.saveCommandTemplate(null, name, template) }
            },
            onDismiss = { showSaveAsTemplate = false },
            prefillTemplate = text,
        )
    }
}
