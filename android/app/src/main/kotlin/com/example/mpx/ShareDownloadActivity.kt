package com.example.mpx

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity

class ShareDownloadActivity : ComponentActivity() {
    private val qualityLabels = arrayOf("Auto", "1080p", "720p", "480p", "Audio only")
    private val qualityValues = mapOf(
        "Auto" to "best[ext=mp4]/best",
        "1080p" to "best[height<=1080][ext=mp4]/best[height<=1080]/best",
        "720p" to "best[height<=720][ext=mp4]/best[height<=720]/best",
        "480p" to "best[height<=480][ext=mp4]/best[height<=480]/best",
        "Audio only" to "bestaudio[ext=m4a]/bestaudio/best",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUrl = extractSharedUrl(intent)
        if (sharedUrl.isNullOrBlank()) {
            finish()
            return
        }

        var selectedIndex = qualityIndexFor(DownloaderPythonBridge.defaultSharedQuality(this))

        AlertDialog.Builder(this)
            .setTitle("Download to MPxReels")
            .setMessage(sharedUrl)
            .setSingleChoiceItems(qualityLabels, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Download") { _, _ ->
                val label = qualityLabels[selectedIndex]
                val formatSelector = qualityValues[label] ?: qualityValues.getValue("Auto")
                startShareDownloadService(this, sharedUrl, formatSelector)
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startShareDownloadService(context: Context, url: String, formatSelector: String) {
        val intent = ShareDownloadService.createIntent(context, url, formatSelector)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) {
            return null
        }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val matcher = Patterns.WEB_URL.matcher(sharedText)
        return if (matcher.find()) matcher.group() else null
    }

    private fun qualityIndexFor(value: String): Int {
        return when (value) {
            "p1080" -> 1
            "p720" -> 2
            "p480" -> 3
            "audioOnly" -> 4
            else -> 0
        }
    }
}
