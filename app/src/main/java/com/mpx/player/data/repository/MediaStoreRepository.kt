package com.mpx.player.data.repository

import android.content.Context
import android.net.Uri
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import java.io.File

/**
 * Real repository backed by the Android MediaStore content provider.
 * Scans video folders with actual video counts and aggregate byte sizes,
 * and audio tracks with accurate title, artist, album, and duration metadata.
 *
 * Fully supports multi-volume scanning across both primary internal storage
 * and removable external storage (SD cards, USB OTG) using volume-specific
 * MediaStore content URIs ([MediaStore.getExternalVolumeNames]).
 *
 * Thin facade delegating specialized scanning concerns to:
 * - [VideoMediaScanner]: video folders, folder drill-down, and all-videos queries.
 * - [AudioMediaScanner]: audio tracks, albums, artists, and audio folders.
 * - [PlaylistMediaScanner]: device playlists and playlist track resolution.
 * - [MediaStoreScannerHelper]: shared volume discovery and storage path filtering.
 * - [MediaStoreCache]: instance-managed scan cache with explicit invalidation hooks.
 *
 * Invariants:
 * - Returns raw numeric values (durations in ms, sizes in bytes); string formatting is performed
 *   strictly in the UI layer.
 * - Caching is instance-managed via [MediaStoreCache] (no companion-object or global mutable static state).
 *   Cache can be explicitly refreshed via [invalidateCache].
 *
 * ## Cursor-based Pagination Feasibility Verdict (CP-7, Step 4.3)
 * Full cursor-based pagination was investigated and rejected as high-risk and uncontained:
 * 1. Multi-volume scanning: Storage volumes (internal, SD card, USB OTG) are queried via
 *    independent content URIs. Paging with LIMIT/OFFSET cannot be unified across distinct
 *    provider queries without complex multi-cursor priority merges.
 * 2. In-memory aggregation: MediaStore stores only file rows. [MediaFolder]s, audio albums,
 *    and artists are derived in-memory by grouping tracks and calculating aggregate counts,
 *    byte sizes, and durations. Cursor paging would yield partial groups and inaccurate totals.
 * 3. In-memory filtering: Hidden files and system cache exclusions (/Android/data, /Android/obb)
 *    must be filtered in Kotlin due to scoped storage path virtualization (API 29+). Fixed SQL
 *    LIMIT queries would produce variable-sized, unpredictable page results downstream.
 * 4. Tree navigation & sorting: [LocalFolderTree] requires the complete folder graph to build
 *    parent-child hierarchies, and user-configurable multi-field sort orders cannot be mapped
 *    to keyset pagination on `_ID`.
 *
 * Verdict: Cursor pagination is NOT recommended at this time; full in-memory scans with
 * instance-level caching remain optimal for current architecture and performance.
 */
class MediaStoreRepository(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository = SharedPrefsAppSettingsRepository(context),
    private val cache: MediaStoreCache = MediaStoreCache(),
) : MediaRepository {

    private val helper = MediaStoreScannerHelper(context, settingsRepository)

    private val videoScanner = VideoMediaScanner(
        context = context,
        helper = helper,
        getCachedFolderPath = cache::findFolderPath,
    )

    private val audioScanner = AudioMediaScanner(
        context = context,
        helper = helper,
    )

    private val playlistScanner = PlaylistMediaScanner(
        context = context,
        helper = helper,
        audioTrackProvider = audioScanner::getAudioTracks,
    )

    override fun getExternalVolumeNames(): Set<String> = helper.getExternalVolumeNames()

    fun getVideoContentUris(): List<Uri> = helper.getVideoContentUris()
    fun getAudioContentUris(): List<Uri> = helper.getAudioContentUris()
    fun getPlaylistContentUris(): List<Uri> = helper.getPlaylistContentUris()
    fun isRemovablePath(path: String): Boolean = helper.isRemovablePath(path)
    fun getStorageRootDirectories(): List<File> = helper.getStorageRootDirectories()

    override fun cachedFolders(showAllFolders: Boolean, showHiddenFiles: Boolean): List<MediaFolder>? =
        cache.getFolders(showAllFolders, showHiddenFiles)

    override fun cachedVideos(showAll: Boolean, showHiddenFiles: Boolean): List<VideoFile>? =
        cache.getVideos(showAll, showHiddenFiles)

    override fun invalidateCache() {
        cache.invalidate()
    }

    override fun getMediaFolders(): List<MediaFolder> = getMediaFolders(showAllFolders = false)

    override fun getMediaFolders(showAllFolders: Boolean): List<MediaFolder> {
        val showHidden = helper.showHiddenFiles()
        val result = videoScanner.getMediaFolders(showAllFolders)
        cache.updateFolders(showAllFolders, showHidden, result)
        return result
    }

    override fun getVideosInFolder(bucketId: String): List<VideoFile> =
        videoScanner.getVideosInFolder(bucketId)

    override fun getAllVideos(): List<VideoFile> = getAllVideos(showAll = false)

    override fun getAllVideos(showAll: Boolean): List<VideoFile> {
        val showHidden = helper.showHiddenFiles()
        val result = videoScanner.getAllVideos(showAll)
        cache.updateVideos(showAll, showHidden, result)
        return result
    }

    override fun getAudioTracks(): List<AudioTrack> = audioScanner.getAudioTracks()

    override fun getAudioAlbums(): List<AudioAlbum> = audioScanner.getAudioAlbums()

    override fun getAudioArtists(): List<AudioArtist> = audioScanner.getAudioArtists()

    override fun getAudioFolders(): List<AudioFolder> = audioScanner.getAudioFolders()

    override fun getPlaylists(): List<Playlist> = playlistScanner.getPlaylists()

    override fun getPlaylistTracks(playlistId: String): List<AudioTrack> =
        playlistScanner.getPlaylistTracks(playlistId)
}
