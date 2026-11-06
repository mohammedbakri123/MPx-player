package com.mpx.player.ui.screens.local

import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.Playlist
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
     * Issue #242: compares two media ids numerically when both are plain
     * numbers. MediaStore ids are numeric strings, and lexicographic
     * comparison orders "9" before "10" (and "100" before "99") — wrong
     * recency once ids pass single digits. Falls back to plain string
     * comparison when either side is not numeric, which keeps the #217
     * ordering for custom playlists: "custom:<id>" never parses as a
     * Long, so it still sorts after device ids, exactly as before.
     */
    private fun compareIds(a: String, b: String): Int {
        val numA = a.toLongOrNull()
        val numB = b.toLongOrNull()
        return if (numA != null && numB != null) numA.compareTo(numB)
        else a.compareTo(b)
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
        if (titleCmp != 0) titleCmp else compareIds(a.id, b.id)
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
        if (nameCmp != 0) nameCmp else compareIds(a.id, b.id)
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
        if (titleCmp != 0) titleCmp else compareIds(a.id, b.id)
    }

    /**
     * Constructs a Comparator for AudioAlbum with primary sort and secondary tie-break sort.
     * #185 (Albums tab): same field semantics as [audioTrackComparator].
     * Albums carry no duration: SIZE / DURATION sort by track count (most
     * tracks first) — the album's "size". DATE is id recency, like tracks.
     */
    fun audioAlbumComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<AudioAlbum> = Comparator { a, b ->
        var cmp = compareAlbumByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareAlbumByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val titleCmp = a.title.compareTo(b.title, ignoreCase = true)
        if (titleCmp != 0) titleCmp else compareIds(a.id, b.id)
    }

    private fun compareAlbumByField(
        a: AudioAlbum,
        b: AudioAlbum,
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
        SortField.DATE -> compareIds(b.id, a.id)
        // Albums carry no date: id recency first, track count breaks ties.
        SortField.DATE_DURATION -> {
            val idCmp = compareIds(b.id, a.id)
            if (idCmp != 0) idCmp else b.trackCount.compareTo(a.trackCount)
        }
        // Albums carry no duration: track count is the album's size.
        SortField.SIZE, SortField.DURATION -> b.trackCount.compareTo(a.trackCount)
        // Albums carry no resolution: falls through to the title tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // No file type / location for albums: artist, then title tie-break.
        SortField.STATUS_TYPE_LOCATION -> a.artist.compareTo(b.artist, ignoreCase = true)
        SortField.COUNT, SortField.NONE -> 0
    }

    /**
     * Constructs a Comparator for AudioArtist with primary sort and secondary tie-break sort.
     * #185 (Artists tab): same field semantics as [audioAlbumComparator].
     * Artists carry no duration: SIZE / DURATION sort by track count (most
     * tracks first) — the artist's "size". DATE is id recency, like tracks.
     */
    fun audioArtistComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<AudioArtist> = Comparator { a, b ->
        var cmp = compareArtistByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareArtistByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val nameCmp = a.name.compareTo(b.name, ignoreCase = true)
        if (nameCmp != 0) nameCmp else compareIds(a.id, b.id)
    }

    private fun compareArtistByField(
        a: AudioArtist,
        b: AudioArtist,
        field: SortField,
        numeric: Boolean,
        caseSensitive: Boolean,
        ignoreExtension: Boolean,
    ): Int = when (field) {
        SortField.NAME -> {
            if (numeric) {
                naturalCompare(a.name, b.name, caseSensitive, ignoreExtension)
            } else {
                if (caseSensitive) a.name.compareTo(b.name)
                else a.name.lowercase(Locale.US).compareTo(b.name.lowercase(Locale.US))
            }
        }
        SortField.DATE -> compareIds(b.id, a.id)
        // Artists carry no date: id recency first, track count breaks ties.
        SortField.DATE_DURATION -> {
            val idCmp = compareIds(b.id, a.id)
            if (idCmp != 0) idCmp else b.trackCount.compareTo(a.trackCount)
        }
        // Artists carry no duration: track count is the artist's size.
        SortField.SIZE, SortField.DURATION -> b.trackCount.compareTo(a.trackCount)
        // Artists carry no resolution: falls through to the name tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // No file type / location for artists: falls through to the name tie-break.
        SortField.STATUS_TYPE_LOCATION -> 0
        SortField.COUNT, SortField.NONE -> 0
    }

    /**
     * Constructs a Comparator for Playlist with primary sort and secondary tie-break sort.
     * #185 (Playlists tab): same field semantics as [audioAlbumComparator].
     * Playlists carry no duration: SIZE / DURATION sort by item count (most
     * items first) — the playlist's "size". COUNT is the playlist's own
     * item count, so it sorts the same way. DATE is id recency, like
     * albums/artists: ids are MediaStore numeric strings for device
     * playlists and "custom:<id>" for custom ones, compared numerically
     * with a string fallback (issue #242) — so custom playlists still
     * sort after device ones, as #217 documented.
     */
    fun audioPlaylistComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<Playlist> = Comparator { a, b ->
        var cmp = comparePlaylistByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = comparePlaylistByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val titleCmp = a.title.compareTo(b.title, ignoreCase = true)
        if (titleCmp != 0) titleCmp else compareIds(a.id, b.id)
    }

    private fun comparePlaylistByField(
        a: Playlist,
        b: Playlist,
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
        SortField.DATE -> compareIds(b.id, a.id)
        // Playlists carry no date: id recency first, item count breaks ties.
        SortField.DATE_DURATION -> {
            val idCmp = compareIds(b.id, a.id)
            if (idCmp != 0) idCmp else b.itemCount.compareTo(a.itemCount)
        }
        // Playlists carry no duration: item count is the playlist's size.
        SortField.SIZE, SortField.DURATION, SortField.COUNT -> b.itemCount.compareTo(a.itemCount)
        // Playlists carry no resolution: falls through to the title tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // No file type / location for playlists: the subtitle groups the
        // kinds ("Custom Playlist" / "Video Playlist" / "" for a device
        // list), then the title tie-break orders inside each group.
        SortField.STATUS_TYPE_LOCATION -> a.subtitle.compareTo(b.subtitle, ignoreCase = true)
        SortField.NONE -> 0
    }


    /**
     * Constructs a Comparator for AudioFolder with primary sort and secondary tie-break sort.
     * #185 (Folders tab): same field semantics as [audioArtistComparator],
     * except DATE has nothing to sort by — folders carry no date, and
     * AudioFolder.id is a path hash, not recency, so DATE / DATE_DURATION
     * fall through to the name tie-break. Folders carry no size or
     * duration either: SIZE / DURATION / COUNT sort by track count (most
     * tracks first) — the folder's "size". STATUS_TYPE_LOCATION has no
     * status or file type for folders, so it sorts by location (path).
     */
    fun audioFolderComparator(
        primary: SortField,
        secondary: SortField = SortField.NAME,
        reverse: Boolean = false,
        secondaryReverse: Boolean = false,
        numeric: Boolean = true,
        caseSensitive: Boolean = false,
        ignoreExtension: Boolean = true,
    ): Comparator<AudioFolder> = Comparator { a, b ->
        var cmp = compareAudioFolderByField(a, b, primary, numeric, caseSensitive, ignoreExtension)
        if (reverse) cmp = -cmp
        if (cmp != 0) return@Comparator cmp

        if (secondary != SortField.NONE && secondary != primary) {
            var secCmp = compareAudioFolderByField(a, b, secondary, numeric, caseSensitive, ignoreExtension)
            if (secondaryReverse) secCmp = -secCmp
            if (secCmp != 0) return@Comparator secCmp
        }

        val nameCmp = a.name.compareTo(b.name, ignoreCase = true)
        if (nameCmp != 0) nameCmp else compareIds(a.id, b.id)
    }

    private fun compareAudioFolderByField(
        a: AudioFolder,
        b: AudioFolder,
        field: SortField,
        numeric: Boolean,
        caseSensitive: Boolean,
        ignoreExtension: Boolean,
    ): Int = when (field) {
        SortField.NAME -> {
            if (numeric) {
                naturalCompare(a.name, b.name, caseSensitive, ignoreExtension)
            } else {
                if (caseSensitive) a.name.compareTo(b.name)
                else a.name.lowercase(Locale.US).compareTo(b.name.lowercase(Locale.US))
            }
        }
        // Folders carry no date (and id is a path hash, not recency):
        // falls through to the name tie-break.
        SortField.DATE, SortField.DATE_DURATION -> 0
        // Folders carry no size or duration: track count is the folder's size.
        SortField.SIZE, SortField.DURATION, SortField.COUNT -> b.trackCount.compareTo(a.trackCount)
        // Folders carry no resolution: falls through to the name tie-break.
        SortField.RESOLUTION_FRAMERATE -> 0
        // No status or file type for folders: location (full path) only.
        SortField.STATUS_TYPE_LOCATION -> a.path.compareTo(b.path)
        SortField.NONE -> 0
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
        SortField.DATE -> compareIds(b.id, a.id)
        // Audio tracks carry no date: id recency first, duration breaks ties.
        SortField.DATE_DURATION -> {
            val idCmp = compareIds(b.id, a.id)
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
