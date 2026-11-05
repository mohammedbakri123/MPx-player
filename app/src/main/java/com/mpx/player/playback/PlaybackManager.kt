package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import android.util.Log
import com.mpx.player.playback.engine.Media3PlayerEngine
import com.mpx.player.playback.engine.MpvPlaybackException
import com.mpx.player.playback.engine.MpvPlayerEngine
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.TrackType
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
 * Central controller for playback orchestration in MPx Player.
 * Manages the high-level playback state, playlist queue, real position ticker,
 * Room database-backed persistent resume tracking, playback history recording,
 * and delegates underlying playback engine operations to [PlayerEngine].
 */
class PlaybackManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    val database: MpxDatabase = MpxDatabase.getInstance(appContext)
    val persistenceRepository: MediaPersistenceRepository = RoomMediaPersistenceRepository(database)
    val resumeManager = PlaybackResumeManager(persistenceRepository)
    val settingsRepository = SharedPrefsAppSettingsRepository(appContext)

    /** Last playback queue, persisted so a process death in the background
     * does not leave the restored Player screen empty (issue #149).
     * Positions are NOT stored here: they already live in Room via
     * PlaybackResumeManager and playQueue() re-applies them. */
    private val queueStore = PlaybackQueueStore(appContext)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    /** Preparing watchdog: a video load that produces neither READY nor an
     * error within 20s is a stuck load, not a slow one (local files). Firing
     * routes a real timeout error through onError, which clears the splash
     * and (for mpv) falls back to Media3 — the spinner must never spin
     * forever again. Armed per video load; cancelled on READY/ENDED, on any
     * error, and on stop. The timeout message names the observed mpv stage,
     * so the banner itself becomes the next diagnostic. */
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
                st.playbackState != Player.STATE_READY && st.playbackState != Player.STATE_ENDED
            ) {
                val stage = engine.getLoadStage()
                val detail = if (stage != null) {
                    "engine=${if (isMpv) "MPV" else "MEDIA3"}, initialized=${stage.initialized}, " +
                        "surface=${stage.surfaceAttached}, loadIssued=${stage.loadIssued}, " +
                        "lastEvent=${stage.lastEvent}"
                } else {
                    "engine=${if (isMpv) "MPV" else "MEDIA3"}"
                }
                engineListener.onError(Exception("Playback load timed out after 20s ($detail)"))
            }
        }
    }
    private var currentQueue: List<PlayableMedia> = emptyList()
    private var pendingSubtitleTrackLabel: String? = null
    /** Media id the Media3->mpv auto-fallback already fired for; prevents mpv<->Media3 ping-pong on one item. */
    private var lastMpvFallbackItemId: String? = null
    /** Media id the no-supported-audio warning already fired for; avoids re-showing it after dismissal. */
    private var lastNoAudioReportedItemId: String? = null
    /** True when the user deliberately turned audio off for the current item; suppresses the no-sound warning. */
    private var userDisabledAudio: Boolean = false
    /** Pre-shuffle queue order, restored when shuffle is turned off (issue #76). */
    private var unshuffledQueue: List<PlayableMedia>? = null

    private val initialDecoderMode: DecoderMode = resolveInitialDecoderMode()

    private val _playbackState = MutableStateFlow(
        PlaybackState(decoderMode = initialDecoderMode)
    )
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val engineListener = object : PlayerEngine.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                val settings = settingsRepository.load()
                if (settings.backgroundPlay) {
                    PlaybackService.start(appContext)
                }
                startTicker()
            } else {
                stopTicker()
                updatePosition()
                _playbackState.value.currentItem?.let { item ->
                    val pos = engine.currentPosition
                    val dur = engine.duration
                    if (pos > 1_500L) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                persistenceRepository.recordHistory(
                                    PlaybackHistoryItem(
                                        mediaUri = item.uri,
                                        mediaId = item.id,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        isVideo = item.isVideo,
                                        lastPlayedAt = System.currentTimeMillis(),
                                        playbackPositionMs = pos,
                                        durationMs = if (dur > 0L) dur else item.durationMs,
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                cancelPreparingWatchdog()
            }
            _playbackState.update {
                it.copy(
                    playbackState = playbackState,
                    // Preparing ends the moment the engine is READY or ENDED;
                    // anything else (BUFFERING, IDLE mid-load) keeps the splash.
                    isPreparing = if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) false else it.isPreparing,
                )
            }
            updatePosition()
            updateTracksState()
            if (playbackState == Player.STATE_ENDED) {
                // Repeat handling (issue #76): both engines report STATE_ENDED
                // at the end of the playlist (Media3 only at the last item, mpv
                // on eof-reached). Repeat ONE restarts the current item; repeat
                // ALL wraps to the first item; OFF keeps the old behaviour.
                val state = _playbackState.value
                val current = state.currentItem
                when {
                    state.repeatMode == RepeatMode.ONE && current != null -> {
                        engine.seekTo(0L)
                        engine.play()
                    }
                    state.repeatMode == RepeatMode.ALL && current != null &&
                        currentQueue.isNotEmpty() -> {
                        engine.seekTo(0, 0L)
                        engine.play()
                    }
                    else -> {
                        current?.let { resumeManager.clearResumePosition(it.id) }
                    }
                }
            }
        }

        override fun onMediaItemTransition(index: Int, isAuto: Boolean) {
            val current = currentQueue.getOrNull(index)

            val savedPos = current?.let { resumeManager.getResumePosition(it.id) }
            val shouldResume = settingsRepository.load().resumePlayback &&
                isAuto && savedPos != null && savedPos > 2_000L

            if (shouldResume && savedPos != null) {
                engine.seekTo(savedPos)
            }

            current?.let { item ->
                scope.launch(Dispatchers.IO) {
                    try {
                        persistenceRepository.recordHistory(
                            PlaybackHistoryItem(
                                mediaUri = item.uri,
                                mediaId = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                isVideo = item.isVideo,
                                lastPlayedAt = System.currentTimeMillis(),
                                playbackPositionMs = savedPos ?: 0L,
                                durationMs = item.durationMs,
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            _playbackState.update {
                it.copy(
                    currentItem = current,
                    currentIndex = index,
                    hasPrevious = engine.hasPreviousMediaItem,
                    hasNext = engine.hasNextMediaItem,
                    resumedFromMs = if (shouldResume) savedPos else null,
                )
            }
            updateTracksState()
            updatePosition()
            // Issue #149: the current index moved; the queue itself did not.
            persistQueue(index)
        }

        override fun onTracksChanged() {
            updateTracksState()
            pendingSubtitleTrackLabel?.let { targetLabel ->
                val subtitleTracks = _playbackState.value.subtitleTracks
                val match = subtitleTracks.find { it.label == targetLabel }
                if (match != null) {
                    selectSubtitleTrack(match.groupIndex, match.trackIndex)
                    pendingSubtitleTrackLabel = null
                }
            }
        }

        override fun onPlaybackParametersChanged(speed: Float) {
            _playbackState.update { it.copy(speed = speed) }
        }

        override fun onVideoDecoderInitialized(decoderName: String) {
            val currentMode = _playbackState.value.decoderMode
            val isSoftware = MpxMediaCodecSelector.isSoftwareCodecName(decoderName)

            val fallbackNote = when {
                (currentMode == DecoderMode.HW || currentMode == DecoderMode.HW_PLUS) && isSoftware -> {
                    "Platform override: Hardware decoder unavailable for this format on this device. Fallback to software decoder ($decoderName)."
                }
                currentMode == DecoderMode.SW && !isSoftware -> {
                    "Platform override: Software decoder unavailable for this format. Fallback to hardware decoder ($decoderName)."
                }
                else -> null
            }

            _playbackState.update {
                it.copy(
                    activeDecoderName = decoderName,
                    decoderFallbackNote = fallbackNote,
                )
            }
        }

        override fun onError(error: Exception) {
            cancelPreparingWatchdog()
            Log.e("PlaybackManager", "Engine error encountered", error)
            // An error ends preparing: the banner below replaces the splash.
            _playbackState.update { it.copy(isPreparing = false) }
            if (isMpv) {
                val codec = if (error is MpvPlaybackException) error.codecName else _playbackState.value.activeDecoderName
                fallbackToMedia3(failedCodecName = codec ?: "unknown", reason = error.message)
            } else {
                // Media3 errors previously never arrived here (no onPlayerError
                // forwarding) — now they do. Decoder failures auto-fall back to
                // mpv once per item; anything else gets a codec-named message
                // plus a one-tap mpv offer in the banner.
                val codec = extractCodecName(error)
                val itemId = _playbackState.value.currentItem?.id
                if (codec != null && itemId != null && itemId != lastMpvFallbackItemId) {
                    lastMpvFallbackItemId = itemId
                    fallbackToMpv(failedCodecName = codec, reason = error.message)
                } else {
                    _playbackState.update {
                        it.copy(
                            userNotification = if (codec != null) {
                                appContext.getString(R.string.player_codec_error, codec)
                            } else {
                                "Playback error: ${error.message}"
                            },
                            showMpvSwitchOffer = true,
                        )
                    }
                }
            }
        }
    }

    private var activeSurface: Surface? = null

    /**
     * Active player engine instance. Defaults to [Media3PlayerEngine] and supports
     * switching to [MpvPlayerEngine] based on settings, keeping Media3 as fallback path.
     */
    var engine: PlayerEngine = createEngine(resolveInitialEngineType(), initialDecoderMode)
        private set

    val isMedia3: Boolean
        get() = engine is Media3PlayerEngine

    val isMpv: Boolean
        get() = engine is MpvPlayerEngine

    /**
     * Compatibility accessor exposing the underlying ExoPlayer instance for Media3-specific UI.
     */
    val player: ExoPlayer
        get() = (engine as? Media3PlayerEngine)?.player
            ?: throw IllegalStateException("Current engine is not Media3PlayerEngine")

    val exoPlayer: ExoPlayer?
        get() = (engine as? Media3PlayerEngine)?.player

    private fun resolveInitialEngineType(): String {
        return try {
            settingsRepository.load().playerEngine
        } catch (_: Exception) {
            "MEDIA3"
        }
    }

    private fun createEngine(engineType: String, mode: DecoderMode): PlayerEngine {
        return try {
            when (engineType.uppercase()) {
                "MPV" -> MpvPlayerEngine(appContext, mode).apply {
                    setListener(engineListener)
                }
                else -> Media3PlayerEngine(appContext, mode).apply {
                    setListener(engineListener)
                }
            }
        } catch (e: Throwable) {
            // Throwable, not just Exception: a missing native libmpv raises
            // UnsatisfiedLinkError, which previously would have crashed the
            // app on engine switch. Degrade to Media3 and SAY so.
            Log.e("PlaybackManager", "Error instantiating engine $engineType, falling back to Media3", e)
            _playbackState.update {
                it.copy(
                    userNotification = appContext.getString(
                        R.string.player_mpv_start_failed,
                        e.message ?: "unknown error",
                    ),
                    showMpvSwitchOffer = false,
                )
            }
            Media3PlayerEngine(appContext, mode).apply {
                setListener(engineListener)
            }
        }
    }

    private fun resolveInitialDecoderMode(): DecoderMode {
        return try {
            val savedModeStr = settingsRepository.load().decoderMode
            DecoderMode.values().find { it.label == savedModeStr } ?: DecoderMode.HW
        } catch (_: Exception) {
            DecoderMode.HW
        }
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
     * "android-surface-size"; Media3 ignores them via the interface default).
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
     * Applies subtitle style settings to the active engine. Only the mpv
     * engine renders subtitles itself (Media3 subtitles are styled by the
     * PlayerView in PlayerScreen), so this is a no-op for Media3.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        if (isMpv) {
            (engine as? MpvPlayerEngine)?.applySubtitleSettings(settings)
        }
    }

    /**
     * Switches playback engine with a seamless live handover preserving active queue,
     * timeline position, speed, and video surface.
     */
    fun setPlayerEngine(engineType: String) {
        val target = engineType.uppercase()
        val currentType = if (isMpv) "MPV" else "MEDIA3"
        if (currentType == target) return

        val wasPlaying = engine.isPlaying
        val currentPos = engine.currentPosition.coerceAtLeast(0L)
        val currentIndex = engine.currentMediaItemIndex
        val currentSpeed = _playbackState.value.speed
        val queue = currentQueue
        val currentMode = _playbackState.value.decoderMode
        val surface = activeSurface

        // 1. Release previous engine
        engine.setListener(null)
        engine.stop()
        engine.release()

        // 2. Persist new engine setting
        val settings = settingsRepository.load().copy(playerEngine = target)
        settingsRepository.save(settings)

        // 3. Create new engine and restore bindings
        val newEngine = createEngine(target, currentMode)
        engine = newEngine
        newEngine.setSpeed(currentSpeed)

        if (surface != null) {
            newEngine.attachSurface(surface)
        }

        // 4. Restore active queue and timeline position
        if (queue.isNotEmpty()) {
            val targetIndex = currentIndex.coerceIn(0, queue.lastIndex)
            newEngine.load(queue, targetIndex, currentPos)
            if (wasPlaying) {
                newEngine.play()
            }
        }

        _playbackState.update {
            it.copy(
                activeDecoderName = null,
                decoderFallbackNote = null,
                userNotification = "Switched engine to $target",
                // The new engine load is itself a fresh prepare.
                isPreparing = queue.getOrNull(currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0)))?.isVideo == true,
            )
        }
        updatePosition()
        updateTracksState()
        startTicker()
        armPreparingWatchdog()
    }

    /**
     * Fallback to Media3 on hard failure from MPV engine, surfacing a real error
     * naming the codec (never a silent stall) and preserving playback state.
     */
    fun fallbackToMedia3(failedCodecName: String = "unknown", reason: String? = null) {
        if (isMedia3) return

        val wasPlaying = engine.isPlaying
        val currentPos = engine.currentPosition.coerceAtLeast(0L)
        val currentIndex = engine.currentMediaItemIndex
        val currentSpeed = _playbackState.value.speed
        val queue = currentQueue
        val currentMode = _playbackState.value.decoderMode
        val surface = activeSurface

        Log.e("PlaybackManager", "Hard failure in MPV for codec '$failedCodecName': $reason. Falling back to Media3.")

        // Release failing MPV engine
        engine.setListener(null)
        engine.stop()
        engine.release()

        // Create Media3 engine fallback
        val fallbackEngine = Media3PlayerEngine(appContext, currentMode).apply {
            setListener(engineListener)
        }
        engine = fallbackEngine
        fallbackEngine.setSpeed(currentSpeed)

        if (surface != null) {
            fallbackEngine.attachSurface(surface)
        }

        // Restore playback state
        if (queue.isNotEmpty()) {
            val targetIndex = currentIndex.coerceIn(0, queue.lastIndex)
            fallbackEngine.load(queue, targetIndex, currentPos)
            if (wasPlaying) {
                fallbackEngine.play()
            }
        }

        _playbackState.update {
            it.copy(
                activeDecoderName = null,
                decoderFallbackNote = null,
                // When the codec is unknown (e.g. mpv could not even open the
                // file), naming a codec would be a lie — show the real reason.
                userNotification = if (failedCodecName == "unknown" && reason != null) {
                    appContext.getString(R.string.player_mpv_failed_media3, reason)
                } else {
                    "MPV decoder failed for codec '$failedCodecName'. Switched to Media3 engine."
                },
                // The fallback engine load is itself a fresh prepare.
                isPreparing = queue.getOrNull(currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0)))?.isVideo == true,
            )
        }
        updatePosition()
        updateTracksState()
        startTicker()
        armPreparingWatchdog()
    }

    /**
     * Mirror of [fallbackToMedia3]: Media3 decoder failure falls back to mpv,
     * preserving queue/position/speed/surface. Temporary only — the saved
     * engine setting is NOT changed, so the next session still starts on the
     * user's chosen engine. Guarded per item by [lastMpvFallbackItemId].
     */
    fun fallbackToMpv(failedCodecName: String = "unknown", reason: String? = null) {
        if (isMpv) return

        val wasPlaying = engine.isPlaying
        val currentPos = engine.currentPosition.coerceAtLeast(0L)
        val currentIndex = engine.currentMediaItemIndex
        val currentSpeed = _playbackState.value.speed
        val queue = currentQueue
        val currentMode = _playbackState.value.decoderMode
        val surface = activeSurface

        Log.e("PlaybackManager", "Hard failure in Media3 for codec '$failedCodecName': $reason. Falling back to mpv.")

        engine.setListener(null)
        engine.stop()
        engine.release()

        val fallbackEngine = MpvPlayerEngine(appContext, currentMode).apply {
            setListener(engineListener)
        }
        engine = fallbackEngine
        fallbackEngine.setSpeed(currentSpeed)

        if (surface != null) {
            fallbackEngine.attachSurface(surface)
        }

        if (queue.isNotEmpty()) {
            val targetIndex = currentIndex.coerceIn(0, queue.lastIndex)
            fallbackEngine.load(queue, targetIndex, currentPos)
            if (wasPlaying) {
                fallbackEngine.play()
            }
        }

        _playbackState.update {
            it.copy(
                activeDecoderName = null,
                decoderFallbackNote = null,
                userNotification = appContext.getString(R.string.player_media3_failed_mpv, failedCodecName),
                showMpvSwitchOffer = false,
                // The fallback engine load is itself a fresh prepare.
                isPreparing = queue.getOrNull(currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0)))?.isVideo == true,
            )
        }
        updatePosition()
        updateTracksState()
        startTicker()
        armPreparingWatchdog()
    }

    /** Walks the cause chain for an ExoPlayer DecoderInitializationException and returns its MIME type. */
    private fun extractCodecName(error: Throwable): String? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is MediaCodecRenderer.DecoderInitializationException) return cause.mimeType
            cause = cause.cause
        }
        return null
    }

    /**
     * Loads a single media item with optional resume position and begins playback.
     * An explicit [resumeMs] is a direct user request (e.g. tapping a Continue
     * Watching card), so it bypasses the automatic-resume toggle; plain queue
     * playback still honours the resumePlayback setting.
     */
    fun load(media: PlayableMedia, resumeMs: Long = 0L) {
        if (resumeMs > 0L) {
            resumeManager.saveResumePosition(media.id, resumeMs, media.uri, media.durationMs)
        }
        playQueue(listOf(media), startIndex = 0, autoPlay = true, forceResume = resumeMs > 0L)
    }

    fun playQueue(
        items: List<PlayableMedia>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        forceResume: Boolean = false,
    ) {
        if (items.isEmpty()) return
        // A new queue is a new session: shuffle order does not carry over.
        unshuffledQueue = null
        userDisabledAudio = false
        currentQueue = items

        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        val targetItem = items[targetIndex]
        // Issue #149: persist the new session immediately, before the engine
        // load, so even a crash mid-load leaves a restorable queue behind.
        persistQueue(targetIndex)
        val savedPos = resumeManager.getResumePosition(targetItem.id) ?: 0L
        val settings = settingsRepository.load()
        val shouldResume = (forceResume || settings.resumePlayback) &&
            savedPos > 2_000L

        engine.load(items, targetIndex, if (shouldResume) savedPos else 0L)
        // Persisted default speed (issue #76): every new session starts here.
        // The in-player speed toggle stays session-only (see setSpeed).
        engine.setSpeed(settings.defaultPlaybackSpeed)
        if (autoPlay) {
            engine.play()
        }

        targetItem.let { item ->
            scope.launch(Dispatchers.IO) {
                try {
                    persistenceRepository.recordHistory(
                        PlaybackHistoryItem(
                            mediaUri = item.uri,
                            mediaId = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            isVideo = item.isVideo,
                            lastPlayedAt = System.currentTimeMillis(),
                            playbackPositionMs = if (shouldResume) savedPos else 0L,
                            durationMs = item.durationMs,
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }

        _playbackState.update {
            it.copy(
                currentItem = targetItem,
                queue = items,
                currentIndex = targetIndex,
                hasPrevious = engine.hasPreviousMediaItem,
                hasNext = engine.hasNextMediaItem,
                resumedFromMs = if (shouldResume) savedPos else null,
                speed = settings.defaultPlaybackSpeed,
                shuffleEnabled = false,
                // A fresh video load is preparing until the engine reports READY.
                isPreparing = targetItem.isVideo,
            )
        }
        updatePosition()
        updateTracksState()
        startTicker()
        armPreparingWatchdog()
    }

    fun playAudioQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        val playableTracks = tracks.filter { it.path.isNotBlank() }
        if (playableTracks.isEmpty()) return
        val items = playableTracks.map { track ->
            PlayableMedia(
                id = track.id,
                title = track.title,
                subtitle = "${track.artist} • ${track.album}",
                uri = track.path,
                isVideo = false,
                durationMs = track.durationMs,
            )
        }
        playQueue(items, startIndex.coerceIn(0, items.lastIndex), autoPlay = true)
    }

    fun playVideoQueue(folderId: String = "camera") {
        // Fallback to sample video queue removed (issue #57). Real videos are played via playVideoFiles.
    }

    /** Play real video files from a MediaStore folder. Empty list = no-op. */
    fun playVideoFiles(videos: List<com.mpx.player.domain.model.VideoFile>, startIndex: Int = 0) {
        if (videos.isEmpty()) return
        val items = videos.map { video ->
            PlayableMedia(
                id = video.id,
                title = video.title,
                subtitle = "",
                uri = video.path,
                isVideo = true,
                durationMs = video.durationMs,
            )
        }
        playQueue(items, startIndex, autoPlay = true)
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun togglePlayPause() {
        if (engine.isPlaying) {
            engine.pause()
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

    fun skipNext() {
        engine.seekToNextMediaItem()
    }

    fun skipPrevious() {
        engine.seekToPreviousMediaItem()
    }

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    /** Cycles OFF -> ALL -> ONE -> OFF. The mode only changes end-of-queue
     * behaviour (see the STATE_ENDED branch); it never invents tracks. */
    fun cycleRepeatMode() {
        val next = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.update { it.copy(repeatMode = next) }
    }

    /**
     * Toggles shuffle (issue #76). Enabling reorders the live queue with the
     * currently playing item first and the rest shuffled, reloading the engine
     * at the same position so playback continues uninterrupted; the current
     * speed is re-applied after reload. Disabling restores the pre-shuffle
     * order. A no-op with no current item (the flag still flips so the button
     * reflects the request, and the next queue starts unshuffled).
     */
    fun setShuffleEnabled(enabled: Boolean) {
        val state = _playbackState.value
        if (enabled == state.shuffleEnabled && currentQueue.isNotEmpty()) return
        val current = state.currentItem
        if (current == null || currentQueue.isEmpty()) {
            _playbackState.update { it.copy(shuffleEnabled = enabled) }
            return
        }
        val position = engine.currentPosition
        val wasPlaying = engine.isPlaying
        val speed = state.speed
        if (enabled) {
            unshuffledQueue = currentQueue
            val currentIndex = state.currentIndex.coerceIn(0, currentQueue.lastIndex)
            val rest = currentQueue.filterIndexed { index, _ -> index != currentIndex }.shuffled()
            val newQueue = listOf(currentQueue[currentIndex]) + rest
            currentQueue = newQueue
            engine.load(newQueue, 0, position)
            engine.setSpeed(speed)
            if (wasPlaying) engine.play()
            _playbackState.update {
                it.copy(
                    queue = newQueue,
                    currentIndex = 0,
                    hasPrevious = false,
                    hasNext = newQueue.size > 1,
                    shuffleEnabled = true,
                )
            }
            // Issue #149: the shuffled order is the live order now.
            persistQueue(0)
        } else {
            val original = unshuffledQueue
            unshuffledQueue = null
            if (original != null && original.isNotEmpty()) {
                val index = original.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                currentQueue = original
                engine.load(original, index, position)
                engine.setSpeed(speed)
                if (wasPlaying) engine.play()
                _playbackState.update {
                    it.copy(
                        queue = original,
                        currentIndex = index,
                        hasPrevious = index > 0,
                        hasNext = index < original.lastIndex,
                        shuffleEnabled = false,
                    )
                }
                // Issue #149: the restored pre-shuffle order is live again.
                persistQueue(index)
            } else {
                _playbackState.update { it.copy(shuffleEnabled = false) }
            }
        }
        updatePosition()
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
        if (isMpv) {
            (engine as? MpvPlayerEngine)?.setAspectRatio(mode)
        }
    }

    /**
     * Changes the decoder mode (HW, HW+, SW).
     * Rebuilds the underlying ExoPlayer instance in [Media3PlayerEngine] with the requested
     * [MpxMediaCodecSelector] configuration, restoring playback position, speed,
     * queue, and track selections seamlessly.
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
        if (type == TrackType.AUDIO) {
            userDisabledAudio = (id == null || id == "-1" || id.isBlank())
        }
        engine.selectTrack(type, id)
        updateTracksState()
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        engine.selectAudioTrack(groupIndex, trackIndex)
        updateTracksState()
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        engine.selectSubtitleTrack(groupIndex, trackIndex)
        updateTracksState()
    }

    /**
     * Loads an external subtitle file (SRT / VTT) into the currently playing media item.
     * Replaces the media item in the engine so the new sidecar subtitle track is loaded,
     * maintaining the current playback position and state, and marks the track to be auto-selected.
     */
    fun loadExternalSubtitle(uri: Uri, displayName: String, mimeType: String): Boolean {
        val currentItem = _playbackState.value.currentItem ?: return false
        val index = engine.currentMediaItemIndex
        if (index < 0 || index >= currentQueue.size) return false

        val ext = ExternalSubtitle(
            uri = uri.toString(),
            name = displayName,
            mimeType = mimeType,
        )

        // Avoid duplicate entry of the same URI
        val existingSubs = currentItem.externalSubtitles.filter { it.uri != ext.uri }
        val updatedSubs = existingSubs + ext
        val updatedItem = currentItem.copy(externalSubtitles = updatedSubs)

        val updatedQueue = currentQueue.toMutableList()
        updatedQueue[index] = updatedItem
        currentQueue = updatedQueue

        val currentPos = engine.currentPosition
        val wasPlaying = engine.isPlaying

        // Replace current media item in engine timeline
        engine.replaceMediaItem(index, updatedItem)

        if (currentPos > 0L) {
            engine.seekTo(index, currentPos)
        }
        if (wasPlaying && !engine.isPlaying) {
            engine.play()
        }

        // Set pending label to auto-select this track once onTracksChanged fires
        pendingSubtitleTrackLabel = displayName

        _playbackState.update {
            it.copy(
                currentItem = updatedItem,
                queue = updatedQueue,
                userNotification = "External subtitle loaded: $displayName",
            )
        }
        // Issue #149: the queue item itself changed (new subtitle list).
        persistQueue(index)
        return true
    }

    fun clearDecoderFallbackNote() {
        _playbackState.update { it.copy(decoderFallbackNote = null) }
    }

    fun clearUserNotification() {
        _playbackState.update { it.copy(userNotification = null, showMpvSwitchOffer = false) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val durationMs = minutes * 60 * 1000L
        val targetElapsedRealtime = SystemClock.elapsedRealtime() + durationMs
        _playbackState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = durationMs / 1000L,
            )
        }
        sleepTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val remainingMs = targetElapsedRealtime - SystemClock.elapsedRealtime()
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
     * Writes the live queue + current index to the queue store (issue #149).
     * Called on every queue mutation and on track transitions, never from
     * the position ticker, so a write costs one small JSON encode per track
     * change, not per tick. A write failure is swallowed inside the store:
     * persistence must never break playback.
     */
    private fun persistQueue(currentIndex: Int = _playbackState.value.currentIndex) {
        queueStore.save(currentQueue, currentIndex)
    }

    /**
     * Restores the last persisted queue when the live queue is empty
     * (issue #149): the process died in the background and Android restored
     * the Player destination with a fresh, empty PlaybackManager. Loads the
     * queue paused at the Room-saved position (playQueue's normal resume
     * rules apply, so with resume playback off it starts at 0 instead).
     * Returns false when there is nothing to restore, so the caller keeps
     * the empty state. Deliberate stops clear the persisted queue in
     * stop(), so this never resurrects a session the user ended. Shuffle
     * order is not persisted: a restored queue always starts unshuffled.
     */
    fun restorePersistedQueueIfEmpty(): Boolean {
        if (_playbackState.value.queue.isNotEmpty()) return false
        val persisted = queueStore.load() ?: return false
        playQueue(persisted.items, persisted.currentIndex, autoPlay = false)
        return true
    }

    fun stop() {
        cancelSleepTimer()
        cancelPreparingWatchdog()
        engine.stop()
        stopTicker()
        PlaybackService.stop(appContext)
        unshuffledQueue = null
        queueStore.clear()
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
        PlaybackService.stop(appContext)
    }

    fun setVideoBackgroundActive(active: Boolean) {
        _playbackState.update {
            it.copy(
                isVideoBackgroundActive = active,
                isBackgroundPlaying = active,
                userNotification = if (active) {
                    appContext.getString(R.string.player_background_active_banner)
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

    private fun updatePosition() {
        val current = engine.currentPosition
        val duration = engine.duration
        val buffered = engine.bufferedPosition

        // Save position into persistent resume store
        _playbackState.value.currentItem?.let { item ->
            if (current > 1_500L && (duration <= 0L || current < duration - 2_000L)) {
                resumeManager.saveResumePosition(
                    id = item.id,
                    positionMs = current,
                    mediaUri = item.uri,
                    durationMs = duration,
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
                // diagnostics rows track the live engine (MPV values, null
                // for Media3's unmeasured fields is handled by the rows).
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

        // Silent no-sound detection (issue #82/#83): ExoPlayer excludes
        // undecodable audio tracks from selection and plays video anyway, so
        // a file can play with no sound and no error. When the current video
        // has audio tracks but none is selected AND none is supported, say so
        // naming the codecs, and offer the mpv switch — once per item, and
        // only on Media3 (mpv reports its tracks as supported).
        val currentItem = _playbackState.value.currentItem
        if (isMedia3 && !userDisabledAudio && currentItem?.isVideo == true && audioList.isNotEmpty() &&
            selectedAudioIdx < 0 && audioList.none { it.isSupported } &&
            currentItem.id != lastNoAudioReportedItemId
        ) {
            lastNoAudioReportedItemId = currentItem.id
            val codecs = audioList.mapNotNull { it.mimeType }.distinct().joinToString(", ").ifBlank { "unknown" }
            _playbackState.update {
                it.copy(
                    userNotification = appContext.getString(R.string.player_no_audio_codec, codecs),
                    showMpvSwitchOffer = true,
                )
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
