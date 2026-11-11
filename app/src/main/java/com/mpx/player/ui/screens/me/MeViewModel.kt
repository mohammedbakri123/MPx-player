package com.mpx.player.ui.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// #265: one page of history rows. The list grows by this much each time
// the user scrolls near the end, until every row is loaded. Moved here
// from HistoryScreen with the paging logic.
internal const val HISTORY_PAGE_SIZE = 100

/**
 * Migration Phase 2e: the Me tab's state, moved out of [MeScreen],
 * [HistoryScreen], and [PlaylistsScreen] so it has one owner and can
 * be tested with fakes. One [MeViewModel] serves all three screens —
 * each destination obtains its own instance with `koinViewModel()`.
 *
 * Owns only the testable, non-UI state the screens held before:
 * settings load / reload-on-resume / save (language, pop-up play),
 * the badge flows (favourites, history total, playlists), history
 * paging (#265), playlist selection with its item load and the
 * #269 stale-load guard, and the persistence calls behind the
 * favourites / history / playlist actions.
 *
 * Compose-only concerns stay in the screens: dialog visibility,
 * scroll position, the network-stream text field, PiP entry, and
 * playback itself — transport still goes through `PlaybackCoordinator`
 * directly from the screens, and HistoryScreen still calls
 * `resumeManager.clearAll()` beside [clearHistory] (issue #353), as
 * before. No behaviour change: every value below is computed exactly
 * the way the screens computed it before.
 *
 * Dependencies arrive through the constructor, on interfaces only
 * ([MediaPersistenceRepository], [AppSettingsRepository]), so a test
 * can pass fakes. Koin resolves both from the [appModule] singles.
 */
