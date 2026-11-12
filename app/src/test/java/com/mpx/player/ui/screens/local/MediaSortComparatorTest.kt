package com.mpx.player.ui.screens.local

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSortComparatorTest {

    @Test
    fun `naturalCompare orders numeric segments logically`() {
        // Natural numbers: 2 before 10
        assertTrue(MediaSortComparator.naturalCompare("Episode 2", "Episode 10") < 0)
        assertTrue(MediaSortComparator.naturalCompare("Episode 10", "Episode 2") > 0)
        assertTrue(MediaSortComparator.naturalCompare("Track 01", "Track 2") < 0)
        assertEquals(0, MediaSortComparator.naturalCompare("Title", "Title"))
    }

    @Test
    fun `naturalCompare respects case sensitivity and extension stripping`() {
        // Case insensitive by default
        assertEquals(0, MediaSortComparator.naturalCompare("apple", "Apple", caseSensitive = false))
        assertTrue(MediaSortComparator.naturalCompare("apple", "Apple", caseSensitive = true) > 0)

        // Ignore extension
        assertEquals(0, MediaSortComparator.naturalCompare("video.mp4", "video.mkv", ignoreExtension = true))
        assertTrue(MediaSortComparator.naturalCompare("video.mp4", "video.mkv", ignoreExtension = false) > 0)
    }

    @Test
    fun `videoComparator sorts by NAME, DATE, SIZE, and DURATION`() {
        val v1 = VideoFile(id = "1", title = "B_Movie", path = "/b.mp4", dateModified = 1000L, sizeBytes = 500L, durationMs = 3000L)
        val v2 = VideoFile(id = "2", title = "A_Movie", path = "/a.mp4", dateModified = 2000L, sizeBytes = 100L, durationMs = 1000L)
        val list = listOf(v1, v2)

        // Sort by NAME ascending
        val byName = list.sortedWith(MediaSortComparator.videoComparator(SortField.NAME, SortField.NAME, reverse = false))
        assertEquals(listOf("A_Movie", "B_Movie"), byName.map { it.title })

        // Sort by NAME descending (reverse)
        val byNameRev = list.sortedWith(MediaSortComparator.videoComparator(SortField.NAME, SortField.NAME, reverse = true))
        assertEquals(listOf("B_Movie", "A_Movie"), byNameRev.map { it.title })

        // Sort by DATE ascending (oldest first: v1 then v2)
        val byDate = list.sortedWith(MediaSortComparator.videoComparator(SortField.DATE, SortField.NAME, reverse = false))
        assertEquals(listOf("B_Movie", "A_Movie"), byDate.map { it.title })

        // Sort by SIZE ascending (smallest first: v2 then v1)
        val bySize = list.sortedWith(MediaSortComparator.videoComparator(SortField.SIZE, SortField.NAME, reverse = false))
        assertEquals(listOf("A_Movie", "B_Movie"), bySize.map { it.title })

        // Sort by DURATION ascending (shortest first: v2 then v1)
        val byDuration = list.sortedWith(MediaSortComparator.videoComparator(SortField.DURATION, SortField.NAME, reverse = false))
        assertEquals(listOf("A_Movie", "B_Movie"), byDuration.map { it.title })
    }

    @Test
    fun `videoComparator secondary sort breaks primary ties`() {
        val v1 = VideoFile(id = "1", title = "Beta", path = "/b.mp4", dateModified = 1000L)
        val v2 = VideoFile(id = "2", title = "Alpha", path = "/a.mp4", dateModified = 1000L)
        val list = listOf(v1, v2)

        // Primary DATE is identical (1000L), secondary NAME breaks tie
        val sorted = list.sortedWith(MediaSortComparator.videoComparator(SortField.DATE, SortField.NAME, reverse = false))
        assertEquals(listOf("Alpha", "Beta"), sorted.map { it.title })
    }

    @Test
    fun `folderComparator sorts by NAME, COUNT, and SIZE`() {
        val f1 = MediaFolder(id = "1", name = "Videos", path = "/v", videoCount = 10, totalSizeFormatted = "1.0 MB", totalSizeBytes = 1000L)
        val f2 = MediaFolder(id = "2", name = "Camera", path = "/c", videoCount = 50, totalSizeFormatted = "500 KB", totalSizeBytes = 500L)
        val list = listOf(f1, f2)

        val byName = list.sortedWith(MediaSortComparator.folderComparator(SortField.NAME, reverse = false))
        assertEquals(listOf("Camera", "Videos"), byName.map { it.name })

        val byCount = list.sortedWith(MediaSortComparator.folderComparator(SortField.COUNT, reverse = false))
        assertEquals(listOf("Videos", "Camera"), byCount.map { it.name })

        val bySize = list.sortedWith(MediaSortComparator.folderComparator(SortField.SIZE, reverse = false))
        assertEquals(listOf("Camera", "Videos"), bySize.map { it.name })
    }

    @Test
    fun `audioTrackComparator sorts by track details`() {
        val t1 = AudioTrack(id = "1", title = "Song B", artist = "Artist Z", album = "Album 1", durationFormatted = "0:02", path = "/1.mp3", durationMs = 2000L)
        val t2 = AudioTrack(id = "2", title = "Song A", artist = "Artist A", album = "Album 2", durationFormatted = "0:01", path = "/2.mp3", durationMs = 1000L)
        val list = listOf(t1, t2)

        val byName = list.sortedWith(MediaSortComparator.audioTrackComparator(SortField.NAME, SortField.NAME, reverse = false))
        assertEquals(listOf("Song A", "Song B"), byName.map { it.title })

        val byDuration = list.sortedWith(MediaSortComparator.audioTrackComparator(SortField.DURATION, SortField.NAME, reverse = false))
        assertEquals(listOf("Song B", "Song A"), byDuration.map { it.title })
    }

    @Test
    fun `audioPlaylistComparator orders device playlists numerically before custom playlists`() {
        val p1 = Playlist(id = "10", title = "Device 10", itemCount = 2)
        val p2 = Playlist(id = "2", title = "Device 2", itemCount = 5)
        val pCustom = Playlist(id = "custom:1", title = "Custom Playlist", itemCount = 1)
        val list = listOf(p1, pCustom, p2)

        // Issue #242: numeric IDs sort numerically (2 before 10), and custom IDs sort after
        val sorted = list.sortedWith(MediaSortComparator.audioPlaylistComparator(SortField.DATE, reverse = false))
        assertEquals(listOf("Device 2", "Device 10", "Custom Playlist"), sorted.map { it.title })
    }
}
