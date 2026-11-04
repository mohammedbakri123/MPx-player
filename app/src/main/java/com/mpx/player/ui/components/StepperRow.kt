package com.mpx.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R

/**
 * A labelled − / value / + stepper row, used by the subtitle timing
 * controls (issue #79, MX menu_subtitle sync/speed rows) in both the
 * player subtitle dialog and the Settings subtitle sub-page.
 *
 * [valueText] is formatted by the caller (e.g. "+0.50s", "1.0×") so this
 * row stays free of any timing semantics. [onReset] is null when the
 * value is already at its default, hiding the reset affordance.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    minusContentDescription: String,
    plusContentDescription: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = minusContentDescription)
            }
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = plusContentDescription)
            }
            if (onReset != null) {
                Spacer(Modifier.width(2.dp))
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.subtitle_timing_reset), fontSize = 12.sp)
                }
            }
        }
    }
}
