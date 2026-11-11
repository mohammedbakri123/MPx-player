package com.mpx.player.ui.screens.music
import com.mpx.player.ui.theme.MpxTheme

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.mpx.player.ui.ActivityResultHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.customPlaylistId
import org.koin.compose.viewmodel.koinViewModel
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.screens.local.AudioAlbumGridCard
import com.mpx.player.ui.screens.local.AudioAlbumRow
import com.mpx.player.ui.screens.local.AudioArtistGridCard
import com.mpx.player.ui.screens.local.AudioArtistRow
import com.mpx.player.ui.screens.local.AudioFolderGridCard
import com.mpx.player.ui.screens.local.AudioFolderRow
import com.mpx.player.ui.screens.local.AudioPlaylistGridCard
import com.mpx.player.ui.screens.local.AudioPlaylistRow
import com.mpx.player.ui.screens.local.AudioTrackGridCard
import com.mpx.player.ui.screens.local.AudioTrackRow
import com.mpx.player.ui.screens.local.LocalSortPopup
import com.mpx.player.ui.screens.local.MultiSelectAction
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.util.MediaPermissions
import com.mpx.player.util.PipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

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

// #265: one page of history rows in the PlaybackHistoryDialog — same
// value as the full History page (its own private const there).
private const val HISTORY_PAGE_SIZE = 100

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
    // Migration Phase 2c: screen state lives in MusicViewModel now —
    // library loading, settings, sort, search filtering, and selection.
    // The screen still takes PlaybackCoordinator directly for playback; that
    // moves behind an interface in a later phase (same split as Phase
    // 2a's PlayerViewModel). Settings come from the ViewModel (Koin's
    // AppSettingsRepository single), so the old settingsRepository
    // parameter is gone.
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
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedTrackIds by viewModel.selectedTrackIds.collectAsState()
    val isTrackSelectionMode = selectedTrackIds.isNotEmpty()
    val trackSelectionCount = selectedTrackIds.size

    fun clearTrackSelection() = viewModel.clearTrackSelection()

    fun toggleTrackSelection(track: AudioTrack) = viewModel.toggleTrackSelection(track)

    val selectedAlbumIds by viewModel.selectedAlbumIds.collectAsState()
    val isAlbumSelectionMode = selectedAlbumIds.isNotEmpty()
    val albumSelectionCount = selectedAlbumIds.size

    fun clearAlbumSelection() = viewModel.clearAlbumSelection()

    fun toggleAlbumSelection(album: AudioAlbum) = viewModel.toggleAlbumSelection(album)

    val selectedArtistIds by viewModel.selectedArtistIds.collectAsState()
    val isArtistSelectionMode = selectedArtistIds.isNotEmpty()
    val artistSelectionCount = selectedArtistIds.size

    fun clearArtistSelection() = viewModel.clearArtistSelection()

    fun toggleArtistSelection(artist: AudioArtist) = viewModel.toggleArtistSelection(artist)

    val selectedPlaylistIds by viewModel.selectedPlaylistIds.collectAsState()
    val isPlaylistSelectionMode = selectedPlaylistIds.isNotEmpty()
    val playlistSelectionCount = selectedPlaylistIds.size

    fun clearPlaylistSelection() = viewModel.clearPlaylistSelection()

    fun togglePlaylistSelection(playlist: Playlist) = viewModel.togglePlaylistSelection(playlist)

    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val isFolderSelectionMode = selectedFolderIds.isNotEmpty()
    val folderSelectionCount = selectedFolderIds.size

    fun clearFolderSelection() = viewModel.clearFolderSelection()

    fun toggleFolderSelection(folder: AudioFolder) = viewModel.toggleFolderSelection(folder)

    fun updateViewMode(newMode: ViewMode) = viewModel.updateViewMode(newMode)

    val favourites by viewModel.favourites.collectAsState()
    val historyTotal by viewModel.historyTotal.collectAsState()
    var historyLimit by rememberSaveable { mutableStateOf(HISTORY_PAGE_SIZE) }
    val history by viewModel.history.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    // The limit is rememberSaveable here so rotation does not drop rows
    // already loaded in the dialog (#265); the ViewModel owns the paging
    // itself — mirror the limit into it.
    LaunchedEffect(historyLimit) { viewModel.setHistoryLimit(historyLimit) }

    var showFavouritesDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForPlaylistAdd by remember { mutableStateOf<AudioTrack?>(null) }
    // #185: tracks queued for "Add to playlist" from the multi-select bar.
    var tracksForPlaylistAdd by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedCustomPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    val visibleTabs by viewModel.visibleTabs.collectAsState()

    // Issue #256: rememberSaveable, not remember — navigateToTab pops
    // this tab with saveState/restoreState, which restores only
    // back-stack-entry saved state, so a plain remember reset the inner
    // tab to the first visible tab on every return. The name-based
    // MusicTabSaver above round-trips the enum; the LaunchedEffect guard
    // below still runs after a restore (it is keyed on visibleTabs, which
    // is recomputed on re-creation) and resets to the first visible tab
    // if the restored tab was hidden in Settings meanwhile.
    var selectedTab by rememberSaveable(stateSaver = MusicTabSaver) {
        mutableStateOf(visibleTabs.firstOrNull() ?: MusicTab.TRACKS)
    }

    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            selectedTab = visibleTabs.firstOrNull() ?: MusicTab.TRACKS
        }
    }

    // #185: Back exits multi-select (exactly one step), like Local.
    BackHandler(enabled = isTrackSelectionMode || isAlbumSelectionMode || isArtistSelectionMode || isPlaylistSelectionMode || isFolderSelectionMode) {
        viewModel.clearAllSelections()
    }

    // #185: leaving a tab drops its selection — a selection must not
    // linger (and its action bar must not show) on the other tabs.
    LaunchedEffect(selectedTab) {
        viewModel.clearSelectionsExcept(selectedTab)
    }

    var showManageTabsDialog by remember { mutableStateOf(false) }
    // Issue #255: rememberSaveable, not remember — navigateToTab pops
    // this tab with saveState/restoreState, which restores only
    // back-stack-entry saved state, so a plain remember query reset to
    // "" on every tab switch. A String is saveable by default, so no
    // custom Saver is needed (unlike #175's MediaFolder).
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // #326: search lives in the top bar now (Local pattern) — the open
    // state is rememberSaveable like the query, so rotation or a tab
    // switch does not drop the field while a query is active.
    var isSearching by rememberSaveable { mutableStateOf(false) }

    // Migration Phase 2c: the query and the open state live in the
    // ViewModel, but their rememberSaveable copies stay here — the
    // ViewModel is cleared when this tab is popped with saveState, and
    // the query must survive a dock-tab switch (#255/#326).
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }
    LaunchedEffect(isSearching) { viewModel.setSearching(isSearching) }

    // #326: Back exits search (exactly one step), like Local — it closes
    // the field and clears the query. Guarded against selection mode:
    // search can still be open when a long-press starts a selection
    // (the top bar is replaced), and Back must exit the selection first.
    // (Placed after the isSearching/searchQuery declarations: a local
    // state var is only visible after its declaration.)
    BackHandler(enabled = isSearching && !isTrackSelectionMode && !isAlbumSelectionMode && !isArtistSelectionMode && !isPlaylistSelectionMode && !isFolderSelectionMode) {
        isSearching = false
        searchQuery = ""
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
    val sortedAllTracks by viewModel.sortedAllTracks.collectAsState()
    val filteredAlbums by viewModel.filteredAlbums.collectAsState()
    val sortedAlbums by viewModel.sortedAlbums.collectAsState()
    val sortedAllAlbums by viewModel.sortedAllAlbums.collectAsState()
    val filteredArtists by viewModel.filteredArtists.collectAsState()
    val sortedArtists by viewModel.sortedArtists.collectAsState()
    val sortedAllArtists by viewModel.sortedAllArtists.collectAsState()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsState()
    val sortedPlaylists by viewModel.sortedPlaylists.collectAsState()
    val sortedAllPlaylists by viewModel.sortedAllPlaylists.collectAsState()
    val filteredFolders by viewModel.filteredFolders.collectAsState()
    val sortedFolders by viewModel.sortedFolders.collectAsState()
    val sortedAllFolders by viewModel.sortedAllFolders.collectAsState()

    // Issue #234: resolve against the UNFILTERED sorted list — the search
    // box must not narrow an existing selection.
    fun selectedTracksInView(): List<AudioTrack> = viewModel.selectedTracksInView()

    fun playTrackSelection() {
        val selected = selectedTracksInView()
        if (selected.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(selected, startIndex = 0)
            onOpenPlayer()
            clearTrackSelection()
        }
    }

    fun addTrackSelectionToPlaylist() {
        val selected = selectedTracksInView()
        if (selected.isNotEmpty()) {
            tracksForPlaylistAdd = selected
            showAddToPlaylistDialog = true
        }
    }

    // Issue #321: single-track favourite toggle for the track heart.
    // Mirrors LocalScreen's toggleVideoFavourite (#235): the same
    // repository toggle, so the heart flips through the observed
    // favourites Flow — no manual refresh needed. The FavouriteItem
    // shape matches the selection favourite below (audio: isVideo
    // false, subtitle "artist • album").
    fun toggleTrackFavourite(track: AudioTrack) = viewModel.toggleTrackFavourite(track)

    fun toggleTrackSelectionFavourite() {
        val selected = selectedTracksInView()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                clearTrackSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // #185: Albums multi-select actions, mirroring the Tracks ones. An
    // album's tracks are its library tracks (getAudioAlbums groups by
    // (album, artist) — #243, so same-named albums by different artists
    // stay separate), in library order — no new repository call needed.
    fun albumTracks(album: AudioAlbum): List<AudioTrack> = viewModel.albumTracks(album)

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedAlbumsInView(): List<AudioAlbum> = viewModel.selectedAlbumsInView()

    fun selectedAlbumTracks(): List<AudioTrack> = viewModel.selectedAlbumTracks()

    fun playAlbumSelection() {
        val albumTrackList = selectedAlbumTracks()
        if (albumTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(albumTrackList, startIndex = 0)
            onOpenPlayer()
            clearAlbumSelection()
        }
    }

    fun addAlbumSelectionToPlaylist() {
        val albumTrackList = selectedAlbumTracks()
        if (albumTrackList.isNotEmpty()) {
            tracksForPlaylistAdd = albumTrackList
            showAddToPlaylistDialog = true
        }
    }

    fun toggleAlbumSelectionFavourite() {
        val selected = selectedAlbumTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                clearAlbumSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // #185: Artists multi-select actions, mirroring the Albums ones. An
    // artist's tracks are its library tracks (getAudioArtists groups by
    // artist name), in library order — no new repository call needed.
    fun artistTracks(artist: AudioArtist): List<AudioTrack> = viewModel.artistTracks(artist)

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedArtistsInView(): List<AudioArtist> = viewModel.selectedArtistsInView()

    fun selectedArtistTracks(): List<AudioTrack> = viewModel.selectedArtistTracks()

    fun playArtistSelection() {
        val artistTrackList = selectedArtistTracks()
        if (artistTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(artistTrackList, startIndex = 0)
            onOpenPlayer()
            clearArtistSelection()
        }
    }

    fun addArtistSelectionToPlaylist() {
        val artistTrackList = selectedArtistTracks()
        if (artistTrackList.isNotEmpty()) {
            tracksForPlaylistAdd = artistTrackList
            showAddToPlaylistDialog = true
        }
    }

    fun toggleArtistSelectionFavourite() {
        val selected = selectedArtistTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                clearArtistSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // #185: Playlists multi-select actions, mirroring the Albums/Artists
    // ones — except a playlist's tracks are NOT an in-memory filter.
    // A device playlist's members come from MediaStore
    // (repository.getPlaylistTracks, a blocking call — IO only, like the
    // single-tap path), and a custom playlist's items come from Room
    // (viewModel.getPlaylistItems, suspend). Both lookups
    // skip members that are no longer in the library, so the result is
    // exactly what tapping the playlist would queue. Two shapes are
    // needed because the two kinds of playlist play differently:
    // playlistAudioTracks() for Add-to-playlist / Favourite (which work
    // on library AudioTracks, like the album/artist actions), and
    // playlistPlayableMedia() for Play (which maps straight to the
    // queue's PlayableMedia — the same mapping the custom-playlist
    // details dialog's onPlayAll uses — so a custom playlist keeps its
    // video items and its own order instead of being forced through the
    // audio-only path).
    suspend fun playlistAudioTracks(playlist: Playlist): List<AudioTrack> =
        viewModel.playlistAudioTracks(playlist)

    suspend fun playlistPlayableMedia(playlist: Playlist): List<PlayableMedia> =
        viewModel.playlistPlayableMedia(playlist)

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedPlaylistsInView(): List<Playlist> = viewModel.selectedPlaylistsInView()

    fun playPlaylistSelection() {
        scope.launch(Dispatchers.IO) {
            val playable = selectedPlaylistsInView().flatMap { playlistPlayableMedia(it) }
            withContext(Dispatchers.Main) {
                if (playable.isNotEmpty()) {
                    playbackCoordinator?.playQueue(playable, startIndex = 0)
                    onOpenPlayer()
                    clearPlaylistSelection()
                } else {
                    // #207: same silent-tap problem as a single empty
                    // playlist tap — say so instead of doing nothing.
                    // The selection stays, so another action can be tried.
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
            }
        }
    }

    fun addPlaylistSelectionToPlaylist() {
        scope.launch(Dispatchers.IO) {
            val audioTracks = selectedPlaylistsInView().flatMap { playlistAudioTracks(it) }
            withContext(Dispatchers.Main) {
                if (audioTracks.isNotEmpty()) {
                    tracksForPlaylistAdd = audioTracks
                    showAddToPlaylistDialog = true
                } else {
                    // Only video items / vanished files: nothing an
                    // audio playlist could take. Keep the selection.
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
            }
        }
    }

    fun togglePlaylistSelectionFavourite() {
        scope.launch(Dispatchers.IO) {
            val selected = selectedPlaylistsInView().flatMap { playlistAudioTracks(it) }
            if (selected.isEmpty()) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
                return@launch
            }
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                clearPlaylistSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // #185: Folders multi-select actions, mirroring the Artists ones. A
    // folder's tracks are its library tracks (folderTracks groups by
    // parent dir, the same way getAudioFolders does), in library order —
    // no new repository call needed.
    fun folderTracks(folder: AudioFolder): List<AudioTrack> = viewModel.folderTracks(folder)

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedFoldersInView(): List<AudioFolder> = viewModel.selectedFoldersInView()

    fun selectedFolderTracks(): List<AudioTrack> = viewModel.selectedFolderTracks()

    fun playFolderSelection() {
        val folderTrackList = selectedFolderTracks()
        if (folderTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(folderTrackList, startIndex = 0)
            onOpenPlayer()
            clearFolderSelection()
        }
    }

    fun addFolderSelectionToPlaylist() {
        val folderTrackList = selectedFolderTracks()
        if (folderTrackList.isNotEmpty()) {
            tracksForPlaylistAdd = folderTrackList
            showAddToPlaylistDialog = true
        }
    }

    fun toggleFolderSelectionFavourite() {
        val selected = selectedFolderTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                clearFolderSelection()
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
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    // #326: Shuffle All (#64), shared by both top-bar states. Shuffles
    // the currently visible/filtered track list (sortedTracks is
    // search-filtered, then sorted — verified, the old button already
    // shuffled the right list) and starts playback from it via the
    // existing audio queue path, so next/previous follow shuffled order.
    // No-op on an empty list instead of opening an empty player; the
    // button is disabled then, instead of looking tappable and doing
    // nothing like the old filled button did.
    fun shuffleVisibleTracks() {
        if (sortedTracks.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(sortedTracks.shuffled(), startIndex = 0)
            onOpenPlayer()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isTrackSelectionMode || isAlbumSelectionMode || isArtistSelectionMode || isPlaylistSelectionMode || isFolderSelectionMode) {
                // #185: selection top bar — count + close. Back exits too.
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            clearTrackSelection()
                            clearAlbumSelection()
                            clearArtistSelection()
                            clearPlaylistSelection()
                            clearFolderSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_exit_selection),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(
                                R.string.multiselect_selected_count,
                                when {
                                    isPlaylistSelectionMode -> playlistSelectionCount
                                    isAlbumSelectionMode -> albumSelectionCount
                                    isArtistSelectionMode -> artistSelectionCount
                                    isFolderSelectionMode -> folderSelectionCount
                                    else -> trackSelectionCount
                                },
                            ),
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
            } else {
            TopAppBar(
                title = {
                    if (isSearching) {
                        // #326: Local folder-view pattern — the title slot
                        // becomes the search field while searching, so the
                        // field gets the full bar width instead of sharing
                        // a row with the shuffle button.
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    stringResource(R.string.music_search_placeholder),
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
                        Text(
                            text = stringResource(R.string.music_screen_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                            color = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
                actions = {
                    if (isSearching) {
                        // #326: while searching only Close + Shuffle stay —
                        // the field needs the width, and shuffle-the-matches
                        // is the action that matters mid-search.
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_search),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        IconButton(
                            onClick = { shuffleVisibleTracks() },
                            enabled = sortedTracks.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = stringResource(R.string.music_shuffle_all),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            updateViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(R.string.cd_toggle_view),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        IconButton(onClick = {
                            // #264: wire to the system equalizer like Me's
                            // Equalizer tool (mpv-only: no ExoPlayer audio
                            // session any more, so the system equalizer
                            // opens against the global output mix, session
                            // 0). On devices without a control panel, say
                            // so instead of pretending something was
                            // scheduled (the old snackbar lied).
                            if (!PipUtils.launchSystemEqualizer(context, 0)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.equalizer_dialog_msg))
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Equalizer,
                                contentDescription = stringResource(R.string.music_cd_equalizer),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        // #326: search and shuffle as quiet icon buttons, in
                        // the Local folder-view order (search, then the play
                        // action) before the overflow menu — no more big
                        // filled Shuffle pill eating the header row.
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        IconButton(
                            onClick = { shuffleVisibleTracks() },
                            enabled = sortedTracks.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = stringResource(R.string.music_shuffle_all),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.action_more),
                                    tint = MpxTheme.colors.textPrimary,
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
                                        showSortPopup = true
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
                                    text = { Text(stringResource(R.string.music_menu_manage_tabs), color = MpxTheme.colors.textPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Tab, contentDescription = null, tint = MpxTheme.colors.primary)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showManageTabsDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            if (isTrackSelectionMode || isAlbumSelectionMode || isArtistSelectionMode || isPlaylistSelectionMode || isFolderSelectionMode) {
                // #185: multi-select action bar — exactly the track-menu
                // actions: Play, Add to playlist, Favourite. No delete.
                // On Albums / Artists / Playlists / Folders the same three actions
                // run over the selected items' tracks.
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
                            onClick = {
                                when {
                                    isPlaylistSelectionMode -> playPlaylistSelection()
                                    isAlbumSelectionMode -> playAlbumSelection()
                                    isArtistSelectionMode -> playArtistSelection()
                                    isFolderSelectionMode -> playFolderSelection()
                                    else -> playTrackSelection()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectAction(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = stringResource(R.string.music_menu_add_to_playlist),
                            onClick = {
                                when {
                                    isPlaylistSelectionMode -> addPlaylistSelectionToPlaylist()
                                    isAlbumSelectionMode -> addAlbumSelectionToPlaylist()
                                    isArtistSelectionMode -> addArtistSelectionToPlaylist()
                                    isFolderSelectionMode -> addFolderSelectionToPlaylist()
                                    else -> addTrackSelectionToPlaylist()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectAction(
                            icon = Icons.Filled.Favorite,
                            label = stringResource(R.string.music_menu_toggle_favourite),
                            onClick = {
                                when {
                                    isPlaylistSelectionMode -> togglePlaylistSelectionFavourite()
                                    isAlbumSelectionMode -> toggleAlbumSelectionFavourite()
                                    isArtistSelectionMode -> toggleArtistSelectionFavourite()
                                    isFolderSelectionMode -> toggleFolderSelectionFavourite()
                                    else -> toggleTrackSelectionFavourite()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
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

            // #326: the old "Search Bar + Shuffle All Row" lived here — a
            // cramped search field squeezed next to a big filled Shuffle
            // pill. Both now live in the top bar (Local pattern): the
            // search icon expands a field in the title slot, shuffle is a
            // quiet icon button. The tab row moves up to fill the space.

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
                            // #185: pull to refresh, like Local's content.
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
                                    // Issue #321: the favourite heart.
                                    favourites = favourites,
                                    onToggleTrackFavourite = { toggleTrackFavourite(it) },
                                    onToggleTrackSelection = { toggleTrackSelection(it) },
                                    onTrackClick = { index ->
                                        playbackCoordinator?.playAudioQueue(sortedTracks, startIndex = index)
                                        onOpenPlayer()
                                    },
                                    onTrackOptionsClick = { track ->
                                        trackForOptions = track
                                    },
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
                            // #185: pull to refresh, like Local's content.
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
                                    onTogglePlaylistSelection = { togglePlaylistSelection(it) },
                                    favouritesCount = favourites.size,
                                    historyCount = historyTotal,
                                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                                    onFavouritesClick = { showFavouritesDialog = true },
                                    onHistoryClick = { showHistoryDialog = true },
                                    onPlaylistClick = { playlist ->
                                        // Issue #217: compare against the namespaced custom id —
                                        // a bare room id would also match a device playlist
                                        // with the same number, opening the wrong one.
                                        val custom = customPlaylists.find { customPlaylistId(it.id) == playlist.id }
                                        if (custom != null) {
                                            selectedCustomPlaylist = custom
                                            // #269: clear the old list at
                                            // once, so the dialog never shows
                                            // the previous playlist's items
                                            // under this title while loading.
                                            playlistItemsForDialog = emptyList()
                                            scope.launch {
                                                val items = viewModel.getPlaylistItems(custom.id)
                                                // #269: a second tap starts
                                                // a second load — only apply
                                                // this one if this playlist
                                                // is still selected, so the
                                                // slower load cannot win over
                                                // the newer tap and put the
                                                // wrong songs under the
                                                // dialog's title.
                                                if (selectedCustomPlaylist?.id == custom.id) {
                                                    playlistItemsForDialog = items
                                                }
                                            }
                                        } else {
                                            // Device (MediaStore) playlist: queue exactly its
                                            // member tracks, in playlist order. An empty
                                            // member list queues nothing instead of
                                            // falling back to the whole library.
                                            scope.launch(Dispatchers.IO) {
                                                val memberTracks = viewModel.playlistAudioTracks(playlist)
                                                withContext(Dispatchers.Main) {
                                                    if (memberTracks.isNotEmpty()) {
                                                        playbackCoordinator?.playAudioQueue(memberTracks, startIndex = 0)
                                                        onOpenPlayer()
                                                    } else {
                                                        // #207: an empty playlist tap used
                                                        // to do nothing at all — say so,
                                                        // instead of looking like a dead tap.
                                                        snackbarHostState.showSnackbar(context.getString(R.string.playlist_empty_title))
                                                    }
                                                }
                                            }
                                        }
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
                            // #185: pull to refresh, like Local's content.
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
                                    onToggleAlbumSelection = { toggleAlbumSelection(it) },
                                    onAlbumClick = { album ->
                                        // #200: play only this album's
                                        // tracks, from its first track —
                                        // never the whole library.
                                        val albumTrackList = albumTracks(album)
                                        if (albumTrackList.isNotEmpty()) {
                                            playbackCoordinator?.playAudioQueue(albumTrackList, startIndex = 0)
                                            onOpenPlayer()
                                        } else {
                                            // #207: same silent-tap problem as
                                            // playlists — say so instead of
                                            // doing nothing.
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                                            }
                                        }
                                    },
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
                            // #185: pull to refresh, like Local's content.
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
                                    onToggleArtistSelection = { toggleArtistSelection(it) },
                                    onArtistClick = { artist ->
                                        // #200: play only this artist's
                                        // tracks, from its first track —
                                        // never the whole library.
                                        val artistTrackList = artistTracks(artist)
                                        if (artistTrackList.isNotEmpty()) {
                                            playbackCoordinator?.playAudioQueue(artistTrackList, startIndex = 0)
                                            onOpenPlayer()
                                        } else {
                                            // #207: same silent-tap problem as
                                            // playlists — say so instead of
                                            // doing nothing.
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                                            }
                                        }
                                    },
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
                            // #185: pull to refresh, like Local's content.
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
                                    onToggleFolderSelection = { toggleFolderSelection(it) },
                                    onFolderClick = { folder ->
                                        // #200: play only this folder's tracks,
                                        // from its first track — never the
                                        // whole library.
                                        val folderTrackList = folderTracks(folder)
                                        if (folderTrackList.isNotEmpty()) {
                                            playbackCoordinator?.playAudioQueue(folderTrackList, startIndex = 0)
                                            onOpenPlayer()
                                        } else {
                                            // #207: same silent-tap problem as
                                            // playlists — say so instead of
                                            // doing nothing.
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                                            }
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

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            // Issue #310: cancelling creation must not lose the pending
            // videos — the single-track add dialog reappears on its own
            // (trackForPlaylistAdd is still set), and the multi-select
            // one is put back explicitly below.
            onDismiss = {
                showCreatePlaylistDialog = false
                if (tracksForPlaylistAdd.isNotEmpty()) showAddToPlaylistDialog = true
            },
            // #268: Music creates audio playlists; the fixed type is
            // passed through (no selector on this screen).
            onConfirm = { name, isVideo ->
                scope.launch {
                    // Issue #310: carry the pending add-to-playlist
                    // track(s) into the newly created playlist, with
                    // the same item mapping, order and feedback as
                    // adding to an existing playlist below. A plain
                    // create (no pending track) behaves as before.
                    val targetTrack = trackForPlaylistAdd
                    val selected = tracksForPlaylistAdd
                    try {
                        val newId = viewModel.createPlaylist(
                            name, isVideoPlaylist = isVideo
                        )
                        when {
                            targetTrack != null -> {
                                val rowId = viewModel.addItemToPlaylist(
                                    PlaylistItem(
                                        playlistId = newId,
                                        mediaUri = getPlayableUri(targetTrack),
                                        mediaId = targetTrack.id,
                                        title = targetTrack.title,
                                        subtitle = "${targetTrack.artist} • ${targetTrack.album}",
                                        isVideo = false,
                                    )
                                )
                                trackForPlaylistAdd = null
                                showCreatePlaylistDialog = false
                                if (rowId != -1L) {
                                    snackbarHostState.showSnackbar(context.getString(R.string.music_added_to_playlist_msg, name))
                                } else {
                                    snackbarHostState.showSnackbar(context.getString(R.string.music_already_in_playlist_msg, name))
                                }
                            }
                            selected.isNotEmpty() -> {
                                var addedCount = 0
                                selected.forEach { track ->
                                    val rowId = viewModel.addItemToPlaylist(
                                        PlaylistItem(
                                            playlistId = newId,
                                            mediaUri = track.path,
                                            mediaId = track.id,
                                            title = track.title,
                                            subtitle = "${track.artist} • ${track.album}",
                                            isVideo = false,
                                            durationMs = track.durationMs,
                                        )
                                    )
                                    if (rowId != -1L) addedCount++
                                }
                                tracksForPlaylistAdd = emptyList()
                                showAddToPlaylistDialog = false
                                clearTrackSelection()
                                clearAlbumSelection()
                                clearArtistSelection()
                                clearPlaylistSelection()
                                clearFolderSelection()
                                showCreatePlaylistDialog = false
                                when {
                                    addedCount == selected.size -> snackbarHostState.showSnackbar(
                                        context.getString(R.string.music_added_to_playlist_msg, name)
                                    )
                                    addedCount == 0 -> snackbarHostState.showSnackbar(
                                        context.getString(R.string.music_already_in_playlist_msg, name)
                                    )
                                    else -> snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.music_added_some_to_playlist_msg,
                                            addedCount, name, selected.size - addedCount,
                                        )
                                    )
                                }
                            }
                            else -> {
                                showCreatePlaylistDialog = false
                                snackbarHostState.showSnackbar(context.getString(R.string.music_playlist_created_msg, name))
                            }
                        }
                    } catch (_: Exception) {
                        // Creation failed — keep the pending track(s);
                        // the add dialog comes back (single-track on its
                        // own, multi-select explicitly) so the user can
                        // retry instead of silently losing the selection.
                        showCreatePlaylistDialog = false
                        if (selected.isNotEmpty()) showAddToPlaylistDialog = true
                    }
                }
            },
        )
    }

    // Issue #310: hidden while the create dialog is open — the pending
    // track is carried through creation instead of being dropped, and
    // comes back here if creation is cancelled or fails.
    if (trackForPlaylistAdd != null && !showCreatePlaylistDialog) {
        val targetTrack = trackForPlaylistAdd!!
        AddToPlaylistDialog(
            playlists = customPlaylists,
            mediaTitle = targetTrack.title,
            onDismiss = { trackForPlaylistAdd = null },
            onSelectPlaylist = { pl ->
                scope.launch {
                    val playableUri = getPlayableUri(targetTrack)
                    // Issue #230: -1 means the track is already in the
                    // playlist — say so instead of "added".
                    val rowId = viewModel.addItemToPlaylist(
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
                    if (rowId != -1L) {
                        snackbarHostState.showSnackbar(context.getString(R.string.music_added_to_playlist_msg, pl.name))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.music_already_in_playlist_msg, pl.name))
                    }
                }
            },
            onCreateNewPlaylist = {
                // Issue #310: keep trackForPlaylistAdd — the create
                // dialog's onConfirm adds it to the new playlist, and
                // the add dialog comes back if creation is cancelled.
                showCreatePlaylistDialog = true
            },
        )
    }

    // #185: multi-select "Add to playlist" — the whole selection, added
    // to an audio playlist (video playlists filtered out, mirroring
    // Local's video filter the other way).
    if (showAddToPlaylistDialog && tracksForPlaylistAdd.isNotEmpty()) {
        val selected = tracksForPlaylistAdd
        val audioPlaylists = customPlaylists.filter { !it.isVideoPlaylist }
        AddToPlaylistDialog(
            playlists = audioPlaylists,
            mediaTitle = if (selected.size == 1) selected.first().title
            else stringResource(R.string.playlist_items_count, selected.size),
            onDismiss = {
                showAddToPlaylistDialog = false
                tracksForPlaylistAdd = emptyList()
            },
            onSelectPlaylist = { pl ->
                scope.launch {
                    // Issue #230: addItemToPlaylist returns -1 for a track
                    // that is already in the playlist — count the real
                    // adds so the feedback tells the truth.
                    var addedCount = 0
                    selected.forEach { track ->
                        val rowId = viewModel.addItemToPlaylist(
                            PlaylistItem(
                                playlistId = pl.id,
                                mediaUri = track.path,
                                mediaId = track.id,
                                title = track.title,
                                subtitle = "${track.artist} • ${track.album}",
                                isVideo = false,
                                durationMs = track.durationMs,
                            )
                        )
                        if (rowId != -1L) addedCount++
                    }
                    showAddToPlaylistDialog = false
                    tracksForPlaylistAdd = emptyList()
                    clearTrackSelection()
                    clearAlbumSelection()
                    clearArtistSelection()
                    clearPlaylistSelection()
                    clearFolderSelection()
                    when {
                        addedCount == selected.size -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_added_to_playlist_msg, pl.name)
                        )
                        addedCount == 0 -> snackbarHostState.showSnackbar(
                            context.getString(R.string.music_already_in_playlist_msg, pl.name)
                        )
                        else -> snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.music_added_some_to_playlist_msg,
                                addedCount, pl.name, selected.size - addedCount,
                            )
                        )
                    }
                }
            },
            onCreateNewPlaylist = {
                // Issue #310: keep tracksForPlaylistAdd — the create
                // dialog's onConfirm adds them to the new playlist, and
                // its onDismiss puts this dialog back if creation is
                // cancelled, so the pending tracks are never dropped.
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            },
        )
    }

    if (trackForOptions != null) {
        val targetTrack = trackForOptions!!
        AlertDialog(
            onDismissRequest = { trackForOptions = null },
            containerColor = MpxTheme.colors.black,
            title = {
                Text(
                    text = targetTrack.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Text(
                        text = "${targetTrack.artist} • ${targetTrack.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                trackForOptions = null
                                // Issue #244: play the visible list — the
                                // filtered, sorted queue row tap plays — not
                                // the whole unsorted library.
                                val idx = sortedTracks.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
                                playbackCoordinator?.playAudioQueue(sortedTracks, startIndex = idx)
                                onOpenPlayer()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_play_track), color = MpxTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                trackForOptions = null
                                scope.launch {
                                    val uri = getPlayableUri(targetTrack)
                                    val added = viewModel.toggleFavourite(
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
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_toggle_favourite), color = MpxTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable {
                                trackForPlaylistAdd = targetTrack
                                trackForOptions = null
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxTheme.colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.music_menu_add_to_playlist), color = MpxTheme.colors.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForOptions = null }) {
                    Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
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
                    playbackCoordinator?.playQueue(playable, startIndex = 0)
                    selectedCustomPlaylist = null
                    onOpenPlayer()
                }
            },
            onPlayItem = { item ->
                playbackCoordinator?.playQueue(
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
                    viewModel.removeItemFromPlaylist(itemId)
                    playlistItemsForDialog = viewModel.getPlaylistItems(pl.id)
                }
            },
            onDeletePlaylist = {
                scope.launch {
                    viewModel.deletePlaylist(pl.id)
                    selectedCustomPlaylist = null
                    snackbarHostState.showSnackbar(context.getString(R.string.music_deleted_playlist_msg, pl.name))
                }
            },
            onRenamePlaylist = { newName ->
                scope.launch {
                    viewModel.renamePlaylist(pl.id, newName)
                    // Issue #230: the dialog reads its title from this
                    // snapshot — update it so the new name shows at once.
                    // The Playlists tab refreshes itself via the
                    // observePlaylists Flow.
                    selectedCustomPlaylist = pl.copy(name = newName)
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
                playbackCoordinator?.playQueue(
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
                    viewModel.removeFavourite(uri)
                    snackbarHostState.showSnackbar(context.getString(R.string.music_removed_from_favourites))
                }
            },
            onClearAll = {
                scope.launch {
                    viewModel.clearFavourites()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_favourites_msg))
                }
            },
        )
    }

    if (showHistoryDialog) {
        PlaybackHistoryDialog(
            history = history,
            historyTotal = historyTotal,
            // #265: same paging as the full History page — the dialog
            // loads the next page when scrolled near the end.
            onLoadMore = {
                if (history.size < historyTotal) historyLimit += HISTORY_PAGE_SIZE
            },
            playbackCoordinator = playbackCoordinator,
            onDismiss = { showHistoryDialog = false },
            onItemClick = { item ->
                showHistoryDialog = false
                playbackCoordinator?.playQueue(
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
                    viewModel.clearHistory()
                    // Issue #353: clearing history must also forget every
                    // saved resume position, or videos keep offering to
                    // resume from the old position (and the "Resumed
                    // from" banner still appears). Prefer the resume
                    // manager's clearAll() — it clears the in-memory map,
                    // the throttle state, and the Room table. With no
                    // manager in scope there is no in-memory state, so
                    // clear the Room table directly instead.
                    playbackCoordinator?.resumeManager?.clearAll()
                        ?: viewModel.clearAllResumePositions()
                    snackbarHostState.showSnackbar(context.getString(R.string.music_cleared_history_msg))
                }
            },
        )
    }

    // #185: the shared #156 Sort & View popup, like Local. It offers
    // Title / Date / Size; for audio, Size sorts by duration (the
    // comparator maps both to durationMs). Playlists carry no duration:
    // SIZE / DURATION sort by item count instead. Tree mode, grid
    // columns and Fields are Local-only and hidden here. The old
    // SortViewDialog's separate-view-layout toggle is not in this popup
    // (it lives in Settings, as it does for Local); the full field set
    // stays out — the popup is the quick picker, not the full sort dialog.
    if (showSortPopup) {
        LocalSortPopup(
            isFolderScope = false,
            currentSort = sortField,
            onSortChange = { newSort -> viewModel.setSortField(newSort) },
            sortReverse = appSettings.sortReverse,
            onSortReverseChange = { reversed -> viewModel.setSortReverse(reversed) },
            currentView = viewMode,
            onViewChange = { updateViewMode(it) },
            isTreeMode = false,
            onTreeModeChange = {},
            folderGridColumns = 2,
            onFolderGridColumnsChange = {},
            videoGridColumns = 2,
            onVideoGridColumnsChange = {},
            showFullName = false,
            onShowFullNameChange = {},
            showFieldLocation = false,
            onShowFieldLocationChange = {},
            showFieldCount = false,
            onShowFieldCountChange = {},
            showFieldDuration = false,
            onShowFieldDurationChange = {},
            showFieldSize = false,
            onShowFieldSizeChange = {},
            showFieldDate = false,
            onShowFieldDateChange = {},
            showTreeMode = false,
            showGridColumns = false,
            showFieldsSection = false,
            onDismiss = { showSortPopup = false },
        )
    }

    if (showManageTabsDialog) {
        ManageMusicTabsDialog(
            settings = appSettings,
            onDismiss = { showManageTabsDialog = false },
            onToggleTab = { tabId, currentVal ->
                if (!viewModel.toggleMusicTab(tabId, currentVal)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_tab_min_one_required))
                    }
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
        containerColor = MpxTheme.colors.black,
        title = {
            Text(
                text = stringResource(R.string.music_dialog_tabs_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.music_dialog_tabs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
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
                Text(stringResource(R.string.action_done), color = MpxTheme.colors.primary)
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
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}

private fun getPlayableUri(track: AudioTrack): String = track.path

@Composable
private fun TracksTabContent(
    tracks: List<AudioTrack>,
    viewMode: ViewMode = ViewMode.LIST,
    showThumbnails: Boolean = true,
    selectedTrackIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    // Issue #321: the favourite heart state + toggle for track rows
    // and grid cards (audio had no heart in either layout).
    favourites: List<FavouriteItem> = emptyList(),
    onToggleTrackFavourite: (AudioTrack) -> Unit = {},
    onToggleTrackSelection: (AudioTrack) -> Unit = {},
    onTrackClick: (Int) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
) {
    // Issue #360: the favourite URIs as a Set, computed once per
    // favourites change — the rows below used to run
    // favourites.any { it.mediaUri == track.path } per row, a full
    // list scan on every row composition while scrolling.
    val favouriteUris = remember(favourites) { favourites.map { it.mediaUri }.toSet() }
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
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackRow(
                        track = track,
                        // Issue #321: the heart, wired to the same
                        // favourites Flow + toggle as the video rows.
                        isFavourite = track.path in favouriteUris,
                        onToggleFavourite = { onToggleTrackFavourite(track) },
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleTrackSelection(track) },
                        onOptionsClick = { onTrackOptionsClick(track) },
                        onClick = {
                            if (isSelectionMode) onToggleTrackSelection(track)
                            else onTrackClick(index)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
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
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackGridCard(
                        track = track,
                        // Issue #321: the heart — the audio grid card
                        // had none either.
                        isFavourite = track.path in favouriteUris,
                        onToggleFavourite = { onToggleTrackFavourite(track) },
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleTrackSelection(track) },
                        onOptionsClick = { onTrackOptionsClick(track) },
                        onClick = {
                            if (isSelectionMode) onToggleTrackSelection(track)
                            else onTrackClick(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<Playlist>,
    viewMode: ViewMode = ViewMode.LIST,
    selectedPlaylistIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onTogglePlaylistSelection: (Playlist) -> Unit = {},
    favouritesCount: Int,
    historyCount: Int,
    onCreatePlaylist: () -> Unit,
    onFavouritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    // #209: the count must match what the list / grid actually emits:
    // 3 header rows + the playlist rows + the empty-state item when the
    // list is empty (the old `playlists.size + 3` missed the empty item).
    // List and grid emit the same number of items, so one count serves
    // both scrollbars.
    val playlistItemCount = playlists.size + 3 + if (playlists.isEmpty()) 1 else 0
    if (viewMode == ViewMode.LIST) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item(key = "new_playlist_header", contentType = "new_playlist_action") {
                    NewPlaylistActionRow(onCreatePlaylist)
                }
                item(key = "favourites_row", contentType = "favourites_action") {
                    FavouritesActionRow(favouritesCount, onFavouritesClick)
                }
                item(key = "history_row", contentType = "history_action") {
                    HistoryActionRow(historyCount, onHistoryClick)
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_playlists", contentType = "empty_state") {
                        EmptyPlaylistsState()
                    }
                }
                items(items = playlists, key = { it.id.ifBlank { it.title } }, contentType = { "playlist_row" }) { playlist ->
                    val isSelected = selectedPlaylistIds.contains(playlist.id.ifBlank { playlist.title })
                    AudioPlaylistRow(
                        playlist = playlist,
                        isSelected = isSelected,
                        onLongClick = { onTogglePlaylistSelection(playlist) },
                        onClick = {
                            if (isSelectionMode) onTogglePlaylistSelection(playlist)
                            else onPlaylistClick(playlist)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
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
                // #185: the 3 header rows are actions, not playlists —
                // they stay visible in grid mode as full-span items.
                item(key = "new_playlist_header", contentType = "new_playlist_action", span = { GridItemSpan(maxLineSpan) }) {
                    NewPlaylistActionRow(onCreatePlaylist)
                }
                item(key = "favourites_row", contentType = "favourites_action", span = { GridItemSpan(maxLineSpan) }) {
                    FavouritesActionRow(favouritesCount, onFavouritesClick)
                }
                item(key = "history_row", contentType = "history_action", span = { GridItemSpan(maxLineSpan) }) {
                    HistoryActionRow(historyCount, onHistoryClick)
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_playlists", contentType = "empty_state", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyPlaylistsState()
                    }
                }
                items(
                    items = playlists,
                    key = { playlist -> playlist.id.ifBlank { playlist.title } },
                    contentType = { "playlist_grid_card" },
                ) { playlist ->
                    val isSelected = selectedPlaylistIds.contains(playlist.id.ifBlank { playlist.title })
                    AudioPlaylistGridCard(
                        playlist = playlist,
                        isSelected = isSelected,
                        onLongClick = { onTogglePlaylistSelection(playlist) },
                        onClick = {
                            if (isSelectionMode) onTogglePlaylistSelection(playlist)
                            else onPlaylistClick(playlist)
                        },
                    )
                }
            }
        }
    }
}

// #185: the Playlists tab's 3 header rows are actions, not playlists —
// extracted so the list and grid branches share them (the grid shows
// them as full-span items). Each row keeps the divider it had inline.
@Composable
private fun NewPlaylistActionRow(onCreatePlaylist: () -> Unit) {
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
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = stringResource(R.string.music_new_playlist_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MpxTheme.colors.primary))
            Text(text = stringResource(R.string.music_new_playlist_subtitle), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun FavouritesActionRow(favouritesCount: Int, onFavouritesClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onFavouritesClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp).clip(MpxShapes.small).background(MpxTheme.colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.music_favourites_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTheme.colors.textPrimary)
            Text(text = stringResource(R.string.music_songs_count, favouritesCount), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun HistoryActionRow(historyCount: Int, onHistoryClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onHistoryClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp).clip(MpxShapes.small).background(MpxTheme.colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MpxTheme.colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.music_recently_played_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MpxTheme.colors.textPrimary)
            Text(text = stringResource(R.string.music_songs_count, historyCount), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
}

@Composable
private fun EmptyPlaylistsState() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.music_empty_playlists_title), style = MaterialTheme.typography.titleMedium, color = MpxTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = stringResource(R.string.music_empty_playlists_desc), style = MaterialTheme.typography.bodySmall, color = MpxTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AudioAlbum>,
    viewMode: ViewMode = ViewMode.GRID,
    showThumbnails: Boolean = true,
    selectedAlbumIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleAlbumSelection: (AudioAlbum) -> Unit = {},
    onAlbumClick: (AudioAlbum) -> Unit,
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
                    items = albums,
                    key = { album -> album.id.ifBlank { album.title } },
                    contentType = { "album_row" },
                ) { album ->
                    val isSelected = selectedAlbumIds.contains(album.id.ifBlank { album.title })
                    AudioAlbumRow(
                        album = album,
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleAlbumSelection(album) },
                        onClick = {
                            if (isSelectionMode) onToggleAlbumSelection(album)
                            else onAlbumClick(album)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
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
                    items = albums,
                    key = { album -> album.id.ifBlank { album.title } },
                    contentType = { "album_card" },
                ) { album ->
                    val isSelected = selectedAlbumIds.contains(album.id.ifBlank { album.title })
                    AudioAlbumGridCard(
                        album = album,
                        showThumbnails = showThumbnails,
                        isSelected = isSelected,
                        onLongClick = { onToggleAlbumSelection(album) },
                        onClick = {
                            if (isSelectionMode) onToggleAlbumSelection(album)
                            else onAlbumClick(album)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<AudioArtist>,
    viewMode: ViewMode = ViewMode.LIST,
    selectedArtistIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleArtistSelection: (AudioArtist) -> Unit = {},
    onArtistClick: (AudioArtist) -> Unit,
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
                    items = artists,
                    key = { artist -> artist.id.ifBlank { artist.name } },
                    contentType = { "artist_row" },
                ) { artist ->
                    val isSelected = selectedArtistIds.contains(artist.id.ifBlank { artist.name })
                    AudioArtistRow(
                        artist = artist,
                        isSelected = isSelected,
                        onLongClick = { onToggleArtistSelection(artist) },
                        onClick = {
                            if (isSelectionMode) onToggleArtistSelection(artist)
                            else onArtistClick(artist)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
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
                    items = artists,
                    key = { artist -> artist.id.ifBlank { artist.name } },
                    contentType = { "artist_card" },
                ) { artist ->
                    val isSelected = selectedArtistIds.contains(artist.id.ifBlank { artist.name })
                    AudioArtistGridCard(
                        artist = artist,
                        isSelected = isSelected,
                        onLongClick = { onToggleArtistSelection(artist) },
                        onClick = {
                            if (isSelectionMode) onToggleArtistSelection(artist)
                            else onArtistClick(artist)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<AudioFolder>,
    viewMode: ViewMode = ViewMode.LIST,
    selectedFolderIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleFolderSelection: (AudioFolder) -> Unit = {},
    onFolderClick: (AudioFolder) -> Unit,
) {
    // #185: the view toggle was already honoured (the old tab had both
    // branches); both now use the shared AudioFolderRow / GridCard.
    // Scrollbar counts: each branch emits exactly one lazy item per
    // folder (the list divider lives inside the item lambda, not as its
    // own item), so itemCount = folders.size in both branches. The tab
    // branch never composes this content with an empty list — the empty
    // and no-search-matches states are EmptyStateViews outside it — so
    // there is no empty-state item to count, unlike Playlists (#209).
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
                    key = { folder -> folder.id.ifBlank { folder.path } },
                    contentType = { "audio_folder_row" },
                ) { folder ->
                    val isSelected = selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                    AudioFolderRow(
                        folder = folder,
                        isSelected = isSelected,
                        onLongClick = { onToggleFolderSelection(folder) },
                        onClick = {
                            if (isSelectionMode) onToggleFolderSelection(folder)
                            else onFolderClick(folder)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )
                }
            }
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
                    key = { folder -> folder.id.ifBlank { folder.path } },
                    contentType = { "audio_folder_card" },
                ) { folder ->
                    val isSelected = selectedFolderIds.contains(folder.id.ifBlank { folder.path })
                    AudioFolderGridCard(
                        folder = folder,
                        isSelected = isSelected,
                        onLongClick = { onToggleFolderSelection(folder) },
                        onClick = {
                            if (isSelectionMode) onToggleFolderSelection(folder)
                            else onFolderClick(folder)
                        },
                    )
                }
            }
        }
    }
}
