package com.mpx.player.playback.engine

import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MpvTrackSelection] (Phase 8, Step 3.1 / CP-6).
 *
 * Verifies observable behaviour:
 * - mpv property reads and group/ordinal conversions for audio, subtitle, and video tracks.
 * - mpv property writes (`aid`, `sid`, `vid`) on track selection.
 * - Stale tap handling (no-op; does not drop current playback tracks).
 * - Host interaction: embedded subtitle or Off clears remembered external pick,
 *   while external subtitle leaves remembered pick intact (issue #372).
 */
class MpvTrackSelectionTest {

    private lateinit var testContext: TestContext
    private lateinit var fakePort: FakeMpvPort
    private lateinit var directHandler: DirectEngineHandler
    private lateinit var fakeCoreHost: TestCoreHost
    private lateinit var fakeTrackHost: TestTrackHost
    private lateinit var core: MpvCoreEngine
    private lateinit var trackSelection: MpvTrackSelection

    private class TestTrackHost : MpvTrackSelection.Host {
        var clearRememberedExternalPickCalls: Int = 0

        override fun clearRememberedExternalPick() {
            clearRememberedExternalPickCalls++
        }
    }

    private class TestCoreHost : MpvCoreEngine.Host {
        override fun hwdecValueFor(mode: DecoderMode): String = "mediacodec"
        override fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = "mediacodec"
        override fun applyHwdecForMode(mode: DecoderMode) {}
        override fun applyPostInitSettings(core: MpvCoreEngine) {}
        override fun onLoadStarted() {}
        override fun onLoadTargetResolved(mediaId: String) {}
        override fun onTrackListChanged() {}
        override fun onFileLoaded() {}
        override fun getCurrentQueue(): List<PlayableMedia> = emptyList()
        override fun getCurrentMediaIndex(): Int = 0
        override fun loadSettings(): AppSettings = AppSettings()
    }

    @Before
    fun setUp() {
        testContext = TestContext()
        fakePort = FakeMpvPort()
        directHandler = DirectEngineHandler()
        fakeCoreHost = TestCoreHost()
        fakeTrackHost = TestTrackHost()
        core = MpvCoreEngine(
            context = testContext,
            host = fakeCoreHost,
            port = fakePort,
            mainHandler = directHandler,
        )
        trackSelection = MpvTrackSelection(core, fakeTrackHost)
    }

    // --- Audio track listing and selection ---

    @Test
    fun `getAudioTracks returns empty list when track count is zero`() {
        fakePort.recordedProperties["track-list/count"] = 0
        assertTrue(trackSelection.getAudioTracks().isEmpty())
    }

    @Test
    fun `getAudioTracks correctly parses audio tracks and converts group indices`() {
        // Setup a track-list with mixed video and audio tracks
        fakePort.recordedProperties["track-list/count"] = 3

        // Track 0: video
        fakePort.recordedProperties["track-list/0/type"] = "video"
        fakePort.recordedProperties["track-list/0/id"] = 1

        // Track 1: audio (group 0)
        fakePort.recordedProperties["track-list/1/type"] = "audio"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/title"] = "English Commentary"
        fakePort.recordedProperties["track-list/1/lang"] = "eng"
        fakePort.recordedProperties["track-list/1/selected"] = true
        fakePort.recordedProperties["track-list/1/codec"] = "aac"

        // Track 2: audio (group 1, fallback title and und lang)
        fakePort.recordedProperties["track-list/2/type"] = "audio"
        fakePort.recordedProperties["track-list/2/id"] = 3
        fakePort.recordedProperties["track-list/2/title"] = ""
        // lang is intentionally omitted to verify fallback to "und"
        fakePort.recordedProperties["track-list/2/selected"] = false
        fakePort.recordedProperties["track-list/2/codec"] = "opus"

        val tracks = trackSelection.getAudioTracks()
        assertEquals(2, tracks.size)

        // First audio track: groupIndex 0, trackIndex 2
        assertEquals(0, tracks[0].groupIndex)
        assertEquals(2, tracks[0].trackIndex)
        assertEquals("English Commentary", tracks[0].label)
        assertEquals("eng", tracks[0].language)
        assertTrue(tracks[0].isSelected)
        assertEquals("aac", tracks[0].mimeType)

        // Second audio track: groupIndex 1, trackIndex 3
        assertEquals(1, tracks[1].groupIndex)
        assertEquals(3, tracks[1].trackIndex)
        assertEquals("Audio 3 (und)", tracks[1].label)
        assertEquals("und", tracks[1].language)
        assertFalse(tracks[1].isSelected)
        assertEquals("opus", tracks[1].mimeType)
    }

    @Test
    fun `selectTrack with audio null or minus one disables audio`() {
        trackSelection.selectTrack(TrackType.AUDIO, null)
        assertEquals("no", fakePort.recordedProperties["aid"])

        trackSelection.selectTrack(TrackType.AUDIO, "-1")
        assertEquals("no", fakePort.recordedProperties["aid"])

        trackSelection.selectTrack(TrackType.AUDIO, "   ")
        assertEquals("no", fakePort.recordedProperties["aid"])
    }

    @Test
    fun `selectTrack with audio group and track selects target track`() {
        populateAudioTracks()

        // "1:2" -> group 1, trackIndex 2
        trackSelection.selectTrack(TrackType.AUDIO, "1:2")
        assertEquals("2", fakePort.recordedProperties["aid"])
    }

    @Test
    fun `selectTrack with audio ordinal index selects target track`() {
        populateAudioTracks()

        // "0" -> ordinal 0, which is trackIndex 1
        trackSelection.selectTrack(TrackType.AUDIO, "0")
        assertEquals("1", fakePort.recordedProperties["aid"])

        // "1" -> ordinal 1, which is trackIndex 2
        trackSelection.selectTrack(TrackType.AUDIO, "1")
        assertEquals("2", fakePort.recordedProperties["aid"])
    }

    @Test
    fun `selectAudioTrack with stale track pick is ignored and does not set aid to no`() {
        populateAudioTracks()
        fakePort.recordedProperties["aid"] = "1"

        // Group 0, trackIndex 999 does not exist
        trackSelection.selectAudioTrack(0, 999)

        // aid remains "1", not set to "no"
        assertEquals("1", fakePort.recordedProperties["aid"])
    }

    // --- Subtitle track listing, selection, and external pick clearing ---

    @Test
    fun `getSubtitleTracks returns empty list when track count is zero`() {
        fakePort.recordedProperties["track-list/count"] = 0
        assertTrue(trackSelection.getSubtitleTracks().isEmpty())
    }

    @Test
    fun `getSubtitleTracks correctly parses embedded and external subtitle tracks`() {
        fakePort.recordedProperties["track-list/count"] = 2

        // Track 0: embedded sub (group 0)
        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/title"] = "English SDH"
        fakePort.recordedProperties["track-list/0/lang"] = "eng"
        fakePort.recordedProperties["track-list/0/selected"] = true
        fakePort.recordedProperties["track-list/0/external"] = false
        fakePort.recordedProperties["track-list/0/default"] = true

        // Track 1: external sub (group 1, fallback title)
        fakePort.recordedProperties["track-list/1/type"] = "sub"
        fakePort.recordedProperties["track-list/1/id"] = 2
        // title intentionally omitted to test fallback title
        fakePort.recordedProperties["track-list/1/lang"] = "ara"
        fakePort.recordedProperties["track-list/1/selected"] = false
        fakePort.recordedProperties["track-list/1/external"] = true
        fakePort.recordedProperties["track-list/1/default"] = false

        val subs = trackSelection.getSubtitleTracks()
        assertEquals(2, subs.size)

        // Embedded subtitle
        assertEquals(0, subs[0].groupIndex)
        assertEquals(1, subs[0].trackIndex)
        assertEquals("English SDH", subs[0].label)
        assertEquals("eng", subs[0].language)
        assertTrue(subs[0].isSelected)
        assertFalse(subs[0].isExternal)
        assertTrue(subs[0].isDefault)

        // External subtitle
        assertEquals(1, subs[1].groupIndex)
        assertEquals(2, subs[1].trackIndex)
        assertEquals("Subtitle 2 (ara)", subs[1].label)
        assertEquals("ara", subs[1].language)
        assertFalse(subs[1].isSelected)
        assertTrue(subs[1].isExternal)
        assertFalse(subs[1].isDefault)
    }

    @Test
    fun `selectTrack with subtitle null or minus one turns subtitles off and clears host pick`() {
        trackSelection.selectTrack(TrackType.SUBTITLE, null)
        assertEquals("no", fakePort.recordedProperties["sid"])
        assertEquals(1, fakeTrackHost.clearRememberedExternalPickCalls)

        trackSelection.selectTrack(TrackType.SUBTITLE, "-1")
        assertEquals("no", fakePort.recordedProperties["sid"])
        assertEquals(2, fakeTrackHost.clearRememberedExternalPickCalls)

        trackSelection.selectTrack(TrackType.SUBTITLE, "")
        assertEquals("no", fakePort.recordedProperties["sid"])
        assertEquals(3, fakeTrackHost.clearRememberedExternalPickCalls)
    }

    @Test
    fun `selectTrack with invalid subtitle ordinal turns subtitles off and clears host pick`() {
        populateSubtitleTracks()

        // Negative ordinal
        trackSelection.selectTrack(TrackType.SUBTITLE, "-2")
        assertEquals("no", fakePort.recordedProperties["sid"])
        assertEquals(1, fakeTrackHost.clearRememberedExternalPickCalls)

        // Out of range ordinal
        trackSelection.selectTrack(TrackType.SUBTITLE, "99")
        assertEquals("no", fakePort.recordedProperties["sid"])
        assertEquals(2, fakeTrackHost.clearRememberedExternalPickCalls)
    }

    @Test
    fun `selectSubtitleTrack with embedded track sets sid and clears remembered external pick`() {
        populateSubtitleTracks()

        // Track 1 is embedded (group 0, id 1)
        trackSelection.selectSubtitleTrack(0, 1)

        assertEquals("1", fakePort.recordedProperties["sid"])
        // Embedded track supersedes external pick -> host notified
        assertEquals(1, fakeTrackHost.clearRememberedExternalPickCalls)
    }

    @Test
    fun `selectSubtitleTrack with external track sets sid and preserves remembered external pick`() {
        populateSubtitleTracks()

        // Track 2 is external (group 1, id 2)
        trackSelection.selectSubtitleTrack(1, 2)

        assertEquals("2", fakePort.recordedProperties["sid"])
        // External track must NOT clear remembered external pick
        assertEquals(0, fakeTrackHost.clearRememberedExternalPickCalls)
    }

    @Test
    fun `selectSubtitleTrack with stale track does not write sid or clear host pick`() {
        populateSubtitleTracks()
        fakePort.recordedProperties["sid"] = "1"

        // Stale track (group 0, id 999)
        trackSelection.selectSubtitleTrack(0, 999)

        // sid left unchanged, host not notified
        assertEquals("1", fakePort.recordedProperties["sid"])
        assertEquals(0, fakeTrackHost.clearRememberedExternalPickCalls)
    }

    // --- Video track listing and selection ---

    @Test
    fun `getVideoTrackIds returns ids in order and filters out audio and subtitle tracks`() {
        fakePort.recordedProperties["track-list/count"] = 3
        fakePort.recordedProperties["track-list/0/type"] = "video"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/1/type"] = "audio"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/2/type"] = "video"
        fakePort.recordedProperties["track-list/2/id"] = 3

        val videoIds = trackSelection.getVideoTrackIds()
        assertEquals(listOf(1, 3), videoIds)
    }

    @Test
    fun `selectTrack with video null or minus one sets vid to auto`() {
        trackSelection.selectTrack(TrackType.VIDEO, null)
        assertEquals("auto", fakePort.recordedProperties["vid"])

        trackSelection.selectTrack(TrackType.VIDEO, "-1")
        assertEquals("auto", fakePort.recordedProperties["vid"])

        trackSelection.selectTrack(TrackType.VIDEO, "")
        assertEquals("auto", fakePort.recordedProperties["vid"])
    }

    @Test
    fun `selectTrack with video group and track validates matching id before setting vid`() {
        populateVideoTracks()

        // "0:1" -> ordinal 0 has id 1: matches
        trackSelection.selectTrack(TrackType.VIDEO, "0:1")
        assertEquals("1", fakePort.recordedProperties["vid"])

        // "0:2" -> ordinal 0 has id 1, not 2: stale mismatch, should not update vid
        fakePort.recordedProperties["vid"] = "1"
        trackSelection.selectTrack(TrackType.VIDEO, "0:2")
        assertEquals("1", fakePort.recordedProperties["vid"])
    }

    @Test
    fun `selectTrack with video ordinal selects target vid`() {
        populateVideoTracks()

        // "1" -> ordinal 1 has id 3
        trackSelection.selectTrack(TrackType.VIDEO, "1")
        assertEquals("3", fakePort.recordedProperties["vid"])

        // Out of range ordinal -> ignored
        trackSelection.selectTrack(TrackType.VIDEO, "99")
        assertEquals("3", fakePort.recordedProperties["vid"])
    }

    // --- Test helpers ---

    private fun populateAudioTracks() {
        fakePort.recordedProperties["track-list/count"] = 2
        fakePort.recordedProperties["track-list/0/type"] = "audio"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/title"] = "Track 1"
        fakePort.recordedProperties["track-list/0/lang"] = "eng"

        fakePort.recordedProperties["track-list/1/type"] = "audio"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/title"] = "Track 2"
        fakePort.recordedProperties["track-list/1/lang"] = "fra"
    }

    private fun populateSubtitleTracks() {
        fakePort.recordedProperties["track-list/count"] = 2
        fakePort.recordedProperties["track-list/0/type"] = "sub"
        fakePort.recordedProperties["track-list/0/id"] = 1
        fakePort.recordedProperties["track-list/0/title"] = "Embedded Sub"
        fakePort.recordedProperties["track-list/0/lang"] = "eng"
        fakePort.recordedProperties["track-list/0/external"] = false

        fakePort.recordedProperties["track-list/1/type"] = "sub"
        fakePort.recordedProperties["track-list/1/id"] = 2
        fakePort.recordedProperties["track-list/1/title"] = "External Sub"
        fakePort.recordedProperties["track-list/1/lang"] = "esp"
        fakePort.recordedProperties["track-list/1/external"] = true
    }

    private fun populateVideoTracks() {
        fakePort.recordedProperties["track-list/count"] = 2
        fakePort.recordedProperties["track-list/0/type"] = "video"
        fakePort.recordedProperties["track-list/0/id"] = 1

        fakePort.recordedProperties["track-list/1/type"] = "video"
        fakePort.recordedProperties["track-list/1/id"] = 3
    }
}
