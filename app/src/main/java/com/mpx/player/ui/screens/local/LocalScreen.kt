package com.mpx.player.ui.screens.local
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.playback.PlaybackCoordinator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.util.MediaPermissions
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

/**
 * Saver for the open folder (#175): MediaFolder is a plain data class,
 * not Parcelable, so it is saved as its Bundle-safe fields and rebuilt
 * on restore. Back-stack restore then keeps the folder the video was
 * played from, instead of dropping back to Home.
 */
private val MediaFolderSaver: Saver<MediaFolder?, List<Any>> = Saver(
    save = { folder ->
        folder?.let {
            listOf(
                it.id,
                it.name,
                it.path,
                it.videoCount,
                it.totalSizeFormatted,
                it.isNew,
                it.lastModified,
                it.isStorageRemovable,
                it.totalDurationMs,
                it.totalSizeBytes,
            )
        }
    },
    restore = { saved ->
        MediaFolder(
            id = saved[0] as String,
            name = saved[1] as String,
            path = saved[2] as String,
            videoCount = saved[3] as Int,
            totalSizeFormatted = saved[4] as String,
            isNew = saved[5] as Boolean,
            lastModified = saved[6] as Long,
            isStorageRemovable = saved[7] as Boolean,
            totalDurationMs = saved[8] as Long,
            // Appended after totalDurationMs (#307): state saved by an
            // older build has only 9 entries, so default to 0L there.
            totalSizeBytes = saved.getOrNull(9) as? Long ?: 0L,
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
    // #156: the header layout button opens the Sort & View popup
    // (LocalSortPopup), which replaced #141's LocalLayoutSheet.
    var showSortPopup by remember { mutableStateOf(false) }
    var popupFolderScope by remember { mutableStateOf(false) }
    var showFolderSortDialog by remember { mutableStateOf(false) }
    // #142: the search page replaces the old inline root search.
    // Issue #318: rememberSaveable, not remember — the query lives
    // in the ViewModel's SavedStateHandle, but the flag gating the
    // page still needs to survive rotation, or the saved query would
    // never be reached. (Same query + flag pair as Music's #255.)
    var showSearchPage by rememberSaveable { mutableStateOf(false) }
    // Folder view mode state (drilled-down folder contents view).
    // Issue #175: rememberSaveable, not remember — Compose Navigation
    // disposes this screen's composition when the Player destination is
    // pushed, so plain remember re-initialised the folder to null (Home)
    // on the way back. The Saver above round-trips the folder through
    // the back-stack entry's saved state.
    var currentFolder by rememberSaveable(stateSaver = MediaFolderSaver) {
        mutableStateOf<MediaFolder?>(null)
    }
    var isRefreshing by remember { mutableStateOf(false) }

    // Issue #245: report the search page state to MainActivity so the
    // tab-swipe detector can turn off while the page is open — a swipe
    // mid-search used to switch tabs and destroy the typed query.
    // LaunchedEffect also fires on first composition, reporting false,
    // which resets the flag if this screen was re-created while the
    // page was open (e.g. after returning from the Player). The
    // callback captures the parent's state object, not its value, so a
    // stale instance still writes to the same state.
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

    fun updateViewMode(newMode: ViewMode) {
        viewModel.updateViewMode(newMode)
    }

    fun updateTreeMode(tree: Boolean) {
        viewModel.updateTreeMode(tree)
    }

    fun clearSelection() {
        viewModel.clearSelection()
    }

    fun toggleFolderSelection(folder: MediaFolder) {
        viewModel.toggleFolderSelection(folder)
    }

    fun toggleVideoSelection(video: VideoFile) {
        viewModel.toggleVideoSelection(video)
    }

    // #146: shared "back to root" — the back button and the breadcrumb's
    // Local segment both run this (clears the folder and its search state).
    // Issue #276: also clear the multi-select — at root in folder mode
    // selectedVideosInView() returns emptyList, so a folder's video
    // selection would survive as a lying "N selected" badge whose actions
    // resolve to nothing.
    fun goToRoot() {
        currentFolder = null
        viewModel.clearFolderViewState()
    }

    fun openFolder(folder: MediaFolder) {
        // Issue #276: a new folder is a new view — a selection made in
        // the previous folder no longer resolves against this folder's
        // videos, so clear it instead of leaving a lying badge behind.
        // Issue #186: one layout for Home and all folders — the folder
        // contents use the shared viewMode, no per-folder lookup.
        currentFolder = folder
        viewModel.openFolder(folder)
    }

    fun refreshFolderVideos(folderId: String) {
        viewModel.refreshFolderVideos(folderId)
    }

    fun refreshFolders(showAll: Boolean = appSettings.showAllFolders) {
        viewModel.refreshFolders(showAll)
    }

    // #378: startEntry resumes a specific video from the folder (quick-play
    // FAB) instead of starting at its first one. Defaults to null — every
    // other caller (tile taps, Play all) keeps the from-the-start behaviour.
    fun playFolder(folder: MediaFolder, startEntry: PlaybackHistoryItem? = null) {
        scope.launch(Dispatchers.IO) {
            val videos = viewModel.videosInFolder(folder.id)
            withContext(Dispatchers.Main) {
                if (videos.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_no_playable_videos, folder.name)
                        )
                    }
                } else {
                    // #378: randomStart is gone — the FAB was its only
                    // caller and it never rolls dice anymore. startEntry
                    // resumes a specific video: the queue starts there,
                    // or at 0 when that video is gone.
                    val startIndex = startEntry?.let { entry ->
                        videos.indexOfFirst { it.id == entry.mediaId || it.path == entry.mediaUri }
                    }?.takeIf { it >= 0 } ?: 0
                    playbackCoordinator?.playVideoFiles(videos, startIndex)
                    onOpenPlayer()
                }
            }
        }
    }

    fun playVideoWithFolder(video: VideoFile) {
        scope.launch(Dispatchers.IO) {
            val vids = if (video.bucketId.isNotBlank()) {
                val found = viewModel.videosInFolder(video.bucketId)
                if (found.isNotEmpty()) found else listOf(video)
            } else {
                listOf(video)
            }
            val startIndex = vids.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            withContext(Dispatchers.Main) {
                playbackCoordinator?.playVideoFiles(vids, startIndex)
                onOpenPlayer()
            }
        }
    }

    // Issue #378: the last played video in a list (see LocalViewModel).
    fun lastPlayedIn(videos: List<VideoFile>): VideoFile? =
        viewModel.lastPlayedIn(videos)

    fun clearMediaStateOnPermissionLoss() {
        // Issue #319: the folder is closed here, not in the ViewModel
        // — the screen owns currentFolder — so the ON_RESUME
        // refreshFolderVideos below is skipped instead of rescanning
        // a folder the app can no longer read.
        currentFolder = null
        viewModel.clearMediaStateOnPermissionLoss()
    }

    DisposableEffect(permissionResultHandler) {
        onDispose { permissionResultHandler.callback = null }
    }
    SideEffect {
        permissionResultHandler.callback = { isGranted ->
            viewModel.setHasPermission(isGranted)
            if (isGranted) {
                // Issue #295: the scan runs through the ViewModel's
                // reload path, which cancels any in-flight scan first.
                viewModel.reloadFolders()
            } else {
                // Issue #319: clear all media state, not just folders.
                clearMediaStateOnPermissionLoss()
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
                    // Issue #276: leaving the folder this way must clear
                    // the selection too, like every other folder exit.
                    viewModel.clearSelection()
                }
                // The view mode / tree mode re-sync the screen used to do
                // here now follows the settings reload in the ViewModel.
                if (currentPerm) {
                    viewModel.reloadFolders(showAll = loadedSettings.showAllFolders)
                } else {
                    // Issue #319: clear all media state, not just
                    // folders — and close the open folder, so the
                    // refreshFolderVideos call below is skipped
                    // (currentFolder is null) instead of rescanning
                    // a folder the app can no longer read.
                    clearMediaStateOnPermissionLoss()
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

    // #142: root lists are no longer query-filtered — search lives on its
    // own page over these same sorted lists.
    val filteredFolders = sortedFolders

    // File view: all videos ungrouped, sorted and filtered
    val filteredVideos = sortedVideos

    val rootEmpty = if (appSettings.fileView) filteredVideos.isEmpty() else filteredFolders.isEmpty()

    // #375: immediate subfolders of the open folder (MX/mpvEx drill-in):
    // derived from the same scanned list through the tree's parent logic,
    // so synthesized intermediates appear too. The folder view lists
    // these above the folder's direct videos; tapping one drills in.
    val currentSubfolders = remember(filteredFolders, currentFolder) {
        currentFolder?.let { subfoldersOf(filteredFolders, it.path) } ?: emptyList()
    }

    // #375: back from a folder goes up exactly one level (MX/mpvEx),
    // not straight to root — unless the folder is already a root, in
    // which case there is no parent and it goes to the root list.
    fun goToParentFolder() {
        val parent = currentFolder?.let { viewModel.findParentFolder(it.path) }
        if (parent != null) openFolder(parent) else goToRoot()
    }

    // Intercept back button when viewing a folder to navigate up one level
    BackHandler(enabled = currentFolder != null) {
        goToParentFolder()
    }

    // #144: back exits multi-select first — exactly one step — and only
    // the next back leaves the folder. Registered after the folder
    // handler so it wins while both are enabled.
    BackHandler(enabled = isSelectionMode) {
        clearSelection()
    }

    // #144: multi-select actions for the action bar. They resolve the
    // current selection to videos: directly selected videos PLUS — for
    // selected folders — the videos inside them (#233: both halves are
    // combined, never videos-first with a folders-only fallback).
    // There is NO delete action: it never existed in the tile menus.
    fun playSelection() {
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by id so
            // a video that is both directly selected and inside a
            // selected folder plays once.
            val all = viewModel.resolveSelectionVideos(currentFolder, showSearchPage)
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.first().name,
                        )
                    )
                } else {
                    playbackCoordinator?.playVideoFiles(all)
                    onOpenPlayer()
                }
                viewModel.clearSelection()
            }
        }
    }

    fun playSelectionFolder() {
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isNotEmpty()) {
            // Play the folder the first selected video lives in,
            // starting at that video.
            val first = videos.first()
            scope.launch(Dispatchers.IO) {
                val vids = if (first.bucketId.isNotBlank()) {
                    viewModel.videosInFolder(first.bucketId)
                } else {
                    listOf(first)
                }
                // Issue #233: do not drop the folder half of a mixed
                // selection — the videos inside selected folders are
                // appended after this folder's videos, deduped by id so
                // a video already in the folder plays once (including
                // when the first video's own folder is selected).
                val all = (vids + viewModel.videosInFolders(folders))
                    .distinctBy { it.id.ifBlank { it.path } }
                val startIndex = all.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
                withContext(Dispatchers.Main) {
                    playbackCoordinator?.playVideoFiles(all, startIndex)
                    onOpenPlayer()
                    viewModel.clearSelection()
                }
            }
        } else if (folders.isNotEmpty()) {
            // A selected folder IS its folder: same as Play.
            playSelection()
        }
    }

    fun addSelectionToPlaylist() {
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by id so
            // a video that is both directly selected and inside a
            // selected folder is added once.
            val all = viewModel.resolveSelectionVideos(currentFolder, showSearchPage)
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.firstOrNull()?.name ?: "",
                        )
                    )
                } else {
                    viewModel.showAddToPlaylist(all)
                }
            }
        }
    }

    // Issue #235: single-video favourite toggle for the grid card heart.
    // The ViewModel owns the toggle; the heart flips through the
    // observed favourites Flow — no manual refresh needed.
    fun toggleVideoFavourite(video: VideoFile) {
        viewModel.toggleVideoFavourite(video)
    }

    fun toggleSelectionFavourite() {
        scope.launch {
            when (viewModel.toggleSelectionFavourite(currentFolder, showSearchPage)) {
                SelectionFavouriteOutcome.ADDED -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_added_to_favourites)
                )
                SelectionFavouriteOutcome.REMOVED -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_removed_favourites)
                )
                SelectionFavouriteOutcome.EMPTY -> snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.snackbar_no_playable_videos,
                        viewModel.selectedFoldersInView(currentFolder, showSearchPage)
                            .firstOrNull()?.name ?: "",
                    )
                )
            }
        }
    }

    LaunchedEffect(isLoading, isFolderLoading) {
        if (!isLoading && !isFolderLoading) isRefreshing = false
    }

    // Issue #320: the queued-refresh drain used to live here as a
    // LaunchedEffect(isLoading); the ViewModel drains internally
    // when a scan finishes, so no effect is needed here anymore.

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                // #144: selection top bar — count + close. Back exits too.
                MpxCompactTopBar(
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_exit_selection),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.multiselect_selected_count, selectionCount),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                            color = MpxTheme.colors.textPrimary,
                        )
                    },
                )
            } else if (showSearchPage) {
                // #142: the search page draws its own header (back + field).
            } else if (currentFolder != null) {
                // Folder View Top Bar
                val folder = currentFolder!!
                MpxCompactTopBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            // Issue #276: go through goToParentFolder()
                            // (not the inline clear) so leaving the folder
                            // also clears the multi-select. #375: up one
                            // level, not straight to root.
                            goToParentFolder()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back_to_folders),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    },
                    title = {
                        if (isFolderSearching) {
                            OutlinedTextField(
                                value = folderSearchQuery,
                                onValueChange = { viewModel.setFolderSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.folder_view_search_placeholder),
                                        color = MpxTheme.colors.textSecondary,
                                        fontSize = 14.sp,
                                    )
                                },
                                singleLine = true,
                                shape = MpxShapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                                    focusedBorderColor = MpxTheme.colors.primary,
                                    unfocusedBorderColor = MpxTheme.colors.border,
                                    focusedTextColor = MpxTheme.colors.textPrimary,
                                    unfocusedTextColor = MpxTheme.colors.textPrimary,
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
                                    color = MpxTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val count = filteredFolderVideos.size
                                // #252: the count is the filtered list, so
                                // the size must be too — the whole-folder
                                // total misstates the matches while
                                // searching. With no query the filtered list
                                // IS the folder, so the stored total is kept
                                // verbatim; with a query the filtered sizes
                                // are summed and formatted the same way.
                                val sizeStr = if (folderSearchQuery.isBlank()) {
                                    folder.totalSizeFormatted
                                } else {
                                    formatSize(filteredFolderVideos.sumOf { it.sizeBytes })
                                }
                                val subText = if (count == 1) {
                                    stringResource(R.string.folder_view_subtitle_single, sizeStr)
                                } else {
                                    stringResource(R.string.folder_view_subtitle, count, sizeStr)
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    },
                    actions = {
                        if (isFolderSearching) {
                            IconButton(onClick = { viewModel.setFolderSearching(false); viewModel.setFolderSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_close_search),
                                    tint = MpxTheme.colors.textPrimary,
                                )
                            }
                        }

                        // #156: layout button opens the Sort & View popup.
                        IconButton(onClick = {
                            popupFolderScope = true
                            showSortPopup = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.cd_layout_options),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }

                        IconButton(onClick = { viewModel.setFolderSearching(true) }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }

                        // Issue #184: the old three-dot menu is gone. Its
                        // actions already live elsewhere — Sort & View and
                        // Reset view in the Sort & View popup, Refresh in
                        // pull-to-refresh — except Play all, which now has
                        // this header play button (same action: play the
                        // folder's videos from the start). The FAB plays a
                        // RANDOM video (#169); this button plays them in order.
                        IconButton(onClick = {
                            if (filteredFolderVideos.isNotEmpty()) {
                                playbackCoordinator?.playVideoFiles(filteredFolderVideos, startIndex = 0)
                                onOpenPlayer()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.folder_menu_play_all),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    },
                )
            } else {
                // Root Folders List Top Bar
                MpxCompactTopBar(
                    title = {
                        Text(
                            text = stringResource(R.string.local_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                            color = MpxTheme.colors.textPrimary,
                        )
                    },
                    actions = {
                        // #156: layout button opens the Sort & View popup.
                        IconButton(onClick = {
                            popupFolderScope = false
                            showSortPopup = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.cd_layout_options),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        // #142: opens the whole search page over all files and folders.
                        IconButton(onClick = { showSearchPage = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // #144: the quick-play FAB hides while selecting, so a tap
            // cannot play something outside the selection.
            // #142: no quick-play FAB while the search page is open.
            if (!isSelectionMode && !showSearchPage) {
                FloatingActionButton(
                    onClick = {
                        // #378: quick-play is RESUME-or-first, like
                        // mpvEx's "Play recently played or first video"
                        // — never random. MX has no play FAB on the
                        // local list at all. The queue stays the full
                        // list, starting at the resumed (or first)
                        // video, so Next still walks the folder in order.
                        val played = if (currentFolder != null) {
                            if (filteredFolderVideos.isNotEmpty()) {
                                val target = lastPlayedIn(filteredFolderVideos)
                                    ?: filteredFolderVideos.first()
                                playbackCoordinator?.playVideoFiles(
                                    filteredFolderVideos,
                                    startIndex = filteredFolderVideos.indexOf(target),
                                )
                                onOpenPlayer()
                                true
                            } else false
                        } else if (appSettings.fileView) {
                            val target = lastPlayedIn(filteredVideos)
                                ?: filteredVideos.firstOrNull()
                            if (target != null) {
                                playVideoWithFolder(target)
                                true
                            } else false
                        } else {
                            // Folder view: resume the last played video
                            // overall — its folder is found by the file's
                            // parent directory — else the first video of
                            // the first folder.
                            val entry = history.firstOrNull { it.isVideo }
                            val folder = entry?.let {
                                File(it.mediaUri).parent?.let { parent ->
                                    filteredFolders.firstOrNull { folder -> folder.path == parent }
                                }
                            }
                            if (folder != null) {
                                playFolder(folder, startEntry = entry)
                                true
                            } else if (filteredFolders.isNotEmpty()) {
                                playFolder(filteredFolders.first())
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
                    // #148: the icon sits on the primary background, so it uses
                    // White (the schemes' onPrimary in both Dark and Light),
                    // not textPrimary, which is near-black in Light and showed
                    // a black triangle on the blue FAB.
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
                // #144: multi-select action bar — exactly the old
                // tile-menu actions: Play, Play folder, Add to playlist,
                // Favourite. No delete: it never existed in those menus.
                Column {
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MpxTheme.colors.black)
                            .padding(vertical = 4.dp),
                    ) {
                        MultiSelectAction(
                            icon = Icons.Filled.PlayArrow,
                            label = stringResource(R.string.video_item_play),
                            onClick = { playSelection() },
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectAction(
                            icon = Icons.Filled.Folder,
                            label = stringResource(R.string.multiselect_play_folder),
                            onClick = { playSelectionFolder() },
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectAction(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = stringResource(R.string.music_menu_add_to_playlist),
                            onClick = { addSelectionToPlaylist() },
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectAction(
                            icon = Icons.Filled.Favorite,
                            label = stringResource(R.string.music_menu_toggle_favourite),
                            onClick = { toggleSelectionFavourite() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (currentFolder == null && !showSearchPage) {
                Column {
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MpxTheme.colors.black)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // #252: local_status_strip ("%d folders • %d
                        // videos") is folder-shaped, so in file view the
                        // list shows files while the strip counted folders.
                        // File view shows the files count instead, reusing
                        // the existing "%d videos" string — no new string.
                        Text(
                            text = if (appSettings.fileView) {
                                stringResource(
                                    R.string.folder_item_videos_count,
                                    filteredVideos.size,
                                )
                            } else {
                                stringResource(
                                    R.string.local_status_strip,
                                    filteredFolders.size,
                                    filteredFolders.sumOf { it.videoCount },
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                val folder = currentFolder
                if (folder != null) refreshFolderVideos(folder.id) else refreshFolders()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
            if (showSearchPage) {
                // ==================== SEARCH PAGE (#142) ====================
                // Whole-page fuzzy search over all folders and files.
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
                    // Phase 2b: the search query lives in the ViewModel
                    // (#318: rotation keeps it); the page renders it.
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    // #165: search results are Local's own tiles, so they
                    // share the #144 multi-select state and toggles.
                    selectedFolderIds = selectedFolderIds,
                    selectedVideoIds = selectedVideoIds,
                    isSelectionMode = isSelectionMode,
                    onToggleFolderSelection = { toggleFolderSelection(it) },
                    onToggleVideoSelection = { toggleVideoSelection(it) },
                    // Issue #321: search-result list rows get the heart too.
                    favourites = favourites,
                    onToggleVideoFavourite = { toggleVideoFavourite(it) },
                    onFolderClick = { folder ->
                        showSearchPage = false
                        openFolder(folder)
                    },
                    onVideoClick = { video -> playVideoWithFolder(video) },
                    onBack = { showSearchPage = false },
                )
            } else if (currentFolder != null) {
                // ==================== FOLDER VIEW (Drilled-Down Contents) ====================
                // Path visualizer (#146): replaces the old "VIDEOS n + Sort:"
                // header. Sort lives in the layout sheet (#141); the count
                // lives in the top bar subtitle. It sits above the
                // loading/empty states so the way back to Local is always
                // visible inside a folder.
                val folder = currentFolder!!
                LocalPathBreadcrumb(
                    segments = listOf(
                        PathSegment(
                            label = stringResource(R.string.nav_local),
                            onClick = { goToRoot() },
                        ),
                        PathSegment(label = folder.name),
                    ),
                )
                if (isFolderLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MpxLoadingIndicator()
                    }
                } else if (filteredFolderVideos.isEmpty() && folderSearchQuery.isNotBlank()) {
                    EmptyStateView(
                        title = stringResource(R.string.folder_view_search_empty_title),
                        message = stringResource(R.string.folder_view_search_empty_desc, folderSearchQuery),
                        icon = Icons.Filled.PlayArrow,
                        actionLabel = stringResource(R.string.empty_search_action),
                        onActionClick = { viewModel.setFolderSearchQuery("") },
                    )
                } else if (filteredFolderVideos.isEmpty() && currentSubfolders.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.folder_view_empty_title),
                        message = stringResource(R.string.folder_view_empty_desc),
                        icon = Icons.Filled.PlayArrow,
                        actionLabel = stringResource(R.string.action_back_to_folders),
                        // Issue #276: leaving the folder via the empty
                        // state goes through goToRoot() so the selection
                        // is cleared like every other folder exit.
                        onActionClick = { goToRoot() },
                    )
                } else {
                    // Folder contents: video list or grid based on the shared viewMode (#186)
                    // Issue #279: the list/grid states used to be created
                    // inside the viewMode branches, in the same composition
                    // slot for every folder — opening folder B kept folder
                    // A's scroll offset. Key both states by folder id so
                    // each folder starts at the top with its own state, and
                    // hoist them above the branch so toggling list <-> grid
                    // keeps each layout's own scroll position.
                    val folderKey = folder.id.ifBlank { folder.path }
                    val videoListState = remember(folderKey) { LazyListState() }
                    val videoGridState = remember(folderKey) { LazyGridState() }
                    if (viewMode == ViewMode.LIST) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = videoListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                // #375: immediate subfolders first (MX/mpvEx
                                // drill-in) — tapping one opens it; they are
                                // navigation rows, not selectable here.
                                items(
                                    items = currentSubfolders,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "folder_subfolder_row" },
                                ) { subfolder ->
                                    FolderItemRow(
                                        folder = subfolder,
                                        showThumbnails = appSettings.showThumbnails,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        isSelected = false,
                                        onLongClick = {},
                                        showCount = appSettings.showFieldCount,
                                        showFullName = appSettings.showFullName,
                                        onClick = { openFolder(subfolder) },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 76.dp),
                                        color = MpxTheme.colors.border,
                                        thickness = MpxHairline,
                                    )
                                }
                                itemsIndexed(
                                    items = filteredFolderVideos,
                                    key = { _, video -> video.id.ifBlank { video.path } },
                                    contentType = { _, _ -> "video_item_row" },
                                ) { index, video ->
                                    VideoItemRow(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        // Issue #321: list rows get the
                                        // favourite heart the grid card
                                        // already had (#235).
                                        isFavourite = video.path in favouriteUris,
                                        onToggleFavourite = { toggleVideoFavourite(video) },
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedVideoIds.contains(
                                            video.id.ifBlank { video.path }
                                        ),
                                        onLongClick = { toggleVideoSelection(video) },
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleVideoSelection(video)
                                            } else {
                                                playbackCoordinator?.playVideoFiles(filteredFolderVideos, startIndex = index)
                                                onOpenPlayer()
                                            }
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 130.dp),
                                        color = MpxTheme.colors.border,
                                        thickness = MpxHairline,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = videoGridState,
                                columns = GridCells.Fixed(appSettings.videoGridColumns.coerceIn(1, 4)),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // #375: immediate subfolders as full-width
                                // rows above the video grid — same drill-in
                                // as the list layout.
                                items(
                                    items = currentSubfolders,
                                    key = { it.id.ifBlank { it.path } },
                                    contentType = { "folder_subfolder_row" },
                                    span = { GridItemSpan(maxLineSpan) },
                                ) { subfolder ->
                                    FolderItemRow(
                                        folder = subfolder,
                                        showThumbnails = appSettings.showThumbnails,
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        isSelected = false,
                                        onLongClick = {},
                                        showCount = appSettings.showFieldCount,
                                        showFullName = appSettings.showFullName,
                                        onClick = { openFolder(subfolder) },
                                    )
                                }
                                itemsIndexed(
                                    items = filteredFolderVideos,
                                    key = { _, video -> video.id.ifBlank { video.path } },
                                    contentType = { _, _ -> "video_item_grid" },
                                ) { index, video ->
                                    VideoItemGridCard(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        // Issue #235: the folder-view grid
                                        // passed neither isFavourite nor a
                                        // handler — the heart was always
                                        // outline and dead.
                                        isFavourite = video.path in favouriteUris,
                                        onToggleFavourite = { toggleVideoFavourite(video) },
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedVideoIds.contains(
                                            video.id.ifBlank { video.path }
                                        ),
                                        onLongClick = { toggleVideoSelection(video) },
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleVideoSelection(video)
                                            } else {
                                                playbackCoordinator?.playVideoFiles(filteredFolderVideos, startIndex = index)
                                                onOpenPlayer()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== ROOT FOLDERS VIEW ====================
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
                } else if (isLoading && rootEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MpxLoadingIndicator()
                    }
                } else if (rootEmpty) {
                    if (!appSettings.showAllFolders) {
                        EmptyStateView(
                            title = stringResource(R.string.empty_videos_title),
                            message = stringResource(R.string.empty_videos_desc),
                            icon = Icons.Filled.Folder,
                            actionLabel = stringResource(R.string.list_show_all_folders),
                            onActionClick = {
                                viewModel.updateSettings { it.copy(showAllFolders = true) }
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
                    // Section Header: FILES (shared header, #107)
                    val sortLabelText = if (secondarySortField != SortField.NONE && secondarySortField != sortField) {
                        "${stringResource(sortField.labelRes)} / ${stringResource(secondarySortField.labelRes)}"
                    } else {
                        stringResource(sortField.labelRes)
                    }
                    LocalSectionHeader(
                        title = stringResource(R.string.section_files_header),
                        count = filteredVideos.size,
                        sortLabel = stringResource(R.string.local_sort_label, sortLabelText),
                        onSortClick = { showSortDialog = true },
                    )
                    // Issue #279: hoisted above the viewMode branch so
                    // toggling list <-> grid keeps each layout's own scroll
                    // position instead of discarding it.
                    val listState = rememberLazyListState()
                    val gridState = rememberLazyGridState()
                    if (viewMode == ViewMode.LIST) {
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
                                    VideoItemRow(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        // Issue #321: list rows get the
                                        // favourite heart the grid card
                                        // already had (#235).
                                        isFavourite = video.path in favouriteUris,
                                        onToggleFavourite = { toggleVideoFavourite(video) },
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedVideoIds.contains(
                                            video.id.ifBlank { video.path }
                                        ),
                                        onLongClick = { toggleVideoSelection(video) },
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) toggleVideoSelection(video)
                                            else playVideoWithFolder(video)
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 128.dp),
                                        color = MpxTheme.colors.border,
                                        thickness = MpxHairline,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(appSettings.videoGridColumns.coerceIn(1, 4)),
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
                                    val isFav = video.path in favouriteUris
                                    VideoItemGridCard(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
                                        isFavourite = isFav,
                                        // Issue #235: the file-view grid
                                        // showed the state but passed no
                                        // handler — the heart was dead.
                                        onToggleFavourite = { toggleVideoFavourite(video) },
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        showDuration = appSettings.showFieldDuration,
                                        showLocation = appSettings.showFieldLocation,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedVideoIds.contains(
                                            video.id.ifBlank { video.path }
                                        ),
                                        onLongClick = { toggleVideoSelection(video) },
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) toggleVideoSelection(video)
                                            else playVideoWithFolder(video)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Path visualizer (#146): replaces the "FOLDERS n + Sort:"
                    // row. Sort lives in the layout sheet (#141); the
                    // folder/video counts live in the bottom status strip.
                    LocalPathBreadcrumb(
                        segments = listOf(
                            PathSegment(label = stringResource(R.string.nav_local)),
                        ),
                        badgeText = if (appSettings.showAllFolders) stringResource(R.string.local_badge_show_all) else null,
                    )

                    // Folder list / grid with smooth LazyColumn/LazyGrid, stable keys and fast scrollbar
                    // #164: Tree view — the folder hierarchy inline. It
                    // applies to the folders LIST layout; GRID renders
                    // the flat grid below, exactly as in Folder mode.
                    // Issue #279: hoisted above the tree/list/grid branches
                    // so toggling between them keeps each layout's own
                    // scroll position instead of discarding it.
                    val treeListState = rememberLazyListState()
                    val listState = rememberLazyListState()
                    val gridState = rememberLazyGridState()
                    if (viewMode == ViewMode.LIST && isTreeMode) {
                        LocalFolderTree(
                            folders = filteredFolders,
                            listState = treeListState,
                            expandedKeys = expandedTreeFolders,
                            onToggleExpand = { folder ->
                                viewModel.toggleTreeFolder(folder)
                            },
                            showThumbnails = appSettings.showThumbnails,
                            showSize = appSettings.showFieldSize,
                            showDate = appSettings.showFieldDate,
                            showCount = appSettings.showFieldCount,
                            showFullName = appSettings.showFullName,
                            isSelected = { folder ->
                                selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                            },
                            onLongClick = { folder -> toggleFolderSelection(folder) },
                            onClick = { folder ->
                                if (isSelectionMode) toggleFolderSelection(folder)
                                else openFolder(folder)
                            },
                        )
                    } else if (viewMode == ViewMode.LIST) {
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
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedFolderIds.contains(
                                            folder.id.ifBlank { folder.path }
                                        ),
                                        onLongClick = { toggleFolderSelection(folder) },
                                        showCount = appSettings.showFieldCount,
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) toggleFolderSelection(folder)
                                            else openFolder(folder)
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 76.dp),
                                        color = MpxTheme.colors.border,
                                        thickness = MpxHairline,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(appSettings.folderGridColumns.coerceIn(1, 4)),
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
                                        showSize = appSettings.showFieldSize,
                                        showDate = appSettings.showFieldDate,
                                        // #144: tap toggles while selecting,
                                        // long-press enters selection mode.
                                        isSelected = selectedFolderIds.contains(
                                            folder.id.ifBlank { folder.path }
                                        ),
                                        onLongClick = { toggleFolderSelection(folder) },
                                        showCount = appSettings.showFieldCount,
                                        showFullName = appSettings.showFullName,
                                        onClick = {
                                            if (isSelectionMode) toggleFolderSelection(folder)
                                            else openFolder(folder)
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
                // Issue #276: folder-mode and file-view selections do not
                // resolve in each other (selectedFoldersInView() is
                // emptyList in file view, selectedVideosInView() is
                // emptyList at root in folder mode), so switching views
                // must clear the selection instead of leaving a badge
                // whose actions resolve to nothing.
                viewModel.setFileView(isChecked)
            },
            onSortChange = { newSort ->
                viewModel.setRootSortField(newSort)
            },
            onSecondarySortChange = { newSecSort ->
                viewModel.setRootSecondarySortField(newSecSort)
            },
            onViewChange = {
                updateViewMode(it)
            },
            onSeparateViewLayoutChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(separateViewLayout = isChecked) }
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
                viewModel.updateSettings { it.copy(showAllFolders = isChecked) }
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
                viewModel.updateSettings(commit = true) { it.copy(showFieldSize = isChecked) }
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDate = isChecked) }
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = isChecked) }
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = isChecked) }
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortReverse = isChecked) }
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortSecondaryReverse = isChecked) }
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortNumeric = isChecked) }
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortCaseSensitive = isChecked) }
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortIgnoreExtension = isChecked) }
            },
            onDismiss = { showSortDialog = false },
        )
    }

    // SortViewDialog for Folder Contents View — issue #186: the folder
    // contents share Home's layout (viewMode), no per-folder override.
    if (showFolderSortDialog && currentFolder != null) {
        SortViewDialog(
            currentSort = folderSortField,
            currentView = viewMode,
            // Folder contents are files, so the file-metadata rows
            // (duration, location, ignore-extension) apply here.
            isFileView = true,
            onViewChange = { newMode ->
                updateViewMode(newMode)
            },
            onSortChange = { newSort ->
                viewModel.setFolderSortField(newSort)
            },
            currentSecondarySort = folderSecondarySortField,
            onSecondarySortChange = { newSecSort ->
                viewModel.setFolderSecondarySortField(newSecSort)
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldSize = isChecked) }
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDate = isChecked) }
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = isChecked) }
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = isChecked) }
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortReverse = isChecked) }
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortSecondaryReverse = isChecked) }
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortNumeric = isChecked) }
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortCaseSensitive = isChecked) }
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                viewModel.updateSettings(commit = true) { it.copy(sortIgnoreExtension = isChecked) }
            },
            onDismiss = {
                showFolderSortDialog = false
            },
        )
    }

    // Local "View" sheet (#141): layout, tile size, sort. Folder scope edits
    // that folder's override; root scope edits the global mode.
    // #156: Sort & View popup (mpvEx-style), replacing #141's layout sheet.
    if (showSortPopup) {
        if (popupFolderScope && currentFolder != null) {
            val folder = currentFolder!!
            LocalSortPopup(
                isFolderScope = true,
                currentSort = folderSortField,
                onSortChange = { newSort ->
                    viewModel.setFolderSortField(newSort)
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    viewModel.updateSettings(commit = true) { it.copy(sortReverse = reversed) }
                },
                currentView = viewMode,
                onViewChange = { newMode ->
                    // Issue #186: the shared layout — a change here applies
                    // to Home and every folder at once.
                    updateViewMode(newMode)
                    scope.launch {
                        val msg = if (newMode == ViewMode.GRID) {
                            context.getString(R.string.view_mode_switched_grid)
                        } else {
                            context.getString(R.string.view_mode_switched_list)
                        }
                        snackbarHostState.showSnackbar(msg)
                    }
                },
                // #164: Folder / Tree mode for the root folders list.
                isTreeMode = isTreeMode,
                onTreeModeChange = { updateTreeMode(it) },
                // Issue #309: Tree mode edits the root folders list and
                // changes nothing inside a folder — hide the tile here
                // (Music precedent: showTreeMode = false).
                showTreeMode = false,
                folderGridColumns = appSettings.folderGridColumns,
                onFolderGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.folderGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(folderGridColumns = columns) }
                    }
                },
                // Issue #309: the Folder-grid slider edits the root
                // folders grid only — hide it in folder scope. The
                // Video-grid slider below stays: folder contents
                // render in the video grid (videoGridColumns).
                showFolderGridColumns = false,
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.videoGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(videoGridColumns = columns) }
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFullName = show) }
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = show) }
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldCount = show) }
                },
                // Issue #309: the Total-videos chip is folder-row-only —
                // no video row inside a folder renders it. Hide the chip
                // here; the setting itself is untouched.
                showFieldCountChip = false,
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = show) }
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldSize = show) }
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDate = show) }
                },
                onDismiss = { showSortPopup = false },
                // Issue #232: the folder-scope full sort dialog
                // (SortViewDialog for folder contents) had no entry point
                // — this row in the popup is it.
                onOpenFullSortDialog = { showFolderSortDialog = true },
            )
        } else {
            LocalSortPopup(
                isFolderScope = false,
                currentSort = sortField,
                onSortChange = { newSort ->
                    viewModel.setRootSortField(newSort)
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    viewModel.updateSettings(commit = true) { it.copy(sortReverse = reversed) }
                },
                currentView = viewMode,
                onViewChange = { updateViewMode(it) },
                // #164: Folder / Tree mode for the root folders list.
                isTreeMode = isTreeMode,
                onTreeModeChange = { updateTreeMode(it) },
                folderGridColumns = appSettings.folderGridColumns,
                onFolderGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.folderGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(folderGridColumns = columns) }
                    }
                },
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.videoGridColumns) {
                        viewModel.updateSettings(commit = true) { it.copy(videoGridColumns = columns) }
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFullName = show) }
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldLocation = show) }
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldCount = show) }
                },
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDuration = show) }
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldSize = show) }
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    viewModel.updateSettings(commit = true) { it.copy(showFieldDate = show) }
                },
                onDismiss = { showSortPopup = false },
                // Issue #232: the root-scope full sort dialog
                // (SortViewDialog for the root folders list) was reachable
                // only from the file-view section header — this row in the
                // popup is the entry point from the folders view.
                onOpenFullSortDialog = { showSortDialog = true },
            )
        }
    }

    // Add Videos to Playlist Dialog — one video from a tile menu, or the
    // whole #144 multi-select action-bar selection. The same dialog
    // handles both; a multi-add shows the item count as its title.
    if (showAddToPlaylistDialog && videosForPlaylistAdd.isNotEmpty()) {
        val vids = videosForPlaylistAdd
        val videoPlaylists = customPlaylists.filter { it.isVideoPlaylist }
        AddToPlaylistDialog(
            playlists = videoPlaylists,
            mediaTitle = if (vids.size == 1) vids.first().title
            else stringResource(R.string.playlist_items_count, vids.size),
            onDismiss = {
                viewModel.dismissAddToPlaylist()
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    // Issue #230: the ViewModel counts the real adds
                    // (addItemToPlaylist returns -1 for an item already
                    // in the playlist) so the feedback tells the truth.
                    val result = viewModel.addPendingToPlaylist(pl.id)
                    when {
                        result.added == result.total -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_added_to_playlist_msg, pl.name)
                        )
                        result.added == 0 -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_already_in_playlist_msg, pl.name)
                        )
                        else -> snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.music_added_some_to_playlist_msg,
                                result.added, pl.name, result.total - result.added,
                            )
                        )
                    }
                }
            },
            onCreateNewPlaylist = {
                // Issue #310: keep videosForPlaylistAdd — the create
                // dialog's onConfirm adds them to the new playlist, and
                // its onDismiss puts this dialog back if creation is
                // cancelled, so the pending videos are never dropped.
                viewModel.openCreatePlaylist()
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            // Issue #310: cancelling creation must not lose the pending
            // videos — put the add dialog back so the user can pick an
            // existing playlist or try creating again.
            onDismiss = {
                viewModel.dismissCreatePlaylist()
            },
            // #268: Local creates video playlists; the fixed type is
            // passed through (no selector on this screen).
            initialIsVideoPlaylist = true,
            onConfirm = { name, isVideo ->
                scope.launch {
                    // Issue #310: carry the pending add-to-playlist
                    // videos into the newly created playlist, with the
                    // same item mapping, order and feedback as adding
                    // to an existing playlist above. A failure keeps
                    // the pending videos and puts the add dialog back
                    // so the user can retry instead of silently losing
                    // the selection.
                    when (val outcome = viewModel.createPlaylistAndAddPending(name, isVideo)) {
                        is CreatePlaylistOutcome.Created -> snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_playlist_created, outcome.name)
                        )
                        is CreatePlaylistOutcome.Added -> when {
                            outcome.added == outcome.total -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_added_to_playlist_msg, outcome.name)
                            )
                            outcome.added == 0 -> snackbarHostState.showSnackbar(
                                context.getString(R.string.music_already_in_playlist_msg, outcome.name)
                            )
                            else -> snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.music_added_some_to_playlist_msg,
                                    outcome.added, outcome.name, outcome.total - outcome.added,
                                )
                            )
                        }
                        CreatePlaylistOutcome.Failed -> Unit
                    }
                }
            },
        )
    }
}

/**
 * #252: formats a summed byte count for the folder subtitle while
 * searching. Mirrors MediaStoreRepository.formatSize exactly (same
 * thresholds, same "%.1f GB/MB/KB" units) so a filtered sum reads the
 * same as the stored whole-folder total it replaces.
 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
