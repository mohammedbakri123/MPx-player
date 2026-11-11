package com.mpx.player.playback

import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.engine.PlayerEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What [EngineEventTranslator] needs from the playback coordinator
 * that owns the published [PlaybackState], the position ticker, and
 * the preparing watchdog. The translator decides what an engine event
 * means; the host applies the state and lifecycle effects (publishing
 * state, starting/stopping the ticker, arming/cancelling the watchdog,
 * recording history, starting the background service) so the
 * translator never touches the coordinator's state directly.
 *
 * The one deliberate asymmetry: the translator talks to the
 * [PlayerEngine], the [PlaybackQueue], the [SubtitleManager], and
 * the [PlaybackResumeManager] directly (reads and commands), but
 * every [PlaybackState] write goes through this host — the state
 * object stays owned by exactly one class.
 */
interface EngineEventHost {
    /** Snapshot of the published playback state. */
    val state: PlaybackState

    /** Publish an `isPlaying` change. */
    fun publishIsPlaying(isPlaying: Boolean)

    /** Start the foreground service for background playback. */
    fun startBackgroundService()

    /** Start the 250 ms position ticker. */
    fun startPositionTicker()

    /** Stop the position ticker. */
    fun stopPositionTicker()

    /**
     * Issue #274: flush the current position into Room at once
     * (the pause flush point — the ticker stops with playback, so
     * without this the persisted position could sit up to 5 s
     * behind). Also refreshes the published position fields.
     */
    fun flushPosition()

    /**
     * Record a history row for [item] at [positionMs], fire-and-
     * forget on IO. A failure is swallowed by the host: history must
     * never break playback. Same row as [QueueHost.recordHistory];
     * one implementation serves both interfaces.
     */
    fun recordHistory(item: PlayableMedia, positionMs: Long)

    /**
     * Record the pause-time history row for [item]: [positionMs] is
     * the live engine position and [durationMs] the live engine
     * duration (falling back to the item's stored duration when the
     * engine reports none). Same fire-and-forget, failure-swallowed
     * contract as [recordHistory].
     */
    fun recordPauseHistory(item: PlayableMedia, positionMs: Long, durationMs: Long)

    /** Cancel the 20 s preparing watchdog (READY, ENDED, or error). */
    fun cancelLoadWatchdog()

    /**
     * Publish an engine playback-state change: the state int, whether
     * a video load is still preparing, and the user notification
     * (already cleared by the caller when a READY follows an error
     * banner, per issues #187/#223).
     */
    fun publishEngineState(playbackState: Int, isPreparing: Boolean, userNotification: String?)

    /** Refresh position and track state after an engine event. */
    fun refreshPositionAndTracks()

    /** Run subtitle auto-detect for the current item, if applicable. */
    fun maybeAutoDetectSubtitles()

    /**
     * Publish a media-item transition: the item now live at [index]
     * (null when the transition resolves to nothing), the engine's
     * has-previous/has-next, and the resume banner — [resumedFromMs]
     * already resolved by the caller (issue #330: kept only when the
     * transition stays on the same item). The video size resets to 0
     * (issue #260: the new item's dimensions are not known yet).
     */
    fun publishTransition(
        item: PlayableMedia?,
        index: Int,
        hasPrevious: Boolean,
        hasNext: Boolean,
        resumedFromMs: Long?,
    )

    /** Re-read the engine's track lists into published state. */
    fun refreshTracksState()

    /** Publish a playback-speed change from the engine. */
    fun publishSpeed(speed: Float)

    /** Publish the video's display size (issue #260). */
    fun publishVideoSize(width: Int, height: Int)

    /**
     * Publish a decoder initialisation: the active decoder name and
     * the fallback note (already computed by the caller, or null when
     * the decoder matches the requested mode).
     */
    fun publishDecoderInitialized(decoderName: String, fallbackNote: String?)

    /**
     * Publish an engine error: preparing ends and the error becomes
     * the user notification (a codec error names the codec, per issue
     * #150). The host also logs the error; the message formatting
     * needs string resources, so it stays with the host.
     */
    fun publishEngineError(error: Exception)
}

