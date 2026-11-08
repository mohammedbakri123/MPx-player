package com.mpx.player.ui.screens.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.download.MpxDownloader
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #394: audio folder dialog — mirrors [DownloadDirectoryDialog],
 * but for the audio-only downloads folder. One text field; blank means
 * "same as the video folder" (the effective folder is shown under the
 * field), so clearing it goes back to today's behavior. A set name is
 * sanitized to a single segment on save, through the same helper the
 * downloader uses, so the stored value and the folder downloads go to
 * can never disagree.
 */
@Composable
fun AudioDirectoryDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var text by remember { mutableStateOf(settings.downloadAudioDirectory) }

    MpxDialog(
        title = stringResource(R.string.download_audio_directory_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            val clean = MpxDownloader.sanitizeAudioFolderName(text)
            settingsRepository.save(settings.copy(downloadAudioDirectory = clean))
            onDismiss()
        },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.download_directory_hint),
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
                text = stringResource(
                    R.string.download_directory_summary,
                    MpxDownloader.sanitizeAudioFolderName(text).ifBlank {
                        MpxDownloader.sanitizeDownloadFolderName(settings.downloadDirectory)
                    },
                ),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
