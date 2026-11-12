package com.mpx.player.playback

import android.view.Surface
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.PlaybackResumePosition
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.fakes.FakePlayerEngine
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration Phase 3, step 2: unit tests for the extracted
 * [PlaybackQueue]. Tested through fakes only — a fake [PlayerEngine],
 * a fake [QueueStore], a fake [AppSettingsRepository], a fake
 * [QueueHost], and a real [PlaybackResumeManager] over a fake
 * repository (or none) — no mocking framework, no Android runtime.
 *
 * What these tests pin: queue building, the shuffle/unshuffle
 * round-trip, repeat-mode cycling, next/previous selection (the
 * [PlaybackQueue.endedAction] decision), persist/restore, and the
 * deferred-load generation guard (#220/#258/#272). What they cannot
 * pin, and can only be verified on device: actual playback through
 * mpv (the fake engine answers synchronously), and the real queue
 * store against SharedPreferences (the fake store is in-memory).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueTest {

    private fun video(
        id: String,
        isVideo: Boolean = true,
        filePath: String? = null,
    ) = PlayableMedia(
        id = id,
        title = "Video $id",
        uri = "content://video/$id",
        isVideo = isVideo,
        filePath = filePath,
    )

    private fun queueOf(vararg ids: String) = ids.map { video(it) }

    /** Builds a queue whose Room preload is already complete (no repository). */
    private suspend fun createQueue(
        scope: CoroutineScope,
        engine: FakePlayerEngine = FakePlayerEngine(),
        store: FakeQueueStore = FakeQueueStore(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        host: FakeQueueHost = FakeQueueHost(),
        resumeManager: PlaybackResumeManager = PlaybackResumeManager(null, scope),
    ): PlaybackQueue {
        resumeManager.awaitPreload()
        return PlaybackQueue(
            engine = engine,
            queueStore = store,
            resumeManager = resumeManager,
            settingsRepository = settings,
            host = host,
            scope = scope,
        )
    }

    @Test
    fun `playQueue with an empty list is a no-op`() = runTest {
        val engine = FakePlayerEngine()
        val store = FakeQueueStore()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, store = store, host = host)

        queue.playQueue(emptyList())

        assertTrue(engine.loadCalls.isEmpty())
        assertEquals(0, store.saveCalls)
        assertEquals(0, host.clearAutoDetectCalls)
    }

    @Test
    fun `playQueue fast path loads at the saved resume position`() = runTest {
        val engine = FakePlayerEngine()
        val store = FakeQueueStore()
        val host = FakeQueueHost()
        val resumeManager = PlaybackResumeManager(null, this)
        resumeManager.saveResumePosition("v1", 5_000L, "content://video/v1")
        val queue = createQueue(this, engine = engine, store = store, host = host, resumeManager = resumeManager)
        val items = queueOf("v1", "v2")

        queue.playQueue(items, startIndex = 0)

        assertEquals(listOf(Triple(items, 0, 5_000L)), engine.loadCalls)
        assertEquals(listOf(1.0f), engine.speedCalls)
        assertEquals(1, engine.playCalls)
        assertEquals("v1", host.state.currentItem?.id)
        assertEquals(5_000L, host.state.resumedFromMs)
        assertTrue(host.state.isPreparing)
        assertEquals(1, host.tickerCalls)
        assertEquals(1, host.watchdogCalls)
        assertEquals(listOf("v1" to 5_000L), host.historyCalls)
        assertEquals(1, store.saveCalls)
        assertEquals(items, store.savedItems)
        assertEquals(0, store.savedIndex)
    }

    @Test
    fun `playQueue starts at zero when resume playback is off`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val settings = FakeSettingsRepository()
        settings.settings = AppSettings(resumePlayback = false)
        val resumeManager = PlaybackResumeManager(null, this)
        resumeManager.saveResumePosition("v1", 5_000L, "content://video/v1")
        val queue = createQueue(this, engine = engine, host = host, settings = settings, resumeManager = resumeManager)

        queue.playQueue(queueOf("v1", "v2"))

        assertEquals(0L, engine.loadCalls.single().third)
        assertNull(host.state.resumedFromMs)
        assertEquals(listOf("v1" to 0L), host.historyCalls)
    }

    @Test
    fun `playQueue starts at zero for a saved position under two seconds`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val resumeManager = PlaybackResumeManager(null, this)
        // 1800 ms is stored (> 1500 ms) but below the 2000 ms resume bar.
        resumeManager.saveResumePosition("v1", 1_800L, "content://video/v1")
        val queue = createQueue(this, engine = engine, host = host, resumeManager = resumeManager)

        queue.playQueue(queueOf("v1", "v2"))

        assertEquals(0L, engine.loadCalls.single().third)
        assertNull(host.state.resumedFromMs)
    }

    @Test
    fun `playQueue coerces an out-of-range start index`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        val items = queueOf("v1", "v2")

        queue.playQueue(items, startIndex = 99)

        assertEquals(1, engine.loadCalls.single().second)
        assertEquals("v2", host.state.currentItem?.id)
        assertEquals(1, host.state.currentIndex)
    }

    @Test
    fun `playQueue with autoPlay false loads paused without ticker`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)

        queue.playQueue(queueOf("v1", "v2"), autoPlay = false)

        assertEquals(1, engine.loadCalls.size)
        assertEquals(0, engine.playCalls)
        assertEquals(0, host.tickerCalls)
        assertEquals("v1", host.state.currentItem?.id)
    }

    @Test
    fun `playLiveQueueItem keeps shuffle state and speed`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val settings = FakeSettingsRepository()
        settings.settings = AppSettings(defaultPlaybackSpeed = 1.0f)
        val queue = createQueue(this, engine = engine, host = host, settings = settings)
        engine.isPlaying = true
        val items = queueOf("v1", "v2", "v3")
        queue.playQueue(items, startIndex = 0)
        queue.setShuffleEnabled(true)
        // Simulate the player running at 1.5x before the live pick.
        host.state = host.state.copy(speed = 1.5f)
        engine.speedCalls.clear()
        engine.loadCalls.clear()
        val shuffled = host.state.queue

        queue.playLiveQueueItem(2)

        assertEquals(listOf(Triple(shuffled, 2, 0L)), engine.loadCalls)
        assertEquals(listOf(1.5f), engine.speedCalls)
        assertTrue(host.state.shuffleEnabled)
        assertEquals(1.5f, host.state.speed)
        assertEquals(shuffled[2], host.state.currentItem)
    }

    @Test
    fun `playAudioQueue filters blank paths and maps tracks`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        val tracks = listOf(
            AudioTrack(
                id = "a1", title = "Song One", artist = "Singer", album = "Album",
                durationMs = 180_000L,
                path = "/music/song.mp3", contentUri = "content://media/audio/1",
            ),
            AudioTrack(
                id = "a2", title = "No Path", artist = "Singer", album = "Album",
                path = "",
            ),
            AudioTrack(
                id = "a3", title = "Song Three", artist = "Singer", album = "Album",
                durationMs = 120_000L,
                path = "/music/song3.mp3", contentUri = "",
            ),
        )

        queue.playAudioQueue(tracks, startIndex = 1)

        val loaded = engine.loadCalls.single().first
        assertEquals(listOf("a1", "a3"), loaded.map { it.id })
        assertEquals("content://media/audio/1", loaded[0].uri)
        // Blank content URI falls back to the raw path.
        assertEquals("/music/song3.mp3", loaded[1].uri)
        assertFalse(loaded[0].isVideo)
        assertEquals("Singer • Album", loaded[0].subtitle)
        assertEquals(1, engine.loadCalls.single().second)
    }

    @Test
    fun `playAudioQueue with no playable tracks is a no-op`() = runTest {
        val engine = FakePlayerEngine()
        val queue = createQueue(this, engine = engine)
        val tracks = listOf(
            AudioTrack(
                id = "a1", title = "No Path", artist = "Singer", album = "Album",
                path = "",
            ),
        )

        queue.playAudioQueue(tracks)

        assertTrue(engine.loadCalls.isEmpty())
    }

    @Test
    fun `playVideoFiles maps videos keeping the file path`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        val videos = listOf(
            VideoFile(
                id = "v1", title = "Movie", path = "/videos/movie.mp4",
                contentUri = "content://media/video/1",
            ),
            VideoFile(
                id = "v2", title = "Clip", path = "/videos/clip.mp4", contentUri = "",
            ),
        )

        queue.playVideoFiles(videos, startIndex = 1)

        val loaded = engine.loadCalls.single().first
        assertEquals(listOf("v1", "v2"), loaded.map { it.id })
        assertEquals("content://media/video/1", loaded[0].uri)
        assertEquals("/videos/clip.mp4", loaded[1].uri)
        assertEquals("/videos/movie.mp4", loaded[0].filePath)
        assertTrue(loaded.all { it.isVideo })
        assertEquals(1, engine.loadCalls.single().second)
        assertEquals("v2", host.state.currentItem?.id)
    }

    @Test
    fun `playVideoFiles with an empty list is a no-op`() = runTest {
        val engine = FakePlayerEngine()
        val queue = createQueue(this, engine = engine)

        queue.playVideoFiles(emptyList())

        assertTrue(engine.loadCalls.isEmpty())
    }

    @Test
    fun `skipNext and skipPrevious delegate to the engine`() = runTest {
        val engine = FakePlayerEngine()
        val queue = createQueue(this, engine = engine)

        queue.skipNext()
        queue.skipPrevious()

        assertEquals(1, engine.nextCalls)
        assertEquals(1, engine.previousCalls)
    }

    @Test
    fun `cycleRepeatMode cycles OFF ALL ONE OFF`() = runTest {
        val host = FakeQueueHost()
        val queue = createQueue(this, host = host)

        queue.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, host.state.repeatMode)
        queue.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, host.state.repeatMode)
        queue.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, host.state.repeatMode)
    }

    @Test
    fun `setShuffleEnabled puts the current item first and shuffles the rest`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        engine.isPlaying = true
        engine.currentPosition = 12_345L
        val items = queueOf("v1", "v2", "v3", "v4")
        queue.playQueue(items, startIndex = 1)
        engine.loadCalls.clear()
        engine.speedCalls.clear()

        queue.setShuffleEnabled(true)

        val shuffled = host.state.queue
        assertEquals(4, shuffled.size)
        assertEquals(items.toSet(), shuffled.toSet())
        assertEquals("v2", shuffled[0].id)
        assertEquals(0, host.state.currentIndex)
        assertFalse(host.state.hasPrevious)
        assertTrue(host.state.hasNext)
        assertTrue(host.state.shuffleEnabled)
        assertEquals(listOf(Triple(shuffled, 0, 12_345L)), engine.loadCalls)
        assertEquals(listOf(1.0f), engine.speedCalls)
        assertEquals(2, engine.playCalls)
    }

    @Test
    fun `setShuffleEnabled off restores the original order`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val store = FakeQueueStore()
        val queue = createQueue(this, engine = engine, host = host, store = store)
        engine.isPlaying = true
        val items = queueOf("v1", "v2", "v3", "v4")
        queue.playQueue(items, startIndex = 2)
        queue.setShuffleEnabled(true)
        val shuffledCurrent = host.state.currentItem
        assertEquals("v3", shuffledCurrent?.id)
        engine.loadCalls.clear()

        queue.setShuffleEnabled(false)

        assertEquals(items, host.state.queue)
        assertEquals(2, host.state.currentIndex)
        assertEquals("v3", host.state.currentItem?.id)
        assertFalse(host.state.shuffleEnabled)
        assertTrue(host.state.hasPrevious)
        assertTrue(host.state.hasNext)
        assertEquals(listOf(Triple(items, 2, 0L)), engine.loadCalls)
        assertEquals(items, store.savedItems)
        assertEquals(2, store.savedIndex)
    }

    @Test
    fun `setShuffleEnabled on twice is a no-op the second time`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        queue.playQueue(queueOf("v1", "v2", "v3"), startIndex = 0)
        queue.setShuffleEnabled(true)
        engine.loadCalls.clear()

        queue.setShuffleEnabled(true)

        assertTrue(engine.loadCalls.isEmpty())
    }

    @Test
    fun `setShuffleEnabled with no current item flips the flag only`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)

        queue.setShuffleEnabled(true)

        assertTrue(host.state.shuffleEnabled)
        assertTrue(engine.loadCalls.isEmpty())
        assertEquals(1, host.shuffleFlagCalls)
    }

    @Test
    fun `setShuffleEnabled off with no saved order only clears the flag`() = runTest {
        val engine = FakePlayerEngine()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, host = host)
        // Shuffle on in state, but no pre-shuffle order was ever saved
        // (e.g. state restored without going through setShuffleEnabled).
        host.state = host.state.copy(shuffleEnabled = true)

        queue.setShuffleEnabled(false)

        assertFalse(host.state.shuffleEnabled)
        assertTrue(engine.loadCalls.isEmpty())
        assertEquals(1, host.shuffleFlagCalls)
    }

    @Test
    fun `restore returns false when the state queue is not empty`() = runTest {
        val host = FakeQueueHost()
        val queue = createQueue(this, host = host)
        queue.playQueue(queueOf("v1", "v2"))

        assertFalse(queue.restorePersistedQueueIfEmpty())
    }

    @Test
    fun `restore returns false when nothing is persisted`() = runTest {
        val queue = createQueue(this)

        assertFalse(queue.restorePersistedQueueIfEmpty())
    }

    @Test
    fun `persist restore round-trip reloads the queue paused`() = runTest {
        val engine = FakePlayerEngine()
        val store = FakeQueueStore()
        val host = FakeQueueHost()
        val queue = createQueue(this, engine = engine, store = store, host = host)
        val items = queueOf("v1", "v2", "v3")
        queue.playQueue(items, startIndex = 1)
        // A fresh queue over the same store, with an empty state.
        val engine2 = FakePlayerEngine()
        val host2 = FakeQueueHost()
        val queue2 = createQueue(this, engine = engine2, store = store, host = host2)

        assertTrue(queue2.restorePersistedQueueIfEmpty())

        assertEquals(items, host2.state.queue)
        assertEquals(1, host2.state.currentIndex)
        assertEquals("v2", host2.state.currentItem?.id)
        // Restored paused: loaded, but not played and no ticker.
        assertEquals(1, engine2.loadCalls.size)
        assertEquals(0, engine2.playCalls)
        assertEquals(0, host2.tickerCalls)
        // Restoring the persisted order does not re-shuffle.
        assertFalse(host2.state.shuffleEnabled)
    }

    @Test
    fun `replaceItem swaps one queue item`() = runTest {
        val queue = createQueue(this)
        queue.playQueue(queueOf("v1", "v2"))
        val updated = video("v1").copy(title = "Renamed")

        queue.replaceItem(0, updated)

        assertEquals(listOf("Renamed", "Video v2"), queue.items.map { it.title })
    }

    @Test
    fun `endedAction advances in OFF until the last item`() = runTest {
        val queue = createQueue(this)
        val items = queueOf("v1", "v2", "v3")
        queue.playQueue(items, startIndex = 0)

        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(1), queue.endedAction(video("v1"), 0, RepeatMode.OFF))
        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(2), queue.endedAction(video("v2"), 1, RepeatMode.OFF))
        assertEquals(PlaybackQueue.EndedAction.Stop, queue.endedAction(video("v3"), 2, RepeatMode.OFF))
    }

    @Test
    fun `endedAction wraps in ALL and replays in ONE`() = runTest {
        val queue = createQueue(this)
        val items = queueOf("v1", "v2", "v3")
        queue.playQueue(items, startIndex = 0)

        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(2), queue.endedAction(video("v2"), 1, RepeatMode.ALL))
        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(0), queue.endedAction(video("v3"), 2, RepeatMode.ALL))
        assertEquals(PlaybackQueue.EndedAction.ReplayCurrent, queue.endedAction(video("v3"), 2, RepeatMode.ONE))
        assertEquals(PlaybackQueue.EndedAction.ReplayCurrent, queue.endedAction(video("v1"), 0, RepeatMode.ONE))
    }

    @Test
    fun `endedAction stops with an empty queue or no current item`() = runTest {
        val queue = createQueue(this)

        assertEquals(PlaybackQueue.EndedAction.Stop, queue.endedAction(null, 0, RepeatMode.OFF))
        assertEquals(PlaybackQueue.EndedAction.Stop, queue.endedAction(null, 0, RepeatMode.ALL))
        assertEquals(PlaybackQueue.EndedAction.Stop, queue.endedAction(null, 0, RepeatMode.ONE))

        queue.playQueue(queueOf("v1", "v2"))
        assertEquals(PlaybackQueue.EndedAction.Stop, queue.endedAction(null, 0, RepeatMode.OFF))
    }

    @Test
    fun `endedAction resolves a stale index by id`() = runTest {
        val queue = createQueue(this)
        queue.playQueue(queueOf("v1", "v2", "v3"), startIndex = 0)

        // The tracked index is stale (points at v1) but the item that
        // ended is v2: the decision follows the item, not the index.
        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(2), queue.endedAction(video("v2"), 0, RepeatMode.OFF))
        assertEquals(PlaybackQueue.EndedAction.AdvanceTo(0), queue.endedAction(video("v3"), 0, RepeatMode.ALL))
    }

    @Test
    fun `deferred load is superseded by a newer playQueue`() = runTest {
        val engine = FakePlayerEngine()
        val store = FakeQueueStore()
        val host = FakeQueueHost()
        val repo = BlockingResumeRepository()
        val resumeManager = PlaybackResumeManager(repo, this)
        val queue = PlaybackQueue(
            engine = engine,
            queueStore = store,
            resumeManager = resumeManager,
            settingsRepository = FakeSettingsRepository(),
            host = host,
            scope = this,
        )
        val itemsA = queueOf("a1", "a2")
        val itemsB = queueOf("b1", "b2")

        queue.playQueue(itemsA, startIndex = 0)
        queue.playQueue(itemsB, startIndex = 1)
        // The preload is still pending: nothing has loaded yet.
        assertTrue(engine.loadCalls.isEmpty())

        repo.completePreload(emptyMap())
        advanceUntilIdle()

        // Only the second session started, at its own index.
        assertEquals(listOf(Triple(itemsB, 1, 0L)), engine.loadCalls)
        assertEquals(1, engine.playCalls)
        assertEquals("b2", host.state.currentItem?.id)
    }

    @Test
    fun `clearForStop supersedes a deferred load and clears the store`() = runTest {
        val engine = FakePlayerEngine()
        val store = FakeQueueStore()
        val host = FakeQueueHost()
        val repo = BlockingResumeRepository()
        val resumeManager = PlaybackResumeManager(repo, this)
        val queue = PlaybackQueue(
            engine = engine,
            queueStore = store,
            resumeManager = resumeManager,
            settingsRepository = FakeSettingsRepository(),
            host = host,
            scope = this,
        )
        queue.playQueue(queueOf("a1", "a2"), startIndex = 0)
        assertEquals(1, store.saveCalls)

        queue.clearForStop()
        assertTrue(queue.items.isEmpty())
        assertNull(store.load())

        repo.completePreload(emptyMap())
        advanceUntilIdle()

        // The deferred load never fired after the stop.
        assertTrue(engine.loadCalls.isEmpty())
    }

    @Test
    fun `playbackQueue writes directly to PlaybackStateStore without proxy`() = runTest {
        val store = PlaybackStateStore()
        val dispatchedEvents = mutableListOf<PlaybackEvent>()
        store.setEventListener { dispatchedEvents.add(it) }

        val engine = FakePlayerEngine()
        val resumeManager = PlaybackResumeManager(null, this)
        resumeManager.awaitPreload()

        val queue = PlaybackQueue(
            engine = engine,
            queueStore = FakeQueueStore(),
            resumeManager = resumeManager,
            settingsRepository = FakeSettingsRepository(),
            host = store,
            scope = this,
        )

        val items = queueOf("q1", "q2")
        queue.playQueue(items, startIndex = 0)

        assertEquals("q1", store.state.currentItem?.id)
        assertEquals(items, store.state.queue)
        assertEquals(0, store.state.currentIndex)
        assertTrue(store.state.isPreparing)
        assertTrue(dispatchedEvents.contains(PlaybackEvent.StartPositionTicker))
        assertTrue(dispatchedEvents.contains(PlaybackEvent.ArmLoadWatchdog))
    }


    /**
     * Fake queue store: in-memory, mirroring the real store's
     * "empty means nothing persisted" rule.
     */
    private class FakeQueueStore : QueueStore {
        var saveCalls: Int = 0
        var savedItems: List<PlayableMedia> = emptyList()
        var savedIndex: Int = 0
        private var stored: PersistedQueue? = null

        override fun save(items: List<PlayableMedia>, currentIndex: Int) {
            saveCalls++
            savedItems = items
            savedIndex = currentIndex
            stored = if (items.isEmpty()) null else PersistedQueue(items, currentIndex)
        }

        override fun load(): PersistedQueue? = stored

        override fun clear() {
            stored = null
            savedItems = emptyList()
            savedIndex = 0
        }
    }


    /**
     * Fake host: mirrors the real coordinator's publishes (the session
     * and queue-order copies apply exactly the fields the manager
     * applies) and records history, ticker, and watchdog effects.
     */
    private class FakeQueueHost : QueueHost {
        override var state: PlaybackState = PlaybackState()
        val historyCalls = mutableListOf<Pair<String, Long>>()
        var clearAutoDetectCalls: Int = 0
        var refreshCalls: Int = 0
        var tickerCalls: Int = 0
        var watchdogCalls: Int = 0
        var shuffleFlagCalls: Int = 0
        var repeatCalls: Int = 0

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
            state = state.copy(
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

        override fun publishQueueOrder(
            items: List<PlayableMedia>,
            currentIndex: Int,
            shuffleEnabled: Boolean,
            hasPrevious: Boolean,
            hasNext: Boolean,
        ) {
            state = state.copy(
                queue = items,
                currentIndex = currentIndex,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                shuffleEnabled = shuffleEnabled,
            )
        }

        override fun publishShuffleEnabled(enabled: Boolean) {
            shuffleFlagCalls++
            state = state.copy(shuffleEnabled = enabled)
        }

        override fun publishRepeatMode(mode: RepeatMode) {
            repeatCalls++
            state = state.copy(repeatMode = mode)
        }

        override fun recordHistory(item: PlayableMedia, positionMs: Long) {
            historyCalls.add(item.id to positionMs)
        }

        override fun clearAutoDetectAttempts() {
            clearAutoDetectCalls++
        }

        override fun refreshPositionAndTracks() {
            refreshCalls++
        }

        override fun refreshPosition() {
            refreshCalls++
        }

        override fun startPositionTicker() {
            tickerCalls++
        }

        override fun armLoadWatchdog() {
            watchdogCalls++
        }
    }

    /**
     * Fake persistence repository whose resume preload never lands
     * until [completePreload] is called. Every other method is an
     * empty default — the queue only ever reads resume positions.
     */
    private class BlockingResumeRepository : MediaPersistenceRepository {
        private val preload = CompletableDeferred<Map<String, Long>>()

        fun completePreload(positions: Map<String, Long>) {
            preload.complete(positions)
        }

        override fun observeFavourites(): Flow<List<FavouriteItem>> = flowOf(emptyList())
        override suspend fun getFavourites(): List<FavouriteItem> = emptyList()
        override fun observeIsFavourite(mediaUri: String): Flow<Boolean> = flowOf(false)
        override suspend fun isFavourite(mediaUri: String): Boolean = false
        override suspend fun addFavourite(item: FavouriteItem) = Unit
        override suspend fun removeFavourite(mediaUri: String) = Unit
        override suspend fun toggleFavourite(item: FavouriteItem): Boolean = false
        override suspend fun clearFavourites() = Unit
        override fun observeHistory(limit: Int, offset: Int): Flow<List<PlaybackHistoryItem>> = flowOf(emptyList())
        override suspend fun getHistory(limit: Int, offset: Int): List<PlaybackHistoryItem> = emptyList()
        override fun observeHistoryCount(): Flow<Int> = flowOf(0)
        override suspend fun recordHistory(item: PlaybackHistoryItem) = Unit
        override suspend fun deleteHistoryItem(mediaUri: String) = Unit
        override suspend fun clearHistory() = Unit
        override fun observePlaylists(): Flow<List<CustomPlaylist>> = flowOf(emptyList())
        override suspend fun getPlaylists(): List<CustomPlaylist> = emptyList()
        override suspend fun getPlaylistById(id: Long): CustomPlaylist? = null
        override suspend fun createPlaylist(name: String, isVideoPlaylist: Boolean): Long = 0L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) = Unit
        override suspend fun deletePlaylist(playlistId: Long) = Unit
        override suspend fun clearPlaylists() = Unit
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = flowOf(emptyList())
        override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem> = emptyList()
        override suspend fun addItemToPlaylist(item: PlaylistItem): Long = 0L
        override suspend fun removeItemFromPlaylist(playlistItemId: Long) = Unit
        override suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) = Unit
        override suspend fun clearPlaylistItems(playlistId: Long) = Unit
        override fun observeResumePositions(): Flow<List<PlaybackResumePosition>> = flowOf(emptyList())
        override suspend fun getAllResumePositions(): List<PlaybackResumePosition> = emptyList()
        override suspend fun getResumePosition(mediaId: String): Long? = null
        override suspend fun saveResumePosition(mediaId: String, mediaUri: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clearResumePosition(mediaId: String) = Unit
        override suspend fun clearAllResumePositions() = Unit
        override suspend fun loadAllResumePositionsMap(): Map<String, Long> = preload.await()
    }
}
