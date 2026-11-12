package com.mpx.player.ui.components.me

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

/**
 * About MPx Player In-App Dialog.
 */
@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit,
    onOpenAboutBeta: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pref_about_mpx), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_version_name),
                    fontWeight = FontWeight.Bold,
                    color = MpxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Row {
                if (onOpenAboutBeta != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenAboutBeta()
                    }) {
                        Text(stringResource(R.string.about_beta_open_page_button), color = MpxTheme.colors.primary)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary) }
            }
        },
    )
}
