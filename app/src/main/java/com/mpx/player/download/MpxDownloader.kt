package com.mpx.player.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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

/** One download row shown on the Download tab while it is running or finished. */
data class ActiveDownload(
    val id: String,
    val url: String,
    val title: String,
    val status: DownloadStatus,
    /** 0..100, -1 while the size is still unknown. */
    val progress: Float = -1f,
    val etaSeconds: Long = -1L,
    val file: File? = null,
    val error: String? = null,
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

/** Result of a manual yt-dlp update request (issue #333). */
enum class YtDlpUpdateOutcome {
    UPDATED,
    UP_TO_DATE,
    FAILED,
    /** A download is running, or an update is already in flight. */
    BUSY,
}

/**
 * Downloader engine (issue #324): Seal's technique — the youtubedl-android
 * library (yausername/youtubedl-android 0.18.1, `:library` only), which
 * bundles CPython + a lazy-extractors yt-dlp build as native .so files.
 * The plain yt-dlp_linux_aarch64 standalone binary does NOT run on Android
 * (glibc vs Bionic) — that route was checked and rejected.
 *
 * No `:ffmpeg` artifact is bundled (139 MB AAR): without ffmpeg, yt-dlp
 * cannot merge separate video+audio streams, so downloads use the best
 * SINGLE-file format (`best[ext=mp4]/best` — typically 720p for YouTube).
 * If merging matters later, adding `:ffmpeg` is the follow-up.
 *
 * Files go to the app-private external Downloads dir
 * (`Android/data/com.mpx.player/files/Download/MPx/`): no storage
 * permission is needed there, and raw File writes to the public
 * Download/ folder are blocked by scoped storage on API 30+ without
 * MANAGE_EXTERNAL_STORAGE. Files survive restarts and are deleted on
 * uninstall. The completed list is a scan of that dir, so it is
 * persistent by construction — no Room table.
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

    /** App-private external Downloads/MPx — falls back to internal files. */
    val downloadsDir: File = run {
        val external = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val base = if (external != null) File(external, "MPx") else File(appContext.filesDir, "downloads/MPx")
        base.apply { mkdirs() }
    }

    private val _initState = MutableStateFlow(DownloaderInitState.IDLE)
    val initState: StateFlow<DownloaderInitState> = _initState.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownload>> = _activeDownloads.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFiles: StateFlow<List<File>> = _downloadedFiles.asStateFlow()

    /** Last yt-dlp update outcome (issue #333); IDLE until the first attempt. */
    private val _updateState = MutableStateFlow(YtDlpUpdateState.IDLE)
    val updateState: StateFlow<YtDlpUpdateState> = _updateState.asStateFlow()

    /** Installed yt-dlp version once known (issue #333); "" until then. */
    private val _ytdlpVersion = MutableStateFlow(settingsRepository.load().ytdlpVersion)
    val ytdlpVersion: StateFlow<String> = _ytdlpVersion.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        refreshFiles()
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
                withContext(Dispatchers.IO) { YoutubeDL.getInstance().init(appContext) }
                _initState.value = DownloaderInitState.READY
                true
            } catch (e: CancellationException) {
                // The caller went away (e.g. screen disposed mid-init) —
                // not a failed init, so the next attempt retries cleanly.
                _initState.value = DownloaderInitState.IDLE
                throw e
            } catch (e: Exception) {
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

    private fun saveYtDlpVersion(version: String) {
        _ytdlpVersion.value = version
        val settings = settingsRepository.load()
        settingsRepository.save(settings.copy(ytdlpVersion = version))
    }

    private fun saveYtDlpLastUpdateTime(nowMs: Long) {
        val settings = settingsRepository.load()
        settingsRepository.save(settings.copy(ytdlpLastUpdateTimeMs = nowMs))
    }

    /** Starts a download. No-op for a blank URL. */
    fun startDownload(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) return
        val id = UUID.randomUUID().toString()
        val item = ActiveDownload(
            id = id,
            url = url,
            title = url,
            status = DownloadStatus.QUEUED,
        )
        _activeDownloads.update { it + item }
        val job = scope.launch {
            runDownload(item)
        }
        synchronized(jobs) { jobs[id] = job }
        job.invokeOnCompletion { synchronized(jobs) { jobs.remove(id) } }
    }

    /** Cancels a running download (yt-dlp process + the coroutine). */
    fun cancelDownload(id: String) {
        synchronized(jobs) { jobs[id] }?.cancel()
        // Best effort: also kill the yt-dlp process directly (no-op when
        // the download is still in the getInfo phase or the HTTP fallback).
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (_: Exception) {
        }
    }

    /** Deletes a downloaded file from disk and refreshes the list. */
    fun deleteFile(file: File) {
        scope.launch {
            try {
                file.delete()
            } catch (_: Exception) {
            }
            refreshFiles()
        }
    }

    /** Re-scans the download dir for completed media files, newest first. */
    fun refreshFiles() {
        val files = downloadsDir.listFiles()
            ?.filter { it.isFile && isMediaFile(it) && !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        _downloadedFiles.value = files
    }

    private fun updateItem(id: String, transform: (ActiveDownload) -> ActiveDownload) {
        _activeDownloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun runDownload(item: ActiveDownload) {
        val id = item.id
        try {
            if (!ensureInitialized()) {
                updateItem(id) {
                    it.copy(status = DownloadStatus.FAILED, error = "Downloader couldn't start")
                }
                return
            }
            updateItem(id) { it.copy(status = DownloadStatus.DOWNLOADING) }

            // Title first, so the row shows something real while the bytes
            // move. getInfo is a full yt-dlp run of its own; when it fails
            // (unsupported site, network) the download still goes ahead
            // with the URL as the title.
            val title = try {
                withContext(Dispatchers.IO) { YoutubeDL.getInstance().getInfo(item.url).title }
                    ?.takeIf { it.isNotBlank() } ?: item.url
            } catch (_: Exception) {
                item.url
            }
            updateItem(id) { it.copy(title = title) }

            val beforeNames = downloadsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            val request = YoutubeDLRequest(item.url).apply {
                addOption("-o", File(downloadsDir, "%(title)s.%(ext)s").absolutePath)
                // Best single-file format: no ffmpeg is bundled, so no
                // merging of separate video+audio streams (see class doc).
                addOption("-f", "best[ext=mp4]/best")
                addOption("--no-playlist")
                addOption("--no-mtime")
            }
            val response = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, id) { progress, eta, _ ->
                    updateItem(id) {
                        it.copy(progress = progress, etaSeconds = eta, status = DownloadStatus.DOWNLOADING)
                    }
                }
            }
            if (response.exitCode == 0) {
                val file = findOutputFile(response.out, beforeNames)
                if (file != null) {
                    updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = file) }
                } else {
                    updateItem(id) {
                        it.copy(status = DownloadStatus.FAILED, error = "Finished but no file was found")
                    }
                }
                refreshFiles()
                return
            }
            // Non-zero exit without an exception: fall through to the
            // direct-link fallback below when it applies.
            throw YoutubeDLException(response.err.ifBlank { "yt-dlp exited with code ${response.exitCode}" })
        } catch (e: CancellationException) {
            cleanupPartialFiles()
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw e
        } catch (e: InterruptedException) {
            // Coroutine cancellation interrupts the thread blocked in
            // execute()/getInfo() — that is a user cancel, not a failure.
            cleanupPartialFiles()
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw CancellationException("Download canceled", e)
        } catch (e: YoutubeDL.CanceledException) {
            cleanupPartialFiles()
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
        } catch (e: Exception) {
            // yt-dlp failed. A direct media link gets one more chance via
            // a plain HTTP download; anything else is a real failure.
            if (isDirectMediaUrl(item.url)) {
                runHttpFallback(item)
            } else {
                updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
            }
        }
    }

    /**
     * Plain HttpURLConnection download for direct media links, used only
     * when yt-dlp failed. No new dependency: HttpURLConnection is in the
     * platform. Progress comes from Content-Length when the server sends
     * one, otherwise it stays indeterminate (-1).
     */
    private suspend fun runHttpFallback(item: ActiveDownload) {
        val id = item.id
        val fileName = fileNameFromUrl(item.url)
        val outFile = File(downloadsDir, fileName)
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
                connection.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
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
                            if (total > 0) {
                                val pct = (readSoFar * 100f / total).coerceIn(0f, 100f)
                                updateItem(id) {
                                    it.copy(progress = pct, status = DownloadStatus.DOWNLOADING)
                                }
                            }
                        }
                    }
                }
            }
            updateItem(id) { it.copy(status = DownloadStatus.DONE, progress = 100f, file = outFile) }
            refreshFiles()
        } catch (e: CancellationException) {
            outFile.delete()
            updateItem(id) { it.copy(status = DownloadStatus.CANCELED, progress = -1f) }
            throw e
        } catch (e: Exception) {
            outFile.delete()
            updateItem(id) { it.copy(status = DownloadStatus.FAILED, error = shortError(e)) }
        }
    }

    /** Finds the file yt-dlp just wrote: the Destination line first, else the newest new file. */
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

    private fun cleanupPartialFiles() {
        try {
            downloadsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".part") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {
        }
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

        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "flv", "3gp", "ts", "m2ts",
            "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
        )

        fun isMediaFile(file: File): Boolean {
            val ext = file.extension.lowercase()
            return ext in MEDIA_EXTENSIONS
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
