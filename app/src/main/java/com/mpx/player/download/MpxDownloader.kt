package com.mpx.player.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.data.local.entity.DownloadTaskEntity
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.ExternalSubtitle
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

/** Status of one in-progress (or finished) download row. */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    DONE,
    FAILED,
    CANCELED,
}

/**
 * Which stage a running download is in (issue #350). Shown on the
 * Download tab so merging / post-processing does not look like a
 * stalled download.
 */
enum class DownloadPhase {
    /** The getInfo phase is still running — no bytes yet. */
    FETCHING_INFO,
    /** Bytes are moving (yt-dlp `[download]` / aria2c lines, or the HTTP fallback). */
    DOWNLOADING,
    /** ffmpeg is merging the video+audio streams (`[Merger]`). */
    MERGING,
    /** A post-processor is running (`[ExtractAudio]`, `[EmbedSubtitle]`, `[SponsorBlock]`, …). */
    POST_PROCESSING,
}

/** One download row shown on the Download tab while it is running or finished. */
data class ActiveDownload(
    val id: String,
    val url: String,
    val title: String,
    val status: DownloadStatus,
    /** 0..100, -1 while the size is still unknown. */
    val progress: Float = -1f,
    val etaSeconds: Long = -1L,
    /** Issue #350: bytes downloaded so far, -1 while unknown. */
    val downloadedBytes: Long = -1L,
    /** Issue #350: total bytes when known, -1 while unknown. */
    val totalBytes: Long = -1L,
    /** Issue #350: download speed in bytes/s, -1 while unknown. */
    val speedBytesPerSec: Long = -1L,
    /** Issue #350: which stage the download is in. */
    val phase: DownloadPhase = DownloadPhase.FETCHING_INFO,
    /** The finished file's public location once published (#344); null until DONE. */
    val file: DownloadedFile? = null,
    val error: String? = null,
    /**
     * Issue #346: this row finished because yt-dlp had already recorded
     * the video in the download archive — nothing was downloaded this
     * time. The tab shows "Already downloaded" instead of "Download
     * complete" for it. Only meaningful with status DONE.
     */
    val alreadyDownloaded: Boolean = false,
    /** Issue #337: per-download Netscape cookie file, or null for a plain download. Deleted when the download exits. */
    val cookiesFile: File? = null,
    /** Issue #340: audio-only download — extract best audio as mp3 instead of video. */
    val audioOnly: Boolean = false,
    /** Issue #341: also fetch subtitles (en,ar) at download time, embedded + sidecar. */
    val subtitles: Boolean = false,
    /**
     * Issue #383: the per-video format selector the user picked in the
     * format sheet, e.g. "137+140" or "bestvideo+140" (Seal's
     * `TaskFactory` shape: the chosen format ids joined with "+",
     * "bestvideo"/"bestaudio" filling a side left on Best). Null means
     * no per-video choice — the global format sorter applies instead.
     * Only set on video downloads; the sheet never appears for
     * audio-only or playlist downloads.
     */
    val formatId: String? = null,
    /**
     * Issue #386: the clips the user added in the format sheet, in
     * seconds (Seal's `VideoClip` shape). Empty means the whole
     * video. Only set on video downloads; each clip becomes
     * `--download-sections "*<start>-<end>"` and comes out as its
     * own file.
     */
    val clips: List<VideoClip> = emptyList(),
    /**
     * Issue #387: split this download by chapter (Seal's
     * `FormatConfig.splitByChapter`). Set from the switch in the
     * format sheet; each chapter becomes `--split-chapters` output
     * in a subfolder named after the video. Only set on video
     * downloads — a video without chapters downloads as one file.
     */
    val splitByChapter: Boolean = false,
    /**
     * Issue #388: rename before download (Seal's
     * `DownloadPreferences.newTitle`). The new title from the
     * rename field in the format sheet; null means no rename —
     * the video keeps its own title. Only set on video downloads.
     */
    val newTitle: String? = null,
)

/**
 * One video clip (issue #386): start and end in seconds, same shape
 * as Seal's `VideoClip` (JunkFood02/Seal, GPL-3.0).
 */
data class VideoClip(val start: Int = 0, val end: Int = 0)

/**
 * One finished file in the completed list (issue #344).
 *
 * [uri] is either a `content://` MediaStore URI (public file in
 * `Downloads/MPx/`, [isPublic] true) or an absolute filesystem path
 * (app-private file, [isPublic] false — the publish to the public
 * folder failed, or the device is below API 29 where the MediaStore
 * Downloads collection does not exist). The player opens both: file
 * paths directly, `content://` URIs through MpvFdOpener (#150).
 * [lastModified] is ms since epoch (MediaStore reports seconds).
 */
data class DownloadedFile(
    val uri: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isPublic: Boolean,
) {
    /** Extension without the dot, lowercase; "" when the name has none. */
    val extension: String
        get() = if ('.' in name) name.substringAfterLast('.').lowercase() else ""

    /** Name without the extension, for display and titles. */
    val nameWithoutExtension: String
        get() = if ('.' in name) name.substringBeforeLast('.') else name
}

/** One entry of a playlist (issue #345). */
data class PlaylistEntry(
    val url: String,
    val title: String,
    /** Duration in seconds, or null when yt-dlp does not report one. */
    val durationSeconds: Long?,
)

/** A URL that resolves to a playlist (issue #345). */
data class PlaylistInfo(
    val title: String,
    val entries: List<PlaylistEntry>,
)

enum class DownloaderInitState {
    IDLE,
    INITIALIZING,
    READY,
    FAILED,
}

/** Outcome of a yt-dlp update attempt (issue #333). */
enum class YtDlpUpdateState {
    IDLE,
    UPDATING,
    /** The update ran and installed a newer yt-dlp. */
    DONE,
    /** Already on the latest yt-dlp for the channel. */
    UP_TO_DATE,
    FAILED,
}

/** Result of tapping Done in the in-app sign-in WebView (issue #337). */
enum class CookieImportOutcome {
    /** Cookies were imported and the download started with them. */
    IMPORTED,
    /** The WebView store has no cookies for this URL's host. */
    NO_COOKIES,
    /** The import itself threw (WebView store unreadable, disk error). */
    FAILED,
}

/** Result of a manual yt-dlp update request (issue #333). */
enum class YtDlpUpdateOutcome {
    UPDATED,
    UP_TO_DATE,
    FAILED,
    /** A download is running, or an update is already in flight. */
    BUSY,
}

/**
 * Issue #350: what one yt-dlp stdout line says about the download.
 * The library's progress callback (see [parseProgressLine]) passes
 * the raw line as its 3rd parameter — the library itself only
 * extracts progress % and ETA from it.
 */
private data class ProgressLine(
    val downloadedBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = -1L,
    /** A stage this line announces, or null when it says nothing about the stage. */
    val phase: DownloadPhase? = null,
    /** An ffmpeg progress line (`frame=… size=…`) — post-processing is running even without a header. */
    val isFfmpegLine: Boolean = false,
)

// yt-dlp `[download]` lines (sizes are binary units, `~` marks an approximate total):
//   [download]  45.2% of 120.50MiB at  2.41MiB/s ETA 00:27
//   [download]  45.2% of ~120.50MiB at Unknown speed ETA Unknown
//   [download] 100% of 120.50MiB in 00:05 at 24.10MiB/s   (final line: `in` instead of ETA)
//   [download]  12.34KiB at  2.41MiB/s            (total unknown)
private val YTDLP_DOWNLOAD_LINE =
    Regex("""\[download\]\s+(\d+(?:\.\d+)?)%\s+of\s+~?([0-9.]+(?:KiB|MiB|GiB|TiB|B))(?:\s+in\s+\d+(?::\d+){1,2})?\s+at\s+([0-9.]+(?:KiB|MiB|GiB|TiB|B)/s|Unknown speed)""")
private val YTDLP_DOWNLOAD_NO_TOTAL_LINE =
    Regex("""\[download\]\s+([0-9.]+(?:KiB|MiB|GiB|TiB|B))(?:\s+in\s+\d+(?::\d+){1,2})?\s+at\s+([0-9.]+(?:KiB|MiB|GiB|TiB|B)/s|Unknown speed)""")

// aria2c lines (used when `--downloader libaria2c.so` is active, #339):
//   [#a1b2c3 120MiB/450MiB(26%) CN:8 DL:2.4MiB ETA:27s]
private val ARIA2C_LINE =
    Regex("""\[#\w{6}\s+([0-9.]+(?:KiB|MiB|GiB|TiB|B))/([0-9.]+(?:KiB|MiB|GiB|TiB|B))\((\d+(?:\.\d+)?)%\)[^\]]*?DL:([0-9.]+(?:KiB|MiB|GiB|TiB|B))""")

private val YTDLP_SIZE_TOKEN = Regex("""^([0-9.]+)(KiB|MiB|GiB|TiB|KB|MB|GB|TB|B)$""")

/** Post-processor headers that are not a merge — all post-processing. */
private val POST_PROCESSOR_HEADERS = listOf(
    "[ExtractAudio]", "[VideoConvertor]", "[EmbedSubtitle]", "[SponsorBlock]",
    "[Metadata]", "[EmbedThumbnail]", "[Thumbnails]", "[FixupM4a]",
    "[MoveFiles]", "[FFmpeg]",
)

/** Issue #350: yt-dlp/aria2c size tokens ("120.50MiB", "850KiB", "512B") to bytes, or -1 when unparseable. */
private fun parseYtDlpSize(token: String): Long {
    val match = YTDLP_SIZE_TOKEN.matchEntire(token.trim()) ?: return -1L
    val value = match.groupValues[1].toDoubleOrNull() ?: return -1L
    val mult = when (match.groupValues[2]) {
        "B" -> 1L
        "KiB" -> 1024L
        "MiB" -> 1024L * 1024
        "GiB" -> 1024L * 1024 * 1024
        "TiB" -> 1024L * 1024 * 1024 * 1024
        "KB" -> 1000L
        "MB" -> 1000L * 1000
        "GB" -> 1000L * 1000 * 1000
        "TB" -> 1000L * 1000 * 1000 * 1000
        else -> return -1L
    }
    return (value * mult).toLong()
}

private fun parseYtDlpSpeed(token: String): Long =
    if (token == "Unknown speed") -1L else parseYtDlpSize(token.removeSuffix("/s"))

/**
 * Issue #350: parse one raw yt-dlp stdout line into sizes, speed,
 * and stage. Verified against the youtubedl-android 0.18.1 AAR:
 * `StreamProcessExtractor.processOutputLine` invokes the progress
 * callback with `(getProgress(line), getEta(line), line)` — the 3rd
 * parameter IS the raw line, fired for every line (on `\r` or `\n`).
 * The library's own patterns only match `[download] …% … ETA mm:ss`
 * (progress), `[#xxxxxx …(n%)…]` (aria2c), and `size=…` (ffmpeg,
 * forced to 99%) — everything else is passed through untouched, so
 * sizes, speed, and the merge/post-processing stage have to be
 * parsed here.
 */
