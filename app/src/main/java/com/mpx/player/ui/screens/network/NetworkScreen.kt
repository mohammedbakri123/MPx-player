package com.mpx.player.ui.screens.network

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.network.smb.SmbConnection
import com.mpx.player.data.network.smb.SmbEntry
import com.mpx.player.data.network.smb.SmbPlaybackSession
import com.mpx.player.data.network.smb.SmbStreamProxy
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.network.isSupportedStreamUrl
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts", "m2ts", "flv", "wmv", "mpg", "mpeg")
private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")

private fun String.extension(): String = substringAfterLast(".", "").lowercase()

/**
 * Network tab (issue #435): play a video straight from a pasted URL,
 * and browse a local network server (SMB) — the SMB browser promoted
 * from Me's dialog (issue #78) into this tab, same state machine,
 * same proxy/session playback path.
 *
 * The URL section reuses Me's Network Stream dialog behaviour
 * (issue #297): Play is only enabled for a supported stream URL
 * (http/https/rtsp), with a paste button for the clipboard.
 * A link shared to the app arrives pre-filled via [sharedUrl].
 */
@Composable
fun NetworkScreen(
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    var appSettings by remember { mutableStateOf(realSettingsRepo.load()) }

    var urlText by remember { mutableStateOf(sharedUrl ?: "") }
    // Issue #416: a shared link pre-fills the URL field once, then is
    // consumed so a later share can pre-fill again.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            urlText = sharedUrl
            onSharedUrlConsumed?.invoke()
        }
    }
    val isValidUrl = isSupportedStreamUrl(urlText)
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun pasteClipboard() {
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (isSupportedStreamUrl(text)) {
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

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                isError = urlText.isNotBlank() && !isValidUrl,
                supportingText = {
                    if (urlText.isNotBlank() && !isValidUrl) {
                        Text(stringResource(R.string.dialog_network_invalid_url))
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
                    onClick = { if (isValidUrl) playUrl(urlText.trim()) },
                    enabled = isValidUrl,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.dialog_network_play))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        SmbBrowserContent(
            initialHost = appSettings.lastSmbHost,
            onHostRemembered = { host ->
                val updated = appSettings.copy(lastSmbHost = host)
                appSettings = updated
                realSettingsRepo.save(updated)
            },
            onPlayMedia = { title, proxyUrl, isVideo, smbId ->
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = smbId,
                            title = title,
                            subtitle = proxyUrl,
                            uri = proxyUrl,
                            isVideo = isVideo,
                            durationMs = 0L,
                        ),
                    ),
                    startIndex = 0,
                    autoPlay = true,
                )
                onOpenPlayer()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Real SMB browsing for the Local Network section (issue #78), written
 * against smbj (pure-Java SMB2/3) — no MX code, no fake shares.
 *
 * Flow: server form (host, optional share, optional credentials) ->
 * share list or share root -> folder navigation -> tap a video/audio
 * file to play it through the existing PlaybackManager path via a
 * localhost proxy URL (see [SmbStreamProxy]; credentials never enter
 * the playback URI). Passwords are session-only and never persisted;
 * only the host is remembered, via [onHostRemembered]. Non-media
 * files are listed but not playable. Network discovery (broadcast
 * scanning) is not implemented: the server address must be typed in.
 *
 * Promoted from Me's SmbBrowserDialog in #435: same state machine,
 * as a screen section instead of a dialog. Leaving the tab closes
 * the connection (unless a play handed it to [SmbPlaybackSession]),
 * exactly as dismissing the dialog used to.
 */
@Composable
private fun SmbBrowserContent(
    initialHost: String,
    onHostRemembered: (String) -> Unit,
    onPlayMedia: (title: String, proxyUrl: String, isVideo: Boolean, smbId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // The proxy must outlive this screen, because playback continues (and
    // seeks re-request ranges) after leaving the tab — so on play it is
    // handed to SmbPlaybackSession (issue #229), which owns it until
    // SMB playback ends. It binds loopback only and serves only
    // registered tokens.
    val proxy = remember { SmbStreamProxy() }

    var host by remember { mutableStateOf(initialHost) }
    var shareName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    var connection by remember { mutableStateOf<SmbConnection?>(null) }
    // In-flight connect (issue #302): the Connect button is disabled while
    // loading, but a second tap can still land before recomposition, and
    // the tab can be left while a connect is in flight — both need the
    // job handle so the stale attempt can be cancelled.
    var connectJob by remember { mutableStateOf<Job?>(null) }
    // Set when the screen leaves composition: the connect success path
    // must not adopt a connection after the tab went away (nobody owns
    // the fresh one).
    var disposed by remember { mutableStateOf(false) }
    var connectedHost by remember { mutableStateOf("") }
    var currentShare by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf("") } // "" = share root; segments joined by "/"
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var browsing by remember { mutableStateOf(false) }
    // Set when the user taps play: the connection must stay open for
    // playback and seeks, and SmbPlaybackSession owns it from here.
    var keepAlive by remember { mutableStateOf(false) }

    // Leaving the tab is the screen's dismiss: cancel an in-flight
    // connect and close the connection, unless a play handed it to
    // the session (keepAlive). The proxy only ever binds on play
    // (register), which sets keepAlive, so stop() here is a no-op.
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            connectJob?.cancel()
            connectJob = null
            if (!keepAlive) {
                connection?.close()
                connection = null
                proxy.stop()
            }
        }
    }

    val unknownError = stringResource(R.string.smb_error_unknown)
    val openFailed = stringResource(R.string.smb_open_failed)

    fun showError(message: String) {
        error = message
        loading = false
    }

    fun loadDirectory(share: String, path: String) {
        val conn = connection ?: return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { conn.listDirectory(share, path) }
            }
            result.onSuccess { list ->
                entries = list.sortedWith(compareByDescending<SmbEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                currentShare = share
                currentPath = path
                loading = false
            }.onFailure { e ->
                showError(e.message ?: unknownError)
            }
        }
    }

    fun connect() {
        val h = host.trim()
        if (h.isEmpty()) return
        disposed = false
        loading = true
        error = null
        // A previous connect can still be in flight if a second tap
        // landed before recomposition disabled the button; cancel it so
        // only the newest attempt can adopt its connection.
        connectJob?.cancel()
        connectJob = scope.launch {
            val old = connection
            val fresh = SmbConnection(h, username.trim(), password, domain.trim())
            val result = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        try {
                            fresh.connect()
                        } catch (e: Exception) {
                            // Half-open (socket connected, auth failed):
                            // close it so it does not leak.
                            fresh.close()
                            throw e
                        }
                        // Only close the old connection once the new one
                        // is live, so a failed reconnect leaves the
                        // previous (still working) connection assigned
                        // instead of a closed object (issue #302).
                        old?.close()
                        fresh
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled (left the tab, or superseded) while the blocking
                // connect was in flight: the success path below never
                // runs, so close the fresh connection here — nobody owns
                // it — and rethrow so cancellation still propagates.
                // SmbConnection.close() is idempotent, so this is safe
                // even if the inner catch above already closed it.
                withContext(NonCancellable + Dispatchers.IO) { fresh.close() }
                throw e
            }
            result.onSuccess { conn ->
                if (disposed) {
                    // Left the tab after the blocking connect returned but
                    // before the scope was cancelled at disposal: nobody
                    // owns this connection, so close it instead of
                    // assigning it (issue #302).
                    conn.close()
                    loading = false
                    return@onSuccess
                }
                connection = conn
                connectedHost = h
                onHostRemembered(h)
                val wantedShare = shareName.trim()
                if (wantedShare.isEmpty()) {
                    val shares = withContext(Dispatchers.IO) { runCatching { conn.listShares() } }
                    shares.onSuccess { names ->
                        entries = names.map { SmbEntry(it, isDirectory = true, size = 0L) }
                        currentShare = null
                        currentPath = ""
                        browsing = true
                        loading = false
                    }.onFailure { e ->
                        showError(e.message ?: unknownError)
                    }
                } else {
                    browsing = true
                    loading = false
                    loadDirectory(wantedShare, "")
                }
            }.onFailure { e ->
                // The old connection was never closed (it is closed only
                // after a new one connects), so `connection` still points
                // at a live object — or null on a first connect — and the
                // next browse attempt starts from a clean state.
                showError(e.message ?: unknownError)
            }
        }
    }

    fun goBack() {
        if (currentShare == null) {
            browsing = false
            entries = emptyList()
            return
        }
        val share = currentShare ?: return
        if (currentPath.isEmpty()) {
            // Back to the share list.
            loading = true
            scope.launch {
                val shares = withContext(Dispatchers.IO) { runCatching { connection?.listShares() ?: emptyList() } }
                shares.onSuccess { names ->
                    entries = names.map { SmbEntry(it, isDirectory = true, size = 0L) }
                    currentShare = null
                    currentPath = ""
                    loading = false
                }.onFailure { e -> showError(e.message ?: unknownError) }
            }
        } else {
            val parent = currentPath.substringBeforeLast("/", "")
            loadDirectory(share, parent)
        }
    }

    fun playEntry(entry: SmbEntry) {
        val share = currentShare ?: return
        val fullPath = if (currentPath.isEmpty()) entry.name else "$currentPath/${entry.name}"
        val ext = entry.name.extension()
        val isVideo = ext in VIDEO_EXTS
        val isAudio = ext in AUDIO_EXTS
        if (!isVideo && !isAudio) return
        val conn = connection ?: return
        loading = true
        scope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching {
                    proxy.register { offset ->
                        conn.openStream(share, fullPath, offset)
                    }
                }.getOrNull()
            }
            loading = false
            if (url != null) {
                keepAlive = true
                // Issue #229: hand the proxy + connection to the
                // application-scoped session — playback goes on after
                // leaving this tab, and nothing else would ever close
                // them. Starting a new SMB play closes the previous
                // session (its item has left the queue by then).
                SmbPlaybackSession.start(proxy, conn)
                onPlayMedia(entry.name, url, isVideo, "smb:$connectedHost/$share/$fullPath")
            } else {
                showError(openFailed)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (browsing) {
                IconButton(onClick = { goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = when {
                    !browsing -> stringResource(R.string.tool_local_network_title)
                    currentShare == null -> stringResource(R.string.smb_shares_title, connectedHost)
                    currentPath.isEmpty() -> currentShare ?: ""
                    else -> currentPath.substringAfterLast("/")
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (!browsing) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.smb_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.smb_host_label), fontSize = 13.sp) },
                    placeholder = { Text("192.168.1.10", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = shareName,
                    onValueChange = { shareName = it },
                    label = { Text(stringResource(R.string.smb_share_label), fontSize = 13.sp) },
                    placeholder = { Text(stringResource(R.string.smb_share_hint), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.smb_username_label), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.smb_password_label), fontSize = 13.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.smb_domain_label), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.smb_credentials_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { connect() },
                        enabled = host.isNotBlank() && !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(if (loading) stringResource(R.string.smb_connecting) else stringResource(R.string.smb_connect))
                    }
                }
            }
        } else {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.smb_empty_folder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries) { entry ->
                        val ext = entry.name.extension()
                        val playable = entry.isDirectory || ext in VIDEO_EXTS || ext in AUDIO_EXTS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = playable) {
                                    if (entry.isDirectory) {
                                        if (currentShare == null) {
                                            loadDirectory(entry.name, "")
                                        } else {
                                            val share = currentShare ?: return@clickable
                                            val next = if (currentPath.isEmpty()) entry.name else "$currentPath/${entry.name}"
                                            loadDirectory(share, next)
                                        }
                                    } else {
                                        playEntry(entry)
                                    }
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    entry.isDirectory -> Icons.Filled.Folder
                                    ext in VIDEO_EXTS -> Icons.Filled.Movie
                                    ext in AUDIO_EXTS -> Icons.Filled.AudioFile
                                    else -> Icons.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = if (playable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                if (!entry.isDirectory) {
                                    Text(
                                        formatSize(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}
