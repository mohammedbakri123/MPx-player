package com.mpx.player.ui.screens.player

import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.PlaybackStateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Migration Phase 2a: the player screen's state, moved out of
 * [PlayerScreen]'s Compose `remember` blocks so it has one owner
 * and can be tested with fakes.
 *
 * Owns only the testable, non-UI state the screen held before:
 * playback-state collection, settings load / reload-on-resume,
 * queue restore on first entry, the rotation override, and the
 * keep-screen-on decision. Compose-only concerns stay in the
 * screen: gestures, the SurfaceView, dialogs, and layout.
 *
 * Dependencies arrive through the constructor, on interfaces only
 * ([PlaybackStateSource], [AppSettingsRepository]), so a test can
 * pass fakes. Transport actions, subtitles, favourites, and
 * playlists still go through `PlaybackCoordinator` directly from the
 * screen — they move behind interfaces in later phases, not here.
 * No behaviour change: every value below is computed exactly the
 * way the screen computed it before.
 */
class PlayerViewModel(
    private val playback: PlaybackStateSource,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    /** Playback state, collected by the screen exactly as before. */
    val playbackState: StateFlow<PlaybackState> = playback.playbackState

    private val _settings = MutableStateFlow(settingsRepository.load())
    /** Current settings; reloaded on resume via [reloadSettings]. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Issue #241: settings can change while this screen sits in the
     * back stack (user opens Settings, then comes back). Reload so
     * the playing video picks the new values up.
     */
    fun reloadSettings() {
        _settings.value = settingsRepository.load()
    }

    /**
     * A fresh read straight from the repository, for the two places
     * the screen used to call `load()` at the moment of use instead
     * of trusting the last loaded value: the leave-the-player
     * stop check (issue #92) and the decoder change.
     */
    fun loadSettingsNow(): AppSettings = settingsRepository.load()

    /** Persists [settings] and publishes it, replacing the old
     * `currentSettings = updated` + `save(updated)` pair. */
    fun updateSettings(settings: AppSettings) {
        settingsRepository.save(settings)
        _settings.value = settings
    }

    /**
     * Issue #149: after a process death in the background, Android
     * restores this destination but the playback singleton is fresh
     * with an empty queue. Restore the last persisted queue instead
     * (paused, at the saved position). A no-op when the queue is
     * already non-empty or nothing was persisted.
     */
    fun restoreQueueIfEmpty(): Boolean {
        if (playback.playbackState.value.queue.isNotEmpty()) return false
        return playback.restorePersistedQueueIfEmpty()
    }

    // -- Rotation (issue #363) -------------------------------------------
    //
    // MX-style rotation: every video starts in portrait, and the
    // transport row's rotation button toggles landscape directly, no
    // menu. In Auto mode the target is SENSOR_PORTRAIT whatever the
    // video's aspect. The toggle is a per-video session override:
    // it resets when the current item changes, so the next video
    // starts in portrait again. An explicit Portrait/Landscape lock
    // in Settings still wins as the default the override starts from.

    private val _rotationOverride = MutableStateFlow<String?>(null)
    /** "landscape" / "portrait" while the user has toggled, null otherwise. */
    val rotationOverride: StateFlow<String?> = _rotationOverride.asStateFlow()

    /** Effective orientation the transport toggle shows (override,
     * else the persisted setting; Auto means portrait). */
    val isLandscapeOrientation: StateFlow<Boolean> = combine(
        _rotationOverride,
        _settings,
    ) { override, settings ->
        resolveIsLandscape(override, settings)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        resolveIsLandscape(_rotationOverride.value, _settings.value),
    )

    /** Activity orientation the screen applies via `requestedOrientation`. */
    val targetOrientation: StateFlow<Int> = combine(
        _rotationOverride,
        _settings,
    ) { override, settings ->
        resolveTargetOrientation(override, settings)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        resolveTargetOrientation(_rotationOverride.value, _settings.value),
    )

    /** The transport row's rotation button: portrait <-> landscape. */
    fun toggleRotation() {
        _rotationOverride.value =
            if (isLandscapeOrientation.value) "portrait" else "landscape"
    }

    init {
        // The override is keyed to the current item: the next video
        // starts in portrait again. Mirrors the screen's old
        // `remember(state.currentItem?.id)` reset.
        viewModelScope.launch {
            var lastItemId: String? = playbackState.value.currentItem?.id
            playbackState.collect { state ->
                val itemId = state.currentItem?.id
                if (itemId != lastItemId) {
                    lastItemId = itemId
                    _rotationOverride.value = null
                }
            }
        }
    }

    // -- Keep screen on ----------------------------------------------------
    //
    // Keep the screen awake while a video is playing: without this
    // Android applies the system screen timeout mid-video. View
    // .keepScreenOn is the standard video-player mechanism: on while
    // a video is playing or loading, off when paused, audio-only, or
    // in PiP. The lock button does not change it: a locked player is
    // still a video being watched.

    private val _isInPipMode = MutableStateFlow(false)

    /** The screen reports PiP entry/exit; the decision stays here. */
    fun setPipMode(inPipMode: Boolean) {
        _isInPipMode.value = inPipMode
    }

    /** True while a video is playing or loading and not in PiP. */
    val keepScreenOn: StateFlow<Boolean> = combine(
        playbackState,
        _isInPipMode,
    ) { state, inPipMode ->
        state.currentItem?.isVideo == true && !inPipMode &&
            (state.isPlaying || state.isPreparing)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        resolveKeepScreenOn(playbackState.value, _isInPipMode.value),
    )
}

/** Pure rotation resolution, kept testable through the ViewModel's flows. */
private fun resolveIsLandscape(override: String?, settings: AppSettings): Boolean =
    override?.equals("landscape", ignoreCase = true) == true ||
        (override == null && !settings.autoScreenRotation &&
            settings.screenOrientation.equals("landscape", ignoreCase = true))

private fun resolveTargetOrientation(override: String?, settings: AppSettings): Int =
    when {
        override.equals("landscape", ignoreCase = true) ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        override.equals("portrait", ignoreCase = true) ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        settings.autoScreenRotation || settings.screenOrientation == "auto" ||
            settings.screenOrientation == "sensor" ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        settings.screenOrientation.equals("portrait", ignoreCase = true) ->
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

private fun resolveKeepScreenOn(state: PlaybackState, inPipMode: Boolean): Boolean =
    state.currentItem?.isVideo == true && !inPipMode &&
        (state.isPlaying || state.isPreparing)
