package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
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
 * ViewModels and screens talk to (migration Phase 3, step 4).
 *
 * This is [PlaybackManager] with its shell removed: it owns the
 * published [PlaybackState], the position ticker, the preparing
 * watchdog, the sleep timer, transport and session functions, and it
 * builds the engine, the database/repositories, the resume manager,
 * and the three extracted pieces ([SubtitleManager], [PlaybackQueue],
 * [EngineEventTranslator]) — and it is their host
 * ([SubtitleHost], [QueueHost], [EngineEventHost]), so every state
 * write still goes through exactly one class. [PlaybackManager]
 * keeps its exact public API and delegates everything here; no
 * caller changed in this step, and the final Phase 3 step rewires
 * the callers to this class and deletes [PlaybackManager].
 *
 * No behaviour change from the [PlaybackManager] code this was moved
 * from — the bodies are verbatim except for the seams below.
 *
 * Seams (all constructor parameters, so the class is unit-testable
 * with fakes and no Android runtime):
 * - The collaborators arrive already built: [engine],
 *   [database], [persistenceRepository], [resumeManager],
 *   [settingsRepository], the queue store, and the subtitle
 *   [SubtitleManager.FileAccess]. Production wiring lives in the
 *   [production] factory, which builds them from a [Context] in
 *   exactly the order [PlaybackManager] built them. Taking the
 *   already-built pieces (instead of a bare [Context]) keeps the
 *   seam clean: the coordinator never calls `getInstance` or a
 *   constructor it cannot test, and the final step can hand the
 *   pieces in from Koin instead.
 * - [strings] and [backgroundService] cover the two Android-only
 *   effects the coordinator itself performs (banner texts and the
 *   foreground service); the pieces already had their own seams.
 * - [clock] is the elapsed-realtime source for the sleep timer
 *   (production: [SystemClock.elapsedRealtime]); tests tie it to
 *   the test scheduler so the timer can be driven by virtual time.
 * - [ioDispatcher] is where history rows are recorded
 *   (production: [Dispatchers.IO]); tests pass an unconfined test
 *   dispatcher so the fire-and-forget writes are observable.
 * - [scope] is the Main-scope the ticker, watchdog, sleep timer,
 *   and the pieces' deferred work run in (production:
 *   `CoroutineScope(Dispatchers.Main)`).
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
 */
