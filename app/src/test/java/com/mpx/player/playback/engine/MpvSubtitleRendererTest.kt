package com.mpx.player.playback.engine

import com.mpx.player.playback.ExternalSubtitle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
