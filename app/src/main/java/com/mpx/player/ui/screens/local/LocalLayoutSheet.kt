package com.mpx.player.ui.screens.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxBottomSheet
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

/**
 * Grid tile size (#141). Medium keeps the previous Fixed(2) grid exactly;
 * Small fits more tiles per row, Large shows one wide tile per row.
 * List rows are unaffected: tile size is a grid-only setting in v1.
 */
enum class TileSize(val labelRes: Int, val columns: Int) {
    SMALL(R.string.tile_size_small, 3),
    MEDIUM(R.string.tile_size_medium, 2),
    LARGE(R.string.tile_size_large, 1);

    companion object {
        fun fromName(name: String): TileSize =
            values().find { it.name == name } ?: MEDIUM
    }
}

/**
 * The Local "View" bottom sheet (#141). It replaces the old header view
 * toggle buttons (root and folder view): layout, tile size and sort all live
 * here now. The sheet stays open after a choice, like SortViewDialog did,
 * so several options can change in one visit.
 *
 * Root scope edits the global mode via [onViewChange]; folder scope edits
 * that folder's override through the same callback (the caller owns the
 * distinction). [onResetToDefault] is only shown in folder scope when the
 * folder actually has an override.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLayoutSheet(
    isFolderScope: Boolean,
    currentView: ViewMode,
    onViewChange: (ViewMode) -> Unit,
    tileSize: TileSize,
    onTileSizeChange: (TileSize) -> Unit,
    currentSort: SortField,
    onSortChange: (SortField) -> Unit,
    currentSecondarySort: SortField,
    onSecondarySortChange: (SortField) -> Unit,
    hasFolderOverride: Boolean,
    onResetToDefault: (() -> Unit)?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    MpxBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.layout_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))

            SheetSectionLabel(stringResource(R.string.sort_section_view_mode))
            Spacer(Modifier.height(4.dp))
            ViewMode.values().forEach { mode ->
                SheetRadioRow(
                    label = stringResource(mode.labelRes),
                    selected = currentView == mode,
                    onClick = { onViewChange(mode) },
                )
            }

            Spacer(Modifier.height(16.dp))
            SheetSectionLabel(stringResource(R.string.layout_section_tile_size))
            Spacer(Modifier.height(4.dp))
            TileSize.values().forEach { size ->
                SheetRadioRow(
                    label = stringResource(size.labelRes),
                    selected = tileSize == size,
                    onClick = { onTileSizeChange(size) },
                )
            }

            Spacer(Modifier.height(16.dp))
            SheetSectionLabel(stringResource(R.string.sort_section_sort_by))
            Spacer(Modifier.height(4.dp))
            MX_SORT_FIELDS.forEach { field ->
                SheetRadioRow(
                    label = stringResource(field.labelRes),
                    selected = currentSort == field,
                    onClick = { onSortChange(field) },
                )
            }

            Spacer(Modifier.height(16.dp))
            SheetSectionLabel(stringResource(R.string.sort_section_secondary_sort))
            Spacer(Modifier.height(4.dp))
            (listOf(SortField.NONE) + MX_SORT_FIELDS).forEach { field ->
                SheetRadioRow(
                    label = stringResource(field.labelRes),
                    selected = currentSecondarySort == field,
                    onClick = { onSecondarySortChange(field) },
                )
            }

            if (isFolderScope && hasFolderOverride && onResetToDefault != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
                TextButton(onClick = onResetToDefault) {
                    Text(
                        text = stringResource(R.string.folder_view_reset_default),
                        color = MpxTheme.colors.primary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MpxTheme.colors.textSecondary,
        fontSize = 11.sp,
    )
}

@Composable
private fun SheetRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = MpxTheme.colors.textPrimary,
        )
    }
}
