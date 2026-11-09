package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.CommandTemplateEntity
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #410: pick a saved command template to fill the
 * custom-command editor — Seal's per-download template picker
 * (`TemplatePickerDialog`, `DownloadDialogV2.kt:577+`). Tap a
 * template to use its command; the picker only opens when
 * templates exist, so an empty list shows the empty text instead
 * of a blank dialog.
 */
@Composable
fun TemplatePickerDialog(
    templates: List<CommandTemplateEntity>,
    onPick: (CommandTemplateEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    MpxDialog(
        title = stringResource(R.string.download_command_templates_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
    ) {
        if (templates.isEmpty()) {
            Text(
                text = stringResource(R.string.download_command_templates_none),
                color = MpxTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
        } else {
            LazyColumn {
                items(templates, key = { it.id }) { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(template) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.name,
                                color = MpxTheme.colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = template.template,
                                color = MpxTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
