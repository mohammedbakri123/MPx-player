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
import androidx.compose.foundation.lazy.grid.GridCells
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
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.components.FastScrollbar
import com.mpx.player.ui.components.GridFastScrollbar
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.util.MediaPermissions
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        )
    },
)

/**
 * #202: is this path covered by the folder blacklist (#158)? A blacklisted
 * path hides the folder itself AND everything under it — exact match, or
 * the path starts with the blacklisted path plus a separator. The
 * separator matters: blacklisting `/Movies` must NOT hide the sibling
 * `/Movies2` (a different folder whose name merely starts with the same
 * characters). Both sides go through [normalizedTreePath] first, so a
 * trailing slash on either side is harmless and the storage-prefix aliases
 * (#196: `/sdcard`, `/mnt/sdcard`, `/storage/emulated/legacy`, …) compare
 * equal to their canonical `/storage/emulated/0` form. A case-insensitive
 * fallback mirrors the tree's parent walk (#196): scanned paths can differ
 * in case from the stored entry for the same directory. A blacklisted
 * storage root (e.g. `/storage/emulated/0`) hides everything under it —
 * that is the user's explicit choice, no guard is applied here.
 */
internal fun isPathBlacklisted(path: String, blacklist: Set<String>): Boolean {
    if (blacklist.isEmpty()) return false
    val normalized = normalizedTreePath(path)
    return blacklist.any { entry ->
        val base = normalizedTreePath(entry)
        normalized == base ||
            normalized.startsWith("$base/") ||
            normalized.equals(base, ignoreCase = true) ||
            normalized.startsWith("$base/", ignoreCase = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    permissionResultHandler: ActivityResultHandler<Boolean>,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    // Issue #245: reports whether the whole-page search is open so
    // MainActivity can turn the tab-swipe detector off while it is —
    // a swipe mid-search used to switch tabs and destroy the typed
    // query. Defaults to a no-op for other callers.
    onSearchPageChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaStoreRepository(context) }
    val realSettingsRepo = remember(settingsRepository, context) {
        settingsRepository ?: SharedPrefsAppSettingsRepository(context)
    }
    var appSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    // #100: permission state survives back-stack restore (rememberSaveable),
    // and the last scan survives tab switches via the repository's
    // process-lifetime cache, so returning to Local renders the previous
    // content instantly instead of flashing an empty page while the rescan
    // runs. First launch (no cache) still starts in the loading state.
    var hasPermission by rememberSaveable {
        mutableStateOf(MediaPermissions.hasVideoPermission(context))
    }
    var folders by remember {
        mutableStateOf(
            MediaStoreRepository.cachedFolders(
                appSettings.showAllFolders,
                appSettings.showHiddenFiles,
            ) ?: emptyList()
        )
    }
    var isLoading by remember {
        mutableStateOf(
            MediaStoreRepository.cachedFolders(
                appSettings.showAllFolders,
                appSettings.showHiddenFiles,
            ) == null
        )
    }
    var allVideos by remember {
        mutableStateOf(
            MediaStoreRepository.cachedVideos(
                appSettings.showAllFolders,
                appSettings.showHiddenFiles,
            ) ?: emptyList()
        )
    }

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
        realSettingsRepo.save(updated, commit = true)
        appSettings = updated
    }
    // #164: Tree view (Sort & View popup): the root folders list shows
    // the folder hierarchy inline. Persisted in AppSettings.localTreeMode;
    // expanded folders are session-only (not persisted).
    var isTreeMode by remember { mutableStateOf(appSettings.localTreeMode) }
    var expandedTreeFolders by remember { mutableStateOf(setOf<String>()) }

    fun updateTreeMode(tree: Boolean) {
        isTreeMode = tree
        val updated = appSettings.copy(localTreeMode = tree)
        realSettingsRepo.save(updated, commit = true)
        appSettings = updated
    }
    var showSortDialog by remember { mutableStateOf(false) }
    // #156: the header layout button opens the Sort & View popup
    // (LocalSortPopup), which replaced #141's LocalLayoutSheet.
    var showSortPopup by remember { mutableStateOf(false) }
    var popupFolderScope by remember { mutableStateOf(false) }
    // #142: the search page replaces the old inline root search.
    var showSearchPage by remember { mutableStateOf(false) }
    // Issue #245: report the search page state to MainActivity so the
    // tab-swipe detector can turn off while the page is open — a swipe
    // mid-search used to switch tabs and destroy the typed query (this
    // flag and the query inside LocalSearchScreen are plain remember,
    // lost when the composition is disposed). LaunchedEffect also fires
    // on first composition, reporting false, which resets the flag if
    // this screen was re-created while the page was open (e.g. after
    // returning from the Player). The callback captures the parent's
    // state object, not its value, so a stale instance still writes to
    // the same state.
    LaunchedEffect(showSearchPage) {
        onSearchPageChanged(showSearchPage)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Folder view mode state (drilled-down folder contents view).
    // Issue #175: rememberSaveable, not remember — Compose Navigation
    // disposes this screen's composition when the Player destination is
    // pushed, so plain remember re-initialised the folder to null (Home)
    // on the way back. The Saver above round-trips the folder through
    // the back-stack entry's saved state.
    var currentFolder by rememberSaveable(stateSaver = MediaFolderSaver) {
        mutableStateOf<MediaFolder?>(null)
    }
    // Re-resolve the restored folder against the freshly scanned list, so
    // the header counts are never a stale snapshot from before navigation.
    LaunchedEffect(folders) {
        val path = currentFolder?.path
        if (path != null) {
            folders.find { it.path == path }?.let { currentFolder = it }
        }
    }
    var folderVideos by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var isFolderLoading by remember { mutableStateOf(false) }
    // #277: the in-flight folder-videos load — cancelled before a new one
    // starts, so a stale scan can never overwrite the current folder's
    // videos (same Job-handle pattern as PlayerScreen's hide jobs).
    var folderVideosJob by remember { mutableStateOf<Job?>(null) }
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
    // #144: videos queued for "Add to playlist" — the whole
    // multi-select action-bar selection.
    var videosForPlaylistAdd by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    // #144: long-press multi-select. One selection per view: folders on
    // the root folder list, videos in file view and inside a folder.
    // Long-press toggles an item (entering selection mode when empty);
    // a tap while selecting toggles too. Back / the X exits and clears.
    var selectedFolderIds by remember { mutableStateOf(setOf<String>()) }
    var selectedVideoIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedFolderIds.isNotEmpty() || selectedVideoIds.isNotEmpty()
    val selectionCount = selectedFolderIds.size + selectedVideoIds.size

    fun clearSelection() {
        selectedFolderIds = emptySet()
        selectedVideoIds = emptySet()
    }

    fun toggleFolderSelection(folder: MediaFolder) {
        val key = folder.id.ifBlank { folder.path }
        selectedFolderIds =
            if (selectedFolderIds.contains(key)) selectedFolderIds - key
            else selectedFolderIds + key
    }

    fun toggleVideoSelection(video: VideoFile) {
        val key = video.id.ifBlank { video.path }
        selectedVideoIds =
            if (selectedVideoIds.contains(key)) selectedVideoIds - key
            else selectedVideoIds + key
    }

    // #146: shared "back to root" — the back button and the breadcrumb's
    // Local segment both run this (clears the folder and its search state).
    // Issue #276: also clear the multi-select — at root in folder mode
    // selectedVideosInView() returns emptyList, so a folder's video
    // selection would survive as a lying "N selected" badge whose actions
    // resolve to nothing.
    fun goToRoot() {
        currentFolder = null
        isFolderSearching = false
        folderSearchQuery = ""
        clearSelection()
    }

    // #142: back from the search page returns to Local (exactly one step).
    BackHandler(enabled = showSearchPage) {
        showSearchPage = false
    }

    // Intercept back button when viewing a folder to navigate back to folder list
    BackHandler(enabled = currentFolder != null) {
        goToRoot()
    }

    // #144: back exits multi-select first — exactly one step — and only
    // the next back leaves the folder. Registered after the folder
    // handler so it wins while both are enabled.
    BackHandler(enabled = isSelectionMode) {
        clearSelection()
    }

    // #277: the single entry point for every folder-videos scan
    // (openFolder, refreshFolderVideos — hence the ON_RESUME refresh and
    // pull-to-refresh too). Declared before openFolder, because local
    // functions are only visible after their declaration point.
    // One in-flight load at a time: the previous load is cancelled
    // before a new one starts, and the result is only applied if this
    // folder is still the open one when the scan completes — tapping
    // folder A then B quickly can no longer show A's videos under B's
    // header, whichever scan finishes last. isFolderLoading is written
    // here on Main (all callers run on Main); the old code wrote it
    // from the IO thread in both functions.
    fun loadFolderVideos(folderId: String) {
        folderVideosJob?.cancel()
        isFolderLoading = true
        folderVideosJob = scope.launch(Dispatchers.IO) {
            val vids = repository.getVideosInFolder(folderId)
            withContext(Dispatchers.Main) {
                if (currentFolder?.id == folderId) {
                    folderVideos = vids
                }
                isFolderLoading = false
            }
        }
    }

    fun openFolder(folder: MediaFolder) {
        // Issue #276: a new folder is a new view — a selection made in
        // the previous folder no longer resolves against this folder's
        // videos, so clear it instead of leaving a lying badge behind.
        clearSelection()
        currentFolder = folder
        isFolderSearching = false
        folderSearchQuery = ""
        // Issue #186: one layout for Home and all folders — the folder
        // contents use the shared viewMode, no per-folder lookup.
        // #277: clear at once, so the previous folder's videos are never
        // shown under this folder's header while the scan runs (same as
        // the #269 playlist-dialog clear).
        folderVideos = emptyList()
        loadFolderVideos(folder.id)
    }

    fun refreshFolderVideos(folderId: String) {
        // #277: same folder — no synchronous clear, so pull-to-refresh /
        // ON_RESUME keeps showing the current videos while reloading.
        loadFolderVideos(folderId)
    }

    // #169: randomStart plays a random video from the folder instead of
    // its first one (quick-play FAB). Defaults to false — every other
    // caller (tile taps, Play all) keeps the old from-the-start behaviour.
    fun playFolder(folder: MediaFolder, randomStart: Boolean = false) {
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
                    val startIndex = if (randomStart) videos.indices.random() else 0
                    playbackManager?.playVideoFiles(videos, startIndex)
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
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())


    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // #158: folder blacklist (Preferences > Folders). A blacklisted
    // folder and its videos never reach the UI; the filter runs on the
    // scanned result at every load site, so it also applies after
    // returning from Preferences. #202: the match is a prefix rule
    // ([isPathBlacklisted]) — a blacklisted folder hides its subfolders
    // and their videos too, not just its own top level.
    fun applyFolderBlacklist(
        scanned: List<MediaFolder>,
        scannedVideos: List<VideoFile>,
        blacklist: Set<String>,
    ): Pair<List<MediaFolder>, List<VideoFile>> {
        if (blacklist.isEmpty()) return scanned to scannedVideos
        val visibleFolders = scanned.filter { !isPathBlacklisted(it.path, blacklist) }
        val visibleVideos = scannedVideos.filter { video ->
            val parent = File(video.path).parent
            parent == null || !isPathBlacklisted(parent, blacklist)
        }
        return visibleFolders to visibleVideos
    }

    fun refreshFolders(showAll: Boolean = appSettings.showAllFolders) {
        if (isLoading) return
        if (MediaPermissions.hasVideoPermission(context)) {
            hasPermission = true
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val scanned = repository.getMediaFolders(showAll)
                val scannedVideos = repository.getAllVideos(showAll)
                val (visibleFolders, visibleVideos) = applyFolderBlacklist(scanned, scannedVideos, appSettings.blacklistedFolders)
                withContext(Dispatchers.Main) {
                    folders = visibleFolders
                    allVideos = visibleVideos
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
                val (visibleFolders, visibleVideos) = applyFolderBlacklist(scanned, scannedVideos, appSettings.blacklistedFolders)
                withContext(Dispatchers.Main) {
                    folders = visibleFolders
                    allVideos = visibleVideos
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
                // #158: if the open folder was just blacklisted, leave it.
                // #202: prefix rule — leaving applies when the open folder
                // is itself blacklisted OR sits under a blacklisted parent.
                if (currentFolder != null && isPathBlacklisted(currentFolder!!.path, loadedSettings.blacklistedFolders)) {
                    currentFolder = null
                    // Issue #276: leaving the folder this way must clear
                    // the selection too, like every other folder exit.
                    clearSelection()
                }
                viewMode = if (loadedSettings.localViewMode == "GRID") ViewMode.GRID else ViewMode.LIST
                isTreeMode = loadedSettings.localTreeMode
                if (currentPerm) {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        val scanned = repository.getMediaFolders(loadedSettings.showAllFolders)
                        val scannedVideos = repository.getAllVideos(loadedSettings.showAllFolders)
                        val (visibleFolders, visibleVideos) = applyFolderBlacklist(scanned, scannedVideos, loadedSettings.blacklistedFolders)
                        withContext(Dispatchers.Main) {
                            folders = visibleFolders
                            allVideos = visibleVideos
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

    // #142: root lists are no longer query-filtered — search lives on its
    // own page over these same sorted lists.
    val filteredFolders = sortedFolders

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

    val filteredVideos = sortedVideos

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

    // #144: multi-select actions for the action bar. They resolve the
    // current selection to videos: directly selected videos PLUS — for
    // selected folders — the videos inside them (#233: both halves are
    // combined, never videos-first with a folders-only fallback).
    // There is NO delete action: it never existed in the tile menus.
    // Issue #234: the resolvers below filter the UNFILTERED sorted lists
    // (sortedFolderVideos / sortedVideos / sortedFolders) — never the
    // search-filtered ones. The search box helps find items; it must not
    // narrow an existing selection. (filteredVideos / filteredFolders are
    // aliases of the sorted lists today, but the resolvers name the sorted
    // lists so the intent survives any future filtering of those aliases.
    // The in-folder search box DOES filter filteredFolderVideos, which is
    // why the folder branch previously dropped hidden selections here.)
    fun selectedVideosInView(): List<VideoFile> {
        return when {
            // #165: on the search page the selection resolves against
            // the full video list — matches are a subset of it, and the
            // folder/file views below cannot see them.
            showSearchPage -> allVideos.filter {
                selectedVideoIds.contains(it.id.ifBlank { it.path })
            }
            currentFolder != null -> sortedFolderVideos.filter {
                selectedVideoIds.contains(it.id.ifBlank { it.path })
            }
            appSettings.fileView -> sortedVideos.filter {
                selectedVideoIds.contains(it.id.ifBlank { it.path })
            }
            else -> emptyList()
        }
    }

    fun selectedFoldersInView(): List<MediaFolder> {
        return when {
            // #165: same as videos — search matches live in the full
            // folder list.
            showSearchPage -> folders.filter {
                selectedFolderIds.contains(it.id.ifBlank { it.path })
            }
            currentFolder == null && !appSettings.fileView -> {
                sortedFolders.filter {
                    selectedFolderIds.contains(it.id.ifBlank { it.path })
                }
            }
            else -> emptyList()
        }
    }

    // Issue #233: videos inside the given folders — the folder half of a
    // multi-select. Call only from an IO coroutine: it scans MediaStore
    // buckets. (The folder half must never be silently dropped when the
    // selection also holds videos — see the four actions below.)
    fun videosInFolders(folders: List<MediaFolder>): List<VideoFile> =
        folders.flatMap { repository.getVideosInFolder(it.id) }

    fun playSelection() {
        val videos = selectedVideosInView()
        val folders = selectedFoldersInView()
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by id so
            // a video that is both directly selected and inside a
            // selected folder plays once.
            val all = (videos + videosInFolders(folders))
                .distinctBy { it.id.ifBlank { it.path } }
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.first().name,
                        )
                    )
                } else {
                    playbackManager?.playVideoFiles(all)
                    onOpenPlayer()
                }
                clearSelection()
            }
        }
    }

    fun playSelectionFolder() {
        val videos = selectedVideosInView()
        val folders = selectedFoldersInView()
        if (videos.isNotEmpty()) {
            // Play the folder the first selected video lives in,
            // starting at that video.
            val first = videos.first()
            scope.launch(Dispatchers.IO) {
                val vids = if (first.bucketId.isNotBlank()) {
                    repository.getVideosInFolder(first.bucketId)
                } else {
                    listOf(first)
                }
                // Issue #233: do not drop the folder half of a mixed
                // selection — the videos inside selected folders are
                // appended after this folder's videos, deduped by id so
                // a video already in the folder plays once (including
                // when the first video's own folder is selected).
                val all = (vids + videosInFolders(folders))
                    .distinctBy { it.id.ifBlank { it.path } }
                val startIndex = all.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
                withContext(Dispatchers.Main) {
                    playbackManager?.playVideoFiles(all, startIndex)
                    onOpenPlayer()
                    clearSelection()
                }
            }
        } else if (folders.isNotEmpty()) {
            // A selected folder IS its folder: same as Play.
            playSelection()
        }
    }

    fun addSelectionToPlaylist() {
        val videos = selectedVideosInView()
        val folders = selectedFoldersInView()
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by id so
            // a video that is both directly selected and inside a
            // selected folder is added once.
            val all = (videos + videosInFolders(folders))
                .distinctBy { it.id.ifBlank { it.path } }
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.firstOrNull()?.name ?: "",
                        )
                    )
                } else {
                    videosForPlaylistAdd = all
                    showAddToPlaylistDialog = true
                }
            }
        }
    }

    // Issue #235: single-video favourite toggle for the grid card heart.
    // Uses the same repository toggle the player uses, so the heart flips
    // through the observed favourites Flow — no manual refresh needed.
    fun toggleVideoFavourite(video: VideoFile) {
        scope.launch(Dispatchers.IO) {
            persistenceRepository.toggleFavourite(
                FavouriteItem(
                    mediaId = video.id,
                    title = video.title,
                    subtitle = video.folderName,
                    mediaUri = video.path,
                    isVideo = true,
                    durationMs = video.durationMs,
                ),
            )
        }
    }

    fun toggleSelectionFavourite() {
        val direct = selectedVideosInView()
        val folders = selectedFoldersInView()
        val favUris = favourites.map { it.mediaUri }.toSet()
        scope.launch(Dispatchers.IO) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by path
            // (favourites are keyed by path, so a video that is both
            // directly selected and inside a selected folder must
            // resolve once).
            val resolved = (direct + videosInFolders(folders))
                .distinctBy { it.path }
            if (resolved.isEmpty()) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.firstOrNull()?.name ?: "",
                        )
                    )
                }
                return@launch
            }
            // Toggle as a group: if every selected video is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = resolved.all { favUris.contains(it.path) }
            if (allFav) {
                resolved.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                resolved.filter { !favUris.contains(it.path) }.forEach { video ->
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
                }
            }
            withContext(Dispatchers.Main) {
                clearSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, isFolderLoading) {
        if (!isLoading && !isFolderLoading) isRefreshing = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                // #144: selection top bar — count + close. Back exits too.
                TopAppBar(
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MpxTheme.colors.black,
                    ),
                )
            } else if (showSearchPage) {
                // #142: the search page draws its own header (back + field).
            } else if (currentFolder != null) {
                // Folder View Top Bar
                val folder = currentFolder!!
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            // Issue #276: go through goToRoot() (not the
                            // inline clear) so leaving the folder also
                            // clears the multi-select.
                            goToRoot()
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
                                onValueChange = { folderSearchQuery = it },
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxTheme.colors.black),
                    actions = {
                        if (isFolderSearching) {
                            IconButton(onClick = { isFolderSearching = false; folderSearchQuery = "" }) {
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

                        IconButton(onClick = { isFolderSearching = true }) {
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
                                playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = 0)
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
                TopAppBar(
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MpxTheme.colors.black,
                    ),
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
                        // #169: quick-play opens a RANDOM video from the
                        // current scope, not the first one. The queue stays
                        // the full list, starting at the random video.
                        val played = if (currentFolder != null) {
                            if (filteredFolderVideos.isNotEmpty()) {
                                playbackManager?.playVideoFiles(
                                    filteredFolderVideos,
                                    startIndex = filteredFolderVideos.indices.random(),
                                )
                                onOpenPlayer()
                                true
                            } else false
                        } else if (appSettings.fileView) {
                            val random = filteredVideos.randomOrNull()
                            if (random != null) {
                                playVideoWithFolder(random)
                                true
                            } else false
                        } else {
                            val random = filteredFolders.randomOrNull()
                            if (random != null) {
                                playFolder(random, randomStart = true)
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
                    // #165: search results are Local's own tiles, so they
                    // share the #144 multi-select state and toggles.
                    selectedFolderIds = selectedFolderIds,
                    selectedVideoIds = selectedVideoIds,
                    isSelectionMode = isSelectionMode,
                    onToggleFolderSelection = { toggleFolderSelection(it) },
                    onToggleVideoSelection = { toggleVideoSelection(it) },
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
                        onActionClick = { folderSearchQuery = "" },
                    )
                } else if (filteredFolderVideos.isEmpty()) {
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
                    if (viewMode == ViewMode.LIST) {
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
                                                playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = index)
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
                                columns = GridCells.Fixed(appSettings.videoGridColumns.coerceIn(1, 4)),
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
                                        // Issue #235: the folder-view grid
                                        // passed neither isFavourite nor a
                                        // handler — the heart was always
                                        // outline and dead.
                                        isFavourite = favourites.any { it.mediaUri == video.path },
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
                                                playbackManager?.playVideoFiles(filteredFolderVideos, startIndex = index)
                                                onOpenPlayer()
                                            }
                                        },
                                    )
                                }
                            }
                            GridFastScrollbar(
                                gridState = videoGridState,
                                itemCount = filteredFolderVideos.size,
                                // Issue #236: the grid's real column count —
                                // the scrollbar's row math needs it.
                                columns = appSettings.videoGridColumns.coerceIn(1, 4),
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
                                    VideoItemRow(
                                        video = video,
                                        showThumbnails = appSettings.showThumbnails,
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
                                    val isFav = favourites.any { it.mediaUri == video.path }
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
                            GridFastScrollbar(
                                gridState = gridState,
                                itemCount = filteredVideos.size,
                                // Issue #236: the grid's real column count.
                                columns = appSettings.videoGridColumns.coerceIn(1, 4),
                                getSectionText = { index ->
                                    filteredVideos.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
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
                    if (viewMode == ViewMode.LIST && isTreeMode) {
                        LocalFolderTree(
                            folders = filteredFolders,
                            expandedKeys = expandedTreeFolders,
                            onToggleExpand = { folder ->
                                val key = folder.id.ifBlank { folder.path }
                                expandedTreeFolders =
                                    if (expandedTreeFolders.contains(key)) expandedTreeFolders - key
                                    else expandedTreeFolders + key
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
                            GridFastScrollbar(
                                gridState = gridState,
                                itemCount = filteredFolders.size,
                                // Issue #236: the grid's real column count.
                                columns = appSettings.folderGridColumns.coerceIn(1, 4),
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
                clearSelection()
                val updated = appSettings.copy(fileView = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            onSortChange = { newSort ->
                sortField = newSort
                val updated = appSettings.copy(primarySortField = newSort.name)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            onSecondarySortChange = { newSecSort ->
                secondarySortField = newSecSort
                val updated = appSettings.copy(secondarySortField = newSecSort.name)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            onViewChange = {
                updateViewMode(it)
            },
            onSeparateViewLayoutChange = { isChecked ->
                val updated = appSettings.copy(separateViewLayout = isChecked)
                realSettingsRepo.save(updated, commit = true)
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
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                val updated = appSettings.copy(showFieldDate = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                val updated = appSettings.copy(showFieldDuration = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                val updated = appSettings.copy(showFieldLocation = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                val updated = appSettings.copy(sortReverse = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                val updated = appSettings.copy(sortSecondaryReverse = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                val updated = appSettings.copy(sortNumeric = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                val updated = appSettings.copy(sortCaseSensitive = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                val updated = appSettings.copy(sortIgnoreExtension = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
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
                folderSortField = newSort
                val updated = appSettings.copy(folderPrimarySortField = newSort.name)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            currentSecondarySort = folderSecondarySortField,
            onSecondarySortChange = { newSecSort ->
                folderSecondarySortField = newSecSort
                val updated = appSettings.copy(folderSecondarySortField = newSecSort.name)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldSize = appSettings.showFieldSize,
            onShowFieldSizeChange = { isChecked ->
                val updated = appSettings.copy(showFieldSize = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldDate = appSettings.showFieldDate,
            onShowFieldDateChange = { isChecked ->
                val updated = appSettings.copy(showFieldDate = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldDuration = appSettings.showFieldDuration,
            onShowFieldDurationChange = { isChecked ->
                val updated = appSettings.copy(showFieldDuration = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            showFieldLocation = appSettings.showFieldLocation,
            onShowFieldLocationChange = { isChecked ->
                val updated = appSettings.copy(showFieldLocation = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { isChecked ->
                val updated = appSettings.copy(sortReverse = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortSecondaryReverse = appSettings.sortSecondaryReverse,
            onSortSecondaryReverseChange = { isChecked ->
                val updated = appSettings.copy(sortSecondaryReverse = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortNumeric = appSettings.sortNumeric,
            onSortNumericChange = { isChecked ->
                val updated = appSettings.copy(sortNumeric = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortCaseSensitive = appSettings.sortCaseSensitive,
            onSortCaseSensitiveChange = { isChecked ->
                val updated = appSettings.copy(sortCaseSensitive = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
            },
            sortIgnoreExtension = appSettings.sortIgnoreExtension,
            onSortIgnoreExtensionChange = { isChecked ->
                val updated = appSettings.copy(sortIgnoreExtension = isChecked)
                realSettingsRepo.save(updated, commit = true)
                appSettings = updated
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
                    folderSortField = newSort
                    val updated = appSettings.copy(folderPrimarySortField = newSort.name)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    val updated = appSettings.copy(sortReverse = reversed)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
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
                folderGridColumns = appSettings.folderGridColumns,
                onFolderGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.folderGridColumns) {
                        val updated = appSettings.copy(folderGridColumns = columns)
                        realSettingsRepo.save(updated, commit = true)
                        appSettings = updated
                    }
                },
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.videoGridColumns) {
                        val updated = appSettings.copy(videoGridColumns = columns)
                        realSettingsRepo.save(updated, commit = true)
                        appSettings = updated
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    val updated = appSettings.copy(showFullName = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    val updated = appSettings.copy(showFieldLocation = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    val updated = appSettings.copy(showFieldCount = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    val updated = appSettings.copy(showFieldDuration = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    val updated = appSettings.copy(showFieldSize = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    val updated = appSettings.copy(showFieldDate = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
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
                    sortField = newSort
                    val updated = appSettings.copy(primarySortField = newSort.name)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                sortReverse = appSettings.sortReverse,
                onSortReverseChange = { reversed ->
                    val updated = appSettings.copy(sortReverse = reversed)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
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
                        val updated = appSettings.copy(folderGridColumns = columns)
                        realSettingsRepo.save(updated, commit = true)
                        appSettings = updated
                    }
                },
                videoGridColumns = appSettings.videoGridColumns,
                onVideoGridColumnsChange = { columns ->
                    // Issue #191: the slider fires on every drag tick, and
                    // a commit() is a synchronous disk write — only write
                    // when the value actually changed.
                    if (columns != appSettings.videoGridColumns) {
                        val updated = appSettings.copy(videoGridColumns = columns)
                        realSettingsRepo.save(updated, commit = true)
                        appSettings = updated
                    }
                },
                showFullName = appSettings.showFullName,
                onShowFullNameChange = { show ->
                    val updated = appSettings.copy(showFullName = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldLocation = appSettings.showFieldLocation,
                onShowFieldLocationChange = { show ->
                    val updated = appSettings.copy(showFieldLocation = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldCount = appSettings.showFieldCount,
                onShowFieldCountChange = { show ->
                    val updated = appSettings.copy(showFieldCount = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldDuration = appSettings.showFieldDuration,
                onShowFieldDurationChange = { show ->
                    val updated = appSettings.copy(showFieldDuration = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldSize = appSettings.showFieldSize,
                onShowFieldSizeChange = { show ->
                    val updated = appSettings.copy(showFieldSize = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
                },
                showFieldDate = appSettings.showFieldDate,
                onShowFieldDateChange = { show ->
                    val updated = appSettings.copy(showFieldDate = show)
                    realSettingsRepo.save(updated, commit = true)
                    appSettings = updated
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
                showAddToPlaylistDialog = false
                videosForPlaylistAdd = emptyList()
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    // Issue #230: addItemToPlaylist returns -1 for an item
                    // that is already in the playlist — count the real
                    // adds so the feedback tells the truth.
                    var addedCount = 0
                    vids.forEach { vid ->
                        val rowId = persistenceRepository.addItemToPlaylist(
                            PlaylistItem(
                                playlistId = pl.id,
                                mediaUri = vid.path,
                                mediaId = vid.id,
                                title = vid.title,
                                subtitle = vid.folderName,
                                isVideo = true,
                                durationMs = vid.durationMs,
                            )
                        )
                        if (rowId != -1L) addedCount++
                    }
                    showAddToPlaylistDialog = false
                    videosForPlaylistAdd = emptyList()
                    clearSelection()
                    when {
                        addedCount == vids.size -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_added_to_playlist_msg, pl.name)
                        )
                        addedCount == 0 -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_already_in_playlist_msg, pl.name)
                        )
                        else -> snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.music_added_some_to_playlist_msg,
                                addedCount, pl.name, vids.size - addedCount,
                            )
                        )
                    }
                }
            },
            onCreateNewPlaylist = {
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            // #268: Local creates video playlists; the fixed type is
            // passed through (no selector on this screen).
            initialIsVideoPlaylist = true,
            onConfirm = { name, isVideo ->
                scope.launch {
                    persistenceRepository.createPlaylist(name, isVideoPlaylist = isVideo)
                    showCreatePlaylistDialog = false
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_playlist_created, name)
                    )
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
