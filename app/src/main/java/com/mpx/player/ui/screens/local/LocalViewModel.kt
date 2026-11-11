package com.mpx.player.ui.screens.local

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SavedStateHandle keys for the search states (see the Search section).
// Namespaced (`local.*`) so they cannot collide with navigation
// arguments carried in the same handle.
private const val KEY_SEARCH_QUERY = "local.searchQuery"
private const val KEY_FOLDER_SEARCH_QUERY = "local.folderSearchQuery"
private const val KEY_FOLDER_SEARCHING = "local.isFolderSearching"

/**
 * Migration Phase 2b: the Local tab's state, moved out of
 * [LocalScreen]'s Compose `remember` blocks so it has one owner
 * and can be tested with fakes.
 *
 * Owns the testable, non-UI state the screen held before: settings
 * load / reload-on-resume and every settings write the sort, view
 * and field dialogs make; media and folder loading (with the
 * #320 queued-refresh rule and the #277 stale-load guard); sort
 * and view state and the sorted / filtered lists derived from them;
 * folder and whole-page search state; multi-select state and the
 * selection resolvers; favourites / history / playlist observation
 * and the favourite / playlist data operations; and the pending
 * add-to-playlist dialog state.
 *
 * Compose-only concerns stay in the screen: layout, gestures,
 * scroll states, dialog visibility for the sort / view dialogs,
 * the permission launcher and lifecycle observer, snackbars, and
 * playback calls through `PlaybackCoordinator`.
 *
 * Deliberately NOT moved here: the open folder itself
 * (`currentFolder`) and the search-page flag (`showSearchPage`).
 * Both are `rememberSaveable` in the screen — the folder round-trips
 * through the `MediaFolderSaver` so a process death restores the
 * drilled-in folder, and the search page restores with it. The screen
 * passes the folder / page flag into the functions below that need
 * them ([selectedVideosInView], [toggleSelectionFavourite],
 * [resolveSelectionVideos]).
 *
 * The search states below (whole-page query, in-folder query and
 * in-folder open flag) DO live here, but backed by the
 * [SavedStateHandle] — not plain `MutableStateFlow`s. The old code
 * kept them in `rememberSaveable` (#318), so they survived rotation,
 * dock-tab switches (this destination is popped with saveState, which
 * clears this ViewModel) and process death; plain flows would lose
 * them on the last two. Koin injects the handle from the
 * NavBackStackEntry's CreationExtras (see ViewModelModule).
 *
 * Dependencies arrive through the constructor, on interfaces only
 * ([MediaRepository], [MediaPersistenceRepository],
 * [AppSettingsRepository]), so a test can pass fakes. No behaviour
 * change: every value below is computed exactly the way the screen
 * computed it before.
 */
