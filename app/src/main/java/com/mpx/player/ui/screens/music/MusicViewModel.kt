package com.mpx.player.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.customPlaylistId
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.ui.screens.local.MediaSortComparator
import com.mpx.player.ui.screens.local.SortField
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.screens.local.isPathBlacklisted
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// #265: one page of history rows in the PlaybackHistoryDialog — same
// value as the full History page (its own private const there) and as
// the screen's local HISTORY_PAGE_SIZE.
private const val HISTORY_PAGE_SIZE = 100

/**
 * Migration Phase 2c: the music screen's state, moved out of
 * [MusicScreen]'s Compose `remember` blocks so it has one owner
 * and can be tested with fakes.
 *
 * Owns the testable, non-UI state the screen held before: audio
 * library loading (with the folder-blacklist filter, issue #300),
 * settings load / reload-on-resume, view mode and sort state, search
 * filtering and sorting for all five tabs, the five multi-select
 * selections and their track resolution, and the favourites / history
 * / custom-playlist flows from the persistence repository. The thin
 * persistence pass-throughs at the bottom exist only so the screen's
 * dialogs no longer construct a repository themselves.
 *
 * Dependencies arrive through the constructor, on interfaces only
 * ([MediaRepository], [MediaPersistenceRepository],
 * [AppSettingsRepository]), so a test can pass fakes. Playback still
 * goes through `PlaybackManager` directly from the screen — it has no
 * interface yet, and moves behind one in a later phase (same split as
 * Phase 2a's PlayerViewModel). No behaviour change: every value below
 * is computed exactly the way the screen computed it before.
 *
 * Deliberately NOT owned here (they stay in the screen):
 * - The selected inner tab, the search query / open state, and the
 *   history page limit: all three are `rememberSaveable` in the screen
 *   because `navigateToTab` pops this destination with saveState, which
 *   clears this ViewModel on every dock-tab switch (issues #255, #256,
 *   #326). The screen mirrors the query, the open state, and the limit
 *   into [setSearchQuery], [setSearching], and [setHistoryLimit]; the
 *   filtering and paging logic still lives here.
 * - Dialog / popup / menu visibility and the snackbar: Compose-only.
 */
