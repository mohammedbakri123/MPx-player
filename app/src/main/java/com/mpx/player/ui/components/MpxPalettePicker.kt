package com.mpx.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxPalettes
import com.mpx.player.ui.theme.MpxPalette
import com.mpx.player.ui.theme.MpxRadiusPill
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Accent-colour palette picker (#147), shown on the Appearance page
 * (Settings → App Theme) only.
 *
 * Pattern modelled on mpvex's ThemePicker (Apache-2.0, studied
 * read-only): a horizontal row of mini UI preview cards, about
 * 90×140dp, each showing a tiny preview of the palette's accent over
 * the current theme's surfaces, with the palette name below. The
 * selected palette gets an accent border + elevation. Rebuilt in
 * MPx's own code and tokens — no mpvex source, assets or branding.
 *
 * A palette changes only the accent; the Dark true-black and Light
 * white surfaces in the preview come from the current theme, exactly
 * as they will look once applied.
 */
@Composable
fun MpxPalettePicker(
    selectedId: String,
    onPaletteSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Bring the current palette into view on first composition.
    LaunchedEffect(Unit) {
        val index = MpxPalettes.indexOfFirst { it.id == selectedId }
        if (index > 0) listState.scrollToItem(index)
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(MpxPalettes, key = { it.id }) { palette ->
            MpxPalettePreviewCard(
                palette = palette,
                isSelected = palette.id == selectedId,
                onClick = { onPaletteSelected(palette.id) },
            )
        }
    }
}

@Composable
private fun MpxPalettePreviewCard(
    palette: MpxPalette,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MpxTheme.colors
    val name = stringResource(palette.nameRes)

    Column(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = name),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mini UI preview: current theme's surfaces, this palette's accent.
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 140.dp)
                .shadow(
                    elevation = if (isSelected) 8.dp else 2.dp,
                    shape = MpxShapes.medium,
                    ambientColor = if (isSelected) palette.primary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    spotColor = if (isSelected) palette.primary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                )
                .clip(MpxShapes.medium)
                .background(colors.surface)
                .border(
                    width = if (isSelected) 2.dp else MpxHairline,
                    color = if (isSelected) palette.primary else colors.border,
                    shape = MpxShapes.medium,
                ),
        ) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(MpxShapes.small)
                        .background(colors.surfaceVariant),
                )
                // Middle mini card with a toggle pill in the accent.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(MpxShapes.small)
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 12.dp)
                                .clip(RoundedCornerShape(MpxRadiusPill))
                                .background(palette.primary),
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.primaryVariant),
                        )
                    }
                }
                // Bottom button bar in the accent.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(MpxShapes.small)
                        .background(palette.primary),
                )
                // Bottom accent dot.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette.primaryHover),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) palette.primary else colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
