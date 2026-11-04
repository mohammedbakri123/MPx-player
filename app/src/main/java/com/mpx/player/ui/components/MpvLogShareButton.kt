package com.mpx.player.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mpx.player.playback.MpvLogFile

/**
 * Standalone button composable that shares the rotating mpv log file via the system share sheet.
 *
 * Designed to be wired into Settings / Diagnostics UI by parent merge.
 */
@Composable
fun MpvLogShareButton(
    modifier: Modifier = Modifier,
    label: String = "Share MPV Log",
    outlined: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    if (outlined) {
        OutlinedButton(
            onClick = {
                MpvLogFile.shareLog(context)
                onClick?.invoke()
            },
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = label,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label)
        }
    } else {
        Button(
            onClick = {
                MpvLogFile.shareLog(context)
                onClick?.invoke()
            },
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = label,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label)
        }
    }
}
