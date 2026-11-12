package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.playback.engine.MpvPlaybackException
import com.mpx.player.playback.engine.MpvPlayerEngine
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.TrackType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The string resources the coordinator needs. The production
 * implementation reads them from a [Context]; tests pass a fake that
 * returns a marker string, so the codec-error branch is pinned
 * without an Android runtime (the same split the extracted pieces
 * already use for their own Android seams).
 */
interface CoordinatorStrings {
    /** The "Playback failed: unsupported codec <name>" banner. */
    fun codecError(codecName: String): String

    /** The "Loaded subtitle: <name>" banner. */
    fun subtitleLoaded(displayName: String): String

    /** The "Failed to load subtitle: <name>" banner. */
    fun subtitleFailed(displayName: String): String

    /** The background-playback-active banner. */
    fun backgroundActiveBanner(): String
}

/**
 * Starting and stopping the foreground playback service. The
 * production implementation calls [PlaybackService]; tests pass a
 * fake that records the calls, so the service itself is never
 * started from a unit test.
 */
interface BackgroundService {
    fun start()
    fun stop()
}

/**
 * Playback coordinator: the small public face of playback that the
 * ViewModels and screens talk to (migration Phase 3, step 5: the
 * old PlaybackManager shell is deleted, this class is the only
 * playback owner).
 *
 * It owns the published [PlaybackState], the position ticker, the
 * preparing watchdog, the sleep timer, transport and session
 * functions, and it builds the engine, the database/repositories,
 * the resume manager, and the three extracted pieces
 * ([SubtitleManager], [PlaybackQueue], [EngineEventTranslator]) —
 * and it is their host ([SubtitleHost], [QueueHost],
 * [EngineEventHost]), so every state write still goes through
 * exactly one class.
 *
 * No behaviour change from the old PlaybackManager code this was
 * moved from — the bodies are verbatim except for the seams below.
 *
 * Seams (all constructor parameters, so the class is unit-testable
 * with fakes and no Android runtime):
 * - The collaborators arrive already built via constructor parameters: [engine],
 *   [database], [persistenceRepository], [resumeManager], [settingsRepository],
 *   [subtitleManager], [playbackQueue], and [engineEventTranslator].
 * - Production wiring lives in the [production] factory, which serves as the real
 *   composition root (R-5). In Phase 3 (D-10), Koin singles ([database],
 *   [persistenceRepository], [settingsRepository]) are injected into [production],
 *   which coordinates engine initialization and collaborator construction.
 *   As documented in R-5 and §5, [SubtitleManager], [PlaybackQueue], and
 *   [EngineEventTranslator] take the coordinator as their [SubtitleHost],
 *   [QueueHost], and [EngineEventHost]; because this circular relationship
 *   cannot be expressed in Koin, they are constructed in [production] and passed
 *   into the coordinator constructor (they are coordinator-owned, NOT Koin singles).
 * - For testability, constructor defaults allow unit tests to pass custom fakes
 *   or let the constructor instantiate default collaborators taking the coordinator
 *   as host when [subtitleFileAccess] and [queueStore] are supplied.
 * - [strings], [backgroundService], and [errorLog] cover the three
 *   Android-only effects the coordinator itself performs (banner
 *   texts, the foreground service, and the engine-error logcat line);
 *   the pieces already had their own seams. [errorLog] defaults to
 *   [Log.e] with the "PlaybackManager" tag, so existing logcat
 *   filters keep working; tests record the calls instead (a direct
 *   [Log] call would throw in JVM unit tests, where Android methods
 *   are not mocked).
 * - [clock] is the elapsed-realtime source for the sleep timer
 *   (production: [SystemClock.elapsedRealtime]); tests tie it to
 *   the test scheduler so the timer can be driven by virtual time.
 * - [ioDispatcher] is where history rows are recorded
 *   (production: [Dispatchers.IO]); tests pass an unconfined test
 *   dispatcher so the fire-and-forget writes are observable.
 * - [scope] is the Main-scope the ticker, watchdog, sleep timer,
 *   and the pieces' deferred work run in (production:
 *   `CoroutineScope(SupervisorJob() + Dispatchers.Main)`).
 *   Scope lifecycle note (D-2): in production, this scope intentionally has
 *   no cancellation point because the coordinator is a process-lifetime
 *   singleton. Neither MainActivity nor PlaybackService owns the scope;
 *   cancelling it on Activity or Service destruction would break
 *   transitions between background and foreground playback. Transient
 *   and looping jobs launched on this scope (ticker, watchdog, sleep timer)
 *   are individually tracked and cancelled per session. SupervisorJob
 *   ensures child failures do not cancel sibling jobs.
 *
 * What unit tests cover: transport delegation to the engine, state
 * publishing (including the decoder-mode seed from settings), the
 * sleep timer and the position ticker under virtual time, the
 * preparing watchdog firing and being cancelled, stop/release, and
 * the play/pause effects through the real translator. What they
 * cannot cover, and can only be verified on device: real mpv
 * playback and event timing, the real watchdog against a stuck
 * load, the background service actually starting, and the localised
 * banner texts themselves (the fake strings return markers).
 *
 * Lifetime note (D-11): the coordinator and underlying engine are
 * intentionally process-lifetime singles in production. They must NOT
 * be wired to component lifecycles (MainActivity or PlaybackService).
 */
