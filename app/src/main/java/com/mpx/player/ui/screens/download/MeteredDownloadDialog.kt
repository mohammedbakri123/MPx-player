package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #380: metered-network gate — Seal's `CELLULAR_DOWNLOAD` default
 * (off) is "ask first". This dialog is the ask: it appears when a download
 * starts on a metered network (mobile data or a metered WiFi) while
 * `downloadAllowMetered` is off. "Download" runs the waiting download
 * once; the "Always allow" box saves `downloadAllowMetered = true` first,
 * so the next download on mobile data starts without asking. Cancel (or
 * back / outside tap) drops the waiting download — the picker or sign-in
 * dialog that opened it stays open, because the guarded action only runs
 * after confirmation.
 */
@Composable
fun MeteredDownloadDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    var alwaysAllow by remember { mutableStateOf(false) }
    MpxDialog(
        title = stringResource(R.string.download_metered_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.download_button),
        onConfirm = {
            if (alwaysAllow) {
                val settings = settingsRepository.load()
                settingsRepository.save(settings.copy(downloadAllowMetered = true))
            }
            onDownload()
            onDismiss()
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.download_metered_message),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { alwaysAllow = !alwaysAllow },
            ) {
                Checkbox(checked = alwaysAllow, onCheckedChange = { alwaysAllow = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.download_metered_always),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
