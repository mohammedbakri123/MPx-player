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
 * Issue #379: download folder dialog — the subfolder of the public
 * Downloads folder finished downloads land in. One text field; the name
 * is sanitized to a single segment on save ("MPx" when blank), through
 * the same helper the downloader uses, so the stored value and the
 * folder downloads go to can never disagree. The summary under the
 * field shows the folder the name resolves to.
 *
 * A folder change narrows the Downloaded list to the new folder's files
 * (the MediaStore query follows the setting); files in the old folder
 * stay on disk and history still finds them.
 */
@Composable
fun DownloadDirectoryDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var text by remember { mutableStateOf(settings.downloadDirectory) }

    MpxDialog(
        title = stringResource(R.string.download_directory_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            val clean = MpxDownloader.sanitizeDownloadFolderName(text)
            settingsRepository.save(settings.copy(downloadDirectory = clean))
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
                    MpxDownloader.sanitizeDownloadFolderName(text),
                ),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
