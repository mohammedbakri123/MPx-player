package com.mpx.player.playback

import com.mpx.player.playback.engine.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sink for engine position and tracks updates published by [PositionTracker] (migration Phase 4, step 2: R-2).
 *
 * Implemented by [PlaybackCoordinator] so state updates remain coordinated in a single owner.
 */
interface PositionSink {
    /** The currently playing media item, if any. */
    val currentItem: PlayableMedia?

    /**
     * Publishes polled engine position, duration, buffering, navigation flags,
     * and diagnostics metrics.
     */
    fun updatePositionState(
        currentPositionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
        hasPrevious: Boolean,
        hasNext: Boolean,
        playbackMetrics: EnginePlaybackMetrics?,
        mpvLoadStage: MpvLoadStage?,
    )

    /**
     * Publishes refreshed audio and subtitle tracks.
     */
    fun updateTracksState(
        audioTracks: List<AudioTrackInfo>,
        selectedAudioTrackIndex: Int,
        subtitleTracks: List<SubtitleTrackInfo>,
        selectedSubtitleTrackIndex: Int,
    )
}

/**
 * Tracks engine playback position and tracks state (migration Phase 4, step 2: R-2).
 *
 * Owns the ticker job, engine polling at 250ms cadence, resume-save throttling
 * (1,500ms startup threshold / 2s-from-end rules, with [forceSave] flush on pause/stop),
 * and tracks state synchronization. State mutations are forwarded to [PositionSink].
 */
class PositionTracker(
    private val engine: PlayerEngine,
    private val resumeManager: PlaybackResumeManager,
    private val scope: CoroutineScope,
    private val sink: PositionSink,
) {
    private var tickerJob: Job? = null

    /**
     * Starts or restarts the position ticker at 250ms cadence.
     */
    fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updatePosition()
                delay(250)
            }
        }
    }

    /**
     * Stops the running position ticker job.
     */
    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Polls the engine's live playback position, saves resume position with throttling,
     * and updates published playback state through [sink].
     *
     * Issue #274: [forceSave] bypasses the manager's Room-write throttle
     * for the final position when playback stops (pause) — the ticker
     * stops with it, so without the flush the persisted position could
     * sit up to 5s behind. The ticker itself passes false.
     */
    fun updatePosition(forceSave: Boolean = false) {
        val current = engine.currentPosition
        val duration = engine.duration
        val buffered = engine.bufferedPosition

        // Save position into persistent resume store. Issue #227: the
        // manager keys this row by the media URI, not the bare id — both
        // are passed so the key can be derived.
        sink.currentItem?.let { item ->
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

        sink.updatePositionState(
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

    /**
     * Polls engine tracks, annotates external subtitles based on the current item,
     * and publishes track lists with selected indices to [sink].
     */
    fun updateTracksState() {
        val audioList = engine.getAudioTracks().toMutableList()
        val rawSubtitles = engine.getSubtitleTracks()
        val externalNames = sink.currentItem?.externalSubtitles?.map { it.name } ?: emptyList()

        val subtitleList = rawSubtitles.map { track ->
            val isExt = externalNames.any { it.equals(track.label, ignoreCase = true) }
            if (isExt) track.copy(isExternal = true) else track
        }.toMutableList()

        val selectedAudioIdx = audioList.indexOfFirst { it.isSelected }
        val selectedSubtitleIdx = subtitleList.indexOfFirst { it.isSelected }

        sink.updateTracksState(
            audioTracks = audioList,
            selectedAudioTrackIndex = selectedAudioIdx,
            subtitleTracks = subtitleList,
            selectedSubtitleTrackIndex = selectedSubtitleIdx,
        )
    }
}