class PlaybackCoordinator(
    private val database: MpxDatabase,
    private val persistenceRepository: MediaPersistenceRepository,
    private val resumeManager: PlaybackResumeManager,
    private val settingsRepository: AppSettingsRepository,
    private val engine: PlayerEngine,
    private val queueStore: QueueStore? = null,
    private val subtitleFileAccess: SubtitleManager.FileAccess? = null,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val strings: CoordinatorStrings,
    private val backgroundService: BackgroundService,
    private val audioFocusManager: AudioFocusManager = AudioFocusManager.NoOp,
    initialDecoderMode: DecoderMode = resolveInitialDecoderModeFrom(settingsRepository),
    private val errorLog: (message: String, error: Throwable?) -> Unit =
        { message, error -> Log.e("PlaybackManager", message, error) },
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    subtitleManager: SubtitleManager? = null,
    playbackQueue: PlaybackQueue? = null,
    engineEventTranslator: EngineEventTranslator? = null,
    stateStore: PlaybackStateStore? = null,
) : PlaybackStateSource, StreamPlayer, SleepTimerHost, PositionSink {

    val stateStore: PlaybackStateStore = stateStore ?: PlaybackStateStore(
        initialDecoderMode = initialDecoderMode,
        strings = strings,
    )
    private val _playbackState: PlaybackStateStore get() = this.stateStore
    override val playbackState: StateFlow<PlaybackState> = this.stateStore.playbackState

    private val positionTracker = PositionTracker(
        engine = engine,
        resumeManager = resumeManager,
        scope = scope,
        sink = this,
    )
    private val sleepTimer = SleepTimer(
        scope = scope,
        clock = clock,
        host = this,
    )
    private var pausedForTransientFocus: Boolean = false

    /** Preparing watchdog: a video load that produces neither READY nor an
     * error within 20s is a stuck load, not a slow one (local files). Firing
     * routes a real timeout error through onError, which clears the splash
     * and names the observed stage in a banner. Armed per video load;
     * cancelled on READY/ENDED, on any error, and on stop. The timeout
     * message names the observed mpv stage, so the banner itself becomes
     * the next diagnostic. */
    private var preparingWatchdogJob: Job? = null
    private fun cancelPreparingWatchdog() {
        preparingWatchdogJob?.cancel()
        preparingWatchdogJob = null
    }
    private fun armPreparingWatchdog() {
        val item = _playbackState.value.currentItem ?: return
        if (!item.isVideo) return
        cancelPreparingWatchdog()
        preparingWatchdogJob = scope.launch {
            delay(20_000L)
            val st = _playbackState.value
            if (st.isPreparing && st.currentItem?.id == item.id &&
                st.playbackState != PlayerEngine.STATE_READY && st.playbackState != PlayerEngine.STATE_ENDED
            ) {
                val stage = engine.getLoadStage()
                val detail = if (stage != null) {
                    "engine=MPV, initialized=${stage.initialized}, " +
                        "surface=${stage.surfaceAttached}, loadIssued=${stage.loadIssued}, " +
                        "lastEvent=${stage.lastEvent}"
                } else {
                    "engine=MPV"
                }
                engineEventTranslator.onError(Exception("Playback load timed out after 20s ($detail)"))
            }
        }
    }

    private var activeSurface: Surface? = null

    /**
     * External subtitle handling (R-5).
     * Injected via constructor or constructed with stateStore as [SubtitleHost].
     */
    internal val subtitleManager: SubtitleManager = subtitleManager ?: SubtitleManager(
        engine = engine,
        host = this.stateStore,
        scope = scope,
        fileAccess = subtitleFileAccess ?: error("subtitleFileAccess required when subtitleManager is not injected"),
        ioDispatcher = ioDispatcher,
    )

    /**
     * Playback queue handling (R-5).
     * Injected via constructor or constructed with stateStore as [QueueHost].
     */
    internal val playbackQueue: PlaybackQueue = playbackQueue ?: PlaybackQueue(
        engine = engine,
        queueStore = queueStore ?: error("queueStore required when playbackQueue is not injected"),
        resumeManager = resumeManager,
        settingsRepository = settingsRepository,
        host = this.stateStore,
        scope = scope,
    )

    /**
     * Engine event translation (R-5).
     * Injected via constructor or constructed with stateStore as [EngineEventHost].
     */
    internal val engineEventTranslator: EngineEventTranslator = engineEventTranslator ?: EngineEventTranslator(
        engine = engine,
        queue = this.playbackQueue,
        subtitleManager = this.subtitleManager,
        resumeManager = resumeManager,
        settingsRepository = settingsRepository,
        host = this.stateStore,
        scope = scope,
        ioDispatcher = ioDispatcher,
    )

    init {
        this.stateStore.setEventListener { handlePlaybackEvent(it) }
        engine.setListener(this.engineEventTranslator)
        engine.setQueueSource(
            queue = { this.playbackQueue.items },
            // The queue's own index is the source of truth: -1 exactly
            // when the queue is empty (initial, clearForStop) and >= 0
            // whenever a session is live (every persistQueue call sets
            // it). Falling back to the published state's currentIndex
            // was wrong — stop() clears the queue but leaves the state's
            // index at the last session's value, so the engine would
            // keep seeing a stale index after a stop.
            currentIndex = { this.playbackQueue.currentIndex },
        )
        audioFocusManager.setListener(object : AudioFocusManager.Listener {
            override fun onAudioFocusLost(isTransient: Boolean, canDuck: Boolean) {
                if (isTransient) {
                    if (engine.isPlaying) {
                        pausedForTransientFocus = true
                        engine.pause()
                    }
                } else {
                    pausedForTransientFocus = false
                    audioFocusManager.abandonAudioFocus()
                    pause()
                }
            }

            override fun onAudioFocusGained() {
                if (pausedForTransientFocus) {
                    pausedForTransientFocus = false
                    // Resume unless the item ended while paused: the flag
                    // alone proves a session was playing when focus was
                    // lost (stop()/release() clear it), and playbackState
                    // is IDLE while a load is still preparing — publishSession
                    // never sets it, only the engine's READY report does —
                    // so an IDLE check here would also block a legitimate
                    // resume in the preparing window. ENDED is the only
                    // state where resuming would restart a finished item.
                    if (_playbackState.value.playbackState != PlayerEngine.STATE_ENDED) {
                        engine.play()
                    }
                }
            }

            override fun onAudioBecomingNoisy() {
                pause()
            }
        })
    }

    private fun handlePlaybackEvent(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.StartPositionTicker -> startTicker()
            is PlaybackEvent.StopPositionTicker -> stopTicker()
            is PlaybackEvent.RefreshPosition -> updatePosition()
            is PlaybackEvent.RefreshPositionAndTracks -> {
                updatePosition()
                updateTracksState()
            }
            is PlaybackEvent.RefreshTracksState -> updateTracksState()
            is PlaybackEvent.FlushPosition -> updatePosition(forceSave = true)
            is PlaybackEvent.ArmLoadWatchdog -> armPreparingWatchdog()
            is PlaybackEvent.CancelLoadWatchdog -> cancelPreparingWatchdog()
            is PlaybackEvent.StartBackgroundService -> backgroundService.start()
            is PlaybackEvent.RecordHistory -> {
                scope.launch(ioDispatcher) {
                    try {
                        persistenceRepository.recordHistory(
                            PlaybackHistoryItem(
                                mediaUri = event.item.uri,
                                mediaId = event.item.id,
                                title = event.item.title,
                                subtitle = event.item.subtitle,
                                isVideo = event.item.isVideo,
                                lastPlayedAt = System.currentTimeMillis(),
                                playbackPositionMs = event.positionMs,
                                durationMs = event.item.durationMs,
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            is PlaybackEvent.RecordPauseHistory -> {
                scope.launch(ioDispatcher) {
                    try {
                        persistenceRepository.recordHistory(
                            PlaybackHistoryItem(
                                mediaUri = event.item.uri,
                                mediaId = event.item.id,
                                title = event.item.title,
                                subtitle = event.item.subtitle,
                                isVideo = event.item.isVideo,
                                lastPlayedAt = System.currentTimeMillis(),
                                playbackPositionMs = event.positionMs,
                                durationMs = event.durationMs,
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            is PlaybackEvent.ClearAutoDetectAttempts -> {
                subtitleManager.clearAutoDetectAttempts()
            }
            is PlaybackEvent.MaybeAutoDetectSubtitles -> {
                subtitleManager.maybeAutoDetectSubtitles()
            }
            is PlaybackEvent.CommitSubtitleItem -> {
                playbackQueue.replaceItem(event.index, event.updatedItem)
                _playbackState.update { state ->
                    val withItem = state.copy(currentItem = event.updatedItem, queue = playbackQueue.items)
                    if (event.notification != null) withItem.copy(userNotification = event.notification) else withItem
                }
                playbackQueue.persistQueue(event.index)
            }
            is PlaybackEvent.EngineError -> {
                errorLog("Engine error encountered", event.error)
            }
        }
    }

    override val currentItem: PlayableMedia?
        get() = _playbackState.value.currentItem
    val liveQueue: List<PlayableMedia>
        get() = playbackQueue.items
    val subtitleTracks: List<SubtitleTrackInfo>
        get() = _playbackState.value.subtitleTracks
    val state: PlaybackState
        get() = _playbackState.value

    fun subtitleLoadedMessage(displayName: String): String =
        strings.subtitleLoaded(displayName)

    fun subtitleFailedMessage(displayName: String): String =
        strings.subtitleFailed(displayName)

    fun showNotification(message: String) {
        stateStore.showNotification(message)
    }

    /**
     * Issue #225: display size of the current video (engine's
     * `dwidth`/`dheight`), the PiP aspect-ratio source of truth. 0 when
     * no video is loaded.
     */
    override val videoWidth: Int
        get() = engine.videoWidth

    override val videoHeight: Int
        get() = engine.videoHeight

    /**
     * Attaches a native video surface to the active playback engine.
     */
    override fun attachSurface(surface: Surface) {
        activeSurface = surface
        engine.attachSurface(surface)
    }

    /**
     * Forwards SurfaceView size changes to the engine (mpv reports them as
     * "android-surface-size").
     */
    override fun surfaceChanged(width: Int, height: Int) {
        engine.onSurfaceChanged(width, height)
    }

    /**
     * Detaches the video surface from the active playback engine.
     */
    override fun detachSurface() {
        activeSurface = null
        engine.detachSurface()
    }

    /**
     * Applies subtitle style settings to the mpv engine, which renders
     * subtitles itself. Delegates to [subtitleManager]; behaviour is
     * unchanged.
     */
    override fun applySubtitleSettings(settings: AppSettings) {
        subtitleManager.applySubtitleSettings(settings)
    }

    /**
     * Applies audio delay + video zoom to the active engine (issue #153).
     * mpv-only, mirroring [applySubtitleSettings].
     */
    override fun applyPlaybackTweaks(settings: AppSettings) {
        engine.applyPlaybackTweaks(settings)
    }

    override fun setSubtitleMarginY(margin: Int) {
        engine.setSubtitleMarginY(margin)
    }

    /**
     * Live video-zoom for the pinch gesture (#172): applies immediately
     * without persisting; the caller saves the final zoom to settings
     * when the gesture ends.
     */
    override fun setVideoZoom(zoom: Float) {
        engine.setVideoZoom(zoom)
    }


    /**
     * Loads a single media item with optional resume position and begins playback.
     * An explicit [resumeMs] is a direct user request (e.g. tapping a Continue
     * Watching card), so it bypasses the automatic-resume toggle; plain queue
     * playback still honours the resumePlayback setting.
     */
    fun load(media: PlayableMedia, resumeMs: Long = 0L) {
        if (resumeMs > 0L) {
            // Issue #227: the manager keys this row by media.uri, not the
            // bare id — both are passed so the key can be derived.
            // Issue #274: force — an explicit user-chosen resume position
            // must land in Room at once, not at the next throttle window.
            resumeManager.saveResumePosition(media.id, resumeMs, media.uri, media.durationMs, force = true)
        }
        playQueue(listOf(media), startIndex = 0, autoPlay = true, forceResume = resumeMs > 0L)
    }

    /**
     * Migration Phase 2d ([StreamPlayer]): plays a stream URL as a
     * one-item queue. Moved verbatim from NetworkScreen's old inline
     * play action — the title is the URL's last path segment, or
     * "Network Stream" when there is none.
     */
    override fun playStream(url: String) {
        val streamTitle = url.substringAfterLast("/").substringBefore("?").ifBlank { "Network Stream" }
        playQueue(
            listOf(
                PlayableMedia(
                    id = "stream_${System.currentTimeMillis()}",
                    title = streamTitle,
                    subtitle = url,
                    uri = url,
                    isVideo = true,
                    durationMs = 0L,
                ),
            ),
        )
    }

    /**
     * Starts [items] as a new playback session from [startIndex].
     * Delegates to [playbackQueue]; behaviour is unchanged. See
     * [PlaybackQueue.playQueue] for the session rules (#203 shuffle,
     * #239 speed, #220 preload, #149 persist).
     */
    fun playQueue(
        items: List<PlayableMedia>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        forceResume: Boolean = false,
        preserveShuffleState: Boolean = false,
    ) {
        if (autoPlay) {
            val granted = audioFocusManager.requestAudioFocus()
            if (!granted) return
            pausedForTransientFocus = false
        }
        playbackQueue.playQueue(items, startIndex, autoPlay, forceResume, preserveShuffleState)
    }

    /**
     * Plays the item at [index] in the LIVE queue (issue #203).
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playLiveQueueItem(index: Int) {
        val granted = audioFocusManager.requestAudioFocus()
        if (!granted) return
        pausedForTransientFocus = false
        playbackQueue.playLiveQueueItem(index)
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playAudioQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playbackQueue.playAudioQueue(tracks, startIndex)
    }

    /**
     * Play real video files from a MediaStore folder. Empty list = no-op.
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playVideoFiles(videos: List<VideoFile>, startIndex: Int = 0) {
        playbackQueue.playVideoFiles(videos, startIndex)
    }

    override fun play() {
        val granted = audioFocusManager.requestAudioFocus()
        if (!granted) return
        pausedForTransientFocus = false
        // Issue #211: at EOF mpv stays eof-reached, so un-pausing alone
        // cannot restart — only a seek to 0 can. This is the MediaSession
        // onPlay() / notification Play path; togglePlayPause() below has
        // the same guard for the on-screen Play buttons.
        if (_playbackState.value.playbackState == PlayerEngine.STATE_ENDED) {
            engine.seekTo(0L)
        }
        engine.play()
    }

    override fun pause() {
        pausedForTransientFocus = false
        audioFocusManager.abandonAudioFocus()
        engine.pause()
    }

    override fun togglePlayPause() {
        if (engine.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        updatePosition()
    }

    override fun skipForward(deltaMs: Long) {
        seekTo(engine.currentPosition + deltaMs)
    }

    override fun skipBackward(deltaMs: Long) {
        seekTo(engine.currentPosition - deltaMs)
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    override fun skipNext() {
        playbackQueue.skipNext()
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    override fun skipPrevious() {
        playbackQueue.skipPrevious()
    }

    override fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    /**
     * Cycles OFF -> ALL -> ONE -> OFF. Delegates to [playbackQueue];
     * behaviour is unchanged.
     */
    override fun cycleRepeatMode() {
        playbackQueue.cycleRepeatMode()
    }

    /**
     * Toggles shuffle (issue #76). Delegates to [playbackQueue];
     * behaviour is unchanged.
     */
    override fun setShuffleEnabled(enabled: Boolean) {
        playbackQueue.setShuffleEnabled(enabled)
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
        engine.setAspectRatio(mode)
    }

    /**
     * Changes the decoder mode (HW, HW+, SW). The mpv engine applies the
     * new hwdec value and reloads the current item so the pill matches the
     * decoder actually in use.
     *
     * Known limit (issue #150): that reload is engine-internal and re-uses
     * the queue's old fd:// URI. For a content:// file (blank MediaStore
     * DATA) mpv has already closed that fd, so the reload can fail with a
     * real error banner; re-tapping the file replays it under the new mode
     * (playQueue re-opens a fresh fd). Path-based files are unaffected.
     */
    override fun setDecoderMode(mode: DecoderMode) {
        if (_playbackState.value.decoderMode == mode) return

        engine.setDecoderMode(mode)

        _playbackState.update {
            it.copy(
                decoderMode = mode,
                activeDecoderName = null,
                decoderFallbackNote = null,
            )
        }
    }

    override fun setLocked(locked: Boolean) {
        _playbackState.update { it.copy(isLocked = locked) }
    }

    override fun selectTrack(type: TrackType, id: String?) {
        engine.selectTrack(type, id)
        updateTracksState()
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        engine.selectAudioTrack(groupIndex, trackIndex)
        updateTracksState()
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        subtitleManager.selectSubtitleTrack(groupIndex, trackIndex)
    }

    /**
     * Loads an external subtitle file (SRT / VTT) into the currently playing media item.
     * Delegates to [subtitleManager], which owns the load path
     * (issue #332: the returned boolean is whether the subtitle was
     * ACCEPTED for loading, and the success/failure banner arrives
     * asynchronously). Behaviour is unchanged.
     */
    override fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean {
        return subtitleManager.loadExternalSubtitle(uri.toString(), displayName, mimeType)
    }

    override fun clearDecoderFallbackNote() {
        _playbackState.update { it.copy(decoderFallbackNote = null) }
    }

    override fun clearUserNotification() {
        _playbackState.update { it.copy(userNotification = null) }
    }

    override fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
    }

    override fun cancelSleepTimer() {
        sleepTimer.cancel()
    }

    override fun onSleepTimerStarted(minutes: Int, remainingSeconds: Long) {
        _playbackState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = remainingSeconds,
            )
        }
    }

    override fun onSleepTimerTick(remainingSeconds: Long) {
        _playbackState.update {
            it.copy(sleepTimerRemainingSeconds = remainingSeconds)
        }
    }

    override fun onSleepTimerExpired() {
        _playbackState.update {
            it.copy(
                sleepTimerMinutes = null,
                sleepTimerRemainingSeconds = null,
            )
        }
        pause()
    }

    override fun onSleepTimerCancelled() {
        _playbackState.update {
            it.copy(
                sleepTimerMinutes = null,
                sleepTimerRemainingSeconds = null,
            )
        }
    }

    /**
     * Restores the last persisted queue when the live queue is empty
     * (issue #149). Delegates to [playbackQueue]; behaviour is
     * unchanged.
     */
    override fun restorePersistedQueueIfEmpty(): Boolean {
        return playbackQueue.restorePersistedQueueIfEmpty()
    }

    override fun stop() {
        pausedForTransientFocus = false
        audioFocusManager.abandonAudioFocus()
        cancelSleepTimer()
        cancelPreparingWatchdog()
        engine.stop()
        stopTicker()
        backgroundService.stop()
        // Issue #258: a deliberate stop ends the session — the live
        // queue, the persisted queue, and any deferred playQueue load
        // all go; see PlaybackQueue.clearForStop. (One reordering:
        // the persisted-queue clear now runs before the subtitle
        // auto-detect clear below instead of after it; the two touch
        // independent state.) Otherwise the fallback reload's
        // onMediaItemTransition (or any other in-flight transition)
        // resurrects currentItem in state and persistQueue() re-saves
        // the queue stop() just cleared from the store. With the queue
        // empty, a late transition resolves to null and persistQueue
        // re-saves nothing (save() clears on an empty list).
        playbackQueue.clearForStop()
        subtitleManager.clearAutoDetectAttempts()
        // Issue #149: a deliberate stop ends the session, so the persisted
        // queue must not resurrect it later. This is also what makes the
        // stop-on-leave effect (PlayerScreen) safe: leaving via back clears
        // both the live and the persisted queue. release() deliberately does
        // NOT clear: it can run while the Player destination is still on the
        // stack (activity destroyed, process alive), and clearing there would
        // destroy the queue a later process-death restore needs.
        _playbackState.update {
            it.copy(
                currentItem = null,
                queue = emptyList(),
                shuffleEnabled = false,
                isPlaying = false,
                isBackgroundPlaying = false,
                isVideoBackgroundActive = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                isPreparing = false,
                playbackMetrics = null,
                mpvLoadStage = null,
            )
        }
    }

    /**
     * Releases player and coordinator resources.
     *
     * **Test-only lifecycle method (D-11).**
     *
     * In production, the coordinator and underlying engine are intentionally
     * process-lifetime singles. Do **NOT** wire this method to any Android component
     * lifecycle:
     * - [PlaybackService.onDestroy] can fire while [MainActivity] is active and visible
     *   (e.g. background service stopped after playback pause).
     * - [MainActivity.onDestroy] can fire during configuration changes or when
     *   background playback continues in [PlaybackService].
     *
     * Wiring [release] to either component lifecycle destroys the native mpv instance while
     * another component holds the coordinator, causing black video or native crashes.
     * In production, the native mpv instance lives for the process lifetime and is reclaimed
     * on process termination. This method exists exclusively for unit tests to tear down fixtures.
     */
    @VisibleForTesting
    fun release() {
        pausedForTransientFocus = false
        audioFocusManager.abandonAudioFocus()
        audioFocusManager.setListener(null)
        cancelSleepTimer()
        cancelPreparingWatchdog()
        engine.release()
        stopTicker()
        backgroundService.stop()
    }

    override fun setVideoBackgroundActive(active: Boolean) {
        _playbackState.update {
            it.copy(
                isVideoBackgroundActive = active,
                isBackgroundPlaying = active,
                userNotification = if (active) {
                    strings.backgroundActiveBanner()
                } else null,
            )
        }
    }

    override fun setBackgroundPlaying(active: Boolean) {
        _playbackState.update { it.copy(isBackgroundPlaying = active) }
    }

    override fun clearResumedNotification() {
        _playbackState.update { it.copy(resumedFromMs = null) }
    }

    private fun startTicker() {
        positionTracker.startTicker()
    }

    private fun stopTicker() {
        positionTracker.stopTicker()
    }

    // Issue #274: [forceSave] bypasses the manager's Room-write throttle
    // for the final position when playback stops (pause) — the ticker
    // stops with it, so without the flush the persisted position could
    // sit up to 5 s behind. The ticker itself passes the default.
    private fun updatePosition(forceSave: Boolean = false) {
        positionTracker.updatePosition(forceSave)
    }

    private fun updateTracksState() {
        positionTracker.updateTracksState()
    }

    override fun updatePositionState(
        currentPositionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
        hasPrevious: Boolean,
        hasNext: Boolean,
        playbackMetrics: EnginePlaybackMetrics?,
        mpvLoadStage: MpvLoadStage?,
    ) {
        _playbackState.update {
            it.copy(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                playbackMetrics = playbackMetrics,
                mpvLoadStage = mpvLoadStage,
            )
        }
    }

    override fun updateTracksState(
        audioTracks: List<AudioTrackInfo>,
        selectedAudioTrackIndex: Int,
        subtitleTracks: List<SubtitleTrackInfo>,
        selectedSubtitleTrackIndex: Int,
    ) {
        _playbackState.update {
            it.copy(
                audioTracks = audioTracks,
                selectedAudioTrackIndex = selectedAudioTrackIndex,
                subtitleTracks = subtitleTracks,
                selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
            )
        }
    }

    /**
     * Clears all persisted and in-memory playback resume positions (issue #353).
     */
    fun clearResumePositions() {
        resumeManager.clearAll()
    }

    /**
     * Fresh background play setting for background service gating (D-3, D-12).
     */
    fun isBackgroundPlayEnabled(): Boolean =
        settingsRepository.load().backgroundPlay

    /**
     * Fresh double-tap seek skip duration in milliseconds (issue #351, D-3).
     */
    fun getDoubleTapSeekDurationMs(): Long =
        settingsRepository.load().doubleTapSeekSeconds * 1000L

    /**
     * Fresh resume playback preference for persistence dialogs (issue #266, D-3).
     */
    fun isResumePlaybackEnabled(): Boolean =
        settingsRepository.load().resumePlayback

    /**
     * Handles task removal from the recents tray (issue #258, D-3).
     * If background play is disabled or nothing is playing, pauses playback
     * and clears background state, returning true if the background service should stop.
     */
    fun onTaskRemoved(): Boolean {
        val state = _playbackState.value
        val settings = settingsRepository.load()
        return if (!settings.backgroundPlay || !state.isPlaying) {
            pause()
            setVideoBackgroundActive(false)
            setBackgroundPlaying(false)
            true
        } else {
            false
        }
    }

    companion object {
        /**
         * Production wiring: builds the coordinator from injected Koin
         * singles ([database], [persistenceRepository], [settingsRepository])
         * and the given [Context] (D-10, R-5). Acts as the composition root
         * for the playback subsystem: creates the [PlayerEngine], [PlaybackQueueStore],
         * and the three extracted collaborators ([SubtitleManager], [PlaybackQueue],
         * [EngineEventTranslator]) before injecting them into the coordinator constructor.
         *
         * This factory is the process-wide entry point: Koin
         * registers the coordinator built here as a `single`, and
         * [PlaybackService] injects that same instance.
         */
        fun production(
            context: Context,
            database: MpxDatabase,
            persistenceRepository: MediaPersistenceRepository,
            settingsRepository: AppSettingsRepository,
        ): PlaybackCoordinator {
            val appContext = context.applicationContext
            val resumeManager = PlaybackResumeManager(persistenceRepository)
            val queueStore = PlaybackQueueStore(appContext)
            val initialDecoderMode = resolveInitialDecoderModeFrom(settingsRepository)
            val engine: PlayerEngine = MpvPlayerEngine(appContext, initialDecoderMode, settingsRepository)
            // D-2: Process-lifetime scope backed by SupervisorJob so individual child
            // job failures do not cancel sibling coordinator or collaborator tasks.
            // Intentionally uncancelled in production: the coordinator lives for the
            // process lifetime and outlives individual Activity/Service lifecycles.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val strings = ProductionStrings(appContext)
            val stateStore = PlaybackStateStore(
                initialDecoderMode = initialDecoderMode,
                strings = strings,
            )
            val subtitleManager = SubtitleManager(
                engine = engine,
                host = stateStore,
                scope = scope,
                fileAccess = SubtitleManager.productionFileAccess(appContext),
            )
            val playbackQueue = PlaybackQueue(
                engine = engine,
                queueStore = queueStore,
                resumeManager = resumeManager,
                settingsRepository = settingsRepository,
                host = stateStore,
                scope = scope,
            )
            val engineEventTranslator = EngineEventTranslator(
                engine = engine,
                queue = playbackQueue,
                subtitleManager = subtitleManager,
                resumeManager = resumeManager,
                settingsRepository = settingsRepository,
                host = stateStore,
                scope = scope,
            )
            return PlaybackCoordinator(
                database = database,
                persistenceRepository = persistenceRepository,
                resumeManager = resumeManager,
                settingsRepository = settingsRepository,
                engine = engine,
                scope = scope,
                strings = strings,
                backgroundService = ProductionBackgroundService(appContext),
                audioFocusManager = ProductionAudioFocusManager(appContext),
                initialDecoderMode = initialDecoderMode,
                subtitleManager = subtitleManager,
                playbackQueue = playbackQueue,
                engineEventTranslator = engineEventTranslator,
                stateStore = stateStore,
            )
        }

        private fun resolveInitialDecoderModeFrom(
            settingsRepository: AppSettingsRepository,
        ): DecoderMode {
            return try {
                val savedModeStr = settingsRepository.load().decoderMode
                DecoderMode.values().find { it.label == savedModeStr } ?: DecoderMode.HW
            } catch (_: Exception) {
                DecoderMode.HW
            }
        }
    }
}

/** Production [CoordinatorStrings]: the banner texts from resources. */
private class ProductionStrings(private val context: Context) : CoordinatorStrings {
    override fun codecError(codecName: String): String =
        context.getString(R.string.player_codec_error, codecName)

    override fun subtitleLoaded(displayName: String): String =
        context.getString(R.string.player_toast_subtitle_loaded, displayName)

    override fun subtitleFailed(displayName: String): String =
        context.getString(R.string.player_subtitle_load_failed, displayName)

    override fun backgroundActiveBanner(): String =
        context.getString(R.string.player_background_active_banner)
}

/** Production [BackgroundService]: the real foreground service. */
private class ProductionBackgroundService(private val context: Context) : BackgroundService {
    override fun start() {
        PlaybackService.start(context)
    }

    override fun stop() {
        PlaybackService.stop(context)
    }
}


