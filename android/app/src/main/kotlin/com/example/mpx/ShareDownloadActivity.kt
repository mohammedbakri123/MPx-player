package com.example.mpx

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var pendingUrl: String? = null
    private var pendingFormatSelector: String? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        startPendingShareDownload()
    }

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
                pendingUrl = sharedUrl
                pendingFormatSelector = formatSelector
                ensureNotificationsThenStart()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun ensureNotificationsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startPendingShareDownload()
    }

    private fun startPendingShareDownload() {
        val url = pendingUrl
        val formatSelector = pendingFormatSelector
        if (url == null || formatSelector == null) {
            finish()
            return
        }
        startShareDownloadService(this, url, formatSelector)
        Toast.makeText(this, "Share download started", Toast.LENGTH_SHORT).show()
        finish()
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
