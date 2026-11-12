package com.mpx.player.ui.screens.music

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import com.mpx.player.R
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.screens.music.components.MusicDialogs
import com.mpx.player.ui.screens.music.components.MusicSelectionBottomBar
import com.mpx.player.ui.screens.music.components.MusicSelectionTopBar
import com.mpx.player.ui.screens.music.components.MusicTopBar
import com.mpx.player.ui.screens.music.components.rememberMusicSelectionActions
import com.mpx.player.ui.screens.music.components.tabs.AlbumsTabContent
import com.mpx.player.ui.screens.music.components.tabs.ArtistsTabContent
import com.mpx.player.ui.screens.music.components.tabs.FoldersTabContent
import com.mpx.player.ui.screens.music.components.tabs.PlaylistsTabContent
import com.mpx.player.ui.screens.music.components.tabs.TracksTabContent
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.MediaPermissions
import com.mpx.player.util.PipUtils
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

enum class MusicTab(
    val id: String,
    @StringRes val titleRes: Int,
) {
    TRACKS("tracks", R.string.music_tab_tracks),
    PLAYLISTS("playlists", R.string.music_tab_playlists),
    ALBUMS("albums", R.string.music_tab_albums),
    ARTISTS("artists", R.string.music_tab_artists),
    FOLDERS("folders", R.string.music_tab_folders);
}

