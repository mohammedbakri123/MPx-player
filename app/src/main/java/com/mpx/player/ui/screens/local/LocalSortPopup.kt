package com.mpx.player.ui.screens.local

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxFilterChip
import com.mpx.player.ui.components.MpxSlider
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

/**
 * The Local "Sort & View Options" popup (#156, mpvEx-style). It replaces
 * #141's LocalLayoutSheet bottom sheet: the header layout button opens this
 * AlertDialog instead.
 *
 * Pattern studied from mpvEx's SortDialog (Apache-2.0, read-only) and
 * rebuilt in MPx's own code: sort-by icon tiles, an A-Z/Z-A segmented
 * order row, View Mode | Layout side by side, grid-column sliders, and an
 * expandable Fields chip section. MPx tokens only; no mpvEx source copied.
 *
 * Scope: root scope edits the global sort/view; folder scope edits that
 * folder's view-mode override and the (global) folder sort. Sort direction
 * ([sortReverse]) is global in both scopes, as before. Secondary sort,
 * numeric/case/extension and the other advanced options stay in the old
 * SortViewDialog (section header / folder menu) — this popup is the quick
 * Sort & View picker, not the full sort dialog.
 *
 * The popup offers exactly three sort fields (Title / Date / Size). If the
 * stored primary sort is another field (Duration, Date/Duration, ...), no
 * tile is selected; tapping a tile switches to it. The full field set stays
 * available in SortViewDialog.
 *
 * Tree view (#164) shows the folder hierarchy inline (LocalFolderTree);
 * the Tree tile is live and persists via AppSettings.localTreeMode.
 */

// Sort direction, mpvEx-style: the ascending label depends on the field,
// because MPx's natural order is A-Z for names but newest/largest first
// for dates and sizes. sortReverse=false IS the natural order.
private fun isSortAscending(field: SortField, sortReverse: Boolean): Boolean =
    if (field == SortField.NAME) !sortReverse else sortReverse

private fun sortReverseFor(field: SortField, ascending: Boolean): Boolean =
    if (field == SortField.NAME) !ascending else ascending

