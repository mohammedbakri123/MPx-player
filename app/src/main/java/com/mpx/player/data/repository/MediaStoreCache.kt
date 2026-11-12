package com.mpx.player.data.repository

import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile

/**
 * Repository-managed process-lifetime cache of the last media scan results.
 * Owned as an instance by [MediaStoreRepository] (registered as a Koin singleton).
 *
 * Preserves the previous scan across LocalViewModel re-creations so returning
 * to the Local tab renders previous content instantly without flashing empty
 * while the background rescan runs (#100).
 *
 * Invariants:
 * - Thread safety: cached data is held in `@Volatile` triples ([cachedFolders], [cachedVideos])
 *   keyed by the `showAll` and `showHiddenFiles` query flags.
 * - Instance ownership: completely avoids global mutable companion or static state.
 * - Explicit invalidation: provides fine-grained invalidation hooks ([invalidate],
 *   [invalidateFolders], [invalidateVideos]) for explicit cache clearing.
 * - Bucket path resolution: provides [findFolderPath] to resolve directory paths for blank bucket ID rows.
 */
class MediaStoreCache {
    @Volatile
    private var cachedFolders: Triple<Boolean, Boolean, List<MediaFolder>>? = null

    @Volatile
    private var cachedVideos: Triple<Boolean, Boolean, List<VideoFile>>? = null

    fun getFolders(showAllFolders: Boolean, showHiddenFiles: Boolean): List<MediaFolder>? =
        cachedFolders?.takeIf { it.first == showAllFolders && it.second == showHiddenFiles }?.third

    fun getVideos(showAll: Boolean, showHiddenFiles: Boolean): List<VideoFile>? =
        cachedVideos?.takeIf { it.first == showAll && it.second == showHiddenFiles }?.third

    fun updateFolders(showAllFolders: Boolean, showHiddenFiles: Boolean, folders: List<MediaFolder>) {
        cachedFolders = Triple(showAllFolders, showHiddenFiles, folders)
    }

    fun updateVideos(showAll: Boolean, showHiddenFiles: Boolean, videos: List<VideoFile>) {
        cachedVideos = Triple(showAll, showHiddenFiles, videos)
    }

    /**
     * Resolves the filesystem path for a bucket ID from the most recent folder scan,
     * used as fallback for blank BUCKET_ID rows.
     */
    fun findFolderPath(bucketId: String): String? =
        cachedFolders?.third?.firstOrNull { it.id == bucketId }?.path

    /**
     * Explicit invalidation hook to clear all cached media scan results.
     */
    fun invalidate() {
        cachedFolders = null
        cachedVideos = null
    }

    fun invalidateFolders() {
        cachedFolders = null
    }

    fun invalidateVideos() {
        cachedVideos = null
    }
}
