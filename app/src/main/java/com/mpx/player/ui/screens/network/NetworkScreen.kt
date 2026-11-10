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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager

/**
 * Hosts that serve web pages, not video files. A link to one of these
 * can never play in a direct-stream player — mpv needs the actual
 * media URL, and extracting it takes yt-dlp, which was removed in
 * #434. mpvEx and MX cannot play these either. The tab says so
 * plainly instead of handing the page to mpv and showing its
 * cryptic "could not play this file" error.
 */
private val VIDEO_PAGE_HOSTS = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
    "vimeo.com", "www.vimeo.com",
    "dailymotion.com", "www.dailymotion.com",
    "twitch.tv", "www.twitch.tv",
    "tiktok.com", "www.tiktok.com",
    "facebook.com", "www.facebook.com", "fb.watch",
    "instagram.com", "www.instagram.com",
    "x.com", "twitter.com", "www.twitter.com",
)

private fun isVideoPageUrl(url: String): Boolean {
    val host = url.substringAfter("://", "").substringBefore("/").substringBefore(":")
        .lowercase().trim()
    return host in VIDEO_PAGE_HOSTS
}

/**
 * Network tab (issue #438): streaming only, like mpvEx's "Stream
 * Link" section — a URL field with Paste + Play that hands the URL
 * straight to the player, no validation beyond non-blank. No server
 * browsing: the SMB section (#435) is gone, with the whole SMB
 * stack (data/network/smb, smbj, lastSmbHost, smb_* strings).
 *
 * A link shared to the app arrives pre-filled via [sharedUrl].
 */
@Composable
fun NetworkScreen(
    playbackManager: PlaybackManager? = null,
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }

    var urlText by remember { mutableStateOf(sharedUrl ?: "") }
    // Issue #416: a shared link pre-fills the URL field once, then is
    // consumed so a later share can pre-fill again.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            urlText = sharedUrl
            onSharedUrlConsumed?.invoke()
        }
    }
    val trimmedUrl = urlText.trim()
    val isPageUrl = trimmedUrl.isNotBlank() && isVideoPageUrl(trimmedUrl)
    val canPlay = trimmedUrl.isNotBlank() && !isPageUrl
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun pasteClipboard() {
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    urlText = text.trim()
                }
            }
        }
    }

    fun playUrl(url: String) {
        val streamTitle = url.substringAfterLast("/").substringBefore("?").ifBlank { "Network Stream" }
        realPlaybackManager.playQueue(
            listOf(
                PlayableMedia(
                    id = "stream_${System.currentTimeMillis()}",
                    title = streamTitle,
                    subtitle = url,
                    uri = url,
                    isVideo = true,
                    durationMs = 0L,
                ),
            ),
        )
        onOpenPlayer()
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
            value = urlText,
            onValueChange = { urlText = it },
            placeholder = { Text("https://example.com/video.mp4", fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = { pasteClipboard() }) {
                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_paste), tint = MaterialTheme.colorScheme.primary)
                }
            },
            singleLine = true,
            isError = isPageUrl,
            supportingText = {
                if (isPageUrl) {
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
                onClick = { playUrl(trimmedUrl) },
                enabled = canPlay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.dialog_network_play))
            }
        }
    }
}
