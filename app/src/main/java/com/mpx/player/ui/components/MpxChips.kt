package com.mpx.player.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxPrimaryVariant
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

/**
 * M2.5 chip + badge atoms (#106). Selection is blue, sparingly;
 * unselected chips stay quiet. No screen logic.
 */

@Composable
fun MpxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        shape = MpxShapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MpxSurfaceVariant,
            labelColor = MpxTextSecondary,
            selectedContainerColor = MpxPrimaryVariant,
            selectedLabelColor = MpxTextPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MpxSurfaceVariant,
            selectedBorderColor = MpxPrimary,
        ),
    )
}

/** Small count pill (e.g. item counts). Blue only when highlighted. */
@Composable
fun MpxBadge(
    text: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MpxShapes.small,
        color = if (highlighted) MpxPrimary else MpxSurfaceVariant,
        contentColor = if (highlighted) MpxTextPrimary else MpxTextMuted,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = MpxSpaceS, vertical = 2.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
