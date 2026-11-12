package com.mpx.player.ui.components.me

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.LocaleHelper

/**
 * Real In-App Language selector with instant switching and RTL preview.
 */
@Composable
fun AppLanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    // Issue #231: one shared language list — the Me dialog offers the same
    // 10 options, in the same order, as the Settings language picker.
    val languages = LocaleHelper.SUPPORTED_LANGUAGES.map { option ->
        Triple(
            option.code,
            stringResource(option.titleRes),
            stringResource(option.descRes),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lang_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        text = {
            // Issue #231: the list grew from 3 to 10 languages, so it
            // scrolls instead of overflowing the dialog on short screens.
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                languages.forEach { (langKey, langTitle, langDesc) ->
                    val isSelected = currentLanguage.equals(langKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable { onSelectLanguage(langKey) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectLanguage(langKey) },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = langTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MpxTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = langDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lang_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
            }
        },
    )
}
