package com.mpx.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Migration Phase 2e (Settings follow-up): the settings screens'
 * state, moved out of [SettingsScreen], [OverlaySettingsScreen],
 * [AdvancedSettingsScreen], [FoldersBlacklistScreen],
 * [PlayerLayoutScreen], and [ConfigEditorScreen] so it has one owner
 * and can be tested with fakes. Each destination obtains its own
 * instance with `koinViewModel()` — Settings, Overlay, and Advanced
 * are separate NavHost destinations, so each loads fresh from the
 * repository on entry, exactly as their `remember { load() }` did
 * before.
 *
 * Owns only the testable, non-UI state the screens held before:
 * settings load / reload-on-resume / save, the search query and
 * drill-down navigation state (#158, #303), the music-tab toggle
 * with its one-visible-tab guard, the player-menu layout
 * reorder/hide/show/reset (#158, #201), the folder blacklist
 * add/remove/clear (#158, #300), the pop-up-play position choice
 * with its RememberLast rule (#292), and the diagnostics record
 * counts.
 *
 * Compose-only concerns stay in the screens: dialog visibility,
 * scroll position, the update-check flow (#414), the diagnostics
 * scan itself (MediaStore + file sizes, still loaded in the screen),
 * the config-folder / export / import launchers (#432, #433), the
 * config-file text editing (#431), the mpv-conf folder scan in the
 * blacklist add dialog, and the PiP entry itself
 * (`PipUtils.updatePipParams` / `enterPip`, which need the Activity).
 *
 * What this ViewModel deliberately does NOT own: the language /
 * theme / palette / theme-color callbacks and the decoder-mode
 * application in `SettingsScreen.update()` — those are MainActivity
 * wiring and a PlaybackManager call, and the screen keeps them in
 * its thin `update()` wrapper around [updateSettings]. None of the
 * functions below changes any of those fields, so no callback is
 * lost by going through the ViewModel directly: layout, blacklist,
 * and pop-up-play writes only touch their own fields. Transport and
 * playback-state observation still go through `PlaybackManager`
 * directly from the screens, as in [MeViewModel] — no new playback
 * interface was needed for this step, and the screens take the
 * manager as a parameter from MainActivity instead of reaching
 * `PlaybackManager.getInstance` themselves.
 *
 * Dependencies arrive through the constructor, on interfaces only
 * ([AppSettingsRepository], [MediaPersistenceRepository]), so a test
 * can pass fakes. Koin resolves both from the [appModule] singles.
 * No behaviour change: every value below is computed exactly the
 * way the screens computed it before.
 */
class SettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val persistence: MediaPersistenceRepository,
) : ViewModel() {

    // -- Settings ---------------------------------------------------------

    private val _settings = MutableStateFlow(settingsRepository.load())
    /** Current settings; reloaded on resume via [reloadSettings]. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Issue #270: reload settings on resume — a change made in a
     * sibling settings destination (Overlay, Advanced, the config
     * editor) must show up here without the screen being recreated.
     */
    fun reloadSettings() {
        _settings.value = settingsRepository.load()
    }

    /** A fresh read straight from the repository, for the places
     * that used to call `load()` at the moment of use instead of
     * trusting the last loaded value (the config editor's
     * load-modify-save, #431). */
    fun loadSettingsNow(): AppSettings = settingsRepository.load()

    /** Persists [settings] and publishes it, replacing the old
     * `settings = updated` + `save(updated)` pair. */
    fun updateSettings(settings: AppSettings, commit: Boolean = false) {
        settingsRepository.save(settings, commit = commit)
        _settings.value = settings
    }

    /**
     * The language the Settings screen displays: MainActivity's
     * [externalLanguage] wins when set, otherwise the persisted
     * setting — the screen's old `currentLanguage.ifBlank {
     * settings.appLanguage }`, same as `MeViewModel.activeLanguage`.
     */
    fun activeLanguage(externalLanguage: String): String =
        externalLanguage.ifBlank { _settings.value.appLanguage }

    // -- Search + drill-down navigation (#158, #303) ----------------------
    //
    // The query lives here, not in PreferencesSearchScreen: opening a
    // result removes the search screen from the composition and
    // discards any state it owns, so a query kept inside it would
    // reset every time a result category is opened. Hoisted here it
    // survives rotation and returning from a category, and resets
    // when Settings itself is left (the ViewModel is scoped to the
    // Settings destination), like the old rememberSaveable state.

    private val _searchQuery = MutableStateFlow("")
    /** The settings-search query, shared with PreferencesSearchScreen. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _isSearching = MutableStateFlow(false)
    /** True while the settings search page is open. */
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
    }

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    /** The drilled-down category, null on the root category list. */
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    fun selectCategory(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun clearSelectedCategory() {
        _selectedCategoryId.value = null
    }

    /**
     * System back / TopAppBar back: search closes first, then a
     * drilled-down category returns to the category list (#124); only
     * at the root list does it leave Settings. Returns true when the
     * press was consumed here, false when the caller should navigate
     * back.
     */
    fun handleBack(): Boolean {
        if (_isSearching.value) {
            _isSearching.value = false
            return true
        }
        if (_selectedCategoryId.value != null) {
            _selectedCategoryId.value = null
            return true
        }
        return false
    }

    // -- Music tabs ---------------------------------------------------------
    //
    // The toggle refuses to disable the last visible tab — the guard
    // lives in AppSettings.withMusicTabToggled, which returns the
    // settings unchanged when the toggle is refused. The screen shows
    // its snackbar when this returns false.

    /** Toggles one music tab; false when the last visible tab may
     * not be turned off. Enabling a tab always succeeds. */
    fun toggleMusicTab(tabId: String, currentVal: Boolean): Boolean {
        val next = _settings.value.withMusicTabToggled(tabId, !currentVal)
        if (next == _settings.value) return false
        updateSettings(next)
        return true
    }

    // -- Player menu layout (#158, #201) ------------------------------------
    //
    // Visible ids in display order, parsed from the two stored
    // comma-separated strings; ids hidden explicitly never resurrect
    // as "missing". Every write keeps the hidden set in sync and
    // commits synchronously (#191, #228), as the screen did before.

    /** Visible Player Menu row ids, in display order. */
    val visibleMenuItems: StateFlow<List<String>> =
        _settings.map { settings ->
            parsePlayerMenuLayout(settings.playerMenuLayout, settings.hiddenPlayerMenuIds)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            parsePlayerMenuLayout(
                _settings.value.playerMenuLayout,
                _settings.value.hiddenPlayerMenuIds,
            ),
        )

    /** Hidden Player Menu row ids (known ids not in the visible list). */
    val hiddenMenuItems: StateFlow<List<String>> =
        _settings.map { settings ->
            val visible = parsePlayerMenuLayout(
                settings.playerMenuLayout,
                settings.hiddenPlayerMenuIds,
            )
            PLAYER_MENU_IDS.filter { it !in visible }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PLAYER_MENU_IDS.filter {
                it !in parsePlayerMenuLayout(
                    _settings.value.playerMenuLayout,
                    _settings.value.hiddenPlayerMenuIds,
                )
            },
        )

    /** Persists [next] as the visible list, with the hidden set kept
     * in sync — every known id not in the list is recorded as
     * explicitly hidden, so the parse never resurrects it. */
    private fun applyVisibleMenuItems(next: List<String>) {
        updateSettings(
            _settings.value.copy(
                playerMenuLayout = next.joinToString(","),
                hiddenPlayerMenuIds = (PLAYER_MENU_IDS - next.toSet()).joinToString(","),
            ),
            commit = true,
        )
    }

    /** Moves one visible row up; a no-op at the top or for an
     * unknown id. */
    fun moveMenuItemUp(id: String) {
        val visible = visibleMenuItems.value.toMutableList()
        val index = visible.indexOf(id)
        if (index <= 0) return
        val moved = visible.removeAt(index)
        visible.add(index - 1, moved)
        applyVisibleMenuItems(visible)
    }

    /** Moves one visible row down; a no-op at the bottom or for an
     * unknown id. */
    fun moveMenuItemDown(id: String) {
        val visible = visibleMenuItems.value.toMutableList()
        val index = visible.indexOf(id)
        if (index < 0 || index >= visible.lastIndex) return
        val moved = visible.removeAt(index)
        visible.add(index + 1, moved)
        applyVisibleMenuItems(visible)
    }

    /** Hides one visible row. */
    fun hideMenuItem(id: String) {
        applyVisibleMenuItems(visibleMenuItems.value - id)
    }

    /** Shows one hidden row again, appended at the end. */
    fun showMenuItem(id: String) {
        applyVisibleMenuItems(visibleMenuItems.value + id)
    }

    /** Restores the default menu order (all rows visible). */
    fun resetMenuLayout() {
        applyVisibleMenuItems(PLAYER_MENU_IDS)
    }

    // -- Folder blacklist (#158, #300) --------------------------------------
    //
    // Blacklisted folder paths, sorted for display. Writes commit
    // synchronously (#191, #228), as the screen did before.

    /** Blacklisted folder paths, sorted. */
    val blacklistedFolders: StateFlow<List<String>> =
        _settings.map { it.blacklistedFolders.sorted() }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            _settings.value.blacklistedFolders.sorted(),
        )

    /** Adds folders to the blacklist (a set union — already-listed
     * paths stay listed once). */
    fun addBlacklistedFolders(paths: Set<String>) {
        updateSettings(
            _settings.value.copy(
                blacklistedFolders = _settings.value.blacklistedFolders + paths,
            ),
            commit = true,
        )
    }

    /** Removes one folder from the blacklist. */
    fun removeBlacklistedFolder(path: String) {
        updateSettings(
            _settings.value.copy(
                blacklistedFolders = _settings.value.blacklistedFolders - path,
            ),
            commit = true,
        )
    }

    /** Clears the whole blacklist. */
    fun clearBlacklistedFolders() {
        updateSettings(
            _settings.value.copy(blacklistedFolders = emptySet()),
            commit = true,
        )
    }

    // -- Pop-up play position (#292) ----------------------------------------
    //
    // Picking an explicit position also remembers it, so a later
    // RememberLast reuses it. Picking RememberLast itself leaves the
    // last explicit choice untouched.

    /**
     * Sets the pop-up-play position, applying the #292 RememberLast
     * rule, and returns the new settings so the screen can refresh
     * the live PiP params from it.
     */
    fun updatePopupPlayPosition(key: String): AppSettings {
        val next = _settings.value.copy(
            popupPlayPosition = key,
            lastPopupPlayPosition =
                if (key != "RememberLast") key
                else _settings.value.lastPopupPlayPosition,
        )
        updateSettings(next)
        return next
    }

    // -- Diagnostics record counts ------------------------------------------
    //
    // The Development section's record counts, read through the
    // persistence interface instead of opening the Room database
    // directly (`MpxDatabase.getInstance`) from the screen. The
    // counts are the same values the DAO count queries returned:
    // favourites, history rows, playlists, resume positions.

    /** Record counts for the diagnostics section, in the order the
     * section renders them: favourites, history, playlists, resume
     * positions. */
    suspend fun loadRecordCounts(): List<Int> = listOf(
        persistence.getFavourites().size,
        persistence.observeHistoryCount().first(),
        persistence.getPlaylists().size,
        persistence.getAllResumePositions().size,
    )
}
