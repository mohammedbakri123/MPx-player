package com.mpx.player.playback

import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.engine.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What [PlaybackQueue] needs from the playback coordinator that owns
 * the published [PlaybackState] and the engine. The queue decides
 * what plays and in what order; the host applies the state and
 * lifecycle effects (publishing a session, recording history,
 * starting the position ticker, arming the preparing watchdog) so
 * the queue never touches the coordinator's state directly.
 *
 * The one deliberate asymmetry: the queue talks to the [PlayerEngine]
 * directly for loads and transport (it holds the engine reference),
 * but every [PlaybackState] write goes through this host — the state
 * object stays owned by exactly one class.
 */
interface QueueHost {
    /** Snapshot of the published playback state. */
    val state: PlaybackState

    /**
     * Publish the state for a newly started session: the target item
     * becomes current, the queue and index are the session's, the
     * resume banner is [resumedFromMs] (null when the session did
     * not resume), the speed is the session's speed, and the shuffle
     * flag is the session's. The preparing flag follows the target
     * item (a video load is preparing until the engine reports
     * READY); the video size resets to 0, as on a track transition
     * (issue #260).
     */
    fun publishSession(
        items: List<PlayableMedia>,
        targetIndex: Int,
        targetItem: PlayableMedia,
        resumedFromMs: Long?,
        speed: Float,
        shuffleEnabled: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
    )

    /**
     * Publish a queue-order change from shuffle on/off: the queue,
     * the index of the current item in it, and the shuffle flag.
     * [hasPrevious]/[hasNext] are computed from the index and the
     * queue size by the caller.
     */
    fun publishQueueOrder(
        items: List<PlayableMedia>,
        currentIndex: Int,
        shuffleEnabled: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
    )

    /** Publish a shuffle-flag-only change (no current item to reorder). */
    fun publishShuffleEnabled(enabled: Boolean)

    /** Publish a repeat-mode change. */
    fun publishRepeatMode(mode: RepeatMode)

    /**
     * Record a history row for [item] at [positionMs], fire-and-
     * forget on IO. A failure is swallowed by the host: history must
     * never break playback.
     */
    fun recordHistory(item: PlayableMedia, positionMs: Long)

    /** Forget subtitle auto-detect attempts for the new session. */
    fun clearAutoDetectAttempts()

    /** Refresh position and track state after a session start. */
    fun refreshPositionAndTracks()

    /**
     * Refresh position state only (no track re-read). Shuffle
     * re-loads the same item, so its track list is unchanged — the
     * old code refreshed the position alone here, and re-reading the
     * tracks mid-reload could publish a transiently empty list.
     */
    fun refreshPosition()

    /** Start the 250 ms position ticker. */
    fun startPositionTicker()

    /** Arm the 20 s preparing watchdog for a video load. */
    fun armLoadWatchdog()
}

/**
 * Queue responsibility, extracted from the old PlaybackManager (migration
 * Phase 3, step 2). Owns the live queue, the pre-shuffle order,
 * the playQueue session generation, queue building (audio / video /
 * live picks), shuffle and repeat, and queue persistence — plus the
 * pure end-of-item decision (what plays next when the engine
 * reports ENDED).
 *
 * No behaviour change from the old PlaybackManager code this was moved
 * from — the bodies are verbatim except for the seams below.
 *
 * Seams:
 * - The queue holds the [PlayerEngine] and loads it directly, but
 *   every [PlaybackState] write goes through [QueueHost], and
 *   history recording, the position ticker, and the preparing
 *   watchdog are host effects too.
 * - The queue store arrives as the [QueueStore] interface so the
 *   persist/restore paths are unit-testable with a fake; production
 *   passes [PlaybackQueueStore].
 * - The resume position comes from [PlaybackResumeManager] (its
 *   preload is the #220 slow path) and the resume/speed settings
 *   from [AppSettingsRepository].
 *
 * Deliberately NOT moved here: the engine listeners themselves.
 * `onPlaybackStateChanged` (including the STATE_ENDED branch) and
 * `onMediaItemTransition` stay in [PlaybackCoordinator] — they are
 * engine-event translation, a later Phase 3 step. The ENDED branch
 * asks this class for its decision via [endedAction] and executes
 * it; the transition handler reads the queue through [itemAt] and
 * persists through [persistQueue]. Nothing is decided in two
 * places: the queue owns "what plays next", the manager owns
 * "the engine said ENDED, now do it".
 *
 * What unit tests cover: queue building, the shuffle/unshuffle
 * round-trip, repeat-mode cycling, next/previous selection (the
 * [endedAction] decision), persist/restore, and the deferred-load
 * generation guard (#220/#258/#272). What they cannot cover, and
 * can only be verified on device: actual playback through mpv (the
 * fake engine answers synchronously), and the real queue store
 * against SharedPreferences (the fake store is in-memory).
 */