class PlaybackCoordinator(
    val database: MpxDatabase,
    val persistenceRepository: MediaPersistenceRepository,
    val resumeManager: PlaybackResumeManager,
    val settingsRepository: AppSettingsRepository,
    val engine: PlayerEngine,
    private val queueStore: QueueStore,
    private val subtitleFileAccess: SubtitleManager.FileAccess,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val strings: CoordinatorStrings,
    private val backgroundService: BackgroundService,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : PlaybackStateSource, StreamPlayer, SubtitleHost, QueueHost, EngineEventHost {

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null

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
    private val initialDecoderMode: DecoderMode = resolveInitialDecoderMode()

    private val _playbackState = MutableStateFlow(
        PlaybackState(decoderMode = initialDecoderMode)
    )
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeSurface: Surface? = null

    /**
     * Migration Phase 3, step 1: the external-subtitle responsibility,
     * extracted from [PlaybackManager]. Owns subtitle discovery, the
     * manual-pick load path, the pending-confirmation state, and
     * subtitle style settings. This coordinator stays the
     * [SubtitleHost]: it owns the live queue and the published state,
     * so the subtitle manager asks it to apply queue/state effects
     * instead of touching them directly.
     */
    val subtitleManager = SubtitleManager(
        engine = engine,
        host = this,
        scope = scope,
        fileAccess = subtitleFileAccess,
    )

    /**
     * Migration Phase 3, step 2: the queue responsibility, extracted
     * from [PlaybackManager]. Owns the live queue, queue building,
     * shuffle and repeat, and queue persistence. This coordinator
     * stays the [QueueHost]: it owns the published state and the
     * engine lifecycle effects, so the queue asks it to apply
     * session/state effects instead of touching them directly.
     */
    val playbackQueue = PlaybackQueue(
        engine = engine,
        queueStore = queueStore,
        resumeManager = resumeManager,
        settingsRepository = settingsRepository,
        host = this,
        scope = scope,
    )

    /**
     * Migration Phase 3, step 3: the engine-event translation,
     * extracted from [PlaybackManager]. Owns the [PlayerEngine.Listener]
     * implementation that turns mpv callbacks into published-state
     * updates and queue / resume / subtitle effects. This coordinator
     * stays the [EngineEventHost]: it owns the published state, the
     * position ticker, and the preparing watchdog, so the translator
     * asks it to apply state/lifecycle effects instead of touching
     * them directly. Attached to the engine here: the engine, the
     * queue, and the subtitle manager must all exist first, and no
     * load can fire an event before this construction finishes.
     */
    val engineEventTranslator = EngineEventTranslator(
        engine = engine,
        queue = playbackQueue,
        subtitleManager = subtitleManager,
        resumeManager = resumeManager,
        settingsRepository = settingsRepository,
        host = this,
        scope = scope,
    ).also { engine.setListener(it) }

    // SubtitleHost implementation: the queue/state effects
    // SubtitleManager asks for. The commit body is the queue/state half
    // of the old commitSubtitleAdd — the restore half moved into
    // SubtitleManager with the decision logic.
    override val currentItem: PlayableMedia?
        get() = _playbackState.value.currentItem
    override val liveQueue: List<PlayableMedia>
        get() = playbackQueue.items
    override val subtitleTracks: List<SubtitleTrackInfo>
        get() = _playbackState.value.subtitleTracks

    override fun commitSubtitleItem(index: Int, updatedItem: PlayableMedia, notification: String?) {
        playbackQueue.replaceItem(index, updatedItem)
        _playbackState.update { state ->
            val withItem = state.copy(currentItem = updatedItem, queue = playbackQueue.items)
            if (notification != null) withItem.copy(userNotification = notification) else withItem
        }
        // Issue #149: the queue item itself changed (new subtitle list).
        playbackQueue.persistQueue(index)
    }

    override fun subtitleLoadedMessage(displayName: String): String =
        strings.subtitleLoaded(displayName)

    override fun subtitleFailedMessage(displayName: String): String =
        strings.subtitleFailed(displayName)

    override fun showNotification(message: String) {
        _playbackState.update { it.copy(userNotification = message) }
    }

    override fun refreshTracksState() {
        updateTracksState()
    }

    // QueueHost implementation: the session/state effects
    // PlaybackQueue asks for. The queue decides what plays and in
    // what order; these methods are the only places its decisions
    // become published state, history, ticker, or watchdog effects.
    override val state: PlaybackState
        get() = _playbackState.value

    override fun publishSession(
        items: List<PlayableMedia>,
        targetIndex: Int,
        targetItem: PlayableMedia,
        resumedFromMs: Long?,
        speed: Float,
        shuffleEnabled: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        _playbackState.update {
            it.copy(
                currentItem = targetItem,
                queue = items,
                currentIndex = targetIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                speed = speed,
                shuffleEnabled = shuffleEnabled,
                // A fresh video load is preparing until the engine reports READY.
                isPreparing = targetItem.isVideo,
                // Issue #260: same reset as onMediaItemTransition — the
                // transition callback may land after this update, so the
                // effect must not read the previous video's size here.
                videoWidth = 0,
                videoHeight = 0,
            )
        }
    }

    override fun publishQueueOrder(
        items: List<PlayableMedia>,
        currentIndex: Int,
        shuffleEnabled: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        _playbackState.update {
            it.copy(
                queue = items,
                currentIndex = currentIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                shuffleEnabled = shuffleEnabled,
            )
        }
    }

    override fun publishShuffleEnabled(enabled: Boolean) {
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
    }

    override fun publishRepeatMode(mode: RepeatMode) {
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    override fun recordHistory(item: PlayableMedia, positionMs: Long) {
        scope.launch(ioDispatcher) {
            try {
                persistenceRepository.recordHistory(
                    PlaybackHistoryItem(
                        mediaUri = item.uri,
                        mediaId = item.id,
                        title = item.title,
                        subtitle = item.subtitle,
                        isVideo = item.isVideo,
                        lastPlayedAt = System.currentTimeMillis(),
                        playbackPositionMs = positionMs,
                        durationMs = item.durationMs,
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun clearAutoDetectAttempts() {
        subtitleManager.clearAutoDetectAttempts()
    }

    override fun refreshPositionAndTracks() {
        updatePosition()
        updateTracksState()
    }

    override fun refreshPosition() {
        updatePosition()
    }

    override fun startPositionTicker() {
        startTicker()
    }

    override fun armLoadWatchdog() {
        armPreparingWatchdog()
    }

    // EngineEventHost implementation: the state/lifecycle effects
    // EngineEventTranslator asks for. The translator computes the
    // values (the #187/#223 banner clear, the #330 resume-banner
    // rule, the #188 decoder fallback note); these methods only
    // apply the state copy or the effect, verbatim from the old
    // engineListener bodies.
    //
    // Note: recordHistory (in the QueueHost section above) is the
    // single implementation for both QueueHost and EngineEventHost —
    // the two interfaces declare the same signature, so one override
    // serves both. The same holds for refreshPositionAndTracks,
    // startPositionTicker, and state.
    override fun publishIsPlaying(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
    }

    override fun startBackgroundService() {
        backgroundService.start()
    }

    override fun stopPositionTicker() {
        stopTicker()
    }

    override fun flushPosition() {
        updatePosition(forceSave = true)
    }

    override fun recordPauseHistory(item: PlayableMedia, positionMs: Long, durationMs: Long) {
        scope.launch(ioDispatcher) {
            try {
                persistenceRepository.recordHistory(
                    PlaybackHistoryItem(
                        mediaUri = item.uri,
                        mediaId = item.id,
                        title = item.title,
                        subtitle = item.subtitle,
                        isVideo = item.isVideo,
                        lastPlayedAt = System.currentTimeMillis(),
                        playbackPositionMs = positionMs,
                        durationMs = durationMs,
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun cancelLoadWatchdog() {
        cancelPreparingWatchdog()
    }

    override fun publishEngineState(playbackState: Int, isPreparing: Boolean, userNotification: String?) {
        _playbackState.update {
            it.copy(
                playbackState = playbackState,
                isPreparing = isPreparing,
                userNotification = userNotification,
            )
        }
    }

    override fun maybeAutoDetectSubtitles() {
        subtitleManager.maybeAutoDetectSubtitles()
    }

    override fun publishTransition(
        item: PlayableMedia?,
        index: Int,
        hasPrevious: Boolean,
        hasNext: Boolean,
        resumedFromMs: Long?,
    ) {
        _playbackState.update {
            it.copy(
                currentItem = item,
                currentIndex = index,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                // Issue #260: the new item's dimensions are not known
                // yet — drop the previous video's size so the PiP
                // params are not armed with a stale aspect while it
                // loads, and so the 0 -> WxH transition always
                // re-runs the effect even when the new video has the
                // same dimensions as the old one.
                videoWidth = 0,
                videoHeight = 0,
            )
        }
    }

    override fun publishSpeed(speed: Float) {
        _playbackState.update { it.copy(speed = speed) }
    }

    override fun publishVideoSize(width: Int, height: Int) {
        _playbackState.update { it.copy(videoWidth = width, videoHeight = height) }
    }

    override fun publishDecoderInitialized(decoderName: String, fallbackNote: String?) {
        _playbackState.update {
            it.copy(
                activeDecoderName = decoderName,
                decoderFallbackNote = fallbackNote,
            )
        }
    }

    override fun publishEngineError(error: Exception) {
        Log.e("PlaybackManager", "Engine error encountered", error)
        // An error ends preparing: the banner below replaces the splash.
        // mpv-only (issue #150): there is no fallback engine any more,
        // so every error is reported as-is — with the codec name when
        // libmpv knows it — instead of silently switching engines.
        _playbackState.update {
            it.copy(
                isPreparing = false,
                userNotification = if (error is MpvPlaybackException && error.codecName != null) {
                    strings.codecError(error.codecName)
                } else {
                    "Playback error: ${error.message}"
                },
            )
        }
    }

    /**
     * Issue #225: display size of the current video (engine's
     * `dwidth`/`dheight`), the PiP aspect-ratio source of truth. 0 when
     * no video is loaded.
     */
    val videoWidth: Int
        get() = engine.videoWidth

    val videoHeight: Int
        get() = engine.videoHeight

    private fun resolveInitialDecoderMode(): DecoderMode {
        return resolveInitialDecoderModeFrom(settingsRepository)
    }

    /**
     * Attaches a native video surface to the active playback engine.
     */
    fun attachSurface(surface: Surface) {
        activeSurface = surface
        engine.attachSurface(surface)
    }

    /**
     * Forwards SurfaceView size changes to the engine (mpv reports them as
     * "android-surface-size").
     */
    fun surfaceChanged(width: Int, height: Int) {
        engine.onSurfaceChanged(width, height)
    }

    /**
     * Detaches the video surface from the active playback engine.
     */
    fun detachSurface() {
        activeSurface = null
        engine.detachSurface()
    }

    /**
     * Applies subtitle style settings to the mpv engine, which renders
     * subtitles itself. Delegates to [subtitleManager]; behaviour is
     * unchanged.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        subtitleManager.applySubtitleSettings(settings)
    }

    /**
     * Applies audio delay + video zoom to the active engine (issue #153).
     * mpv-only, mirroring [applySubtitleSettings].
     */
    fun applyPlaybackTweaks(settings: AppSettings) {
        (engine as? MpvPlayerEngine)?.applyPlaybackTweaks(settings)
    }

    /**
     * Live video-zoom for the pinch gesture (#172): applies immediately
     * without persisting; the caller saves the final zoom to settings
     * when the gesture ends.
     */
    fun setVideoZoom(zoom: Float) {
        (engine as? MpvPlayerEngine)?.setVideoZoom(zoom)
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
        playbackQueue.playQueue(items, startIndex, autoPlay, forceResume, preserveShuffleState)
    }

    /**
     * Plays the item at [index] in the LIVE queue (issue #203).
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playLiveQueueItem(index: Int) {
        playbackQueue.playLiveQueueItem(index)
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playAudioQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playbackQueue.playAudioQueue(tracks, startIndex)
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged (still a
     * no-op stub — issue #57; real videos play via [playVideoFiles]).
     */
    fun playVideoQueue(folderId: String = "camera") {
        playbackQueue.playVideoQueue(folderId)
    }

    /**
     * Play real video files from a MediaStore folder. Empty list = no-op.
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun playVideoFiles(videos: List<VideoFile>, startIndex: Int = 0) {
        playbackQueue.playVideoFiles(videos, startIndex)
    }

    fun play() {
        // Issue #211: at EOF mpv stays eof-reached, so un-pausing alone
        // cannot restart — only a seek to 0 can. This is the MediaSession
        // onPlay() / notification Play path; togglePlayPause() below has
        // the same guard for the on-screen Play buttons.
        if (_playbackState.value.playbackState == PlayerEngine.STATE_ENDED) {
            engine.seekTo(0L)
        }
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun togglePlayPause() {
        if (engine.isPlaying) {
            engine.pause()
        } else if (_playbackState.value.playbackState == PlayerEngine.STATE_ENDED) {
            // Issue #211: Play after EOF — see play() above.
            engine.seekTo(0L)
            engine.play()
        } else {
            engine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        updatePosition()
    }

    fun skipForward(deltaMs: Long = 10_000L) {
        seekTo(engine.currentPosition + deltaMs)
    }

    fun skipBackward(deltaMs: Long = 10_000L) {
        seekTo(engine.currentPosition - deltaMs)
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun skipNext() {
        playbackQueue.skipNext()
    }

    /**
     * Delegates to [playbackQueue]; behaviour is unchanged.
     */
    fun skipPrevious() {
        playbackQueue.skipPrevious()
    }

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    /**
     * Cycles OFF -> ALL -> ONE -> OFF. Delegates to [playbackQueue];
     * behaviour is unchanged.
     */
    fun cycleRepeatMode() {
        playbackQueue.cycleRepeatMode()
    }

    /**
     * Toggles shuffle (issue #76). Delegates to [playbackQueue];
     * behaviour is unchanged.
     */
    fun setShuffleEnabled(enabled: Boolean) {
        playbackQueue.setShuffleEnabled(enabled)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
        (engine as? MpvPlayerEngine)?.setAspectRatio(mode)
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
    fun setDecoderMode(mode: DecoderMode) {
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

    fun setLocked(locked: Boolean) {
        _playbackState.update { it.copy(isLocked = locked) }
    }

    fun selectTrack(type: TrackType, id: String?) {
        engine.selectTrack(type, id)
        updateTracksState()
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        engine.selectAudioTrack(groupIndex, trackIndex)
        updateTracksState()
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        subtitleManager.selectSubtitleTrack(groupIndex, trackIndex)
    }

    /**
     * Loads an external subtitle file (SRT / VTT) into the currently playing media item.
     * Delegates to [subtitleManager], which owns the load path
     * (issue #332: the returned boolean is whether the subtitle was
     * ACCEPTED for loading, and the success/failure banner arrives
     * asynchronously). Behaviour is unchanged.
     */
    fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean {
        return subtitleManager.loadExternalSubtitle(uri.toString(), displayName, mimeType)
    }

    fun clearDecoderFallbackNote() {
        _playbackState.update { it.copy(decoderFallbackNote = null) }
    }

    fun clearUserNotification() {
        _playbackState.update { it.copy(userNotification = null) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val durationMs = minutes * 60 * 1000L
        val targetElapsedRealtime = clock() + durationMs
        _playbackState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = durationMs / 1000L,
            )
        }
        sleepTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val remainingMs = targetElapsedRealtime - clock()
                if (remainingMs <= 0L) {
                    _playbackState.update {
                        it.copy(
                            sleepTimerMinutes = null,
                            sleepTimerRemainingSeconds = null,
                        )
                    }
                    pause()
                    break
                } else {
                    val remainingSec = (remainingMs + 999L) / 1000L
                    _playbackState.update {
                        it.copy(sleepTimerRemainingSeconds = remainingSec)
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
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

    fun stop() {
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

    fun release() {
        cancelSleepTimer()
        cancelPreparingWatchdog()
        engine.release()
        stopTicker()
        backgroundService.stop()
    }

    fun setVideoBackgroundActive(active: Boolean) {
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

    fun setBackgroundPlaying(active: Boolean) {
        _playbackState.update { it.copy(isBackgroundPlaying = active) }
    }

    fun clearResumedNotification() {
        _playbackState.update { it.copy(resumedFromMs = null) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updatePosition()
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // Issue #274: [forceSave] bypasses the manager's Room-write throttle
    // for the final position when playback stops (pause) — the ticker
    // stops with it, so without the flush the persisted position could
    // sit up to 5 s behind. The ticker itself passes the default.
    private fun updatePosition(forceSave: Boolean = false) {
        val current = engine.currentPosition
        val duration = engine.duration
        val buffered = engine.bufferedPosition

        // Save position into persistent resume store. Issue #227: the
        // manager keys this row by the media URI, not the bare id — both
        // are passed so the key can be derived.
        _playbackState.value.currentItem?.let { item ->
            if (current > 1_500L && (duration <= 0L || current < duration - 2_000L)) {
                resumeManager.saveResumePosition(
                    id = item.id,
                    positionMs = current,
                    mediaUri = item.uri,
                    durationMs = duration,
                    force = forceSave,
                )
            }
        }

        _playbackState.update {
            it.copy(
                currentPositionMs = current,
                durationMs = duration,
                bufferedPositionMs = buffered,
                hasPrevious = engine.hasPreviousMediaItem,
                hasNext = engine.hasNextMediaItem,
                // Refresh measured engine metrics on every tick so the
                // diagnostics rows track the live engine (null before any
                // video has played is rendered as "not observed").
                playbackMetrics = engine.getPlaybackMetrics(),
                mpvLoadStage = engine.getLoadStage(),
            )
        }
    }

    private fun updateTracksState() {
        val audioList = engine.getAudioTracks().toMutableList()
        val rawSubtitles = engine.getSubtitleTracks()
        val externalNames = _playbackState.value.currentItem?.externalSubtitles?.map { it.name } ?: emptyList()

        val subtitleList = rawSubtitles.map { track ->
            val isExt = externalNames.any { it.equals(track.label, ignoreCase = true) }
            if (isExt) track.copy(isExternal = true) else track
        }.toMutableList()

        val selectedAudioIdx = audioList.indexOfFirst { it.isSelected }
        val selectedSubtitleIdx = subtitleList.indexOfFirst { it.isSelected }

        _playbackState.update {
            it.copy(
                audioTracks = audioList,
                selectedAudioTrackIndex = selectedAudioIdx,
                subtitleTracks = subtitleList,
                selectedSubtitleTrackIndex = selectedSubtitleIdx,
            )
        }
    }

    companion object {
        /**
         * Production wiring: builds the coordinator and everything it
         * owns from a [Context], in exactly the order [PlaybackManager]
         * built them (database, persistence, resume, settings, queue
         * store, decoder mode, engine, scope). The engine is built
         * with the decoder mode read from settings, and the state is
         * seeded from the same read inside the constructor — two reads
         * of the same SharedPreferences file during synchronous
         * construction, so they always agree.
         *
         * `PlaybackManager.getInstance` is still the process-wide
         * entry point (Koin and `PlaybackService` use it); it calls
         * this factory. The final Phase 3 step registers the
         * coordinator itself and deletes [PlaybackManager].
         */
        fun production(context: Context): PlaybackCoordinator {
            val appContext = context.applicationContext
            val database = MpxDatabase.getInstance(appContext)
            val persistenceRepository: MediaPersistenceRepository =
                RoomMediaPersistenceRepository(database, appContext)
            val resumeManager = PlaybackResumeManager(persistenceRepository)
            val settingsRepository = SharedPrefsAppSettingsRepository(appContext)
            val queueStore = PlaybackQueueStore(appContext)
            val initialDecoderMode = resolveInitialDecoderModeFrom(settingsRepository)
            val engine: PlayerEngine = MpvPlayerEngine(appContext, initialDecoderMode)
            val scope = CoroutineScope(Dispatchers.Main)
            return PlaybackCoordinator(
                database = database,
                persistenceRepository = persistenceRepository,
                resumeManager = resumeManager,
                settingsRepository = settingsRepository,
                engine = engine,
                queueStore = queueStore,
                subtitleFileAccess = SubtitleManager.productionFileAccess(appContext),
                scope = scope,
                strings = ProductionStrings(appContext),
                backgroundService = ProductionBackgroundService(appContext),
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
