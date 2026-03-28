package com.example.mpx

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore.Audio
import android.provider.MediaStore
import android.provider.MediaStore.Video
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.Locale

object DownloaderPythonBridge {
    private var pythonStarted: Boolean = false
    
    fun ensureStarted(context: Context) {
        if (!pythonStarted && !Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
            pythonStarted = true
        }
    }

    fun module() = Python.getInstance().getModule("downloader_bridge")

    fun cookiesPath(context: Context): String? {
        val prefs = context.getSharedPreferences("flutter.shared_preferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.downloader_cookies_path", null)
    }

    fun defaultSharedQuality(context: Context): String {
        val prefs = context.getSharedPreferences("flutter.shared_preferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.downloader_default_quality", "auto") ?: "auto"
    }

    fun downloadPath(context: Context): String {
        val prefs = context.getSharedPreferences("flutter.shared_preferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.downloader_download_path", "/Movies/mpxReels")
            ?: "/Movies/mpxReels"
    }
}

object PublicMediaExporter {
    fun exportToPublicPath(context: Context, sourcePath: String, preferredPath: String): String {
        val sourceFile = File(sourcePath)
        require(sourceFile.exists()) { "Downloaded file not found: $sourcePath" }

        val displayName = sourceFile.name
        val extension = sourceFile.extension.lowercase(Locale.US)
        val mimeType = when (extension) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            else -> "video/mp4"
        }

        val relativePath = toRelativePath(preferredPath)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = when {
            mimeType.startsWith("audio/") -> Audio.Media.EXTERNAL_CONTENT_URI
            else -> Video.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create public media entry")

        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Failed to open output stream for exported media")

        val publishValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, publishValues, null, null)

        sourceFile.delete()

        val publicDir = Environment.getExternalStorageDirectory()
        return File(publicDir, "$relativePath/$displayName").absolutePath
    }

    private fun toRelativePath(preferredPath: String): String {
        val normalized = preferredPath.trim().ifEmpty { "/Movies/mpxReels" }
        val cleaned = normalized.removePrefix("/").trimEnd('/')
        if (cleaned.isEmpty()) {
            return Environment.DIRECTORY_MOVIES + "/mpxReels"
        }
        return cleaned
    }
}
