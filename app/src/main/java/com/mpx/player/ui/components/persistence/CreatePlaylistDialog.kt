package com.mpx.player.ui.components.persistence

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

/**
 * Dialog to create a new custom playlist persisted in Room.
 * Issue #230: also reused for renaming — pass [initialName] plus the
 * rename title and Save confirm; the text-field + save pattern is the same.
 * Issue #268: [onConfirm] also receives the playlist type. When
 * [showTypeSelector] is true the dialog offers a Video/Audio choice
 * (the shared Playlists page, which used to hardcode video); otherwise
 * the fixed [initialIsVideoPlaylist] is passed through, so the Local
 * (video), Music (audio) and Player (current item) flows keep their type.
 */
@Composable
fun CreatePlaylistDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    titleRes: Int = R.string.playlist_create_dialog_title,
    confirmRes: Int = R.string.action_create,
    initialIsVideoPlaylist: Boolean = false,
    showTypeSelector: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var isVideo by rememberSaveable { mutableStateOf(initialIsVideoPlaylist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.playlist_create_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.playlist_name_placeholder)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                // #268: the type choice — same clickable-row + RadioButton
                // pattern as SortViewDialog. Audio first and default:
                // a playlist made here must be addable from Music.
                if (showTypeSelector) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.playlist_create_type_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVideo = false }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !isVideo,
                            onClick = { isVideo = false },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_type_audio), color = MpxTheme.colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVideo = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isVideo,
                            onClick = { isVideo = true },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_type_video), color = MpxTheme.colors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), isVideo)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MpxTheme.colors.primary),
            ) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.textSecondary)
            }
        },
    )
}
