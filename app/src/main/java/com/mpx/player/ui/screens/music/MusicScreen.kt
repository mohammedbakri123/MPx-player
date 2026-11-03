package com.mpx.player.ui.screens.music

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import com.mpx.player.ui.components.AsyncAlbumThumbnail
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.components.AsyncAudioTrackThumbnail
import com.mpx.player.ui.components.FastScrollbar
import com.mpx.player.ui.components.GridFastScrollbar
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.screens.local.MX_SORT_FIELDS
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.screens.local.SortViewDialog
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.util.MediaPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    permissionResultHandler: ActivityResultHandler<Boolean>,
    onOpenPlayer: () -> Unit,
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
        mutableStateOf(MediaPermissions.hasAudioPermission(context))
    }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AudioAlbum>>(emptyList()) }
    var artists by remember { mutableStateOf<List<AudioArtist>>(emptyList()) }
    var folders by remember { mutableStateOf<List<AudioFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var viewMode by remember {
        mutableStateOf(if (appSettings.musicViewMode == "GRID") ViewMode.GRID else ViewMode.LIST)
    }
    var sortField by remember { mutableStateOf(SortField.NAME) }
    var showSortDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun updateViewMode(newMode: ViewMode) {
        viewMode = newMode
        val updated = if (appSettings.separateViewLayout) {
            appSettings.copy(musicViewMode = newMode.name)
        } else {
            appSettings.copy(musicViewMode = newMode.name, localViewMode = newMode.name)
        }
        realSettingsRepo.save(updated)
        appSettings = updated
    }

    val persistenceRepository = remember(context) {
        playbackManager?.persistenceRepository
            ?: RoomMediaPersistenceRepository(MpxDatabase.getInstance(context))
    }
    val favourites by persistenceRepository.observeFavourites().collectAsState(initial = emptyList())
    val history by persistenceRepository.observeHistory().collectAsState(initial = emptyList())
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())
    val allPlaylists = remember(customPlaylists, playlists) {
        customPlaylists.map { it.toPlaylist() } + playlists
    }

    var showFavouritesDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForPlaylistAdd by remember { mutableStateOf<AudioTrack?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedCustomPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    val visibleTabs = remember(
        appSettings.showMusicTracksTab,
        appSettings.showMusicPlaylistsTab,
        appSettings.showMusicAlbumsTab,
        appSettings.showMusicArtistsTab,
        appSettings.showMusicFoldersTab,
    ) {
        val list = mutableListOf<MusicTab>()
        if (appSettings.showMusicTracksTab) list.add(MusicTab.TRACKS)
        if (appSettings.showMusicPlaylistsTab) list.add(MusicTab.PLAYLISTS)
        if (appSettings.showMusicAlbumsTab) list.add(MusicTab.ALBUMS)
        if (appSettings.showMusicArtistsTab) list.add(MusicTab.ARTISTS)
        if (appSettings.showMusicFoldersTab) list.add(MusicTab.FOLDERS)
        if (list.isEmpty()) {
            list.add(MusicTab.TRACKS)
        }
        list
    }

    var selectedTab by remember { mutableStateOf(visibleTabs.firstOrNull() ?: MusicTab.TRACKS) }

    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            selectedTab = visibleTabs.firstOrNull() ?: MusicTab.TRACKS
        }
    }

    var showManageTabsDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(permissionResultHandler) {
        onDispose { permissionResultHandler.callback = null }
    }
    SideEffect {
        permissionResultHandler.callback = { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val t = repository.getAudioTracks()
                val p = repository.getPlaylists()
                val al = repository.getAudioAlbums()
                val ar = repository.getAudioArtists()
                val f = repository.getAudioFolders()
                withContext(Dispatchers.Main) {
                    tracks = t
                    playlists = p
                    albums = al
                    artists = ar
                    folders = f
                    isLoading = false
                }
            }
        } else {
            tracks = emptyList()
            playlists = emptyList()
            albums = emptyList()
            artists = emptyList()
            folders = emptyList()
        }
        }
    }

    fun refreshMusic() {
        if (MediaPermissions.hasAudioPermission(context)) {
            hasPermission = true
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val t = repository.getAudioTracks()
                val p = repository.getPlaylists()
                val al = repository.getAudioAlbums()
                val ar = repository.getAudioArtists()
                val f = repository.getAudioFolders()
                withContext(Dispatchers.Main) {
                    tracks = t
                    playlists = p
                    albums = al
                    artists = ar
                    folders = f
                    isLoading = false
                }
            }
        } else {
            hasPermission = false
            tracks = emptyList()
            playlists = emptyList()
            albums = emptyList()
            artists = emptyList()
            folders = emptyList()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appSettings = realSettingsRepo.load()
                val currentPerm = MediaPermissions.hasAudioPermission(context)
                hasPermission = currentPerm
                val loadedSettings = realSettingsRepo.load()
                appSettings = loadedSettings
                viewMode = if (loadedSettings.musicViewMode == "GRID") ViewMode.GRID else ViewMode.LIST
                if (currentPerm) {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        val t = repository.getAudioTracks()
                        val p = repository.getPlaylists()
                        val al = repository.getAudioAlbums()
                        val ar = repository.getAudioArtists()
                        val f = repository.getAudioFolders()
                        withContext(Dispatchers.Main) {
                            tracks = t
                            playlists = p
                            albums = al
                            artists = ar
                            folders = f
                            isLoading = false
                        }
                    }
                } else {
                    tracks = emptyList()
                    playlists = emptyList()
                    albums = emptyList()
                    artists = emptyList()
                    folders = emptyList()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sortedTracks = remember(filteredTracks, sortField) {
        when (sortField) {
            SortField.NAME -> filteredTracks.sortedBy { it.title.lowercase(Locale.US) }
            SortField.DATE -> filteredTracks.sortedByDescending { it.id }
            SortField.SIZE -> filteredTracks.sortedByDescending { it.durationMs }
            SortField.COUNT, SortField.NONE -> filteredTracks
            SortField.DURATION -> filteredTracks.sortedByDescending { it.durationMs }
            // MX combined keys (issue #70). Audio carries no date or
            // resolution: DATE_DURATION falls back to id recency, and
            // RESOLUTION_FRAMERATE leaves order unchanged.
            SortField.DATE_DURATION -> filteredTracks.sortedWith(
                compareByDescending<AudioTrack> { it.id }.thenByDescending { it.durationMs },
            )
            SortField.RESOLUTION_FRAMERATE -> filteredTracks
            SortField.STATUS_TYPE_LOCATION -> filteredTracks.sortedWith(
                compareBy<AudioTrack> {
                    val name = it.path.substringAfterLast('/', it.path)
                    val dot = name.lastIndexOf('.')
                    if (dot > 0) name.substring(dot + 1).lowercase(Locale.US) else ""
                }.thenBy { it.path },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.music_screen_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = {
                        updateViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = stringResource(R.string.cd_toggle_view),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.music_equalizer_scheduled))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = stringResource(R.string.music_cd_equalizer),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
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
                                text = { Text(stringResource(R.string.local_menu_refresh)) },
                                onClick = {
                                    menuExpanded = false
                                    refreshMusic()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.music_library_refreshed))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.music_menu_manage_tabs), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Tab, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showManageTabsDialog = true
                                },
                            )
                        }
                    }
                },
            )
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
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
                                color = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // Search Bar + Shuffle All Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.music_search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = onOpenPlayer,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.music_shuffle_all),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Honest milestone notice
            HonestStatusBanner(
                text = stringResource(R.string.music_banner_scanning),
            )

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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            TracksTabContent(
                                tracks = sortedTracks,
                                viewMode = viewMode,
                                onTrackClick = { index ->
                                    playbackManager?.playAudioQueue(sortedTracks, startIndex = index)
                                    onOpenPlayer()
                                },
                                onTrackOptionsClick = { track ->
                                    trackForOptions = track
                                },
                            )
                        }
                    }
                    MusicTab.PLAYLISTS -> PlaylistsTabContent(
                        playlists = allPlaylists,
                        favouritesCount = favourites.size,
                        historyCount = history.size,
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onFavouritesClick = { showFavouritesDialog = true },
                        onHistoryClick = { showHistoryDialog = true },
                        onPlaylistClick = { playlist ->
                            val custom = customPlaylists.find { it.id.toString() == playlist.id }
                            if (custom != null) {
                                selectedCustomPlaylist = custom
                                scope.launch {
                                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(custom.id)
                                }
                            } else {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            }
                        },
                    )
                    MusicTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_albums_title),
                                message = stringResource(R.string.music_empty_albums_desc),
                                icon = Icons.Filled.MusicNote,
                            )
                        } else {
                            AlbumsTabContent(albums = albums, onAlbumClick = {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            })
                        }
                    }
                    MusicTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_artists_title),
                                message = stringResource(R.string.music_empty_artists_desc),
                                icon = Icons.Filled.Person,
                            )
                        } else {
                            ArtistsTabContent(artists = artists, onArtistClick = {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            })
                        }
                    }
                    MusicTab.FOLDERS -> {
                        if (folders.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.music_empty_folders_title),
                                message = stringResource(R.string.music_empty_folders_desc),
                                icon = Icons.Filled.Folder,
                            )
                        } else {
                            FoldersTabContent(
                                folders = folders,
                                viewMode = viewMode,
                                onFolderClick = {
                                    playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                    onOpenPlayer()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    persistenceRepository.createPlaylist(name)
                    showCreatePlaylistDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.music_playlist_created_msg, name))
                }
            },
        )
    }

    if (trackForPlaylistAdd != null) {
        val targetTrack = trackForPlaylistAdd!!
        AddToPlaylistDialog(
            playlists = customPlaylists,
            mediaTitle = targetTrack.title,
            onDismiss = { trackForPlaylistAdd = null },
            onSelectPlaylist = { pl ->
                scope.launch {
                    val playableUri = getPlayableUri(targetTrack)
                    persistenceRepository.addItemToPlaylist(
                        PlaylistItem(
                            playlistId = pl.id,
                            mediaUri = playableUri,
                            mediaId = targetTrack.id,
                            title = targetTrack.title,
                            subtitle = "${targetTrack.artist} • ${targetTrack.album}",
                            isVideo = false,
                        )
                    )
                    trackForPlaylistAdd = null
                    snackbarHostState.showSnackbar(context.getString(R.string.music_added_to_playlist_msg, pl.name))
                }
            },
            onCreateNewPlaylist = {
                trackForPlaylistAdd = null
                showCreatePlaylistDialog = true
            },
        )
    }

    if (trackForOptions != null) {
        val targetTrack = trackForOptions!!
        AlertDialog(
            onDismissRequest = { trackForOptions = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = targetTrack.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Text(
                        text = "${targetTrack.artist} • ${targetTrack.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                trackForOptions = null
                                val idx = tracks.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
                                playbackManager?.playAudioQueue(tracks, startIndex = idx)
                                onOpenPlayer()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_play_track), color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                trackForOptions = null
                                scope.launch {
                                    val uri = getPlayableUri(targetTrack)
                                    val added = persistenceRepository.toggleFavourite(
                                        FavouriteItem(
                                            mediaUri = uri,
                                            mediaId = targetTrack.id,
                                            title = targetTrack.title,
                                            subtitle = "${targetTrack.artist} • ${targetTrack.album}",
                                            isVideo = false,
                                        )
                                    )
                                    snackbarHostState.showSnackbar(
                                        if (added) {
                                            context.getString(R.string.music_added_to_favourites)
                                        } else {
                                            context.getString(R.string.music_removed_from_favourites)
                                        }
                                    )
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_toggle_favourite), color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                trackForPlaylistAdd = targetTrack
                                trackForOptions = null
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_add_to_playlist), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForOptions = null }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    if (selectedCustomPlaylist != null) {
        val pl = selectedCustomPlaylist!!
        PlaylistDetailsDialog(
            playlist = pl,
            items = playlistItemsForDialog,
            onDismiss = { selectedCustomPlaylist = null },
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
                    selectedCustomPlaylist = null
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
                selectedCustomPlaylist = null
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
                    selectedCustomPlaylist = null
                    snackbarHostState.showSnackbar(context.getString(R.string.music_deleted_playlist_msg, pl.name))
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
                    snackbarHostState.showSnackbar(context.getString(R.string.music_removed_from_favourites))
                }
            },
            onClearAll = {
                scope.launch {
                    persistenceRepository.clearFavourites()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_favourites_msg))
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
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_history_msg))
                }
            },
        )
    }

    if (showSortDialog) {
        SortViewDialog(
            currentSort = sortField,
            currentView = viewMode,
            separateViewLayout = appSettings.separateViewLayout,
            availableSortFields = MX_SORT_FIELDS,
            onSortChange = { sortField = it },
            onViewChange = { updateViewMode(it) },
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
            onDismiss = { showSortDialog = false },
        )
    }

    if (showManageTabsDialog) {
        ManageMusicTabsDialog(
            settings = appSettings,
            onDismiss = { showManageTabsDialog = false },
            onToggleTab = { tabId, currentVal ->
                if (currentVal && appSettings.visibleMusicTabsCount <= 1) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_tab_min_one_required))
                    }
                } else {
                    val updated = appSettings.withMusicTabToggled(tabId, !currentVal)
                    appSettings = updated
                    realSettingsRepo.save(updated)
                }
            },
        )
    }
}

