package com.mpx.player.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.Locale

/**
 * Finds subtitle files sitting next to a video, MX-style.
 *
 * MX searches the folder that contains the video (plus any extra
 * "Subtitle Folder" the user configured) for subtitle files with the
 * same name, and loads the match by itself. mpv's own `sub-auto=fuzzy`
 * cannot do this in MPx: the player hands mpv a content:// URI, so mpv
 * has no folder to scan — the app has to find the sibling file and
 * hand it to mpv with sub-add, exactly like a manual pick.
 *
 * The scan lists the folder with java.io.File, the same assumption the
 * folder enumeration in MediaStoreRepository already makes. On
 * Android 11+ that listing only sees subtitle files when the app holds
 * All files access ([hasAllFilesAccess]) — without it the folder looks
 * empty and the scan quietly finds nothing; the subtitle page explains
 * that and offers the grant instead of failing silently.
 */
object SubtitleSiblingFinder {

    /** Subtitle extensions MPx accepts (same set SubtitleUtils validates). */
    val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "webvtt", "ass", "ssa")

    /** One entry of the in-app subtitle browser. */
    data class BrowserEntry(
        val file: File,
        val isDirectory: Boolean,
    )

    /**
     * True when the app may list any folder's files. Below Android 11
     * there is no All files access gate (READ_EXTERNAL_STORAGE covers
     * it); on 11+ it needs MANAGE_EXTERNAL_STORAGE, granted by the user
     * in Settings.
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    fun isSubtitleFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase(Locale.US)
        return ext in SUBTITLE_EXTENSIONS
    }

    /**
     * Subtitle files in [videoPath]'s folder whose name matches the
     * video's name: an exact stem match (`movie.srt` for `movie.mp4`)
     * or the stem plus a language tag (`movie.en.srt`, `movie.ar.srt`).
     * Best match first: the exact stem match, then language-suffixed
     * files alphabetically. Empty when the folder cannot be listed.
     * The caller loads them worst-first so the best match is added
     * last — the engine selects the last sub-added track.
     */
    fun findSiblings(videoPath: String): List<File> {
        val videoFile = File(videoPath)
        val stem = videoFile.nameWithoutExtension
        if (stem.isBlank()) return emptyList()
        val dir = videoFile.parentFile ?: return emptyList()
        val files = try {
            dir.listFiles() ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val lowerStem = stem.lowercase(Locale.US)
        return files
            .filter { isSubtitleFile(it) }
            .filter { candidate ->
                val candidateStem = candidate.nameWithoutExtension.lowercase(Locale.US)
                candidateStem == lowerStem || candidateStem.startsWith("$lowerStem.")
            }
            .sortedWith(
                compareBy<File> { it.nameWithoutExtension.lowercase(Locale.US) != lowerStem }
                    .thenBy { it.name.lowercase(Locale.US) },
            )
    }

    /**
     * Contents of [dir] for the in-app subtitle browser: subfolders
     * first, then subtitle files, each alphabetical. Null when the
     * folder cannot be listed at all (no access), so the caller can
     * tell "empty folder" apart from "cannot see this folder".
     */
    fun listSubtitleDir(dir: File): List<BrowserEntry>? {
        val files = try {
            dir.listFiles() ?: return null
        } catch (_: Exception) {
            return null
        }
        val dirs = files
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase(Locale.US) }
            .map { BrowserEntry(it, isDirectory = true) }
        val subs = files
            .filter { isSubtitleFile(it) }
            .sortedBy { it.name.lowercase(Locale.US) }
            .map { BrowserEntry(it, isDirectory = false) }
        return dirs + subs
    }
}
