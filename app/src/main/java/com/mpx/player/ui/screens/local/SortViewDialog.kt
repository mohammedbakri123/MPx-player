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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

enum class SortField(val label: String) {
    NAME("Title / Name"),
    DATE("Date modified"),
    SIZE("File size"),
    COUNT("Video count"),
}

enum class ViewMode(val label: String) {
    LIST("List view"),
    GRID("Grid view"),
}

@Composable
fun SortViewDialog(
    currentSort: SortField,
    currentView: ViewMode,
    onSortChange: (SortField) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxSurfaceVariant,
        title = {
            Text(
                text = "View & Sort Options",
                style = MaterialTheme.typography.titleLarge,
                color = MpxTextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "VIEW MODE",
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
                        Text(mode.label, color = MpxTextPrimary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                )

                Text(
                    text = "SORT BY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                SortField.values().forEach { field ->
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
                        Text(field.label, color = MpxTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
