package com.mpx.player.ui.screens.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

enum class SortField(val labelRes: Int, val label: String) {
    NAME(R.string.sort_field_name, "Title / Name"),
    DATE(R.string.sort_field_date, "Date modified"),
    SIZE(R.string.sort_field_size, "File size"),
    COUNT(R.string.sort_field_count, "Video count"),
    DURATION(R.string.sort_field_duration, "Duration / Length"),
}

enum class ViewMode(val labelRes: Int, val label: String) {
    LIST(R.string.view_mode_list, "List view"),
    GRID(R.string.view_mode_grid, "Grid view"),
}

@Composable
fun SortViewDialog(
    currentSort: SortField,
    currentView: ViewMode,
    isFileView: Boolean = false,
    showAllFolders: Boolean = false,
    onSortChange: (SortField) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onFileViewChange: ((Boolean) -> Unit)? = null,
    onShowAllFoldersChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.sort_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = MpxTextPrimary,
            )
        },
        text = {
            Column {
                if (onFileViewChange != null) {
                    Text(
                        text = stringResource(R.string.sort_section_view_type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileViewChange(false) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !isFileView,
                            onClick = { onFileViewChange(false) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.view_type_folders), color = MpxTextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileViewChange(true) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isFileView,
                            onClick = { onFileViewChange(true) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.view_type_files), color = MpxTextPrimary)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxBorder,
                    )
                }

                Text(
                    text = stringResource(R.string.sort_section_view_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                ViewMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewChange(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentView == mode,
                            onClick = { onViewChange(mode) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(mode.labelRes), color = MpxTextPrimary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                )

                Text(
                    text = stringResource(R.string.sort_section_sort_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))

                val fieldsToShow = if (isFileView) {
                    listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.DURATION)
                } else {
                    listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.COUNT)
                }

                fieldsToShow.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortChange(field) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentSort == field,
                            onClick = { onSortChange(field) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(field.labelRes), color = MpxTextPrimary)
                    }
                }

                if (onShowAllFoldersChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxBorder,
                    )

                    Text(
                        text = if (isFileView) stringResource(R.string.section_files_header) else "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowAllFoldersChange(!showAllFolders) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = showAllFolders,
                            onCheckedChange = onShowAllFoldersChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MpxPrimary,
                                checkedTrackColor = MpxPrimary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files)
                                    else stringResource(R.string.list_show_all_folders),
                                color = MpxTextPrimary,
                            )
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files_desc)
                                    else stringResource(R.string.list_show_all_folders_desc),
                                color = MpxTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}
