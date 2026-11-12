package com.mpx.player.ui.components.me

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
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
 * Real Legal & Open Source In-App Info Page.
 */
@Composable
fun LegalLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Gavel, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_legal_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.legal_sec1_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec2_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec3_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec3_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary) }
        },
    )
}
