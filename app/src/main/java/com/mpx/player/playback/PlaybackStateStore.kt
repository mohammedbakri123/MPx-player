package com.mpx.player.playback

import com.mpx.player.playback.engine.MpvPlaybackException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lifecycle and state side-effects dispatched by playback collaborators
 * ([SubtitleManager], [PlaybackQueue], [EngineEventTranslator]) to the coordinator.
 */
sealed interface PlaybackEvent {
    data object StartPositionTicker : PlaybackEvent
    data object StopPositionTicker : PlaybackEvent
    data object RefreshPosition : PlaybackEvent
    data object RefreshPositionAndTracks : PlaybackEvent
    data object RefreshTracksState : PlaybackEvent
    data object FlushPosition : PlaybackEvent
    data object ArmLoadWatchdog : PlaybackEvent
    data object CancelLoadWatchdog : PlaybackEvent
    data object StartBackgroundService : PlaybackEvent
    data class RecordHistory(val item: PlayableMedia, val positionMs: Long) : PlaybackEvent
    data class RecordPauseHistory(val item: PlayableMedia, val positionMs: Long, val durationMs: Long) : PlaybackEvent
    data object ClearAutoDetectAttempts : PlaybackEvent
    data object MaybeAutoDetectSubtitles : PlaybackEvent
    data class CommitSubtitleItem(val index: Int, val updatedItem: PlayableMedia, val notification: String?) : PlaybackEvent
    data class EngineError(val error: Exception) : PlaybackEvent
}

/**
 * Event dispatch contract for decoupling collaborator lifecycle commands
 * from the coordinator.
 */
interface PlaybackStateDispatcher {
    fun dispatch(event: PlaybackEvent)
    fun setEventListener(listener: ((PlaybackEvent) -> Unit)?)
}

/**
 * Independent state store that owns [PlaybackState] publishing and event dispatch (Phase 8 CP-1).
 *
 * Constructible BEFORE collaborators and [PlaybackCoordinator], breaking the cyclic
 * dependency previously bridged by `CoordinatorHostProxy`. It maintains the single source
 * of truth for playback state across the application via [StateFlow] and thread-safe `.update { }`.
 */
