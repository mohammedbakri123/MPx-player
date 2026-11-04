package com.mpx.player.playback

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manages native mpv log capture to a rotating plain-text file in the app cache directory.
 *
 * Characteristics:
 * - Plain text log file: `cacheDir/mpv.log`
 * - Each line prefixed with ISO-style timestamp `[yyyy-MM-dd HH:mm:ss.SSS]`, log level, and mpv prefix.
 * - Rotating: capped at last ~500 lines or ~200 KB (oldest lines dropped on rotation).
 * - Thread-safe appends and rotations.
 * - Supports sharing via system share sheet (using FileProvider if available, with graceful text fallback).
 */
object MpvLogFile {

    private const val TAG = "MpvLogFile"
    private const val LOG_FILE_NAME = "mpv.log"
    const val MAX_LINES: Int = 500
    const val MAX_BYTES: Long = 200 * 1024L // 200 KB

    private val lock = Any()
    private var lineCount: Int = -1

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
     * @param context Application or UI context
     * @param prefix mpv module/tag prefix (e.g. "cplayer", "ffmpeg", "mediacodec")
     * @param level mpv numeric log level
     * @param text Log message text
     */
    fun append(context: Context, prefix: String, level: Int, text: String) {
        if (text.isBlank()) return

        val cleanText = text.trimEnd('\r', '\n')
        val lines = cleanText.split('\n')
        val levelStr = levelToString(level)

        synchronized(lock) {
            val timestamp = dateFormat.format(Date())
            val formattedLines = lines.map { line ->
                if (prefix.isNotEmpty()) {
                    "[$timestamp] [$levelStr] [$prefix] $line"
                } else {
                    "[$timestamp] [$levelStr] $line"
                }
            }

            try {
                val file = getLogFile(context)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }

                ensureInitialized(file)

                FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                    for (line in formattedLines) {
                        writer.write(line)
                        writer.newLine()
                    }
                }
                lineCount += formattedLines.size

                if (lineCount > MAX_LINES || file.length() > MAX_BYTES) {
                    rotateLocked(file)
                }
                Unit
            } catch (e: Exception) {
                Log.w(TAG, "Failed appending to mpv log file", e)
            }
            Unit
        }
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
        }
    }

    /**
     * Rotates the log file so that it does not exceed [MAX_LINES] lines or [MAX_BYTES] bytes,
     * dropping the oldest lines first.
     */
    private fun rotateLocked(file: File) {
        try {
            if (!file.exists()) {
                lineCount = 0
                return
            }

            val allLines = file.readLines(Charsets.UTF_8)
            if (allLines.isEmpty()) {
                lineCount = 0
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
            tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (line in retainedLines) {
                    writer.write(line)
                    writer.newLine()
                }
            }

            if (!tempFile.renameTo(file)) {
                file.delete()
                tempFile.renameTo(file)
            }
            lineCount = retainedLines.size
        } catch (e: Exception) {
            Log.w(TAG, "Failed rotating mpv log file", e)
        }
    }

    /**
     * Clears the log file and resets line count.
     */
    fun clear(context: Context) {
        synchronized(lock) {
            try {
                val file = getLogFile(context)
                if (file.exists()) {
                    file.delete()
                }
                lineCount = 0
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing mpv log file", e)
            }
        }
    }

    /**
     * Launches the system share sheet to share the mpv log file.
     * Uses [FileProvider] if configured in the app manifest; otherwise falls back to
     * sharing log text directly via [Intent.EXTRA_TEXT].
     */
    fun shareLog(context: Context) {
        val file = getLogFile(context)
        synchronized(lock) {
            if (!file.exists()) {
                try {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed creating empty log file for share", e)
                }
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
                val logContent = synchronized(lock) {
                    try {
                        if (file.exists()) file.readText(Charsets.UTF_8) else ""
                    } catch (_: Exception) {
                        ""
                    }
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
