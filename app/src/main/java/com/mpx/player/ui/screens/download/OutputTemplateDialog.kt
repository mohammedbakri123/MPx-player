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
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #398: output filename template dialog — mirrors
 * [DownloadDirectoryDialog], but for the yt-dlp `-o` template
 * (Seal's OUTPUT_TEMPLATE / CUSTOM_OUTPUT_TEMPLATE). One text
 * field; the template is saved as-is (no sanitizer — slashes in
 * a template are meaningful subfolders to yt-dlp). Blank means
 * the default template. The hint shows Seal's disambiguating
 * variant with the video id, for same-title videos.
 */
@Composable
fun OutputTemplateDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var text by remember { mutableStateOf(settings.downloadOutputTemplate) }

    MpxDialog(
        title = stringResource(R.string.download_output_template_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            settingsRepository.save(settings.copy(downloadOutputTemplate = text.trim()))
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
                        text = "%(title).200B.%(ext)s",
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
                text = "Example: %(title).200B [%(id)s].%(ext)s",
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