private fun parseProgressLine(line: String): ProgressLine {
    if (line.startsWith("[Merger]")) return ProgressLine(phase = DownloadPhase.MERGING)
    if (POST_PROCESSOR_HEADERS.any { line.startsWith(it) }) {
        return ProgressLine(phase = DownloadPhase.POST_PROCESSING)
    }
    // ffmpeg progress lines during merge/convert (`frame=… size=…`) —
    // the download phase is over; the caller keeps the current phase
    // unless it is still DOWNLOADING, in which case this is
    // post-processing (a header was missed or never printed).
    if (line.startsWith("frame=") && line.contains("size=")) {
        return ProgressLine(isFfmpegLine = true)
    }
    YTDLP_DOWNLOAD_LINE.find(line)?.let { match ->
        val percent = match.groupValues[1].toFloatOrNull() ?: return@let
        val total = parseYtDlpSize(match.groupValues[2])
        val speed = parseYtDlpSpeed(match.groupValues[3])
        return ProgressLine(
            // Double math: Float would round a ~100 MB total by whole
            // bytes and could leave downloaded < total at 100%.
            downloadedBytes = if (total > 0) (total * percent.toDouble() / 100.0).toLong() else -1L,
            totalBytes = total,
            speedBytesPerSec = speed,
            phase = DownloadPhase.DOWNLOADING,
        )
    }
    YTDLP_DOWNLOAD_NO_TOTAL_LINE.find(line)?.let { match ->
        return ProgressLine(
            downloadedBytes = parseYtDlpSize(match.groupValues[1]),
            speedBytesPerSec = parseYtDlpSpeed(match.groupValues[2]),
            phase = DownloadPhase.DOWNLOADING,
        )
    }
    ARIA2C_LINE.find(line)?.let { match ->
        val total = parseYtDlpSize(match.groupValues[2])
        return ProgressLine(
            downloadedBytes = parseYtDlpSize(match.groupValues[1]),
            totalBytes = total,
            speedBytesPerSec = parseYtDlpSpeed(match.groupValues[4] + "/s"),
            phase = DownloadPhase.DOWNLOADING,
        )
    }
    return ProgressLine()
}

/**
 * Downloader engine (issue #324): Seal's technique — the youtubedl-android
 * library (yausername/youtubedl-android 0.18.1, `:library` only), which
 * bundles CPython + a lazy-extractors yt-dlp build as native .so files.
 * The plain yt-dlp_linux_aarch64 standalone binary does NOT run on Android
 * (glibc vs Bionic) — that route was checked and rejected.
 *
 * The `:ffmpeg` artifact is bundled too (issue #336, 139 MB AAR across
 * 4 ABIs — the arm64 release APK grows by its ~36 MB arm64 slice):
 * with ffmpeg, yt-dlp merges
 * the best separate video+audio streams (1080p+ on YouTube) instead of
 * capping at the best single file (typically 720p). No `-f` is passed;
 * Seal's `-S` format sorter picks the streams (see FormatSorter.kt).
 *
 * The `:aria2c` artifact is bundled too (issue #339, ~20 MB AAR —
 * the arm64 release APK grows by its ~6.5 MB arm64 slice): when its
 * init succeeds, downloads use `--downloader libaria2c.so` for
 * multi-connection downloads; otherwise `--concurrent-fragments 8`
 * (Seal's default) speeds up fragmented (HLS/DASH) videos instead.
 * A failed aria2c init never fails the engine init.
 *
 * Downloads still land in the app-private external Downloads dir
 * (`Android/data/com.mpx.player/files/Download/MPx/`) — no storage
 * permission is needed there, and raw File writes to the public
 * Download/ folder are blocked by scoped storage on API 30+ without
 * MANAGE_EXTERNAL_STORAGE. On success the finished file is moved
 * into the public `Downloads/MPx/` folder via a MediaStore insert
 * (issue #344, API 29+): the private copy is deleted, the public
 * entry is scanned into the media library so galleries, file
 * managers and other players see it, and it survives uninstall.
 * The completed list is a MediaStore query of that folder plus any
 * private leftovers (a failed publish keeps the private file), so
 * it is persistent by construction. The *queue* is persisted
 * separately in Room (`download_tasks`, issue #342): unfinished
 * tasks survive process death and are restored on start.
 *
 * Application-scoped singleton (like PlaybackManager): a download keeps
 * running when the user leaves the tab, and the screen just observes the
 * flows. yt-dlp runs in its own subprocess per download; the progress
 * callback arrives on the library's stdout-reader thread, and StateFlow
 * updates are thread-safe.
 *
 * Honest limits: on-device yt-dlp is increasingly fragile upstream for
 * YouTube (yt-dlp deprecated its built-in JS interpreter in 2025.11.12;
 * curl_cffi TLS impersonation is documented broken on Android; POT
 * tokens). Direct links and many other sites work; some YouTube videos
 * may fail. Errors are surfaced, never swallowed.
 *
 * yt-dlp auto-update (issue #333): the bundled yt-dlp rots as
 * extractors change, so [maybeAutoUpdateYtDlp] checks for a newer one
 * on app launch (Seal's YtdlpUpdater gates: idle, network, weekly
 * interval) and the Download tab offers a manual "Update yt-dlp".
 * The update itself is Seal's `UpdateUtil.updateYtDlp()` copied with
 * attribution — see [updateYtDlp].
 */
