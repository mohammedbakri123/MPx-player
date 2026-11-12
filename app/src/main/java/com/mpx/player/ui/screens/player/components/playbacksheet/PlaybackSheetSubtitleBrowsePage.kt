package com.mpx.player.ui.screens.player.components.playbacksheet

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.SubtitleSiblingFinder
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MX-style subtitle file browser: lists the subtitle files in the
 * current video's folder (starting there, like MX's Open), with
 * subfolders to walk into and a ".." row to go up. Tapping a file
 * loads it exactly like a system-picker pick. When the folder cannot
 * be listed (no All files access on Android 11+) the page says so and
 * offers the grant; the system picker stays as the fallback at the
 * bottom for subtitles living anywhere else.
 */
@Composable
internal fun PlaybackSheetSubtitleBrowsePage(
    state: PlaybackState,
    playbackController: PlaybackController,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val startDir = remember(state.currentItem?.id) {
        state.currentItem?.filePath?.let { File(it).parentFile }
    }
    var currentDir by remember(state.currentItem?.id) { mutableStateOf(startDir) }
    var entries by remember { mutableStateOf<List<SubtitleSiblingFinder.BrowserEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val hasAccess = remember { SubtitleSiblingFinder.hasAllFilesAccess(context) }

    LaunchedEffect(currentDir) {
        val dir = currentDir
        loading = true
        entries = withContext(Dispatchers.IO) {
            dir?.let { SubtitleSiblingFinder.listSubtitleDir(it) }
        }
        loading = false
    }

    fun openAllFilesSettings() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: Exception) {
            // A ROM without that settings page: the system picker
            // below still works, so there is nothing else to do.
        }
    }

    fun pickFile(file: File) {
        when (val validation = SubtitleUtils.validateSubtitleFile(context, Uri.fromFile(file))) {
            is SubtitleValidationResult.Supported -> {
                playbackController.loadExternalSubtitle(
                    uri = validation.uri,
                    displayName = validation.displayName,
                    mimeType = validation.mimeType,
                )
                onDismiss()
            }
            is SubtitleValidationResult.Unsupported -> {
                // The browser only lists supported extensions.
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = currentDir?.absolutePath ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        if (!hasAccess) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MpxShapes.large,
                color = MpxTheme.colors.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.player_subtitle_all_files_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MpxTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_subtitle_all_files_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { openAllFilesSettings() }) {
                        Text(
                            text = stringResource(R.string.player_subtitle_all_files_allow),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (loading) {
            Text(
                text = stringResource(R.string.player_subtitle_browse_loading),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
        } else if (entries == null) {
            Text(
                text = stringResource(R.string.player_subtitle_browse_unavailable),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
        } else {
            val listed = entries ?: emptyList()
            if (currentDir?.parentFile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentDir = currentDir?.parentFile }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "..",
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (listed.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_subtitle_browse_empty),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                listed.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) {
                                    currentDir = entry.file
                                } else {
                                    pickFile(entry.file)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (entry.isDirectory) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = entry.file.name,
                            color = MpxTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                onDismiss()
                subtitleLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.FileUpload,
                contentDescription = null,
                tint = MpxTheme.colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_subtitle_browse_system_picker),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
