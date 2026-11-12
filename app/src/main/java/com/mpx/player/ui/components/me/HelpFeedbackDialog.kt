package com.mpx.player.ui.components.me

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.BuildConfig
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Real Help & Feedback In-App Info Page with gesture guides and device diagnostics.
 */
@Composable
fun HelpFeedbackDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_help_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.help_sec1_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec2_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec3_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.surfaceElevated)
                            .border(1.dp, MpxTheme.colors.border, MpxShapes.small)
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(stringResource(R.string.help_diag_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(stringResource(R.string.help_diag_os, Build.VERSION.SDK_INT, Build.VERSION.RELEASE), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(stringResource(R.string.help_diag_device, Build.MANUFACTURER, Build.MODEL), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(
                                stringResource(
                                    R.string.help_diag_engine,
                                    "libmpv (mpv-android-lib 0.1.12)",
                                ),
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary,
                            )
                            Text(stringResource(R.string.help_diag_db), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary) }
        },
    )
}
