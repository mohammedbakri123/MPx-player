package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import android.util.Log
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
                engineListener.onError(Exception("Playback load timed out after 20s ($detail)"))
            }
        }
    }
    private var currentQueue: List<PlayableMedia> = emptyList()
    // Issue #192: a manual external-subtitle pick the engine could not
    // pin yet (track not visible in track-list when sub-add returned).
    // Re-applied on the next track-list change by picking the newest
    // external track — NOT by display-name label: mpv reports the added
    // track's title as "srt" for fd:// subs, so label matching could
    // never fire (user's v0.3.1 log). Scoped to the item it was picked
    // for, so it can never select a track in a different video.
    private var pendingExternalSubtitleMediaId: String? = null
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
            if (playbackState == PlayerEngine.STATE_READY || playbackState == PlayerEngine.STATE_ENDED) {
                cancelPreparingWatchdog()
            }
            _playbackState.update {
                it.copy(
                    playbackState = playbackState,
                    // Preparing ends the moment the engine is READY or ENDED;
                    // anything else (BUFFERING, IDLE mid-load) keeps the splash.
                    isPreparing = if (playbackState == PlayerEngine.STATE_READY || playbackState == PlayerEngine.STATE_ENDED) false else it.isPreparing,
                    // Issue #187: a stale 'Playback error' banner must not
                    // outlive a recovery — a file that loads fine after a
                    // spurious error clears it. Other notifications (e.g.
                    // 'External subtitle loaded') are left alone.
                    userNotification = if (playbackState == PlayerEngine.STATE_READY && it.userNotification?.startsWith("Playback error:") == true) null else it.userNotification,
                )
            }
            updatePosition()
            updateTracksState()
            if (playbackState == PlayerEngine.STATE_ENDED) {
                // Issue #211: mpv reports STATE_ENDED at the end of EVERY
                // item — the engine holds only the current item
                // (playlist-clear + one loadfile in load()), not a
                // playlist. The old code assumed ENDED meant the end of
                // the playlist (issue #76): OFF stopped after the first
                // item and ALL reloaded index 0 forever.
                val state = _playbackState.value
                val current = state.currentItem
                // Index of the item that just ended, from the manager's
                // own state: the tracked index when it still points at
                // the current item, otherwise resolved against the live
                // queue by id.
                val endedIndex = if (currentQueue.isEmpty()) {
                    0
                } else if (current != null &&
                    state.currentIndex in currentQueue.indices &&
                    currentQueue[state.currentIndex].id == current.id
                ) {
                    state.currentIndex
                } else {
                    current?.let { item -> currentQueue.indexOfFirst { it.id == item.id } }
                        ?.takeIf { it >= 0 }
                        ?: state.currentIndex.coerceIn(0, currentQueue.lastIndex)
                }
                val hasNext = current != null && currentQueue.isNotEmpty() &&
                    endedIndex < currentQueue.lastIndex
                when {
                    state.repeatMode == RepeatMode.ONE && current != null -> {
                        engine.seekTo(0L)
                        engine.play()
                    }
                    state.repeatMode == RepeatMode.ALL && current != null &&
                        currentQueue.isNotEmpty() -> {
                        // Advance, wrapping to 0 only from the last item.
                        // A finished item keeps no resume position.
                        resumeManager.clearResumePosition(current.id)
                        val next = if (endedIndex >= currentQueue.lastIndex) 0 else endedIndex + 1
                        // Fresh-fd load via engineLoad: the engine resolves
                        // the target from its original URI at load time, so
                        // this never reuses the fd mpv closed when the
                        // previous stream ended (issues #213/#219).
                        engineLoad(currentQueue, next, 0L)
                        engine.play()
                    }
                    hasNext -> {
                        // Repeat OFF: keep going through the queue; only
                        // the last item stops at EOF.
                        current?.let { resumeManager.clearResumePosition(it.id) }
                        engineLoad(currentQueue, endedIndex + 1, 0L)
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
            // Issue #192: second chance for a manual external pick the
            // engine could not pin synchronously — select the newest
            // external track (mpv appends added tracks, so the last one
            // is the one just picked). Only for the item it was picked
            // for; a manual pick in the sheet clears it first, and a
            // different video clears it here instead of mis-selecting.
            pendingExternalSubtitleMediaId?.let { mediaId ->
                if (_playbackState.value.currentItem?.id == mediaId) {
                    val newestExternal = _playbackState.value.subtitleTracks.lastOrNull { it.isExternal }
                    if (newestExternal != null) {
                        selectSubtitleTrack(newestExternal.groupIndex, newestExternal.trackIndex)
                        pendingExternalSubtitleMediaId = null
                    }
                } else {
                    pendingExternalSubtitleMediaId = null
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
                    // Issue #188: do not wrap the decoder name in parens
                    // again — it already carries its own ("software (H.265)").
                    "Platform override: Hardware decoder unavailable for this format on this device. Fallback to $decoderName."
                }
                currentMode == DecoderMode.SW && !isSoftware -> {
                    "Platform override: Software decoder unavailable for this format. Fallback to $decoderName."
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
            // mpv-only (issue #150): there is no fallback engine any more,
            // so every error is reported as-is — with the codec name when
            // libmpv knows it — instead of silently switching engines.
            _playbackState.update {
                it.copy(
                    isPreparing = false,
                    userNotification = if (error is MpvPlaybackException && error.codecName != null) {
                        appContext.getString(R.string.player_codec_error, error.codecName)
                    } else {
                        "Playback error: ${error.message}"
                    },
                )
            }
        }
    }

    private var activeSurface: Surface? = null

    /**
     * Active player engine instance. mpv-only (issue #150): the engine
     * setting, the engine switch and both auto-fallbacks are gone, so the
     * manager always owns exactly one [MpvPlayerEngine]. mpv captures init
     * failures instead of throwing (see MpvPlayerEngine), so construction
     * here cannot crash the app — a dead core surfaces as a real error
     * through the listener instead.
     */
    var engine: PlayerEngine = MpvPlayerEngine(appContext, initialDecoderMode).apply {
        setListener(engineListener)
    }
        private set

    /**
     * Loads [items] into the engine. The items are passed RAW (original
     * content:// URIs) — the engine resolves only the target item to an
     * fd at load time (issues #213/#219): an fd is single-use, so
     * resolving the whole queue up front leaked N-1 descriptors and left
     * the engine holding dead fd:// URIs for every re-load. The live
     * queue, the persisted queue and history keep the original URI, so a
     * restored queue re-opens a fresh fd.
     */
    private fun engineLoad(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
        engine.load(items, startIndex, resumeMs)
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
     * subtitles itself.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        (engine as? MpvPlayerEngine)?.applySubtitleSettings(settings)
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
            resumeManager.saveResumePosition(media.id, resumeMs, media.uri, media.durationMs)
        }
        playQueue(listOf(media), startIndex = 0, autoPlay = true, forceResume = resumeMs > 0L)
    }

    /**
     * Starts [items] as a new playback session from [startIndex].
     *
     * [preserveShuffleState] is for playing a position in the LIVE queue
     * (issue #203): the mini-player queue dialog passes the live queue
     * back with a new start index, which is not a new session — so the
     * pre-shuffle order and the shuffle flag are kept. It only takes
     * effect when [items] IS the live queue (same list instance); any
     * other list is a genuinely new queue and resets shuffle as before.
     * Every other caller passes a fresh list and gets the default.
     */
    fun playQueue(
        items: List<PlayableMedia>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        forceResume: Boolean = false,
        preserveShuffleState: Boolean = false,
    ) {
        if (items.isEmpty()) return
        // Issue #203: keep the shuffle state only when the queue being
        // played is the live one (same items, different start index).
        // A new queue is a new session: shuffle order does not carry over.
        val keepShuffle = preserveShuffleState && items === currentQueue
        if (!keepShuffle) {
            unshuffledQueue = null
        }
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

        engineLoad(items, targetIndex, if (shouldResume) savedPos else 0L)
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
                // Issue #203: a live-queue pick keeps the shuffle flag as
                // it is (on stays on, off stays off); a new queue resets
                // it, as before.
                shuffleEnabled = if (keepShuffle) it.shuffleEnabled else false,
                // A fresh video load is preparing until the engine reports READY.
                isPreparing = targetItem.isVideo,
            )
        }
        updatePosition()
        updateTracksState()
        // Issue #222: only run the 250 ms ticker when this session
        // actually plays. A paused restore (restorePersistedQueueIfEmpty
        // passes autoPlay = false) produces no playing-state transition,
        // so an unconditional startTicker() here ran forever — Room
        // resume writes, state emissions and notification rebuilds
        // every 250 ms until the next play/pause. Pressing play after
        // the restore still starts the ticker via onIsPlayingChanged.
        if (autoPlay) {
            startTicker()
        }
        armPreparingWatchdog()
    }

    /**
     * Plays the item at [index] in the LIVE queue (issue #203) — the
     * mini-player queue dialog's pick. The queue itself does not change,
     * only the start index, so the shuffle state is preserved: while
     * shuffled, the pre-shuffle order stays saved in [unshuffledQueue]
     * and the shuffle flag stays on, so turning shuffle off still
     * restores the original order. While unshuffled this is a plain
     * jump to the picked item. No-op on an empty queue.
     */
    fun playLiveQueueItem(index: Int) {
        if (currentQueue.isEmpty()) return
        playQueue(
            currentQueue,
            startIndex = index.coerceIn(0, currentQueue.lastIndex),
            autoPlay = true,
            preserveShuffleState = true,
        )
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
            engineLoad(newQueue, 0, position)
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
                engineLoad(original, index, position)
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
        // Issue #192: a manual pick (sheet tap / Off) supersedes any
        // pending external pick — never let a stale pending re-select
        // over the user's explicit choice.
        pendingExternalSubtitleMediaId = null
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

        // Replace current media item in engine timeline. The raw item is
        // handed over — the engine resolves only the subtitle URIs itself
        // (content:// subtitles need the same treatment as content://
        // media); the media URI is never resolved here, because
        // replaceMediaItem never reloads the file and the fd would leak
        // (issue #219). The stored item keeps the original URI so the
        // persisted queue stays restorable.
        engine.replaceMediaItem(index, updatedItem)

        // Plain seek, NOT seekTo(index, pos): the mpv engine's
        // replaceMediaItem never reloads the file (sub-add only), so the
        // position is already where it was — no reload is needed at all.
        if (currentPos > 0L) {
            engine.seekTo(currentPos)
        }
        if (wasPlaying && !engine.isPlaying) {
            engine.play()
        }

        // Issue #192: the engine pins sid to the new track synchronously
        // in replaceMediaItem; keep this as the second chance in case the
        // track was not in track-list yet when sub-add returned.
        pendingExternalSubtitleMediaId = currentItem.id

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
        _playbackState.update { it.copy(userNotification = null) }
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
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
