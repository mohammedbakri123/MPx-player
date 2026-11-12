package com.mpx.player.ui.screens.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.Surface
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakePlaybackStateSource
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.PlaybackStateSource
import com.mpx.player.playback.engine.TrackType
import com.mpx.player.ui.screens.local.CreatePlaylistOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private lateinit var persistence: FakeMediaPersistenceRepository

    @Before
    fun setUp() {
        playback = FakePlaybackStateSource()
        settingsRepo = FakeSettingsRepository(
            AppSettings(
                autoScreenRotation = true,
                screenOrientation = "landscape",
            ),
        )
        persistence = FakeMediaPersistenceRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlayerViewModel(playback, settingsRepo, persistence)

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

    @Test
    fun `isFavourite reflects persistence repository and updates on toggle`() = runTest {
        setTestMainDispatcher()
        // toggleFavourite() launches fire-and-forget on ioDispatcher,
        // which defaults to the real Dispatchers.IO pool — asserting
        // repo state right after the call races that launch. Give the
        // VM the test dispatcher so the toggle lands deterministically.
        val viewModel = PlayerViewModel(
            playback,
            settingsRepo,
            persistence,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val v1 = video("v1")
        playback.emit(PlaybackState(currentItem = v1, isPlaying = true))

        assertFalse(viewModel.isFavourite.value)

        viewModel.toggleFavourite()
        runCurrent()
        assertTrue(persistence.isFavourite(v1.uri))
        assertTrue(viewModel.isFavourite.value)

        // Toggling again removes it, and the flow reflects the removal.
        viewModel.toggleFavourite()
        runCurrent()
        assertFalse(persistence.isFavourite(v1.uri))
        assertFalse(viewModel.isFavourite.value)
    }

    @Test
    fun `showAddToPlaylist, dismiss, and addPendingToPlaylist manage pending media`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val v1 = video("v1")

        viewModel.showAddToPlaylist(v1)
        assertTrue(viewModel.showAddToPlaylistDialog.value)
        assertEquals(v1, viewModel.pendingMediaForPlaylistAdd.value)

        val plId = persistence.createPlaylist("Watchlist", isVideoPlaylist = true)
        val added = viewModel.addPendingToPlaylist(plId)

        assertTrue(added)
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertEquals(null, viewModel.pendingMediaForPlaylistAdd.value)
        assertEquals(listOf(v1.uri), persistence.getPlaylistItems(plId).map { it.mediaUri })
    }

    @Test
    fun `createPlaylistAndAddPending creates playlist and adds pending media`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val v1 = video("v1")

        viewModel.showAddToPlaylist(v1)
        val outcome = viewModel.createPlaylistAndAddPending("Favorites", isVideo = true)

        assertEquals(CreatePlaylistOutcome.Added("Favorites", added = 1, total = 1), outcome)
        val pl = persistence.getPlaylists().first { it.name == "Favorites" }
        assertTrue(pl.isVideoPlaylist)
        assertEquals(listOf(v1.uri), persistence.getPlaylistItems(pl.id).map { it.mediaUri })
    }

    @Test
    fun `openCreatePlaylist and dismissCreatePlaylist manage dialog flags`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        val v1 = video("v1")

        viewModel.showAddToPlaylist(v1)
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        viewModel.openCreatePlaylist()
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertTrue(viewModel.showCreatePlaylistDialog.value)

        // Dismissing create with pending tracks returns to add dialog (#310)
        viewModel.dismissCreatePlaylist()
        assertFalse(viewModel.showCreatePlaylistDialog.value)
        assertTrue(viewModel.showAddToPlaylistDialog.value)

        viewModel.dismissAddToPlaylist()
        assertFalse(viewModel.showAddToPlaylistDialog.value)
        assertEquals(null, viewModel.pendingMediaForPlaylistAdd.value)
    }

    @Test
    fun `skipForward and skipBackward delegate delta to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.skipForward(10_000L)
        assertEquals(listOf(10_000L), playback.skipForwardCalls)
        assertEquals(listOf(10_000L), playback.seekCalls)

        viewModel.skipBackward(5_000L)
        assertEquals(listOf(5_000L), playback.skipBackwardCalls)
        assertEquals(listOf(10_000L, -5_000L), playback.seekCalls)
    }

    @Test
    fun `skipNext and skipPrevious delegate to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.skipNext()
        assertEquals(1, playback.skipNextCalls)

        viewModel.skipPrevious()
        assertEquals(1, playback.skipPreviousCalls)
    }

    @Test
    fun `shuffle, repeat, decoder, locked, and sleep timer actions delegate to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.setShuffleEnabled(true)
        assertEquals(listOf(true), playback.shuffleEnabledCalls)

        viewModel.cycleRepeatMode()
        assertEquals(1, playback.cycleRepeatModeCalls)

        viewModel.setDecoderMode(DecoderMode.SW)
        assertEquals(listOf(DecoderMode.SW), playback.decoderModeCalls)

        viewModel.setLocked(true)
        assertEquals(1, playback.lockedCalls)

        viewModel.setSleepTimer(45)
        assertEquals(listOf(45), playback.sleepTimerCalls)

        viewModel.cancelSleepTimer()
        assertEquals(1, playback.cancelSleepTimerCalls)
    }

    @Test
    fun `notification clearing actions delegate to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.clearResumedNotification()
        assertEquals(1, playback.clearResumedNotificationCalls)

        viewModel.clearDecoderFallbackNote()
        assertEquals(1, playback.clearDecoderFallbackNoteCalls)

        viewModel.clearUserNotification()
        assertEquals(1, playback.clearUserNotificationCalls)
    }

    @Test
    fun `track selection and subtitle loading delegate to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.selectTrack(TrackType.AUDIO, "aid_2")
        assertEquals(listOf(TrackType.AUDIO to "aid_2"), playback.selectTrackCalls)

        viewModel.selectAudioTrack(1, 2)
        assertEquals(listOf(1 to 2), playback.selectAudioTrackCalls)

        viewModel.selectSubtitleTrack(0, 1)
        assertEquals(listOf(0 to 1), playback.selectSubtitleTrackCalls)

        val uri = Uri.parse("file:///storage/emulated/0/subs.srt")
        val loaded = viewModel.loadExternalSubtitle(uri, "subs.srt", "text/plain")
        assertTrue(loaded)
        assertEquals(listOf(Triple(uri, "subs.srt", "text/plain")), playback.loadExternalSubtitleCalls)
    }

    @Test
    fun `subtitle settings, playback tweaks, and background active delegate to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        val subtitleSettings = AppSettings(subtitleSize = "Large", subtitleTextColor = "White")
        viewModel.applySubtitleSettings(subtitleSettings)
        assertEquals(listOf(subtitleSettings), playback.appliedSubtitleSettings)

        val tweakSettings = AppSettings(audioDelayMs = 200)
        viewModel.applyPlaybackTweaks(tweakSettings)
        assertEquals(listOf(tweakSettings), playback.appliedPlaybackTweaks)

        viewModel.setSubtitleMarginY(115)
        assertEquals(listOf(115), playback.setSubtitleMarginYCalls)

        viewModel.setVideoBackgroundActive(true)
        assertEquals(listOf(true), playback.videoBackgroundActiveCalls)

        viewModel.setBackgroundPlaying(true)
        assertEquals(listOf(true), playback.backgroundPlayingCalls)
    }

    @Test
    fun `surfaceChanged delegates dimensions to playback`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        viewModel.surfaceChanged(1280, 720)
        assertEquals(listOf(1280 to 720), playback.surfaceChangedCalls)
    }

    @Test
    fun `loadSettingsNow reads settingsRepository directly`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()
        settingsRepo.stored = settingsRepo.stored.copy(defaultPlaybackSpeed = 1.75f)

        val current = viewModel.loadSettingsNow()
        assertEquals(1.75f, current.defaultPlaybackSpeed)
    }

    @Test
    fun `addPendingToPlaylist returns false when pending media or repository is missing`() = runTest {
        setTestMainDispatcher()
        val viewModelWithoutRepo = PlayerViewModel(playback, settingsRepo, persistenceRepository = null)
        val v1 = video("v1")

        // No pending media set
        assertFalse(viewModelWithoutRepo.addPendingToPlaylist(1L))

        // Pending media set, but repository is null
        viewModelWithoutRepo.showAddToPlaylist(v1)
        assertFalse(viewModelWithoutRepo.addPendingToPlaylist(1L))
    }

    @Test
    fun `createPlaylistAndAddPending fails gracefully when repository is null or throws`() = runTest {
        setTestMainDispatcher()
        val v1 = video("v1")

        // 1. Repository is null
        val viewModelWithoutRepo = PlayerViewModel(playback, settingsRepo, persistenceRepository = null)
        viewModelWithoutRepo.showAddToPlaylist(v1)
        val outcomeNullRepo = viewModelWithoutRepo.createPlaylistAndAddPending("Test", isVideo = true)
        assertEquals(CreatePlaylistOutcome.Failed, outcomeNullRepo)

        // 2. Repository throws exception
        val viewModelWithRepo = createViewModel()
        viewModelWithRepo.showAddToPlaylist(v1)
        persistence.createPlaylistError = RuntimeException("Database disk full")

        val outcomeError = viewModelWithRepo.createPlaylistAndAddPending("Test", isVideo = true)
        assertEquals(CreatePlaylistOutcome.Failed, outcomeError)
        // Dialog should reopen with pending media preserved for retry (#310)
        assertFalse(viewModelWithRepo.showCreatePlaylistDialog.value)
        assertTrue(viewModelWithRepo.showAddToPlaylistDialog.value)
        assertEquals(v1, viewModelWithRepo.pendingMediaForPlaylistAdd.value)
    }

    @Test
    fun `createPlaylistAndAddPending creates playlist without pending media`() = runTest {
        setTestMainDispatcher()
        val viewModel = createViewModel()

        val outcome = viewModel.createPlaylistAndAddPending("Solo Playlist", isVideo = true)
        assertEquals(CreatePlaylistOutcome.Created("Solo Playlist"), outcome)
        assertFalse(viewModel.showCreatePlaylistDialog.value)
        assertEquals(listOf("Solo Playlist"), persistence.getPlaylists().map { it.name })
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

}
