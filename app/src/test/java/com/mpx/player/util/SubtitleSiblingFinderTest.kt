package com.mpx.player.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SubtitleSiblingFinderTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File.createTempFile("mpx_sub_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `SUBTITLE_EXTENSIONS contains all expected formats`() {
        val expected = setOf("srt", "vtt", "webvtt", "ass", "ssa")
        assertEquals(expected, SubtitleSiblingFinder.SUBTITLE_EXTENSIONS)
    }

    @Test
    fun `isSubtitleFile identifies subtitle extensions case-insensitively`() {
        val srtFile = File(tempDir, "sample.SRT").apply { writeText("1\n00:00:01 --> 00:00:02\nHi") }
        val vttFile = File(tempDir, "sample.vtt").apply { writeText("WEBVTT") }
        val assFile = File(tempDir, "sample.ass").apply { writeText("[Script Info]") }
        val textFile = File(tempDir, "notes.txt").apply { writeText("just notes") }
        val subDir = File(tempDir, "folder.srt").apply { mkdirs() }

        assertTrue(SubtitleSiblingFinder.isSubtitleFile(srtFile))
        assertTrue(SubtitleSiblingFinder.isSubtitleFile(vttFile))
        assertTrue(SubtitleSiblingFinder.isSubtitleFile(assFile))
        assertFalse(SubtitleSiblingFinder.isSubtitleFile(textFile))
        // Directory with .srt extension must not be considered a subtitle file
        assertFalse(SubtitleSiblingFinder.isSubtitleFile(subDir))
    }

    @Test
    fun `findSiblings returns exact stem match before language tagged matches`() {
        val movie = File(tempDir, "Movie.mp4").apply { writeText("dummy video") }
        val exactSrt = File(tempDir, "Movie.srt").apply { writeText("exact") }
        val enSrt = File(tempDir, "Movie.en.srt").apply { writeText("english") }
        val arSrt = File(tempDir, "Movie.ar.vtt").apply { writeText("arabic") }
        val unrelatedSrt = File(tempDir, "Other.srt").apply { writeText("other") }

        val siblings = SubtitleSiblingFinder.findSiblings(movie.absolutePath)

        // Exact match first, then language-tagged alphabetically
        assertEquals(listOf(exactSrt, arSrt, enSrt), siblings)
        assertFalse(siblings.contains(unrelatedSrt))
    }

    @Test
    fun `findSiblings returns empty list when directory has no matches or cannot be listed`() {
        val movie = File(tempDir, "Solo.mkv").apply { writeText("solo video") }
        val siblings = SubtitleSiblingFinder.findSiblings(movie.absolutePath)
        assertTrue(siblings.isEmpty())

        // Non-existent path returns empty list
        val nonExistent = SubtitleSiblingFinder.findSiblings("/non/existent/path/video.mp4")
        assertTrue(nonExistent.isEmpty())
    }

    @Test
    fun `listSubtitleDir lists directories first then subtitle files alphabetically`() {
        val subFolderB = File(tempDir, "SubDirB").apply { mkdirs() }
        val subFolderA = File(tempDir, "SubDirA").apply { mkdirs() }
        val srtZ = File(tempDir, "Zeta.srt").apply { writeText("z") }
        val srtA = File(tempDir, "Alpha.srt").apply { writeText("a") }
        val txtFile = File(tempDir, "Readme.txt").apply { writeText("readme") }

        val entries = SubtitleSiblingFinder.listSubtitleDir(tempDir)
        assertNotNull(entries)

        // Directories first (A, B), then subtitle files (Alpha.srt, Zeta.srt)
        val expected = listOf(
            SubtitleSiblingFinder.BrowserEntry(subFolderA, isDirectory = true),
            SubtitleSiblingFinder.BrowserEntry(subFolderB, isDirectory = true),
            SubtitleSiblingFinder.BrowserEntry(srtA, isDirectory = false),
            SubtitleSiblingFinder.BrowserEntry(srtZ, isDirectory = false),
        )
        assertEquals(expected, entries)
    }

    @Test
    fun `listSubtitleDir returns null for non-existent directory`() {
        val missing = File(tempDir, "MissingDir")
        assertNull(SubtitleSiblingFinder.listSubtitleDir(missing))
    }
}
