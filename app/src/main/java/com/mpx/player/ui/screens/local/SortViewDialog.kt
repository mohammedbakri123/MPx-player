package com.mpx.player.ui.screens.local
import com.mpx.player.ui.theme.MpxTheme

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.theme.MpxHairline

enum class SortField(val labelRes: Int, val label: String) {
    NONE(R.string.sort_field_none, "None"),
    NAME(R.string.sort_field_name, "Title / Name"),
    DATE(R.string.sort_field_date, "Date modified"),
    SIZE(R.string.sort_field_size, "File size"),
    COUNT(R.string.sort_field_count, "Video count"),
    DURATION(R.string.sort_field_duration, "Duration / Length"),
    // MX combined sort keys (issue #70, grounded in the decompiled MX sort set).
    // TITLE and LENGTH reuse the existing NAME / DURATION entries; SIZE is shared.
    // DATE_DURATION sorts by date modified, breaking ties by duration.
    DATE_DURATION(R.string.sort_field_date_duration, "Date / Duration"),
    // Resolution first (pixel count from MediaStore WIDTH/HEIGHT), then frame
    // rate where known (MediaStore exposes no frame-rate column, so it is 0
    // until a metadata pass populates it — never faked).
    RESOLUTION_FRAMERATE(R.string.sort_field_resolution_framerate, "Resolution / Frame rate"),
    // Status (recently added first), then file type (extension), then location (path).
    STATUS_TYPE_LOCATION(R.string.sort_field_status_type_location, "Status / Type / Location"),
}

/**
 * The six MX combined sort keys offered in the sort picker, in MX order.
 * Legacy entries (DATE, COUNT) stay in the enum so previously persisted
 * preferences keep working, but the picker offers the combined set.
 */
