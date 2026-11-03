package com.mpx.player.ui.screens.music

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.mpx.player.ui.components.AsyncAlbumThumbnail
import com.mpx.player.ui.components.AsyncAudioTrackThumbnail
import com.mpx.player.ui.components.FastScrollbar
import com.mpx.player.ui.components.GridFastScrollbar
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import com.mpx.player.util.MediaPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onOpenPlayer: () -> Unit,
    playbackManager: PlaybackManager? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaStoreRepository(context) }
    var hasPermission by remember {
        mutableStateOf(MediaPermissions.hasAudioPermission(context))
    }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AudioAlbum>>(emptyList()) }
    var artists by remember { mutableStateOf<List<AudioArtist>>(emptyList()) }
    var folders by remember { mutableStateOf<List<AudioFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

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

    val tabs = listOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
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
                val currentPerm = MediaPermissions.hasAudioPermission(context)
                hasPermission = currentPerm
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxSurface,
                ),
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Equalizer is scheduled for audio phase")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = MpxTextPrimary,
                        )
                    }
                    IconButton(onClick = {
                        refreshMusic()
                        scope.launch {
                            snackbarHostState.showSnackbar("Music library refreshed")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MpxTextPrimary,
                        )
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
            // Scrollable 5-Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MpxSurface,
                contentColor = MpxPrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MpxPrimary,
                    )
                },
                divider = {
                    HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selectedTab == index) MpxPrimary else MpxTextSecondary,
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
                            "Search songs...",
                            color = MpxTextMuted,
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MpxTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxSurfaceElevated,
                        unfocusedContainerColor = MpxSurfaceVariant,
                        focusedBorderColor = MpxPrimary,
                        unfocusedBorderColor = MpxBorder,
                        focusedTextColor = MpxTextPrimary,
                        unfocusedTextColor = MpxTextPrimary,
                    ),
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = onOpenPlayer,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MpxPrimary,
                        contentColor = MpxTextPrimary,
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
                        "Shuffle All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Honest milestone notice
            HonestStatusBanner(
                text = "Real MediaStore scanning active. Audio tracks, albums, artists, and folders reflect device media.",
            )

            // Tab Content
            if (!hasPermission) {
                EmptyStateView(
                    title = "Permission Required",
                    message = "MPx needs permission to access your audio files to display your music library.",
                    icon = Icons.Filled.MusicNote,
                    actionLabel = "Grant Permission",
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
                    CircularProgressIndicator(color = MpxPrimary)
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        if (tracks.isEmpty()) {
                            EmptyStateView(
                                title = "No Music Found",
                                message = "No audio tracks were found on this device.",
                                icon = Icons.Filled.MusicNote,
                                actionLabel = "Refresh",
                                onActionClick = { refreshMusic() },
                            )
                        } else if (filteredTracks.isEmpty()) {
                            EmptyStateView(
                                title = "No Matches Found",
                                message = "No songs match \"$searchQuery\".",
                                icon = Icons.Filled.Search,
                            )
                        } else {
                            TracksTabContent(
                                tracks = filteredTracks,
                                onTrackClick = { index ->
                                    playbackManager?.playAudioQueue(filteredTracks, startIndex = index)
                                    onOpenPlayer()
                                },
                                onTrackOptionsClick = { track ->
                                    trackForOptions = track
                                },
                            )
                        }
                    }
                    1 -> PlaylistsTabContent(
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
                    2 -> {
                        if (albums.isEmpty()) {
                            EmptyStateView(
                                title = "No Albums Found",
                                message = "No audio albums were found on this device.",
                                icon = Icons.Filled.MusicNote,
                            )
                        } else {
                            AlbumsTabContent(albums = albums, onAlbumClick = {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            })
                        }
                    }
                    3 -> {
                        if (artists.isEmpty()) {
                            EmptyStateView(
                                title = "No Artists Found",
                                message = "No music artists were found on this device.",
                                icon = Icons.Filled.Person,
                            )
                        } else {
                            ArtistsTabContent(artists = artists, onArtistClick = {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            })
                        }
                    }
                    4 -> {
                        if (folders.isEmpty()) {
                            EmptyStateView(
                                title = "No Audio Folders Found",
                                message = "No audio folders were found on this device.",
                                icon = Icons.Filled.Folder,
                            )
                        } else {
                            FoldersTabContent(folders = folders, onFolderClick = {
                                playbackManager?.playAudioQueue(tracks, startIndex = 0)
                                onOpenPlayer()
                            })
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
                    snackbarHostState.showSnackbar("Playlist \"$name\" created")
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
                    snackbarHostState.showSnackbar("Added to \"${pl.name}\"")
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
            containerColor = MpxSurface,
            title = {
                Text(
                    text = targetTrack.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Text(
                        text = "${targetTrack.artist} • ${targetTrack.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
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
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Play Track", color = MpxTextPrimary)
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
                                        if (added) "Added to Favourites" else "Removed from Favourites"
                                    )
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(12.dp))
                        Text("Toggle Favourite", color = MpxTextPrimary)
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
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = MpxPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Playlist...", color = MpxTextPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForOptions = null }) {
                    Text("Close", color = MpxPrimary)
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
                    snackbarHostState.showSnackbar("Deleted \"${pl.name}\"")
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
                    snackbarHostState.showSnackbar("Removed from Favourites")
                }
            },
            onClearAll = {
                scope.launch {
                    persistenceRepository.clearFavourites()
                    snackbarHostState.showSnackbar("Cleared Favourites")
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
                    snackbarHostState.showSnackbar("Cleared History")
                }
            },
        )
    }
}

