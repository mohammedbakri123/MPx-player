package com.mpx.player.ui.screens.local

import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.FastScrollbar
import com.mpx.player.ui.components.GridFastScrollbar
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.ShortcutChipRow
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.util.MediaPermissions
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    permissionResultHandler: ActivityResultHandler<Boolean>,
    onOpenPlayer: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaStoreRepository(context) }
    val realSettingsRepo = remember(settingsRepository, context) {
        settingsRepository ?: SharedPrefsAppSettingsRepository(context)
    }
    var appSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    var hasPermission by remember {
        mutableStateOf(MediaPermissions.hasVideoPermission(context))
    }
    var folders by remember { mutableStateOf<List<MediaFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<VideoFile>>(emptyList()) }

    // Root folder list view mode: loaded from persisted localViewMode
    var viewMode by remember {
        mutableStateOf(
            try {
                ViewMode.valueOf(appSettings.localViewMode)
            } catch (_: Exception) {
                ViewMode.LIST
            }
        )
    }
    var sortField by remember {
        mutableStateOf(
            try {
                SortField.valueOf(appSettings.primarySortField)
            } catch (_: Exception) {
                SortField.DATE
            }
        )
    }
    var secondarySortField by remember {
        mutableStateOf(
            try {
                SortField.valueOf(appSettings.secondarySortField)
            } catch (_: Exception) {
                SortField.NAME
            }
        )
    }

    fun updateViewMode(newMode: ViewMode) {
        viewMode = newMode
        val updated = if (appSettings.separateViewLayout) {
            appSettings.copy(localViewMode = newMode.name)
        } else {
            appSettings.copy(localViewMode = newMode.name, musicViewMode = newMode.name)
        }
        realSettingsRepo.save(updated)
        appSettings = updated
    }
    var showSortDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var featureDialogMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Folder view mode state (drilled-down folder contents view)
    var currentFolder by remember { mutableStateOf<MediaFolder?>(null) }
    var folderVideos by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var isFolderLoading by remember { mutableStateOf(false) }
    var folderViewMode by remember { mutableStateOf(ViewMode.LIST) }
    var folderSortField by remember {
        mutableStateOf(
            try {
                SortField.valueOf(appSettings.folderPrimarySortField)
            } catch (_: Exception) {
                SortField.DATE
            }
        )
    }
    var folderSecondarySortField by remember {
        mutableStateOf(
            try {
                SortField.valueOf(appSettings.folderSecondarySortField)
            } catch (_: Exception) {
                SortField.NAME
            }
        )
    }
    var folderSearchQuery by remember { mutableStateOf("") }
    var isFolderSearching by remember { mutableStateOf(false) }
    var showFolderSortDialog by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var folderForMenu by remember { mutableStateOf<MediaFolder?>(null) }
    var videoForMenu by remember { mutableStateOf<VideoFile?>(null) }
    var videoForPlaylistAdd by remember { mutableStateOf<VideoFile?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    // Intercept back button when viewing a folder to navigate back to folder list
    BackHandler(enabled = currentFolder != null) {
        currentFolder = null
        isFolderSearching = false
        folderSearchQuery = ""
    }

    fun openFolder(folder: MediaFolder) {
        currentFolder = folder
        isFolderSearching = false
        folderSearchQuery = ""
        // Global-with-override: retrieve per-folder override or fall back to global folderViewMode
        val modeName = realSettingsRepo.getFolderViewMode(folder.id)
        folderViewMode = try {
            ViewMode.valueOf(modeName)
        } catch (_: Exception) {
            ViewMode.LIST
        }
        scope.launch(Dispatchers.IO) {
            isFolderLoading = true
            val vids = repository.getVideosInFolder(folder.id)
            withContext(Dispatchers.Main) {
                folderVideos = vids
                isFolderLoading = false
            }
        }
    }

    fun refreshFolderVideos(folderId: String) {
        scope.launch(Dispatchers.IO) {
            isFolderLoading = true
            val vids = repository.getVideosInFolder(folderId)
            withContext(Dispatchers.Main) {
                folderVideos = vids
                isFolderLoading = false
            }
        }
    }

    fun playFolder(folder: MediaFolder) {
        scope.launch(Dispatchers.IO) {
            val videos = repository.getVideosInFolder(folder.id)
            withContext(Dispatchers.Main) {
                if (videos.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_no_playable_videos, folder.name)
                        )
                    }
                } else {
                    playbackManager?.playVideoFiles(videos)
                    onOpenPlayer()
                }
            }
        }
    }

    fun playFolderById(bucketId: String, folderName: String) {
        scope.launch(Dispatchers.IO) {
            val vids = repository.getVideosInFolder(bucketId)
            withContext(Dispatchers.Main) {
                if (vids.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_no_playable_videos, folderName)
                        )
                    }
                } else {
                    playbackManager?.playVideoFiles(vids)
                    onOpenPlayer()
                }
            }
        }
    }

    fun playVideoWithFolder(video: VideoFile) {
        scope.launch(Dispatchers.IO) {
            val vids = if (video.bucketId.isNotBlank()) {
                val found = repository.getVideosInFolder(video.bucketId)
                if (found.isNotEmpty()) found else listOf(video)
            } else {
                listOf(video)
            }
            val startIndex = vids.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            withContext(Dispatchers.Main) {
                playbackManager?.playVideoFiles(vids, startIndex)
                onOpenPlayer()
            }
        }
    }

    val persistenceRepository = remember(context) {
        playbackManager?.persistenceRepository
            ?: RoomMediaPersistenceRepository(MpxDatabase.getInstance(context))
    }
    val favourites by persistenceRepository.observeFavourites().collectAsState(initial = emptyList())
    val history by persistenceRepository.observeHistory().collectAsState(initial = emptyList())
    val resumePositions by persistenceRepository.observeResumePositions().collectAsState(initial = emptyList())
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())

    val resumePositionsMap = remember(resumePositions) {
        resumePositions.associateBy { it.mediaId }
    }

    val continueWatchingItems = remember(history, resumePositionsMap, allVideos) {
        history
            .filter { it.isVideo }
            .mapNotNull { item ->
                val resumeEntry = resumePositionsMap[item.mediaId] ?: resumePositionsMap[item.mediaUri]
                val savedPosition = when {
                    resumeEntry != null && resumeEntry.positionMs > 0L -> resumeEntry.positionMs
                    item.playbackPositionMs > 0L -> item.playbackPositionMs
                    else -> 0L
                }
                val rawDuration = when {
                    item.durationMs > 0L -> item.durationMs
                    resumeEntry != null && resumeEntry.durationMs > 0L -> resumeEntry.durationMs
                    else -> allVideos.find { it.path == item.mediaUri || it.id == item.mediaId }?.durationMs ?: 0L
                }
                // Only include if there is a real saved position and it has not been watched to completion
                if (savedPosition > 0L && (rawDuration <= 0L || savedPosition < rawDuration - 2000L)) {
                    val resolvedTitle = item.title.ifBlank {
                        allVideos.find { it.path == item.mediaUri || it.id == item.mediaId }?.title
                            ?: item.mediaUri.substringAfterLast('/').substringBeforeLast('.')
                    }
                    ContinueWatchingItem(
                        mediaUri = item.mediaUri,
                        mediaId = item.mediaId,
                        title = resolvedTitle,
                        subtitle = item.subtitle,
                        positionMs = savedPosition,
                        durationMs = rawDuration,
                        lastPlayedAt = item.lastPlayedAt,
                    )
                } else {
                    null
                }
            }
            .take(10)
    }

    fun resumeVideo(item: ContinueWatchingItem) {
        val matchingVideo = allVideos.find { it.path == item.mediaUri || it.id == item.mediaId }
        if (matchingVideo != null && matchingVideo.bucketId.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                val vids = repository.getVideosInFolder(matchingVideo.bucketId)
                val playlist = if (vids.isNotEmpty()) vids else listOf(matchingVideo)
                val startIndex = playlist.indexOfFirst { it.id == matchingVideo.id }.coerceAtLeast(0)
                playbackManager?.resumeManager?.saveResumePosition(
                    id = matchingVideo.id,
                    positionMs = item.positionMs,
                    mediaUri = matchingVideo.path,
                    durationMs = item.durationMs,
                )
                withContext(Dispatchers.Main) {
                    playbackManager?.playVideoFiles(playlist, startIndex)
                    onOpenPlayer()
                }
            }
        } else {
            val media = PlayableMedia(
                id = item.mediaId,
                title = item.title,
                subtitle = item.subtitle,
                uri = item.mediaUri,
                isVideo = true,
                durationMs = item.durationMs,
            )
            playbackManager?.load(media, resumeMs = item.positionMs)
            onOpenPlayer()
        }
    }

    fun dismissContinueWatching(item: ContinueWatchingItem) {
        scope.launch(Dispatchers.IO) {
            persistenceRepository.deleteHistoryItem(item.mediaUri)
            persistenceRepository.clearResumePosition(item.mediaId)
            if (item.mediaUri.isNotBlank()) {
                persistenceRepository.clearResumePosition(item.mediaUri)
            }
            playbackManager?.resumeManager?.clearResumePosition(item.mediaId)
            if (item.mediaUri.isNotBlank()) {
                playbackManager?.resumeManager?.clearResumePosition(item.mediaUri)
            }
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.continue_watching_dismissed, item.title)
                )
            }
        }
    }

    var showFavouritesDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    fun refreshFolders(showAll: Boolean = appSettings.showAllFolders) {
        if (isLoading) return
        if (MediaPermissions.hasVideoPermission(context)) {
            hasPermission = true
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val scanned = repository.getMediaFolders(showAll)
                val scannedVideos = repository.getAllVideos(showAll)
                withContext(Dispatchers.Main) {
                    folders = scanned
                    allVideos = scannedVideos
                    isLoading = false
                }
            }
        } else {
            hasPermission = false
            folders = emptyList()
        }
    }

    DisposableEffect(permissionResultHandler) {
        onDispose { permissionResultHandler.callback = null }
    }
    SideEffect {
        permissionResultHandler.callback = { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val scanned = repository.getMediaFolders(appSettings.showAllFolders)
                val scannedVideos = repository.getAllVideos(appSettings.showAllFolders)
                withContext(Dispatchers.Main) {
                    folders = scanned
                    allVideos = scannedVideos
                    isLoading = false
                }
            }
        } else {
            folders = emptyList()
        }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPerm = MediaPermissions.hasVideoPermission(context)
                hasPermission = currentPerm
                val loadedSettings = realSettingsRepo.load()
                appSettings = loadedSettings
                viewMode = if (loadedSettings.localViewMode == "GRID") ViewMode.GRID else ViewMode.LIST
                if (currentPerm) {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        val scanned = repository.getMediaFolders(loadedSettings.showAllFolders)
                        val scannedVideos = repository.getAllVideos(loadedSettings.showAllFolders)
                        withContext(Dispatchers.Main) {
                            folders = scanned
                            allVideos = scannedVideos
                            isLoading = false
                        }
                    }
                } else {
                    folders = emptyList()
                }
                currentFolder?.let { cf ->
                    refreshFolderVideos(cf.id)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val sortedFolders = remember(folders, sortField, secondarySortField, appSettings) {
        folders.sortedWith(
            MediaSortComparator.folderComparator(
                primary = sortField,
                secondary = secondarySortField,
                reverse = appSettings.sortReverse,
                secondaryReverse = appSettings.sortSecondaryReverse,
                numeric = appSettings.sortNumeric,
                caseSensitive = appSettings.sortCaseSensitive,
            )
        )
    }

    val filteredFolders = remember(sortedFolders, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) sortedFolders
        else sortedFolders.filter { it.name.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
    }

    // File view: all videos ungrouped, sorted and filtered
    val sortedVideos = remember(allVideos, sortField, secondarySortField, appSettings) {
        allVideos.sortedWith(
            MediaSortComparator.videoComparator(
                primary = sortField,
                secondary = secondarySortField,
                reverse = appSettings.sortReverse,
                secondaryReverse = appSettings.sortSecondaryReverse,
                numeric = appSettings.sortNumeric,
                caseSensitive = appSettings.sortCaseSensitive,
                ignoreExtension = appSettings.sortIgnoreExtension,
            )
        )
    }

    val filteredVideos = remember(sortedVideos, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) sortedVideos
        else sortedVideos.filter { it.title.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
    }

    val rootEmpty = if (appSettings.fileView) filteredVideos.isEmpty() else filteredFolders.isEmpty()

    // Folder view sorted and filtered videos
    val sortedFolderVideos = remember(folderVideos, folderSortField, folderSecondarySortField, appSettings) {
        folderVideos.sortedWith(
            MediaSortComparator.videoComparator(
                primary = folderSortField,
                secondary = folderSecondarySortField,
                reverse = appSettings.sortReverse,
                secondaryReverse = appSettings.sortSecondaryReverse,
                numeric = appSettings.sortNumeric,
                caseSensitive = appSettings.sortCaseSensitive,
                ignoreExtension = appSettings.sortIgnoreExtension,
            )
        )
    }

    val filteredFolderVideos = remember(sortedFolderVideos, folderSearchQuery) {
        val q = folderSearchQuery.trim()
        if (q.isBlank()) sortedFolderVideos
        else sortedFolderVideos.filter { it.title.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
    }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(pullRefreshState.isRefreshing) {
            val folder = currentFolder
            if (folder != null) refreshFolderVideos(folder.id) else refreshFolders()
        }
    }
    LaunchedEffect(isLoading, isFolderLoading) {
        if (!isLoading && !isFolderLoading) pullRefreshState.endRefresh()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentFolder != null) {
                // Folder View Top Bar
                val folder = currentFolder!!
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            currentFolder = null
                            isFolderSearching = false
                            folderSearchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back_to_folders),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    title = {
                        if (isFolderSearching) {
                            OutlinedTextField(
                                value = folderSearchQuery,
                                onValueChange = { folderSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.folder_view_search_placeholder),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        } else {
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val count = filteredFolderVideos.size
                                val sizeStr = folder.totalSizeFormatted
                                val subText = if (count == 1) {
                                    stringResource(R.string.folder_view_subtitle_single, sizeStr)
                                } else {
                                    stringResource(R.string.folder_view_subtitle, count, sizeStr)
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    actions = {
                        if (isFolderSearching) {
                            IconButton(onClick = { isFolderSearching = false; folderSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_close_search),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Toggle folder view mode (LIST <-> GRID)
                        IconButton(onClick = {
                            val nextMode = if (folderViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            folderViewMode = nextMode
                            realSettingsRepo.setFolderViewMode(folder.id, nextMode.name)
                            scope.launch {
                                val msg = if (nextMode == ViewMode.GRID) {
                                    context.getString(R.string.view_mode_switched_grid)
                                } else {
                                    context.getString(R.string.view_mode_switched_list)
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }) {
                            Icon(
                                imageVector = if (folderViewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(R.string.cd_toggle_view),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        IconButton(onClick = { isFolderSearching = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Box {
                            IconButton(onClick = { folderMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(
                                expanded = folderMenuExpanded,
                                onDismissRequest = { folderMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.folder_view_dialog_title)) },
                                    onClick = {
                                        folderMenuExpanded = false
                                        showFolderSortDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.folder_menu_play_all)) },
                                    onClick = {
                                        folderMenuExpanded = false
                                        if (filteredFolderVideos.isNotEmpty()) {
                                            playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = 0)
                                            onOpenPlayer()
                                        }
                                    },
                                )
                                if (realSettingsRepo.hasFolderViewModeOverride(folder.id)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_view_reset_default)) },
                                        onClick = {
                                            folderMenuExpanded = false
                                            realSettingsRepo.clearFolderViewModeOverride(folder.id)
                                            val globalMode = try {
                                                ViewMode.valueOf(realSettingsRepo.load().folderViewMode)
                                            } catch (_: Exception) {
                                                ViewMode.LIST
                                            }
                                            folderViewMode = globalMode
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        R.string.folder_view_reset_default_snackbar,
                                                        context.getString(globalMode.labelRes)
                                                    )
                                                )
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.local_menu_refresh)) },
                                    onClick = {
                                        folderMenuExpanded = false
                                        refreshFolderVideos(folder.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.snackbar_library_refreshed)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            } else {
                // Root Folders List Top Bar
                TopAppBar(
                    title = {
                        if (isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.local_search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.local_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    actions = {
                        if (isSearching) {
                            IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_close_search),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(onClick = {
                            updateViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(R.string.cd_toggle_view),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.local_menu_view_sort)) },
                                    onClick = {
                                        menuExpanded = false
                                        showSortDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (appSettings.fileView) stringResource(R.string.local_menu_folder_view)
                                            else stringResource(R.string.local_menu_file_view)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val updated = appSettings.copy(fileView = !appSettings.fileView)
                                        realSettingsRepo.save(updated)
                                        appSettings = updated
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (appSettings.showAllFolders) stringResource(R.string.local_menu_filtered_view)
                                            else stringResource(R.string.list_show_all_folders)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val updated = appSettings.copy(showAllFolders = !appSettings.showAllFolders)
                                        realSettingsRepo.save(updated)
                                        appSettings = updated
                                        refreshFolders(updated.showAllFolders)
                                        scope.launch {
                                            val msg = if (updated.showAllFolders) {
                                                context.getString(R.string.local_snackbar_show_all)
                                            } else {
                                                context.getString(R.string.local_snackbar_filtered)
                                            }
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.local_menu_refresh)) },
                                    onClick = {
                                        menuExpanded = false
                                        refreshFolders()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.snackbar_library_refreshed)
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (currentFolder != null) {
                        if (filteredFolderVideos.isNotEmpty()) {
                            playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = 0)
                            onOpenPlayer()
                        }
                    } else if (appSettings.fileView) {
                        val first = filteredVideos.firstOrNull()
                        if (first != null) playVideoWithFolder(first)
                    } else {
                        val first = filteredFolders.firstOrNull()
                        if (first != null) playFolder(first)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_quick_play),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        bottomBar = {
            if (currentFolder == null) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.local_status_strip,
                                filteredFolders.size,
                                filteredFolders.sumOf { it.videoCount },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
            if (currentFolder != null) {
                // ==================== FOLDER VIEW (Drilled-Down Contents) ====================
                if (isFolderLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredFolderVideos.isEmpty() && folderSearchQuery.isNotBlank()) {
                    EmptyStateView(
                        title = stringResource(R.string.folder_view_search_empty_title),
                        message = stringResource(R.string.folder_view_search_empty_desc, folderSearchQuery),
                        icon = Icons.Filled.PlayArrow,
                        actionLabel = stringResource(R.string.empty_search_action),
                        onActionClick = { folderSearchQuery = "" },
                    )
                } else if (filteredFolderVideos.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.folder_view_empty_title),
                        message = stringResource(R.string.folder_view_empty_desc),
                        icon = Icons.Filled.PlayArrow,
                        actionLabel = stringResource(R.string.action_back_to_folders),
                        onActionClick = { currentFolder = null },
                    )
                } else {
                    // Folder contents section header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.mini_player_video).uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${filteredFolderVideos.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (realSettingsRepo.hasFolderViewModeOverride(currentFolder!!.id)) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = stringResource(folderViewMode.labelRes),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showFolderSortDialog = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val folderSortLabelText = if (folderSecondarySortField != SortField.NONE && folderSecondarySortField != folderSortField) {
                                "${stringResource(folderSortField.labelRes)} / ${stringResource(folderSecondarySortField.labelRes)}"
                            } else {
                                stringResource(folderSortField.labelRes)
                            }
                            Text(
                                text = stringResource(R.string.local_sort_label, folderSortLabelText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    // Video list or grid based on folderViewMode
                    if (folderViewMode == ViewMode.LIST) {
                        val videoListState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = videoListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                itemsIndexed(
                                    items = filteredFolderVideos,
                                    key = { _, video -> video.id.ifBlank { video.path } },
                                    contentType = { _, _ -> "video_item_row" },
                                ) { index, video ->
                                    VideoItemRow(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        onClick = {
                                            playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = index)
                                            onOpenPlayer()
                                        },
                                        onMenuClick = {
                                            videoForMenu = video
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 130.dp),
                                        color = MaterialTheme.colorScheme.outline,
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                            FastScrollbar(
                                listState = videoListState,
                                itemCount = filteredFolderVideos.size,
                                getSectionText = { index ->
                                    filteredFolderVideos.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    } else {
                        val videoGridState = rememberLazyGridState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = videoGridState,
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(
                                    items = filteredFolderVideos,
                                    key = { _, video -> video.id.ifBlank { video.path } },
                                    contentType = { _, _ -> "video_item_grid" },
                                ) { index, video ->
                                    VideoItemGridCard(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        onClick = {
                                            playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = index)
                                            onOpenPlayer()
                                        },
                                        onMenuClick = {
                                            videoForMenu = video
                                        },
                                    )
                                }
                            }
                            GridFastScrollbar(
                                gridState = videoGridState,
                                itemCount = filteredFolderVideos.size,
                                getSectionText = { index ->
                                    filteredFolderVideos.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            } else {
                // ==================== ROOT FOLDERS VIEW ====================
                // Shortcut Chips Row (Music, Transfer, Private, Playlists, etc.)
                ShortcutChipRow(
                    onOpenMusic = onOpenMusic,
                    onOpenTransfer = onOpenTransfer,
                    onChipClick = { featureName ->
                        when (featureName) {
                            "Favourites" -> showFavouritesDialog = true
                            "History" -> showHistoryDialog = true
                            "Video Playlists" -> showPlaylistsDialog = true
                            "Private Folder" -> featureDialogMessage = context.getString(
                                R.string.feature_placeholder_msg,
                                context.getString(R.string.tool_private_title)
                            )
                            "Status Saver" -> featureDialogMessage = context.getString(
                                R.string.feature_placeholder_msg,
                                context.getString(R.string.tool_status_title)
                            )
                            "Network Stream" -> featureDialogMessage = context.getString(
                                R.string.feature_placeholder_msg,
                                context.getString(R.string.tool_network_title)
                            )
                            else -> featureDialogMessage = context.getString(
                                R.string.feature_placeholder_msg,
                                featureName
                            )
                        }
                    },
                )

                // Honest milestone notice
                HonestStatusBanner(
                    text = stringResource(R.string.local_status_banner),
                )

                // Continue Watching Row (Issue #55)
                if (continueWatchingItems.isNotEmpty() && searchQuery.isBlank() && hasPermission && !isLoading) {
                    ContinueWatchingRow(
                        items = continueWatchingItems,
                        showThumbnails = appSettings.showThumbnails,
                        onItemClick = { item -> resumeVideo(item) },
                        onDismissItem = { item -> dismissContinueWatching(item) },
                    )
                }

                if (!hasPermission) {
                    EmptyStateView(
                        title = stringResource(R.string.empty_permission_title),
                        message = stringResource(R.string.empty_permission_desc),
                        icon = Icons.Filled.Folder,
                        actionLabel = stringResource(R.string.empty_permission_action),
                        onActionClick = {
                            permissionLauncher.launch(MediaPermissions.videoPermission())
                        },
                    )
                } else if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (rootEmpty && searchQuery.isNotBlank()) {
                    EmptyStateView(
                        title = stringResource(R.string.empty_search_title),
                        message = stringResource(R.string.empty_search_desc, searchQuery),
                        icon = Icons.Filled.Folder,
                        actionLabel = stringResource(R.string.empty_search_action),
                        onActionClick = { searchQuery = "" },
                    )
                } else if (rootEmpty) {
                    if (!appSettings.showAllFolders) {
                        EmptyStateView(
                            title = stringResource(R.string.empty_videos_title),
                            message = stringResource(R.string.empty_videos_desc),
                            icon = Icons.Filled.Folder,
                            actionLabel = stringResource(R.string.list_show_all_folders),
                            onActionClick = {
                                val updated = appSettings.copy(showAllFolders = true)
                                realSettingsRepo.save(updated)
                                appSettings = updated
                                refreshFolders(showAll = true)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.local_snackbar_show_all)
                                    )
                                }
                            },
                        )
                    } else {
                        EmptyStateView(
                            title = stringResource(R.string.empty_folders_title),
                            message = stringResource(R.string.empty_folders_desc),
                            icon = Icons.Filled.Folder,
                            actionLabel = stringResource(R.string.empty_videos_action),
                            onActionClick = {
                                refreshFolders(showAll = true)
                            },
                        )
                    }
                } else if (appSettings.fileView) {
                    // Section Header: FILES + Sort Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.section_files_header),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${filteredVideos.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showSortDialog = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val sortLabelText = if (secondarySortField != SortField.NONE && secondarySortField != sortField) {
                                "${stringResource(sortField.labelRes)} / ${stringResource(secondarySortField.labelRes)}"
                            } else {
                                stringResource(sortField.labelRes)
                            }
                            Text(
                                text = stringResource(R.string.local_sort_label, sortLabelText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    if (viewMode == ViewMode.LIST) {
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                items(
                                    items = filteredVideos,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "video_item_row" },
                                ) { video ->
                                    val isFav = favourites.any { it.mediaUri == video.path }
                                    VideoItemRow(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        isFavourite = isFav,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        onClick = { playVideoWithFolder(video) },
                                        onPlayFolder = { playFolderById(video.bucketId, video.folderName) },
                                        onAddToPlaylist = {
                                            videoForPlaylistAdd = video
                                            showAddToPlaylistDialog = true
                                        },
                                        onToggleFavourite = {
                                            scope.launch {
                                                if (isFav) {
                                                    persistenceRepository.removeFavourite(video.path)
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.snackbar_removed_favourites)
                                                    )
                                                } else {
                                                    persistenceRepository.addFavourite(
                                                        FavouriteItem(
                                                            mediaId = video.id,
                                                            title = video.title,
                                                            subtitle = video.folderName,
                                                            mediaUri = video.path,
                                                            isVideo = true,
                                                            durationMs = video.durationMs,
                                                        )
                                                    )
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.snackbar_added_to_favourites)
                                                    )
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 128.dp),
                                        color = MaterialTheme.colorScheme.outline,
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                            FastScrollbar(
                                listState = listState,
                                itemCount = filteredVideos.size,
                                getSectionText = { index ->
                                    filteredVideos.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = filteredVideos,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "video_item_grid" },
                                ) { video ->
                                    val isFav = favourites.any { it.mediaUri == video.path }
                                    VideoItemGridCard(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        isFavourite = isFav,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        onClick = { playVideoWithFolder(video) },
                                        onPlayFolder = { playFolderById(video.bucketId, video.folderName) },
                                        onAddToPlaylist = {
                                            videoForPlaylistAdd = video
                                            showAddToPlaylistDialog = true
                                        },
                                        onToggleFavourite = {
                                            scope.launch {
                                                if (isFav) {
                                                    persistenceRepository.removeFavourite(video.path)
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.snackbar_removed_favourites)
                                                    )
                                                } else {
                                                    persistenceRepository.addFavourite(
                                                        FavouriteItem(
                                                            mediaId = video.id,
                                                            title = video.title,
                                                            subtitle = video.folderName,
                                                            mediaUri = video.path,
                                                            isVideo = true,
                                                            durationMs = video.durationMs,
                                                        )
                                                    )
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.snackbar_added_to_favourites)
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            GridFastScrollbar(
                                gridState = gridState,
                                itemCount = filteredVideos.size,
                                getSectionText = { index ->
                                    filteredVideos.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                } else {
                    // Section Header: FOLDERS + Sort Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (appSettings.showAllFolders) stringResource(R.string.local_section_all_folders)
                                else stringResource(R.string.local_section_folders),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${filteredFolders.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (appSettings.showAllFolders) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.local_badge_show_all),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showSortDialog = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val sortLabelText = if (secondarySortField != SortField.NONE && secondarySortField != sortField) {
                                "${stringResource(sortField.labelRes)} / ${stringResource(secondarySortField.labelRes)}"
                            } else {
                                stringResource(sortField.labelRes)
                            }
                            Text(
                                text = stringResource(R.string.local_sort_label, sortLabelText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    // Folder list / grid with smooth LazyColumn/LazyGrid, stable keys and fast scrollbar
                    if (viewMode == ViewMode.LIST) {
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                items(
                                    items = filteredFolders,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "folder_item_row" },
                                ) { folder ->
                                    FolderItemRow(
                                        folder = folder,
                                        showThumbnails = appSettings.showThumbnails,
                                        showCount = appSettings.showFieldCount,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        onClick = { openFolder(folder) },
                                        onMenuClick = {
                                            folderForMenu = folder
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 76.dp),
                                        color = MaterialTheme.colorScheme.outline,
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                            FastScrollbar(
                                listState = listState,
                                itemCount = filteredFolders.size,
                                getSectionText = { index ->
                                    filteredFolders.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = filteredFolders,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "folder_item_grid" },
                                ) { folder ->
                                    FolderItemGridCard(
                                        folder = folder,
                                        showThumbnails = appSettings.showThumbnails,
                                        showCount = appSettings.showFieldCount,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        onClick = { openFolder(folder) },
                                    )
                                }
                            }
                            GridFastScrollbar(
                                gridState = gridState,
                                itemCount = filteredFolders.size,
                                getSectionText = { index ->
                                    filteredFolders.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            }
            }
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    // SortViewDialog for Root Folders List
    if (showSortDialog && currentFolder == null) {
        SortViewDialog(
            currentSort = sortField,
            currentSecondarySort = secondarySortField,
            currentView = viewMode,
            showAllFolders = appSettings.showAllFolders,
            separateViewLayout = appSettings.separateViewLayout,
            isFileView = appSettings.fileView,
            onFileViewChange = { isChecked ->
                val updated = appSettings.copy(fileView = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            onSortChange = { newSort ->
                sortField = newSort
                val updated = appSettings.copy(primarySortField = newSort.name)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            onSecondarySortChange = { newSecSort ->
                secondarySortField = newSecSort
                val updated = appSettings.copy(secondarySortField = newSecSort.name)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            onViewChange = {
                updateViewMode(it)
            },
            onSeparateViewLayoutChange = { isChecked ->
                val updated = appSettings.copy(separateViewLayout = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
                scope.launch {
                    val msg = if (isChecked) {
                        context.getString(R.string.snackbar_separate_view_enabled)
                    } else {
                        context.getString(R.string.snackbar_separate_view_disabled)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            },
            onShowAllFoldersChange = { isChecked ->
                val updated = appSettings.copy(showAllFolders = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
                refreshFolders(isChecked)
                scope.launch {
                    val msg = if (isChecked) {
                        context.getString(R.string.local_snackbar_show_all)
                    } else {
                        context.getString(R.string.local_snackbar_filtered)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                val updated = appSettings.copy(showFieldSize = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                val updated = appSettings.copy(showFieldDate = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                val updated = appSettings.copy(showFieldDuration = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                val updated = appSettings.copy(showFieldLocation = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldCount = appSettings.showFieldCount,
            onShowFieldCountChange = { isChecked ->
                val updated = appSettings.copy(showFieldCount = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                val updated = appSettings.copy(sortReverse = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                val updated = appSettings.copy(sortSecondaryReverse = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                val updated = appSettings.copy(sortNumeric = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                val updated = appSettings.copy(sortCaseSensitive = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                val updated = appSettings.copy(sortIgnoreExtension = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            onDismiss = { showSortDialog = false },
        )
    }

    // SortViewDialog for Folder Contents View
    if (showFolderSortDialog && currentFolder != null) {
        val folder = currentFolder!!
        var isSetAsDefault by remember { mutableStateOf(false) }
        SortViewDialog(
            currentSort = folderSortField,
            currentView = folderViewMode,
            isFolderScope = true,
            folderName = folder.name,
            hasFolderOverride = realSettingsRepo.hasFolderViewModeOverride(folder.id),
            isSetAsDefault = isSetAsDefault,
            onSetAsDefaultChange = { isSetAsDefault = it },
            onViewChange = { newMode ->
                folderViewMode = newMode
                if (isSetAsDefault) {
                    val updated = appSettings.copy(folderViewMode = newMode.name)
                    realSettingsRepo.save(updated)
                    appSettings = updated
                    realSettingsRepo.setFolderViewMode(folder.id, newMode.name)
                } else {
                    realSettingsRepo.setFolderViewMode(folder.id, newMode.name)
                }
            },
            onSortChange = { newSort ->
                folderSortField = newSort
                val updated = appSettings.copy(folderPrimarySortField = newSort.name)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            currentSecondarySort = folderSecondarySortField,
            onSecondarySortChange = { newSecSort ->
                folderSecondarySortField = newSecSort
                val updated = appSettings.copy(folderSecondarySortField = newSecSort.name)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                val updated = appSettings.copy(showFieldSize = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                val updated = appSettings.copy(showFieldDate = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                val updated = appSettings.copy(showFieldDuration = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                val updated = appSettings.copy(showFieldLocation = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            showFieldCount = appSettings.showFieldCount,
            onShowFieldCountChange = { isChecked ->
                val updated = appSettings.copy(showFieldCount = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                val updated = appSettings.copy(sortReverse = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                val updated = appSettings.copy(sortSecondaryReverse = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                val updated = appSettings.copy(sortNumeric = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                val updated = appSettings.copy(sortCaseSensitive = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                val updated = appSettings.copy(sortIgnoreExtension = isChecked)
                realSettingsRepo.save(updated)
                appSettings = updated
            },
            onResetToDefault = {
                realSettingsRepo.clearFolderViewModeOverride(folder.id)
                val globalMode = try {
                    ViewMode.valueOf(realSettingsRepo.load().folderViewMode)
                } catch (_: Exception) {
                    ViewMode.LIST
                }
                folderViewMode = globalMode
                showFolderSortDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.folder_view_reset_default_snackbar,
                            context.getString(globalMode.labelRes)
                        )
                    )
                }
            },
            onDismiss = {
                if (isSetAsDefault) {
                    val updated = appSettings.copy(folderViewMode = folderViewMode.name)
                    realSettingsRepo.save(updated)
                    appSettings = updated
                }
                showFolderSortDialog = false
            },
        )
    }

    // Context menu for a folder in the folder list
    if (folderForMenu != null) {
        val f = folderForMenu!!
        AlertDialog(
            onDismissRequest = { folderForMenu = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(text = f.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.folder_item_videos_and_size, f.videoCount, f.totalSizeFormatted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                folderForMenu = null
                                openFolder(f)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.folder_menu_open), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                folderForMenu = null
                                playFolder(f)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.folder_menu_play_all), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderForMenu = null }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Context menu for a video in the folder view
    if (videoForMenu != null) {
        val vid = videoForMenu!!
        val isFav = favourites.any { it.mediaUri == vid.path || it.mediaId == vid.id }
        AlertDialog(
            onDismissRequest = { videoForMenu = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = vid.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                videoForMenu = null
                                playbackManager?.playVideoFiles(listOf(vid))
                                onOpenPlayer()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.video_item_play), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                videoForPlaylistAdd = vid
                                videoForMenu = null
                                showAddToPlaylistDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.video_item_add_to_playlist), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                videoForMenu = null
                                scope.launch {
                                    val favItem = FavouriteItem(
                                        mediaUri = vid.path,
                                        mediaId = vid.id,
                                        title = vid.title,
                                        subtitle = currentFolder?.name ?: "",
                                        isVideo = true,
                                        durationMs = vid.durationMs,
                                    )
                                    val favResult = persistenceRepository.toggleFavourite(favItem)
                                    val msg = if (favResult) {
                                        context.getString(R.string.music_added_to_favourites)
                                    } else {
                                        context.getString(R.string.snackbar_removed_favourites)
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isFav) stringResource(R.string.video_item_unfavourite) else stringResource(R.string.video_item_favourite),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { videoForMenu = null }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // Add Video to Playlist Dialog
    if (showAddToPlaylistDialog && videoForPlaylistAdd != null) {
        val vid = videoForPlaylistAdd!!
        val videoPlaylists = customPlaylists.filter { it.isVideoPlaylist }
        AddToPlaylistDialog(
            playlists = videoPlaylists,
            mediaTitle = vid.title,
            onDismiss = {
                showAddToPlaylistDialog = false
                videoForPlaylistAdd = null
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    persistenceRepository.addItemToPlaylist(
                        PlaylistItem(
                            playlistId = pl.id,
                            mediaUri = vid.path,
                            mediaId = vid.id,
                            title = vid.title,
                            subtitle = currentFolder?.name ?: "",
                            isVideo = true,
                            durationMs = vid.durationMs,
                        )
                    )
                    showAddToPlaylistDialog = false
                    videoForPlaylistAdd = null
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.music_added_to_playlist_msg, pl.name)
                    )
                }
            },
            onCreateNewPlaylist = {
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            },
        )
    }

    if (featureDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { featureDialogMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.dialog_feature_placeholder_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(featureDialogMessage ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { featureDialogMessage = null }) {
                    Text(stringResource(R.string.action_ok), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    if (showFavouritesDialog) {
        FavouritesDialog(
            favourites = favourites,
            onDismiss = { showFavouritesDialog = false },
            onItemClick = { item ->
                showFavouritesDialog = false
                playbackManager?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        )
                    )
                )
                onOpenPlayer()
            },
            onRemoveFavourite = { uri ->
                scope.launch {
                    persistenceRepository.removeFavourite(uri)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_removed_favourites)
                    )
                }
            },
            onClearAll = {
                scope.launch {
                    persistenceRepository.clearFavourites()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_cleared_favourites)
                    )
                }
            },
        )
    }

    if (showHistoryDialog) {
        PlaybackHistoryDialog(
            history = history,
            onDismiss = { showHistoryDialog = false },
            onItemClick = { item ->
                showHistoryDialog = false
                playbackManager?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        )
                    )
                )
                onOpenPlayer()
            },
            onClearHistory = {
                scope.launch {
                    persistenceRepository.clearHistory()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_cleared_history)
                    )
                }
            },
        )
    }

    if (showPlaylistsDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.dialog_video_playlists_title, customPlaylists.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = {
                        showPlaylistsDialog = false
                        showCreatePlaylistDialog = true
                    }) {
                        Text(stringResource(R.string.dialog_new_playlist), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (customPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_playlists_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    ) {
                        items(customPlaylists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPlaylistsDialog = false
                                        selectedPlaylist = pl
                                        scope.launch {
                                            playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(text = pl.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        text = stringResource(R.string.playlist_items_count, pl.itemCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistsDialog = false }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    persistenceRepository.createPlaylist(name, isVideoPlaylist = true)
                    showCreatePlaylistDialog = false
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_playlist_created, name)
                    )
                }
            },
        )
    }

    if (selectedPlaylist != null) {
        val pl = selectedPlaylist!!
        PlaylistDetailsDialog(
            playlist = pl,
            items = playlistItemsForDialog,
            onDismiss = { selectedPlaylist = null },
            onPlayAll = {
                val playable = playlistItemsForDialog.map { item ->
                    PlayableMedia(
                        id = item.mediaId,
                        title = item.title,
                        subtitle = item.subtitle,
                        uri = item.mediaUri,
                        isVideo = item.isVideo,
                    )
                }
                if (playable.isNotEmpty()) {
                    playbackManager?.playQueue(playable, startIndex = 0)
                    selectedPlaylist = null
                    onOpenPlayer()
                }
            },
            onPlayItem = { item ->
                playbackManager?.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                        )
                    )
                )
                selectedPlaylist = null
                onOpenPlayer()
            },
            onRemoveItem = { itemId ->
                scope.launch {
                    persistenceRepository.removeItemFromPlaylist(itemId)
                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                }
            },
            onDeletePlaylist = {
                scope.launch {
                    persistenceRepository.deletePlaylist(pl.id)
                    selectedPlaylist = null
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_playlist_deleted, pl.name)
                    )
                }
            },
        )
    }
}
