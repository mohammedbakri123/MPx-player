package com.mpx.player.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.screens.local.components.formatSize
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.update.AppUpdateChecker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Issue #414: the update dialog — shown when the manual check finds
 * a newer release. Download writes the APK to `cacheDir/updates/`
 * (with progress; a cached APK whose version already covers the
 * release is reused, so a re-tap after a failed install does not
 * re-download ~88 MB), then the confirm button becomes Install and
 * opens the package installer. The dialog never auto-checks — the
 * check that opened it was the user's tap on the About row.
 */
@Composable
fun AppUpdateDialog(
    release: AppUpdateChecker.Release,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    val published = remember(release.publishedAt) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .parse(release.publishedAt)
            if (parsed != null) {
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed)
            } else {
                release.publishedAt.substringBefore("T")
            }
        } catch (_: Exception) {
            release.publishedAt.substringBefore("T")
        }
    }

    fun startDownload() {
        downloading = true
        failed = false
        progress = 0f
        scope.launch {
            val file = AppUpdateChecker.downloadApk(context, release) { downloaded, total ->
                if (total > 0) progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
            downloading = false
            if (file != null) {
                downloadedFile = file
            } else {
                failed = true
            }
        }
    }

    MpxDialog(
        title = stringResource(R.string.app_update_dialog_title),
        onDismiss = onDismiss,
        confirmText = stringResource(
            when {
                failed -> R.string.app_update_download
                downloadedFile != null -> R.string.app_update_install
                downloading -> R.string.app_update_downloading
                else -> R.string.app_update_download
            },
        ),
        onConfirm = {
            when {
                downloading -> { /* Tapping while downloading is a no-op. */ }
                downloadedFile != null -> {
                    val file = downloadedFile
                    if (file != null) {
                        if (AppUpdateChecker.installApk(context, file)) {
                            onDismiss()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.app_update_install_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                else -> startDownload()
            }
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            Text(
                text = stringResource(
                    R.string.app_update_dialog_body,
                    release.tag,
                    published,
                    if (release.apkSize > 0) formatSize(release.apkSize) else "?",
                ),
                color = MpxTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
            if (downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.app_update_downloading_percent,
                        (progress * 100).toInt(),
                    ),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_update_download_failed),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
