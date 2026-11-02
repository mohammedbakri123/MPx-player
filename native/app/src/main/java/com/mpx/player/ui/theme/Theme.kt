package com.mpx.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MPx Midnight theme, native version: pure black background,
// white text, one blue accent — the MX look, in MPx's own
// colours. No copied MX assets, icons, or branding.
private val MidnightScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF0E1118),
    surfaceVariant = Color(0xFF1A1F2B),
    primary = Color(0xFF3C8CF0),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2A3346),
)

@Composable
fun MpxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightScheme,
        content = content,
    )
}
