package com.mpx.player.ui.screens.local

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

enum class SortField(val labelRes: Int, val label: String) {
    NONE(R.string.sort_field_none, "None"),
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
    currentSecondarySort: SortField = SortField.NAME,
    currentView: ViewMode,
    isFileView: Boolean = false,
    showAllFolders: Boolean = false,
    separateViewLayout: Boolean = true,
    availableSortFields: List<SortField>? = null,
    availableSecondarySortFields: List<SortField>? = null,
    onSortChange: (SortField) -> Unit,
    onSecondarySortChange: ((SortField) -> Unit)? = null,
    onViewChange: (ViewMode) -> Unit,
    onFileViewChange: ((Boolean) -> Unit)? = null,
    onShowAllFoldersChange: ((Boolean) -> Unit)? = null,
    isFolderScope: Boolean = false,
    folderName: String? = null,
    hasFolderOverride: Boolean = false,
    isSetAsDefault: Boolean = false,
    onSetAsDefaultChange: ((Boolean) -> Unit)? = null,
    onResetToDefault: (() -> Unit)? = null,
    onSeparateViewLayoutChange: ((Boolean) -> Unit)? = null,

    // Fields options (persisted metadata columns)
    showFieldSize: Boolean = true,
    onShowFieldSizeChange: ((Boolean) -> Unit)? = null,
    showFieldDate: Boolean = false,
    onShowFieldDateChange: ((Boolean) -> Unit)? = null,
    showFieldDuration: Boolean = true,
    onShowFieldDurationChange: ((Boolean) -> Unit)? = null,
    showFieldLocation: Boolean = true,
    onShowFieldLocationChange: ((Boolean) -> Unit)? = null,
    showFieldCount: Boolean = true,
    onShowFieldCountChange: ((Boolean) -> Unit)? = null,

    // Advanced sort options
    sortReverse: Boolean = false,
    onSortReverseChange: ((Boolean) -> Unit)? = null,
    sortSecondaryReverse: Boolean = false,
    onSortSecondaryReverseChange: ((Boolean) -> Unit)? = null,
    sortNumeric: Boolean = true,
    onSortNumericChange: ((Boolean) -> Unit)? = null,
    sortCaseSensitive: Boolean = false,
    onSortCaseSensitiveChange: ((Boolean) -> Unit)? = null,
    sortIgnoreExtension: Boolean = true,
    onSortIgnoreExtensionChange: ((Boolean) -> Unit)? = null,