class MusicViewModel(
    private val mediaRepository: MediaRepository,
    private val persistence: MediaPersistenceRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    // -- Settings ---------------------------------------------------------

    private val _settings = MutableStateFlow(settingsRepository.load())
    /** Current settings; reloaded on resume via [onResume]. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Fresh read straight from the repository, for the resume path. */
    fun reloadSettings() {
        _settings.value = settingsRepository.load()
    }

    /** Persists [updated] and publishes it. */
    private fun updateSettings(updated: AppSettings, commit: Boolean = false) {
        settingsRepository.save(updated, commit = commit)
        _settings.value = updated
    }

    /** List / grid, derived from settings so a resume reload re-derives
     * it instead of going stale. */
    val viewMode: StateFlow<ViewMode> = settings.map {
        if (it.musicViewMode == "GRID") ViewMode.GRID else ViewMode.LIST
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (_settings.value.musicViewMode == "GRID") ViewMode.GRID else ViewMode.LIST,
    )

    /**
     * Issue #294: keyed on musicPrimarySortField — a Settings reset (or
     * any other outside change) re-derives the field on resume instead
     * of a one-time remember going stale.
     */
    val sortField: StateFlow<SortField> = settings.map {
        parseSortField(it.musicPrimarySortField)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        parseSortField(_settings.value.musicPrimarySortField),
    )

    /** The visible inner tabs, derived from the tab-visibility settings. */
    val visibleTabs: StateFlow<List<MusicTab>> = settings.map {
        visibleMusicTabs(it)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        visibleMusicTabs(_settings.value),
    )

    fun updateViewMode(newMode: ViewMode) {
        val updated = if (_settings.value.separateViewLayout) {
            _settings.value.copy(musicViewMode = newMode.name)
        } else {
            _settings.value.copy(musicViewMode = newMode.name, localViewMode = newMode.name)
        }
        // Issue #191: layout save — commit() so a swipe-away right after
        // the tap cannot lose the view-mode choice.
        updateSettings(updated, commit = true)
    }

    fun setSortField(newSort: SortField) {
        updateSettings(
            _settings.value.copy(musicPrimarySortField = newSort.name),
            commit = true,
        )
    }

    fun setSortReverse(reversed: Boolean) {
        updateSettings(_settings.value.copy(sortReverse = reversed), commit = true)
    }

    /**
     * Toggles one inner tab's visibility. Returns false when the toggle
     * was refused — the last visible tab cannot be hidden — so the
     * screen can show the "at least one tab" message; true when applied.
     */
    fun toggleMusicTab(tabId: String, currentValue: Boolean): Boolean {
        val updated = _settings.value.withMusicTabToggled(tabId, !currentValue)
        if (updated == _settings.value) return false
        updateSettings(updated)
        return true
    }

    // -- Library loading ----------------------------------------------------

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    /** Device (MediaStore) playlists; see [allPlaylists] for the combined list. */
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _albums = MutableStateFlow<List<AudioAlbum>>(emptyList())
    val albums: StateFlow<List<AudioAlbum>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<AudioArtist>>(emptyList())
    val artists: StateFlow<List<AudioArtist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<AudioFolder>>(emptyList())
    val folders: StateFlow<List<AudioFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /**
     * Loads (or reloads) the whole audio library. Mirrors the screen's
     * old `refreshMusic()`: with permission, the five MediaStore lists
     * load on IO and the folder blacklist is applied; without it, every
     * list is cleared. Must be called from Main — [isLoading] is
     * snapshot state (issue #295), and the results are assigned on the
     * caller's context after the IO work returns.
     */
    suspend fun refreshLibrary(hasPermission: Boolean) {
        _hasPermission.value = hasPermission
        if (!hasPermission) {
            _tracks.value = emptyList()
            _playlists.value = emptyList()
            _albums.value = emptyList()
            _artists.value = emptyList()
            _folders.value = emptyList()
            return
        }
        // Issue #295: isLoading is snapshot state — write it on Main,
        // before the IO work, not inside the IO block.
        _isLoading.value = true
        val (visible, devicePlaylists) = withContext(Dispatchers.IO) {
            val t = mediaRepository.getAudioTracks()
            val p = mediaRepository.getPlaylists()
            val al = mediaRepository.getAudioAlbums()
            val ar = mediaRepository.getAudioArtists()
            val f = mediaRepository.getAudioFolders()
            // Issue #300: folder blacklist hides blacklisted audio too.
            applyAudioBlacklist(t, al, ar, f, _settings.value.blacklistedFolders) to p
        }
        _tracks.value = visible.tracks
        _playlists.value = devicePlaylists
        _albums.value = visible.albums
        _artists.value = visible.artists
        _folders.value = visible.folders
        _isLoading.value = false
    }

    /**
     * The ON_RESUME path: reload settings first, so a blacklist change
     * in Preferences applies on return (issue #300), then reload the
     * library with the freshly reloaded settings.
     */
    suspend fun onResume(hasPermission: Boolean) {
        reloadSettings()
        refreshLibrary(hasPermission)
    }

    // -- Search ---------------------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The screen mirrors its rememberSaveable query here (see class doc). */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** The screen mirrors its rememberSaveable open state here (see class doc). */
    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
    }

    // -- Persistence flows -----------------------------------------------------

    val favourites: StateFlow<List<FavouriteItem>> = persistence.observeFavourites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // #265: the dialog list is paged (HISTORY_PAGE_SIZE rows at a time)
    // and the counts show the real total. The old code rendered
    // history.size from the 100-row-capped observer, freezing the Music
    // history count and the dialog title at 100 and hiding older rows.
    // The limit is rememberSaveable in the screen so rotation does not
    // drop rows the user already loaded in the dialog; the screen mirrors
    // it here via [setHistoryLimit].
    val historyTotal: StateFlow<Int> = persistence.observeHistoryCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)
    val historyLimit: StateFlow<Int> = _historyLimit.asStateFlow()

    fun setHistoryLimit(limit: Int) {
        _historyLimit.value = limit
    }

    /** #265: same paging as the full History page — loads the next page
     * when the dialog is scrolled near the end. */
    fun loadMoreHistory() {
        if (history.value.size < historyTotal.value) {
            _historyLimit.value += HISTORY_PAGE_SIZE
        }
    }

    val history: StateFlow<List<PlaybackHistoryItem>> =
        _historyLimit.flatMapLatest { persistence.observeHistory(limit = it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customPlaylists: StateFlow<List<CustomPlaylist>> = persistence.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = combine(customPlaylists, _playlists) { custom, device ->
        custom.map { it.toPlaylist() } + device
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    // -- Filtered / sorted lists ----------------------------------------------
    //
    // #185: one shared sortField / sortReverse for the whole Music
    // screen, like Local. Each tab shares the screen search box and the
    // shared sort state; there is no per-tab sort. The `sortedAll*`
    // variants sort the UNFILTERED list — the selection resolvers below
    // must not lose items the search box hides (issue #234).

    val filteredTracks: StateFlow<List<AudioTrack>> = combine(_tracks, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedTracks: StateFlow<List<AudioTrack>> = combine(
        filteredTracks, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioTrackComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioTrackComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredAlbums: StateFlow<List<AudioAlbum>> = combine(_albums, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAlbums: StateFlow<List<AudioAlbum>> = combine(
        filteredAlbums, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioAlbumComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllAlbums: StateFlow<List<AudioAlbum>> = combine(
        _albums, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioAlbumComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredArtists: StateFlow<List<AudioArtist>> = combine(_artists, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedArtists: StateFlow<List<AudioArtist>> = combine(
        filteredArtists, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioArtistComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllArtists: StateFlow<List<AudioArtist>> = combine(
        _artists, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioArtistComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The list filtered here is the combined one (custom playlists
    // first, then device playlists); the 3 header rows are not
    // filtered — they are actions, not rows.
    val filteredPlaylists: StateFlow<List<Playlist>> = combine(allPlaylists, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedPlaylists: StateFlow<List<Playlist>> = combine(
        filteredPlaylists, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioPlaylistComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllPlaylists: StateFlow<List<Playlist>> = combine(
        allPlaylists, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioPlaylistComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredFolders: StateFlow<List<AudioFolder>> = combine(_folders, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.path.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedFolders: StateFlow<List<AudioFolder>> = combine(
        filteredFolders, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioFolderComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllFolders: StateFlow<List<AudioFolder>> = combine(
        _folders, sortField, settings,
    ) { list, field, s ->
        list.sortedWith(
            MediaSortComparator.audioFolderComparator(
                primary = field,
                secondary = SortField.NAME,
                reverse = s.sortReverse,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -- Multi-select ------------------------------------------------------------
    //
    // #185: long-press multi-select on every tab, mirroring Local's
    // #144. Keyed like Local (id, falling back to path / title / name).
    // Long-press toggles an item (entering selection mode when empty); a
    // tap while selecting toggles too. Back / the X exits and clears.

    private val _selectedTrackIds = MutableStateFlow(setOf<String>())
    val selectedTrackIds: StateFlow<Set<String>> = _selectedTrackIds.asStateFlow()

    private val _selectedAlbumIds = MutableStateFlow(setOf<String>())
    val selectedAlbumIds: StateFlow<Set<String>> = _selectedAlbumIds.asStateFlow()

    private val _selectedArtistIds = MutableStateFlow(setOf<String>())
    val selectedArtistIds: StateFlow<Set<String>> = _selectedArtistIds.asStateFlow()

    private val _selectedPlaylistIds = MutableStateFlow(setOf<String>())
    val selectedPlaylistIds: StateFlow<Set<String>> = _selectedPlaylistIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow(setOf<String>())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    fun toggleTrackSelection(track: AudioTrack) {
        val key = track.id.ifBlank { track.path }
        _selectedTrackIds.value =
            if (_selectedTrackIds.value.contains(key)) _selectedTrackIds.value - key
            else _selectedTrackIds.value + key
    }

    fun clearTrackSelection() {
        _selectedTrackIds.value = emptySet()
    }

    fun toggleAlbumSelection(album: AudioAlbum) {
        val key = album.id.ifBlank { album.title }
        _selectedAlbumIds.value =
            if (_selectedAlbumIds.value.contains(key)) _selectedAlbumIds.value - key
            else _selectedAlbumIds.value + key
    }

    fun clearAlbumSelection() {
        _selectedAlbumIds.value = emptySet()
    }

    fun toggleArtistSelection(artist: AudioArtist) {
        val key = artist.id.ifBlank { artist.name }
        _selectedArtistIds.value =
            if (_selectedArtistIds.value.contains(key)) _selectedArtistIds.value - key
            else _selectedArtistIds.value + key
    }

    fun clearArtistSelection() {
        _selectedArtistIds.value = emptySet()
    }

    fun togglePlaylistSelection(playlist: Playlist) {
        val key = playlist.id.ifBlank { playlist.title }
        _selectedPlaylistIds.value =
            if (_selectedPlaylistIds.value.contains(key)) _selectedPlaylistIds.value - key
            else _selectedPlaylistIds.value + key
    }

    fun clearPlaylistSelection() {
        _selectedPlaylistIds.value = emptySet()
    }

    fun toggleFolderSelection(folder: AudioFolder) {
        val key = folder.id.ifBlank { folder.path }
        _selectedFolderIds.value =
            if (_selectedFolderIds.value.contains(key)) _selectedFolderIds.value - key
            else _selectedFolderIds.value + key
    }

    fun clearFolderSelection() {
        _selectedFolderIds.value = emptySet()
    }

    fun clearAllSelections() {
        clearTrackSelection()
        clearAlbumSelection()
        clearArtistSelection()
        clearPlaylistSelection()
        clearFolderSelection()
    }

    /** Leaving a tab drops its selection — a selection must not linger
     * (and its action bar must not show) on the other tabs. */
    fun clearSelectionsExcept(tab: MusicTab) {
        if (tab != MusicTab.TRACKS) clearTrackSelection()
        if (tab != MusicTab.ALBUMS) clearAlbumSelection()
        if (tab != MusicTab.ARTISTS) clearArtistSelection()
        if (tab != MusicTab.PLAYLISTS) clearPlaylistSelection()
        if (tab != MusicTab.FOLDERS) clearFolderSelection()
    }

    // -- Selection resolution -----------------------------------------------------
    //
    // Issue #234: resolve against the UNFILTERED sorted list — the search
    // box must not narrow an existing selection.

    fun selectedTracksInView(): List<AudioTrack> =
        sortedAllTracks.value.filter { selectedTrackIds.value.contains(it.id.ifBlank { it.path }) }

    // #185: an album's tracks are its library tracks (getAudioAlbums
    // groups by (album, artist) — #243, so same-named albums by
    // different artists stay separate), in library order — no new
    // repository call needed.
    fun albumTracks(album: AudioAlbum): List<AudioTrack> =
        tracks.value.filter { it.album == album.title && it.artist == album.artist }

    fun selectedAlbumsInView(): List<AudioAlbum> =
        sortedAllAlbums.value.filter { selectedAlbumIds.value.contains(it.id.ifBlank { it.title }) }

    fun selectedAlbumTracks(): List<AudioTrack> =
        selectedAlbumsInView().flatMap { albumTracks(it) }

    // #185: an artist's tracks are its library tracks (getAudioArtists
    // groups by artist name), in library order — no new repository call
    // needed.
    fun artistTracks(artist: AudioArtist): List<AudioTrack> =
        tracks.value.filter { it.artist == artist.name }

    fun selectedArtistsInView(): List<AudioArtist> =
        sortedAllArtists.value.filter { selectedArtistIds.value.contains(it.id.ifBlank { it.name }) }

    fun selectedArtistTracks(): List<AudioTrack> =
        selectedArtistsInView().flatMap { artistTracks(it) }

    // #185: a playlist's tracks are NOT an in-memory filter. A device
    // playlist's members come from MediaStore
    // (mediaRepository.getPlaylistTracks, a blocking call — call from IO
    // only, like the single-tap path), and a custom playlist's items come
    // from Room (persistence.getPlaylistItems, suspend). Both lookups
    // skip members that are no longer in the library, so the result is
    // exactly what tapping the playlist would queue.
    suspend fun playlistAudioTracks(playlist: Playlist): List<AudioTrack> {
        // Issue #217: compare against the namespaced custom id — a bare
        // room id would also match a device playlist with the same number.
        val custom = customPlaylists.value.find { customPlaylistId(it.id) == playlist.id }
        if (custom != null) {
            val items = persistence.getPlaylistItems(custom.id)
            // Library resolution only: a video item has no AudioTrack,
            // and an item whose file is gone has no library row — both
            // are skipped, not faked.
            return items.filter { !it.isVideo }.mapNotNull { item ->
                tracks.value.firstOrNull { it.path == item.mediaUri }
            }
        }
        return mediaRepository.getPlaylistTracks(playlist.id)
    }

    // Two shapes are needed because the two kinds of playlist play
    // differently: playlistAudioTracks() for Add-to-playlist / Favourite
    // (which work on library AudioTracks, like the album/artist actions),
    // and playlistPlayableMedia() for Play (which maps straight to the
    // queue's PlayableMedia — the same mapping the custom-playlist
    // details dialog's onPlayAll uses — so a custom playlist keeps its
    // video items and its own order instead of being forced through the
    // audio-only path).
    suspend fun playlistPlayableMedia(playlist: Playlist): List<PlayableMedia> {
        // Issue #217: compare against the namespaced custom id — a bare
        // room id would also match a device playlist with the same number.
        val custom = customPlaylists.value.find { customPlaylistId(it.id) == playlist.id }
        if (custom != null) {
            return persistence.getPlaylistItems(custom.id).map { item ->
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
        return mediaRepository.getPlaylistTracks(playlist.id).map { track ->
            PlayableMedia(
                id = track.id,
                title = track.title,
                subtitle = "${track.artist} • ${track.album}",
                // Local playback fix: play via the MediaStore content:// URI
                // (MpvFdOpener turns it into fd:// at load time). The raw
                // DATA path is denied to native code under scoped storage.
                uri = track.contentUri.ifBlank { track.path },
                isVideo = false,
                durationMs = track.durationMs,
            )
        }
    }

    fun selectedPlaylistsInView(): List<Playlist> =
        sortedAllPlaylists.value.filter { selectedPlaylistIds.value.contains(it.id.ifBlank { it.title }) }

    // #185: a folder's tracks are its library tracks, grouped exactly the
    // way MediaStoreRepository.getAudioFolders() groups them (parent dir
    // of the file path, blank and content:// paths fall back to
    // /storage/emulated/0/Music) — no new repository call needed.
    // #267: a content:// path has no filesystem parent, so
    // File(...).parent is the meaningless
    // "content://media/external/audio/media" and never equals any folder
    // path. Such tracks group under the Music fallback, like blank paths
    // — otherwise tapping the "Music" folder (or Play / Add-to-playlist /
    // Favourite on a folder selection) silently dropped every
    // content:// track.
    fun folderTracks(folder: AudioFolder): List<AudioTrack> =
        tracks.value.filter { track ->
            val folderPath = if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
                File(track.path).parent ?: "/storage/emulated/0/Music"
            } else {
                "/storage/emulated/0/Music"
            }
            folderPath == folder.path
        }

    fun selectedFoldersInView(): List<AudioFolder> =
        sortedAllFolders.value.filter { selectedFolderIds.value.contains(it.id.ifBlank { it.path }) }

    fun selectedFolderTracks(): List<AudioTrack> =
        selectedFoldersInView().flatMap { folderTracks(it) }

    // -- Favourites ---------------------------------------------------------------

    // Issue #321: single-track favourite toggle for the track heart.
    // Mirrors LocalScreen's toggleVideoFavourite (#235): the same
    // repository toggle, so the heart flips through the observed
    // favourites Flow — no manual refresh needed. The FavouriteItem
    // shape matches the selection favourite below (audio: isVideo
    // false, subtitle "artist • album").
    fun toggleTrackFavourite(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            persistence.toggleFavourite(
                FavouriteItem(
                    mediaId = track.id,
                    title = track.title,
                    subtitle = "${track.artist} • ${track.album}",
                    mediaUri = track.path,
                    isVideo = false,
                    durationMs = track.durationMs,
                ),
            )
        }
    }

    /**
     * Group favourite toggle for a multi-select: if every selected track
     * is already a favourite, remove them all; otherwise add the missing
     * ones. Returns true when tracks were removed, false when they were
     * added — the screen clears the selection and shows the snackbar
     * from the result. Call from IO; reads the favourites snapshot.
     */
    suspend fun toggleFavouriteForTracks(selected: List<AudioTrack>): Boolean {
        if (selected.isEmpty()) return false
        val favUris = favourites.value.map { it.mediaUri }.toSet()
        val allFav = selected.all { favUris.contains(it.path) }
        if (allFav) {
            selected.forEach { persistence.removeFavourite(it.path) }
        } else {
            selected.filter { !favUris.contains(it.path) }.forEach { track ->
                persistence.addFavourite(
                    FavouriteItem(
                        mediaId = track.id,
                        title = track.title,
                        subtitle = "${track.artist} • ${track.album}",
                        mediaUri = track.path,
                        isVideo = false,
                        durationMs = track.durationMs,
                    ),
                )
            }
        }
        return allFav
    }

    // -- Persistence pass-throughs for the dialogs ---------------------------------
    //
    // One-liners so the screen's dialogs no longer construct or hold a
    // repository. The dialog logic (item mapping, order, feedback) stays
    // in the screen, unchanged.

    suspend fun toggleFavourite(item: FavouriteItem): Boolean =
        persistence.toggleFavourite(item)

    suspend fun removeFavourite(mediaUri: String) =
        persistence.removeFavourite(mediaUri)

    suspend fun clearFavourites() = persistence.clearFavourites()

    suspend fun clearHistory() = persistence.clearHistory()

    suspend fun clearAllResumePositions() = persistence.clearAllResumePositions()

    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> =
        persistence.getPlaylistItems(playlistId)

    suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long =
        persistence.createPlaylist(name, isVideoPlaylist = isVideoPlaylist)

    suspend fun addItemToPlaylist(item: PlaylistItem): Long =
        persistence.addItemToPlaylist(item)

    suspend fun removeItemFromPlaylist(playlistItemId: Long) =
        persistence.removeItemFromPlaylist(playlistItemId)

    suspend fun deletePlaylist(playlistId: Long) =
        persistence.deletePlaylist(playlistId)

    suspend fun renamePlaylist(playlistId: Long, newName: String) =
        persistence.renamePlaylist(playlistId, newName)
}

// -- Pure helpers, moved verbatim from the screen -----------------------------------

private fun parseSortField(name: String): SortField =
    try {
        SortField.valueOf(name)
    } catch (_: Exception) {
        SortField.NAME
    }

private fun visibleMusicTabs(settings: AppSettings): List<MusicTab> {
    val list = mutableListOf<MusicTab>()
    if (settings.showMusicTracksTab) list.add(MusicTab.TRACKS)
    if (settings.showMusicPlaylistsTab) list.add(MusicTab.PLAYLISTS)
    if (settings.showMusicAlbumsTab) list.add(MusicTab.ALBUMS)
    if (settings.showMusicArtistsTab) list.add(MusicTab.ARTISTS)
    if (settings.showMusicFoldersTab) list.add(MusicTab.FOLDERS)
    if (list.isEmpty()) {
        list.add(MusicTab.TRACKS)
    }
    return list
}

// Issue #300: the four audio-library lists after the folder blacklist is
// applied (see applyAudioBlacklist in MusicScreen).
internal data class FilteredAudioLibrary(
    val tracks: List<AudioTrack>,
    val albums: List<AudioAlbum>,
    val artists: List<AudioArtist>,
    val folders: List<AudioFolder>,
)

// Issue #300: a track's folder, derived exactly the way
// MediaStoreRepository.getAudioFolders() and folderTracks() group it
// (parent dir; blank and content:// paths fall back to
// /storage/emulated/0/Music, #253/#267), so a track and its folder
// tile are hidden together by the blacklist below.
// Issue #317: a content:// track's File.parent is meaningless, so
// the old /Music fallback let it leak out of a blacklisted
// subfolder. folderPathByBucketId resolves it from the folder list
// instead: the key is the MediaStore BUCKET_ID scheme (#253 — hash
// of the lower-cased parent path), and the track carries its bucket
// id. An unknown bucket keeps the old /Music fallback — visible,
// never falsely hidden.
internal fun audioFolderPath(
    track: AudioTrack,
    folderPathByBucketId: Map<String, String> = emptyMap(),
): String =
    if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
        File(track.path).parent ?: "/storage/emulated/0/Music"
    } else {
        folderPathByBucketId[track.bucketId] ?: "/storage/emulated/0/Music"
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
// played.
internal fun applyAudioBlacklist(
    scannedTracks: List<AudioTrack>,
    scannedAlbums: List<AudioAlbum>,
    scannedArtists: List<AudioArtist>,
    scannedFolders: List<AudioFolder>,
    blacklist: Set<String>,
): FilteredAudioLibrary {
    if (blacklist.isEmpty()) {
        return FilteredAudioLibrary(scannedTracks, scannedAlbums, scannedArtists, scannedFolders)
    }
    // Issue #317: bucket id -> folder path, so content:// tracks
    // resolve to their real folder (see audioFolderPath). Built
    // from the unfiltered list so a blacklisted folder stays
    // resolvable; the key is the MediaStore BUCKET_ID scheme
    // (#253): hash of the lower-cased parent path.
    val folderPathByBucketId = scannedFolders
        .filter { it.path.isNotBlank() }
        .associate { it.path.lowercase(Locale.US).hashCode().toString() to it.path }
    val visibleTracks = scannedTracks.filter { !isPathBlacklisted(audioFolderPath(it, folderPathByBucketId), blacklist) }
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
