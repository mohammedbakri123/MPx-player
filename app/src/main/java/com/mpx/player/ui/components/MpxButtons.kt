package com.mpx.player.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxPrimaryVariant
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

/**
 * M2.5 button atoms (#106). Tokens only, no screen logic.
 * Primary action = identity blue, sparingly. Everything else is
 * quiet black/white. Minimum touch height 48dp.
 */

@Composable
fun MpxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MpxShapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MpxPrimary,
            contentColor = MpxTextPrimary,
            disabledContainerColor = MpxPrimaryVariant,
            disabledContentColor = MpxTextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text)
    }
}

@Composable
fun MpxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MpxShapes.large,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(MpxBorder),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MpxTextSecondary,
            disabledContentColor = MpxTextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text)
    }
}

@Composable
fun MpxTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MpxPrimary,
            disabledContentColor = MpxTextMuted,
        ),
    ) {
        Text(text)
    }
}

/** Pill FAB: one primary action per screen, blue, icon + label. */
@Composable
fun MpxPillFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MpxShapes.extraLarge,
        containerColor = MpxPrimary,
        contentColor = MpxTextPrimary,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
    )
}
