package com.mpx.player.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mpx.player.ui.theme.MpxTheme.colors.black
import com.mpx.player.ui.theme.MpxTheme.colors.textMuted
import com.mpx.player.ui.theme.MpxTheme.colors.textPrimary

/**
 * M2.5 dialog + sheet atoms (#106). Black containers, blue confirm
 * action via [MpxTextButton]. No screen logic.
 */

@Composable
fun MpxDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title, color = MpxTheme.colors.textPrimary) },
        text = body,
        confirmButton = {
            MpxTextButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = if (dismissText != null) {
            { MpxTextButton(text = dismissText, onClick = onDismiss) }
        } else {
            null
        },
        containerColor = MpxTheme.colors.black,
        titleContentColor = MpxTheme.colors.textPrimary,
        textContentColor = MpxTheme.colors.textMuted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpxBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MpxTheme.colors.black,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        content = content,
    )
}
