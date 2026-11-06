package com.mpx.player.playback

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages native mpv log capture to a rotating plain-text file in the app cache directory.
 *
 * Characteristics:
 * - Plain text log file: `cacheDir/mpv.log`
 * - Each line prefixed with ISO-style timestamp `[yyyy-MM-dd HH:mm:ss.SSS]`, log level, and mpv prefix.
 * - Rotating: capped at last ~500 lines or ~200 KB (oldest lines dropped on rotation).
 * - Thread-safe appends and rotations.
 * - Supports sharing via system share sheet (using FileProvider if available, with graceful text fallback).
 *
 * Threading (issue #306): all file I/O runs on a single dedicated daemon writer thread, so
 * `append()` — called from the mpv log-callback thread — never touches the file itself: it only
 * hands the raw lines to the writer thread and returns. One [BufferedWriter] stays open across
 * appends instead of opening/closing a stream per line. Rotation (read the tail, rewrite the
 * file) runs on the writer thread too, never on the mpv callback thread, and appends queued
 * behind it are written afterwards, in order.
 *
 * Volume note: the mpv-android wrapper requests log messages at level "terminal-default"
 * (hard-coded in its native `nativeCreate`), which libmpv resolves to its default terminal
 * verbosity — status level and below (FATAL/ERROR/WARN/STATUS, level <= 30). Normal playback
 * is therefore quiet (a burst of lines at file open, then near-silence), so the per-line
 * open/write/close was LOW severity — but the buffered writer is still strictly cheaper and
 * rotation no longer stalls the mpv event thread.
 */
object MpvLogFile {

    private const val TAG = "MpvLogFile"
    private const val LOG_FILE_NAME = "mpv.log"
    const val MAX_LINES: Int = 500
    const val MAX_BYTES: Long = 200 * 1024L // 200 KB

    /**
     * Lines are flushed to disk after this many unflushed lines. Bounds what is lost if the
     * process is killed without a clean shutdown (at most this many trailing lines) while
     * keeping a flush syscall far rarer than one per line. The writer's 8 KB buffer also
     * flushes on its own when full. [shareLog] and [close] always flush synchronously first.
     */
    private const val FLUSH_EVERY_LINES: Int = 16

    /** Bound on waiting for the writer thread from a synchronous call ([clear], [close], [shareLog]). */
    private const val SYNC_TIMEOUT_MS: Long = 2_000L

    /**
     * Serializes every file access. Daemon thread: it never holds the process open, and it is
     * deliberately never shut down — [close] only flushes/closes the writer, so a later engine
     * instance can resume appending and the writer is reopened lazily.
     */
    private val writerExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MpvLogFile").apply { isDaemon = true }
        }

    // All of the state below is touched only on the writer thread, except where noted.
    private var writer: BufferedWriter? = null
    private var lineCount: Int = -1
    /** Bytes in the file as tracked from writes (avoids a stat call per line; exact, not estimated). */
    private var approxBytes: Long = 0L
    private var linesSinceFlush: Int = 0

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Returns the file object for the mpv log in the app's cache directory.
     */
    fun getLogFile(context: Context): File =
        File(context.applicationContext.cacheDir, LOG_FILE_NAME)

    /**
     * Maps mpv integer log levels to human-readable strings.
     */
    private fun levelToString(level: Int): String = when (level) {
        0 -> "NONE"
        10 -> "FATAL"
        20 -> "ERROR"
        30 -> "WARN"
        40 -> "INFO"
        50 -> "V"
        60 -> "DEBUG"
        70 -> "TRACE"
        else -> "LVL$level"
    }

    /**
     * Appends an mpv log message to the rotating log file.
     *
     * Returns immediately: the lines are queued for the writer thread, which formats, writes,
     * and (when due) rotates them. Safe to call from the mpv log-callback thread.
     *
     * @param context Application or UI context
     * @param prefix mpv module/tag prefix (e.g. "cplayer", "ffmpeg", "mediacodec")
     * @param level mpv numeric log level
     * @param text Log message text
     */
    fun append(context: Context, prefix: String, level: Int, text: String) {
        if (text.isBlank()) return

        val cleanText = text.trimEnd('\r', '\n')
        val lines = cleanText.split('\n')
        val appContext = context.applicationContext
        try {
            writerExecutor.execute {
                try {
                    appendOnWriterThread(appContext, prefix, level, lines)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed appending to mpv log file", e)
                }
            }
        } catch (e: Exception) {
            // Executor rejected the task (cannot happen: it is never shut down) — drop the line.
            Log.w(TAG, "Failed queueing mpv log line", e)
        }
    }

    /** Writer thread only. Formats and writes the lines, flushing and rotating when due. */
    private fun appendOnWriterThread(context: Context, prefix: String, level: Int, lines: List<String>) {
        val file = getLogFile(context)
        ensureWriterOpen(file)

        val out = writer ?: return
        val timestamp = dateFormat.format(Date())
        val levelStr = levelToString(level)
        for (line in lines) {
            val formatted = if (prefix.isNotEmpty()) {
                "[$timestamp] [$levelStr] [$prefix] $line"
            } else {
                "[$timestamp] [$levelStr] $line"
            }
            out.write(formatted)
            out.newLine()
            approxBytes += formatted.toByteArray(Charsets.UTF_8).size + 1 // + newline
        }
        lineCount += lines.size
        linesSinceFlush += lines.size

        if (linesSinceFlush >= FLUSH_EVERY_LINES) {
            out.flush()
            linesSinceFlush = 0
        }

        if (lineCount > MAX_LINES || approxBytes > MAX_BYTES) {
            rotateOnWriterThread(file)
        }
    }

    /** Writer thread only. Opens the shared writer (counting existing lines/bytes first, once). */
    private fun ensureWriterOpen(file: File) {
        if (writer != null) return
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        ensureInitialized(file)
        // Append mode: bufferedWriter() alone would truncate the existing log.
        writer = FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8)
    }

    /** Writer thread only. Flushes and closes the shared writer; a later append reopens it. */
    private fun closeWriter() {
        try {
            writer?.flush()
        } catch (_: Exception) {
            // Best effort: still close below.
        }
        try {
            writer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed closing mpv log file", e)
        }
        writer = null
        linesSinceFlush = 0
    }

    private fun ensureInitialized(file: File) {
        if (lineCount < 0) {
            lineCount = if (file.exists()) {
                try {
                    file.useLines { it.count() }
                } catch (_: Exception) {
                    0
                }
            } else {
                0
            }
            approxBytes = if (file.exists()) file.length() else 0L
        }
    }

    /**
     * Rotates the log file so that it does not exceed [MAX_LINES] lines or [MAX_BYTES] bytes,
     * dropping the oldest lines first.
     *
     * Writer thread only. Same retained set as before (last ~500 lines / ~200 KB); only the
     * execution moved — previously this whole-file rewrite ran under the append lock on the
     * mpv log-callback thread. The writer is closed first so the rewrite never races a write.
     */
    private fun rotateOnWriterThread(file: File) {
        try {
            closeWriter()
            if (!file.exists()) {
                lineCount = 0
                approxBytes = 0L
                return
            }

            val allLines = file.readLines(Charsets.UTF_8)
            if (allLines.isEmpty()) {
                lineCount = 0
                approxBytes = 0L
                ensureWriterOpen(file)
                return
            }

            var startIndex = (allLines.size - MAX_LINES).coerceAtLeast(0)
            var totalBytes = 0L

            for (i in (allLines.size - 1) downTo 0) {
                val lineByteSize = allLines[i].toByteArray(Charsets.UTF_8).size + 1 // + newline
                if (totalBytes + lineByteSize > MAX_BYTES || i < startIndex) {
                    startIndex = i + 1
                    break
                }
                totalBytes += lineByteSize
            }

            val retainedLines = if (startIndex < allLines.size) {
                allLines.subList(startIndex, allLines.size)
            } else {
                emptyList()
            }

            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.bufferedWriter(Charsets.UTF_8).use { tempWriter ->
                for (line in retainedLines) {
                    tempWriter.write(line)
                    tempWriter.newLine()
                }
            }

            if (!tempFile.renameTo(file)) {
                file.delete()
                tempFile.renameTo(file)
            }
            lineCount = retainedLines.size
            approxBytes = retainedLines.sumOf { it.toByteArray(Charsets.UTF_8).size + 1L }
            ensureWriterOpen(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed rotating mpv log file", e)
            // Rotation failed mid-way: the writer is closed, so a later append reopens it and
            // the cap is re-checked then. Never leave a half-open writer behind.
            writer = null
            lineCount = -1
            approxBytes = 0L
        }
    }

    /**
     * Runs [action] on the writer thread and waits for it (bounded). Returns false on timeout
     * or failure instead of throwing — callers must stay usable when the writer is busy.
     * Must not be called from the writer thread itself.
     */
    private fun runSyncOnWriterThread(action: () -> Unit): Boolean {
        return try {
            writerExecutor.submit(Runnable(action)).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Timed out waiting for mpv log writer thread", e)
            false
        }
    }

    /**
     * Flushes any buffered lines to disk and closes the writer. Called when the engine is
     * released so no appended line is lost; a later engine instance reopens the writer lazily.
     * Safe to call repeatedly. Does not shut down the writer thread.
     */
    fun close() {
        runSyncOnWriterThread {
            try {
                closeWriter()
            } catch (e: Exception) {
                Log.w(TAG, "Failed closing mpv log file", e)
            }
        }
    }

    /**
     * Clears the log file and resets line count.
     */
    fun clear(context: Context) {
        val appContext = context.applicationContext
        runSyncOnWriterThread {
            try {
                closeWriter()
                val file = getLogFile(appContext)
                if (file.exists()) {
                    file.delete()
                }
                lineCount = 0
                approxBytes = 0L
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing mpv log file", e)
            }
        }
    }

    /**
     * Launches the system share sheet to share the mpv log file.
     * Uses [FileProvider] if configured in the app manifest; otherwise falls back to
     * sharing log text directly via [Intent.EXTRA_TEXT].
     *
     * Flushes the writer first (synchronously, on the writer thread) so the shared file
     * contains every line appended so far, not just the flushed prefix.
     */
    fun shareLog(context: Context) {
        val file = getLogFile(context)
        runSyncOnWriterThread {
            try {
                writer?.flush()
                linesSinceFlush = 0
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed flushing mpv log file for share", e)
            }
        }

        val appContext = context.applicationContext
        val authorities = listOf(
            "${appContext.packageName}.fileprovider",
            "${appContext.packageName}.provider",
        )

        var contentUri: Uri? = null
        for (authority in authorities) {
            try {
                contentUri = FileProvider.getUriForFile(appContext, authority, file)
                if (contentUri != null) break
            } catch (_: Exception) {
                // Provider authority not registered in AndroidManifest.xml
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MPV Log")
            if (contentUri != null) {
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newRawUri("mpv.log", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // Read on the writer thread so the snapshot cannot tear against a concurrent append.
                val logContent = try {
                    writerExecutor.submit(Callable {
                        try {
                            writer?.flush()
                            linesSinceFlush = 0
                            if (file.exists()) file.readText(Charsets.UTF_8) else ""
                        } catch (_: Exception) {
                            ""
                        }
                    }).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    ""
                }
                putExtra(Intent.EXTRA_TEXT, logContent)
            }
        }

        val chooser = Intent.createChooser(shareIntent, "Share MPV Log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (contentUri != null) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(chooser)
    }
}