class MpxDownloader private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    // Same pattern as PlaybackManager: read the shared settings blob
    // directly (channel, auto-update flag, interval, last-update time).
    private val settingsRepository = SharedPrefsAppSettingsRepository(appContext)

    /**
     * Issue #379: the public folder for finished downloads, from the
     * downloadDirectory setting (MediaStore RELATIVE_PATH form — no leading
     * slash, no trailing slash; the query form gains one). "MPx" is the old
     * fixed folder, so existing installs keep their files. Read fresh on
     * every use, like the other settings above — a folder change applies
     * to the next download and the next listing refresh.
     */
    private fun publicDownloadsRelativePath(): String =
        "Download/${sanitizeDownloadFolderName(settingsRepository.load().downloadDirectory)}"

    private fun publicDownloadsRelativePathSlash(): String =
        "${publicDownloadsRelativePath()}/"
    // Issue #342: the persisted queue (same MpxDatabase every other
    // feature uses — no second database). Building the Room object
    // does no I/O; every DAO call below runs on the IO scope.
    private val downloadTaskDao = MpxDatabase.getInstance(appContext).downloadTaskDao()
    // Issue #346: the download history, same database. One row per
    // finished download; the History section observes the flow below.
    private val downloadHistoryDao = MpxDatabase.getInstance(appContext).downloadHistoryDao()

    /**
     * Issue #346: yt-dlp's `--download-archive` file (Seal's
     * `FileUtil.getArchiveFile`, `filesDir/archive.txt`, JunkFood02/Seal,
     * GPL-3.0). One line per finished download, `"<extractor> <id>"`,
     * appended by yt-dlp itself on success — re-downloading an archived
     * video is skipped instead of duplicated. Internal files dir, not
     * cache: it must survive app restarts and updates. yt-dlp creates
     * it on first use; the pre-check below only reads it when it exists.
     */
    private val archiveFile: File = File(appContext.filesDir, "ytdlp-archive.txt")

    /**
     * App-private external Downloads/MPx — staging only since #344
     * (falls back to internal files). yt-dlp downloads here, then a
     * finished file is moved into the public `Downloads/MPx/` folder
     * via MediaStore and the private copy deleted; the private dir
     * keeps in-progress work and publish failures only.
     */
    val downloadsDir: File = run {
        val external = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val base = if (external != null) File(external, "MPx") else File(appContext.filesDir, "downloads/MPx")
        base.apply { mkdirs() }
    }

    /**
     * Partial downloads live here (issue #334): yt-dlp writes
     * `<title>.<ext>.part` into the temp dir and moves the finished file
     * into [downloadsDir] on completion, so a `.part` file can never be
     * mistaken for a finished download. Pattern from Seal
     * (JunkFood02/Seal, GPL-3.0 — `DownloadUtil.kt` `-P temp:` plus
     * `FileUtil.getExternalTempDir()`); Seal uses one shared tmp dir,
     * MPx gives each download its own subdir (named by the download id)
     * so a cancel can only ever delete that download's own partials.
     */
    private val tempDir: File = File(downloadsDir, "tmp").apply { mkdirs() }

    private val _initState = MutableStateFlow(DownloaderInitState.IDLE)
    val initState: StateFlow<DownloaderInitState> = _initState.asStateFlow()

    /**
     * Whether the bundled aria2c native binary extracted cleanly
     * (issue #339). Set in [ensureInitialized]; a failed init leaves
     * it false and downloads fall back to `--concurrent-fragments`
     * instead of failing the whole engine init.
     */
    @Volatile
    private var aria2cAvailable = false

    private val _activeDownloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownload>> = _activeDownloads.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val downloadedFiles: StateFlow<List<DownloadedFile>> = _downloadedFiles.asStateFlow()

    /** Issue #346: the download history, newest first, for the History section. */
    private val _downloadHistory = MutableStateFlow<List<DownloadHistoryEntity>>(emptyList())
    val downloadHistory: StateFlow<List<DownloadHistoryEntity>> = _downloadHistory.asStateFlow()

    /** Last yt-dlp update outcome (issue #333); IDLE until the first attempt. */
    private val _updateState = MutableStateFlow(YtDlpUpdateState.IDLE)
    val updateState: StateFlow<YtDlpUpdateState> = _updateState.asStateFlow()

    /** Installed yt-dlp version once known (issue #333); "" until then. */
    private val _ytdlpVersion = MutableStateFlow(settingsRepository.load().ytdlpVersion)
    val ytdlpVersion: StateFlow<String> = _ytdlpVersion.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        refreshFiles()
        // Issue #337: drop orphaned per-download cookie files left by
        // a kill between import and download start. Only the staging
        // files are touched — per-download temp subdirs are untouched.
        try {
            tempDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("cookies-") && it.name.endsWith(".txt") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {
        }
        // Issue #342: a kill leaves per-download temp subdirs behind
        // (the #334 finally block never ran). Nothing is downloading
        // yet — this init runs at singleton construction, before any
        // startDownload can happen — so every subdir here is stale:
        // wipe them and let runDownload recreate its own on demand.
        try {
            tempDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
        // Issue #344: files finished before this change are still in
        // the private dir — move them to the public folder once, so
        // nothing is stranded there. Off the calling thread: the
        // publish copies bytes through MediaStore.
        scope.launch { migratePrivateDownloadsToPublic() }
        // Issue #342: restore the persisted queue on a background
        // thread — Room must not run on the calling thread.
        scope.launch { restorePersistedTasks() }
        // Issue #346: load the download history for the History
        // section; it refreshes on every insert/delete below.
        scope.launch { refreshHistory() }
    }

    /** Issue #346: reload the history flow from Room. Best-effort — a failed read leaves the last good list. */
    private suspend fun refreshHistory() {
        try {
            _downloadHistory.value = downloadHistoryDao.getAll()
        } catch (_: Exception) {
        }
    }

    /**
     * Issue #346: record one finished download in the history. Called
     * at every DONE site with the published file and whatever video
     * info the getInfo phase captured (nulls for direct-link HTTP
     * downloads, which have no extractor id). The title is read from
     * the live row, not the [item] snapshot — the getInfo phase updates
     * it after the download starts. A failed insert never breaks the
     * download: the row is already DONE by then.
     */
    private fun recordHistory(
        id: String,
        url: String,
        videoId: String?,
        extractor: String?,
        uploader: String?,
        thumbnailUrl: String?,
        published: DownloadedFile,
    ) {
        scope.launch {
            try {
                val title = _activeDownloads.value.firstOrNull { it.id == id }?.title
                    ?: published.nameWithoutExtension
                downloadHistoryDao.insert(
                    DownloadHistoryEntity(
                        videoId = videoId,
                        extractor = extractor,
                        url = url,
                        title = title,
                        uploader = uploader,
                        thumbnailUrl = thumbnailUrl,
                        fileUri = published.uri,
                        fileName = published.name,
                        fileSize = published.size,
                        isPublic = published.isPublic,
                        completedAt = System.currentTimeMillis(),
                    ),
                )
                refreshHistory()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Issue #346: forget one history entry. The file is NOT deleted —
     * history is a record, and the file may already be gone (or still
     * in the completed list). Called from the History section.
     */
    fun deleteHistoryEntry(id: Long) {
        scope.launch {
            try {
                downloadHistoryDao.deleteById(id)
                refreshHistory()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Issue #346: the finished file of a history entry, or null when it
     * is gone (deleted outside the app, or the MediaStore entry was
     * removed). A public file is checked through the ContentResolver —
     * the URI may be stale — a private one through File. The returned
     * [DownloadedFile] plays exactly like a completed-list row.
     */
    fun historyFileIfExists(entry: DownloadHistoryEntity): DownloadedFile? {
        val file = entry.toDownloadedFile()
        val exists = try {
            if (entry.isPublic) {
                appContext.contentResolver.openFileDescriptor(Uri.parse(entry.fileUri), "r")
                    ?.use { true } ?: false
            } else {
                File(entry.fileUri).isFile
            }
        } catch (_: Exception) {
            false
        }
        return if (exists) file else null
    }

    /**
     * Issue #346: has yt-dlp already recorded this video in the
     * archive? Seal's pre-check (`DownloadUtil.kt:~703`,
     * JunkFood02/Seal, GPL-3.0) compares the whole
     * `"<extractor> <id>"` line; MPx matches the id and accepts
     * either the extractor name or the extractor key as the first
     * token — yt-dlp's archive line uses one of the two, and the
     * library exposes both (`VideoInfo.extractor` / `extractorKey`).
     * A null id (direct links) never matches.
     */
    private fun isArchived(extractor: String?, extractorKey: String?, videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        val archive = try {
            archiveFile.takeIf { it.isFile }?.readText().orEmpty()
        } catch (_: Exception) {
            return false
        }
        return archive.lineSequence().any { line ->
            val parts = line.trim().split(' ').filter { it.isNotBlank() }
            parts.size >= 2 && parts.last() == videoId &&
                (parts[0] == extractor || parts[0] == extractorKey)
        }
    }

    /**
     * Issue #346: yt-dlp's own archive-skip signal in the download
     * output — "[download] <id> has already been recorded in the
     * archive". The pre-check above should have caught it, but the
     * archive may have been written by an older build or a different
     * extractor spelling; the stdout check is the backstop.
     */
    private fun isArchiveSkipped(stdout: String): Boolean =
        stdout.contains("has already been recorded in the archive")

    /**
     * Issue #346: finish a download that yt-dlp skipped as already
     * archived — never FAILED. With the file still there (latest
     * history row for the video id, else the URL), the row completes
     * with that file; without it, the row completes with the
     * already-downloaded note (see [ActiveDownload.alreadyDownloaded]).
     * No new history row: nothing was downloaded this time.
     */
    private suspend fun handleArchiveSkip(id: String, url: String, videoId: String?) {
        val entry = try {
            if (videoId != null) downloadHistoryDao.getLatestByVideoId(videoId) else null
        } catch (_: Exception) {
            null
        } ?: try {
            downloadHistoryDao.getLatestByUrl(url)
        } catch (_: Exception) {
            null
        }
        val existingFile = entry?.let { historyFileIfExists(it) }
        if (existingFile != null) {
            updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = existingFile) }
        } else {
            updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, alreadyDownloaded = true) }
        }
        refreshFiles()
    }

    /**
     * Issue #342: rebuild the queue from Room after a restart. Rows
     * still DOWNLOADING when the process died are marked FAILED with
     * an "interrupted" error — the retry button in the Download tab
     * re-downloads them from scratch (yt-dlp has no resume for a
     * killed process here). QUEUED rows resume where they left off
     * and FAILED rows stay retryable. DONE/CANCELED rows should not
     * exist (they are deleted on transition) and are dropped
     * defensively. A restored task never carries cookies (#337 staging
     * files are deleted on exit and never persisted), and a file that
     * already landed before the kill still shows in the completed
     * list even though its row comes back FAILED — the retry is a
     * harmless duplicate in that case.
     */
    private suspend fun restorePersistedTasks() {
        val restored = try {
            downloadTaskDao.getAll()
        } catch (_: Exception) {
            return
        }
        val interrupted = appContext.getString(R.string.download_interrupted)
        val items = mutableListOf<ActiveDownload>()
        for (row in restored) {
            when (row.status) {
                DownloadStatus.DOWNLOADING.name -> {
                    val failed = row.copy(status = DownloadStatus.FAILED.name, error = interrupted)
                    try {
                        downloadTaskDao.upsert(failed)
                    } catch (_: Exception) {
                    }
                    items += failed.toActiveDownload()
                }
                DownloadStatus.QUEUED.name, DownloadStatus.FAILED.name -> {
                    items += row.toActiveDownload()
                }
                else -> {
                    try {
                        downloadTaskDao.deleteById(row.id)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        if (items.isNotEmpty()) {
            // Append, never replace: a download started between
            // construction and this restore must not be lost.
            _activeDownloads.update { it + items }
            doYourWork()
        }
    }

    /** One-shot lazy init: extracts Python + the yt-dlp script on first use. */
    suspend fun ensureInitialized(): Boolean {
        if (_initState.value == DownloaderInitState.READY) return true
        initMutex.withLock {
            if (_initState.value == DownloaderInitState.READY) return true
            // No early return on FAILED: a failed init (e.g. storage hiccup
            // on first run) is retried on the next download attempt.
            _initState.value = DownloaderInitState.INITIALIZING
            return try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(appContext)
                    // Issue #336: ffmpeg ships in its own artifact
                    // (Seal calls FFmpeg.init at App start, App.kt:92);
                    // without this init yt-dlp cannot merge separate
                    // video+audio streams even with the artifact bundled.
                    FFmpeg.init(appContext)
                    // Issue #339: aria2c ships in its own artifact
                    // (Seal calls Aria2c.init at App start, App.kt:93).
                    // Its failure must NOT fail the engine init: yt-dlp
                    // still downloads fine without it, just on its own
                    // downloader — the request below falls back to
                    // `--concurrent-fragments` when this is false.
                    aria2cAvailable = try {
                        Aria2c.init(appContext)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                _initState.value = DownloaderInitState.READY
                true
            } catch (e: CancellationException) {
                // The caller went away (e.g. screen disposed mid-init) —
                // not a failed init, so the next attempt retries cleanly.
                _initState.value = DownloaderInitState.IDLE
                throw e
            } catch (e: Throwable) {
                // Issue #356: init can fail with an Error too
                // (ExceptionInInitializerError when R8 stripped a class
                // the library inits reflectively). A failed init must
                // degrade to FAILED, never escape and kill the app.
                // CancellationException is still rethrown above.
                _initState.value = DownloaderInitState.FAILED
                false
            }
        }
    }

    /** True while a download is queued or running (issue #333 Idle gate). */
    fun hasActiveDownloads(): Boolean {
        return _activeDownloads.value.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
    }

    /**
     * yt-dlp update (issue #333), adapted from Seal's
     * `UpdateUtil.updateYtDlp()` (JunkFood02/Seal, GPL-3.0,
     * `app/src/main/java/com/junkfood/seal/util/UpdateUtil.kt:56-73`).
     * The library API is identical in 0.18.1; only the settings access
     * changed (Seal reads its PreferenceUtil keys, MPx reads the shared
     * AppSettings blob). Calls the library's `updateYoutubeDL` on the
     * configured channel, records the new version on DONE, and records
     * the check time on any non-throwing return (DONE or
     * ALREADY_UP_TO_DATE — a failure records nothing, so a broken
     * network retries on the next launch instead of waiting out the
     * interval). Throws on failure; the caller decides what to show.
     */
    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (settingsRepository.load().ytdlpUpdateChannel) {
                    "NIGHTLY" -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = appContext, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(appContext)?.let { version ->
                            saveYtDlpVersion(version)
                        }
                    }
                    saveYtDlpLastUpdateTime(System.currentTimeMillis())
                }
        }

    /**
     * Auto-update on launch (issue #333), Seal's `YtdlpUpdater` gates
     * (`app/src/main/java/com/junkfood/seal/ui/page/YtdlpUpdater.kt:20-46`):
     * skip while a download runs (Idle gate), skip without network,
     * skip until the interval elapsed since the last check. When
     * auto-update is off, still run once while the installed version
     * is unknown, so the Download tab can show it. Silent — the outcome
     * lands in [updateState]/[ytdlpVersion] only. Never blocks init or
     * downloads: init runs first (fast no-op after the first run), and
     * the library's update call does not hold any lock `execute` needs
     * (`execute` is not synchronized; only `init`/`updateYoutubeDL`
     * are, and they never overlap — the Idle gate plus [updateMutex]
     * make sure of that).
     */
    suspend fun maybeAutoUpdateYtDlp() {
        val settings = settingsRepository.load()
        if (!settings.ytdlpAutoUpdate && settings.ytdlpVersion.isNotEmpty()) return
        if (hasActiveDownloads()) return
        if (!isNetworkAvailable()) return
        val now = System.currentTimeMillis()
        if (now < settings.ytdlpLastUpdateTimeMs + settings.ytdlpUpdateIntervalMs) return
        if (!ensureInitialized()) return
        runUpdateAttempt()
    }

    /**
     * Manual "Update yt-dlp" (issue #333): the Download tab calls this.
     * BUSY while a download runs or an update is already in flight;
     * FAILED when the engine cannot start or the update throws.
     */
    suspend fun updateYtDlpNow(): YtDlpUpdateOutcome {
        if (_updateState.value == YtDlpUpdateState.UPDATING) return YtDlpUpdateOutcome.BUSY
        if (hasActiveDownloads()) return YtDlpUpdateOutcome.BUSY
        if (!ensureInitialized()) return YtDlpUpdateOutcome.FAILED
        return runUpdateAttempt()
    }

    /** One update attempt under [updateMutex]; re-checks the Idle gate inside. */
    private suspend fun runUpdateAttempt(): YtDlpUpdateOutcome = updateMutex.withLock {
        // A download may have started while init was running — the
        // Idle gate is checked again here, inside the mutex.
        if (hasActiveDownloads()) return YtDlpUpdateOutcome.BUSY
        _updateState.value = YtDlpUpdateState.UPDATING
        try {
            when (updateYtDlp()) {
                YoutubeDL.UpdateStatus.DONE -> {
                    _updateState.value = YtDlpUpdateState.DONE
                    YtDlpUpdateOutcome.UPDATED
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                    _updateState.value = YtDlpUpdateState.UP_TO_DATE
                    YtDlpUpdateOutcome.UP_TO_DATE
                }
                null -> {
                    _updateState.value = YtDlpUpdateState.FAILED
                    YtDlpUpdateOutcome.FAILED
                }
            }
        } catch (_: Exception) {
            _updateState.value = YtDlpUpdateState.FAILED
            YtDlpUpdateOutcome.FAILED
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Issue #380: whether the active network is metered (mobile data or a
     * metered WiFi) — Seal's `CELLULAR_DOWNLOAD` gate uses the same
     * `ConnectivityManager.isActiveNetworkMetered`. False when there is no
     * connectivity manager (nothing to gate — the download fails on no
     * network anyway).
     */
    fun isActiveNetworkMetered(): Boolean {
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        return connectivity.isActiveNetworkMetered
    }

    private fun saveYtDlpVersion(version: String) {
        _ytdlpVersion.value = version
        val settings = settingsRepository.load()
        settingsRepository.save(settings.copy(ytdlpVersion = version))
    }

    private fun saveYtDlpLastUpdateTime(nowMs: Long) {
        val settings = settingsRepository.load()
        settingsRepository.save(settings.copy(ytdlpLastUpdateTimeMs = nowMs))
    }

    /**
     * The User-Agent the in-app sign-in WebView sends (issue #337):
     * the same string yt-dlp sends, so the site sees one consistent
     * identity between the login and the download (Seal's pattern).
     */
    fun loadUserAgent(): String = settingsRepository.load().downloadUserAgent

    /**
     * Imports the cookies MPx's own in-app sign-in WebView set for
     * [rawUrl]'s host and starts the download with them (issue #337).
     * The import is per-download: the cookie file is written for this
     * URL only, passed to that download's yt-dlp requests, and deleted
     * when it exits — nothing is persisted and nothing is imported
     * without the user signing in and tapping Done. A blank URL
     * reports NO_COOKIES (the button is disabled then, so this is
     * unreachable from the UI).
     */
    suspend fun importCookiesAndStart(
        rawUrl: String,
        audioOnly: Boolean = false,
        subtitles: Boolean = false,
    ): CookieImportOutcome {
        val url = CookieImporter.normalizeUrl(rawUrl) ?: return CookieImportOutcome.NO_COOKIES
        val stagingFile = File(tempDir, "cookies-${UUID.randomUUID()}.txt")
        return try {
            if (!CookieImporter.importForUrl(url, stagingFile)) {
                stagingFile.delete()
                CookieImportOutcome.NO_COOKIES
            } else {
                startDownload(url, cookiesFile = stagingFile, audioOnly = audioOnly, subtitles = subtitles)
                CookieImportOutcome.IMPORTED
            }
        } catch (_: Exception) {
            stagingFile.delete()
            CookieImportOutcome.FAILED
        }
    }

    /**
     * Probes [rawUrl] for playlist content (issue #345), adapted from
     * Seal's `getPlaylistOrVideoInfo` (JunkFood02/Seal, GPL-3.0,
     * `DownloadUtil.kt:87-124`): `--flat-playlist --dump-single-json`
     * with the same fail-fast options as the info fetch. Returns the
     * playlist when the URL resolves to one with at least one usable
     * entry, null otherwise — a single video, an empty playlist, a
     * fetch failure, or a URL yt-dlp cannot read all return null, and
     * the caller falls back to the plain single download. The
     * library's `VideoInfo` mapper has no `entries` field, so the JSON
     * is parsed here with org.json (platform API, no new dependency)
     * instead of the library's getInfo. Cancellation propagates, so a
     * cancelled probe never silently falls back to a download.
     */
    suspend fun fetchPlaylistInfo(rawUrl: String): PlaylistInfo? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null
        if (!ensureInitialized()) return null
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("-o", "%(title).200B")
            addOption("-R", "1")
            addOption("--socket-timeout", "5")
            val settings = settingsRepository.load()
            if (settings.downloadUserAgent.isNotBlank()) addOption("--user-agent", settings.downloadUserAgent)
            // Issue #347: same network flags as the info fetch — a
            // proxied / rate-limited network needs them on the probe too.
            addOptionsForNetworkFlags(settings)
        }
        val out = try {
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString()).out
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        return parsePlaylistInfo(out)
    }

    /**
     * Fetches the full video info for the format sheet (issue #383):
     * the same getInfo runDownload does, with the same fail-fast
     * options (`-R 1` + `--socket-timeout 5`, user-agent, network
     * flags) — but no cookies, because the sheet appears before any
     * sign-in. The library mapper exposes the `formats` array
     * (verified against the 0.18.1 AAR: VideoInfo.getFormats() →
     * VideoFormat with formatId/ext/vcodec/acodec/width/height/fps/
     * tbr/abr/fileSize), which is what the sheet lists. Null on any
     * failure — the caller falls back to a plain download with the
     * global settings, so a video that needs cookies (or any other
     * info failure) still downloads exactly as before. Cancellation
     * still propagates.
     */
    suspend fun fetchVideoInfoForFormat(rawUrl: String): VideoInfo? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null
        if (!ensureInitialized()) return null
        val settings = settingsRepository.load()
        val request = YoutubeDLRequest(url).apply {
            addOption("-R", "1")
            addOption("--socket-timeout", "5")
            if (settings.downloadUserAgent.isNotBlank()) addOption("--user-agent", settings.downloadUserAgent)
            addOptionsForNetworkFlags(settings)
        }
        return try {
            withContext(Dispatchers.IO) { YoutubeDL.getInstance().getInfo(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a `--flat-playlist --dump-single-json` result (issue
     * #345): the `_type` field tells a playlist from a single video
     * (Seal's `PlaylistResult`/`VideoInfo` split, same idea, parsed
     * with org.json instead of kotlinx.serialization). Entries with
     * no title (unavailable videos) or no usable URL are skipped;
     * null when the JSON is not a playlist or no entry survives.
     */
    private fun parsePlaylistInfo(json: String): PlaylistInfo? {
        return try {
            val root = JSONObject(json)
            if (root.optString("_type") != "playlist") return null
            val entries = root.optJSONArray("entries") ?: return null
            val items = mutableListOf<PlaylistEntry>()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val title = entry.optString("title").trim()
                if (title.isBlank()) continue
                val entryUrl = playlistEntryUrl(entry) ?: continue
                val duration = if (entry.has("duration") && !entry.isNull("duration")) {
                    entry.optDouble("duration")
                        .takeIf { it.isFinite() && it >= 0 }
                        ?.toLong()
                } else {
                    null
                }
                items += PlaylistEntry(url = entryUrl, title = title, durationSeconds = duration)
            }
            if (items.isEmpty()) return null
            PlaylistInfo(title = root.optString("title").trim(), entries = items)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A flat-playlist entry's video URL (issue #345): the `url` field
     * when yt-dlp provides it (YouTube gives the full watch URL);
     * otherwise the YouTube watch URL built from the id (`ie_key`
     * "Youtube"). Other extractors without a url cannot be addressed
     * reliably and are skipped.
     */
    private fun playlistEntryUrl(entry: JSONObject): String? {
        entry.optString("url").trim().takeIf { it.isNotBlank() }?.let { return it }
        if (entry.optString("ie_key") == "Youtube") {
            val id = entry.optString("id").trim()
            if (id.isNotBlank()) return "https://www.youtube.com/watch?v=$id"
        }
        return null
    }

    /**
     * Enqueues several downloads at once (issue #345): the playlist
     * picker's selected entries. Each URL becomes its own row and the
     * #342 queue cap (3 at a time) applies — [doYourWork] starts them
     * as slots free up.
     */
    fun startDownloads(
        urls: List<String>,
        audioOnly: Boolean = false,
        subtitles: Boolean = false,
    ) {
        for (url in urls) {
            startDownload(url, audioOnly = audioOnly, subtitles = subtitles)
        }
    }

    /**
     * Enqueues a download (issue #342). No-op for a blank URL. The row
     * starts QUEUED and is persisted immediately, then [doYourWork]
     * starts it as soon as a slot is free — at most
     * [MAX_CONCURRENT_DOWNLOADS] yt-dlp processes run at once.
     */
    fun startDownload(
        rawUrl: String,
        cookiesFile: File? = null,
        audioOnly: Boolean = false,
        subtitles: Boolean = false,
        formatId: String? = null,
        clips: List<VideoClip> = emptyList(),
        splitByChapter: Boolean = false,
        newTitle: String? = null,
    ) {
        val url = rawUrl.trim()
        if (url.isBlank()) return
        val id = UUID.randomUUID().toString()
        val item = ActiveDownload(
            id = id,
            url = url,
            title = url,
            status = DownloadStatus.QUEUED,
            cookiesFile = cookiesFile,
            audioOnly = audioOnly,
            subtitles = subtitles,
            formatId = formatId,
            clips = clips,
            splitByChapter = splitByChapter,
            newTitle = newTitle,
        )
        _activeDownloads.update { it + item }
        persistTask(item)
        doYourWork()
    }

    /**
     * Issue #342: the queue pump, adapted from Seal's
     * `DownloaderV2.doYourWork()` (JunkFood02/Seal, GPL-3.0,
     * `download/DownloaderV2.kt:219-236` — cap at 3, pick the next
     * waiting task). Seal starts one task per call and relies on state
     * changes to re-trigger; MPx loops so freed slots fill immediately
     * even with several tasks queued. Called after every enqueue,
     * every job completion, every cancel, and once after restoring
     * persisted tasks. The jobs lock is held while selecting and
     * launching so [cancelDownload] cannot mark a row CANCELED in
     * between — a canceled row must never start.
     */
    private fun doYourWork() {
        var launched = false
        synchronized(jobs) {
            while (jobs.size < MAX_CONCURRENT_DOWNLOADS) {
                val next = _activeDownloads.value
                    .firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
                val job = scope.launch { runDownload(next) }
                jobs[next.id] = job
                launched = true
                job.invokeOnCompletion {
                    synchronized(jobs) { jobs.remove(next.id) }
                    // A cancel that landed before runDownload's first
                    // state write leaves the row QUEUED forever — the
                    // job is gone, so finish it as CANCELED here.
                    // Any other terminal state is already set by
                    // runDownload itself, so this is a no-op for it.
                    val current = _activeDownloads.value.firstOrNull { it.id == next.id }
                    if (current?.status == DownloadStatus.QUEUED) {
                        updateItem(next.id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
                    }
                    doYourWork()
                }
            }
        }
        // Issue #343: a launched job gets a foreground service
        // (progress notification + process priority). The service
        // stops itself when the queue empties, so the caller never
        // needs a matching stop.
        if (launched) DownloadService.start(appContext)
    }

    /**
     * Cancels a download (yt-dlp process + the coroutine). A row still
     * QUEUED has no job yet — it is marked CANCELED directly under the
     * jobs lock, so [doYourWork] cannot pick it up in between (it
     * never occupied a slot, so no slot is freed).
     */
    fun cancelDownload(id: String) {
        val job = synchronized(jobs) {
            val running = jobs[id]
            if (running == null) {
                updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            }
            running
        }
        if (job != null) {
            job.cancel()
            // Best effort: also kill the yt-dlp process directly (no-op when
            // the download is still in the getInfo phase or the HTTP fallback).
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Deletes a downloaded file and refreshes the list (issue #344).
     * A public file is deleted through its MediaStore entry — no
     * permission is needed for a file the app created; a private
     * leftover is deleted as a plain file.
     */
    fun deleteFile(file: DownloadedFile) {
        scope.launch {
            try {
                if (file.isPublic) {
                    appContext.contentResolver.delete(Uri.parse(file.uri), null, null)
                } else {
                    File(file.uri).delete()
                }
            } catch (_: Exception) {
            }
            refreshFiles()
        }
    }

    /**
     * Re-scans the completed list, newest first (issue #344): the
     * public `Downloads/MPx/` folder via a MediaStore query, plus any
     * app-private leftovers (a failed publish keeps the private file,
     * and the startup migration moves pre-#344 files out — so the
     * private dir only ever holds in-progress work and publish
     * failures). Subtitle sidecars are not media and never listed.
     */
    fun refreshFiles() {
        val files = queryPublicDownloadEntries()
            .filter { isMediaFileName(it.name) }
            .toMutableList()
        downloadsDir.listFiles()
            ?.filter { it.isFile && isMediaFile(it) && !it.name.endsWith(".part") }
            ?.map {
                DownloadedFile(
                    uri = it.absolutePath,
                    name = it.name,
                    size = it.length(),
                    lastModified = it.lastModified(),
                    isPublic = false,
                )
            }
            ?.let { files += it }
        _downloadedFiles.value = files.sortedByDescending { it.lastModified }
    }

    /**
     * Every entry MPx put in the public `Downloads/MPx/` folder
     * (issue #344): one MediaStore.Downloads query on RELATIVE_PATH,
     * newest first. Files the app created are visible without any
     * storage permission; other apps' files in the same folder are
     * not returned to us, so this is exactly our own downloads.
     * Media files and subtitle sidecars alike — callers filter by
     * name. An empty list on any failure (provider error, API < 29),
     * never null.
     */
    private fun queryPublicDownloadEntries(): List<DownloadedFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
        )
        val entries = mutableListOf<DownloadedFile>()
        try {
            appContext.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(publicDownloadsRelativePathSlash()),
                "${MediaStore.Downloads.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    if (idCol < 0 || nameCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    if (name.isBlank()) continue
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val modified = if (dateCol >= 0) cursor.getLong(dateCol).coerceAtLeast(0L) else 0L
                    entries += DownloadedFile(
                        uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id,
                        ).toString(),
                        name = name,
                        size = size,
                        lastModified = modified * 1000L,
                        isPublic = true,
                    )
                }
            }
        } catch (_: Exception) {
        }
        return entries
    }

    /**
     * Sidecar subtitles for [file] (issue #341, moved here from
     * DownloadScreen in #344 so both storage locations are covered):
     * a public video's sidecars are MediaStore entries in the same
     * `Downloads/MPx/` folder (`<title>.<lang>.srt|.vtt`, e.g.
     * `video.en.vtt`); a private leftover's sidecars sit next to it
     * in the private dir. Either way the URIs ride along as
     * [ExternalSubtitle]s and the engine sub-adds and pins them via
     * the #332 flow — `content://` subtitles go through MpvFdOpener
     * like `content://` video. SRT/VTT are the two formats MPx's own
     * subtitle validation claims (SubtitleUtils); ASS is mpv-native
     * too and yt-dlp writes it for some sites. Sorted by name for a
     * stable order (`.ar` before `.en`).
     */
    fun findSiblingSubtitles(file: DownloadedFile): List<ExternalSubtitle> {
        val base = file.nameWithoutExtension
        val prefix = "$base."
        val siblings: List<DownloadedFile> = if (file.isPublic) {
            queryPublicDownloadEntries()
                .filter { it.name.startsWith(prefix) && it.extension in SUBTITLE_EXTENSIONS }
        } else {
            val dir = File(file.uri).parentFile ?: return emptyList()
            try {
                dir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(prefix) && it.extension.lowercase() in SUBTITLE_EXTENSIONS }
                    ?.map {
                        DownloadedFile(
                            uri = it.absolutePath,
                            name = it.name,
                            size = it.length(),
                            lastModified = it.lastModified(),
                            isPublic = false,
                        )
                    }
                    ?: emptyList()
            } catch (_: Exception) {
                return emptyList()
            }
        }
        return siblings.sortedBy { it.name }.map {
            ExternalSubtitle(
                uri = it.uri,
                name = it.name,
                mimeType = when (it.extension) {
                    "srt" -> "application/x-subrip"
                    "ass", "ssa" -> "text/x-ssa"
                    else -> "text/vtt"
                },
            )
        }
    }

    /**
     * Moves a finished private file into the public `Downloads/MPx/`
     * folder (issue #344) and returns how the completed list should
     * represent it. The video's subtitle sidecars move with it, so
     * [findSiblingSubtitles] still finds them. On any failure — or
     * below API 29, where the MediaStore Downloads collection does
     * not exist — the private file is kept and returned as the
     * fallback, so a failed publish never loses a download.
     */
    private fun publishFinishedFile(privateFile: File): DownloadedFile {
        val published = publishToPublicDownloads(privateFile)
        if (published.isPublic) {
            publishSiblingSubtitles(privateFile)
            publishSiblingThumbnail(privateFile)
        }
        return published
    }

    /**
     * One file into the public Downloads folder (issue #344):
     * MediaStore insert into the Downloads collection with
     * RELATIVE_PATH `Download/MPx` — scoped storage, API 29+, no
     * permission is needed for files the app itself creates, and
     * explicitly NOT Seal's `MANAGE_EXTERNAL_STORAGE` +
     * `requestLegacyExternalStorage` route. IS_PENDING hides the
     * half-copied file from other apps until the bytes are in, then
     * the entry is scanned into the media library (see
     * [scanPublicFile]). The private copy is deleted only after the
     * bytes are safely in MediaStore. Returns the public entry, or
     * the private file as fallback on any failure. Never throws.
     */
    private fun publishToPublicDownloads(
        privateFile: File,
        scanIntoLibrary: Boolean = true,
    ): DownloadedFile {
        val private = DownloadedFile(
            uri = privateFile.absolutePath,
            name = privateFile.name,
            size = privateFile.length(),
            lastModified = privateFile.lastModified(),
            isPublic = false,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return private
        val mimeType = mimeTypeFor(privateFile.name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, privateFile.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, publicDownloadsRelativePath())
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = try {
            appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
            null
        } ?: return private
        // 1. Copy the bytes. Failure: drop the entry, keep the private file.
        try {
            appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(privateFile).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("no output stream")
        } catch (_: Exception) {
            try {
                appContext.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            return private
        }
        // 2. Un-hide the entry. Failure: drop the entry, keep the private file.
        try {
            val clearPending = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            appContext.contentResolver.update(uri, clearPending, null, null)
        } catch (_: Exception) {
            try {
                appContext.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            return private
        }
        // 3. Read back what MediaStore actually stored — it uniquifies
        // the display name on collision ("name (1).mp4"). Best-effort:
        // the private file's values are the fallback.
        var actualName = privateFile.name
        var actualSize = privateFile.length()
        var actualModified = privateFile.lastModified()
        try {
            appContext.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
                    if (nameCol >= 0) cursor.getString(nameCol)?.takeIf { it.isNotBlank() }?.let { actualName = it }
                    if (sizeCol >= 0) cursor.getLong(sizeCol).takeIf { it > 0L }?.let { actualSize = it }
                    if (dateCol >= 0) cursor.getLong(dateCol).takeIf { it > 0L }?.let { actualModified = it * 1000L }
                }
            }
        } catch (_: Exception) {
        }
        // 4. The bytes are in MediaStore now — the private copy dies.
        try {
            privateFile.delete()
        } catch (_: Exception) {
        }
        // 5. Scan, so galleries / file managers / other players see
        // the file (a Downloads-collection entry alone is not in the
        // Video/Audio collections). Best-effort.
        if (scanIntoLibrary) scanPublicFile(actualName, mimeType)
        return DownloadedFile(
            uri = uri.toString(),
            name = actualName,
            size = actualSize,
            lastModified = actualModified,
            isPublic = true,
        )
    }

    /**
     * Moves the subtitle sidecars yt-dlp wrote next to [video] into
     * the public folder too (issue #344, #341): same base name, same
     * folder, so [findSiblingSubtitles] still finds them after the
     * video moved. Best-effort per file — a sidecar that fails to
     * publish stays private next to nothing (its video is gone), so
     * it is deleted rather than stranded. Never scanned (see
     * [scanPublicFile] — subtitles are not media).
     */
    private fun publishSiblingSubtitles(video: File) {
        val base = video.nameWithoutExtension
        val prefix = "$base."
        val sidecars = try {
            downloadsDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) && it.extension.lowercase() in SUBTITLE_EXTENSIONS }
                ?: emptyList()
        } catch (_: Exception) {
            return
        }
        for (sidecar in sidecars) {
            val published = publishToPublicDownloads(sidecar, scanIntoLibrary = false)
            if (!published.isPublic) {
                try {
                    sidecar.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Issue #385: the thumbnail sidecar. `--write-thumbnail` +
     * `--convert-thumbnails png` (Seal's createThumbnail) writes
     * `<title>.png` next to the video in the private downloads dir;
     * it rides to the public folder with the video, exactly like a
     * subtitle sidecar — same basename match, same no-scan publish
     * (a cover file is not library media). Without the toggle there
     * is no png, so this finds nothing and does nothing.
     */
    private fun publishSiblingThumbnail(video: File) {
        val base = video.nameWithoutExtension
        val prefix = "$base."
        val thumbnails = try {
            downloadsDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) && it.extension.lowercase() == "png" }
                ?: emptyList()
        } catch (_: Exception) {
            return
        }
        for (thumbnail in thumbnails) {
            val published = publishToPublicDownloads(thumbnail, scanIntoLibrary = false)
            if (!published.isPublic) {
                try {
                    thumbnail.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Scans one published file into the media library (issue #344),
     * adapted from Seal's `FileUtil.scanFileToMediaLibraryPostDownload`
     * (JunkFood02/Seal, GPL-3.0 — `FileUtil.kt:107-118`): Seal scans
     * every file in its download dir matching the title; MPx scans
     * just the one finished file. The scan runs in the system
     * MediaScanner process, which can read the public path — a
     * Downloads-collection entry alone never lands in the
     * Video/Audio collections, so without this galleries, file
     * managers and other players would not see the file. Subtitle
     * sidecars are never scanned (Seal drops them from its scan list
     * too — they are not media).
     */
    private fun scanPublicFile(publicName: String, mimeType: String) {
        try {
            val publicPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "${publicDownloadsRelativePath()}/$publicName",
            ).absolutePath
            MediaScannerConnection.scanFile(appContext, arrayOf(publicPath), arrayOf(mimeType), null)
        } catch (_: Exception) {
        }
    }

    /** MIME type for a MediaStore insert, from the file extension. */
    private fun mimeTypeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "mp4", "mov" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            "ts", "m2ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "vtt", "webvtt" -> "text/vtt"
            else -> "application/octet-stream"
        }
    }

    /**
     * Issue #344: files finished before this change still sit in the
     * private dir — move them to the public folder once, so nothing
     * is stranded there. Runs in the IO scope at singleton
     * construction. The list is snapshotted first: a download
     * starting later (restored QUEUED rows) writes its own files
     * after this snapshot and publishes them itself on success, so
     * the migration can never grab an in-progress file. A `.part`
     * file is never finished and is skipped (the temp-dir cleanup
     * above already wiped stale temp subdirs).
     */
    private suspend fun migratePrivateDownloadsToPublic() {
        val leftovers = try {
            downloadsDir.listFiles()
                ?.filter { it.isFile && isMediaFile(it) && !it.name.endsWith(".part") }
                ?: emptyList()
        } catch (_: Exception) {
            return
        }
        if (leftovers.isEmpty()) return
        for (file in leftovers) {
            try {
                publishFinishedFile(file)
            } catch (_: Exception) {
            }
        }
        refreshFiles()
    }

    /**
     * Issue #342: every row change flows through here, so persistence
     * rides along: only a *status* change writes to Room (progress
     * ticks do not — they would hammer the DB and a restart marks a
     * mid-download row FAILED anyway). DONE and CANCELED rows are
     * deleted (the finished file is in the completed list, a canceled
     * row has no action); QUEUED, DOWNLOADING and FAILED rows are
     * upserted. A failed write is swallowed — persistence is
     * best-effort and must never break the download itself.
     */
    private fun updateItem(id: String, transform: (ActiveDownload) -> ActiveDownload) {
        var previous: ActiveDownload? = null
        var updated: ActiveDownload? = null
        _activeDownloads.update { list ->
            list.map {
                if (it.id == id) {
                    previous = it
                    transform(it).also { updated = it }
                } else it
            }
        }
        val next = updated ?: return
        if (previous?.status != next.status) persistTask(next)
    }

    /** Issue #342: one status write, preserving the queue position. */
    private fun persistTask(item: ActiveDownload) {
        scope.launch {
            try {
                when (item.status) {
                    DownloadStatus.DONE, DownloadStatus.CANCELED ->
                        downloadTaskDao.deleteById(item.id)
                    else -> {
                        // REPLACE would reset createdAt and move the
                        // task to the back of the queue — keep the
                        // stored value when the row already exists.
                        val createdAt = downloadTaskDao.getById(item.id)?.createdAt
                            ?: System.currentTimeMillis()
                        downloadTaskDao.upsert(
                            DownloadTaskEntity.fromActiveDownload(item, createdAt = createdAt),
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Issue #340: audio-only options, adapted from Seal's
     * `addOptionsForAudioDownloads` (DownloadUtil.kt:559-604,
     * JunkFood02/Seal, GPL-3.0). Seal's full version also handles
     * subtitles, an explicit format id, and metadata embedding prefs;
     * MPx keeps the convert branch only: `-x` extracts the best audio
     * and `--audio-format mp3` converts it with the bundled ffmpeg
     * (#336) — mp3 plays everywhere, including MPx's own player.
     * `--embed-metadata` tags the file with the video's title/uploader.
     * No `-f` and no video sorter: with `-x`, yt-dlp's default format
     * selection already picks the best audio stream.
     */
    private fun YoutubeDLRequest.addOptionsForAudioDownloads(): YoutubeDLRequest =
        apply {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--embed-metadata")
        }

    /**
     * Issue #341 + #382: subtitle options, Seal's video-download subtitle
     * block (DownloadUtil.kt:452-468, JunkFood02/Seal, GPL-3.0). #341 kept
     * the fixed core; #382 adds the rest: `--sub-langs` from the language
     * setting (blank falls back to "en,ar"); `--write-auto-subs` with
     * translated subs skipped, like Seal's default; `--convert-subs`
     * when a format is chosen; `--embed-subs` + `--write-subs` together
     * is the default (exactly #341's behavior) — embed off leaves the
     * sidecar only, keep off while embedding drops the sidecar.
     * `--write-subs` alone needs no ffmpeg; embed/convert do (#336).
     */
    private fun YoutubeDLRequest.addOptionsForSubtitles(settings: AppSettings): YoutubeDLRequest =
        apply {
            val langs = settings.downloadSubtitleLanguages.trim().ifBlank { SUBTITLE_LANGS }
            if (settings.downloadAutoSubtitles) {
                addOption("--write-auto-subs")
                addOption("--extractor-args", "youtube:skip=translated_subs")
            }
            addOption("--sub-langs", langs)
            if (settings.downloadEmbedSubtitles) {
                addOption("--embed-subs")
                if (settings.downloadKeepSubtitles) {
                    addOption("--write-subs")
                }
            } else {
                addOption("--write-subs")
            }
            when (settings.downloadConvertSubtitles) {
                "srt" -> addOption("--convert-subs", "srt")
                "vtt" -> addOption("--convert-subs", "vtt")
                "ass" -> addOption("--convert-subs", "ass")
            }
        }

    /**
     * Issue #347: small network/filename flags, Seal's downloadVideo
     * shape (DownloadUtil.kt:~700-730, JunkFood02/Seal, GPL-3.0):
     * `-r` rate limit, `--proxy`, `-4` force IPv4. Applied to every
     * yt-dlp request (info, playlist probe, download) — a slow or
     * proxied network fails the info fetch the same way it fails the
     * download. `--restrict-filenames` is download-only (applied at
     * the call site): it changes the output filename, which info and
     * probe requests never produce.
     */
    private fun YoutubeDLRequest.addOptionsForNetworkFlags(settings: AppSettings): YoutubeDLRequest =
        apply {
            val rateLimit = settings.downloadRateLimit.trim()
            if (rateLimit.isNotBlank()) {
                // Seal stores a bare number and appends "K"; MPx stores
                // the raw `-r` value, so a bare number gets the same "K"
                // (KB/s) and a suffixed value ("500K", "2M") passes as-is.
                addOption("-r", if (rateLimit.all { it.isDigit() }) "${rateLimit}K" else rateLimit)
            }
            val proxy = settings.downloadProxy.trim()
            if (proxy.isNotBlank()) addOption("--proxy", proxy)
            if (settings.downloadForceIpv4) addOption("-4")
        }

    /**
     * Issue #348: SponsorBlock removal, Seal's shape
     * (DownloadUtil.kt:751-752, JunkFood02/Seal, GPL-3.0):
     * `--sponsorblock-remove <categories>` cuts the segments with
     * the bundled ffmpeg (#336). Download-only: info/probe
     * requests never produce a file to cut. Off by default; blank
     * categories pass no flag. Applied to video and audio-only
     * downloads alike, like Seal — yt-dlp cuts the segments from
     * the extracted audio too.
     */
    private fun YoutubeDLRequest.addOptionsForSponsorBlock(settings: AppSettings): YoutubeDLRequest =
        apply {
            val categories = settings.downloadSponsorBlockCategories.trim()
            if (settings.downloadSponsorBlock && categories.isNotBlank()) {
                addOption("--sponsorblock-remove", categories)
            }
        }

    private suspend fun runDownload(item: ActiveDownload) {
        val id = item.id
        // Issue #334: this download's own temp dir (Seal's `-P temp:`
        // pattern, per-download instead of shared). Deleting it on any
        // exit path can never touch another download's partials.
        val tempDirFor = File(tempDir, id).apply { mkdirs() }
        // Issue #346: video info from the getInfo phase, for the history
        // record. Declared outside the try: the audio-fallback catch
        // below records history too, and locals declared inside try are
        // not visible in catch. Null until getInfo succeeds (direct
        // links never get here).
        var videoId: String? = null
        var extractor: String? = null
        var uploader: String? = null
        var thumbnailUrl: String? = null
        try {
            if (!ensureInitialized()) {
                updateItem(id) {
                    it.copy(status = DownloadStatus.FAILED, error = "Downloader couldn't start")
                }
                return
            }
            updateItem(id) {
                it.copy(status = DownloadStatus.DOWNLOADING, phase = DownloadPhase.FETCHING_INFO)
            }

            // Issue #337: the User-Agent goes on every yt-dlp request
            // (info + download), like Seal's `enableCookies` — a blank
            // setting sends no header at all. The whole blob is loaded
            // once here: the quality settings (#338) are read from the
            // same load at request time below, so a change on the
            // Download tab applies to the next download.
            val settings = settingsRepository.load()
            val userAgent = settings.downloadUserAgent
            val cookiesFile = item.cookiesFile?.takeIf { it.isFile }

            // Title first, so the row shows something real while the bytes
            // move. getInfo is a full yt-dlp run of its own. Seal's info
            // fetch (DownloadUtil.fetchVideoInfoFromUrl, JunkFood02/Seal,
            // GPL-3.0) fails fast with `-R 1` + `--socket-timeout 5`, and
            // a failure is terminal there — MPx does the same: an info
            // failure marks the row FAILED with the real cause instead of
            // proceeding blind and failing later at execute() with a
            // worse error. Cancellation still propagates as a cancel.
            val infoRequest = YoutubeDLRequest(item.url).apply {
                addOption("-R", "1")
                addOption("--socket-timeout", "5")
                if (userAgent.isNotBlank()) addOption("--user-agent", userAgent)
                cookiesFile?.let { addOption("--cookies", it.absolutePath) }
                // Issue #347: rate limit / proxy / IPv4 on the info
                // fetch too — a slow or proxied network fails here the
                // same way it fails the download.
                addOptionsForNetworkFlags(settings)
            }
            // Issue #346: keep the whole VideoInfo, not just the title —
            // the id/extractor feed the download-archive pre-check and
            // the history record below (uploader/thumbnail ride along).
            // The library mapper exposes all of them (verified against
            // the 0.18.1 AAR: VideoInfo.id/extractor/extractorKey/
            // uploader/thumbnail).
            val info = try {
                withContext(Dispatchers.IO) { YoutubeDL.getInstance().getInfo(infoRequest) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: Exception) {
                // A direct media link does not need yt-dlp info at all —
                // the plain HTTP fallback (same as the execute() failure
                // path below) is still the right next step for it. Any
                // other URL fails here with the real cause.
                if (isDirectMediaUrl(item.url)) {
                    runHttpFallback(item, tempDirFor)
                } else {
                    updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
                }
                return
            }
            val title = info.title?.takeIf { it.isNotBlank() } ?: item.url
            videoId = info.id?.takeIf { it.isNotBlank() }
            extractor = info.extractor?.takeIf { it.isNotBlank() }
            val extractorKey = info.extractorKey?.takeIf { it.isNotBlank() }
            uploader = info.uploader?.takeIf { it.isNotBlank() }
            thumbnailUrl = info.thumbnail?.takeIf { it.isNotBlank() }
            // Issue #388: the row shows the new title when the user
            // renamed the download, so it matches the file that
            // comes out.
            val displayTitle = item.newTitle?.takeIf { it.isNotBlank() } ?: title
            updateItem(id) { it.copy(title = displayTitle) }

            // Issue #346: already in the download archive — skip the
            // yt-dlp run entirely. Never FAILED: the row completes
            // with the existing file when it is still there, else with
            // the already-downloaded note (see handleArchiveSkip).
            if (isArchived(extractor, extractorKey, videoId)) {
                handleArchiveSkip(id, item.url, videoId)
                return
            }

            val beforeNames = downloadsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            // Issue #384: Seal's executeCommandInBackground writes the
            // template text to a file and passes it as
            // --config-locations — the template IS the command. The
            // file is written fresh on every run; if the write fails,
            // the custom command is ignored for this run (the normal
            // options apply) rather than failing the download.
            val customCommandFile = if (settings.customCommandEnabled && settings.customCommand.isNotBlank()) {
                runCatching {
                    File(appContext.filesDir, "custom-command.conf").apply { writeText(settings.customCommand) }
                }.getOrNull()
            } else {
                null
            }
            val useCustomCommand = customCommandFile != null
            val request = YoutubeDLRequest(item.url).apply {
                // Issue #384: --config-locations FIRST, so MPx's own
                // -o/-P/archive below (added later) win over anything
                // the template says about the same options — the files
                // must land where the finish flow looks for them.
                if (useCustomCommand) {
                    addOption("--config-locations", customCommandFile.absolutePath)
                }
                // Seal's output template (DownloadUtil.BASENAME,
                // JunkFood02/Seal, GPL-3.0): title truncated to 200 bytes
                // so long titles cannot exceed filesystem filename limits.
                // Issue #386 follow-up: with clips, Seal switches to
                // its OUTPUT_TEMPLATE_CLIPS — "[start-end]" in the
                // name — because the plain template gives every clip
                // the same filename, so the second clip would
                // overwrite (or be skipped as already downloaded).
                // The section fields come from yt-dlp itself.
                val outputTemplate = if (item.clips.isEmpty()) {
                    "%(title).200B.%(ext)s"
                } else {
                    "%(title).200B [%(section_start)d-%(section_end)d].%(ext)s"
                }
                addOption("-o", File(downloadsDir, outputTemplate).absolutePath)
                // Issue #387: split by chapter (Seal's
                // FormatConfig.splitByChapter, DownloadUtil
                // OUTPUT_TEMPLATE_CHAPTERS + --split-chapters): the
                // chapter template (the `chapter:` prefix makes it
                // apply to chapters only) sends each chapter into a
                // subfolder named after the video, "01 - Chapter
                // title.ext"; the -o above stays the final template
                // for the whole video when it has no chapters.
                // Audio-only never splits (the sheet never offers
                // it there), so the flag is video-only.
                if (item.splitByChapter && !item.audioOnly && !useCustomCommand) {
                    addOption(
                        "-o",
                        "chapter:${File(downloadsDir, "%(title).200B/%(section_number)d - %(section_title).200B.%(ext)s").absolutePath}",
                    )
                    addOption("--split-chapters")
                }
                // Issue #334: partials go to this download's own temp dir
                // (Seal's `-P temp:`); yt-dlp moves the finished file into
                // downloadsDir on completion, so the Destination line in
                // stdout still points at the final file.
                addOption("-P", "temp:${tempDirFor.absolutePath}")
                // Issue #340: audio-only downloads extract best audio
                // as mp3 instead of the video sorter below.
                // Issue #384: with a custom command the template IS
                // the command — this whole branch (format sorter/-f,
                // clips, metadata/chapters, advanced flags,
                // subtitles) is skipped; only the base options below
                // apply.
                if (item.audioOnly && !useCustomCommand) {
                    addOptionsForAudioDownloads()
                } else if (!item.audioOnly && !useCustomCommand) {
                    // Issue #388: rename before download (Seal's
                    // DownloadUtil.downloadVideo, JunkFood02/Seal,
                    // GPL-3.0): --replace-in-metadata swaps the
                    // video's title everywhere, so the filename
                    // follows and the clips/split templates keep
                    // working (they all read the title). addOption
                    // takes one value only (verified against the
                    // 0.18.1 AAR), so the three-value option goes
                    // through addCommands, like Seal.
                    item.newTitle?.takeIf { it.isNotBlank() }?.let { renamed ->
                        addCommands(listOf("--replace-in-metadata", "title", ".+", renamed))
                    }
                    // Issue #336 + #338: Seal's `-S` format sorter instead
                    // of a hardcoded `-f` — no `-f` means yt-dlp picks
                    // (and, with ffmpeg bundled, merges) the best
                    // video+audio streams. The sorter parameters come from
                    // the quality settings, read at request time above:
                    // "Auto" keeps #336's default (vp9.2, or av01 when the
                    // device hardware-decodes AV1); "VP9"/"AV1" force that
                    // codec; "H264" is Seal's compatibility format. A
                    // non-blank custom sorter replaces the built sorter
                    // entirely. The sorter works without ffmpeg too (it
                    // still improves single-file picks).
                    // Issue #383: a per-video format choice replaces
                    // the sorter entirely (Seal's
                    // DownloadUtil.downloadVideo: a non-empty
                    // formatIdString means `-f <ids>`, else the
                    // sorter). The metadata/chapters/advanced flags
                    // below still apply either way.
                    if (!item.formatId.isNullOrBlank()) {
                        addOption("-f", item.formatId)
                    } else {
                        applyFormatSorter(
                        formatSorter(
                            videoFormat = if (settings.downloadCodec == "H264") {
                                FORMAT_COMPATIBILITY
                            } else {
                                FORMAT_QUALITY
                            },
                            videoResolution = settings.downloadResolution,
                            supportAv1HardwareDecoding = when (settings.downloadCodec) {
                                "AV1" -> true
                                "VP9" -> false
                                else -> checkIfAv1HardwareAccelerated()
                            },
                        ),
                        formatSorting = settings.downloadCustomSorter.isNotBlank(),
                        sortingFields = settings.downloadCustomSorter,
                    )
                    }
                    // Issue #386: Seal's per-clip sections
                    // (DownloadUtil.downloadVideo, JunkFood02/Seal,
                    // GPL-3.0): one `--download-sections "*start-end"`
                    // per clip, times in seconds. Each clip comes out
                    // as its own file. No clips means the whole video.
                    item.clips.forEach { clip ->
                        addOption("--download-sections", "*${clip.start}-${clip.end}")
                    }
                    // Issue #381: Seal's always-on video flags
                    // (DownloadUtil.addOptionsForVideoDownloads,
                    // JunkFood02/Seal, GPL-3.0): `--add-metadata` tags
                    // the file with title/uploader, `--embed-chapters`
                    // writes the video's chapters into it (the player
                    // can only show chapters a file actually carries —
                    // see #178). Both need the bundled ffmpeg (#336);
                    // a video without chapters simply gets none.
                    // Audio-only downloads already embed metadata.
                    addOption("--add-metadata")
                    addOption("--embed-chapters")
                    // Issue #385: Seal's advanced video flags
                    // (DownloadUtil.addOptionsForVideoDownloads,
                    // JunkFood02/Seal, GPL-3.0) — the settings-shaped
                    // ones. Merge to MKV remuxes + merges to .mkv;
                    // audio multistreams keeps every audio track
                    // (Seal gates it on a chosen format id; #383
                    // now has one, and the flag still applies
                    // whenever it is on); write thumbnail saves the
                    // cover as a .png next to the video. All three
                    // need ffmpeg (#336).
                    if (settings.downloadMergeToMkv) {
                        addOption("--remux-video", "mkv")
                        addOption("--merge-output-format", "mkv")
                    }
                    if (settings.downloadAudioMultistreams) {
                        addOption("--audio-multistreams")
                    }
                    if (settings.downloadWriteThumbnail) {
                        addOption("--write-thumbnail")
                        addOption("--convert-thumbnails", "png")
                    }
                    // Issue #341: subtitles for video downloads only —
                    // an mp3 has no use for a sidecar subtitle file.
                    if (item.subtitles) {
                        addOptionsForSubtitles(settings)
                    }
                }
                // Issue #339: faster downloads, Seal's shape
                // (DownloadUtil.kt:433-434 + :733-735, JunkFood02/Seal,
                // GPL-3.0): aria2c when its init succeeded, else
                // concurrent fragments. Seal's default fragment count
                // is 8 (PreferenceUtil.kt:226 `CONCURRENT to 8`); there
                // is no settings UI for it in this issue. Note Seal
                // passes `--downloader libaria2c.so`, not
                // `--external-downloader aria2c` — the latter would look
                // for an `aria2c` binary on PATH, which does not exist
                // on Android; the bundled native lib is selected by name.
                if (aria2cAvailable) {
                    addOption("--downloader", "libaria2c.so")
                } else {
                    addOption("--concurrent-fragments", CONCURRENT_FRAGMENTS_DEFAULT)
                }
                // Issue #348: SponsorBlock removal (Seal's
                // DownloadUtil.kt:751-752, JunkFood02/Seal, GPL-3.0) —
                // the segments are cut with the bundled ffmpeg (#336).
                // Issue #384: skipped with a custom command — the
                // template owns the options.
                if (!useCustomCommand) addOptionsForSponsorBlock(settings)
                addOption("--no-playlist")
                addOption("--no-mtime")
                // Issue #346: the download archive (Seal's
                // `useDownloadArchive`, DownloadUtil.kt:371-372,
                // JunkFood02/Seal, GPL-3.0 — one file, one option).
                // yt-dlp appends "<extractor> <id>" on success and
                // skips archived videos on the next run; the pre-check
                // above and the exit-0 branch below turn that skip
                // into a DONE row instead of a failure. The file lives
                // in the internal files dir (not cache) so it survives
                // restarts and updates.
                addOption("--download-archive", archiveFile.absolutePath)
                // Same fail-fast options as the info fetch above.
                addOption("-R", "1")
                addOption("--socket-timeout", "5")
                // Issue #347: rate limit / proxy / IPv4, same as the
                // info fetch; restrict-filenames is download-only — it
                // changes the output filename, which info/probe
                // requests never produce.
                addOptionsForNetworkFlags(settings)
                if (settings.downloadRestrictFilenames) addOption("--restrict-filenames")
                // Issue #337: same UA + cookies as the info fetch.
                if (userAgent.isNotBlank()) addOption("--user-agent", userAgent)
                cookiesFile?.let { addOption("--cookies", it.absolutePath) }
            }
            // Issue #350: the download phase starts here — getInfo is
            // done, bytes are about to move. The callback below keeps
            // the phase current from the output lines.
            updateItem(id) { it.copy(phase = DownloadPhase.DOWNLOADING) }
            // Issue #350: the callback's 3rd parameter is the raw
            // yt-dlp output line (verified against the 0.18.1 AAR:
            // StreamProcessExtractor passes every line through) — parse
            // it for downloaded/total bytes, speed, and the
            // merge/post-processing stage; the library only gives
            // progress % + ETA. Emits are throttled to ~4/sec so the
            // row does not recompose on every line, but a phase change
            // or the final 100% always goes out immediately. The
            // locals below are only touched on the extractor thread
            // that invokes this callback; updateItem itself is
            // thread-safe (StateFlow.update) and progress-only updates
            // never touch Room (#342).
            var lastEmitMs = 0L
            var currentPhase = DownloadPhase.DOWNLOADING
            var doneEmitted = false
            val response = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, id) { progress, eta, line ->
                    val parsed = parseProgressLine(line)
                    val nextPhase = when {
                        parsed.phase != null -> parsed.phase
                        parsed.isFfmpegLine && currentPhase == DownloadPhase.DOWNLOADING ->
                            DownloadPhase.POST_PROCESSING
                        else -> currentPhase
                    }
                    val now = SystemClock.uptimeMillis()
                    val phaseChanged = nextPhase != currentPhase
                    // The library's own progress may never reach exactly
                    // 100 (its pattern needs `…% … ETA …` and the final
                    // line prints `100% … in mm:ss`), so completion is
                    // also recognised from the parsed sizes.
                    val done = (progress >= 100f ||
                        (parsed.totalBytes > 0 && parsed.downloadedBytes >= parsed.totalBytes)) &&
                        !doneEmitted
                    if (phaseChanged || done || now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
                        lastEmitMs = now
                        currentPhase = nextPhase
                        if (done) doneEmitted = true
                        updateItem(id) {
                            it.copy(
                                progress = progress,
                                etaSeconds = eta,
                                status = DownloadStatus.DOWNLOADING,
                                phase = nextPhase,
                                downloadedBytes = parsed.downloadedBytes.takeIf { it >= 0 }
                                    ?: it.downloadedBytes,
                                totalBytes = parsed.totalBytes.takeIf { it >= 0 }
                                    ?: it.totalBytes,
                                speedBytesPerSec = parsed.speedBytesPerSec.takeIf { it >= 0 }
                                    ?: it.speedBytesPerSec,
                            )
                        }
                    }
                }
            }
            if (response.exitCode == 0) {
                if (item.splitByChapter) {
                    // Issue #387: a split download produces one file
                    // per chapter in a subfolder — the single-file
                    // finish below would only publish the last
                    // chapter (findOutputFile takes the last
                    // Destination line) and the rest would sit
                    // invisible in the private dir. Publish every
                    // chapter flat into Downloads/MPx/ so each shows
                    // as its own row in Downloaded; the row itself
                    // completes with the first chapter, so tapping it
                    // plays chapter 1. No chapters found (a
                    // chapter-less video, or the Destination lines
                    // were lost) falls through to the single-file
                    // path — a chapter-less video downloads as one
                    // file, unchanged.
                    val chapters = findSplitOutputFiles(response.out, beforeNames)
                    if (chapters.isNotEmpty()) {
                        val published = chapters.map { publishFinishedFile(it) }
                        val first = published.first()
                        updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = first) }
                        recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, first)
                        refreshFiles()
                        return
                    }
                }
                val file = findOutputFile(response.out, beforeNames)
                if (file != null) {
                    // Issue #344: the finished file moves to the public
                    // Downloads/MPx/ folder before the row completes, so
                    // the completed list picks it up from MediaStore.
                    val published = publishFinishedFile(file)
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
                    // Issue #346: record the finished download.
                    recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, published)
                } else if (isArchiveSkipped(response.out)) {
                    // Issue #346: yt-dlp skipped this video as already
                    // archived — the pre-check above missed it (archive
                    // written by an older build, or a different
                    // extractor spelling). Same DONE handling as the
                    // pre-check, never FAILED.
                    handleArchiveSkip(id, item.url, videoId)
                } else {
                    updateItem(id) {
                        it.copy(status = DownloadStatus.FAILED, error = "Finished but no file was found")
                    }
                }
                refreshFiles()
                return
            }
            // Non-zero exit without an exception: an audio-only download
            // may still have its file (see findAudioFallbackFile) —
            // otherwise fall through to the direct-link fallback below
            // when it applies.
            if (item.audioOnly) {
                val fallback = findAudioFallbackFile(beforeNames, tempDirFor)
                if (fallback != null) {
                    val published = publishFinishedFile(fallback)
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
                    recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, published)
                    refreshFiles()
                    return
                }
            }
            // Issue #341: a subtitle download whose post-processing
            // (embed) failed after the video itself landed — yt-dlp
            // only moves the finished file out of the temp dir once the
            // download phase completed, and a missing subtitle is a
            // warning, not an error, so a file in downloadsDir here
            // means the download succeeded and the subs must not fail
            // the row. A partial never reaches downloadsDir (it stays
            // in the temp dir as `.part`), so this cannot mask a real
            // download failure.
            if (item.subtitles) {
                val file = findOutputFile(response.out, beforeNames)
                if (file != null) {
                    val published = publishFinishedFile(file)
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
                    recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, published)
                    refreshFiles()
                    return
                }
            }
            // Issue #348: SponsorBlock API unreachable — Seal's
            // fallback (DownloadUtil.kt:805-815, JunkFood02/Seal,
            // GPL-3.0). The video itself downloaded fine; only the
            // segment lookup failed (yt-dlp fetches the segments while
            // post-processing, after the download), so the row still
            // completes instead of failing. A file in downloadsDir (or
            // the temp dir, via the Destination line) here means the
            // download phase finished — a partial never reaches
            // downloadsDir and the temp file is only complete once the
            // download phase ends, so this cannot mask a real download
            // failure. Without a file there is nothing to keep and the
            // error below is thrown as usual.
            if (settings.downloadSponsorBlock &&
                response.err.contains(SPONSORBLOCK_API_ERROR)
            ) {
                val file = findOutputFile(response.out, beforeNames)
                if (file != null) {
                    val published = publishFinishedFile(file)
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
                    recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, published)
                    refreshFiles()
                    return
                }
            }
            throw YoutubeDLException(response.err.ifBlank { "yt-dlp exited with code ${response.exitCode}" })
        } catch (e: CancellationException) {
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw e
        } catch (e: InterruptedException) {
            // Coroutine cancellation interrupts the thread blocked in
            // execute()/getInfo() — that is a user cancel, not a failure.
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw CancellationException("Download canceled", e)
        } catch (e: YoutubeDL.CanceledException) {
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
        } catch (e: Exception) {
            // yt-dlp failed. A direct media link gets one more chance via
            // a plain HTTP download; an audio-only download may still
            // have its raw audio on disk (conversion failed after the
            // download); anything else is a real failure.
            if (isDirectMediaUrl(item.url)) {
                runHttpFallback(item, tempDirFor)
            } else if (item.audioOnly) {
                // beforeNames is not in scope here (it is declared after
                // the getInfo phase), so only the temp dir is searched.
                // The video info locals are in scope (the getInfo phase
                // ran before the execute), so the history record keeps
                // its id/extractor/uploader.
                val fallback = findAudioFallbackFile(null, tempDirFor)
                if (fallback != null) {
                    val published = publishFinishedFile(fallback)
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
                    recordHistory(id, item.url, videoId, extractor, uploader, thumbnailUrl, published)
                    refreshFiles()
                } else {
                    updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
                }
            } else {
                updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
            }
        } finally {
            // Issue #334: the temp dir holds only this download's partials
            // — the finished file was already moved into downloadsDir, and
            // the HTTP fallback stages its bytes here too (#349) — so
            // deleting it on every exit (success, failure, or cancel)
            // can never touch another download's in-progress parts.
            try {
                tempDirFor.deleteRecursively()
            } catch (_: Exception) {
            }
            // Issue #337: the per-download cookie file dies with the
            // download — it is never reused and never persisted.
            try {
                item.cookiesFile?.delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Plain HttpURLConnection download for direct media links, used only
     * when yt-dlp failed. No new dependency: HttpURLConnection is in the
     * platform. Progress comes from Content-Length when the server sends
     * one, otherwise it stays indeterminate (-1).
     *
     * Issue #349: the bytes go to `<name>.part` inside this download's
     * own temp dir ([tempDirFor], the #334 per-download dir), and the
     * file is renamed to its final name in [downloadsDir] only on
     * successful completion. The old code wrote the final name straight
     * into downloadsDir while downloading, so a crash mid-download left
     * a full-named partial that the completed list showed as finished.
     * A crash now leaves only a `.part` in a temp subdir, which the
     * #342 startup wipe deletes — and `refreshFiles()` excludes `.part`
     * files anyway, so the completed list can never see one.
     */
    private suspend fun runHttpFallback(item: ActiveDownload, tempDirFor: File) {
        val id = item.id
        val fileName = fileNameFromUrl(item.url)
        val partFile = File(tempDirFor, "$fileName.part")
        val outFile = File(downloadsDir, fileName)
        // Issue #350: bytes are about to move again (the yt-dlp run
        // failed) — the phase goes back to DOWNLOADING even if the
        // failed run had reached merging.
        updateItem(id) { it.copy(phase = DownloadPhase.DOWNLOADING) }
        try {
            withContext(Dispatchers.IO) {
                val connection = URL(item.url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw YoutubeDLException("HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                var readSoFar = 0L
                // Issue #350: the fallback counted readSoFar but never
                // exposed it — report bytes + speed (bytes since the
                // last emit over that interval), throttled like the
                // yt-dlp path. With no Content-Length the bar stays
                // indeterminate (-1) but the row still shows the
                // downloaded size and speed.
                var lastEmitMs = SystemClock.uptimeMillis()
                var lastEmitReadSoFar = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            // Blocking reads do not suspend, so cancellation
                            // would only be noticed after the download
                            // finished — check every chunk instead.
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            readSoFar += n
                            val now = SystemClock.uptimeMillis()
                            if (now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
                                val dtMs = (now - lastEmitMs).coerceAtLeast(1L)
                                val speed = (readSoFar - lastEmitReadSoFar) * 1000L / dtMs
                                lastEmitMs = now
                                lastEmitReadSoFar = readSoFar
                                val pct = if (total > 0) {
                                    (readSoFar * 100f / total).coerceIn(0f, 100f)
                                } else {
                                    -1f
                                }
                                updateItem(id) {
                                    it.copy(
                                        progress = pct,
                                        status = DownloadStatus.DOWNLOADING,
                                        phase = DownloadPhase.DOWNLOADING,
                                        downloadedBytes = readSoFar,
                                        totalBytes = total,
                                        speedBytesPerSec = speed,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Download finished: move the partial to its final name.
            // Same filesystem (both under the app-private dir), so
            // renameTo is atomic; the copy is only a fallback if the
            // rename ever refuses (e.g. a stale target) so a fully
            // downloaded file is never lost.
            if (!partFile.renameTo(outFile)) {
                partFile.inputStream().use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                partFile.delete()
            }
            // Issue #344: same public move as the yt-dlp path.
            val published = publishFinishedFile(outFile)
            updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = published) }
            // Issue #346: record the finished download. A direct link
            // has no video id/extractor/uploader/thumbnail — the
            // history row carries the URL and the file name instead.
            recordHistory(id, item.url, null, null, null, null, published)
            refreshFiles()
        } catch (e: CancellationException) {
            partFile.delete()
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw e
        } catch (e: Exception) {
            partFile.delete()
            updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
        }
    }

    /**
     * Issue #340: conversion fallback for audio-only downloads. When
     * `--audio-format mp3` fails (ffmpeg error, odd container), yt-dlp
     * exits non-zero but the raw best-audio file is usually already on
     * disk — either written to downloadsDir before a later step failed,
     * or still sitting in this download's temp dir as the unconverted
     * intermediate. Move the newest such file into downloadsDir and
     * return it, so the raw audio is kept instead of failing the row.
     * [beforeNames] is null when called from the catch block (it is not
     * in scope there), in which case only the temp dir is searched.
     * Null when nothing usable exists.
     */
    private fun findAudioFallbackFile(beforeNames: Set<String>?, tempDirFor: File): File? {
        // A finished .mp3 written before a later step failed.
        if (beforeNames != null) {
            downloadsDir.listFiles()
                ?.filter { it.isFile && it.name !in beforeNames && isMediaFile(it) && !it.name.endsWith(".part") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { return it }
        }
        // Raw intermediate still in this download's temp dir: move it
        // out (the finally block deletes the temp dir right after this).
        val raw = tempDirFor.listFiles()
            ?.filter { it.isFile && isMediaFile(it) && !it.name.endsWith(".part") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        val target = File(downloadsDir, raw.name)
        return if (raw.renameTo(target)) target else null
    }

    /** Finds the file yt-dlp just wrote: the Destination line first, else the newest new file. */
    /**
     * Issue #387: every file one split download produced. The
     * `[download] Destination:` lines tie the chapters to this yt-dlp
     * run exactly (one per chapter, in chapter order), so a
     * concurrent download cannot leak in. Falls back to a scan of the
     * new entries in downloadsDir — new top-level names only,
     * descending into a new subfolder (a re-download of the same
     * video reuses the old title folder, so its old chapters must
     * not be picked up) — newest first, when the Destination lines
     * are lost.
     */
    private fun findSplitOutputFiles(stdout: String, beforeNames: Set<String>): List<File> {
        val destinations = stdout.lineSequence()
            .filter { it.contains("[download] Destination: ") }
            .map { File(it.substringAfter("[download] Destination: ").trim()) }
            .filter { it.isFile && isMediaFile(it) }
            .toList()
        if (destinations.isNotEmpty()) return destinations
        return downloadsDir.listFiles().orEmpty()
            .filter { it.name !in beforeNames }
            .flatMap { entry ->
                if (entry.isDirectory) entry.walkTopDown().filter { it.isFile }.toList()
                else listOf(entry)
            }
            .filter { isMediaFile(it) && !it.name.endsWith(".part") }
            .sortedBy { it.lastModified() }
    }

    private fun findOutputFile(stdout: String, beforeNames: Set<String>): File? {
        val destination = stdout.lineSequence()
            .filter { it.contains("[download] Destination: ") }
            .map { it.substringAfter("[download] Destination: ").trim() }
            .lastOrNull()
            ?.let { File(it) }
            ?.takeIf { it.isFile && isMediaFile(it) }
        if (destination != null) return destination
        return downloadsDir.listFiles()
            ?.filter { it.isFile && it.name !in beforeNames && isMediaFile(it) && !it.name.endsWith(".part") }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun shortError(e: Exception): String {
        // yt-dlp stderr can be pages long; the last line is the cause.
        val message = e.message?.trim().orEmpty()
        val lastLine = message.lineSequence().filter { it.isNotBlank() }.lastOrNull().orEmpty()
        return lastLine.take(200).ifBlank { e.javaClass.simpleName }
    }

    companion object {
        @Volatile
        private var instance: MpxDownloader? = null

        fun getInstance(context: Context): MpxDownloader {
            return instance ?: synchronized(this) {
                instance ?: MpxDownloader(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Concurrent downloads (issue #342): Seal's `MAX_CONCURRENCY`
         * (`DownloaderV2.kt:43`, JunkFood02/Seal, GPL-3.0) — at most 3
         * yt-dlp processes at once; the rest wait QUEUED and start as
         * slots free up (see [MpxDownloader.doYourWork]). No settings
         * UI in this issue — a fixed value.
         */
        private const val MAX_CONCURRENT_DOWNLOADS = 3

        /**
         * Concurrent fragment count when aria2c is unavailable
         * (issue #339): Seal's default, `CONCURRENT to 8` in its
         * PreferenceUtil. No settings UI in this issue — a fixed value.
         */
        private const val CONCURRENT_FRAGMENTS_DEFAULT = "8"

        /**
         * Progress-row update throttle (issue #350): the yt-dlp
         * callback fires on every output line (several per second),
         * and every emit recomposes the Download tab row. Emit at
         * most every 250ms (~4/sec); a phase change or the final 100%
         * always goes out immediately (see runDownload).
         */
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L

        /**
         * Subtitle languages fetched at download time (issue #341):
         * fixed default, English + Arabic, matching MPx's locales.
         * No language picker in this issue — a follow-up if wanted.
         */
        private const val SUBTITLE_LANGS = "en,ar"

        /**
         * SponsorBlock API failure marker (issue #348): the stderr
         * substring Seal matches in its fallback
         * (DownloadUtil.kt:805-815, JunkFood02/Seal, GPL-3.0). When
         * the download fails with this message, the video itself
         * downloaded fine and the row still completes (see
         * runDownload's non-zero-exit path).
         */
        private const val SPONSORBLOCK_API_ERROR = "Unable to communicate with SponsorBlock API"

        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "flv", "3gp", "ts", "m2ts",
            "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
        )

        /**
         * Issue #379: one download-folder name — trims, takes the first
         * segment, falls back to "MPx". Shared with the Settings row so
         * the stored value and the folder the downloader uses can never
         * disagree.
         */
        internal fun sanitizeDownloadFolderName(raw: String): String {
            val first = raw.trim().trim('/').substringBefore('/').trim()
            return first.ifBlank { "MPx" }
        }

        /** Subtitle sidecar extensions (issue #341, moved here in #344). */
        private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "webvtt", "ass", "ssa")

        fun isMediaFile(file: File): Boolean {
            val ext = file.extension.lowercase()
            return ext in MEDIA_EXTENSIONS
        }

        /** Same check by file name (MediaStore rows have no File). */
        fun isMediaFileName(name: String): Boolean {
            val ext = name.substringAfterLast('.', "")
            return '.' in name && ext.lowercase() in MEDIA_EXTENSIONS
        }

        /** A direct media link: path ends in a media extension (query stripped). */
        fun isDirectMediaUrl(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return MEDIA_EXTENSIONS.any { path.endsWith(".$it") }
        }

        private fun fileNameFromUrl(url: String): String {
            val raw = url.substringBefore('?').substringBefore('#')
                .substringAfterLast('/').trim()
                .ifBlank { "download-${UUID.randomUUID().toString().take(8)}" }
            val sanitized = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            return if ('.' in sanitized) sanitized else "$sanitized.mp4"
        }
    }
}