class LocalViewModel(
    private val media: MediaRepository,
    private val persistence: MediaPersistenceRepository,
    private val settingsRepository: AppSettingsRepository,
    hasPermission: Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // -- Settings ---------------------------------------------------------
    //
    // The screen used to hold `appSettings` in a `remember` and reload
    // it on resume; the sort / view fields were one-time remembers
    // keyed on their settings values (#331). They are all derived from
    // this one flow now, so a reload re-derives them together.

    private val _settings = MutableStateFlow(settingsRepository.load())
    /** Current settings; reloaded on resume via [reloadSettings]. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Reloads settings (ON_RESUME); derived sort / view fields follow. */
    fun reloadSettings() {
        _settings.value = settingsRepository.load()
    }

    /**
     * Persists [transform] of the current settings and publishes it,
     * replacing the old `save(updated)` + `appSettings = updated`
     * pair. `commit` keeps the #191 rule: layout saves commit
     * synchronously, the rest use the queued write.
     */
    fun updateSettings(commit: Boolean = false, transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        settingsRepository.save(updated, commit)
        _settings.value = updated
    }

    /** Root sort field, derived from settings (invalid value -> DATE). */
    val sortField: StateFlow<SortField> = _settings.map {
        parseSortField(it.primarySortField, SortField.DATE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, parseSortField(_settings.value.primarySortField, SortField.DATE))

    /** Root secondary sort field, derived from settings (invalid -> NAME). */
    val secondarySortField: StateFlow<SortField> = _settings.map {
        parseSortField(it.secondarySortField, SortField.NAME)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, parseSortField(_settings.value.secondarySortField, SortField.NAME))

    /** Folder-contents sort field, derived from settings (invalid -> DATE). */
    val folderSortField: StateFlow<SortField> = _settings.map {
        parseSortField(it.folderPrimarySortField, SortField.DATE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, parseSortField(_settings.value.folderPrimarySortField, SortField.DATE))

    /** Folder-contents secondary sort field, derived from settings (invalid -> NAME). */
    val folderSecondarySortField: StateFlow<SortField> = _settings.map {
        parseSortField(it.folderSecondarySortField, SortField.NAME)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, parseSortField(_settings.value.folderSecondarySortField, SortField.NAME))

    /** Shared Home + folder-contents view mode (#186), derived from settings. */
    val viewMode: StateFlow<ViewMode> = _settings.map {
        parseViewMode(it.localViewMode)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, parseViewMode(_settings.value.localViewMode))

    /** #164: Tree view for the root folders list, derived from settings. */
    val isTreeMode: StateFlow<Boolean> = _settings.map {
        it.localTreeMode
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _settings.value.localTreeMode)

    /** Persists a new shared view mode (#186: one layout for Home and
     * all folders; `separateViewLayout = false` mirrors it to Music). */
    fun updateViewMode(newMode: ViewMode) {
        updateSettings(commit = true) { current ->
            if (current.separateViewLayout) {
                current.copy(localViewMode = newMode.name)
            } else {
                current.copy(localViewMode = newMode.name, musicViewMode = newMode.name)
            }
        }
    }

    /** Persists the #164 tree-mode flag. */
    fun updateTreeMode(tree: Boolean) {
        updateSettings(commit = true) { it.copy(localTreeMode = tree) }
    }

    /** Persists the root primary sort field. */
    fun setRootSortField(field: SortField) {
        updateSettings(commit = true) { it.copy(primarySortField = field.name) }
    }

    /** Persists the root secondary sort field. */
    fun setRootSecondarySortField(field: SortField) {
        updateSettings(commit = true) { it.copy(secondarySortField = field.name) }
    }

    /** Persists the folder-contents primary sort field. */
    fun setFolderSortField(field: SortField) {
        updateSettings(commit = true) { it.copy(folderPrimarySortField = field.name) }
    }

    /** Persists the folder-contents secondary sort field. */
    fun setFolderSecondarySortField(field: SortField) {
        updateSettings(commit = true) { it.copy(folderSecondarySortField = field.name) }
    }

    /**
     * File-view toggle (#276: switching views clears the selection —
     * folder-mode and file-view selections do not resolve in each
     * other, so a stale badge would resolve to nothing).
     */
    fun setFileView(fileView: Boolean) {
        clearSelection()
        updateSettings(commit = true) { it.copy(fileView = fileView) }
    }

    // -- Media loading ------------------------------------------------------
    //
    // The lists start from the repository's process-lifetime cache
    // (#100) exactly as the screen's `remember` initializers did, so
    // returning to Local renders the previous content instantly
    // instead of flashing an empty page while the rescan runs. First
    // launch (no cache) still starts in the loading state.

    private val _folders: MutableStateFlow<List<MediaFolder>>
    private val _allVideos: MutableStateFlow<List<VideoFile>>
    private val _isLoading: MutableStateFlow<Boolean>

    /** Scanned root folders (blacklist-filtered). */
    val folders: StateFlow<List<MediaFolder>>
        get() = _folders.asStateFlow()

    /** All scanned videos, ungrouped (blacklist-filtered). */
    val allVideos: StateFlow<List<VideoFile>>
        get() = _allVideos.asStateFlow()

    /** True while a root scan is in flight. */
    val isLoading: StateFlow<Boolean>
        get() = _isLoading.asStateFlow()

    private val _folderVideos = MutableStateFlow<List<VideoFile>>(emptyList())
    /** Videos of the open folder. */
    val folderVideos: StateFlow<List<VideoFile>> = _folderVideos.asStateFlow()

    private val _isFolderLoading = MutableStateFlow(false)
    /** True while a folder-videos scan is in flight. */
    val isFolderLoading: StateFlow<Boolean> = _isFolderLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(hasPermission)
    /** Whether the video permission is currently granted. The screen
     * owns the platform check (it needs a Context) and reports it
     * here via [setHasPermission] on the permission callback and on
     * resume; the initial value arrives as a Koin parameter. */
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** The screen reports the live permission state here. */
    fun setHasPermission(granted: Boolean) {
        _hasPermission.value = granted
    }

    init {
        val initialSettings = _settings.value
        val cachedFolders = media.cachedFolders(
            initialSettings.showAllFolders,
            initialSettings.showHiddenFiles,
        )
        val cachedVideos = media.cachedVideos(
            initialSettings.showAllFolders,
            initialSettings.showHiddenFiles,
        )
        _folders = MutableStateFlow(cachedFolders ?: emptyList())
        _allVideos = MutableStateFlow(cachedVideos ?: emptyList())
        _isLoading = MutableStateFlow(cachedFolders == null)
    }

    // Issue #319: the in-flight root scan — cancelled on permission
    // loss so a stale completion cannot repopulate the cleared lists
    // afterwards, same Job-handle pattern as folderVideosJob (#277).
    private var foldersLoadJob: Job? = null

    // #277: the in-flight folder-videos load — cancelled before a new
    // one starts, so a stale scan can never overwrite the current
    // folder's videos.
    private var folderVideosJob: Job? = null

    // #277: the folder the in-flight folder-videos load belongs to.
    // The completion only applies when no newer load has started —
    // tapping folder A then B quickly can no longer show A's videos
    // under B's header, whichever scan finishes last. (The screen
    // used to guard on its `currentFolder?.id`; this id is the same
    // guard without the ViewModel owning the folder object.)
    private var loadingFolderId: String? = null

    // Issue #320: a refresh requested while a scan is in flight is
    // queued instead of dropped. Repeated requests coalesce into this
    // single pending refresh (latest showAll wins); it runs when the
    // in-flight scan finishes (see [drainPendingRefresh]). Null means
    // no refresh is queued.
    private var pendingRefreshShowAll: Boolean? = null

    /**
     * The user-initiated root refresh (pull-to-refresh, the Sort
     * dialog's show-all toggle, the empty states). Issue #320: a
     * refresh requested while a scan is in flight is queued instead
     * of dropped — the spinner stays up until the queued refresh
     * actually runs.
     */
    fun refreshFolders(showAll: Boolean = settings.value.showAllFolders) {
        // Queue only when a scan is genuinely in flight. `isLoading`
        // is NOT the right test: with a cold cache it is true from
        // construction, before any scan has started — queueing on it
        // would stall the refresh forever, since nothing is in flight
        // to finish and drain it (the first scan is kicked by
        // [reloadFolders] on ON_RESUME, not by a refresh).
        if (foldersLoadJob?.isActive == true) {
            pendingRefreshShowAll = showAll
            return
        }
        startFoldersScan(showAll)
    }

    /**
     * The permission-grant / ON_RESUME refresh: cancels any in-flight
     * scan and starts a fresh one now, instead of queueing behind it
     * like [refreshFolders] — the screen used to cancel
     * `foldersLoadJob` and launch directly at both call sites.
     */
    fun reloadFolders(showAll: Boolean = settings.value.showAllFolders) {
        foldersLoadJob?.cancel()
        startFoldersScan(showAll)
    }

    private fun startFoldersScan(showAll: Boolean) {
        if (!hasPermission.value) {
            _hasPermission.value = false
            clearMediaStateOnPermissionLoss()
            return
        }
        // Issue #295: isLoading is state — write it on Main, before
        // the IO work, not inside the IO block.
        _hasPermission.value = true
        _isLoading.value = true
        foldersLoadJob?.cancel()
        // The settings snapshot is read on Main at call time, as the
        // screen's IO block used to read its captured settings.
        val settingsSnapshot = settings.value
        foldersLoadJob = viewModelScope.launch {
            val scanned = withContext(ioDispatcher) {
                media.getMediaFolders(showAll)
            }
            val scannedVideos = withContext(ioDispatcher) {
                media.getAllVideos(showAll)
            }
            val (visibleFolders, visibleVideos) = applyFolderBlacklist(
                scanned,
                scannedVideos,
                settingsSnapshot.blacklistedFolders,
            )
            _folders.value = visibleFolders
            _allVideos.value = visibleVideos
            _isLoading.value = false
            drainPendingRefresh()
        }
    }

    // Issue #320: drain a queued refresh when the in-flight root scan
    // finishes. The pending value is cleared BEFORE starting the
    // refresh, so the isLoading=true the refresh sets cannot
    // re-trigger this into a loop; a refresh queued during the
    // drained scan is a new pending value and drains on the next
    // finish.
    private fun drainPendingRefresh() {
        pendingRefreshShowAll?.let { showAll ->
            pendingRefreshShowAll = null
            refreshFolders(showAll)
        }
    }

    /**
     * #277: the single entry point for every folder-videos scan
     * (openFolder, refreshFolderVideos — hence the ON_RESUME refresh
     * and pull-to-refresh too). One in-flight load at a time: the
     * previous load is cancelled before a new one starts, and the
     * result is only applied if this folder is still the latest load
     * when the scan completes. isFolderLoading is written here on
     * Main (all callers run on Main).
     */
    fun loadFolderVideos(folderId: String) {
        folderVideosJob?.cancel()
        loadingFolderId = folderId
        _isFolderLoading.value = true
        folderVideosJob = viewModelScope.launch {
            val vids = withContext(ioDispatcher) {
                media.getVideosInFolder(folderId)
            }
            if (loadingFolderId == folderId) {
                _folderVideos.value = vids
            }
            _isFolderLoading.value = false
        }
    }

    /** Same folder, no synchronous clear — pull-to-refresh / ON_RESUME
     * keeps showing the current videos while reloading. */
    fun refreshFolderVideos(folderId: String) {
        loadFolderVideos(folderId)
    }

    /**
     * Opens a folder: a new folder is a new view, so the selection
     * is cleared (#276) and the folder search is reset, and the
     * previous folder's videos are cleared at once so they are never
     * shown under this folder's header while the scan runs (#277).
     * The screen sets its own `currentFolder` (it owns the saveable
     * folder object) and then calls this.
     */
    fun openFolder(folder: MediaFolder) {
        clearSelection()
        resetFolderSearch()
        // #277: clear at once, so the previous folder's videos are
        // never shown under this folder's header while the scan runs
        // (same as the #269 playlist-dialog clear).
        _folderVideos.value = emptyList()
        loadFolderVideos(folder.id)
    }

    /**
     * The ViewModel-owned half of going back to root: the folder
     * search state is cleared and the selection is dropped (#276).
     * The screen nulls its own `currentFolder` alongside this.
     */
    fun clearFolderViewState() {
        resetFolderSearch()
        clearSelection()
    }

    // Issue #319: permission loss must clear ALL media state, not
    // just folders — the old code left allVideos (file view +
    // search) and the open folder's folderVideos stale, so the open
    // folder view kept showing — and playing — videos after
    // permission loss. The folder is closed by the screen (it owns
    // `currentFolder`); the pending playlist-add items are dropped,
    // the add/create dialogs are closed, and both in-flight scans
    // are cancelled first so a stale completion cannot repopulate
    // the cleared lists afterwards. A refresh queued while the
    // cleared scan was in flight still drains — exactly as the
    // screen's old `LaunchedEffect(isLoading)` drain did on the
    // true -> false transition.
    fun clearMediaStateOnPermissionLoss() {
        val wasLoading = _isLoading.value
        foldersLoadJob?.cancel()
        folderVideosJob?.cancel()
        _folders.value = emptyList()
        _allVideos.value = emptyList()
        _folderVideos.value = emptyList()
        _isLoading.value = false
        _isFolderLoading.value = false
        clearSelection()
        _videosForPlaylistAdd.value = emptyList()
        _showAddToPlaylistDialog.value = false
        _showCreatePlaylistDialog.value = false
        resetFolderSearch()
        if (wasLoading) drainPendingRefresh()
    }

    // -- Search state ---------------------------------------------------------
    //
    // The three search states live in the SavedStateHandle, not in plain
    // MutableStateFlows: the old code kept them in `rememberSaveable`
    // (#318), so they survived rotation, dock-tab switches (this
    // destination is popped with saveState, which clears this ViewModel)
    // and process death. `getStateFlow` returns a flow that reads the
    // handle; the setters and [resetFolderSearch] write the handle
    // directly, so a later restore never resurrects a stale query.

    private val _searchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    /** Whole-page search query (#142). The screen passes it down to
     * LocalSearchScreen as a plain parameter. */
    val searchQuery: StateFlow<String> = _searchQuery

    /** The whole-page search field's text. */
    fun setSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    private val _folderSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_FOLDER_SEARCH_QUERY, "")
    /** In-folder search query (#318). */
    val folderSearchQuery: StateFlow<String> = _folderSearchQuery

    /** The in-folder search field's text. */
    fun setFolderSearchQuery(query: String) {
        savedStateHandle[KEY_FOLDER_SEARCH_QUERY] = query
    }

    private val _isFolderSearching: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_FOLDER_SEARCHING, false)
    /** Whether the in-folder search field is open. */
    val isFolderSearching: StateFlow<Boolean> = _isFolderSearching

    /** Opens / closes the in-folder search field. */
    fun setFolderSearching(searching: Boolean) {
        savedStateHandle[KEY_FOLDER_SEARCHING] = searching
    }

    /** Resets the in-folder search state (openFolder / back-to-root /
     * permission loss). Writes the handle, not a local flow, so a
     * later restore does not resurrect the cleared query. */
    private fun resetFolderSearch() {
        savedStateHandle[KEY_FOLDER_SEARCHING] = false
        savedStateHandle[KEY_FOLDER_SEARCH_QUERY] = ""
    }

    // -- Derived sorted / filtered lists ------------------------------------
    //
    // The screen used to compute these with `remember` on the raw
    // lists, the sort fields and settings; the flows below recompute
    // on exactly the same inputs.

    /** Root folders, sorted by the root sort fields. */
    val sortedFolders: StateFlow<List<MediaFolder>> = combine(_folders, _settings) { folders, s ->
        folders.sortedWith(
            MediaSortComparator.folderComparator(
                primary = parseSortField(s.primarySortField, SortField.DATE),
                secondary = parseSortField(s.secondarySortField, SortField.NAME),
                reverse = s.sortReverse,
                secondaryReverse = s.sortSecondaryReverse,
                numeric = s.sortNumeric,
                caseSensitive = s.sortCaseSensitive,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** File view: all videos ungrouped, sorted by the root sort fields. */
    val sortedVideos: StateFlow<List<VideoFile>> = combine(_allVideos, _settings) { videos, s ->
        videos.sortedWith(
            MediaSortComparator.videoComparator(
                primary = parseSortField(s.primarySortField, SortField.DATE),
                secondary = parseSortField(s.secondarySortField, SortField.NAME),
                reverse = s.sortReverse,
                secondaryReverse = s.sortSecondaryReverse,
                numeric = s.sortNumeric,
                caseSensitive = s.sortCaseSensitive,
                ignoreExtension = s.sortIgnoreExtension,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Folder-contents videos, sorted by the folder sort fields. */
    val sortedFolderVideos: StateFlow<List<VideoFile>> = combine(_folderVideos, _settings) { videos, s ->
        videos.sortedWith(
            MediaSortComparator.videoComparator(
                primary = parseSortField(s.folderPrimarySortField, SortField.DATE),
                secondary = parseSortField(s.folderSecondarySortField, SortField.NAME),
                reverse = s.sortReverse,
                secondaryReverse = s.sortSecondaryReverse,
                numeric = s.sortNumeric,
                caseSensitive = s.sortCaseSensitive,
                ignoreExtension = s.sortIgnoreExtension,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Folder-contents videos, filtered by the in-folder search query. */
    val filteredFolderVideos: StateFlow<List<VideoFile>> =
        combine(sortedFolderVideos, _folderSearchQuery) { videos, query ->
            val q = query.trim()
            if (q.isBlank()) videos
            else videos.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.path.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -- Tree view ------------------------------------------------------------

    private val _expandedTreeFolders = MutableStateFlow(setOf<String>())
    /** #164: expanded folders in tree view — session-only, not persisted. */
    val expandedTreeFolders: StateFlow<Set<String>> = _expandedTreeFolders.asStateFlow()

    /** Toggles one folder's expanded state in tree view. */
    fun toggleTreeFolder(folder: MediaFolder) {
        val key = folder.id.ifBlank { folder.path }
        _expandedTreeFolders.value =
            if (_expandedTreeFolders.value.contains(key)) _expandedTreeFolders.value - key
            else _expandedTreeFolders.value + key
    }

    /** #375: the parent of a folder in the scanned list (MX/mpvEx
     * drill-in), or null when it is already a root. */
    fun findParentFolder(folderPath: String): MediaFolder? =
        parentFolderOf(sortedFolders.value, folderPath)

    // -- Multi-select (#144) --------------------------------------------------
    //
    // Long-press multi-select. One selection per view: folders on the
    // root folder list, videos in file view and inside a folder.
    // Long-press toggles an item (entering selection mode when empty);
    // a tap while selecting toggles too. Back / the X exits and clears.

    private val _selectedFolderIds = MutableStateFlow(setOf<String>())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow(setOf<String>())
    val selectedVideoIds: StateFlow<Set<String>> = _selectedVideoIds.asStateFlow()

    /** True while anything is selected. */
    val isSelectionMode: StateFlow<Boolean> =
        combine(_selectedFolderIds, _selectedVideoIds) { folders, videos ->
            folders.isNotEmpty() || videos.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Total selected items, for the selection top bar. */
    val selectionCount: StateFlow<Int> =
        combine(_selectedFolderIds, _selectedVideoIds) { folders, videos ->
            folders.size + videos.size
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun clearSelection() {
        _selectedFolderIds.value = emptySet()
        _selectedVideoIds.value = emptySet()
    }

    fun toggleFolderSelection(folder: MediaFolder) {
        val key = folder.id.ifBlank { folder.path }
        _selectedFolderIds.value =
            if (_selectedFolderIds.value.contains(key)) _selectedFolderIds.value - key
            else _selectedFolderIds.value + key
    }

    fun toggleVideoSelection(video: VideoFile) {
        val key = video.id.ifBlank { video.path }
        _selectedVideoIds.value =
            if (_selectedVideoIds.value.contains(key)) _selectedVideoIds.value - key
            else _selectedVideoIds.value + key
    }

    // #144: multi-select actions resolve the current selection to
    // videos: directly selected videos PLUS — for selected folders —
    // the videos inside them (#233: both halves are combined, never
    // videos-first with a folders-only fallback).
    //
    // Issue #234: the resolvers filter the UNFILTERED sorted lists
    // (sortedFolderVideos / sortedVideos / sortedFolders) — never
    // the search-filtered ones. The search box helps find items; it
    // must not narrow an existing selection.
    fun selectedVideosInView(currentFolder: MediaFolder?, inSearchPage: Boolean): List<VideoFile> {
        return when {
            // #165: on the search page the selection resolves against
            // the full video list — matches are a subset of it, and the
            // folder/file views below cannot see them.
            inSearchPage -> allVideos.value.filter {
                selectedVideoIds.value.contains(it.id.ifBlank { it.path })
            }
            currentFolder != null -> sortedFolderVideos.value.filter {
                selectedVideoIds.value.contains(it.id.ifBlank { it.path })
            }
            settings.value.fileView -> sortedVideos.value.filter {
                selectedVideoIds.value.contains(it.id.ifBlank { it.path })
            }
            else -> emptyList()
        }
    }

    fun selectedFoldersInView(currentFolder: MediaFolder?, inSearchPage: Boolean): List<MediaFolder> {
        return when {
            // #165: same as videos — search matches live in the full
            // folder list.
            inSearchPage -> folders.value.filter {
                selectedFolderIds.value.contains(it.id.ifBlank { it.path })
            }
            currentFolder == null && !settings.value.fileView -> {
                sortedFolders.value.filter {
                    selectedFolderIds.value.contains(it.id.ifBlank { it.path })
                }
            }
            else -> emptyList()
        }
    }

    /** Videos inside the given folders — the folder half of a
     * multi-select. Call only from an IO coroutine: it scans
     * MediaStore buckets. */
    suspend fun videosInFolders(folders: List<MediaFolder>): List<VideoFile> =
        withContext(ioDispatcher) {
            folders.flatMap { media.getVideosInFolder(it.id) }
        }

    /** Videos inside one folder, by bucket id. */
    suspend fun videosInFolder(folderId: String): List<VideoFile> =
        withContext(ioDispatcher) { media.getVideosInFolder(folderId) }

    /**
     * The resolved multi-select queue: directly selected videos
     * first, then the videos inside selected folders, deduped by id
     * so a video that is both directly selected and inside a
     * selected folder plays once (#233).
     */
    suspend fun resolveSelectionVideos(
        currentFolder: MediaFolder?,
        inSearchPage: Boolean,
    ): List<VideoFile> =
        selectionVideosCombined(currentFolder, inSearchPage)
            .distinctBy { it.id.ifBlank { it.path } }

    // The #233 combine without dedup; the callers dedup the way
    // their identity needs (id-or-path for play / add, path for
    // favourites, which are keyed by path).
    private suspend fun selectionVideosCombined(
        currentFolder: MediaFolder?,
        inSearchPage: Boolean,
    ): List<VideoFile> {
        val direct = selectedVideosInView(currentFolder, inSearchPage)
        val folders = selectedFoldersInView(currentFolder, inSearchPage)
        return direct + videosInFolders(folders)
    }

    // -- Favourites / history / playlists -------------------------------------

    /** Observed favourites, for the rows' hearts. */
    val favourites: StateFlow<List<FavouriteItem>> =
        persistence.observeFavourites()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Issue #360: the favourite URIs as a Set, computed once per
    // favourites change — the rows used to scan the whole favourites
    // list per row on every composition while scrolling.
    val favouriteUris: StateFlow<Set<String>> = favourites.map { list ->
        list.map { it.mediaUri }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Observed playback history, newest first — powers the #378
     * quick-play FAB (resume the last played video). */
    val history: StateFlow<List<PlaybackHistoryItem>> =
        persistence.observeHistory()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Observed custom playlists, for the add-to-playlist dialog. */
    val customPlaylists: StateFlow<List<CustomPlaylist>> =
        persistence.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Issue #378: the last played video in a list. History is newest
    // first, so the first entry that matches a video in the list is
    // the most recent one. Matches on the MediaStore id first, the
    // path as a fallback — both are recorded by PlaybackCoordinator.
    fun lastPlayedIn(videos: List<VideoFile>): VideoFile? {
        for (entry in history.value) {
            if (!entry.isVideo) continue
            val match = videos.firstOrNull {
                it.id == entry.mediaId || it.path == entry.mediaUri
            }
            if (match != null) return match
        }
        return null
    }

    // Issue #235: single-video favourite toggle for the grid card
    // heart. Uses the same repository toggle the player uses, so the
    // heart flips through the observed favourites Flow — no manual
    // refresh needed.
    fun toggleVideoFavourite(video: VideoFile) {
        viewModelScope.launch(ioDispatcher) {
            persistence.toggleFavourite(
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

    /**
     * The multi-select favourite toggle. Toggles as a group: if every
     * selected video is already a favourite, removes them all;
     * otherwise adds the missing ones. Returns what happened so the
     * screen can show the right snackbar; the selection is cleared
     * on success, left alone when there was nothing to toggle.
     */
    suspend fun toggleSelectionFavourite(
        currentFolder: MediaFolder?,
        inSearchPage: Boolean,
    ): SelectionFavouriteOutcome {
        val direct = selectedVideosInView(currentFolder, inSearchPage)
        val folders = selectedFoldersInView(currentFolder, inSearchPage)
        val favUris = favourites.value.map { it.mediaUri }.toSet()
        return withContext(ioDispatcher) {
            // Issue #233: combine both halves — selected videos first,
            // then the videos inside selected folders, deduped by path
            // (favourites are keyed by path, so a video that is both
            // directly selected and inside a selected folder must
            // resolve once).
            val resolved = (direct + videosInFolders(folders))
                .distinctBy { it.path }
            if (resolved.isEmpty()) return@withContext SelectionFavouriteOutcome.EMPTY
            val allFav = resolved.all { favUris.contains(it.path) }
            if (allFav) {
                resolved.forEach { persistence.removeFavourite(it.path) }
            } else {
                resolved.filter { !favUris.contains(it.path) }.forEach { video ->
                    persistence.addFavourite(
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
            clearSelection()
            if (allFav) SelectionFavouriteOutcome.REMOVED
            else SelectionFavouriteOutcome.ADDED
        }
    }

    // -- Add to playlist (#144) -----------------------------------------------
    //
    // The pending videos and the two dialog flags live here so the
    // permission-loss clear (#319) drops them together with the rest
    // of the media state. The screen renders the dialogs and shows
    // the snackbars; the counts come back in the results below.

    private val _videosForPlaylistAdd = MutableStateFlow<List<VideoFile>>(emptyList())
    /** #144: videos queued for "Add to playlist" — the whole
     * multi-select action-bar selection. */
    val videosForPlaylistAdd: StateFlow<List<VideoFile>> = _videosForPlaylistAdd.asStateFlow()

    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    val showAddToPlaylistDialog: StateFlow<Boolean> = _showAddToPlaylistDialog.asStateFlow()

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    /** Opens the add-to-playlist dialog for these videos. */
    fun showAddToPlaylist(videos: List<VideoFile>) {
        _videosForPlaylistAdd.value = videos
        _showAddToPlaylistDialog.value = true
    }

    /** Dismisses the add-to-playlist dialog, dropping the pending videos. */
    fun dismissAddToPlaylist() {
        _showAddToPlaylistDialog.value = false
        _videosForPlaylistAdd.value = emptyList()
    }

    /**
     * Opens the create-playlist dialog from the add dialog. Issue
     * #310: keeps the pending videos — the create dialog's confirm
     * adds them to the new playlist, and dismissing puts the add
     * dialog back, so the pending videos are never dropped.
     */
    fun openCreatePlaylist() {
        _showAddToPlaylistDialog.value = false
        _showCreatePlaylistDialog.value = true
    }

    /**
     * Dismisses the create-playlist dialog. Issue #310: cancelling
     * creation must not lose the pending videos — puts the add dialog
     * back so the user can pick an existing playlist or try creating
     * again.
     */
    fun dismissCreatePlaylist() {
        _showCreatePlaylistDialog.value = false
        if (_videosForPlaylistAdd.value.isNotEmpty()) _showAddToPlaylistDialog.value = true
    }

    /**
     * Adds the pending videos to an existing playlist. Issue #230:
     * `addItemToPlaylist` returns -1 for an item that is already in
     * the playlist — the result counts the real adds so the feedback
     * tells the truth. On completion the dialog closes, the pending
     * videos are dropped and the selection is cleared.
     */
    suspend fun addPendingToPlaylist(playlistId: Long): AddToPlaylistResult =
        withContext(ioDispatcher) {
            val vids = _videosForPlaylistAdd.value
            var addedCount = 0
            vids.forEach { vid ->
                val rowId = persistence.addItemToPlaylist(
                    PlaylistItem(
                        playlistId = playlistId,
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
            _showAddToPlaylistDialog.value = false
            _videosForPlaylistAdd.value = emptyList()
            clearSelection()
            AddToPlaylistResult(added = addedCount, total = vids.size)
        }

    /**
     * Creates a playlist and adds the pending videos to it (issue
     * #310: the same item mapping, order and counting as adding to
     * an existing playlist). On failure the pending videos are kept
     * and the add dialog is put back so the user can retry instead
     * of silently losing the selection.
     */
    suspend fun createPlaylistAndAddPending(
        name: String,
        isVideo: Boolean,
    ): CreatePlaylistOutcome = withContext(ioDispatcher) {
        val vids = _videosForPlaylistAdd.value
        try {
            val newId = persistence.createPlaylist(name, isVideoPlaylist = isVideo)
            var addedCount = 0
            vids.forEach { vid ->
                val rowId = persistence.addItemToPlaylist(
                    PlaylistItem(
                        playlistId = newId,
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
            if (vids.isNotEmpty()) {
                _videosForPlaylistAdd.value = emptyList()
                clearSelection()
            }
            _showCreatePlaylistDialog.value = false
            if (vids.isEmpty()) CreatePlaylistOutcome.Created(name)
            else CreatePlaylistOutcome.Added(name, addedCount, vids.size)
        } catch (_: Exception) {
            // Creation failed — keep the pending videos and put the
            // add dialog back so the user can retry instead of
            // silently losing the selection.
            _showCreatePlaylistDialog.value = false
            if (vids.isNotEmpty()) _showAddToPlaylistDialog.value = true
            CreatePlaylistOutcome.Failed
        }
    }
}

/** Result of [LocalViewModel.toggleSelectionFavourite]. */
enum class SelectionFavouriteOutcome {
    /** Every selected video was already a favourite; all were removed. */
    REMOVED,
    /** At least one selected video was not a favourite; the missing ones were added. */
    ADDED,
    /** Nothing resolved from the selection — no change, selection kept. */
    EMPTY,
}

/** Result of [LocalViewModel.addPendingToPlaylist]: real adds vs pending total. */
data class AddToPlaylistResult(
    val added: Int,
    val total: Int,
)

/** Result of [LocalViewModel.createPlaylistAndAddPending]. */
sealed interface CreatePlaylistOutcome {
    /** Created with no pending videos to add. */
    data class Created(val name: String) : CreatePlaylistOutcome
    /** Created and the pending videos were added ([added] of [total]). */
    data class Added(val name: String, val added: Int, val total: Int) : CreatePlaylistOutcome
    /** Creation failed; the pending videos were kept and the add dialog put back. */
    data object Failed : CreatePlaylistOutcome
}

private fun parseSortField(name: String, default: SortField): SortField =
    try {
        SortField.valueOf(name)
    } catch (_: Exception) {
        default
    }

private fun parseViewMode(name: String): ViewMode =
    try {
        ViewMode.valueOf(name)
    } catch (_: Exception) {
        ViewMode.LIST
    }

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
 * that is the user's explicit choice, no guard is applied here. A blank
 * entry is different (#316): it normalizes to `""`, so `"$base/"` is `"/"`
 * and every absolute path would match — blank/whitespace-only entries
 * (and a lone `/`, which also normalizes to `""`) never match anything.
 *
 * Moved here from LocalScreen in Phase 2b (the ViewModel applies it
 * during scans); MusicScreen and FoldersBlacklistScreen import it
 * from this package, unchanged.
 */
internal fun isPathBlacklisted(path: String, blacklist: Set<String>): Boolean {
    if (blacklist.isEmpty()) return false
    val normalized = normalizedTreePath(path)
    return blacklist.any { entry ->
        val base = normalizedTreePath(entry)
        // #316: a blank entry normalizes to "", so "$base/" is "/" and
        // every absolute path would match. Skip blank entries outright.
        if (base.isBlank()) return@any false
        normalized == base ||
            normalized.startsWith("$base/") ||
            normalized.equals(base, ignoreCase = true) ||
            normalized.startsWith("$base/", ignoreCase = true)
    }
}

/**
 * #158: folder blacklist (Preferences > Folders). A blacklisted
 * folder and its videos never reach the UI; the filter runs on the
 * scanned result at every load site, so it also applies after
 * returning from Preferences. #202: the match is a prefix rule
 * ([isPathBlacklisted]) — a blacklisted folder hides its subfolders
 * and their videos too, not just its own top level.
 *
 * Moved here from LocalScreen in Phase 2b, unchanged.
 */
internal fun applyFolderBlacklist(
    scanned: List<MediaFolder>,
    scannedVideos: List<VideoFile>,
    blacklist: Set<String>,
): Pair<List<MediaFolder>, List<VideoFile>> {
    if (blacklist.isEmpty()) return scanned to scannedVideos
    val visibleFolders = scanned.filter { !isPathBlacklisted(it.path, blacklist) }
    // Issue #317: a content://-pathed video (blank DATA) has no
    // filesystem parent — File(path).parent is the meaningless
    // "content://media/external/...", so it could never match a
    // blacklisted folder and leaked into file view and search.
    // Resolve it from the folder list instead: the video's bucketId
    // is the scanned folder's id (MediaFolder.id == bucket id), and
    // the folder carries the real parent path. Built from the
    // unfiltered list — the blacklisted folder itself must stay in
    // the map, or its videos could never be matched. A video whose
    // bucket has no folder entry (bucket "unknown", or every row in
    // the bucket had blank DATA so the folder path is unknown)
    // falls back to the old File.parent behaviour — visible, never
    // falsely hidden.
    val folderPathByBucketId = scanned
        .filter { it.path.isNotBlank() }
        .associate { it.id to it.path }
    val visibleVideos = scannedVideos.filter { video ->
        val parent = if (video.path.isNotBlank() && !video.path.startsWith("content://")) {
            File(video.path).parent
        } else {
            folderPathByBucketId[video.bucketId] ?: File(video.path).parent
        }
        parent == null || !isPathBlacklisted(parent, blacklist)
    }
    return visibleFolders to visibleVideos
}