class MeViewModel(
    private val persistence: MediaPersistenceRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    // -- Settings ---------------------------------------------------------

    private val _settings = MutableStateFlow(settingsRepository.load())
    /** Current settings; reloaded on resume via [reloadSettings]. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Issue #231: reload settings on resume — a PiP / language /
     * last-SMB-host change made in Settings must show up here
     * without the tab being recreated.
     */
    fun reloadSettings() {
        _settings.value = settingsRepository.load()
    }

    /** Persists [settings] and publishes it, replacing the old
     * `appSettings = updated` + `save(updated)` pair. */
    fun updateSettings(settings: AppSettings) {
        settingsRepository.save(settings)
        _settings.value = settings
    }

    /**
     * The language the Me tab displays: MainActivity's
     * [externalLanguage] wins when set, otherwise the persisted
     * setting — the screen's old `currentLanguage.ifBlank {
     * appSettings.appLanguage }`.
     */
    fun activeLanguage(externalLanguage: String): String =
        externalLanguage.ifBlank { _settings.value.appLanguage }

    /** Language picked in the Me dialog: persist it like before. */
    fun updateLanguage(code: String) {
        updateSettings(_settings.value.copy(appLanguage = code))
    }

    /** Pop-up play settings from the Me dialog: persist them. */
    fun updatePopupPlay(enabled: Boolean, controls: String) {
        updateSettings(
            _settings.value.copy(
                popupPlayEnabled = enabled,
                popupPlayControls = controls,
            ),
        )
    }

    /**
     * #252: the "Resume at" label must only appear when a tap would
     * actually resume — `PlaybackCoordinator.playQueue` only resumes
     * when `settings.resumePlayback` is on. Read fresh on every call
     * so a Settings change is picked up when returning to History,
     * exactly as the screen's per-composition read did.
     */
    fun isResumePlaybackEnabled(): Boolean =
        settingsRepository.load().resumePlayback

    // -- Badge flows ------------------------------------------------------
    //
    // The Room flows the Me tab observes for its live badge
    // indicators. Collected eagerly while this ViewModel lives, the
    // same as the screens' `collectAsState` while composed.

    /** Favourites, for the Me favourites badge and dialog. */
    val favourites: StateFlow<List<FavouriteItem>> =
        persistence.observeFavourites()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * #265: the badge shows the real history total, from
     * [MediaPersistenceRepository.observeHistoryCount] — the same
     * count query the screen read before; the paged list below stays
     * capped at the limit.
     */
    val historyTotal: StateFlow<Int> =
        persistence.observeHistoryCount()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Custom playlists, for the Me playlists badge and the page. */
    val playlists: StateFlow<List<CustomPlaylist>> =
        persistence.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -- History paging (#265) --------------------------------------------

    private val _historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    /**
     * The paged history list: [HISTORY_PAGE_SIZE] rows at a time.
     * The limit survives rotation — the ViewModel outlives the
     * composition, where the screen used `rememberSaveable` for it.
     */
    val history: StateFlow<List<PlaybackHistoryItem>> =
        _historyLimit.flatMapLatest { limit ->
            persistence.observeHistory(limit = limit)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Load the next page when the user scrolls near the end. The
     * screen calls this with its scroll-derived `nearEnd` flag (and
     * the list size, so it re-fires after each page lands while the
     * user is still sitting at the bottom). It stops by itself once
     * the loaded rows reach [historyTotal].
     */
    fun loadMoreHistoryIfNearEnd(nearEnd: Boolean) {
        if (nearEnd && history.value.size < historyTotal.value) {
            _historyLimit.value += HISTORY_PAGE_SIZE
        }
    }

    /** Clears the persisted history. The screen additionally clears
     * the resume manager beside this call (issue #353). */
    suspend fun clearHistory() {
        persistence.clearHistory()
    }

    // -- Favourites actions -----------------------------------------------

    suspend fun removeFavourite(mediaUri: String) {
        persistence.removeFavourite(mediaUri)
    }

    suspend fun clearFavourites() {
        persistence.clearFavourites()
    }

    // -- Playlists ----------------------------------------------------------

    private val _selectedPlaylist = MutableStateFlow<CustomPlaylist?>(null)
    /** The playlist whose details dialog is open, if any. */
    val selectedPlaylist: StateFlow<CustomPlaylist?> =
        _selectedPlaylist.asStateFlow()

    private val _playlistItems = MutableStateFlow<List<PlaylistItem>>(emptyList())
    /** Items of the selected playlist, for its details dialog. */
    val playlistItems: StateFlow<List<PlaylistItem>> =
        _playlistItems.asStateFlow()

    /**
     * Opens a playlist's details dialog: select it, clear the old
     * items at once, then load the new ones.
     *
     * #269: a second tap starts a second load — only apply this one
     * if this playlist is still selected, so the slower load cannot
     * win over the newer tap and put the wrong songs under the
     * dialog's title.
     */
    fun selectPlaylist(playlist: CustomPlaylist) {
        _selectedPlaylist.value = playlist
        _playlistItems.value = emptyList()
        viewModelScope.launch {
            val items = persistence.getPlaylistItems(playlist.id)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _playlistItems.value = items
            }
        }
    }

    /** Closes the details dialog. */
    fun clearSelectedPlaylist() {
        _selectedPlaylist.value = null
    }

    suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long =
        persistence.createPlaylist(name, isVideoPlaylist = isVideoPlaylist)

    suspend fun deletePlaylist(playlistId: Long) {
        persistence.deletePlaylist(playlistId)
    }

    /**
     * Issue #230: the dialog reads its title from the selected
     * snapshot — update it so the new name shows at once. The
     * Playlists list refreshes itself via the [playlists] flow.
     */
    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        persistence.renamePlaylist(playlistId, newName)
        _selectedPlaylist.value =
            _selectedPlaylist.value?.takeIf { it.id == playlistId }
                ?.copy(name = newName)
    }

    /** Removes an item, then reloads the dialog's item list. */
    suspend fun removeItemFromPlaylist(playlistItemId: Long) {
        persistence.removeItemFromPlaylist(playlistItemId)
        _selectedPlaylist.value?.let { playlist ->
            _playlistItems.value = persistence.getPlaylistItems(playlist.id)
        }
    }
}
