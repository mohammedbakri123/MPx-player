package com.example.mpx

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ShareDownloadActivity : ComponentActivity() {

    private val qualityLabels = arrayOf("Auto", "1080p", "720p", "480p", "Audio only")
    private val qualityValues = mapOf(
        "Auto" to "best[ext=mp4]/best",
        "1080p" to "best[height<=1080][ext=mp4]/best[height<=1080]/best",
        "720p" to "best[height<=720][ext=mp4]/best[height<=720]/best",
        "480p" to "best[height<=480][ext=mp4]/best[height<=480]/best",
        "Audio only" to "bestaudio[ext=m4a]/bestaudio/best",
    )
    private val qualityIcons = arrayOf("⚡", "🎬", "📺", "📱", "🎵")

    private var sharedUrl: String? = null
    private var selectedIndex = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDownload()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            startDownload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedUrl = extractSharedUrl(intent)
        if (sharedUrl.isNullOrBlank()) {
            finish()
            return
        }

        selectedIndex = qualityIndexFor(DownloaderPythonBridge.defaultSharedQuality(this))
        showDownloadDialog()
    }

    private fun showDownloadDialog() {
        // Colors matching the Flutter app theme
        val primaryColor = Color.parseColor("#2563EB")
        val primaryLight = Color.parseColor("#DCEBFF")
        val surfaceColor = Color.parseColor("#FFFFFF")
        val onSurfaceColor = Color.parseColor("#10233F")
        val mutedColor = Color.parseColor("#6F7E97")
        val subtleBg = Color.parseColor("#F0F6FF")

        val dialogPadding = (24 * resources.displayMetrics.density).toInt()
        val cornerRadius = 24 * resources.displayMetrics.density

        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding)
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                setCornerRadius(cornerRadius)
            }
        }

        // Header icon
        val headerIcon = TextView(this).apply {
            text = "\uD83C\uDFAC"
            textSize = 40f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        dialogRoot.addView(headerIcon)

        // Title
        val titleText = TextView(this).apply {
            text = "Download to MPx"
            textSize = 20f
            setTextColor(onSurfaceColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }
        dialogRoot.addView(titleText)

        // Subtitle
        val subtitleText = TextView(this).apply {
            text = "Choose quality and start downloading"
            textSize = 14f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        dialogRoot.addView(subtitleText)

        // URL preview card
        val urlCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
            )
            background = GradientDrawable().apply {
                setColor(subtleBg)
                setCornerRadius(16 * resources.displayMetrics.density)
            }
        }
        val urlIcon = TextView(this).apply {
            text = "\uD83D\uDD17"
            textSize = 16f
            setPadding(0, 0, (12 * resources.displayMetrics.density).toInt(), 0)
        }
        val urlText = TextView(this).apply {
            text = sharedUrl
            textSize = 13f
            setTextColor(mutedColor)
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        urlCard.addView(urlIcon)
        urlCard.addView(urlText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dialogRoot.addView(urlCard)

        // Quality label
        val qualityLabel = TextView(this).apply {
            text = "Download Quality"
            textSize = 13f
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 8)
        }
        dialogRoot.addView(qualityLabel)

        // Quality selector
        val qualitySpinner = Spinner(this).apply {
            background = GradientDrawable().apply {
                setColor(primaryLight)
                setCornerRadius(16 * resources.displayMetrics.density)
            }
            val adapter = object : ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                qualityLabels.mapIndexed { i, label -> "  ${qualityIcons[i]}  $label" }
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val view = super.getView(position, convertView, parent)
                    (view as? TextView)?.apply {
                        setTextColor(onSurfaceColor)
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(
                            (16 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                            (16 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                        )
                    }
                    return view
                }

                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val view = super.getDropDownView(position, convertView, parent)
                    (view as? TextView)?.apply {
                        setTextColor(onSurfaceColor)
                        textSize = 15f
                        if (position == selectedIndex) {
                            setBackgroundColor(primaryLight)
                        } else {
                            setBackgroundColor(Color.TRANSPARENT)
                        }
                        setPadding(
                            (20 * resources.displayMetrics.density).toInt(),
                            (14 * resources.displayMetrics.density).toInt(),
                            (20 * resources.displayMetrics.density).toInt(),
                            (14 * resources.displayMetrics.density).toInt(),
                        )
                    }
                    return view
                }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            setAdapter(adapter)
            setSelection(selectedIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedIndex = position
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        val spinnerLayout = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialogRoot.addView(qualitySpinner, spinnerLayout)

        // Info text
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        val infoIcon = TextView(this).apply {
            text = "ℹ\uFE0F"
            textSize = 14f
            setPadding(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
        }
        val infoText = TextView(this).apply {
            text = "Downloads run in background with progress notification"
            textSize = 12f
            setTextColor(mutedColor)
        }
        infoRow.addView(infoIcon)
        infoRow.addView(infoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dialogRoot.addView(infoRow)

        // Button row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        // Cancel button
        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(mutedColor)
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
            )
            setOnClickListener { finish() }
        }
        buttonRow.addView(cancelButton)

        // Download button
        val downloadButton = Button(this).apply {
            text = "Download"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(primaryColor)
                setCornerRadius(16 * resources.displayMetrics.density)
            }
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
            )
            setOnClickListener { ensureNotificationsThenStart() }
        }
        buttonRow.addView(downloadButton)
        dialogRoot.addView(buttonRow)

        // Show as dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogRoot)
            .setCancelable(true)
            .setOnCancelListener { finish() }
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.6f)
        dialog.show()

        // Set dialog width
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        startDownload()
    }

    private fun startDownload() {
        val url = sharedUrl
        if (url == null) {
            finish()
            return
        }
        val label = qualityLabels[selectedIndex]
        val formatSelector = qualityValues[label] ?: qualityValues.getValue("Auto")

        val serviceIntent = ShareDownloadService.createIntent(this, url, formatSelector)
        ContextCompat.startForegroundService(this, serviceIntent)

        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        finish()
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