class PlaybackStateStore(
    initialDecoderMode: DecoderMode = DecoderMode.HW,
    private val strings: CoordinatorStrings? = null,
    initialState: PlaybackState = PlaybackState(decoderMode = initialDecoderMode),
) : SubtitleHost, QueueHost, EngineEventHost, PlaybackStateDispatcher {

    private val _playbackState = MutableStateFlow(initialState)

    /** Published state flow collected by UI and coordinator consumers. */
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /** Snapshot of the published playback state. */
    override val state: PlaybackState
        get() = _playbackState.value

    /** Snapshot alias for coordinator `.value` call-sites. */
    val value: PlaybackState
        get() = _playbackState.value

    /** Updates the published state atomically. */
    fun update(transform: (PlaybackState) -> PlaybackState) {
        _playbackState.update(transform)
    }

    private var eventListener: ((PlaybackEvent) -> Unit)? = null
    private val pendingEvents = mutableListOf<PlaybackEvent>()

    override fun setEventListener(listener: ((PlaybackEvent) -> Unit)?) {
        this.eventListener = listener
        if (listener != null && pendingEvents.isNotEmpty()) {
            val toFlush = ArrayList(pendingEvents)
            pendingEvents.clear()
            for (event in toFlush) {
                listener(event)
            }
        }
    }

    override fun dispatch(event: PlaybackEvent) {
        val listener = eventListener
        if (listener != null) {
            listener(event)
        } else {
            pendingEvents.add(event)
        }
    }

    // --- SubtitleHost ---

    override val currentItem: PlayableMedia?
        get() = _playbackState.value.currentItem

    override val liveQueue: List<PlayableMedia>
        get() = _playbackState.value.queue

    override val subtitleTracks: List<SubtitleTrackInfo>
        get() = _playbackState.value.subtitleTracks

    override fun commitSubtitleItem(index: Int, updatedItem: PlayableMedia, notification: String?) {
        dispatch(PlaybackEvent.CommitSubtitleItem(index, updatedItem, notification))
    }

    override fun subtitleLoadedMessage(displayName: String): String =
        strings?.subtitleLoaded(displayName) ?: "Loaded subtitle: $displayName"

    override fun subtitleFailedMessage(displayName: String): String =
        strings?.subtitleFailed(displayName) ?: "Failed to load subtitle: $displayName"

    override fun showNotification(message: String) {
        update { it.copy(userNotification = message) }
    }

    override fun refreshTracksState() {
        dispatch(PlaybackEvent.RefreshTracksState)
    }

    // --- QueueHost ---

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
        update {
            it.copy(
                currentItem = targetItem,
                queue = items,
                currentIndex = targetIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                speed = speed,
                shuffleEnabled = shuffleEnabled,
                isPreparing = targetItem.isVideo,
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
        update {
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
        update { it.copy(shuffleEnabled = enabled) }
    }

    override fun publishRepeatMode(mode: RepeatMode) {
        update { it.copy(repeatMode = mode) }
    }

    override fun recordHistory(item: PlayableMedia, positionMs: Long) {
        dispatch(PlaybackEvent.RecordHistory(item, positionMs))
    }

    override fun clearAutoDetectAttempts() {
        dispatch(PlaybackEvent.ClearAutoDetectAttempts)
    }

    override fun refreshPositionAndTracks() {
        dispatch(PlaybackEvent.RefreshPositionAndTracks)
    }

    override fun refreshPosition() {
        dispatch(PlaybackEvent.RefreshPosition)
    }

    override fun startPositionTicker() {
        dispatch(PlaybackEvent.StartPositionTicker)
    }

    override fun armLoadWatchdog() {
        dispatch(PlaybackEvent.ArmLoadWatchdog)
    }

    // --- EngineEventHost ---

    override fun publishIsPlaying(isPlaying: Boolean) {
        update { it.copy(isPlaying = isPlaying) }
    }

    override fun startBackgroundService() {
        dispatch(PlaybackEvent.StartBackgroundService)
    }

    override fun stopPositionTicker() {
        dispatch(PlaybackEvent.StopPositionTicker)
    }

    override fun flushPosition() {
        dispatch(PlaybackEvent.FlushPosition)
    }

    override fun recordPauseHistory(item: PlayableMedia, positionMs: Long, durationMs: Long) {
        dispatch(PlaybackEvent.RecordPauseHistory(item, positionMs, durationMs))
    }

    override fun cancelLoadWatchdog() {
        dispatch(PlaybackEvent.CancelLoadWatchdog)
    }

    override fun publishEngineState(playbackState: Int, isPreparing: Boolean, userNotification: String?) {
        update {
            it.copy(
                playbackState = playbackState,
                isPreparing = isPreparing,
                userNotification = userNotification,
            )
        }
    }

    override fun maybeAutoDetectSubtitles() {
        dispatch(PlaybackEvent.MaybeAutoDetectSubtitles)
    }

    override fun publishTransition(
        item: PlayableMedia?,
        index: Int,
        hasPrevious: Boolean,
        hasNext: Boolean,
        resumedFromMs: Long?,
    ) {
        update {
            it.copy(
                currentItem = item,
                currentIndex = index,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                resumedFromMs = resumedFromMs,
                videoWidth = 0,
                videoHeight = 0,
            )
        }
    }

    override fun publishSpeed(speed: Float) {
        update { it.copy(speed = speed) }
    }

    override fun publishVideoSize(width: Int, height: Int) {
        update { it.copy(videoWidth = width, videoHeight = height) }
    }

    override fun publishDecoderInitialized(decoderName: String, fallbackNote: String?) {
        update {
            it.copy(
                activeDecoderName = decoderName,
                decoderFallbackNote = fallbackNote,
            )
        }
    }

    override fun publishEngineError(error: Exception) {
        update {
            it.copy(
                isPreparing = false,
                userNotification = if (error is MpvPlaybackException && error.codecName != null) {
                    strings?.codecError(error.codecName) ?: "Playback failed: unsupported codec ${error.codecName}"
                } else {
                    "Playback error: ${error.message}"
                },
            )
        }
        dispatch(PlaybackEvent.EngineError(error))
    }
}
