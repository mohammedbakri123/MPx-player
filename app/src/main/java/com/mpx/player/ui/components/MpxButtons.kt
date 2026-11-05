package com.mpx.player.ui.components
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.mpx.player.ui.theme.MpxShapes

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
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MpxShapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MpxTheme.colors.primary,
            contentColor = MpxTheme.colors.textPrimary,
            disabledContainerColor = MpxTheme.colors.primaryVariant,
            disabledContentColor = MpxTheme.colors.textMuted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text)
    }
}

@Composable
fun MpxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MpxShapes.large,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(MpxTheme.colors.border),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MpxTheme.colors.textSecondary,
            disabledContentColor = MpxTheme.colors.textMuted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
        }
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
            contentColor = MpxTheme.colors.primary,
            disabledContentColor = MpxTheme.colors.textMuted,
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
        containerColor = MpxTheme.colors.primary,
        contentColor = MpxTheme.colors.textPrimary,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
    )
}