private fun orderLabelRes(field: SortField, ascending: Boolean): Int = when (field) {
    SortField.NAME -> if (ascending) R.string.sort_order_az else R.string.sort_order_za
    SortField.DATE -> if (ascending) R.string.sort_order_oldest else R.string.sort_order_newest
    SortField.SIZE -> if (ascending) R.string.sort_order_smallest else R.string.sort_order_largest
    else -> if (ascending) R.string.sort_direction_ascending else R.string.sort_direction_descending
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalSortPopup(
    isFolderScope: Boolean,
    currentSort: SortField,
    onSortChange: (SortField) -> Unit,
    sortReverse: Boolean,
    onSortReverseChange: (Boolean) -> Unit,
    currentView: ViewMode,
    onViewChange: (ViewMode) -> Unit,
    // #164: Folder / Tree mode for the Local root folders list.
    isTreeMode: Boolean,
    onTreeModeChange: (Boolean) -> Unit,
    folderGridColumns: Int,
    onFolderGridColumnsChange: (Int) -> Unit,
    videoGridColumns: Int,
    onVideoGridColumnsChange: (Int) -> Unit,
    showFullName: Boolean,
    onShowFullNameChange: (Boolean) -> Unit,
    showFieldLocation: Boolean,
    onShowFieldLocationChange: (Boolean) -> Unit,
    showFieldCount: Boolean,
    onShowFieldCountChange: (Boolean) -> Unit,
    showFieldDuration: Boolean,
    onShowFieldDurationChange: (Boolean) -> Unit,
    showFieldSize: Boolean,
    onShowFieldSizeChange: (Boolean) -> Unit,
    showFieldDate: Boolean,
    onShowFieldDateChange: (Boolean) -> Unit,
    hasFolderOverride: Boolean,
    onResetToDefault: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var fieldsExpanded by remember { mutableStateOf(false) }
    val ascending = isSortAscending(currentSort, sortReverse)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Text(
                text = stringResource(R.string.sort_popup_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = MpxTheme.colors.textPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- Sort by: Title / Date / Size icon tiles ----
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.sort_popup_sort_by),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MpxTheme.colors.textPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SortTile(
                            icon = Icons.Filled.Title,
                            label = stringResource(R.string.sort_popup_sort_title),
                            selected = currentSort == SortField.NAME,
                            onClick = { onSortChange(SortField.NAME) },
                        )
                        SortTile(
                            icon = Icons.Filled.DateRange,
                            label = stringResource(R.string.sort_popup_sort_date),
                            selected = currentSort == SortField.DATE,
                            onClick = { onSortChange(SortField.DATE) },
                        )
                        SortTile(
                            icon = Icons.Filled.Sort,
                            label = stringResource(R.string.sort_popup_sort_size),
                            selected = currentSort == SortField.SIZE,
                            onClick = { onSortChange(SortField.SIZE) },
                        )
                    }
                }

                // ---- Sort order: ascending / descending segmented row ----
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val labels = listOf(
                        stringResource(orderLabelRes(currentSort, true)),
                        stringResource(orderLabelRes(currentSort, false)),
                    )
                    labels.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = labels.size,
                            ),
                            onClick = {
                                onSortReverseChange(sortReverseFor(currentSort, index == 0))
                            },
                            selected = (index == 0) == ascending,
                            icon = {
                                Icon(
                                    if (index == 0) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MpxTheme.colors.primary,
                                activeContentColor = Color.White,
                                activeBorderColor = MpxTheme.colors.primary,
                                inactiveContainerColor = MpxTheme.colors.surfaceVariant,
                                inactiveContentColor = MpxTheme.colors.textSecondary,
                                inactiveBorderColor = MpxTheme.colors.border,
                            ),
                        ) {
                            Text(label)
                        }
                    }
                }

                // ---- View Mode (Folder / Tree) | Layout (List / Grid) ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sort_popup_view_mode),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MpxTheme.colors.textPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top,
                        ) {
                            ModeTile(
                                icon = Icons.Filled.ViewModule,
                                label = stringResource(R.string.sort_popup_view_folder),
                                selected = !isTreeMode,
                                enabled = true,
                                comingSoon = false,
                                onClick = { onTreeModeChange(false) },
                            )
                            ModeTile(
                                icon = Icons.Filled.AccountTree,
                                label = stringResource(R.string.view_mode_tree),
                                selected = isTreeMode,
                                enabled = true,
                                comingSoon = false,
                                onClick = { onTreeModeChange(true) },
                            )
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MpxTheme.colors.border,
                        thickness = MpxHairline,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sort_popup_layout),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MpxTheme.colors.textPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top,
                        ) {
                            ModeTile(
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                label = stringResource(R.string.sort_popup_layout_list),
                                selected = currentView == ViewMode.LIST,
                                enabled = true,
                                comingSoon = false,
                                onClick = { onViewChange(ViewMode.LIST) },
                            )
                            ModeTile(
                                icon = Icons.Filled.GridView,
                                label = stringResource(R.string.sort_popup_layout_grid),
                                selected = currentView == ViewMode.GRID,
                                enabled = true,
                                comingSoon = false,
                                onClick = { onViewChange(ViewMode.GRID) },
                            )
                        }
                    }
                }

                // ---- Grid Columns: Folder Grid / Video Grid sliders ----
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.sort_popup_grid_columns),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MpxTheme.colors.textPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sort_popup_folder_grid),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = MpxTheme.colors.textSecondary,
                        )
                        MpxSlider(
                            value = folderGridColumns.toFloat(),
                            onValueChange = { onFolderGridColumnsChange(it.toInt()) },
                            valueRange = 1f..4f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.sort_popup_columns_count, folderGridColumns),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = MpxTheme.colors.textSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sort_popup_video_grid),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = MpxTheme.colors.textSecondary,
                        )
                        MpxSlider(
                            value = videoGridColumns.toFloat(),
                            onValueChange = { onVideoGridColumnsChange(it.toInt()) },
                            valueRange = 1f..4f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.sort_popup_columns_count, videoGridColumns),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = MpxTheme.colors.textSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }

                // ---- Fields: expandable FilterChip section ----
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fieldsExpanded = !fieldsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sort_popup_fields),
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MpxTheme.colors.textPrimary,
                            )
                            val activeLabels = mutableListOf<String>()
                            if (showFullName) activeLabels.add(stringResource(R.string.sort_popup_field_full_name))
                            if (showFieldLocation) activeLabels.add(stringResource(R.string.sort_popup_field_path))
                            if (showFieldCount) activeLabels.add(stringResource(R.string.sort_popup_field_total_videos))
                            if (showFieldDuration) activeLabels.add(stringResource(R.string.sort_popup_field_total_duration))
                            if (showFieldSize) activeLabels.add(stringResource(R.string.sort_popup_field_folder_size))
                            if (showFieldDate) activeLabels.add(stringResource(R.string.sort_popup_sort_date))
                            Text(
                                text = if (activeLabels.isEmpty()) {
                                    stringResource(R.string.sort_field_none)
                                } else {
                                    activeLabels.joinToString(", ")
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_field_full_name),
                                selected = showFullName,
                                onClick = { onShowFullNameChange(!showFullName) },
                            )
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_field_path),
                                selected = showFieldLocation,
                                onClick = { onShowFieldLocationChange(!showFieldLocation) },
                            )
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_field_total_videos),
                                selected = showFieldCount,
                                onClick = { onShowFieldCountChange(!showFieldCount) },
                            )
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_field_total_duration),
                                selected = showFieldDuration,
                                onClick = { onShowFieldDurationChange(!showFieldDuration) },
                            )
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_field_folder_size),
                                selected = showFieldSize,
                                onClick = { onShowFieldSizeChange(!showFieldSize) },
                            )
                            MpxFilterChip(
                                label = stringResource(R.string.sort_popup_sort_date),
                                selected = showFieldDate,
                                onClick = { onShowFieldDateChange(!showFieldDate) },
                            )
                        }
                    }
                }

                // ---- Folder scope: reset the view-mode override ----
                if (isFolderScope && hasFolderOverride && onResetToDefault != null) {
                    TextButton(onClick = onResetToDefault) {
                        Text(
                            text = stringResource(R.string.folder_view_reset_default),
                            color = MpxTheme.colors.primary,
                            fontSize = 14.sp,
                        )
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

/** 56dp rounded sort tile (Title / Date / Size), mpvEx pattern. */
@Composable
private fun SortTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(
                    if (selected) MpxTheme.colors.primary
                    else MpxTheme.colors.surfaceElevated,
                )
                .clickable(
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else MpxTheme.colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MpxTheme.colors.textPrimary
            else MpxTheme.colors.textSecondary,
        )
    }
}

/**
 * 44dp rounded mode tile (View Mode / Layout). A disabled tile (Tree) is
 * dimmed and never clickable; [comingSoon] adds the honest caption.
 */
@Composable
private fun ModeTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    comingSoon: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(
                    when {
                        selected && enabled -> MpxTheme.colors.primary
                        enabled -> MpxTheme.colors.surfaceElevated
                        else -> MpxTheme.colors.surfaceElevated.copy(alpha = 0.38f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    selected && enabled -> Color.White
                    enabled -> MpxTheme.colors.textSecondary
                    else -> MpxTheme.colors.textSecondary.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (selected && enabled) FontWeight.Medium else FontWeight.Normal,
            color = when {
                selected && enabled -> MpxTheme.colors.primary
                enabled -> MpxTheme.colors.textSecondary
                else -> MpxTheme.colors.textSecondary.copy(alpha = 0.38f)
            },
        )
        if (comingSoon) {
            Text(
                text = stringResource(R.string.sort_popup_tree_soon),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MpxTheme.colors.textMuted,
            )
        }
    }
}
