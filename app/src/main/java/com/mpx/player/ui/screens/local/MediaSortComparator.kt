package com.mpx.player.ui.screens.local

import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import java.util.Locale

/**
 * Robust, production-grade sort comparator for media files, folders, and audio tracks.
 * Supports primary sort, secondary tie-break sort, reverse direction, natural numeric sorting,
 * case sensitivity, and file extension handling.
 */
object MediaSortComparator {

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    private fun parseFormattedSize(formatted: String): Long {
        if (formatted.isBlank()) return 0L
        val parts = formatted.trim().split(" ")
        if (parts.isEmpty()) return 0L
        val num = parts[0].toDoubleOrNull() ?: return 0L
        val unit = if (parts.size > 1) parts[1].uppercase(Locale.US) else ""
        return when {
            unit.startsWith("TB") -> (num * 1024L * 1024L * 1024L * 1024L).toLong()
            unit.startsWith("GB") -> (num * 1024L * 1024L * 1024L).toLong()
            unit.startsWith("MB") -> (num * 1024L * 1024L).toLong()
            unit.startsWith("KB") -> (num * 1024L).toLong()
            else -> num.toLong()
        }
    }

    /**
     * Compares two strings using natural numeric order (e.g. "Episode 2" comes before "Episode 10").
     */
    fun naturalCompare(
        s1: String,
        s2: String,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = false,
    ): Int {
        val str1 = if (ignoreExtension) stripExtension(s1) else s1
        val str2 = if (ignoreExtension) stripExtension(s2) else s2

        var i = 0
        var j = 0
        val len1 = str1.length
        val len2 = str2.length

        while (i < len1 && j < len2) {
            val c1 = str1[i]
            val c2 = str2[j]

            if (c1.isDigit() && c2.isDigit()) {
                val start1 = i
                while (i < len1 && str1[i].isDigit()) i++
                val num1 = str1.substring(start1, i)

                val start2 = j
                while (j < len2 && str2[j].isDigit()) j++
                val num2 = str2.substring(start2, j)

                val numCmp = compareNumericStrings(num1, num2)
                if (numCmp != 0) return numCmp
            } else {
                val charCmp = if (caseSensitive) {
                    c1.compareTo(c2)
                } else {
                    c1.lowercaseChar().compareTo(c2.lowercaseChar())
                }
                if (charCmp != 0) return charCmp
                i++
                j++
            }
        }
        return len1.compareTo(len2)
    }

    private fun compareNumericStrings(n1: String, n2: String): Int {
        val t1 = n1.trimStart('0')
        val t2 = n2.trimStart('0')
        if (t1.length != t2.length) {
            return t1.length.compareTo(t2.length)
        }
        val cmp = t1.compareTo(t2)
        if (cmp != 0) return cmp
        return n1.length.compareTo(n2.length)
    }

    /**
     * Constructs a Comparator for VideoFile with primary sort and secondary tie-break sort.
     */
    fun videoComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<VideoFile> = Comparator { a, b ->
        var cmp = compareVideoByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        // Primary tied: apply secondary sort if configured
        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareVideoByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        // Final stable tie-breaker: title then id
        val titleCmp = a.title.compareTo(b.title, ignoreCase = true)
        if (titleCmp != 0) titleCmp else a.id.compareTo(b.id)
    }

    private fun compareVideoByField(
        a: VideoFile,
        b: VideoFile,
        field: SortField,
        numeric: Boolean,
        caseSensitive: Boolean,
        ignoreExtension: Boolean,
    ): Int = when (field) {
        SortField.NAME -> {
            if (numeric) {
                naturalCompare(a.title, b.title, caseSensitive, ignoreExtension)
            } else {
                if (caseSensitive) a.title.compareTo(b.title)
                else a.title.lowercase(Locale.US).compareTo(b.title.lowercase(Locale.US))
            }
        }
        SortField.DATE -> b.dateModified.compareTo(a.dateModified)
        // MX combined key: date modified first, duration breaks ties.
        SortField.DATE_DURATION -> {
            val dateCmp = b.dateModified.compareTo(a.dateModified)
            if (dateCmp != 0) dateCmp else b.durationMs.compareTo(a.durationMs)
        }
        SortField.SIZE -> b.sizeBytes.compareTo(a.sizeBytes)
        SortField.DURATION -> b.durationMs.compareTo(a.durationMs)
        // MX combined key: resolution (pixel count) first, then frame rate
        // where known (0 when MediaStore has no frame-rate column), then width.
        SortField.RESOLUTION_FRAMERATE -> {
            val pixelsA = a.widthPx.toLong() * a.heightPx.toLong()
            val pixelsB = b.widthPx.toLong() * b.heightPx.toLong()
            val pixelsCmp = pixelsB.compareTo(pixelsA)
            if (pixelsCmp != 0) pixelsCmp
            else {
                val fpsCmp = b.frameRateFps.compareTo(a.frameRateFps)
                if (fpsCmp != 0) fpsCmp else b.widthPx.compareTo(a.widthPx)
            }
        }
        // MX combined key: status (recently added first), then file type
        // (extension), then location (full path).
        SortField.STATUS_TYPE_LOCATION -> {
            val statusCmp = b.isNew.compareTo(a.isNew)
            if (statusCmp != 0) statusCmp
            else {
                val typeCmp = fileExtension(a.path).compareTo(fileExtension(b.path))
                if (typeCmp != 0) typeCmp else a.path.compareTo(b.path)
            }
        }
        SortField.COUNT, SortField.NONE -> 0
    }

