package com.mpx.player.ui.screens.local

import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.screens.local.components.LocalDialogs
import com.mpx.player.ui.screens.local.components.LocalFolderContent
import com.mpx.player.ui.screens.local.components.LocalFolderTopBar
import com.mpx.player.ui.screens.local.components.LocalRootContent
import com.mpx.player.ui.screens.local.components.LocalRootTopBar
import com.mpx.player.ui.screens.local.components.LocalSelectionBar
import com.mpx.player.ui.screens.local.components.LocalSelectionTopBar
import com.mpx.player.ui.screens.local.components.LocalStatusStrip
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.MediaPermissions
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Issue #175: restores the drilled-down [MediaFolder] across navigation
 * and configuration changes without losing the back-stack state.
 */
val MediaFolderSaver: Saver<MediaFolder?, List<Any>> = Saver(
    save = { folder ->
        folder?.let {
            listOf(
                it.id,
                it.name,
                it.path,
                it.videoCount,
                it.totalSizeBytes,
                it.isNew,
                it.lastModified,
                it.isStorageRemovable,
                it.totalDurationMs,
            )
        }
    },
    restore = { saved ->
        MediaFolder(
            id = saved[0] as String,
            name = saved[1] as String,
            path = saved[2] as String,
            videoCount = saved[3] as Int,
            totalSizeBytes = (saved[4] as? Long) ?: (saved.getOrNull(9) as? Long) ?: 0L,
            isNew = saved[5] as Boolean,
            lastModified = saved[6] as Long,
            isStorageRemovable = saved[7] as Boolean,
            totalDurationMs = saved[8] as Long,
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    permissionResultHandler: ActivityResultHandler<Boolean>,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    playbackCoordinator: PlaybackCoordinator? = null,
    // Issue #245: reports whether the whole-page search is open so
    // MainActivity can turn the tab-swipe detector off while it is —
    // a swipe mid-search used to switch tabs and destroy the typed
    // query. Defaults to a no-op for other callers.
    onSearchPageChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Phase 2b: the screen's state lives in LocalViewModel, obtained
    // from Koin. The video-permission flag arrives as a parameter —
    // the ViewModel cannot check it without a Context — and the
    // screen keeps it in sync via setHasPermission (permission
    // callback, ON_RESUME).
    val viewModel: LocalViewModel = koinViewModel(
        parameters = { parametersOf(MediaPermissions.hasVideoPermission(context)) },
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State owned by the ViewModel, collected here under the same
    // names the screen used before, so the rendering below reads
    // exactly what it read before.
    val appSettings by viewModel.settings.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val folderVideos by viewModel.folderVideos.collectAsState()
    val isFolderLoading by viewModel.isFolderLoading.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val secondarySortField by viewModel.secondarySortField.collectAsState()
    val folderSortField by viewModel.folderSortField.collectAsState()
    val folderSecondarySortField by viewModel.folderSecondarySortField.collectAsState()
    val isTreeMode by viewModel.isTreeMode.collectAsState()
    val expandedTreeFolders by viewModel.expandedTreeFolders.collectAsState()
    val folderSearchQuery by viewModel.folderSearchQuery.collectAsState()
    val isFolderSearching by viewModel.isFolderSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val selectedVideoIds by viewModel.selectedVideoIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectionCount by viewModel.selectionCount.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val favouriteUris by viewModel.favouriteUris.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val history by viewModel.history.collectAsState()
    val sortedFolders by viewModel.sortedFolders.collectAsState()
    val sortedVideos by viewModel.sortedVideos.collectAsState()
    val sortedFolderVideos by viewModel.sortedFolderVideos.collectAsState()
    val filteredFolderVideos by viewModel.filteredFolderVideos.collectAsState()
    val videosForPlaylistAdd by viewModel.videosForPlaylistAdd.collectAsState()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()

    // Compose-only state stays in the screen: dialog visibility for
    // the sort / view dialogs, and the two saveable navigation
    // states below. The ViewModel deliberately does NOT own
    // currentFolder or showSearchPage (see LocalViewModel's KDoc):
    // both are rememberSaveable, so a process death restores the
    // drilled-in folder and the open search page.
    var showSortDialog by remember { mutableStateOf(false) }
    var showSortPopup by remember { mutableStateOf(false) }
    var popupFolderScope by remember { mutableStateOf(false) }
    var showFolderSortDialog by remember { mutableStateOf(false) }
    var showSearchPage by rememberSaveable { mutableStateOf(false) }
    var currentFolder by rememberSaveable(stateSaver = MediaFolderSaver) {
        mutableStateOf<MediaFolder?>(null)
    }
    var isRefreshing by remember { mutableStateOf(false) }

    val actions = rememberLocalActions(
        viewModel = viewModel,
        context = context,
        scope = scope,
        snackbarHostState = snackbarHostState,
        playbackCoordinator = playbackCoordinator,
        onOpenPlayer = onOpenPlayer,
        getCurrentFolder = { currentFolder },
        setCurrentFolder = { currentFolder = it },
        getShowSearchPage = { showSearchPage },
        setShowSearchPage = { showSearchPage = it },
    )

    // Issue #245: report the search page state to MainActivity so the
    // tab-swipe detector can turn off while the page is open — a swipe
    // mid-search used to switch tabs and destroy the typed query.
    LaunchedEffect(showSearchPage) {
        onSearchPageChanged(showSearchPage)
    }

    // Re-resolve the restored folder against the freshly scanned list, so
    // the header counts are never a stale snapshot from before navigation.
    LaunchedEffect(folders) {
        val path = currentFolder?.path
        if (path != null) {
            folders.find { it.path == path }?.let { currentFolder = it }
        }
    }

    // #142: back from the search page returns to Local (exactly one step).
    BackHandler(enabled = showSearchPage) {
        showSearchPage = false
    }

    // #375: immediate subfolders of the open folder (MX/mpvEx drill-in)
    val currentSubfolders = remember(sortedFolders, currentFolder) {
        currentFolder?.let { subfoldersOf(sortedFolders, it.path) } ?: emptyList()
    }

    // Intercept back button when viewing a folder to navigate up one level
    BackHandler(enabled = currentFolder != null) {
        actions.goToParentFolder()
    }

    // #144: back exits multi-select first — exactly one step — and only
    // the next back leaves the folder. Registered after the folder
    // handler so it wins while both are enabled.
    BackHandler(enabled = isSelectionMode) {
        actions.clearSelection()
    }

    DisposableEffect(permissionResultHandler) {
        onDispose { permissionResultHandler.callback = null }
    }
    SideEffect {
        permissionResultHandler.callback = { isGranted ->
            viewModel.setHasPermission(isGranted)
            if (isGranted) {
                viewModel.reloadFolders()
            } else {
                actions.clearMediaStateOnPermissionLoss()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPerm = MediaPermissions.hasVideoPermission(context)
                viewModel.setHasPermission(currentPerm)
                viewModel.reloadSettings()
                val loadedSettings = viewModel.settings.value
                // #158: if the open folder was just blacklisted, leave it.
                // #202: prefix rule — leaving applies when the open folder
                // is itself blacklisted OR sits under a blacklisted parent.
                if (currentFolder != null && isPathBlacklisted(currentFolder!!.path, loadedSettings.blacklistedFolders)) {
                    currentFolder = null
                    viewModel.clearSelection()
                }
                if (currentPerm) {
                    viewModel.reloadFolders(showAll = loadedSettings.showAllFolders)
                } else {
                    actions.clearMediaStateOnPermissionLoss()
                }
                currentFolder?.let { cf ->
                    viewModel.refreshFolderVideos(cf.id)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredFolders = sortedFolders
    val filteredVideos = sortedVideos
    val rootEmpty = if (appSettings.fileView) filteredVideos.isEmpty() else filteredFolders.isEmpty()

    LaunchedEffect(isLoading, isFolderLoading) {
        if (!isLoading && !isFolderLoading) isRefreshing = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                LocalSelectionTopBar(
                    selectionCount = selectionCount,
                    onClearSelection = actions::clearSelection,
                )
            } else if (showSearchPage) {
                // #142: the search page draws its own header (back + field).
            } else if (currentFolder != null) {
                LocalFolderTopBar(
                    folder = currentFolder!!,
                    isFolderSearching = isFolderSearching,
                    folderSearchQuery = folderSearchQuery,
                    filteredFolderVideos = filteredFolderVideos,
                    onBackToParent = actions::goToParentFolder,
                    onFolderSearchQueryChange = { viewModel.setFolderSearchQuery(it) },
                    onStartFolderSearching = { viewModel.setFolderSearching(true) },
                    onCloseFolderSearching = {
                        viewModel.setFolderSearching(false)
                        viewModel.setFolderSearchQuery("")
                    },
                    onOpenSortPopup = {
                        popupFolderScope = true
                        showSortPopup = true
                    },
                    onPlayAll = {
                        if (filteredFolderVideos.isNotEmpty()) {
                            playbackCoordinator?.playVideoFiles(filteredFolderVideos, startIndex = 0)
                            onOpenPlayer()
                        }
                    },
                )
            } else {
                LocalRootTopBar(
                    onOpenSortPopup = {
                        popupFolderScope = false
                        showSortPopup = true
                    },
                    onOpenSearchPage = { showSearchPage = true },
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !showSearchPage) {
                FloatingActionButton(
                    onClick = {
                        val played = if (currentFolder != null) {
                            if (filteredFolderVideos.isNotEmpty()) {
                                val target = actions.lastPlayedIn(filteredFolderVideos)
                                    ?: filteredFolderVideos.first()
                                playbackCoordinator?.playVideoFiles(
                                    filteredFolderVideos,
                                    startIndex = filteredFolderVideos.indexOf(target),
                                )
                                onOpenPlayer()
                                true
                            } else false
                        } else if (appSettings.fileView) {
                            val target = actions.lastPlayedIn(filteredVideos)
                                ?: filteredVideos.firstOrNull()
                            if (target != null) {
                                actions.playVideoWithFolder(target)
                                true
                            } else false
                        } else {
                            val entry = history.firstOrNull { it.isVideo }
                            val folder = entry?.let {
                                File(it.mediaUri).parent?.let { parent ->
                                    filteredFolders.firstOrNull { folder -> folder.path == parent }
                                }
                            }
                            if (folder != null) {
                                actions.playFolder(folder, startEntry = entry)
                                true
                            } else if (filteredFolders.isNotEmpty()) {
                                actions.playFolder(filteredFolders.first())
                                true
                            } else false
                        }
                        if (!played) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.empty_videos_title))
                            }
                        }
                    },
                    containerColor = MpxTheme.colors.primary,
                    contentColor = Color.White,
                    shape = MpxShapes.large,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_quick_play),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                LocalSelectionBar(
                    onPlaySelection = actions::playSelection,
                    onPlaySelectionFolder = actions::playSelectionFolder,
                    onAddSelectionToPlaylist = actions::addSelectionToPlaylist,
                    onToggleSelectionFavourite = actions::toggleSelectionFavourite,
                )
            } else if (currentFolder == null && !showSearchPage) {
                LocalStatusStrip(
                    isFileView = appSettings.fileView,
                    filteredVideosCount = filteredVideos.size,
                    filteredFolders = filteredFolders,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                val folder = currentFolder
                if (folder != null) actions.refreshFolderVideos(folder.id) else actions.refreshFolders(appSettings.showAllFolders)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showSearchPage) {
                    LocalSearchScreen(
                        folders = folders,
                        videos = allVideos,
                        hasPermission = hasPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(MediaPermissions.videoPermission())
                        },
                        showThumbnails = appSettings.showThumbnails,
                        showFieldSize = appSettings.showFieldSize,
                        showFieldDuration = appSettings.showFieldDuration,
                        showFieldCount = appSettings.showFieldCount,
                        showFieldDate = appSettings.showFieldDate,
                        showFieldLocation = appSettings.showFieldLocation,
                        showFullName = appSettings.showFullName,
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        selectedFolderIds = selectedFolderIds,
                        selectedVideoIds = selectedVideoIds,
                        isSelectionMode = isSelectionMode,
                        onToggleFolderSelection = actions::toggleFolderSelection,
                        onToggleVideoSelection = actions::toggleVideoSelection,
                        favourites = favourites,
                        onToggleVideoFavourite = actions::toggleVideoFavourite,
                        onFolderClick = { folder ->
                            showSearchPage = false
                            actions.openFolder(folder)
                        },
                        onVideoClick = { video -> actions.playVideoWithFolder(video) },
                        onBack = { showSearchPage = false },
                    )
                } else if (currentFolder != null) {
                    LocalFolderContent(
                        folder = currentFolder!!,
                        currentSubfolders = currentSubfolders,
                        filteredFolderVideos = filteredFolderVideos,
                        isFolderLoading = isFolderLoading,
                        folderSearchQuery = folderSearchQuery,
                        viewMode = viewMode,
                        appSettings = appSettings,
                        selectedVideoIds = selectedVideoIds,
                        isSelectionMode = isSelectionMode,
                        favouriteUris = favouriteUris,
                        onGoToRoot = actions::goToRoot,
                        onClearFolderSearch = { viewModel.setFolderSearchQuery("") },
                        onOpenFolder = actions::openFolder,
                        onToggleVideoFavourite = actions::toggleVideoFavourite,
                        onToggleVideoSelection = actions::toggleVideoSelection,
                        onPlayVideo = { vids, startIndex ->
                            playbackCoordinator?.playVideoFiles(vids, startIndex = startIndex)
                            onOpenPlayer()
                        },
                    )
                } else {
                    LocalRootContent(
                        hasPermission = hasPermission,
                        isLoading = isLoading,
                        rootEmpty = rootEmpty,
                        appSettings = appSettings,
                        viewMode = viewMode,
                        isTreeMode = isTreeMode,
                        sortField = sortField,
                        secondarySortField = secondarySortField,
                        filteredFolders = filteredFolders,
                        filteredVideos = filteredVideos,
                        expandedTreeFolders = expandedTreeFolders,
                        selectedFolderIds = selectedFolderIds,
                        selectedVideoIds = selectedVideoIds,
                        isSelectionMode = isSelectionMode,
                        favouriteUris = favouriteUris,
                        onRequestPermission = {
                            permissionLauncher.launch(MediaPermissions.videoPermission())
                        },
                        onShowAllFoldersToggle = {
                            viewModel.updateSettings { it.copy(showAllFolders = true) }
                            actions.refreshFolders(true)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.local_snackbar_show_all),
                                )
                            }
                        },
                        onRefreshFolders = { actions.refreshFolders(true) },
                        onOpenSortDialog = { showSortDialog = true },
                        onToggleTreeFolder = { viewModel.toggleTreeFolder(it) },
                        onToggleFolderSelection = actions::toggleFolderSelection,
                        onToggleVideoSelection = actions::toggleVideoSelection,
                        onToggleVideoFavourite = actions::toggleVideoFavourite,
                        onOpenFolder = actions::openFolder,
                        onPlayVideoWithFolder = actions::playVideoWithFolder,
                    )
                }
            }
        }
    }

    LocalDialogs(
        showSortDialog = showSortDialog,
        onDismissSortDialog = { showSortDialog = false },
        showFolderSortDialog = showFolderSortDialog,
        onDismissFolderSortDialog = { showFolderSortDialog = false },
        showSortPopup = showSortPopup,
        onDismissSortPopup = { showSortPopup = false },
        popupFolderScope = popupFolderScope,
        currentFolder = currentFolder,
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        videosForPlaylistAdd = videosForPlaylistAdd,
        customPlaylists = customPlaylists,
        showCreatePlaylistDialog = showCreatePlaylistDialog,
        viewModel = viewModel,
        appSettings = appSettings,
        sortField = sortField,
        secondarySortField = secondarySortField,
        folderSortField = folderSortField,
        folderSecondarySortField = folderSecondarySortField,
        viewMode = viewMode,
        isTreeMode = isTreeMode,
        onViewModeChange = actions::updateViewMode,
        onTreeModeChange = actions::updateTreeMode,
        onOpenFolderSortDialog = { showFolderSortDialog = true },
        onOpenRootSortDialog = { showSortDialog = true },
        onRefreshFolders = actions::refreshFolders,
        snackbarHostState = snackbarHostState,
        scope = scope,
    )
}
