package com.mpx.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M2.5 shape tokens (#105). Restrained radii, standard placements.
 * No weird borders: shapes are soft rectangles and pills only.
 */
val MpxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Named radii for components that need one value, not a Shapes slot.
val MpxRadiusCard = 16.dp
val MpxRadiusTile = 12.dp
val MpxRadiusChip = 24.dp
val MpxRadiusPill = 100.dp
val MpxHairline = 0.5.dp
