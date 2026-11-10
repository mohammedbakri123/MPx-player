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
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import com.mpx.player.util.SubtitleSiblingFinder
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
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
import kotlinx.coroutines.withContext

/**
 * Central controller for playback orchestration in MPx Player.
 * Manages the high-level playback state, playlist queue, real position ticker,
 * Room database-backed persistent resume tracking, playback history recording,
 * and delegates underlying playback engine operations to [PlayerEngine].
 */
class PlaybackManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    val database: MpxDatabase = MpxDatabase.getInstance(appContext)
    val persistenceRepository: MediaPersistenceRepository = RoomMediaPersistenceRepository(database, appContext)
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

    /**
     * Issue #220: counts playQueue sessions. playQueue runs on Main, and
     * when the resume preload has not finished yet it defers the engine
     * load to a coroutine that awaits the preload first — a second tap
     * (or a restore) in that window must supersede the pending load, not
     * race it: the deferred load only runs while its generation is still
     * the latest. Issue #258: stop() bumps the generation too, so a
     * deferred load cannot outlive a deliberate stop and resurrect the
     * queue it just cleared. Issue #272: the fast path bumps it as well —
     * a preload-complete playQueue is a new session just like a slow one,
     * and without the bump a pending deferred load would fire after it
     * and take over.
     */
    private var playQueueGeneration = 0

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
    /**
     * Issue #332: the display name of the subtitle the pending
     * confirmation (above) is waiting for, so the async success/failure
     * banner can name it. Always set and cleared together with the id.
     */
    private var pendingExternalSubtitleDisplayName: String? = null
    /**
     * Subtitle auto-detect: media ids already scanned for same-name
     * subtitle files this session. A scan is attempted once per item
     * (a folder scan that finds nothing stays attempted — re-scanning
     * on every READY re-fire would be pure disk churn). Cleared on a
     * new session (playQueue) and on stop(), so playing the video
     * again later re-scans — a subtitle added to the folder since is
     * then found. The item's own externalSubtitles list is the second
     * guard: an item that already has external subtitles (manual pick,
     * restore, or an earlier auto-detect) is never scanned again.
     */
    private val autoDetectAttemptedIds = mutableSetOf<String>()
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
                // Issue #274: pause is the flush point — the ticker stops
                // here, so force this final position into Room at once
                // instead of leaving it to the 5 s throttle window.
                updatePosition(forceSave = true)
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
                    // 'Loaded subtitle') are left alone.
                    // Issue #223: codec errors render as 'Playback failed:
                    // unsupported codec ...' (player_codec_error), not
                    // 'Playback error:' — the same READY clear must match
                    // both prefixes, or the codec banner survives every
                    // subsequent successful load.
                    userNotification = if (playbackState == PlayerEngine.STATE_READY && (it.userNotification?.startsWith("Playback error:") == true || it.userNotification?.startsWith("Playback failed:") == true)) null else it.userNotification,
                )
            }
            updatePosition()
            updateTracksState()
            if (playbackState == PlayerEngine.STATE_READY) {
                maybeAutoDetectSubtitles()
            }
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
                        // Issue #227: the resume key is the media URI, not
                        // the bare id — pass both so the manager can derive it.
                        resumeManager.clearResumePosition(current.id, current.uri)
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
                        // Issue #227: resume key is the media URI, not the bare id.
                        current?.let { resumeManager.clearResumePosition(it.id, it.uri) }
                        engineLoad(currentQueue, endedIndex + 1, 0L)
                        engine.play()
                    }
                    else -> {
                        // Issue #227: resume key is the media URI, not the bare id.
                        current?.let { resumeManager.clearResumePosition(it.id, it.uri) }
                    }
                }
            }
        }

        override fun onMediaItemTransition(index: Int) {
            val current = currentQueue.getOrNull(index)

            // Issue #283: there is deliberately no resume seek here. The
            // old branch sought to the saved position when isAuto was
            // true, but the engine always passed false (a Media3
            // MEDIA_ITEM_TRANSITION_REASON_AUTO leftover), so it could
            // never fire. Resume is applied in-place instead: every load
            // already carries its start position in load()'s resumeMs —
            // startPlayback passes the saved position, the EOF
            // auto-advance passes 0L on purpose, and shuffle passes the
            // live position. A seek here would only race the load.
            // The live read below is the history position, which must
            // still wait for the preload (issue #220) instead of
            // recording 0 when the transition fires in the first moments
            // after construction.
            current?.let { item ->
                scope.launch(Dispatchers.IO) {
                    try {
                        // Issue #220: wait for the Room preload so the
                        // recorded position is the saved one, not 0.
                        // Issue #227: the resume key is the media URI.
                        val recordedPos = resumeManager.getResumePositionAfterPreload(item.id, item.uri) ?: 0L
                        persistenceRepository.recordHistory(
                            PlaybackHistoryItem(
                                mediaUri = item.uri,
                                mediaId = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                isVideo = item.isVideo,
                                lastPlayedAt = System.currentTimeMillis(),
                                playbackPositionMs = recordedPos,
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
                    // Issue #330: only clear the resume banner when the
                    // transition actually moves to a different item.
                    // startPlayback sets resumedFromMs AFTER engineLoad
                    // posts this transition, so the session-start
                    // transition for the item startPlayback just set used
                    // to wipe the banner in the same main-thread pass,
                    // before Compose ever drew it. The transition's item
                    // is the same item the state already shows (same URI
                    // — the stable item identity, issue #227) for a
                    // session start and for same-item reloads (decoder
                    // fallback, attach, shuffle re-load), so the banner
                    // stays; a move to another item (EOF auto-advance,
                    // Next/Previous, a queue pick) clears it, so a stale
                    // banner cannot linger across the advance. A
                    // transition to no item (current == null, e.g. after
                    // stop()) clears it too.
                    resumedFromMs = if (current != null && current.uri == it.currentItem?.uri) it.resumedFromMs else null,
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
                        pendingExternalSubtitleDisplayName = null
                    }
                } else {
                    pendingExternalSubtitleMediaId = null
                    pendingExternalSubtitleDisplayName = null
                }
            }
        }

        /**
         * Issue #332: the async verdict on a manually added external
         * subtitle (see [PlayerEngine.Listener.onExternalSubtitleConfirmed]).
         * Ignores confirmations the manager is not waiting for (queue
         * restores confirm the same way) and confirmations for an item
         * that is no longer current — the user has moved on, so no banner
         * should arrive for the old pick.
         */
        override fun onExternalSubtitleConfirmed(mediaId: String, success: Boolean) {
            if (pendingExternalSubtitleMediaId != mediaId) return
            val displayName = pendingExternalSubtitleDisplayName ?: return
            pendingExternalSubtitleMediaId = null
            pendingExternalSubtitleDisplayName = null
            if (_playbackState.value.currentItem?.id != mediaId) return
            _playbackState.update {
                it.copy(
                    userNotification = if (success) {
                        appContext.getString(R.string.player_toast_subtitle_loaded, displayName)
                    } else {
                        appContext.getString(R.string.player_subtitle_load_failed, displayName)
                    },
                )
            }
        }

        override fun onPlaybackParametersChanged(speed: Float) {
            _playbackState.update { it.copy(speed = speed) }
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            // Issue #260: the video's dimensions became known (or the
            // video unloaded) — publish them as state so the PiP params
            // effect re-runs with the real aspect.
            _playbackState.update { it.copy(videoWidth = width, videoHeight = height) }
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
     * Issue #225: display size of the current video (engine's
     * `dwidth`/`dheight`), the PiP aspect-ratio source of truth. 0 when
     * no video is loaded.
     */
    val videoWidth: Int
        get() = engine.videoWidth

    val videoHeight: Int
        get() = engine.videoHeight

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
            // Issue #227: the manager keys this row by media.uri, not the
            // bare id — both are passed so the key can be derived.
            // Issue #274: force — an explicit user-chosen resume position
            // must land in Room at once, not at the next throttle window.
            resumeManager.saveResumePosition(media.id, resumeMs, media.uri, media.durationMs, force = true)
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
     * Issue #239: the same "not a new session" applies to speed — a
     * live-queue pick keeps the speed that is playing instead of
     * resetting to the persisted default.
     */
    fun playQueue(
        items: List<PlayableMedia>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        forceResume: Boolean = false,
        preserveShuffleState: Boolean = false,
    ) {
        if (items.isEmpty()) return
        // Subtitle auto-detect: a new session re-scans — a subtitle
        // added to the folder since the last play is then found.
        autoDetectAttemptedIds.clear()
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
        // Issue #220: the engine load needs the saved resume position, and
        // the Room preload may not have landed yet (a tap right after a
        // cold start, or the process-death restore) — reading the cache
        // now would return null and start the video from 0. Fast path:
        // preload already done, load synchronously exactly as before.
        // Slow path: wait for the preload first, on Main (no thread is
        // blocked — the preload is one Room query). A newer playQueue in
        // that window supersedes this one via playQueueGeneration.
        if (resumeManager.isPreloadComplete) {
            // Issue #272: the fast path is a new session too — bump the
            // generation so a still-pending deferred load (tap A while the
            // preload was pending) cannot fire after this load and take
            // over. Without this, tap B once the preload lands started B,
            // then A's coroutine resumed, saw its generation still match,
            // and put A back on screen.
            playQueueGeneration++
            startPlayback(items, targetIndex, targetItem, autoPlay, forceResume, keepShuffle)
        } else {
            val generation = ++playQueueGeneration
            scope.launch {
                resumeManager.awaitPreload()
                if (generation != playQueueGeneration) return@launch
                startPlayback(items, targetIndex, targetItem, autoPlay, forceResume, keepShuffle)
            }
        }
    }

    /**
     * The resume-dependent tail of [playQueue]: reads the saved position
     * (only correct once the Room preload has finished — see #220),
     * loads the target item at it, and publishes the new session state.
     * Runs synchronously from playQueue on the fast path, or from a
     * Main-scope coroutine that awaited the preload on the slow path.
     */
    private fun startPlayback(
        items: List<PlayableMedia>,
        targetIndex: Int,
        targetItem: PlayableMedia,
        autoPlay: Boolean,
        forceResume: Boolean,
        keepShuffle: Boolean,
    ) {
        // Issue #227: the resume lookup key is the media URI, not the bare
        // id — pass both so the manager can derive it.
        val savedPos = resumeManager.getResumePosition(targetItem.id, targetItem.uri) ?: 0L
        val settings = settingsRepository.load()
        val shouldResume = (forceResume || settings.resumePlayback) &&
            savedPos > 2_000L

        // Issue #239: a live-queue pick (playLiveQueueItem) is not a new
        // session, so the speed that is playing is kept — the same
        // reasoning as the shuffle state above. Capture it before the
        // load, mirroring setShuffleEnabled, which re-applies the playing
        // speed after its own reload for the same reason.
        val currentSpeed = _playbackState.value.speed
        engineLoad(items, targetIndex, if (shouldResume) savedPos else 0L)
        if (keepShuffle) {
            engine.setSpeed(currentSpeed)
        } else {
            // Persisted default speed (issue #76): every new session
            // starts here. The in-player speed toggle stays session-only
            // (see setSpeed).
            engine.setSpeed(settings.defaultPlaybackSpeed)
        }
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
                // Issue #239: a live-queue pick keeps the playing speed
                // (state and engine agree — see the setSpeed above); a
                // new session starts at the persisted default (#76).
                speed = if (keepShuffle) it.speed else settings.defaultPlaybackSpeed,
                // Issue #203: a live-queue pick keeps the shuffle flag as
                // it is (on stays on, off stays off); a new queue resets
                // it, as before.
                shuffleEnabled = if (keepShuffle) it.shuffleEnabled else false,
                // A fresh video load is preparing until the engine reports READY.
                isPreparing = targetItem.isVideo,
                // Issue #260: same reset as onMediaItemTransition — the
                // transition callback may land after this update, so the
                // effect must not read the previous video's size here.
                videoWidth = 0,
                videoHeight = 0,
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
                // Local playback fix: play via the MediaStore content:// URI
                // (MpvFdOpener turns it into fd:// at load time). The raw
                // DATA path is denied to native code under scoped storage.
                uri = track.contentUri.ifBlank { track.path },
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
                // Local playback fix: play via the MediaStore content:// URI
                // (MpvFdOpener turns it into fd:// at load time). The raw
                // DATA path is denied to native code under scoped storage.
                uri = video.contentUri.ifBlank { video.path },
                isVideo = true,
                durationMs = video.durationMs,
                // Subtitle auto-detect + in-app browser: keep the raw
                // filesystem path so the player can look for same-name
                // subtitle files in the video's folder (see
                // SubtitleSiblingFinder). The content:// uri above stays
                // the playback source.
                filePath = video.path,
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
        pendingExternalSubtitleDisplayName = null
        engine.selectSubtitleTrack(groupIndex, trackIndex)
        updateTracksState()
    }

    /**
     * Loads an external subtitle file (SRT / VTT) into the currently playing media item.
     * Replaces the media item in the engine so the new sidecar subtitle track is loaded,
     * maintaining the current playback position and state, and marks the track to be auto-selected.
     *
     * Issue #332: returns whether the subtitle was ACCEPTED for loading —
     * never an unconditional success any more. The old code returned true
     * and showed "External subtitle loaded" even when every sub-add had
     * failed, because `mpv.command()` never throws for a rejected file.
     * ISSUED/QUEUED are accepted and their real outcome arrives
     * asynchronously via [onExternalSubtitleConfirmed], which shows the
     * success/failure banner; FAILED (nothing could be attempted) shows
     * the failure banner here and returns false.
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

        val wasPlaying = engine.isPlaying

        // Replace current media item in engine timeline. The raw item is
        // handed over — the engine resolves only the subtitle URIs itself
        // (content:// subtitles need the same treatment as content://
        // media); the media URI is never resolved here, because
        // replaceMediaItem never reloads the file and the fd would leak
        // (issue #219). The stored item keeps the original URI so the
        // persisted queue stays restorable.
        return when (val result = engine.replaceMediaItem(index, updatedItem)) {
            ReplaceMediaItemResult.ISSUED, ReplaceMediaItemResult.QUEUED -> {
                // Issue #192: the engine pins sid to the new track; keep
                // this as the second chance in case the track was not in
                // track-list yet when sub-add returned. Issue #332: the
                // success/failure banner is NOT shown here — it arrives
                // with the async confirmation, once the track actually
                // appears (or the timeout reports it never did).
                pendingExternalSubtitleMediaId = currentItem.id
                pendingExternalSubtitleDisplayName = displayName
                commitSubtitleAdd(index, updatedItem, wasPlaying, notification = null)
                true
            }
            ReplaceMediaItemResult.ALREADY_ADDED -> {
                // No sub-add was issued — the subtitle was already in the
                // item. It is loaded iff mpv's track-list currently holds
                // at least as many external tracks as the item lists
                // external subtitles (checked live on the engine, not the
                // cached state, so a track that only just appeared still
                // counts). A re-pick of a subtitle that never loaded
                // reports failure instead of the old lying success.
                val loadedExternalCount = engine.getSubtitleTracks().count { it.isExternal }
                if (loadedExternalCount >= updatedItem.externalSubtitles.size) {
                    pendingExternalSubtitleMediaId = null
                    pendingExternalSubtitleDisplayName = null
                    commitSubtitleAdd(
                        index, updatedItem, wasPlaying,
                        notification = appContext.getString(R.string.player_toast_subtitle_loaded, displayName),
                    )
                    true
                } else {
                    commitSubtitleAdd(index, updatedItem, wasPlaying, notification = null)
                    _playbackState.update {
                        it.copy(userNotification = appContext.getString(R.string.player_subtitle_load_failed, displayName))
                    }
                    false
                }
            }
            ReplaceMediaItemResult.FAILED -> {
                // Nothing was attempted (unreadable content://, missing
                // file, or no new subtitles). Re-sync the engine's queue
                // with the original item — replaceMediaItem stored the
                // updated item even though it added nothing — and report
                // the failure instead of the old unconditional success.
                engine.replaceMediaItem(index, currentItem)
                restorePlaybackAfterSubtitleAdd(wasPlaying)
                _playbackState.update {
                    it.copy(userNotification = appContext.getString(R.string.player_subtitle_load_failed, displayName))
                }
                false
            }
        }
    }

    /**
     * Subtitle auto-detect (MX behaviour): when a local video's file
     * has loaded, look in its folder for subtitle files with the same
     * name (`movie.srt`, `movie.en.srt`, `movie.ar.srt`) and load them
     * through the same [loadExternalSubtitle] path a manual pick uses
     * — the engine sub-adds them and selects the last one added, and
     * the #332 confirmation shows the usual "Loaded subtitle" banner
     * for it. Matches are added worst-first so the best match (the
     * exact stem) is added last and ends up selected.
     *
     * Runs only for local videos ([PlayableMedia.filePath] present)
     * with no external subtitles yet. A manual pick made while the
     * folder scan is running wins: the state is re-checked on Main
     * before loading, and an item that gained external subtitles in
     * the meantime is skipped. Network streams have no folder, so
     * nothing is attempted for them. A folder the app cannot list
     * (no All files access on Android 11+) simply yields no matches
     * — the subtitle page explains that and offers the grant.
     */
    private fun maybeAutoDetectSubtitles() {
        val item = _playbackState.value.currentItem ?: return
        if (!item.isVideo) return
        val videoPath = item.filePath?.takeIf { it.isNotBlank() } ?: return
        if (item.externalSubtitles.isNotEmpty()) return
        // Without All files access the folder scan cannot see subtitle
        // files on Android 11+ and mpv cannot open a raw path either
        // (issue #444) — attempting would only produce a "failed to
        // load" banner for a file the user never asked for. Checked
        // before marking the id attempted, so granting the permission
        // later in the session still allows a scan on the next video.
        if (!SubtitleSiblingFinder.hasAllFilesAccess(appContext)) return
        if (!autoDetectAttemptedIds.add(item.id)) return
        scope.launch(Dispatchers.IO) {
            val siblings = SubtitleSiblingFinder.findSiblings(videoPath)
            if (siblings.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                val current = _playbackState.value.currentItem ?: return@withContext
                if (current.id != item.id) return@withContext
                if (current.externalSubtitles.isNotEmpty()) return@withContext
                // Worst-first: the engine selects the last sub-added
                // track, so the best match must be added last.
                for (file in siblings.asReversed()) {
                    val uri = Uri.fromFile(file)
                    when (val validation = SubtitleUtils.validateSubtitleFile(appContext, uri)) {
                        is SubtitleValidationResult.Supported -> {
                            loadExternalSubtitle(
                                uri = validation.uri,
                                displayName = validation.displayName,
                                mimeType = validation.mimeType,
                            )
                        }
                        is SubtitleValidationResult.Unsupported -> {
                            // The finder already filters by extension;
                            // unreachable in practice, skip silently.
                        }
                    }
                }
            }
        }
    }

    /**
     * Issue #332: commits a subtitle add to the manager's queue and state
     * (issue #149: the queue item itself changed), restoring the playback
     * position/state afterwards. [notification] is shown only when
     * non-null — an accepted (ISSUED/QUEUED) add shows nothing yet; its
     * banner arrives with the async confirmation.
     */
    private fun commitSubtitleAdd(
        index: Int,
        updatedItem: PlayableMedia,
        wasPlaying: Boolean,
        notification: String?,
    ) {
        val updatedQueue = currentQueue.toMutableList()
        updatedQueue[index] = updatedItem
        currentQueue = updatedQueue
        restorePlaybackAfterSubtitleAdd(wasPlaying)
        _playbackState.update { state ->
            val withItem = state.copy(currentItem = updatedItem, queue = updatedQueue)
            if (notification != null) withItem.copy(userNotification = notification) else withItem
        }
        // Issue #149: the queue item itself changed (new subtitle list).
        persistQueue(index)
    }

    /**
     * Issue #332: plain seek, NOT seekTo(index, pos): the mpv engine's
     * replaceMediaItem never reloads the file (sub-add only), so the
     * position is already where it was — no reload is needed at all.
     *
     * Issue #357: there is deliberately NO seek here at all any more.
     * A seek makes mpv re-evaluate track selection, and with `sid`
     * still at "auto" it falls back to the default-flagged embedded
     * track — unselecting the external subtitle that was just sub-added
     * (user's v0.3.1 log: sid=2 selected, sid=1 back two seconds
     * later). Issue #372 fixed the deeper half of that: the engine now
     * remembers the picked external subtitle by identity and re-applies
     * `sid` to it on every track-list refresh, so a re-evaluation can no
     * longer strand the pick — but the seek is still gone, because
     * there is nothing for it to do here at all.
     */
    private fun restorePlaybackAfterSubtitleAdd(wasPlaying: Boolean) {
        if (wasPlaying && !engine.isPlaying) {
            engine.play()
        }
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
        autoDetectAttemptedIds.clear()
        // Issue #258: a deliberate stop ends the session, so the live
        // queue goes too — otherwise the fallback reload's
        // onMediaItemTransition (or any other in-flight transition)
        // resurrects currentItem in state and persistQueue() re-saves
        // the queue stop() just cleared from the store. With the queue
        // empty, a late transition resolves to null and persistQueue
        // re-saves nothing (save() clears on an empty list).
        currentQueue = emptyList()
        // Issue #258: a playQueue still awaiting the Room preload (the
        // #220 slow path) holds its own captured items, so clearing
        // the queue alone cannot stop it — bump the generation so its
        // deferred startPlayback is superseded like any other stale load.
        playQueueGeneration++
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
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