/**
 * Engine-event translation, extracted from the old PlaybackManager
 * (migration Phase 3, step 3). Owns the [PlayerEngine.Listener]
 * implementation: it turns mpv callbacks into published-state
 * updates and into queue / resume / subtitle effects, and nothing
 * else.
 *
 * No behaviour change from the old PlaybackManager code this was moved
 * from — the callback bodies are verbatim except for the seams
 * below.
 *
 * Seams:
 * - Every [PlaybackState] write goes through [EngineEventHost];
 *   the translator computes the values (the #187/#223 banner clear,
 *   the #330 resume-banner rule, the #188 decoder fallback note)
 *   and the host applies the copy, so the decision logic is here
 *   and unit-testable while the state stays owned by one class.
 * - The preparing watchdog stays in [PlaybackCoordinator]: it is armed
 *   from session start (via [QueueHost.armLoadWatchdog]) as well as
 *   cancelled from listener paths, so it is not listener-only. The
 *   translator only asks the host to cancel it.
 * - The engine load in the STATE_ENDED branch goes straight to
 *   `engine.load(...)` — the old manager's `engineLoad` was a trivial
 *   forwarder to it.
 * - The codec-error banner text stays with the host: it needs string
 *   resources (`player_codec_error`), which the translator must not
 *   reach for. The host receives the raw exception.
 * - The listener is attached by the coordinator after construction
 *   (`engine.setListener(translator)`): the engine, the queue, and
 *   the subtitle manager must all exist first, and no load can fire
 *   an event before the coordinator finishes constructing — the
 *   same ordering as before, when the listener was attached in the
 *   engine property initializer. A captured mpv init failure is
 *   still flushed by `setListener` itself (see MpvPlayerEngine).
 *
 * What unit tests cover: the isPlaying start/stop effects (ticker,
 * background service, pause flush + history), the READY/ENDED
 * state publishes and the ENDED decision execution, transition
 * index handling (including the #330 banner rule and the persisted
 * queue), track/subtitle delegation, speed/size/decoder/error
 * publishes. What they cannot cover, and can only be verified on
 * device: real mpv event timing (the fake engine answers
 * synchronously), the real preparing watchdog against a stuck load,
 * and the background service actually starting.
 */
