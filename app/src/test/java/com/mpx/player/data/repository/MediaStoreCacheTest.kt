package com.mpx.player.data.repository

import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreCacheTest {

    private fun createFolder(id: String, path: String) = MediaFolder(
        id = id,
        name = "Folder $id",
        path = path,
        videoCount = 1,
        totalSizeFormatted = "1 MB",
    )

    private fun createVideo(id: String, path: String, bucketId: String) = VideoFile(
        id = id,
        title = "Video $id",
        path = path,
        bucketId = bucketId,
    )

    @Test
    fun `initial cache is empty`() {
        val cache = MediaStoreCache()
        assertNull(cache.getFolders(showAllFolders = false, showHiddenFiles = false))
        assertNull(cache.getVideos(showAll = false, showHiddenFiles = false))
        assertNull(cache.findFolderPath("bucket1"))
    }

    @Test
    fun `updateFolders caches matching flags and returns null on flag mismatch`() {
        val cache = MediaStoreCache()
        val folders = listOf(createFolder("1", "/storage/emulated/0/Movies"))

        cache.updateFolders(showAllFolders = false, showHiddenFiles = false, folders = folders)

        assertEquals(folders, cache.getFolders(showAllFolders = false, showHiddenFiles = false))
        assertNull(cache.getFolders(showAllFolders = true, showHiddenFiles = false))
        assertNull(cache.getFolders(showAllFolders = false, showHiddenFiles = true))
    }

    @Test
    fun `updateVideos caches matching flags and returns null on flag mismatch`() {
        val cache = MediaStoreCache()
        val videos = listOf(createVideo("1", "/storage/emulated/0/Movies/a.mp4", "1"))

        cache.updateVideos(showAll = false, showHiddenFiles = false, videos = videos)

        assertEquals(videos, cache.getVideos(showAll = false, showHiddenFiles = false))
        assertNull(cache.getVideos(showAll = true, showHiddenFiles = false))
        assertNull(cache.getVideos(showAll = false, showHiddenFiles = true))
    }

    @Test
    fun `findFolderPath resolves bucket path from cached folders`() {
        val cache = MediaStoreCache()
        val folders = listOf(
            createFolder("b1", "/storage/emulated/0/Movies"),
            createFolder("b2", "/storage/emulated/0/DCIM/Camera"),
        )
        cache.updateFolders(showAllFolders = false, showHiddenFiles = false, folders = folders)

        assertEquals("/storage/emulated/0/Movies", cache.findFolderPath("b1"))
        assertEquals("/storage/emulated/0/DCIM/Camera", cache.findFolderPath("b2"))
        assertNull(cache.findFolderPath("b3"))
    }

    @Test
    fun `invalidateFolders clears folders cache but preserves videos`() {
        val cache = MediaStoreCache()
        val folders = listOf(createFolder("1", "/storage/emulated/0/Movies"))
        val videos = listOf(createVideo("1", "/storage/emulated/0/Movies/a.mp4", "1"))

        cache.updateFolders(false, false, folders)
        cache.updateVideos(false, false, videos)

        cache.invalidateFolders()

        assertNull(cache.getFolders(false, false))
        assertEquals(videos, cache.getVideos(false, false))
    }

    @Test
    fun `invalidateVideos clears videos cache but preserves folders`() {
        val cache = MediaStoreCache()
        val folders = listOf(createFolder("1", "/storage/emulated/0/Movies"))
        val videos = listOf(createVideo("1", "/storage/emulated/0/Movies/a.mp4", "1"))

        cache.updateFolders(false, false, folders)
        cache.updateVideos(false, false, videos)

        cache.invalidateVideos()

        assertEquals(folders, cache.getFolders(false, false))
        assertNull(cache.getVideos(false, false))
    }

    @Test
    fun `invalidate clears both folders and videos caches`() {
        val cache = MediaStoreCache()
        val folders = listOf(createFolder("1", "/storage/emulated/0/Movies"))
        val videos = listOf(createVideo("1", "/storage/emulated/0/Movies/a.mp4", "1"))

        cache.updateFolders(false, false, folders)
        cache.updateVideos(false, false, videos)

        cache.invalidate()

        assertNull(cache.getFolders(false, false))
        assertNull(cache.getVideos(false, false))
        assertNull(cache.findFolderPath("1"))
    }
}
