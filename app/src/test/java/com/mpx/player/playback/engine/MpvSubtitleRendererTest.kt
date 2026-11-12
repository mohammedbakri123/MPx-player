package com.mpx.player.playback.engine

import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.ExternalSubtitle
import com.mpx.player.playback.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Migration Phase 4, step 3: unit tests for the extracted
 * [MpvSubtitleRenderer].
 *
 * The renderer itself is not unit-testable: every function touches mpv
 * directly (same as [MpvCoreEngine] and [MpvTrackSelection]), so it is
 * verified by the CI build and on device only. The two exceptions are
 * the pure helpers in its companion object, kept there precisely so
 * they can be tested without mpv. These tests pin them: a wrong answer
 * in [MpvSubtitleRenderer.isUsableSubtitleUri] sub-adds a file mpv can
 * never open (issue #332) or skips one it could, and a wrong answer in
 * [MpvSubtitleRenderer.isSameExternalSubtitle] re-adds an already-added
 * subtitle as a duplicate track (issue #197).
 */
class MpvSubtitleRendererTest {

    private fun sub(uri: String, name: String = "English.srt") =
        ExternalSubtitle(uri = uri, name = name, mimeType = "text/plain")

    // --- isUsableSubtitleUri ---

    @Test
    fun `fd uri is usable`() {
        assertTrue(MpvSubtitleRenderer.isUsableSubtitleUri("fd://12"))
    }

    @Test
    fun `unresolved content uri is not usable`() {
        assertFalse(MpvSubtitleRenderer.isUsableSubtitleUri("content://com.example/tree/1.srt"))
    }

    @Test
    fun `http and https uris are usable`() {
        assertTrue(MpvSubtitleRenderer.isUsableSubtitleUri("http://example.com/a.srt"))
        assertTrue(MpvSubtitleRenderer.isUsableSubtitleUri("https://example.com/a.srt"))
    }

    @Test
    fun `existing file path is usable, missing path is not`() {
        val existing = File.createTempFile("mpx-sub-test", ".srt")
        try {
            assertTrue(MpvSubtitleRenderer.isUsableSubtitleUri(existing.absolutePath))
            assertTrue(MpvSubtitleRenderer.isUsableSubtitleUri("file://${existing.absolutePath}"))
        } finally {
            existing.delete()
        }
        assertFalse(MpvSubtitleRenderer.isUsableSubtitleUri("/definitely/not/a/real/file.srt"))
        assertFalse(MpvSubtitleRenderer.isUsableSubtitleUri("file:///definitely/not/a/real/file.srt"))
    }

    // --- isSameExternalSubtitle ---

    @Test
    fun `same name and same uri are the same subtitle`() {
        assertTrue(MpvSubtitleRenderer.isSameExternalSubtitle(sub("/a/en.srt"), sub("/a/en.srt")))
    }

    @Test
    fun `same name with either side an fd uri is the same subtitle`() {
        // A content:// subtitle resolves to a fresh fd://<n> on every
        // call, so the URI can never match across calls for it.
        assertTrue(MpvSubtitleRenderer.isSameExternalSubtitle(sub("fd://12"), sub("content://x/en.srt")))
        assertTrue(MpvSubtitleRenderer.isSameExternalSubtitle(sub("content://x/en.srt"), sub("fd://12")))
        assertTrue(MpvSubtitleRenderer.isSameExternalSubtitle(sub("fd://12"), sub("fd://13")))
    }

    @Test
    fun `same name but different file paths are different subtitles`() {
        assertFalse(MpvSubtitleRenderer.isSameExternalSubtitle(sub("/a/en.srt"), sub("/b/en.srt")))
    }

    @Test
    fun `different names are different subtitles`() {
        assertFalse(MpvSubtitleRenderer.isSameExternalSubtitle(sub("/a/en.srt"), sub("/a/en.srt", "Arabic.srt")))
    }

    @Test
    fun `same name but different content uris are different subtitles`() {
        assertFalse(
            MpvSubtitleRenderer.isSameExternalSubtitle(
                sub("content://x/en.srt"),
                sub("content://y/en.srt"),
            ),
        )
    }

    // --- SubtitleRenderer track operations and sub-add dispatch ---

    private lateinit var testContext: TestContext
    private lateinit var fakePort: FakeMpvPort
    private lateinit var directHandler: DirectEngineHandler
    private lateinit var fakeCoreHost: TestCoreHost
    private lateinit var fakeTrackHost: TestTrackHost
    private lateinit var fakeListener: TestRendererListener
    private lateinit var core: MpvCoreEngine
    private lateinit var trackSelection: MpvTrackSelection
    private lateinit var renderer: MpvSubtitleRenderer

    private class TestTrackHost : MpvTrackSelection.Host {
        var clearRememberedExternalPickCalls: Int = 0
        override fun clearRememberedExternalPick() {
            clearRememberedExternalPickCalls++
        }
    }

    private class TestCoreHost : MpvCoreEngine.Host {
        var queue = mutableListOf<PlayableMedia>()
        var mediaIndex: Int = 0

        override fun hwdecValueFor(mode: DecoderMode): String = "mediacodec"
        override fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = "mediacodec"
        override fun applyHwdecForMode(mode: DecoderMode) {}
        override fun applyPostInitSettings(core: MpvCoreEngine) {}
        override fun onLoadStarted() {}
        override fun onLoadTargetResolved(mediaId: String) {}
        override fun onTrackListChanged() {}
        override fun onFileLoaded() {}
        override fun getCurrentQueue(): List<PlayableMedia> = queue
        override fun getCurrentMediaIndex(): Int = mediaIndex
        override fun loadSettings(): AppSettings = AppSettings()
    }

    private class TestRendererListener : PlayerEngine.Listener {
        var tracksChangedCalls: Int = 0
        val externalSubtitleConfirmations = mutableListOf<Pair<String, Boolean>>()

        override fun onTracksChanged() {
            tracksChangedCalls++
        }

        override fun onExternalSubtitleConfirmed(mediaId: String, success: Boolean) {
            externalSubtitleConfirmations.add(mediaId to success)
        }
    }

    @Before
    fun setUp() {
        testContext = TestContext()
        fakePort = FakeMpvPort()
        directHandler = DirectEngineHandler()
        fakeCoreHost = TestCoreHost()
        fakeTrackHost = TestTrackHost()
        fakeListener = TestRendererListener()
        core = MpvCoreEngine(
            context = testContext,
            host = fakeCoreHost,
            port = fakePort,
            mainHandler = directHandler,
        )
        core.setListener(fakeListener)
        trackSelection = MpvTrackSelection(core, fakeTrackHost)
        renderer = MpvSubtitleRenderer(core, trackSelection)
    }

    @Test
    fun `onFileLoaded dispatches usable pending subtitles and applies codepage`() {
        core.activeMediaUri = "http://example.com/video.mp4"
        core.activeMediaId = "m1"
        val pendingSubs = listOf(
            PendingSubtitle(
                uri = "http://example.com/en.srt",
                flag = "select",
                title = "English",
                codepage = "cp1256",
            ),
            PendingSubtitle(
                uri = "http://example.com/ar.srt",
                flag = "select",
                title = null,
            ),
        )
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/video.mp4",
            mediaId = "m1",
            subtitles = pendingSubs,
        )

        renderer.onFileLoaded()

        assertNull(core.pendingSubtitles)
        assertEquals(
            listOf("sub-codepage" to "cp1256", "sub-codepage" to "auto"),
            fakePort.propertyHistory.filter { it.first == "sub-codepage" },
        )
        assertEquals("auto", fakePort.recordedProperties["sub-codepage"])
        assertTrue(
            fakePort.recordedCommands.contains(
                listOf("sub-add", "http://example.com/en.srt", "select", "English"),
            ),
        )
        assertTrue(
            fakePort.recordedCommands.contains(
                listOf("sub-add", "http://example.com/ar.srt", "select"),
            ),
        )
    }

    @Test
    fun `onFileLoaded skips unusable subtitle URIs`() {
        core.activeMediaUri = "http://example.com/video.mp4"
        core.activeMediaId = "m1"
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/video.mp4",
            mediaId = "m1",
            subtitles = listOf(
                PendingSubtitle(
                    uri = "content://unresolved/sub.srt",
                    flag = "select",
                    title = "Unresolved",
                ),
            ),
        )

        renderer.onFileLoaded()

        assertNull(core.pendingSubtitles)
        assertTrue(fakePort.recordedCommands.none { it.firstOrNull() == "sub-add" })
    }

    @Test
    fun `onFileLoaded drops stale pending subtitles when mediaUri does not match`() {
        core.activeMediaUri = "http://example.com/active.mp4"
        core.activeMediaId = "active-media"
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/stale.mp4",
            mediaId = "stale-media",
            subtitles = listOf(
                PendingSubtitle(
                    uri = "http://example.com/sub.srt",
                    flag = "select",
                    title = "Sub",
                ),
            ),
        )

        renderer.onFileLoaded()

        assertNull(core.pendingSubtitles)
        assertTrue(fakePort.recordedCommands.none { it.firstOrNull() == "sub-add" })
    }

    @Test
    fun `onFileLoaded auto-selects default embedded subtitle when none selected`() {
        core.pendingSubtitles = null
        fakePort.recordedProperties["track-list/count"] = 2

        // Embedded non-default
        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/external"] = false
        fakePort.recordedProperties["track-list/0/default"] = false
        fakePort.recordedProperties["track-list/0/selected"] = false

        // Embedded default
        fakePort.recordedProperties["track-list/1/type"] = "sub"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/external"] = false
        fakePort.recordedProperties["track-list/1/default"] = true
        fakePort.recordedProperties["track-list/1/selected"] = false

        renderer.onFileLoaded()

        assertEquals("2", fakePort.recordedProperties["sid"])
    }

    @Test
    fun `onFileLoaded auto-selects first embedded subtitle when no default exists`() {
        core.pendingSubtitles = null
        fakePort.recordedProperties["track-list/count"] = 2

        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/external"] = false
        fakePort.recordedProperties["track-list/0/default"] = false
        fakePort.recordedProperties["track-list/0/selected"] = false

        fakePort.recordedProperties["track-list/1/type"] = "sub"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/external"] = false
        fakePort.recordedProperties["track-list/1/default"] = false
        fakePort.recordedProperties["track-list/1/selected"] = false

        renderer.onFileLoaded()

        assertEquals("1", fakePort.recordedProperties["sid"])
    }

    @Test
    fun `onFileLoaded does not auto-select if a subtitle is already selected`() {
        core.pendingSubtitles = null
        fakePort.recordedProperties["track-list/count"] = 1

        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/external"] = false
        fakePort.recordedProperties["track-list/0/default"] = true
        fakePort.recordedProperties["track-list/0/selected"] = true

        fakePort.recordedProperties.remove("sid")
        renderer.onFileLoaded()

        assertNull(fakePort.recordedProperties["sid"])
    }

    @Test
    fun `onTrackListChanged reapplies remembered external pick and confirms addition`() {
        core.activeMediaUri = "http://example.com/video.mp4"
        core.activeMediaId = "m1"
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/video.mp4",
            mediaId = "m1",
            subtitles = listOf(
                PendingSubtitle(
                    uri = "http://example.com/sub.srt",
                    flag = "select",
                    title = "English.srt",
                ),
            ),
        )
        renderer.onFileLoaded()

        // Populate track list with the newly added external track
        fakePort.recordedProperties["track-list/count"] = 1
        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 3
        fakePort.recordedProperties["track-list/0/title"] = "English.srt"
        fakePort.recordedProperties["track-list/0/external"] = true
        fakePort.recordedProperties["track-list/0/selected"] = false

        renderer.onTrackListChanged()

        // sid re-applied to the remembered external track
        assertEquals("3", fakePort.recordedProperties["sid"])
        // Listener notified of confirmation and tracks changed
        assertEquals(listOf("m1" to true), fakeListener.externalSubtitleConfirmations)
        assertEquals(1, fakeListener.tracksChangedCalls)
    }

    @Test
    fun `subtitle confirmation times out when track never appears in track list`() {
        core.activeMediaUri = "http://example.com/video.mp4"
        core.activeMediaId = "m1"
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/video.mp4",
            mediaId = "m1",
            subtitles = listOf(
                PendingSubtitle(
                    uri = "http://example.com/sub.srt",
                    flag = "select",
                    title = "English.srt",
                ),
            ),
        )
        renderer.onFileLoaded()

        // Track never appears; simulate timeout
        directHandler.runDelayedTasks()

        assertEquals(listOf("m1" to false), fakeListener.externalSubtitleConfirmations)
    }

    @Test
    fun `onTrackListChanged is blocked when a different external track is selected`() {
        core.activeMediaUri = "http://example.com/video.mp4"
        core.activeMediaId = "m1"
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/video.mp4",
            mediaId = "m1",
            subtitles = listOf(
                PendingSubtitle(
                    uri = "http://example.com/sub1.srt",
                    flag = "select",
                    title = "Sub 1",
                ),
            ),
        )
        renderer.onFileLoaded()

        // Track list has two external tracks: Sub 1 and Sub 2. Sub 2 is currently selected by the user.
        fakePort.recordedProperties["track-list/count"] = 2
        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/title"] = "Sub 1"
        fakePort.recordedProperties["track-list/0/external"] = true
        fakePort.recordedProperties["track-list/0/selected"] = false

        fakePort.recordedProperties["track-list/1/type"] = "sub"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/title"] = "Sub 2"
        fakePort.recordedProperties["track-list/1/external"] = true
        fakePort.recordedProperties["track-list/1/selected"] = true

        fakePort.recordedProperties["sid"] = "2"
        renderer.onTrackListChanged()

        // sid must remain "2" and NOT be overwritten to "1"
        assertEquals("2", fakePort.recordedProperties["sid"])
    }

    @Test
    fun `replaceMediaItem returns FAILED for out of bounds index`() {
        fakeCoreHost.queue = mutableListOf(PlayableMedia(id = "m0", title = "V0", uri = "http://example.com/v.mp4"))
        fakeCoreHost.mediaIndex = 0

        val result = renderer.replaceMediaItem(5, PlayableMedia(id = "m5", title = "V5", uri = "http://example.com/v5.mp4"))
        assertEquals(ReplaceMediaItemResult.FAILED, result)
    }

    @Test
    fun `replaceMediaItem returns QUEUED for non-active queue index`() {
        fakeCoreHost.queue = mutableListOf(
            PlayableMedia(id = "m0", title = "V0", uri = "http://example.com/v0.mp4"),
            PlayableMedia(id = "m1", title = "V1", uri = "http://example.com/v1.mp4"),
        )
        fakeCoreHost.mediaIndex = 0

        val result = renderer.replaceMediaItem(1, PlayableMedia(id = "m1", title = "V1", uri = "http://example.com/v1.mp4"))
        assertEquals(ReplaceMediaItemResult.QUEUED, result)
    }

    @Test
    fun `replaceMediaItem issues sub-add for new external subtitles and dedupes existing ones`() {
        val initialMedia = PlayableMedia(
            id = "m0",
            title = "V0",
            uri = "http://example.com/v0.mp4",
            externalSubtitles = listOf(sub("http://example.com/sub1.srt", "Sub 1")),
        )
        fakeCoreHost.queue = mutableListOf(initialMedia)
        fakeCoreHost.mediaIndex = 0
        core.fileLoadedForCurrentItem = true

        val updatedMedia = PlayableMedia(
            id = "m0",
            title = "V0",
            uri = "http://example.com/v0.mp4",
            externalSubtitles = listOf(
                sub("http://example.com/sub1.srt", "Sub 1"),
                sub("http://example.com/sub2.srt", "Sub 2"),
            ),
        )

        val result = renderer.replaceMediaItem(0, updatedMedia)
        assertEquals(ReplaceMediaItemResult.ISSUED, result)

        // sub-add issued only for the new Sub 2
        assertTrue(
            fakePort.recordedCommands.contains(
                listOf("sub-add", "http://example.com/sub2.srt", "select", "Sub 2"),
            ),
        )
        assertFalse(
            fakePort.recordedCommands.contains(
                listOf("sub-add", "http://example.com/sub1.srt", "select", "Sub 1"),
            ),
        )

        // In production, SubtitleManager commits the updated item to PlaybackQueue on ISSUED.
        // Update the host queue to reflect the committed item before the repeat call.
        fakeCoreHost.queue[0] = updatedMedia

        // Calling replaceMediaItem again with the same media returns ALREADY_ADDED
        val repeatResult = renderer.replaceMediaItem(0, updatedMedia)
        assertEquals(ReplaceMediaItemResult.ALREADY_ADDED, repeatResult)
    }

    @Test
    fun `applySubtitleSettings sets mpv runtime properties`() {
        val settings = AppSettings(
            subtitleSize = "large",
        )
        renderer.applySubtitleSettings(settings)

        assertEquals("70", fakePort.recordedProperties["sub-font-size"])
    }

    @Test
    fun `cleanupForStopOrRelease clears pending subtitles and remembered state`() {
        core.pendingSubtitles = PendingSubtitles(
            mediaUri = "http://example.com/v.mp4",
            mediaId = "m0",
            subtitles = listOf(PendingSubtitle("http://example.com/s.srt", "select")),
        )

        renderer.cleanupForStopOrRelease()

        assertNull(core.pendingSubtitles)
    }
}
