package com.mpx.player.ui.screens.local.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

/**
 * #252: formats a summed byte count for the folder subtitle while
 * searching. Mirrors MediaStoreRepository.formatSize exactly (same
 * thresholds, same "%.1f GB/MB/KB" units) so a filtered sum reads the
 * same as the stored whole-folder total it replaces.
 */
fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun LocalSelectionTopBar(
    selectionCount: Int,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MpxCompactTopBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_exit_selection),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.multiselect_selected_count, selectionCount),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = MpxTheme.colors.textPrimary,
            )
        },
    )
}

@Composable
fun LocalFolderTopBar(
    folder: MediaFolder,
    isFolderSearching: Boolean,
    folderSearchQuery: String,
    filteredFolderVideos: List<VideoFile>,
    onBackToParent: () -> Unit,
    onFolderSearchQueryChange: (String) -> Unit,
    onStartFolderSearching: () -> Unit,
    onCloseFolderSearching: () -> Unit,
    onOpenSortPopup: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MpxCompactTopBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBackToParent) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_folders),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
        },
        title = {
            if (isFolderSearching) {
                OutlinedTextField(
                    value = folderSearchQuery,
                    onValueChange = onFolderSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.folder_view_search_placeholder),
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 14.sp,
                        )
                    },
                    singleLine = true,
                    shape = MpxShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxTheme.colors.surfaceVariant,
                        unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                )
            } else {
                Column {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val count = filteredFolderVideos.size
                    val sizeStr = if (folderSearchQuery.isBlank()) {
                        folder.totalSizeFormatted
                    } else {
                        formatSize(filteredFolderVideos.sumOf { it.sizeBytes })
                    }
                    val subText = if (count == 1) {
                        stringResource(R.string.folder_view_subtitle_single, sizeStr)
                    } else {
                        stringResource(R.string.folder_view_subtitle, count, sizeStr)
                    }
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        actions = {
            if (isFolderSearching) {
                IconButton(onClick = onCloseFolderSearching) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close_search),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            }

            IconButton(onClick = onOpenSortPopup) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_layout_options),
                    tint = MpxTheme.colors.textPrimary,
                )
            }

            IconButton(onClick = onStartFolderSearching) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MpxTheme.colors.textPrimary,
                )
            }

            IconButton(onClick = onPlayAll) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.folder_menu_play_all),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
        },
    )
}

@Composable
fun LocalRootTopBar(
    onOpenSortPopup: () -> Unit,
    onOpenSearchPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MpxCompactTopBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.local_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = MpxTheme.colors.textPrimary,
            )
        },
        actions = {
            IconButton(onClick = onOpenSortPopup) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_layout_options),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
            IconButton(onClick = onOpenSearchPage) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
        },
    )
}