class PlaybackQueue(
    private val engine: PlayerEngine,
    private val queueStore: QueueStore,
    private val resumeManager: PlaybackResumeManager,
    private val settingsRepository: AppSettingsRepository,
    private val host: QueueHost,
    private val scope: CoroutineScope,
) {
    private var currentQueue: List<PlayableMedia> = emptyList()
    /** Pre-shuffle queue order, restored when shuffle is turned off (issue #76). */
    private var unshuffledQueue: List<PlayableMedia>? = null

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

    /** The live queue, as the coordinator's listeners read it. */
    val items: List<PlayableMedia>
        get() = currentQueue

    /** The queue item at [index], or null when out of range. */
    fun itemAt(index: Int): PlayableMedia? = currentQueue.getOrNull(index)

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
        host.clearAutoDetectAttempts()
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
        val state = host.state
        val currentSpeed = state.speed
        engine.load(items, targetIndex, if (shouldResume) savedPos else 0L)
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

        host.recordHistory(targetItem, if (shouldResume) savedPos else 0L)

        host.publishSession(
            items = items,
            targetIndex = targetIndex,
            targetItem = targetItem,
            resumedFromMs = if (shouldResume) savedPos else null,
            // Issue #239: a live-queue pick keeps the playing speed
            // (state and engine agree — see the setSpeed above); a
            // new session starts at the persisted default (#76).
            speed = if (keepShuffle) currentSpeed else settings.defaultPlaybackSpeed,
            // Issue #203: a live-queue pick keeps the shuffle flag as
            // it is (on stays on, off stays off); a new queue resets
            // it, as before.
            shuffleEnabled = if (keepShuffle) state.shuffleEnabled else false,
            hasPrevious = engine.hasPreviousMediaItem,
            hasNext = engine.hasNextMediaItem,
        )
        host.refreshPositionAndTracks()
        // Issue #222: only run the 250 ms ticker when this session
        // actually plays. A paused restore (restorePersistedQueueIfEmpty
        // passes autoPlay = false) produces no playing-state transition,
        // so an unconditional startTicker() here ran forever — Room
        // resume writes, state emissions and notification rebuilds
        // every 250 ms until the next play/pause. Pressing play after
        // the restore still starts the ticker via onIsPlayingChanged.
        if (autoPlay) {
            host.startPositionTicker()
        }
        host.armLoadWatchdog()
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
    fun playVideoFiles(videos: List<VideoFile>, startIndex: Int = 0) {
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

    fun skipNext() {
        engine.seekToNextMediaItem()
    }

    fun skipPrevious() {
        engine.seekToPreviousMediaItem()
    }

    /** Cycles OFF -> ALL -> ONE -> OFF. The mode only changes end-of-queue
     * behaviour (see [endedAction]); it never invents tracks. */
    fun cycleRepeatMode() {
        val next = when (host.state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        host.publishRepeatMode(next)
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
        val state = host.state
        if (enabled == state.shuffleEnabled && currentQueue.isNotEmpty()) return
        val current = state.currentItem
        if (current == null || currentQueue.isEmpty()) {
            host.publishShuffleEnabled(enabled)
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
            host.publishQueueOrder(
                items = newQueue,
                currentIndex = 0,
                shuffleEnabled = true,
                hasPrevious = false,
                hasNext = newQueue.size > 1,
            )
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
                host.publishQueueOrder(
                    items = original,
                    currentIndex = index,
                    shuffleEnabled = false,
                    hasPrevious = index > 0,
                    hasNext = index < original.lastIndex,
                )
                // Issue #149: the restored pre-shuffle order is live again.
                persistQueue(index)
            } else {
                host.publishShuffleEnabled(false)
            }
        }
        host.refreshPosition()
    }

    /**
     * Writes the live queue + current index to the queue store (issue #149).
     * Called on every queue mutation and on track transitions, never from
     * the position ticker, so a write costs one small JSON encode per track
     * change, not per tick. A write failure is swallowed inside the store:
     * persistence must never break playback.
     */
    fun persistQueue(currentIndex: Int = host.state.currentIndex) {
        queueStore.save(currentQueue, currentIndex)
    }

    /**
     * Restores the last persisted queue when the live queue is empty
     * (issue #149): the process died in the background and Android restored
     * the Player destination with a fresh, empty PlaybackCoordinator. Loads the
     * queue paused at the Room-saved position (playQueue's normal resume
     * rules apply, so with resume playback off it starts at 0 instead).
     * Returns false when there is nothing to restore, so the caller keeps
     * the empty state. Deliberate stops clear the persisted queue in
     * stop(), so this never resurrects a session the user ended. Shuffle
     * order is not persisted: a restored queue always starts unshuffled.
     */
    fun restorePersistedQueueIfEmpty(): Boolean {
        if (host.state.queue.isNotEmpty()) return false
        val persisted = queueStore.load() ?: return false
        playQueue(persisted.items, persisted.currentIndex, autoPlay = false)
        return true
    }

    /**
     * Replaces the queue item at [index] with [updatedItem] (the
     * subtitle host's commit path). The state publish and the persist
     * stay with the caller, in that order — exactly as the old
     * commitSubtitleItem did.
     */
    fun replaceItem(index: Int, updatedItem: PlayableMedia) {
        val updatedQueue = currentQueue.toMutableList()
        updatedQueue[index] = updatedItem
        currentQueue = updatedQueue
    }

    /**
     * Clears the live queue for a deliberate stop (issue #258): the
     * pre-shuffle order goes, the live queue goes (otherwise a late
     * track transition resurrects the current item and re-saves the
     * queue), the generation bumps so a playQueue still awaiting the
     * Room preload is superseded like any other stale load, and the
     * persisted queue is cleared so the stop is not resurrected later.
     * Called from the coordinator's stop(); release() deliberately
     * does NOT call it.
     */
    fun clearForStop() {
        unshuffledQueue = null
        currentQueue = emptyList()
        // Issue #258: a playQueue still awaiting the Room preload (the
        // #220 slow path) holds its own captured items, so clearing
        // the queue alone cannot stop it — bump the generation so its
        // deferred startPlayback is superseded like any other stale load.
        playQueueGeneration++
        queueStore.clear()
    }

    /**
     * The pure end-of-item decision for the coordinator's STATE_ENDED
     * branch (issue #211: mpv reports STATE_ENDED at the end of EVERY
     * item — the engine holds only the current item, not a playlist,
     * so ENDED never means "the playlist is done" on its own).
     *
     * - Repeat ONE replays the item that just ended.
     * - Repeat ALL advances, wrapping to 0 only from the last item.
     * - Repeat OFF advances while there is a next item, and stops at
     *   the last one.
     * - An empty queue or no current item stops.
     *
     * The coordinator executes the decision (seek/reload/play, and
     * clearing the finished item's resume position); this function
     * only decides. The index of the item that just ended is resolved
     * from the queue itself: the tracked index when it still points
     * at the current item, otherwise by id against the live queue.
     */
    fun endedAction(
        currentItem: PlayableMedia?,
        currentIndex: Int,
        repeatMode: RepeatMode,
    ): EndedAction {
        val endedIndex = resolveEndedIndex(currentItem, currentIndex)
        return when {
            repeatMode == RepeatMode.ONE && currentItem != null -> {
                EndedAction.ReplayCurrent
            }
            repeatMode == RepeatMode.ALL && currentItem != null &&
                currentQueue.isNotEmpty() -> {
                // Advance, wrapping to 0 only from the last item.
                val next = if (endedIndex >= currentQueue.lastIndex) 0 else endedIndex + 1
                EndedAction.AdvanceTo(next)
            }
            currentItem != null && currentQueue.isNotEmpty() &&
                endedIndex < currentQueue.lastIndex -> {
                // Repeat OFF: keep going through the queue; only
                // the last item stops at EOF.
                EndedAction.AdvanceTo(endedIndex + 1)
            }
            else -> EndedAction.Stop
        }
    }

    private fun resolveEndedIndex(currentItem: PlayableMedia?, currentIndex: Int): Int {
        return if (currentQueue.isEmpty()) {
            0
        } else if (currentItem != null &&
            currentIndex in currentQueue.indices &&
            currentQueue[currentIndex].id == currentItem.id
        ) {
            currentIndex
        } else {
            currentItem?.let { item -> currentQueue.indexOfFirst { it.id == item.id } }
                ?.takeIf { it >= 0 }
                ?: currentIndex.coerceIn(0, currentQueue.lastIndex)
        }
    }

    /**
     * What the coordinator does when the engine reports the end of
     * an item, as decided by [endedAction].
     */
    sealed interface EndedAction {
        /** Replay the item that just ended (repeat ONE). */
        data object ReplayCurrent : EndedAction

        /** Advance to the item at [index] (repeat ALL wrap or next item). */
        data class AdvanceTo(val index: Int) : EndedAction

        /** Nothing follows: the queue is done, empty, or has no current item. */
        data object Stop : EndedAction
    }
}
