package com.mpx.player.ui.components.me

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.network.isSupportedStreamUrl
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

/**
 * Real Network Stream player dialog allowing direct stream input.
 */
@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayStream: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    // Issue #297: typed text is validated with the same scheme rule as
    // pasteClipboard() below — Play is only enabled for a supported
    // stream URL, so "hello" can no longer reach the player.
    val isValidUrl = isSupportedStreamUrl(urlText)
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun pasteClipboard() {
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) || text.startsWith("rtsp://", ignoreCase = true)) {
                    urlText = text.trim()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_network_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_network_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/video.mp4", color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(onClick = { pasteClipboard() }) {
                            Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_paste), tint = MpxTheme.colors.primary)
                        }
                    },
                    singleLine = true,
                    // Issue #297: non-blank text without a supported
                    // scheme is an error — the field shows why Play is
                    // disabled instead of failing in the player.
                    isError = urlText.isNotBlank() && !isValidUrl,
                    supportingText = {
                        if (urlText.isNotBlank() && !isValidUrl) {
                            Text(stringResource(R.string.dialog_network_invalid_url))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                // Issue #297: Play only for a supported stream URL —
                // the same scheme rule as pasteClipboard(), so typed
                // "hello" can no longer open the player.
                onClick = { if (isValidUrl) onPlayStream(urlText.trim()) },
                enabled = isValidUrl,
                colors = ButtonDefaults.buttonColors(containerColor = MpxTheme.colors.primary),
            ) {
                Text(stringResource(R.string.dialog_network_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.textSecondary)
            }
        },
    )
}
