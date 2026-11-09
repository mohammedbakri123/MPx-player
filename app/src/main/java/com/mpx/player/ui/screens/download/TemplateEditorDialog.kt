package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.mpx.player.data.local.entity.CommandTemplateEntity
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.components.MpxFilterChip
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #410: the template editor — a name field plus the command
 * itself (multi-line, like the custom-command editor). Used for
 * both creating (existing = null) and editing a template; Save is
 * a no-op while either field is blank, so a half-filled template
 * can never be stored. The title says New or Edit.
 *
 * Below the command field, one-tap insert chips for the most-used
 * yt-dlp options (a first cut — Seal's saved option shortcuts,
 * `OptionShortcut`, are user-saved snippets and a follow-up, not
 * silently dropped). Tapping a chip appends the option to the
 * command, with a space when the field does not already end in
 * one. The caller persists the result (see the templates page and
 * the custom-command editor).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorDialog(
    existing: CommandTemplateEntity?,
    onSave: (name: String, template: String) -> Unit,
    onDismiss: () -> Unit,
    // Issue #410: the custom-command editor's "Save as template"
    // pre-fills the command with the text being saved, so the user
    // only types a name. Ignored when editing an existing template.
    prefillTemplate: String = "",
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var template by remember { mutableStateOf(existing?.template ?: prefillTemplate) }

    MpxDialog(
        title = stringResource(
            if (existing == null) R.string.download_template_new_title
            else R.string.download_template_edit_title,
        ),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            if (name.isNotBlank() && template.isNotBlank()) onSave(name, template)
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = stringResource(R.string.download_template_name),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = stringResource(R.string.download_custom_command_title),
                        color = MpxTheme.colors.textSecondary,
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
            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (option in templateInsertOptions) {
                    MpxFilterChip(
                        label = option,
                        selected = false,
                        onClick = {
                            template = if (template.isBlank()) option
                            else if (template.endsWith(" ") || template.endsWith("\n")) template + option
                            else "$template $option"
                        },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** The most-used yt-dlp options, as one-tap inserts for the editor. */
private val templateInsertOptions = listOf(
    "--no-mtime",
    "-S \"ext\"",
    "--no-playlist",
    "--embed-metadata",
    "--add-metadata",
    "--write-subs",
    "--sub-langs en,ar",
    "--merge-output-format mkv",
    "-f bestvideo+bestaudio",
    "--sponsorblock-remove default",
)
