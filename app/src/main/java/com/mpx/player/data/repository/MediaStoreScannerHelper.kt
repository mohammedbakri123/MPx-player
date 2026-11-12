package com.mpx.player.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.mpx.player.domain.settings.AppSettingsRepository
import java.io.File

/**
 * Shared storage volume and file path resolution helpers used across
 * MediaStore scanner components.
 */
internal class MediaStoreScannerHelper(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
) {
    /**
     * Resolves all active external storage volume names available on the device.
     * On Android 10+ (API 29+), includes [MediaStore.VOLUME_EXTERNAL_PRIMARY] and
     * unique volume UUIDs of mounted removable SD cards or USB OTG drives.
     * On Android 11+ (API 30+), also inspects [StorageManager.getStorageVolumes]
     * to guarantee all mounted volumes known to the system are covered.
     */
    fun getExternalVolumeNames(): Set<String> {
        val volumeNames = LinkedHashSet<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                volumeNames.addAll(MediaStore.getExternalVolumeNames(context))
            } catch (_: Exception) {
                // MediaStore provider may throw on early boot or restricted sandbox
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    volume.mediaStoreVolumeName?.let { volumeNames.add(it) }
                }
            } catch (_: Exception) {
                // Storage service access fallback
            }
        }
        return volumeNames
    }

    /**
     * Builds list of [Uri]s for querying video content across all external storage volumes.
     * Falls back to [MediaStore.Video.Media.EXTERNAL_CONTENT_URI] on API < 29 or when
     * volume discovery returns empty.
     */
    fun getVideoContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Video.Media.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * Builds list of [Uri]s for querying audio content across all external storage volumes.
     * Falls back to [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] on API < 29 or when
     * volume discovery returns empty.
     */
    fun getAudioContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Audio.Media.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * Builds list of [Uri]s for querying playlists across all external storage volumes.
     */
    fun getPlaylistContentUris(): List<Uri> {
        val volumeNames = getExternalVolumeNames()
        if (volumeNames.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = mutableListOf<Uri>()
            for (name in volumeNames) {
                try {
                    uris.add(MediaStore.Audio.Playlists.getContentUri(name))
                } catch (_: Exception) {}
            }
            if (uris.isNotEmpty()) return uris
        }
        return listOf(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)
    }

    /**
     * "Show hidden files" setting, read fresh per scan so a toggle change in
     * Settings applies to the next library refresh. Falls back to hiding
     * hidden files if settings cannot be read.
     */
    fun showHiddenFiles(): Boolean = try {
        settingsRepository.load().showHiddenFiles
    } catch (_: Exception) {
        false
    }

    /**
     * A file or folder counts as hidden when its own name or any parent
     * directory name starts with '.'. System cache directories
     * (/Android/data, /Android/obb) are excluded separately and always.
     */
    fun isHiddenPath(path: String, title: String = ""): Boolean =
        title.startsWith(".") ||
            path.split(File.separatorChar).any { it.startsWith(".") }

    /**
     * Issue #218: a path inside another app's private storage. These
     * directories are excluded from the library ALWAYS — regardless of
     * "Show all folders" and regardless of "Show hidden files". They used
     * to be excluded only on the non-show-all path, so turning show-all on
     * let other apps' private videos into the library and made them
     * playable. Keep the same substring match the old filters used, so
     * exactly the same paths are excluded as before on the normal path.
     */
    fun isSystemCachePath(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb")

    /**
     * Returns true if the path points to secondary/removable storage (e.g. physical SD card).
     */
    fun isRemovablePath(path: String): Boolean {
        if (path.isBlank()) return false
        // Primary emulated internal storage is /storage/emulated/0
        // Removable SD cards are typically mounted under /storage/XXXX-XXXX or /mnt/media_rw/XXXX-XXXX
        if (path.startsWith("/storage/emulated/")) return false
        if (path.startsWith("/storage/") || path.startsWith("/mnt/media_rw/")) return true

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                storageManager?.storageVolumes?.forEach { volume ->
                    if (volume.isRemovable) {
                        val uuid = volume.uuid
                        if (!uuid.isNullOrBlank() && path.contains(uuid, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Resolves storage root directories across primary internal storage and mounted SD cards.
     */
    fun getStorageRootDirectories(): List<File> {
        val roots = LinkedHashSet<File>()

        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists()) {
                roots.add(primary)
            }
        } catch (_: Exception) {}

        val emulated = File("/storage/emulated/0")
        if (emulated.exists()) {
            roots.add(emulated)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    volume.directory?.let { if (it.exists()) roots.add(it) }
                }
            } catch (_: Exception) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    val uuid = volume.uuid
                    if (!uuid.isNullOrBlank()) {
                        val sdDir = File("/storage/$uuid")
                        if (sdDir.exists()) roots.add(sdDir)
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (dir in extDirs) {
                if (dir != null) {
                    val full = dir.absolutePath
                    val androidIdx = full.indexOf("/Android")
                    if (androidIdx > 0) {
                        val rootPath = full.substring(0, androidIdx)
                        val rootFile = File(rootPath)
                        if (rootFile.exists()) roots.add(rootFile)
                    }
                }
            }
        } catch (_: Exception) {}

        return roots.toList()
    }
}
