package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.download.MpxDownloader
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.theme.MpxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Issue #379: the Downloaded screen — Seal's task-list page model. The
 * Download page used to list finished files and history inline under the
 * active downloads; they now live here, opened from the page's Downloaded
 * button. The rows are the same [DownloadedFileRow] / [DownloadHistoryRow]
 * as before, only their home changed.
 *
 * A folder change in Downloader settings narrows this list to the new
 * folder's files (the MediaStore query follows the setting); history
 * entries survive — they are the permanent record and still play when
 * the file is there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedScreen(
    onNavigateBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    playbackManager: PlaybackManager? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val downloader = remember(context) { MpxDownloader.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val downloadedFiles by downloader.downloadedFiles.collectAsState()
    // Issue #346: the download history, newest first — the permanent
    // record (it survives the file being deleted, and carries the
    // title/uploader the file scan cannot know).
    val downloadHistory by downloader.downloadHistory.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.download_downloaded_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        if (downloadedFiles.isEmpty() && downloadHistory.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.download_empty_title),
                message = stringResource(R.string.download_downloaded_empty_message),
                icon = Icons.Filled.Download,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(downloadedFiles, key = { it.uri }) { file ->
                    DownloadedFileRow(
                        file = file,
                        dateText = dateFormat.format(Date(file.lastModified)),
                        onPlay = {
                            val isAudio = file.extension in AUDIO_EXTENSIONS
                            realPlaybackManager.load(
                                PlayableMedia(
                                    id = file.uri,
                                    title = file.nameWithoutExtension,
                                    uri = file.uri,
                                    isVideo = !isAudio,
                                    // Issue #341/#344: sidecar subtitles
                                    // ride along as external subtitles —
                                    // the engine sub-adds and pins them
                                    // via the #332 flow.
                                    externalSubtitles = downloader.findSiblingSubtitles(file),
                                ),
                            )
                            onOpenPlayer()
                        },
                        onDelete = {
                            downloader.deleteFile(file)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.download_deleted),
                                )
                            }
                        },
                    )
                }
                if (downloadHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.download_history),
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(downloadHistory, key = { it.id }) { entry ->
                        DownloadHistoryRow(
                            entry = entry,
                            dateText = dateFormat.format(Date(entry.completedAt)),
                            onPlay = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        downloader.historyFileIfExists(entry)
                                    }
                                    if (file != null) {
                                        val isAudio = file.extension in AUDIO_EXTENSIONS
                                        realPlaybackManager.load(
                                            PlayableMedia(
                                                id = file.uri,
                                                title = file.nameWithoutExtension,
                                                uri = file.uri,
                                                isVideo = !isAudio,
                                                externalSubtitles = downloader.findSiblingSubtitles(file),
                                            ),
                                        )
                                        onOpenPlayer()
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.download_history_file_missing),
                                        )
                                    }
                                }
                            },
                            onDelete = { downloader.deleteHistoryEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}
