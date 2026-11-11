package com.mpx.player.ui.screens.network

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import org.koin.compose.viewmodel.koinViewModel

/**
 * Network tab (issue #438): streaming only, like mpvEx's "Stream
 * Link" section — a URL field with Paste + Play that hands the URL
 * straight to the player, no validation beyond non-blank. No server
 * browsing: the SMB section (#435) is gone, with the whole SMB
 * stack (data/network/smb, smbj, lastSmbHost, smb_* strings).
 *
 * A link shared to the app arrives pre-filled via [sharedUrl].
 *
 * Migration Phase 2d: state lives in [NetworkViewModel] — the
 * screen renders [NetworkUiState] and forwards events. It holds no
 * playback reference any more.
 */
@Composable
fun NetworkScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Issue #416: a shared link pre-fills the URL field once, then is
    // consumed so a later share can pre-fill again.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.onSharedUrl(sharedUrl)
            onSharedUrlConsumed?.invoke()
        }
    }
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun pasteClipboard() {
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    viewModel.onUrlChange(text.trim())
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.dialog_network_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dialog_network_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.urlText,
            onValueChange = viewModel::onUrlChange,
            placeholder = { Text("https://example.com/video.mp4", fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = { pasteClipboard() }) {
                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_paste), tint = MaterialTheme.colorScheme.primary)
                }
            },
            singleLine = true,
            isError = uiState.isPageUrl,
            supportingText = {
                if (uiState.isPageUrl) {
                    Text(stringResource(R.string.network_stream_page_link))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    viewModel.playUrl()
                    onOpenPlayer()
                },
                enabled = uiState.canPlay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.dialog_network_play))
            }
        }
    }
}
