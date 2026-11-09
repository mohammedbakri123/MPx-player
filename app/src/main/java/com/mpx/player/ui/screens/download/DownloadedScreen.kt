package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.mpx.player.ui.components.MpxFilterChip
import com.mpx.player.ui.theme.MpxShapes
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
 *
 * Issue #411: search + filters + multi-select — Seal's history page
 * (`VideoListPage.kt`) has a search bar, audio/video + per-extractor
 * filter chips, and a multi-select mode with batch delete. MPx gets
 * the search field (file names + history titles), All/Video/Audio
 * chips, and long-press multi-select with a delete-selected action.
 * Filtering is in memory on the two flows already collected here —
 * no DAO change, no migration. Per-extractor chips are skipped:
 * downloaded files carry no extractor, so they would filter only
 * the history half of the page.
 */
private enum class DownloadFilter { ALL, VIDEO, AUDIO }

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

    // Issue #411: search text + filter survive rotation; the
    // selection itself is plain remember — losing it on rotation is
    // fine, it is a transient mode.
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.ALL) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var selectedHistory by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedFiles.isNotEmpty() || selectedHistory.isNotEmpty()
    val selectedCount = selectedFiles.size + selectedHistory.size

    fun matchesFilter(isAudio: Boolean) = when (filter) {
        DownloadFilter.ALL -> true
        DownloadFilter.VIDEO -> !isAudio
        DownloadFilter.AUDIO -> isAudio
    }
    fun matchesQuery(text: String) = query.isBlank() || text.contains(query, ignoreCase = true)
    val filteredFiles = downloadedFiles.filter { file ->
        matchesFilter(file.extension in AUDIO_EXTENSIONS) &&
            matchesQuery(file.nameWithoutExtension)
    }
    val filteredHistory = downloadHistory.filter { entry ->
        matchesFilter(entry.fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) &&
            matchesQuery(entry.title)
    }
    val hasAnything = downloadedFiles.isNotEmpty() || downloadHistory.isNotEmpty()
    val hasFiltered = filteredFiles.isNotEmpty() || filteredHistory.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.download_selected_count, selectedCount)
                        } else {
                            stringResource(R.string.download_downloaded_title)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectedFiles = emptySet()
                            selectedHistory = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            // Batch delete: each selected file via
                            // deleteFile, each selected history entry
                            // via deleteHistoryEntry, then leave
                            // selection mode.
                            val filesToDelete = downloadedFiles.filter { it.uri in selectedFiles }
                            val historyToDelete = selectedHistory.toList()
                            filesToDelete.forEach { downloader.deleteFile(it) }
                            historyToDelete.forEach { downloader.deleteHistoryEntry(it) }
                            selectedFiles = emptySet()
                            selectedHistory = emptySet()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.download_deleted_selected),
                                )
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                stringResource(R.string.download_delete_selected),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        if (!hasAnything) {
            EmptyStateView(
                title = stringResource(R.string.download_empty_title),
                message = stringResource(R.string.download_downloaded_empty_message),
                icon = Icons.Filled.Download,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.download_search_hint),
                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = MpxTheme.colors.textSecondary,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MpxShapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxTheme.colors.surfaceVariant,
                        unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MpxFilterChip(
                        label = stringResource(R.string.download_filter_all),
                        selected = filter == DownloadFilter.ALL,
                        onClick = { filter = DownloadFilter.ALL },
                    )
                    Spacer(Modifier.width(8.dp))
                    MpxFilterChip(
                        label = stringResource(R.string.download_filter_video),
                        selected = filter == DownloadFilter.VIDEO,
                        onClick = { filter = DownloadFilter.VIDEO },
                    )
                    Spacer(Modifier.width(8.dp))
                    MpxFilterChip(
                        label = stringResource(R.string.download_filter_audio),
                        selected = filter == DownloadFilter.AUDIO,
                        onClick = { filter = DownloadFilter.AUDIO },
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (!hasFiltered) {
                    Text(
                        text = stringResource(R.string.download_no_results),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        items(filteredFiles, key = { it.uri }) { file ->
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
                                selected = file.uri in selectedFiles,
                                selectionMode = selectionMode,
                                onLongClick = { selectedFiles = selectedFiles + file.uri },
                                onToggleSelect = {
                                    selectedFiles = if (file.uri in selectedFiles) {
                                        selectedFiles - file.uri
                                    } else {
                                        selectedFiles + file.uri
                                    }
                                },
                            )
                        }
                        if (filteredHistory.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.download_history),
                                    color = MpxTheme.colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(filteredHistory, key = { it.id }) { entry ->
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
                                    selected = entry.id in selectedHistory,
                                    selectionMode = selectionMode,
                                    onLongClick = { selectedHistory = selectedHistory + entry.id },
                                    onToggleSelect = {
                                        selectedHistory = if (entry.id in selectedHistory) {
                                            selectedHistory - entry.id
                                        } else {
                                            selectedHistory + entry.id
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
