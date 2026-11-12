package com.mpx.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun `normalizedForSearch lowercases and strips diacritics`() {
        assertEquals("cafe", "Café".normalizedForSearch())
        assertEquals("nino", "Niño".normalizedForSearch())
        assertEquals("resume", "RÉSUMÉ".normalizedForSearch())
        assertEquals("simple text", "Simple Text".normalizedForSearch())
    }

    @Test
    fun `fuzzyMatchRank returns correct rank based on match type`() {
        // Rank 0: Prefix match
        assertEquals(0, fuzzyMatchRank("mov", "Movie Title"))
        assertEquals(0, fuzzyMatchRank("caf", "Café Society"))

        // Rank 1: Substring match (non-prefix)
        assertEquals(1, fuzzyMatchRank("vie", "Movie Title"))
        assertEquals(1, fuzzyMatchRank("soc", "Café Society"))

        // Rank 2: Subsequence match (fuzzy)
        assertEquals(2, fuzzyMatchRank("mvt", "Movie Title"))
        assertEquals(2, fuzzyMatchRank("cfs", "Café Society"))

        // Null: No match
        assertNull(fuzzyMatchRank("xyz", "Movie Title"))
        assertNull(fuzzyMatchRank("", "Movie Title"))
    }

    @Test
    fun `fuzzyMatchRank with path falls back to path as substring rank 1`() {
        // Name prefix (rank 0) beats path match
        assertEquals(0, fuzzyMatchRank("mov", "Movie", "/storage/Videos/Movie.mp4"))

        // Name doesn't match, but folder in path matches -> rank 1
        assertEquals(1, fuzzyMatchRank("vid", "Clip", "/storage/Videos/Clip.mp4"))

        // Neither matches -> null
        assertNull(fuzzyMatchRank("music", "Clip", "/storage/Videos/Clip.mp4"))
    }

    data class TestMedia(val name: String, val path: String)

    @Test
    fun `fuzzySearch filters, ranks, and orders results alphabetically within ranks`() {
        val items = listOf(
            TestMedia("Avatar", "/movies/Avatar.mp4"),
            TestMedia("The Avengers", "/movies/The Avengers.mp4"),
            TestMedia("Avocado", "/home/Avocado.mp4"),
            TestMedia("Batman", "/action/Batman.mp4"),
            TestMedia("Dark Knight", "/action/av_folder/Dark Knight.mp4"),
        )

        // Query "av":
        // Rank 0 (prefix): "Avatar", "Avocado" (sorted alphabetically)
        // Rank 1 (substring): "The Avengers" (name contains "Av"), "Dark Knight" (path contains "av_folder")
        // "Batman" does not match.
        val results = fuzzySearch(
            query = "av",
            items = items,
            nameOf = { it.name },
            pathOf = { it.path },
        )

        val expectedNames = listOf(
            "Avatar",        // Rank 0
            "Avocado",       // Rank 0
            "Dark Knight",   // Rank 1 (path hit, D before T)
            "The Avengers",  // Rank 1 (name substring)
        )
        assertEquals(expectedNames, results.map { it.name })
    }

    @Test
    fun `fuzzySearch with blank query returns items unchanged`() {
        val items = listOf(
            TestMedia("B", "/b"),
            TestMedia("A", "/a"),
        )
        val results = fuzzySearch(
            query = "   ",
            items = items,
            nameOf = { it.name },
            pathOf = { it.path },
        )
        assertEquals(items, results)
    }
}