class EngineEventTranslator(
    private val engine: PlayerEngine,
    private val queue: PlaybackQueue,
    private val subtitleManager: SubtitleManager,
    private val resumeManager: PlaybackResumeManager,
    private val settingsRepository: AppSettingsRepository,
    private val host: EngineEventHost,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlayerEngine.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        host.publishIsPlaying(isPlaying)
        if (isPlaying) {
            val settings = settingsRepository.load()
            if (settings.backgroundPlay) {
                host.startBackgroundService()
            }
            host.startPositionTicker()
        } else {
            host.stopPositionTicker()
            // Issue #274: pause is the flush point — the ticker stops
            // here, so force this final position into Room at once
            // instead of leaving it to the 5 s throttle window.
            host.flushPosition()
            host.state.currentItem?.let { item ->
                val pos = engine.currentPosition
                val dur = engine.duration
                if (pos > 1_500L) {
                    host.recordPauseHistory(item, pos, if (dur > 0L) dur else item.durationMs)
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == PlayerEngine.STATE_READY || playbackState == PlayerEngine.STATE_ENDED) {
            host.cancelLoadWatchdog()
        }
        val old = host.state
        host.publishEngineState(
            playbackState = playbackState,
            // Preparing ends the moment the engine is READY or ENDED;
            // anything else (BUFFERING, IDLE mid-load) keeps the splash.
            isPreparing = if (playbackState == PlayerEngine.STATE_READY || playbackState == PlayerEngine.STATE_ENDED) false else old.isPreparing,
            // Issue #187: a stale 'Playback error' banner must not
            // outlive a recovery — a file that loads fine after a
            // spurious error clears it. Other notifications (e.g.
            // 'Loaded subtitle') are left alone.
            // Issue #223: codec errors render as 'Playback failed:
            // unsupported codec ...' (player_codec_error), not
            // 'Playback error:' — the same READY clear must match
            // both prefixes, or the codec banner survives every
            // subsequent successful load.
            userNotification = if (playbackState == PlayerEngine.STATE_READY && (old.userNotification?.startsWith("Playback error:") == true || old.userNotification?.startsWith("Playback failed:") == true)) null else old.userNotification,
        )
        host.refreshPositionAndTracks()
        if (playbackState == PlayerEngine.STATE_READY) {
            host.maybeAutoDetectSubtitles()
        }
        if (playbackState == PlayerEngine.STATE_ENDED) {
            // Issue #211: mpv reports STATE_ENDED at the end of EVERY
            // item — the engine holds only the current item
            // (playlist-clear + one loadfile in load()), not a
            // playlist. The old code assumed ENDED meant the end of
            // the playlist (issue #76): OFF stopped after the first
            // item and ALL reloaded index 0 forever. What plays next
            // is the queue's decision ([PlaybackQueue.endedAction]);
            // this branch only executes it.
            val state = host.state
            val current = state.currentItem
            when (val action = queue.endedAction(current, state.currentIndex, state.repeatMode)) {
                PlaybackQueue.EndedAction.ReplayCurrent -> {
                    engine.seekTo(0L)
                    engine.play()
                }
                is PlaybackQueue.EndedAction.AdvanceTo -> {
                    // A finished item keeps no resume position.
                    // Issue #227: the resume key is the media URI, not
                    // the bare id — pass both so the manager can derive it.
                    current?.let { resumeManager.clearResumePosition(it.id, it.uri) }
                    // Fresh-fd load via engine.load: the engine resolves
                    // the target from its original URI at load time, so
                    // this never reuses the fd mpv closed when the
                    // previous stream ended (issues #213/#219).
                    engine.load(queue.items, action.index, 0L)
                    engine.play()
                }
                PlaybackQueue.EndedAction.Stop -> {
                    // Issue #227: the resume key is the media URI, not the bare id.
                    current?.let { resumeManager.clearResumePosition(it.id, it.uri) }
                }
            }
        }
    }

    override fun onMediaItemTransition(index: Int) {
        val current = queue.itemAt(index)

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
            scope.launch(ioDispatcher) {
                // Issue #220: wait for the Room preload so the
                // recorded position is the saved one, not 0.
                // Issue #227: the resume key is the media URI.
                val recordedPos = resumeManager.getResumePositionAfterPreload(item.id, item.uri) ?: 0L
                host.recordHistory(item, recordedPos)
            }
        }

        val old = host.state
        host.publishTransition(
            item = current,
            index = index,
            hasPrevious = engine.hasPreviousMediaItem,
            hasNext = engine.hasNextMediaItem,
            // Issue #330: only clear the resume banner when the
            // transition actually moves to a different item.
            // startPlayback sets resumedFromMs AFTER the engine load
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
            resumedFromMs = if (current != null && current.uri == old.currentItem?.uri) old.resumedFromMs else null,
        )
        host.refreshPositionAndTracks()
        // Issue #149: the current index moved; the queue itself did not.
        queue.persistQueue(index)
    }

    override fun onTracksChanged() {
        host.refreshTracksState()
        subtitleManager.onTracksChanged()
    }

    override fun onExternalSubtitleConfirmed(mediaId: String, success: Boolean) {
        subtitleManager.onExternalSubtitleConfirmed(mediaId, success)
    }

    override fun onPlaybackParametersChanged(speed: Float) {
        host.publishSpeed(speed)
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        // Issue #260: the video's dimensions became known (or the
        // video unloaded) — publish them as state so the PiP params
        // effect re-runs with the real aspect.
        host.publishVideoSize(width, height)
    }

    override fun onVideoDecoderInitialized(decoderName: String) {
        val currentMode = host.state.decoderMode
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

        host.publishDecoderInitialized(decoderName, fallbackNote)
    }

    override fun onError(error: Exception) {
        host.cancelLoadWatchdog()
        host.publishEngineError(error)
    }
}