// Issue #256: Saver for the selected inner tab — MusicTab is an enum, so
// it is not Bundle-saveable by default. Round-trips through the enum name
// (same shape as LocalScreen's MediaFolderSaver, #175). An unknown or
// corrupt restored name falls back to TRACKS, never crashes.
private val MusicTabSaver: Saver<MusicTab, String> = Saver(
    save = { it.name },
    restore = { runCatching { MusicTab.valueOf(it) }.getOrNull() ?: MusicTab.TRACKS },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    permissionResultHandler: ActivityResultHandler<Boolean>,
    onOpenPlayer: () -> Unit,
    playbackCoordinator: PlaybackCoordinator? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MusicViewModel = koinViewModel()
    val appSettings by viewModel.settings.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    var showSortPopup by remember { mutableStateOf(false) }

    val selectedTrackIds by viewModel.selectedTrackIds.collectAsState()
    val isTrackSelectionMode = selectedTrackIds.isNotEmpty()
    val trackSelectionCount = selectedTrackIds.size

    val selectedAlbumIds by viewModel.selectedAlbumIds.collectAsState()
    val isAlbumSelectionMode = selectedAlbumIds.isNotEmpty()
    val albumSelectionCount = selectedAlbumIds.size

    val selectedArtistIds by viewModel.selectedArtistIds.collectAsState()
    val isArtistSelectionMode = selectedArtistIds.isNotEmpty()
    val artistSelectionCount = selectedArtistIds.size

    val selectedPlaylistIds by viewModel.selectedPlaylistIds.collectAsState()
    val isPlaylistSelectionMode = selectedPlaylistIds.isNotEmpty()
    val playlistSelectionCount = selectedPlaylistIds.size

    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val isFolderSelectionMode = selectedFolderIds.isNotEmpty()
    val folderSelectionCount = selectedFolderIds.size

    val isAnySelectionMode = isTrackSelectionMode || isAlbumSelectionMode ||
        isArtistSelectionMode || isPlaylistSelectionMode || isFolderSelectionMode

    val favourites by viewModel.favourites.collectAsState()
    val historyTotal by viewModel.historyTotal.collectAsState()
    val historyLimit by viewModel.historyLimit.collectAsState()
    val history by viewModel.history.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    var showFavouritesDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var selectedCustomPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var showManageTabsDialog by remember { mutableStateOf(false) }

    // VM-owned playlist dialog states (§6)
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val tracksForPlaylistAdd by viewModel.tracksForPlaylistAdd.collectAsState()

    val visibleTabs by viewModel.visibleTabs.collectAsState()

    // Issue #256: rememberSaveable, not remember — navigateToTab pops
    // this tab with saveState/restoreState, which restores only
    // back-stack-entry saved state, so a plain remember reset the inner
    // tab to the first visible tab on every return.
    var selectedTab by rememberSaveable(stateSaver = MusicTabSaver) {
        mutableStateOf(visibleTabs.firstOrNull() ?: MusicTab.TRACKS)
    }

    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            selectedTab = visibleTabs.firstOrNull() ?: MusicTab.TRACKS
        }
    }

    // #185: Back exits multi-select (exactly one step), like Local.
    BackHandler(enabled = isAnySelectionMode) {
        viewModel.clearAllSelections()
    }

    // #185: leaving a tab drops its selection — a selection must not
    // linger (and its action bar must not show) on the other tabs.
    LaunchedEffect(selectedTab) {
        viewModel.clearSelectionsExcept(selectedTab)
    }

    // SavedStateHandle-backed search state (Local's pattern, #255, #326)
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // #326: Back exits search (exactly one step), like Local.
    BackHandler(enabled = isSearching && !isAnySelectionMode) {
        viewModel.resetSearch()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val actions = rememberMusicSelectionActions(
        viewModel = viewModel,
        context = context,
        scope = scope,
        snackbarHostState = snackbarHostState,
        playbackCoordinator = playbackCoordinator,
        onOpenPlayer = onOpenPlayer,
    )

    DisposableEffect(permissionResultHandler) {
        onDispose { permissionResultHandler.callback = null }
    }
    SideEffect {
        permissionResultHandler.callback = { isGranted ->
            scope.launch { viewModel.refreshLibrary(isGranted) }
        }
    }

    fun refreshMusic() {
        scope.launch { viewModel.refreshLibrary(MediaPermissions.hasAudioPermission(context)) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPerm = MediaPermissions.hasAudioPermission(context)
                scope.launch { viewModel.onResume(currentPerm) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val sortedTracks by viewModel.sortedTracks.collectAsState()
    val filteredAlbums by viewModel.filteredAlbums.collectAsState()
    val sortedAlbums by viewModel.sortedAlbums.collectAsState()
    val filteredArtists by viewModel.filteredArtists.collectAsState()
    val sortedArtists by viewModel.sortedArtists.collectAsState()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsState()
    val sortedPlaylists by viewModel.sortedPlaylists.collectAsState()
    val filteredFolders by viewModel.filteredFolders.collectAsState()
    val sortedFolders by viewModel.sortedFolders.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isAnySelectionMode) {
                MusicSelectionTopBar(
                    selectionCount = when {
                        isPlaylistSelectionMode -> playlistSelectionCount
                        isAlbumSelectionMode -> albumSelectionCount
                        isArtistSelectionMode -> artistSelectionCount
                        isFolderSelectionMode -> folderSelectionCount
                        else -> trackSelectionCount
                    },
                    onClearSelection = { viewModel.clearAllSelections() },
                )
            } else {
                MusicTopBar(
                    isSearching = isSearching,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onCloseSearch = { viewModel.resetSearch() },
                    onOpenSearch = { viewModel.setSearching(true) },
                    canShuffle = sortedTracks.isNotEmpty(),
                    onShuffle = { actions.shuffleVisibleTracks(sortedTracks) },
                    viewMode = viewMode,
                    onToggleViewMode = {
                        viewModel.updateViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                    },
                    onEqualizerClick = {
                        if (!PipUtils.launchSystemEqualizer(context, 0)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.equalizer_dialog_msg))
                            }
                        }
                    },
                    onOpenSortPopup = { showSortPopup = true },
                    onRefresh = {
                        refreshMusic()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.music_library_refreshed))
                        }
                    },
                    onManageTabsClick = { showManageTabsDialog = true },
                )
            }
        },
        bottomBar = {
            if (isAnySelectionMode) {
                MusicSelectionBottomBar(
                    onPlay = {
                        when {
                            isPlaylistSelectionMode -> actions.playPlaylistSelection()
                            isAlbumSelectionMode -> actions.playAlbumSelection()
                            isArtistSelectionMode -> actions.playArtistSelection()
                            isFolderSelectionMode -> actions.playFolderSelection()
                            else -> actions.playTrackSelection()
                        }
                    },
                    onAddToPlaylist = {
                        when {
                            isPlaylistSelectionMode -> actions.addPlaylistSelectionToPlaylist()
                            isAlbumSelectionMode -> actions.addAlbumSelectionToPlaylist()
                            isArtistSelectionMode -> actions.addArtistSelectionToPlaylist()
                            isFolderSelectionMode -> actions.addFolderSelectionToPlaylist()
                            else -> actions.addTrackSelectionToPlaylist()
                        }
                    },
                    onToggleFavourite = {
                        when {
                            isPlaylistSelectionMode -> actions.togglePlaylistSelectionFavourite()
                            isAlbumSelectionMode -> actions.toggleAlbumSelectionFavourite()
                            isArtistSelectionMode -> actions.toggleArtistSelectionFavourite()
                            isFolderSelectionMode -> actions.toggleFolderSelectionFavourite()
                            else -> actions.toggleTrackSelectionFavourite()
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Dynamic Visible Tabs Bar
            val selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MpxTheme.colors.black,
                contentColor = MpxTheme.colors.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MpxTheme.colors.primary,
                        )
                    }
                },
                divider = {
                    androidx.compose.material3.HorizontalDivider(
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                },
            ) {
                visibleTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = stringResource(tab.titleRes),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selectedTab == tab) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                            )
                        },
                    )
                }
            }

            // Tab Content
            if (!hasPermission) {
                EmptyStateView(
                    title = stringResource(R.string.music_permission_required_title),
                    message = stringResource(R.string.music_permission_required_desc),
                    icon = Icons.Filled.MusicNote,
                    actionLabel = stringResource(R.string.music_permission_grant_button),
                    onActionClick = {
                        permissionLauncher.launch(MediaPermissions.audioPermission())
                    },
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MpxLoadingIndicator()
                }
            } else {
                when (selectedTab) {
                    MusicTab.TRACKS -> {
                        if (tracks.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_tracks_title),
                                message = stringResource(R.string.music_empty_tracks_desc),
                                icon = Icons.Filled.MusicNote,
                                actionLabel = stringResource(R.string.music_empty_refresh_action),
                                onActionClick = { refreshMusic() },
                            )
                        } else if (filteredTracks.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_search_no_matches_title),
                                message = stringResource(R.string.music_search_no_matches_desc, searchQuery),
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    refreshMusic()
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                TracksTabContent(
                                    tracks = sortedTracks,
                                    viewMode = viewMode,
                                    showThumbnails = appSettings.showThumbnails,
                                    selectedTrackIds = selectedTrackIds,
                                    isSelectionMode = isTrackSelectionMode,
                                    favourites = favourites,
                                    onToggleTrackFavourite = { actions.toggleTrackFavourite(it) },
                                    onToggleTrackSelection = { viewModel.toggleTrackSelection(it) },
                                    onTrackClick = { index -> actions.playTrack(sortedTracks, index) },
                                    onTrackOptionsClick = { track -> trackForOptions = track },
                                )
                            }
                        }
                    }
                    MusicTab.PLAYLISTS -> {
                        if (filteredPlaylists.isEmpty() && searchQuery.isNotBlank()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_search_no_matches_title),
                                message = stringResource(R.string.music_search_no_matches_desc, searchQuery),
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    refreshMusic()
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                PlaylistsTabContent(
                                    playlists = sortedPlaylists,
                                    viewMode = viewMode,
                                    selectedPlaylistIds = selectedPlaylistIds,
                                    isSelectionMode = isPlaylistSelectionMode,
                                    onTogglePlaylistSelection = { viewModel.togglePlaylistSelection(it) },
                                    favouritesCount = favourites.size,
                                    historyCount = historyTotal,
                                    onCreatePlaylist = { viewModel.openCreatePlaylist() },
                                    onFavouritesClick = { showFavouritesDialog = true },
                                    onHistoryClick = { showHistoryDialog = true },
                                    onPlaylistClick = { playlist ->
                                        actions.handlePlaylistClick(
                                            playlist = playlist,
                                            customPlaylists = customPlaylists,
                                            onSelectCustomPlaylist = { custom ->
                                                selectedCustomPlaylist = custom
                                                playlistItemsForDialog = emptyList()
                                                scope.launch {
                                                    val items = viewModel.getPlaylistItems(custom.id)
                                                    if (selectedCustomPlaylist?.id == custom.id) {
                                                        playlistItemsForDialog = items
                                                    }
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    MusicTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_albums_title),
                                message = stringResource(R.string.music_empty_albums_desc),
                                icon = Icons.Filled.MusicNote,
                            )
                        } else if (filteredAlbums.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_search_no_matches_title),
                                message = stringResource(R.string.music_search_no_matches_desc, searchQuery),
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    refreshMusic()
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AlbumsTabContent(
                                    albums = sortedAlbums,
                                    viewMode = viewMode,
                                    showThumbnails = appSettings.showThumbnails,
                                    selectedAlbumIds = selectedAlbumIds,
                                    isSelectionMode = isAlbumSelectionMode,
                                    onToggleAlbumSelection = { viewModel.toggleAlbumSelection(it) },
                                    onAlbumClick = { album -> actions.playAlbum(album) },
                                )
                            }
                        }
                    }
                    MusicTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_artists_title),
                                message = stringResource(R.string.music_empty_artists_desc),
                                icon = Icons.Filled.Person,
                            )
                        } else if (filteredArtists.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_search_no_matches_title),
                                message = stringResource(R.string.music_search_no_matches_desc, searchQuery),
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    refreshMusic()
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                ArtistsTabContent(
                                    artists = sortedArtists,
                                    viewMode = viewMode,
                                    selectedArtistIds = selectedArtistIds,
                                    isSelectionMode = isArtistSelectionMode,
                                    onToggleArtistSelection = { viewModel.toggleArtistSelection(it) },
                                    onArtistClick = { artist -> actions.playArtist(artist) },
                                )
                            }
                        }
                    }
                    MusicTab.FOLDERS -> {
                        if (folders.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_folders_title),
                                message = stringResource(R.string.music_empty_folders_desc),
                                icon = Icons.Filled.Folder,
                            )
                        } else if (filteredFolders.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_search_no_matches_title),
                                message = stringResource(R.string.music_search_no_matches_desc, searchQuery),
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    refreshMusic()
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                FoldersTabContent(
                                    folders = sortedFolders,
                                    viewMode = viewMode,
                                    selectedFolderIds = selectedFolderIds,
                                    isSelectionMode = isFolderSelectionMode,
                                    onToggleFolderSelection = { viewModel.toggleFolderSelection(it) },
                                    onFolderClick = { folder -> actions.playFolder(folder) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    MusicDialogs(
        viewModel = viewModel,
        appSettings = appSettings,
        customPlaylists = customPlaylists,
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        tracksForPlaylistAdd = tracksForPlaylistAdd,
        showCreatePlaylistDialog = showCreatePlaylistDialog,
        trackForOptions = trackForOptions,
        onDismissTrackOptions = { trackForOptions = null },
        sortedTracks = sortedTracks,
        selectedCustomPlaylist = selectedCustomPlaylist,
        onDismissCustomPlaylist = { selectedCustomPlaylist = null },
        onUpdateCustomPlaylist = { selectedCustomPlaylist = it },
        playlistItemsForDialog = playlistItemsForDialog,
        onPlaylistItemsChange = { playlistItemsForDialog = it },
        showFavouritesDialog = showFavouritesDialog,
        onDismissFavouritesDialog = { showFavouritesDialog = false },
        favourites = favourites,
        showHistoryDialog = showHistoryDialog,
        onDismissHistoryDialog = { showHistoryDialog = false },
        history = history,
        historyTotal = historyTotal,
        historyLimit = historyLimit,
        showSortPopup = showSortPopup,
        onDismissSortPopup = { showSortPopup = false },
        sortField = sortField,
        viewMode = viewMode,
        onViewModeChange = { viewModel.updateViewMode(it) },
        showManageTabsDialog = showManageTabsDialog,
        onDismissManageTabsDialog = { showManageTabsDialog = false },
        playbackCoordinator = playbackCoordinator,
        onOpenPlayer = onOpenPlayer,
        snackbarHostState = snackbarHostState,
        scope = scope,
    )
}
