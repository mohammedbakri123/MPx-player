package com.mpx.player.playback

import android.view.Surface
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.playback.engine.TrackType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Migration Phase 3, step 1: the first unit tests for the extracted
 * [SubtitleManager]. Tested through fakes only — a fake [PlayerEngine]
 * and a fake [SubtitleHost] — no mocking framework, no Android runtime.
 * The manager works in plain `String` URIs and takes its
 * Android-dependent seams ([SubtitleManager.FileAccess]) through its
 * constructor, so none of this needs a device.
 *
 * What these tests pin: pending-state handling, the auto-detect
 * guards, and selection/confirmation delegation. What they cannot
 * pin, and can only be verified on device: the actual mpv sub-add
 * (the fake engine answers synchronously), the real folder scan
 * against device storage, and codepage detection against real files.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleManagerTest {

    private lateinit var engine: FakePlayerEngine
    private lateinit var host: FakeSubtitleHost
    private lateinit var siblingCalls: MutableList<String>
    private var allFilesAccess: Boolean = true

    @Before
    fun setUp() {
        engine = FakePlayerEngine()
        host = FakeSubtitleHost()
        siblingCalls = mutableListOf()
        allFilesAccess = true
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fileAccess(
        detectCodepage: (String) -> String? = { null },
        findSiblings: (String) -> List<File> = { emptyList() },
        validateSibling: (File) -> Triple<String, String, String>? = { null },
    ) = SubtitleManager.FileAccess(
        detectCodepage = detectCodepage,
        hasAllFilesAccess = { allFilesAccess },
        findSiblings = { path ->
            siblingCalls.add(path)
            findSiblings(path)
        },
        validateSibling = validateSibling,
    )

    private fun createManager(
        scope: CoroutineScope,
        access: SubtitleManager.FileAccess = fileAccess(),
    ) = SubtitleManager(
        engine = engine,
        host = host,
        scope = scope,
        fileAccess = access,
        // Unconfined: the auto-detect scan and the hop back to Main run
        // eagerly inside maybeAutoDetectSubtitles(), so the flow is
        // observable without advancing a scheduler.
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun video(
        id: String = "v1",
        filePath: String? = null,
        isVideo: Boolean = true,
        externalSubtitles: List<ExternalSubtitle> = emptyList(),
    ) = PlayableMedia(
        id = id,
        title = "Video $id",
        uri = "content://video/$id",
        isVideo = isVideo,
        filePath = filePath,
        externalSubtitles = externalSubtitles,
    )

    /** Puts [item] in as the current (and only) queue item. */
    private fun play(item: PlayableMedia) {
        host.currentItem = item
        host.queue = listOf(item)
    }

    @Test
    fun `loadExternalSubtitle returns false with no current item`() = runTest {
        val manager = createManager(this)

        assertFalse(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))
        assertTrue(engine.replaceCalls.isEmpty())
    }

    @Test
    fun `loadExternalSubtitle returns false when the engine index is out of range`() = runTest {
        val manager = createManager(this)
        play(video())
        engine.currentMediaItemIndex = 5

        assertFalse(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))
        assertTrue(engine.replaceCalls.isEmpty())
    }

    @Test
    fun `accepted subtitle sets pending state and commits the updated item`() = runTest {
        val manager = createManager(
            this,
            fileAccess(detectCodepage = { "cp1256" }),
        )
        play(video())
        engine.isPlaying = true

        assertTrue(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))

        // One commit, carrying the new subtitle with its detected codepage.
        assertEquals(1, host.commits.size)
        val committed = host.commits.single().second
        assertEquals(1, committed.externalSubtitles.size)
        assertEquals("file:///sub.srt", committed.externalSubtitles.single().uri)
        assertEquals("cp1256", committed.externalSubtitles.single().codepage)
        // Accepted, not yet confirmed: no banner yet, and playback was restored.
        assertTrue(host.notifications.isEmpty())
        assertEquals(1, engine.playCalls)
        // The pending pick is armed — the confirmation below proves it.
        manager.onExternalSubtitleConfirmed("v1", success = true)
        assertEquals(listOf("Loaded subtitle: sub.srt"), host.notifications)
    }

    @Test
    fun `re-picking the same uri replaces the existing entry`() = runTest {
        val manager = createManager(this)
        val existing = ExternalSubtitle("file:///sub.srt", "old.srt", "application/x-subrip")
        play(video(externalSubtitles = listOf(existing)))

        assertTrue(manager.loadExternalSubtitle("file:///sub.srt", "new.srt", "application/x-subrip"))

        val committed = host.commits.single().second
        assertEquals(1, committed.externalSubtitles.size)
        assertEquals("new.srt", committed.externalSubtitles.single().name)
    }

    @Test
    fun `already-added subtitle with enough loaded tracks reports loaded`() = runTest {
        val manager = createManager(this)
        play(video())
        engine.replaceResult = ReplaceMediaItemResult.ALREADY_ADDED
        engine.subtitleTracks = listOf(
            SubtitleTrackInfo(0, 0, "sub", "en", isSelected = true, isExternal = true),
        )

        assertTrue(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))

        assertEquals("Loaded subtitle: sub.srt", host.commits.single().third)
        assertTrue(host.notifications.isEmpty())
    }

    @Test
    fun `already-added subtitle with too few loaded tracks reports failure`() = runTest {
        val manager = createManager(this)
        play(video())
        engine.replaceResult = ReplaceMediaItemResult.ALREADY_ADDED
        engine.subtitleTracks = emptyList()

        assertFalse(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))

        assertEquals(listOf("Failed to load subtitle: sub.srt"), host.notifications)
    }

    @Test
    fun `failed add re-syncs the engine item, restores playback and reports failure`() = runTest {
        val manager = createManager(this)
        val original = video()
        play(original)
        engine.isPlaying = true
        engine.replaceResult = ReplaceMediaItemResult.FAILED

        assertFalse(manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip"))

        // First the add attempt, then the re-sync with the original item.
        assertEquals(2, engine.replaceCalls.size)
        assertEquals(original, engine.replaceCalls[1].second)
        assertEquals(1, engine.playCalls)
        assertEquals(listOf("Failed to load subtitle: sub.srt"), host.notifications)
    }

    @Test
    fun `confirmation for a different media id is ignored`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")

        manager.onExternalSubtitleConfirmed("other", success = true)

        assertTrue(host.notifications.isEmpty())
        // The pending pick is still armed for the real id.
        manager.onExternalSubtitleConfirmed("v1", success = true)
        assertEquals(listOf("Loaded subtitle: sub.srt"), host.notifications)
    }

    @Test
    fun `confirmation is ignored when the current item has moved on`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")
        host.currentItem = video("v2")

        manager.onExternalSubtitleConfirmed("v1", success = true)

        assertTrue(host.notifications.isEmpty())
    }

    @Test
    fun `failed confirmation shows the failure banner`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")

        manager.onExternalSubtitleConfirmed("v1", success = false)

        assertEquals(listOf("Failed to load subtitle: sub.srt"), host.notifications)
        // A spent confirmation is not repeated.
        manager.onExternalSubtitleConfirmed("v1", success = true)
        assertEquals(1, host.notifications.size)
    }

    @Test
    fun `manual track selection clears the pending pick and refreshes track state`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")

        manager.selectSubtitleTrack(0, 1)

        assertEquals(listOf(0 to 1), engine.selectSubtitleCalls)
        assertEquals(1, host.refreshCalls)
        // The pending pick is gone: the confirmation is now ignored.
        manager.onExternalSubtitleConfirmed("v1", success = true)
        assertTrue(host.notifications.isEmpty())
    }

    @Test
    fun `tracks-changed re-selects the newest external track for the pending item`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")
        host.tracks = listOf(
            SubtitleTrackInfo(0, 0, "embedded", "en", isSelected = false),
            SubtitleTrackInfo(0, 1, "srt", "en", isSelected = false, isExternal = true),
            SubtitleTrackInfo(0, 2, "srt", "en", isSelected = false, isExternal = true),
        )

        manager.onTracksChanged()

        assertEquals(listOf(0 to 2), engine.selectSubtitleCalls)
    }

    @Test
    fun `tracks-changed clears a pending pick for a different video`() = runTest {
        val manager = createManager(this)
        play(video())
        manager.loadExternalSubtitle("file:///sub.srt", "sub.srt", "application/x-subrip")
        host.currentItem = video("v2")

        manager.onTracksChanged()

        assertTrue(engine.selectSubtitleCalls.isEmpty())
        manager.onExternalSubtitleConfirmed("v1", success = true)
        assertTrue(host.notifications.isEmpty())
    }

    @Test
    fun `auto-detect skips audio items, items without a path, and items with subtitles`() = runTest {
        val manager = createManager(this)

        play(video(isVideo = false, filePath = "/videos/movie.mp4"))
        manager.maybeAutoDetectSubtitles()
        play(video(filePath = null))
        manager.maybeAutoDetectSubtitles()
        play(video(filePath = "/videos/movie.mp4", externalSubtitles = listOf(ExternalSubtitle("file:///x.srt", "x.srt", "application/x-subrip"))))
        manager.maybeAutoDetectSubtitles()

        assertTrue(siblingCalls.isEmpty())
    }

    @Test
    fun `auto-detect loads siblings worst-first and scans once per item`() = runTest {
        val best = File("/videos/movie.srt")
        val worst = File("/videos/movie.en.srt")
        val manager = createManager(
            this,
            fileAccess(
                findSiblings = { listOf(best, worst) },
                validateSibling = { file ->
                    Triple("file://${file.path}", file.name, "application/x-subrip")
                },
            ),
        )
        play(video(filePath = "/videos/movie.mp4"))

        manager.maybeAutoDetectSubtitles()
        manager.maybeAutoDetectSubtitles()

        assertEquals(listOf("/videos/movie.mp4"), siblingCalls)
        // Worst first, so the best match is added last and ends up selected.
        assertEquals(2, engine.replaceCalls.size)
        assertEquals(listOf("movie.en.srt"), engine.replaceCalls[0].second.externalSubtitles.map { it.name })
        assertEquals(listOf("movie.en.srt", "movie.srt"), engine.replaceCalls[1].second.externalSubtitles.map { it.name })
        assertEquals(2, host.currentItem!!.externalSubtitles.size)
    }

    @Test
    fun `auto-detect does nothing without all-files access and retries after the grant`() = runTest {
        val manager = createManager(this)
        play(video(filePath = "/videos/movie.mp4"))
        allFilesAccess = false

        manager.maybeAutoDetectSubtitles()
        assertTrue(siblingCalls.isEmpty())

        // The id was NOT marked attempted, so granting access later still scans.
        allFilesAccess = true
        manager.maybeAutoDetectSubtitles()
        assertEquals(listOf("/videos/movie.mp4"), siblingCalls)
    }

    @Test
    fun `clearAutoDetectAttempts allows a re-scan`() = runTest {
        val manager = createManager(this)
        play(video(filePath = "/videos/movie.mp4"))

        manager.maybeAutoDetectSubtitles()
        manager.maybeAutoDetectSubtitles()
        assertEquals(1, siblingCalls.size)

        manager.clearAutoDetectAttempts()
        manager.maybeAutoDetectSubtitles()
        assertEquals(2, siblingCalls.size)
    }

    @Test
    fun `auto-detect ignores a sibling that fails validation`() = runTest {
        val good = File("/videos/movie.srt")
        val bad = File("/videos/movie.en.srt")
        val manager = createManager(
            this,
            fileAccess(
                findSiblings = { listOf(good, bad) },
                validateSibling = { file ->
                    if (file == good) Triple("file://${file.path}", file.name, "application/x-subrip") else null
                },
            ),
        )
        play(video(filePath = "/videos/movie.mp4"))

        manager.maybeAutoDetectSubtitles()

        assertEquals(1, engine.replaceCalls.size)
        assertEquals(listOf("movie.srt"), engine.replaceCalls.single().second.externalSubtitles.map { it.name })
    }

    /**
     * Fake engine: answers synchronously from [replaceResult] and
     * [subtitleTracks], records every call the manager makes. A
     * replace pauses playback in this fake (isPlaying = false), which
     * is what makes the manager's restore-playback branch observable;
     * [play] resumes it.
     */
    private class FakePlayerEngine : PlayerEngine {
        var replaceResult: ReplaceMediaItemResult = ReplaceMediaItemResult.ISSUED
        val replaceCalls = mutableListOf<Pair<Int, PlayableMedia>>()
        val selectSubtitleCalls = mutableListOf<Pair<Int?, Int?>>()
        var subtitleTracks: List<SubtitleTrackInfo> = emptyList()
        var currentMediaItemIndex: Int = 0
        var playCalls: Int = 0
        var listener: PlayerEngine.Listener? = null

        override val currentPosition: Long = 0L
        override val duration: Long = 0L
        override val bufferedPosition: Long = 0L
        override var isPlaying: Boolean = false
        override val playbackState: Int = PlayerEngine.STATE_IDLE
        override val hasNextMediaItem: Boolean = false
        override val hasPreviousMediaItem: Boolean = false

        override fun attachSurface(surface: Surface) = Unit
        override fun detachSurface() = Unit
        override fun load(media: PlayableMedia, resumeMs: Long) = Unit
        override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) = Unit
        override fun play() {
            playCalls++
            isPlaying = true
        }
        override fun pause() {
            isPlaying = false
        }
        override fun seekTo(positionMs: Long) = Unit
        override fun seekTo(index: Int, positionMs: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun selectTrack(type: TrackType, id: String?) = Unit
        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) = Unit
        override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
            selectSubtitleCalls.add(groupIndex to trackIndex)
        }
        override fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult {
            replaceCalls.add(index to media)
            isPlaying = false
            return replaceResult
        }
        override fun stop() = Unit
        override fun release() = Unit
        override fun setDecoderMode(mode: DecoderMode) = Unit
        override fun setListener(listener: PlayerEngine.Listener?) {
            this.listener = listener
        }
        override fun seekToNextMediaItem() = Unit
        override fun seekToPreviousMediaItem() = Unit
        override fun getAudioTracks(): List<AudioTrackInfo> = emptyList()
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracks
    }

    /**
     * Fake host: mirrors the real coordinator's commit (the queue item
     * and the current item are replaced, the notification rides along)
     * and records notifications and track refreshes.
     */
    private class FakeSubtitleHost : SubtitleHost {
        var currentItem: PlayableMedia? = null
        var queue: List<PlayableMedia> = emptyList()
        var tracks: List<SubtitleTrackInfo> = emptyList()
        val commits = mutableListOf<Triple<Int, PlayableMedia, String?>>()
        val notifications = mutableListOf<String>()
        var refreshCalls: Int = 0

        override val liveQueue: List<PlayableMedia>
            get() = queue
        override val subtitleTracks: List<SubtitleTrackInfo>
            get() = tracks

        override fun commitSubtitleItem(index: Int, updatedItem: PlayableMedia, notification: String?) {
            commits.add(Triple(index, updatedItem, notification))
            queue = queue.toMutableList().also { it[index] = updatedItem }
            currentItem = updatedItem
        }

        override fun subtitleLoadedMessage(displayName: String): String =
            "Loaded subtitle: $displayName"

        override fun subtitleFailedMessage(displayName: String): String =
            "Failed to load subtitle: $displayName"

        override fun showNotification(message: String) {
            notifications.add(message)
        }

        override fun refreshTracksState() {
            refreshCalls++
        }
    }
}