val MX_SORT_FIELDS: List<SortField> = listOf(
    SortField.NAME,
    SortField.DATE_DURATION,
    SortField.DURATION,
    SortField.RESOLUTION_FRAMERATE,
    SortField.SIZE,
    SortField.STATUS_TYPE_LOCATION,
)

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
    // Issue #70: Fields and Advanced are collapsible sections inside this ONE
    // dialog (MX pattern), not separate dialogs.
    var fieldsExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val dialogTitle = if (isFolderScope) {
        folderName?.let { "$it - ${stringResource(R.string.folder_view_dialog_title)}" }
            ?: stringResource(R.string.folder_view_dialog_title)
    } else {
        stringResource(R.string.sort_dialog_title)
    }

    val primaryFieldsToShow = availableSortFields ?: MX_SORT_FIELDS

    val secondaryFieldsToShow = availableSecondarySortFields ?: (listOf(SortField.NONE) + MX_SORT_FIELDS)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MpxTheme.colors.textPrimary,
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
                        color = MpxTheme.colors.textSecondary,
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
                        Text(stringResource(R.string.view_type_folders), color = MpxTheme.colors.textPrimary)
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
                        Text(stringResource(R.string.view_type_files), color = MpxTheme.colors.textPrimary)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxTheme.colors.border,
                    )
                }

                Text(
                    text = stringResource(R.string.sort_section_view_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTheme.colors.textSecondary,
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
                        Text(stringResource(mode.labelRes), color = MpxTheme.colors.textPrimary)
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
                                checkedThumbColor = MpxTheme.colors.primary,
                                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.folder_view_set_as_default),
                                color = MpxTheme.colors.textPrimary,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = stringResource(R.string.folder_view_set_as_default_desc),
                                color = MpxTheme.colors.textSecondary,
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
                            color = MpxTheme.colors.primary,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (onSeparateViewLayoutChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxTheme.colors.border,
                    )

                    Text(
                        text = stringResource(R.string.sort_section_layout_settings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTheme.colors.textSecondary,
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
                                checkedThumbColor = MpxTheme.colors.primary,
                                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.sort_separate_view_title), color = MpxTheme.colors.textPrimary)
                            Text(
                                stringResource(R.string.sort_separate_view_desc),
                                color = MpxTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Fields section (Issue 59: selects which metadata columns rows
                // show). Issue #70: collapsible inside this dialog, not a sub-dialog.
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fieldsExpanded = !fieldsExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sort_section_fields),
                            style = MaterialTheme.typography.titleMedium,
                            color = MpxTheme.colors.textPrimary,
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
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.rotate(if (fieldsExpanded) 90f else 0f),
                    )
                }

                if (fieldsExpanded) {
                    Spacer(Modifier.height(4.dp))
                    if (onShowFieldSizeChange != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowFieldSizeChange(!showFieldSize) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showFieldSize, onCheckedChange = onShowFieldSizeChange)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_field_show_size), color = MpxTheme.colors.textPrimary)
                        }
                    }
                    if (onShowFieldDateChange != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowFieldDateChange(!showFieldDate) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showFieldDate, onCheckedChange = onShowFieldDateChange)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_field_show_date), color = MpxTheme.colors.textPrimary)
                        }
                    }
                    if ((isFileView || isFolderScope) && onShowFieldDurationChange != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowFieldDurationChange(!showFieldDuration) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showFieldDuration, onCheckedChange = onShowFieldDurationChange)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_field_show_duration), color = MpxTheme.colors.textPrimary)
                        }
                    }
                    if ((isFileView || isFolderScope) && onShowFieldLocationChange != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowFieldLocationChange(!showFieldLocation) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showFieldLocation, onCheckedChange = onShowFieldLocationChange)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_field_show_location), color = MpxTheme.colors.textPrimary)
                        }
                    }
                    if (!isFileView && !isFolderScope && onShowFieldCountChange != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowFieldCountChange(!showFieldCount) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showFieldCount, onCheckedChange = onShowFieldCountChange)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_field_show_count), color = MpxTheme.colors.textPrimary)
                        }
                    }
                }

                // Primary Sort Section
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                )

                Text(
                    text = stringResource(R.string.sort_section_sort_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTheme.colors.textSecondary,
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
                        Text(stringResource(field.labelRes), color = MpxTheme.colors.textPrimary)
                    }
                }

                // Paired direction selectors (Issue #70, MX pattern): two
                // side-by-side choices bound to the existing reverse state.
                if (onSortReverseChange != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSortReverseChange(false) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !sortReverse, onClick = { onSortReverseChange(false) })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_direction_ascending), color = MpxTheme.colors.textPrimary)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSortReverseChange(true) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sortReverse, onClick = { onSortReverseChange(true) })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sort_direction_descending), color = MpxTheme.colors.textPrimary)
                        }
                    }
                }

                // Second Sort Section (Issue 59: tie-break sort selector)
                if (onSecondarySortChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxTheme.colors.border,
                    )

                    Text(
                        text = stringResource(R.string.sort_section_secondary_sort),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = stringResource(R.string.sort_secondary_sort_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.8f),
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
                            Text(stringResource(field.labelRes), color = MpxTheme.colors.textPrimary)
                        }
                    }
                }

                // Advanced section (Issue 59: remaining real sort options).
                // Issue #70: collapsible inside this dialog, not a sub-dialog.
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedExpanded = !advancedExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sort_section_advanced),
                            style = MaterialTheme.typography.titleMedium,
                            color = MpxTheme.colors.textPrimary,
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
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.rotate(if (advancedExpanded) 90f else 0f),
                    )
                }

                if (advancedExpanded) {
                    Spacer(Modifier.height(4.dp))
                    if (onSortReverseChange != null) {
                        AdvancedSwitchRow(
                            checked = sortReverse,
                            onCheckedChange = onSortReverseChange,
                            titleRes = R.string.sort_advanced_reverse_order,
                            descRes = R.string.sort_advanced_reverse_order_desc,
                        )
                    }
                    if (onSortNumericChange != null) {
                        AdvancedSwitchRow(
                            checked = sortNumeric,
                            onCheckedChange = onSortNumericChange,
                            titleRes = R.string.sort_advanced_numeric,
                            descRes = R.string.sort_advanced_numeric_desc,
                        )
                    }
                    if (onSortCaseSensitiveChange != null) {
                        AdvancedSwitchRow(
                            checked = sortCaseSensitive,
                            onCheckedChange = onSortCaseSensitiveChange,
                            titleRes = R.string.sort_advanced_case_sensitive,
                            descRes = R.string.sort_advanced_case_sensitive_desc,
                        )
                    }
                    if ((isFileView || isFolderScope) && onSortIgnoreExtensionChange != null) {
                        AdvancedSwitchRow(
                            checked = sortIgnoreExtension,
                            onCheckedChange = onSortIgnoreExtensionChange,
                            titleRes = R.string.sort_advanced_ignore_extension,
                            descRes = R.string.sort_advanced_ignore_extension_desc,
                        )
                    }
                    if (onSortSecondaryReverseChange != null) {
                        AdvancedSwitchRow(
                            checked = sortSecondaryReverse,
                            onCheckedChange = onSortSecondaryReverseChange,
                            titleRes = R.string.sort_advanced_secondary_reverse,
                            descRes = R.string.sort_advanced_secondary_reverse_desc,
                        )
                    }
                }

                if (!isFolderScope && onShowAllFoldersChange != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MpxTheme.colors.border,
                    )

                    Text(
                        text = if (isFileView) stringResource(R.string.section_files_header) else "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTheme.colors.textSecondary,
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
                                checkedThumbColor = MpxTheme.colors.primary,
                                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files)
                                    else stringResource(R.string.list_show_all_folders),
                                color = MpxTheme.colors.textPrimary,
                            )
                            Text(
                                text = if (isFileView) stringResource(R.string.list_show_all_files_desc)
                                    else stringResource(R.string.list_show_all_folders_desc),
                                color = MpxTheme.colors.textSecondary,
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
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AdvancedSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleRes: Int,
    descRes: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(titleRes),
                color = MpxTheme.colors.textPrimary,
                fontSize = 13.sp,
            )
            Text(
                text = stringResource(descRes),
                color = MpxTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
            )
        }
    }
}