@Composable
private fun ManageMusicTabsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
) {
    val isOnlyOneVisible = settings.visibleMusicTabsCount <= 1
    val lastActiveSub = stringResource(R.string.settings_tab_last_active_sub)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.music_dialog_tabs_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.music_dialog_tabs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_tracks),
                    checked = settings.showMusicTracksTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicTracksTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("tracks", settings.showMusicTracksTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_playlists),
                    checked = settings.showMusicPlaylistsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicPlaylistsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("playlists", settings.showMusicPlaylistsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_albums),
                    checked = settings.showMusicAlbumsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicAlbumsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("albums", settings.showMusicAlbumsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_artists),
                    checked = settings.showMusicArtistsTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicArtistsTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("artists", settings.showMusicArtistsTab) },
                )
                MusicTabDialogToggleRow(
                    label = stringResource(R.string.music_tab_folders),
                    checked = settings.showMusicFoldersTab,
                    isLastVisible = isOnlyOneVisible && settings.showMusicFoldersTab,
                    subtitle = lastActiveSub,
                    onToggle = { onToggleTab("folders", settings.showMusicFoldersTab) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done), color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun MusicTabDialogToggleRow(
    label: String,
    checked: Boolean,
    isLastVisible: Boolean = false,
    subtitle: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            ),
        )
    }
}

