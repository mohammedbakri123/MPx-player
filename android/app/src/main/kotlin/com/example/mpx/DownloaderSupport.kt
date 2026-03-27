package com.example.mpx

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.Locale

object DownloaderPythonBridge {
    fun ensureStarted(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    fun module() = Python.getInstance().getModule("downloader_bridge")

    fun cookiesPath(context: Context): String? {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.downloader_cookies_path", null)
    }

    fun defaultSharedQuality(context: Context): String {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.downloader_default_quality", "auto") ?: "auto"
    }
}

object PublicMediaExporter {
    private val relativeMoviesPath = Environment.DIRECTORY_MOVIES + "/mpxReels"

    fun exportToMovies(context: Context, sourcePath: String): String {
        val sourceFile = File(sourcePath)
        require(sourceFile.exists()) { "Downloaded file not found: $sourcePath" }

        val displayName = sourceFile.name
        val extension = sourceFile.extension.lowercase(Locale.US)
        val mimeType = when (extension) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            else -> "video/mp4"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeMoviesPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Files.getContentUri("external")
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create Movies/MPxReels entry")

        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Failed to open output stream for exported media")

        val publishValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, publishValues, null, null)

        sourceFile.delete()

        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(publicDir, "mpxReels/$displayName").absolutePath
    }
}
