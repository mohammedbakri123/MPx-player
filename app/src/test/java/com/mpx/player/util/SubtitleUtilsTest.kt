package com.mpx.player.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubtitleUtilsTest {

    private lateinit var testContext: Context

    @Before
    fun setUp() {
        testContext = object : ContextWrapper(null) {
            override fun getResources(): Resources = Resources.getSystem()

            // The Unsupported path formats R.string.subtitle_unsupported_format
            // (and R.string.subtitle_unknown_format for extensionless files)
            // via getString(). The wrapper's null base would NPE there;
            // answer with the formatted argument so the reason stays meaningful.
            override fun getString(resId: Int, vararg formatArgs: Any?): String =
                if (formatArgs.isNotEmpty()) "Unsupported format: ${formatArgs[0]}" else "unsupported"
        }
    }

    @Test
    fun `getDisplayName extracts filename from file Uri`() {
        val uri = Uri.parse("file:///storage/emulated/0/Download/movie.srt")
        val name = SubtitleUtils.getDisplayName(testContext, uri)
        assertEquals("movie.srt", name)
    }

    @Test
    fun `validateSubtitleFile accepts srt, vtt, webvtt, ass, ssa extensions`() {
        val srtUri = Uri.parse("file:///movies/sub.srt")
        val srtResult = SubtitleUtils.validateSubtitleFile(testContext, srtUri)
        assertTrue(srtResult is SubtitleValidationResult.Supported)
        assertEquals("application/x-subrip", (srtResult as SubtitleValidationResult.Supported).mimeType)
        assertEquals("sub.srt", srtResult.displayName)

        val vttUri = Uri.parse("file:///movies/sub.vtt")
        val vttResult = SubtitleUtils.validateSubtitleFile(testContext, vttUri)
        assertTrue(vttResult is SubtitleValidationResult.Supported)
        assertEquals("text/vtt", (vttResult as SubtitleValidationResult.Supported).mimeType)

        val webvttUri = Uri.parse("file:///movies/sub.webvtt")
        val webvttResult = SubtitleUtils.validateSubtitleFile(testContext, webvttUri)
        assertTrue(webvttResult is SubtitleValidationResult.Supported)
        assertEquals("text/vtt", (webvttResult as SubtitleValidationResult.Supported).mimeType)

        val assUri = Uri.parse("file:///movies/sub.ass")
        val assResult = SubtitleUtils.validateSubtitleFile(testContext, assUri)
        assertTrue(assResult is SubtitleValidationResult.Supported)
        assertEquals("text/x-ssa", (assResult as SubtitleValidationResult.Supported).mimeType)

        val ssaUri = Uri.parse("file:///movies/sub.ssa")
        val ssaResult = SubtitleUtils.validateSubtitleFile(testContext, ssaUri)
        assertTrue(ssaResult is SubtitleValidationResult.Supported)
        assertEquals("text/x-ssa", (ssaResult as SubtitleValidationResult.Supported).mimeType)
    }

    @Test
    fun `validateSubtitleFile rejects unsupported file extensions`() {
        val exeUri = Uri.parse("file:///movies/sub.exe")
        val result = SubtitleUtils.validateSubtitleFile(testContext, exeUri)
        assertTrue(result is SubtitleValidationResult.Unsupported)
        assertEquals("sub.exe", (result as SubtitleValidationResult.Unsupported).fileName)
    }

    @Test
    fun `detectSubtitleCodepage returns null when file is empty or cannot be opened`() {
        val dummyUri = Uri.parse("file:///missing.srt")
        val codepage = SubtitleUtils.detectSubtitleCodepage(testContext, dummyUri)
        assertNull(codepage)
    }
}
