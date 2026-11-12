package com.mpx.player.ui.screens.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.PlaybackStateSource
import com.mpx.player.playback.engine.TrackType
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
        settingsRepo = FakeSettingsRepository(
            AppSettings(
                autoScreenRotation = true,
                screenOrientation = "landscape",
            ),
        )
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
    fun `transport commands delegate to playback seam`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.play()
        assertEquals(1, playback.playCalls)

        viewModel.pause()
        assertEquals(1, playback.pauseCalls)

        viewModel.togglePlayPause()
        assertEquals(1, playback.togglePlayPauseCalls)

        viewModel.seekTo(15_000L)
        assertEquals(listOf(15_000L), playback.seekCalls)

        viewModel.setSpeed(1.5f)
        assertEquals(listOf(1.5f), playback.speedCalls)

        viewModel.setAspectRatio(AspectRatioMode.ZOOM)
        assertEquals(listOf(AspectRatioMode.ZOOM), playback.aspectRatioCalls)

        viewModel.setVideoZoom(1.75f)
        assertEquals(listOf(1.75f), playback.videoZoomCalls)

        viewModel.stop()
        assertEquals(1, playback.stopCalls)

        assertEquals(1920, viewModel.videoWidth)
        assertEquals(1080, viewModel.videoHeight)
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

    /** Fake playback source: records restore and transport calls, lets the test
     * drive state emissions. */
    private class FakePlaybackStateSource(
        initialState: PlaybackState = PlaybackState(),
    ) : PlaybackStateSource {
        private val _playbackState = MutableStateFlow(initialState)
        override val playbackState: StateFlow<PlaybackState> =
            _playbackState.asStateFlow()

        var restoreCalls = 0
            private set
        var playCalls = 0
        var pauseCalls = 0
        var togglePlayPauseCalls = 0
        val seekCalls = mutableListOf<Long>()
        val speedCalls = mutableListOf<Float>()
        val aspectRatioCalls = mutableListOf<AspectRatioMode>()
        val videoZoomCalls = mutableListOf<Float>()
        var stopCalls = 0

        override fun restorePersistedQueueIfEmpty(): Boolean {
            restoreCalls++
            return true
        }

        override fun play() { playCalls++ }
        override fun pause() { pauseCalls++ }
        override fun togglePlayPause() { togglePlayPauseCalls++ }
        override fun seekTo(positionMs: Long) { seekCalls.add(positionMs) }
        override fun skipForward(deltaMs: Long) { seekCalls.add(deltaMs) }
        override fun skipBackward(deltaMs: Long) { seekCalls.add(-deltaMs) }
        override fun skipNext() {}
        override fun skipPrevious() {}
        override fun setSpeed(speed: Float) { speedCalls.add(speed) }
        override fun setAspectRatio(mode: AspectRatioMode) { aspectRatioCalls.add(mode) }
        override fun setVideoZoom(zoom: Float) { videoZoomCalls.add(zoom) }
        override fun setShuffleEnabled(enabled: Boolean) {}
        override fun cycleRepeatMode() {}
        override fun setDecoderMode(mode: DecoderMode) {}
        override fun setLocked(locked: Boolean) {}
        override fun setSleepTimer(minutes: Int) {}
        override fun cancelSleepTimer() {}
        override fun clearResumedNotification() {}
        override fun clearDecoderFallbackNote() {}
        override fun clearUserNotification() {}
        override fun stop() { stopCalls++ }
        override fun attachSurface(surface: Surface) {}
        override fun detachSurface() {}
        override fun surfaceChanged(width: Int, height: Int) {}
        override fun applySubtitleSettings(settings: AppSettings) {}
        override fun applyPlaybackTweaks(settings: AppSettings) {}
        override fun setVideoBackgroundActive(active: Boolean) {}
        override fun setBackgroundPlaying(active: Boolean) {}
        override fun selectTrack(type: TrackType, id: String?) {}
        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {}
        override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {}
        override fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean = true

        override val videoWidth: Int = 1920
        override val videoHeight: Int = 1080

        fun emit(state: PlaybackState) {
            _playbackState.value = state
        }
    }

}
