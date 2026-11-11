package com.mpx.player.ui.screens.player

import android.content.pm.ActivityInfo
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.PlaybackStateSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Migration Phase 2a: the first unit tests in the repo. The
 * ViewModel is tested through fakes only — a fake
 * [PlaybackStateSource] and a fake [AppSettingsRepository] — no
 * mocking framework, no Android runtime. `ActivityInfo` orientation
 * constants are compile-time ints, so asserting on them needs no
 * device either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private lateinit var playback: FakePlaybackStateSource
    private lateinit var settingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        playback = FakePlaybackStateSource()
        settingsRepo = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlayerViewModel(playback, settingsRepo)

    /** viewModelScope runs on Dispatchers.Main; the unconfined test
     * dispatcher executes the ViewModel's collectors eagerly, so
     * StateFlow values are observable without advancing a scheduler. */
    private fun setTestMainDispatcher(): Unit =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    @Test
    fun `reloadSettings picks up changes made outside the player`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertEquals("landscape", viewModel.settings.value.screenOrientation)

        // Settings changed elsewhere (e.g. the Settings screen while
        // the player sat in the back stack) — issue #241.
        settingsRepo.stored = settingsRepo.stored.copy(
            screenOrientation = "portrait",
            autoScreenRotation = false,
        )
        viewModel.reloadSettings()

        assertEquals("portrait", viewModel.settings.value.screenOrientation)
        assertFalse(viewModel.settings.value.autoScreenRotation)
    }

    @Test
    fun `updateSettings persists and publishes`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val updated = AppSettings(backgroundPlay = true, videoZoom = 1.5f)

        viewModel.updateSettings(updated)

        assertEquals(updated, settingsRepo.stored)
        assertEquals(updated, viewModel.settings.value)
    }

    @Test
    fun `restoreQueueIfEmpty restores only when the queue is empty`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        assertTrue(viewModel.restoreQueueIfEmpty())
        assertEquals(1, playback.restoreCalls)

        // Queue now non-empty: the restore must not run again.
        playback.emit(
            PlaybackState(
                currentItem = video("v1"),
                queue = listOf(video("v1")),
                currentIndex = 0,
            ),
        )
        assertFalse(viewModel.restoreQueueIfEmpty())
        assertEquals(1, playback.restoreCalls)
    }

    @Test
    fun `rotation toggle flips landscape and back in Auto mode`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        // Default settings: autoScreenRotation = true -> portrait.
        assertFalse(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            viewModel.targetOrientation.value,
        )

        viewModel.toggleRotation()
        assertTrue(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            viewModel.targetOrientation.value,
        )

        viewModel.toggleRotation()
        assertFalse(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            viewModel.targetOrientation.value,
        )
    }

    @Test
    fun `rotation follows the locked setting and the override wins`() = runTest {
        setTestMainDispatcher()
        settingsRepo.stored = AppSettings(
            autoScreenRotation = false,
            screenOrientation = "landscape",
        )
        val viewModel = createViewModel()

        // Locked landscape, no override yet.
        assertTrue(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            viewModel.targetOrientation.value,
        )

        // Toggle overrides to portrait.
        viewModel.toggleRotation()
        assertFalse(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            viewModel.targetOrientation.value,
        )
    }

    @Test
    fun `rotation override resets when the next video starts`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        playback.emit(PlaybackState(currentItem = video("v1"), isPlaying = true))

        viewModel.toggleRotation()
        assertTrue(viewModel.isLandscapeOrientation.value)

        // Next video: the override resets, portrait again (issue #363).
        playback.emit(PlaybackState(currentItem = video("v2"), isPlaying = true))
        assertFalse(viewModel.isLandscapeOrientation.value)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            viewModel.targetOrientation.value,
        )
    }

    @Test
    fun `keepScreenOn is on only for a playing or loading video outside PiP`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        assertFalse(viewModel.keepScreenOn.value)

        playback.emit(PlaybackState(currentItem = video("v1"), isPlaying = true))
        assertTrue(viewModel.keepScreenOn.value)

        // Loading counts as playing for this decision.
        playback.emit(
            PlaybackState(currentItem = video("v1"), isPlaying = false, isPreparing = true),
        )
        assertTrue(viewModel.keepScreenOn.value)

        // Paused: off.
        playback.emit(PlaybackState(currentItem = video("v1"), isPlaying = false))
        assertFalse(viewModel.keepScreenOn.value)

        // Audio-only, even while playing: off.
        playback.emit(
            PlaybackState(currentItem = audio("a1"), isPlaying = true),
        )
        assertFalse(viewModel.keepScreenOn.value)

        // Playing video, but in PiP: off.
        playback.emit(PlaybackState(currentItem = video("v1"), isPlaying = true))
        viewModel.setPipMode(true)
        assertFalse(viewModel.keepScreenOn.value)
        viewModel.setPipMode(false)
        assertTrue(viewModel.keepScreenOn.value)
    }

    private fun video(id: String) = PlayableMedia(
        id = id,
        title = "Video $id",
        uri = "content://video/$id",
        isVideo = true,
    )

    private fun audio(id: String) = PlayableMedia(
        id = id,
        title = "Audio $id",
        uri = "content://audio/$id",
        isVideo = false,
    )

    /** Fake playback source: records restore calls, lets the test
     * drive state emissions. */
    private class FakePlaybackStateSource(
        initialState: PlaybackState = PlaybackState(),
    ) : PlaybackStateSource {
        private val _playbackState = MutableStateFlow(initialState)
        override val playbackState: StateFlow<PlaybackState> =
            _playbackState.asStateFlow()

        var restoreCalls = 0
            private set

        override fun restorePersistedQueueIfEmpty(): Boolean {
            restoreCalls++
            return true
        }

        fun emit(state: PlaybackState) {
            _playbackState.value = state
        }
    }

    /** Fake settings repository: an in-memory stored value. */
    private class FakeSettingsRepository(
        var stored: AppSettings = AppSettings(
            autoScreenRotation = true,
            screenOrientation = "landscape",
        ),
    ) : AppSettingsRepository {
        override fun load(): AppSettings = stored

        override fun save(settings: AppSettings, commit: Boolean) {
            stored = settings
        }
    }
}
