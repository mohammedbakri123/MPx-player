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
import com.mpx.player.ui.components.FastScrollbar
import com.mpx.player.ui.components.GridFastScrollbar
import java.io.File
import java.util.Locale
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
import com.mpx.player.domain.model.customPlaylistId
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
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
import com.mpx.player.ui.screens.local.MediaSortComparator
import com.mpx.player.ui.screens.local.MultiSelectAction
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.screens.local.isPathBlacklisted
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

// Issue #300: the four audio-library lists after the folder blacklist is
// applied (see applyAudioBlacklist in MusicScreen).
private data class FilteredAudioLibrary(
    val tracks: List<AudioTrack>,
    val albums: List<AudioAlbum>,
    val artists: List<AudioArtist>,
    val folders: List<AudioFolder>,
)

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
    // #185: Tracks sort uses the shared MediaSortComparator + the #156
    // Sort & View popup, like Local. Persisted in
    // AppSettings.musicPrimarySortField; direction is the shared global
    // sortReverse, same as Local's popup.
    var sortField by remember {
        mutableStateOf(
            try {
                SortField.valueOf(appSettings.musicPrimarySortField)
            } catch (_: Exception) {
                SortField.NAME
            }
        )
    }
    var showSortPopup by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // #185: long-press multi-select on the Tracks tab, mirroring Local's
    // #144. Keyed like Local (id, falling back to path). Long-press
    // toggles an item (entering selection mode when empty); a tap while
    // selecting toggles too. Back / the X exits and clears.
    var selectedTrackIds by remember { mutableStateOf(setOf<String>()) }
    val isTrackSelectionMode = selectedTrackIds.isNotEmpty()
    val trackSelectionCount = selectedTrackIds.size

    fun clearTrackSelection() {
        selectedTrackIds = emptySet()
    }

    fun toggleTrackSelection(track: AudioTrack) {
        val key = track.id.ifBlank { track.path }
        selectedTrackIds =
            if (selectedTrackIds.contains(key)) selectedTrackIds - key
            else selectedTrackIds + key
    }

    // #185: long-press multi-select on the Albums tab, mirroring the
    // Tracks slice / Local's #144. Keyed like Tracks (id, falling back
    // to title). Long-press toggles an item (entering selection mode
    // when empty); a tap while selecting toggles too. Back / the X exits
    // and clears.
    var selectedAlbumIds by remember { mutableStateOf(setOf<String>()) }
    val isAlbumSelectionMode = selectedAlbumIds.isNotEmpty()
    val albumSelectionCount = selectedAlbumIds.size

    fun clearAlbumSelection() {
        selectedAlbumIds = emptySet()
    }

    fun toggleAlbumSelection(album: AudioAlbum) {
        val key = album.id.ifBlank { album.title }
        selectedAlbumIds =
            if (selectedAlbumIds.contains(key)) selectedAlbumIds - key
            else selectedAlbumIds + key
    }

    // #185: long-press multi-select on the Artists tab, mirroring the
    // Tracks/Albums slices / Local's #144. Keyed like Albums (id,
    // falling back to name). Long-press toggles an item (entering
    // selection mode when empty); a tap while selecting toggles too.
    // Back / the X exits and clears.
    var selectedArtistIds by remember { mutableStateOf(setOf<String>()) }
    val isArtistSelectionMode = selectedArtistIds.isNotEmpty()
    val artistSelectionCount = selectedArtistIds.size

    fun clearArtistSelection() {
        selectedArtistIds = emptySet()
    }

    fun toggleArtistSelection(artist: AudioArtist) {
        val key = artist.id.ifBlank { artist.name }
        selectedArtistIds =
            if (selectedArtistIds.contains(key)) selectedArtistIds - key
            else selectedArtistIds + key
    }

    // #185: long-press multi-select on the Playlists tab, mirroring the
    // Albums/Artists slices / Local's #144. Keyed like Albums (id,
    // falling back to title). Long-press toggles an item (entering
    // selection mode when empty); a tap while selecting toggles too.
    // Back / the X exits and clears.
    var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    val isPlaylistSelectionMode = selectedPlaylistIds.isNotEmpty()
    val playlistSelectionCount = selectedPlaylistIds.size

    fun clearPlaylistSelection() {
        selectedPlaylistIds = emptySet()
    }

    fun togglePlaylistSelection(playlist: Playlist) {
        val key = playlist.id.ifBlank { playlist.title }
        selectedPlaylistIds =
            if (selectedPlaylistIds.contains(key)) selectedPlaylistIds - key
            else selectedPlaylistIds + key
    }

    // #185: long-press multi-select on the Folders tab, mirroring the
    // Artists slice / Local's #144. Keyed like Artists (id, falling back
    // to path). Long-press toggles an item (entering selection mode when
    // empty); a tap while selecting toggles too. Back / the X exits and
    // clears.
    var selectedFolderIds by remember { mutableStateOf(setOf<String>()) }
    val isFolderSelectionMode = selectedFolderIds.isNotEmpty()
    val folderSelectionCount = selectedFolderIds.size

    fun clearFolderSelection() {
        selectedFolderIds = emptySet()
    }

    fun toggleFolderSelection(folder: AudioFolder) {
        val key = folder.id.ifBlank { folder.path }
        selectedFolderIds =
            if (selectedFolderIds.contains(key)) selectedFolderIds - key
            else selectedFolderIds + key
    }

    fun updateViewMode(newMode: ViewMode) {
        viewMode = newMode
        val updated = if (appSettings.separateViewLayout) {
            appSettings.copy(musicViewMode = newMode.name)
        } else {
            appSettings.copy(musicViewMode = newMode.name, localViewMode = newMode.name)
        }
        // Issue #191: layout save — commit() so a swipe-away right after
        // the tap cannot lose the view-mode choice.
        realSettingsRepo.save(updated, commit = true)
        appSettings = updated
    }

    val persistenceRepository = remember(context) {
        playbackManager?.persistenceRepository
            ?: RoomMediaPersistenceRepository(MpxDatabase.getInstance(context))
    }
    val favourites by persistenceRepository.observeFavourites().collectAsState(initial = emptyList())
    // #265: the dialog list is paged (HISTORY_PAGE_SIZE rows at a time)
    // and the counts show the real total. The old code rendered
    // history.size from the 100-row-capped observer, freezing the Music
    // history count and the dialog title at 100 and hiding older rows.
    // The limit is rememberSaveable so rotation does not drop rows the
    // user already loaded in the dialog.
    val historyTotal by persistenceRepository.observeHistoryCount().collectAsState(initial = 0)
    var historyLimit by rememberSaveable { mutableStateOf(HISTORY_PAGE_SIZE) }
    val history by persistenceRepository.observeHistory(limit = historyLimit).collectAsState(initial = emptyList())
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())
    val allPlaylists = remember(customPlaylists, playlists) {
        customPlaylists.map { it.toPlaylist() } + playlists
    }

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
        clearTrackSelection()
        clearAlbumSelection()
        clearArtistSelection()
        clearPlaylistSelection()
        clearFolderSelection()
    }

    // #185: leaving a tab drops its selection — a selection must not
    // linger (and its action bar must not show) on the other tabs.
    LaunchedEffect(selectedTab) {
        if (selectedTab != MusicTab.TRACKS) clearTrackSelection()
        if (selectedTab != MusicTab.ALBUMS) clearAlbumSelection()
        if (selectedTab != MusicTab.ARTISTS) clearArtistSelection()
        if (selectedTab != MusicTab.PLAYLISTS) clearPlaylistSelection()
        if (selectedTab != MusicTab.FOLDERS) clearFolderSelection()
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

    // Issue #300: a track's folder, derived exactly the way
    // MediaStoreRepository.getAudioFolders() and folderTracks() group it
    // (parent dir; blank and content:// paths fall back to
    // /storage/emulated/0/Music, #253/#267), so a track and its folder
    // tile are hidden together by the blacklist below.
    fun audioFolderPath(track: AudioTrack): String =
        if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
            File(track.path).parent ?: "/storage/emulated/0/Music"
        } else {
            "/storage/emulated/0/Music"
        }

    // Issue #300: mirror LocalScreen.applyFolderBlacklist for the audio
    // library — the folder blacklist (Preferences > Folders) hides
    // blacklisted folders' audio from Music too, with the same prefix
    // rule ([isPathBlacklisted]). Tracks and folders filter directly on
    // their folder path; albums and artists derive from tracks, so they
    // keep only rows with at least one visible track, with counts
    // recomputed from the visible tracks (a stale count would be #275
    // all over again). Playlists are deliberately NOT filtered: their
    // members are explicit user choices, resolved by the repository when
    // played. Runs on the IO thread at the load sites, like Local's.
    fun applyAudioBlacklist(
        scannedTracks: List<AudioTrack>,
        scannedAlbums: List<AudioAlbum>,
        scannedArtists: List<AudioArtist>,
        scannedFolders: List<AudioFolder>,
        blacklist: Set<String>,
    ): FilteredAudioLibrary {
        if (blacklist.isEmpty()) {
            return FilteredAudioLibrary(scannedTracks, scannedAlbums, scannedArtists, scannedFolders)
        }
        val visibleTracks = scannedTracks.filter { !isPathBlacklisted(audioFolderPath(it), blacklist) }
        val visibleFolders = scannedFolders.filter { !isPathBlacklisted(it.path, blacklist) }
        val visibleAlbums = scannedAlbums.mapNotNull { album ->
            val count = visibleTracks.count { it.album == album.title && it.artist == album.artist }
            if (count > 0) album.copy(trackCount = count) else null
        }
        val visibleArtists = scannedArtists.mapNotNull { artist ->
            val artistTracks = visibleTracks.filter { it.artist == artist.name }
            if (artistTracks.isNotEmpty()) {
                artist.copy(
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.map { it.album }.distinct().size,
                )
            } else {
                null
            }
        }
        return FilteredAudioLibrary(visibleTracks, visibleAlbums, visibleArtists, visibleFolders)
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
                val t = repository.getAudioTracks()
                val p = repository.getPlaylists()
                val al = repository.getAudioAlbums()
                val ar = repository.getAudioArtists()
                val f = repository.getAudioFolders()
                // Issue #300: folder blacklist hides blacklisted audio too.
                val visible = applyAudioBlacklist(t, al, ar, f, appSettings.blacklistedFolders)
                withContext(Dispatchers.Main) {
                    tracks = visible.tracks
                    playlists = p
                    albums = visible.albums
                    artists = visible.artists
                    folders = visible.folders
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
                // Issue #300: folder blacklist hides blacklisted audio too.
                val visible = applyAudioBlacklist(t, al, ar, f, appSettings.blacklistedFolders)
                withContext(Dispatchers.Main) {
                    tracks = visible.tracks
                    playlists = p
                    albums = visible.albums
                    artists = visible.artists
                    folders = visible.folders
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
                        // Issue #300: folder blacklist hides blacklisted
                        // audio too — use the freshly reloaded settings,
                        // so a blacklist change in Preferences applies on
                        // return (this is the ON_RESUME reload).
                        val visible = applyAudioBlacklist(t, al, ar, f, loadedSettings.blacklistedFolders)
                        withContext(Dispatchers.Main) {
                            tracks = visible.tracks
                            playlists = p
                            albums = visible.albums
                            artists = visible.artists
                            folders = visible.folders
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

    // #185: the shared MediaSortComparator (natural numeric order,
    // secondary tie-break) replaces the hand-rolled when() on SortField.
    // Reverse is the shared global sortReverse, like Local's popup.
    // With reverse off the order is identical to the old hand-rolled one
    // (Name A-Z, Date newest first, Size/Duration longest first).
    val sortedTracks = remember(filteredTracks, sortField, appSettings.sortReverse) {
        filteredTracks.sortedWith(
            MediaSortComparator.audioTrackComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: the same sort over the UNFILTERED list — the selection
    // resolvers below must not lose items the search box hides. The
    // visible list stays sortedTracks (search-filtered, then sorted).
    val sortedAllTracks = remember(tracks, sortField, appSettings.sortReverse) {
        tracks.sortedWith(
            MediaSortComparator.audioTrackComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // #185: the Albums tab shares the screen search box (contains-filter
    // on title/artist, like Tracks) and the shared sort state — one
    // sortField / sortReverse for the whole Music screen, like Local.
    // There is no separate albums sort: the popup edits the same field.
    val filteredAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) {
            albums
        } else {
            albums.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // #185: the shared MediaSortComparator for albums — same field
    // semantics as the audio track comparator. Albums carry no duration:
    // SIZE / DURATION sort by track count (most tracks first).
    val sortedAlbums = remember(filteredAlbums, sortField, appSettings.sortReverse) {
        filteredAlbums.sortedWith(
            MediaSortComparator.audioAlbumComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: the same sort over the UNFILTERED list — see sortedAllTracks.
    val sortedAllAlbums = remember(albums, sortField, appSettings.sortReverse) {
        albums.sortedWith(
            MediaSortComparator.audioAlbumComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // #185: the Artists tab shares the screen search box (contains-filter
    // on name, like Tracks/Albums) and the shared sort state — one
    // sortField / sortReverse for the whole Music screen, like Local.
    // There is no separate artists sort: the popup edits the same field.
    val filteredArtists = remember(artists, searchQuery) {
        if (searchQuery.isBlank()) {
            artists
        } else {
            artists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // #185: the shared MediaSortComparator for artists — same field
    // semantics as the audio album comparator. Artists carry no duration:
    // SIZE / DURATION sort by track count (most tracks first).
    val sortedArtists = remember(filteredArtists, sortField, appSettings.sortReverse) {
        filteredArtists.sortedWith(
            MediaSortComparator.audioArtistComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: the same sort over the UNFILTERED list — see sortedAllTracks.
    val sortedAllArtists = remember(artists, sortField, appSettings.sortReverse) {
        artists.sortedWith(
            MediaSortComparator.audioArtistComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // #185: the Playlists tab shares the screen search box
    // (contains-filter on title, like Tracks/Albums/Artists) and the
    // shared sort state — one sortField / sortReverse for the whole
    // Music screen, like Local. There is no separate playlists sort:
    // the popup edits the same field. The list filtered here is the
    // combined one (custom playlists first, then device playlists);
    // the 3 header rows are not filtered — they are actions, not rows.
    val filteredPlaylists = remember(allPlaylists, searchQuery) {
        if (searchQuery.isBlank()) {
            allPlaylists
        } else {
            allPlaylists.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // #185: the Folders tab shares the screen search box (contains-filter
    // on name or path — the path distinguishes same-named folders on
    // different volumes) and the shared sort state — one sortField /
    // sortReverse for the whole Music screen, like Local. There is no
    // separate folders sort: the popup edits the same field.
    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) {
            folders
        } else {
            folders.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.path.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // #185: the shared MediaSortComparator for playlists — same field
    // semantics as the audio album comparator. Playlists carry no
    // duration: SIZE / DURATION / COUNT sort by item count (most items
    // first); DATE is id recency, like albums/artists.
    val sortedPlaylists = remember(filteredPlaylists, sortField, appSettings.sortReverse) {
        filteredPlaylists.sortedWith(
            MediaSortComparator.audioPlaylistComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: the same sort over the UNFILTERED list — see sortedAllTracks.
    val sortedAllPlaylists = remember(allPlaylists, sortField, appSettings.sortReverse) {
        allPlaylists.sortedWith(
            MediaSortComparator.audioPlaylistComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // #185: the shared MediaSortComparator for folders — same field
    // semantics as the audio artist comparator. Folders carry no date,
    // size or duration: SIZE / DURATION / COUNT sort by track count
    // (most tracks first); DATE has nothing to sort by (folder ids are
    // path hashes, not recency) and falls through to the name tie-break.
    val sortedFolders = remember(filteredFolders, sortField, appSettings.sortReverse) {
        filteredFolders.sortedWith(
            MediaSortComparator.audioFolderComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: the same sort over the UNFILTERED list — see sortedAllTracks.
    val sortedAllFolders = remember(folders, sortField, appSettings.sortReverse) {
        folders.sortedWith(
            MediaSortComparator.audioFolderComparator(
                primary = sortField,
                secondary = SortField.NAME,
                reverse = appSettings.sortReverse,
            )
        )
    }

    // Issue #234: resolve against the UNFILTERED sorted list — the search
    // box must not narrow an existing selection.
    fun selectedTracksInView(): List<AudioTrack> =
        sortedAllTracks.filter { selectedTrackIds.contains(it.id.ifBlank { it.path }) }

    fun playTrackSelection() {
        val selected = selectedTracksInView()
        if (selected.isNotEmpty()) {
            playbackManager?.playAudioQueue(selected, startIndex = 0)
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

    fun toggleTrackSelectionFavourite() {
        val selected = selectedTracksInView()
        if (selected.isEmpty()) return
        val favUris = favourites.map { it.mediaUri }.toSet()
        scope.launch(Dispatchers.IO) {
            // Toggle as a group: if every selected track is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = selected.all { favUris.contains(it.path) }
            if (allFav) {
                selected.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                selected.filter { !favUris.contains(it.path) }.forEach { track ->
                    persistenceRepository.addFavourite(
                        FavouriteItem(
                            mediaId = track.id,
                            title = track.title,
                            subtitle = "${track.artist} • ${track.album}",
                            mediaUri = track.path,
                            isVideo = false,
                            durationMs = track.durationMs,
                        )
                    )
                }
            }
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
    fun albumTracks(album: AudioAlbum): List<AudioTrack> =
        tracks.filter { it.album == album.title && it.artist == album.artist }

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedAlbumsInView(): List<AudioAlbum> =
        sortedAllAlbums.filter { selectedAlbumIds.contains(it.id.ifBlank { it.title }) }

    fun selectedAlbumTracks(): List<AudioTrack> =
        selectedAlbumsInView().flatMap { albumTracks(it) }

    fun playAlbumSelection() {
        val albumTrackList = selectedAlbumTracks()
        if (albumTrackList.isNotEmpty()) {
            playbackManager?.playAudioQueue(albumTrackList, startIndex = 0)
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
        val favUris = favourites.map { it.mediaUri }.toSet()
        scope.launch(Dispatchers.IO) {
            // Toggle as a group: if every selected track is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = selected.all { favUris.contains(it.path) }
            if (allFav) {
                selected.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                selected.filter { !favUris.contains(it.path) }.forEach { track ->
                    persistenceRepository.addFavourite(
                        FavouriteItem(
                            mediaId = track.id,
                            title = track.title,
                            subtitle = "${track.artist} • ${track.album}",
                            mediaUri = track.path,
                            isVideo = false,
                            durationMs = track.durationMs,
                        )
                    )
                }
            }
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
    fun artistTracks(artist: AudioArtist): List<AudioTrack> =
        tracks.filter { it.artist == artist.name }

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedArtistsInView(): List<AudioArtist> =
        sortedAllArtists.filter { selectedArtistIds.contains(it.id.ifBlank { it.name }) }

    fun selectedArtistTracks(): List<AudioTrack> =
        selectedArtistsInView().flatMap { artistTracks(it) }

    fun playArtistSelection() {
        val artistTrackList = selectedArtistTracks()
        if (artistTrackList.isNotEmpty()) {
            playbackManager?.playAudioQueue(artistTrackList, startIndex = 0)
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
        val favUris = favourites.map { it.mediaUri }.toSet()
        scope.launch(Dispatchers.IO) {
            // Toggle as a group: if every selected track is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = selected.all { favUris.contains(it.path) }
            if (allFav) {
                selected.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                selected.filter { !favUris.contains(it.path) }.forEach { track ->
                    persistenceRepository.addFavourite(
                        FavouriteItem(
                            mediaId = track.id,
                            title = track.title,
                            subtitle = "${track.artist} • ${track.album}",
                            mediaUri = track.path,
                            isVideo = false,
                            durationMs = track.durationMs,
                        )
                    )
                }
            }
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
    // (persistenceRepository.getPlaylistItems, suspend). Both lookups
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
    suspend fun playlistAudioTracks(playlist: Playlist): List<AudioTrack> {
        // Issue #217: compare against the namespaced custom id — a bare
        // room id would also match a device playlist with the same number.
        val custom = customPlaylists.find { customPlaylistId(it.id) == playlist.id }
        if (custom != null) {
            val items = persistenceRepository.getPlaylistItems(custom.id)
            // Library resolution only: a video item has no AudioTrack,
            // and an item whose file is gone has no library row — both
            // are skipped, not faked.
            return items.filter { !it.isVideo }.mapNotNull { item ->
                tracks.firstOrNull { it.path == item.mediaUri }
            }
        }
        return repository.getPlaylistTracks(playlist.id)
    }

    suspend fun playlistPlayableMedia(playlist: Playlist): List<PlayableMedia> {
        // Issue #217: compare against the namespaced custom id — a bare
        // room id would also match a device playlist with the same number.
        val custom = customPlaylists.find { customPlaylistId(it.id) == playlist.id }
        if (custom != null) {
            return persistenceRepository.getPlaylistItems(custom.id).map { item ->
                PlayableMedia(
                    id = item.mediaId,
                    title = item.title,
                    subtitle = item.subtitle,
                    uri = item.mediaUri,
                    isVideo = item.isVideo,
                    durationMs = item.durationMs,
                )
            }
        }
        return repository.getPlaylistTracks(playlist.id).map { track ->
            PlayableMedia(
                id = track.id,
                title = track.title,
                subtitle = "${track.artist} • ${track.album}",
                uri = track.path,
                isVideo = false,
                durationMs = track.durationMs,
            )
        }
    }

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedPlaylistsInView(): List<Playlist> =
        sortedAllPlaylists.filter { selectedPlaylistIds.contains(it.id.ifBlank { it.title }) }

    fun playPlaylistSelection() {
        scope.launch(Dispatchers.IO) {
            val playable = selectedPlaylistsInView().flatMap { playlistPlayableMedia(it) }
            withContext(Dispatchers.Main) {
                if (playable.isNotEmpty()) {
                    playbackManager?.playQueue(playable, startIndex = 0)
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
            val favUris = favourites.map { it.mediaUri }.toSet()
            // Toggle as a group: if every selected track is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = selected.all { favUris.contains(it.path) }
            if (allFav) {
                selected.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                selected.filter { !favUris.contains(it.path) }.forEach { track ->
                    persistenceRepository.addFavourite(
                        FavouriteItem(
                            mediaId = track.id,
                            title = track.title,
                            subtitle = "${track.artist} • ${track.album}",
                            mediaUri = track.path,
                            isVideo = false,
                            durationMs = track.durationMs,
                        )
                    )
                }
            }
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
    // #200: a folder's tracks are its library tracks, grouped exactly the
    // way MediaStoreRepository.getAudioFolders() groups them (parent dir of
    // the file path, blank and content:// paths fall back to
    // /storage/emulated/0/Music) — no new repository call needed. Declared here, before the selection
    // helpers below, because local functions are only visible after their
    // declaration point — selectedFolderTracks() calls it.
    // #267: mirror MediaStoreRepository.getAudioFolders() (#253) exactly —
    // a content:// path has no filesystem parent, so File(...).parent is
    // the meaningless "content://media/external/audio/media" and never
    // equals any folder path. Such tracks group under the Music fallback,
    // like blank paths — otherwise tapping the "Music" folder (or Play /
    // Add-to-playlist / Favourite on a folder selection) silently dropped
    // every content:// track.
    fun folderTracks(folder: AudioFolder): List<AudioTrack> =
        tracks.filter { track ->
            val folderPath = if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
                File(track.path).parent ?: "/storage/emulated/0/Music"
            } else {
                "/storage/emulated/0/Music"
            }
            folderPath == folder.path
        }

    // Issue #234: resolve against the UNFILTERED sorted list — see selectedTracksInView.
    fun selectedFoldersInView(): List<AudioFolder> =
        sortedAllFolders.filter { selectedFolderIds.contains(it.id.ifBlank { it.path }) }

    fun selectedFolderTracks(): List<AudioTrack> =
        selectedFoldersInView().flatMap { folderTracks(it) }

    fun playFolderSelection() {
        val folderTrackList = selectedFolderTracks()
        if (folderTrackList.isNotEmpty()) {
            playbackManager?.playAudioQueue(folderTrackList, startIndex = 0)
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
        val favUris = favourites.map { it.mediaUri }.toSet()
        scope.launch(Dispatchers.IO) {
            // Toggle as a group: if every selected track is already a
            // favourite, remove them all; otherwise add the missing ones.
            val allFav = selected.all { favUris.contains(it.path) }
            if (allFav) {
                selected.forEach { persistenceRepository.removeFavourite(it.path) }
            } else {
                selected.filter { !favUris.contains(it.path) }.forEach { track ->
                    persistenceRepository.addFavourite(
                        FavouriteItem(
                            mediaId = track.id,
                            title = track.title,
                            subtitle = "${track.artist} • ${track.album}",
                            mediaUri = track.path,
                            isVideo = false,
                            durationMs = track.durationMs,
                        )
                    )
                }
            }
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
            playbackManager?.playAudioQueue(sortedTracks.shuffled(), startIndex = 0)
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
                                    onToggleTrackSelection = { toggleTrackSelection(it) },
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
                                                val items = persistenceRepository.getPlaylistItems(custom.id)
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
                                                val memberTracks = repository.getPlaylistTracks(playlist.id)
                                                withContext(Dispatchers.Main) {
                                                    if (memberTracks.isNotEmpty()) {
                                                        playbackManager?.playAudioQueue(memberTracks, startIndex = 0)
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
                                            playbackManager?.playAudioQueue(albumTrackList, startIndex = 0)
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
                                            playbackManager?.playAudioQueue(artistTrackList, startIndex = 0)
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
                                            playbackManager?.playAudioQueue(folderTrackList, startIndex = 0)
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
            onDismiss = { showCreatePlaylistDialog = false },
            // #268: Music creates audio playlists; the fixed type is
            // passed through (no selector on this screen).
            onConfirm = { name, isVideo ->
                scope.launch {
                    persistenceRepository.createPlaylist(name, isVideoPlaylist = isVideo)
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
                    // Issue #230: -1 means the track is already in the
                    // playlist — say so instead of "added".
                    val rowId = persistenceRepository.addItemToPlaylist(
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
                trackForPlaylistAdd = null
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
                        val rowId = persistenceRepository.addItemToPlaylist(
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
                                playbackManager?.playAudioQueue(sortedTracks, startIndex = idx)
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
            onRenamePlaylist = { newName ->
                scope.launch {
                    persistenceRepository.renamePlaylist(pl.id, newName)
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
            historyTotal = historyTotal,
            // #265: same paging as the full History page — the dialog
            // loads the next page when scrolled near the end.
            onLoadMore = {
                if (history.size < historyTotal) historyLimit += HISTORY_PAGE_SIZE
            },
            playbackManager = playbackManager,
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
            onSortChange = { newSort ->
                sortField = newSort
                val updated = appSettings.copy(musicPrimarySortField = newSort.name)
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
    onToggleTrackSelection: (AudioTrack) -> Unit = {},
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
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackRow(
                        track = track,
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
                    val isSelected = selectedTrackIds.contains(track.id.ifBlank { track.path })
                    AudioTrackGridCard(
                        track = track,
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
            GridFastScrollbar(
                gridState = gridState,
                itemCount = tracks.size,
                // Issue #236: the grid's real column count.
                columns = 2,
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
            FastScrollbar(
                listState = listState,
                itemCount = playlistItemCount,
                getSectionText = { index ->
                    if (index < 3) "" else playlists.getOrNull(index - 3)?.title?.take(1)?.uppercase(Locale.US) ?: ""
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
            GridFastScrollbar(
                gridState = gridState,
                itemCount = playlistItemCount,
                // Issue #236: the grid's real column count, plus the 3
                // leading full-span action rows (New playlist / Favourites
                // / Recently played) — each takes one row of its own.
                columns = 2,
                headerItems = 3,
                getSectionText = { index ->
                    if (index < 3) "" else playlists.getOrNull(index - 3)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
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
            FastScrollbar(
                listState = listState,
                itemCount = albums.size,
                getSectionText = { index ->
                    albums.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
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
            GridFastScrollbar(
                gridState = gridState,
                itemCount = albums.size,
                // Issue #236: the grid's real column count.
                columns = 2,
                getSectionText = { index ->
                    albums.getOrNull(index)?.title?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
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
            FastScrollbar(
                listState = listState,
                itemCount = artists.size,
                getSectionText = { index ->
                    artists.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
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
            GridFastScrollbar(
                gridState = gridState,
                itemCount = artists.size,
                // Issue #236: the grid's real column count.
                columns = 2,
                getSectionText = { index ->
                    artists.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
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
            GridFastScrollbar(
                gridState = gridState,
                itemCount = folders.size,
                // Issue #236: the grid's real column count.
                columns = 2,
                getSectionText = { index ->
                    folders.getOrNull(index)?.name?.take(1)?.uppercase(Locale.US) ?: ""
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
