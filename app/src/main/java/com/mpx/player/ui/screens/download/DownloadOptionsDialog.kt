package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Download network/filename options (issue #347): rate limit, proxy,
 * force IPv4, restrict filenames — Seal's downloadVideo flags
 * (DownloadUtil.kt:~700-730, JunkFood02/Seal, GPL-3.0). Same
 * save-on-tap pattern as DownloadQualityDialog: every choice persists
 * at once, so there is nothing to lose on dismiss — Done just closes
 * the dialog. The downloader reads the settings at request time, so a
 * change here applies to the next download (info, playlist probe and
 * execute requests all get the network flags; restrict-filenames is
 * download-only).
 *
 * Defaults keep today's behavior exactly: no limit, no proxy, IPv4
 * not forced, full filenames.
 */
@Composable
fun DownloadOptionsDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    MpxDialog(
        title = stringResource(R.string.download_options_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            OptionsSectionLabel(stringResource(R.string.download_options_rate_limit))
            OutlinedTextField(
                value = settings.downloadRateLimit,
                onValueChange = { update(settings.copy(downloadRateLimit = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "500K",
                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
                shape = MpxShapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                    focusedBorderColor = MpxTheme.colors.primary,
                    unfocusedBorderColor = MpxTheme.colors.border,
                    focusedTextColor = MpxTheme.colors.textPrimary,
                    unfocusedTextColor = MpxTheme.colors.textPrimary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_options_rate_limit_hint),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            OptionsSectionLabel(stringResource(R.string.download_options_proxy))
            OutlinedTextField(
                value = settings.downloadProxy,
                onValueChange = { update(settings.copy(downloadProxy = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "http://proxy:8080",
                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
                shape = MpxShapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                    focusedBorderColor = MpxTheme.colors.primary,
                    unfocusedBorderColor = MpxTheme.colors.border,
                    focusedTextColor = MpxTheme.colors.textPrimary,
                    unfocusedTextColor = MpxTheme.colors.textPrimary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_options_proxy_hint),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            OptionsSwitchRow(
                label = stringResource(R.string.download_options_force_ipv4),
                checked = settings.downloadForceIpv4,
                onCheckedChange = { update(settings.copy(downloadForceIpv4 = it)) },
            )
            OptionsSwitchRow(
                label = stringResource(R.string.download_options_restrict_filenames),
                checked = settings.downloadRestrictFilenames,
                onCheckedChange = { update(settings.copy(downloadRestrictFilenames = it)) },
            )
        }
    }
}

/** Section header inside the dialog: accent colour, like PreferenceSectionHeader. */
@Composable
private fun OptionsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MpxTheme.colors.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** One switch row (DownloadScreen's audio-only toggle shape): whole row tappable. */
@Composable
private fun OptionsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MpxTheme.colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}