    private fun fileExtension(path: String): String {
        val name = path.substringAfterLast('/', path)
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase(Locale.US) else ""
    }

    /**
     * Constructs a Comparator for MediaFolder with primary sort and secondary tie-break sort.
     */
    fun folderComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
    ): Comparator<MediaFolder> = Comparator { a, b ->
        var cmp = compareFolderByField(a, b, primary, numeric, caseSensitive)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        // Primary tied: apply secondary sort if configured
        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareFolderByField(a, b, secondary, numeric, caseSensitive)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val nameCmp = a.name.compareTo(b.name, ignoreCase = true)
        if (nameCmp != 0) nameCmp else a.id.compareTo(b.id)
    }

    private fun compareFolderByField(
        a: MediaFolder,
        b: MediaFolder,
        field: SortField,
        numeric: Boolean,
        caseSensitive: Boolean,
    ): Int = when (field) {
        SortField.NAME -> {
            if (numeric) {
                naturalCompare(a.name, b.name, caseSensitive, ignoreExtension = false)
            } else {
                if (caseSensitive) a.name.compareTo(b.name)
                else a.name.lowercase(Locale.US).compareTo(b.name.lowercase(Locale.US))
            }
        }
        SortField.DATE -> b.lastModified.compareTo(a.lastModified)
        // MX combined key: date modified first, duration breaks ties.
        // Folder duration is the real summed duration of its videos.
        SortField.DATE_DURATION -> {
            val dateCmp = b.lastModified.compareTo(a.lastModified)
            if (dateCmp != 0) dateCmp else b.totalDurationMs.compareTo(a.totalDurationMs)
        }
        SortField.COUNT -> b.videoCount.compareTo(a.videoCount)
        SortField.SIZE -> parseFormattedSize(b.totalSizeFormatted).compareTo(parseFormattedSize(a.totalSizeFormatted))
        // Issue #70 fix: was lastModified (a copy of DATE). Now the folder's
        // real total video duration, summed at scan time.
        SortField.DURATION -> b.totalDurationMs.compareTo(a.totalDurationMs)
        // Folders carry no resolution: falls through to the name tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // MX combined key: status (recently updated first), then location.
        SortField.STATUS_TYPE_LOCATION -> {
            val statusCmp = b.isNew.compareTo(a.isNew)
            if (statusCmp != 0) statusCmp else a.path.compareTo(b.path)
        }
        SortField.NONE -> 0
    }

    /**
     * Constructs a Comparator for AudioTrack with primary sort and secondary tie-break sort.
     */
    fun audioTrackComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<AudioTrack> = Comparator { a, b ->
        var cmp = compareAudioByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareAudioByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val titleCmp = a.title.compareTo(b.title, ignoreCase = true)
        if (titleCmp != 0) titleCmp else a.id.compareTo(b.id)
    }

    private fun compareAudioByField(
        a: AudioTrack,
        b: AudioTrack,
        field: SortField,
        numeric: Boolean,
        caseSensitive: Boolean,
        ignoreExtension: Boolean,
    ): Int = when (field) {
        SortField.NAME -> {
            if (numeric) {
                naturalCompare(a.title, b.title, caseSensitive, ignoreExtension)
            } else {
                if (caseSensitive) a.title.compareTo(b.title)
                else a.title.lowercase(Locale.US).compareTo(b.title.lowercase(Locale.US))
            }
        }
        SortField.DATE -> b.id.compareTo(a.id)
        // Audio tracks carry no date: id recency first, duration breaks ties.
        SortField.DATE_DURATION -> {
            val idCmp = b.id.compareTo(a.id)
            if (idCmp != 0) idCmp else b.durationMs.compareTo(a.durationMs)
        }
        SortField.SIZE, SortField.DURATION -> b.durationMs.compareTo(a.durationMs)
        // Audio carries no resolution: falls through to the title tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // MX combined key: file type (extension), then location (path).
        SortField.STATUS_TYPE_LOCATION -> {
            val typeCmp = fileExtension(a.path).compareTo(fileExtension(b.path))
            if (typeCmp != 0) typeCmp else a.path.compareTo(b.path)
        }
        SortField.COUNT, SortField.NONE -> 0
    }
}