private fun getPlayableUri(track: AudioTrack): String = track.path

@Composable
private fun TracksTabContent(
    tracks: List<AudioTrack>,
    viewMode: ViewMode = ViewMode.LIST,
    onTrackClick: (Int) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
) {
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> track.id.ifBlank { "${track.path}_$index" } },
                    contentType = { _, _ -> "track_row" },
                ) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncAudioTrackThumbnail(
                                track = track,
                                modifier = Modifier.fillMaxSize(),
                                iconSize = 24.dp,
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${track.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = track.durationFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )

                        Spacer(Modifier.width(8.dp))

                        IconButton(
                            onClick = { onTrackOptionsClick(track) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.music_cd_track_options, track.title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 0.5.dp,
                    )
                }
            }
            FastScrollbar(
                listState = listState,
                itemCount = tracks.size,
                getSectionText = { index ->
                    tracks.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
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
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> track.id.ifBlank { "${track.path}_$index" } },
                    contentType = { _, _ -> "track_grid_card" },
                ) { index, track ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(index) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncAudioTrackThumbnail(
                                    track = track,
                                    modifier = Modifier.fillMaxSize(),
                                    iconSize = 44.dp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { onTrackOptionsClick(track) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.music_cd_track_options, track.title),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = track.durationFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            GridFastScrollbar(
                gridState = gridState,
                itemCount = tracks.size,
                getSectionText = { index ->
                    tracks.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<Playlist>,
    favouritesCount: Int,
    historyCount: Int,
    onCreatePlaylist: () -> Unit,
    onFavouritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item(key = "new_playlist_header", contentType = "new_playlist_action") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreatePlaylist() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(text = stringResource(R.string.music_new_playlist_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary))
                        Text(text = stringResource(R.string.music_new_playlist_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
            item(key = "favourites_row", contentType = "favourites_action") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onFavouritesClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF5252).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF5252))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.music_favourites_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.music_songs_count, favouritesCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
            item(key = "history_row", contentType = "history_action") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHistoryClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.music_recently_played_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.music_songs_count, historyCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
            if (playlists.isEmpty()) {
                item(key = "empty_playlists", contentType = "empty_state") {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(text = stringResource(R.string.music_empty_playlists_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(text = stringResource(R.string.music_empty_playlists_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(items = playlists, key = { it.id.ifBlank { it.title } }, contentType = { "playlist_row" }) { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = playlist.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.music_playlist_songs_subtitle, playlist.itemCount, playlist.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
        FastScrollbar(
            listState = listState,
            itemCount = playlists.size + 3,
            getSectionText = { index ->
                if (index < 3) "" else playlists.getOrNull(index - 3)?.title?.take(1)?.uppercase(Locale.US) ?: ""
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AudioAlbum>,
    onAlbumClick: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = albums,
                key = { it.id.ifBlank { it.title } },
                contentType = { "album_card" },
            ) { album ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onAlbumClick() }
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncAlbumThumbnail(
                            album = album,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 48.dp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.music_album_tracks_format, album.artist, album.trackCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        GridFastScrollbar(
            gridState = gridState,
            itemCount = albums.size,
            getSectionText = { index ->
                albums.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<AudioArtist>,
    onArtistClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(
                items = artists,
                key = { it.id.ifBlank { it.name } },
                contentType = { "artist_row" },
            ) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.music_artist_details_format, artist.trackCount, artist.albumCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp),
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 0.5.dp,
                )
            }
        }
        FastScrollbar(
            listState = listState,
            itemCount = artists.size,
            getSectionText = { index ->
                artists.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<AudioFolder>,
    viewMode: ViewMode = ViewMode.LIST,
    onFolderClick: () -> Unit,
) {
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(
                    items = folders,
                    key = { it.id.ifBlank { it.path } },
                    contentType = { "audio_folder_row" },
                ) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFolderClick() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (folder.isStorageRemovable) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.music_sd_card_badge),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = stringResource(R.string.music_folder_tracks_format, folder.trackCount, folder.path),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 0.5.dp,
                    )
                }
            }
            FastScrollbar(
                listState = listState,
                itemCount = folders.size,
                getSectionText = { index ->
                    folders.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
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
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = folders,
                    key = { it.id.ifBlank { it.path } },
                    contentType = { "audio_folder_grid" },
                ) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFolderClick() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                                if (folder.isStorageRemovable) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.music_sd_card_badge),
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.music_songs_count, folder.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            GridFastScrollbar(
                gridState = gridState,
                itemCount = folders.size,
                getSectionText = { index ->
                    folders.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