private fun getPlayableUri(track: AudioTrack): String {
    return if (track.path.isNotBlank()) {
        track.path
    } else {
        when (track.id) {
            "1" -> "asset:///media/midnight_drive.mp3"
            "2" -> "asset:///media/electric_echoes.mp3"
            else -> "asset:///media/sample_video.mp4"
        }
    }
}

@Composable
private fun TracksTabContent(
    tracks: List<AudioTrack>,
    onTrackClick: (Int) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
) {
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
                            .background(MpxSurfaceElevated),
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
                            color = MpxTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = track.durationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextMuted,
                        fontSize = 12.sp,
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { onTrackClick(index) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Track options for ${track.title}",
                            tint = MpxTextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp),
                    color = MpxBorder,
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
                            .background(MpxPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Filled.PlaylistAdd, contentDescription = null, tint = MpxPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(text = "New Playlist", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MpxPrimary))
                        Text(text = "Create custom playlist", style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary)
                    }
                }
                HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
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
                        Text(text = "Favourites", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTextPrimary)
                        Text(text = "$favouritesCount songs", style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary)
                    }
                }
                HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
            }
            item(key = "history_row", contentType = "history_action") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHistoryClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MpxPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MpxPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Recently Played", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTextPrimary)
                        Text(text = "$historyCount songs", style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary)
                    }
                }
                HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
            }
            if (playlists.isEmpty()) {
                item(key = "empty_playlists", contentType = "empty_state") {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Filled.QueueMusic, contentDescription = null, tint = MpxTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(text = "No Playlists Found", style = MaterialTheme.typography.titleMedium, color = MpxTextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Playlists will appear here once created or synced.", style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary)
                        }
                    }
                }
            }
            items(items = playlists, key = { it.id.ifBlank { it.title } }, contentType = { "playlist_row" }) { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MpxSurfaceElevated), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.QueueMusic, contentDescription = null, tint = MpxPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = playlist.title, style = MaterialTheme.typography.titleMedium, color = MpxTextPrimary)
                        Text(text = "${playlist.itemCount} songs • ${playlist.subtitle}", style = MaterialTheme.typography.bodySmall, color = MpxTextSecondary)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MpxBorder, thickness = 0.5.dp)
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
                        .background(MpxSurfaceVariant)
                        .clickable { onAlbumClick() }
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MpxSurfaceElevated),
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
                        color = MpxTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${album.artist} • ${album.trackCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
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
                            .background(MpxSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MpxPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MpxTextPrimary,
                        )
                        Text(
                            text = "${artist.trackCount} tracks • ${artist.albumCount} album",
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTextSecondary,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp),
                    color = MpxBorder,
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
    onFolderClick: () -> Unit,
) {
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
                            .background(MpxSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MpxPrimary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MpxTextPrimary,
                            )
                            if (folder.isStorageRemovable) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(MpxPrimary.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "SD CARD",
                                        color = MpxPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${folder.trackCount} tracks • ${folder.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    color = MpxBorder,
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
}
