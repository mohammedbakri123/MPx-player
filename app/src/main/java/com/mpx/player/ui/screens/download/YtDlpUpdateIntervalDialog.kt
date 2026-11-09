package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #405: how often to check for yt-dlp updates — Never, Daily,
 * or Weekly (Seal's update interval). Same save-on-tap pattern as
 * the audio-format dialog (#390). The setting already existed from
 * #333 (`ytdlpAutoUpdate` + `ytdlpUpdateIntervalMs`, read by
 * `maybeAutoUpdateYtDlp()`); it just had no UI. "Never" turns the
 * auto-check off — the version row's manual Update still works, and
 * the auto-check still runs once while the installed version is
 * unknown, so the Download tab can show it.
 */
@Composable
fun YtDlpUpdateIntervalDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val options = listOf("never", "daily", "weekly")

    MpxDialog(
        title = stringResource(R.string.download_update_interval_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column {
            options.forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { update(ytdlpUpdateIntervalValue(settings, value)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = ytdlpUpdateIntervalSelected(settings) == value,
                        onClick = { update(ytdlpUpdateIntervalValue(settings, value)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(ytdlpUpdateIntervalLabel(value), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private const val DAY_MS: Long = 24L * 60 * 60 * 1000
private const val WEEK_MS: Long = 7L * DAY_MS

/** The settings a tapped interval choice saves. */
private fun ytdlpUpdateIntervalValue(settings: AppSettings, value: String): AppSettings =
    when (value) {
        "never" -> settings.copy(ytdlpAutoUpdate = false)
        "daily" -> settings.copy(ytdlpAutoUpdate = true, ytdlpUpdateIntervalMs = DAY_MS)
        else -> settings.copy(ytdlpAutoUpdate = true, ytdlpUpdateIntervalMs = WEEK_MS)
    }

/** Which interval choice the current settings select. */
private fun ytdlpUpdateIntervalSelected(settings: AppSettings): String =
    when {
        !settings.ytdlpAutoUpdate -> "never"
        settings.ytdlpUpdateIntervalMs == DAY_MS -> "daily"
        else -> "weekly"
    }

/** Display label for an interval choice ("never" -> "Never"). */
internal fun ytdlpUpdateIntervalLabel(value: String): String = when (value) {
    "never" -> "Never"
    "daily" -> "Daily"
    else -> "Weekly"
}

/** Display label for the current interval settings (for the row subtitle). */
internal fun ytdlpUpdateIntervalLabel(settings: AppSettings): String =
    ytdlpUpdateIntervalLabel(ytdlpUpdateIntervalSelected(settings))