    onDismiss: () -> Unit,
) {
    var showFieldsDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }

    val dialogTitle = if (isFolderScope) {
        folderName?.let { "$it - ${stringResource(R.string.folder_view_dialog_title)}" }
            ?: stringResource(R.string.folder_view_dialog_title)
    } else {
        stringResource(R.string.sort_dialog_title)
    }

    val primaryFieldsToShow = availableSortFields ?: if (isFileView) {
        listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.DURATION)
    } else if (isFolderScope) {
        listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.DURATION)
    } else {
        listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.COUNT)
    }

    val secondaryFieldsToShow = availableSecondarySortFields ?: (listOf(SortField.NONE) + primaryFieldsToShow)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (onFileViewChange != null) {
                    Text(
                        text = stringResource(R.string.sort_section_view_type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(stringResource(R.string.view_type_folders), color = MaterialTheme.colorScheme.onSurface)
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
                        Text(stringResource(R.string.view_type_files), color = MaterialTheme.colorScheme.onSurface)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Text(
                    text = stringResource(R.string.sort_section_view_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(stringResource(mode.labelRes), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (isFolderScope && onSetAsDefaultChange != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetAsDefaultChange(!isSetAsDefault) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = isSetAsDefault,
                            onCheckedChange = onSetAsDefaultChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.folder_view_set_as_default),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.folder_view_set_as_default_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                if (isFolderScope && hasFolderOverride && onResetToDefault != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.folder_view_reset_default),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (onSeparateViewLayoutChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )

                    Text(
                        text = stringResource(R.string.sort_section_layout_settings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeparateViewLayoutChange(!separateViewLayout) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = separateViewLayout,
                            onCheckedChange = onSeparateViewLayoutChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.sort_separate_view_title), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.sort_separate_view_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Fields Row (Issue 59: selects which metadata columns rows show)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFieldsDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sort_section_fields),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        val activeLabels = mutableListOf<String>()
                        if (showFieldSize) activeLabels.add(stringResource(R.string.sort_field_show_size))
                        if (showFieldDate) activeLabels.add(stringResource(R.string.sort_field_show_date))
                        if (showFieldDuration && (isFileView || isFolderScope)) activeLabels.add(stringResource(R.string.sort_field_show_duration))
                        if (showFieldLocation && (isFileView || isFolderScope)) activeLabels.add(stringResource(R.string.sort_field_show_location))
                        if (showFieldCount && !isFileView && !isFolderScope) activeLabels.add(stringResource(R.string.sort_field_show_count))
                        val fieldsSummary = if (activeLabels.isEmpty()) {
                            stringResource(R.string.sort_field_none)
                        } else {
                            activeLabels.joinToString(", ")
                        }
                        Text(
                            text = fieldsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Primary Sort Section
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )

                Text(
                    text = stringResource(R.string.sort_section_sort_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))

                primaryFieldsToShow.forEach { field ->
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
                        Text(stringResource(field.labelRes), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Second Sort Section (Issue 59: tie-break sort selector)
                if (onSecondarySortChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )

                    Text(
                        text = stringResource(R.string.sort_section_secondary_sort),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = stringResource(R.string.sort_secondary_sort_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    secondaryFieldsToShow.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSecondarySortChange(field) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentSecondarySort == field,
                                onClick = { onSecondarySortChange(field) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(field.labelRes), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Advanced Sort Row (Issue 59: remaining real sort options)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sort_section_advanced),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        val advParts = mutableListOf<String>()
                        if (sortReverse) advParts.add(stringResource(R.string.sort_advanced_reverse_order))
                        if (sortNumeric) advParts.add(stringResource(R.string.sort_advanced_numeric))
                        if (sortCaseSensitive) advParts.add(stringResource(R.string.sort_advanced_case_sensitive))
                        if (sortIgnoreExtension && (isFileView || isFolderScope)) advParts.add(stringResource(R.string.sort_advanced_ignore_extension))
                        if (sortSecondaryReverse) advParts.add(stringResource(R.string.sort_advanced_secondary_reverse))
                        val advSummary = if (advParts.isEmpty()) {
                            stringResource(R.string.sort_advanced_summary_default)
                        } else {
                            advParts.joinToString(", ")
                        }
                        Text(
                            text = advSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isFolderScope && onShowAllFoldersChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )

                    Text(
                        text = if (isFileView) stringResource(R.string.section_files_header) else "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files)
                                    else stringResource(R.string.list_show_all_folders),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files_desc)
                                    else stringResource(R.string.list_show_all_folders_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    // Sub-dialog: Fields selection (which metadata columns rows show)
    if (showFieldsDialog) {
        FieldsSelectionDialog(
            isFileView = isFileView || isFolderScope,
            showFieldSize = showFieldSize,
            onShowFieldSizeChange = onShowFieldSizeChange,
            showFieldDate = showFieldDate,
            onShowFieldDateChange = onShowFieldDateChange,
            showFieldDuration = showFieldDuration,
            onShowFieldDurationChange = onShowFieldDurationChange,
            showFieldLocation = showFieldLocation,
            onShowFieldLocationChange = onShowFieldLocationChange,
            showFieldCount = showFieldCount,
            onShowFieldCountChange = onShowFieldCountChange,
            onDismiss = { showFieldsDialog = false },
        )
    }

    // Sub-dialog: Advanced sort options (remaining real sort options)
    if (showAdvancedDialog) {
        AdvancedSortDialog(
            isFileView = isFileView || isFolderScope,
            sortReverse = sortReverse,
            onSortReverseChange = onSortReverseChange,
            sortSecondaryReverse = sortSecondaryReverse,
            onSortSecondaryReverseChange = onSortSecondaryReverseChange,
            sortNumeric = sortNumeric,
            onSortNumericChange = onSortNumericChange,
            sortCaseSensitive = sortCaseSensitive,
            onSortCaseSensitiveChange = onSortCaseSensitiveChange,
            sortIgnoreExtension = sortIgnoreExtension,
            onSortIgnoreExtensionChange = onSortIgnoreExtensionChange,
            onDismiss = { showAdvancedDialog = false },
        )
    }
}

@Composable
private fun FieldsSelectionDialog(
    isFileView: Boolean,
    showFieldSize: Boolean,
    onShowFieldSizeChange: ((Boolean) -> Unit)?,
    showFieldDate: Boolean,
    onShowFieldDateChange: ((Boolean) -> Unit)?,
    showFieldDuration: Boolean,
    onShowFieldDurationChange: ((Boolean) -> Unit)?,
    showFieldLocation: Boolean,
    onShowFieldLocationChange: ((Boolean) -> Unit)?,
    showFieldCount: Boolean,
    onShowFieldCountChange: ((Boolean) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sort_fields_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.sort_fields_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))

                // File size
                if (onShowFieldSizeChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowFieldSizeChange(!showFieldSize) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = showFieldSize,
                            onCheckedChange = onShowFieldSizeChange,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sort_field_show_size), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Date modified
                if (onShowFieldDateChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowFieldDateChange(!showFieldDate) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = showFieldDate,
                            onCheckedChange = onShowFieldDateChange,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sort_field_show_date), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Duration (for individual files)
                if (isFileView && onShowFieldDurationChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowFieldDurationChange(!showFieldDuration) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = showFieldDuration,
                            onCheckedChange = onShowFieldDurationChange,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sort_field_show_duration), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Location / Folder (for individual files)
                if (isFileView && onShowFieldLocationChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowFieldLocationChange(!showFieldLocation) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = showFieldLocation,
                            onCheckedChange = onShowFieldLocationChange,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sort_field_show_location), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Video count (for folders)
                if (!isFileView && onShowFieldCountChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowFieldCountChange(!showFieldCount) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = showFieldCount,
                            onCheckedChange = onShowFieldCountChange,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sort_field_show_count), color = MaterialTheme.colorScheme.onSurface)
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

@Composable
private fun AdvancedSortDialog(
    isFileView: Boolean,
    sortReverse: Boolean,
    onSortReverseChange: ((Boolean) -> Unit)?,
    sortSecondaryReverse: Boolean,
    onSortSecondaryReverseChange: ((Boolean) -> Unit)?,
    sortNumeric: Boolean,
    onSortNumericChange: ((Boolean) -> Unit)?,
    sortCaseSensitive: Boolean,
    onSortCaseSensitiveChange: ((Boolean) -> Unit)?,
    sortIgnoreExtension: Boolean,
    onSortIgnoreExtensionChange: ((Boolean) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sort_advanced_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Reverse primary sort
                if (onSortReverseChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortReverseChange(!sortReverse) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = sortReverse,
                            onCheckedChange = onSortReverseChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_advanced_reverse_order),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.sort_advanced_reverse_order_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Natural numeric sorting
                if (onSortNumericChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortNumericChange(!sortNumeric) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = sortNumeric,
                            onCheckedChange = onSortNumericChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_advanced_numeric),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.sort_advanced_numeric_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Case-sensitive sorting
                if (onSortCaseSensitiveChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortCaseSensitiveChange(!sortCaseSensitive) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = sortCaseSensitive,
                            onCheckedChange = onSortCaseSensitiveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_advanced_case_sensitive),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.sort_advanced_case_sensitive_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Ignore file extension (for files)
                if (isFileView && onSortIgnoreExtensionChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortIgnoreExtensionChange(!sortIgnoreExtension) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = sortIgnoreExtension,
                            onCheckedChange = onSortIgnoreExtensionChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_advanced_ignore_extension),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.sort_advanced_ignore_extension_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Reverse secondary sort
                if (onSortSecondaryReverseChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSecondaryReverseChange(!sortSecondaryReverse) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = sortSecondaryReverse,
                            onCheckedChange = onSortSecondaryReverseChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_advanced_secondary_reverse),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.sort_advanced_secondary_reverse_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
