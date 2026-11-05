package com.mpx.player.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxTheme

/**
 * Shared Preferences-page atoms (#158, mpvEx parity). The pattern follows
 * mpvEx's Preferences page — section headers in the accent colour, 28dp
 * rounded cards, icon rows with dividers — rebuilt with MPx's own tokens
 * (true-black dark surfaces, one identity-blue accent, hairline dividers).
 */

/** mpvEx-style section header: accent colour, label size, wide padding. */
@Composable
fun PreferenceSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MpxTheme.colors.primary,
    )
}

/** mpvEx-style preference card: 28dp corners, raised surface, no border. */
@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { content() }
}

/**
 * One drill-down row inside a [PreferenceCard]: bare 24dp icon, title +
 * subtitle, chevron. No tile background or border behind the icon — the
 * quiet treatment shared with the Me page rows (issue #162). 48dp minimum
 * touch height (12dp vertical padding + 24dp icon).
 */
@Composable
fun PreferenceRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MpxSpaceL, vertical = MpxSpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MpxTheme.colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.size(MpxSpaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MpxTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.size(MpxSpaceM))
            trailing()
        } else {
            Spacer(modifier = Modifier.size(MpxSpaceM))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MpxTheme.colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Divider between rows inside a [PreferenceCard]: starts under the text, past the bare icon + gap (16 + 24 + 12). */
@Composable
fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MpxTheme.colors.border,
        thickness = MpxHairline,
    )
}

/** A card holding several drill-down [SettingCategory] rows with dividers. */
@Composable
fun PreferenceCategoryCard(
    categories: List<SettingCategory>,
    onSelect: (String) -> Unit,
) {
    PreferenceCard {
        categories.forEachIndexed { index, category ->
            PreferenceRow(
                title = category.title,
                subtitle = category.subtitle,
                icon = category.icon,
                onClick = { onSelect(category.id) },
            )
            if (index < categories.lastIndex) PreferenceDivider()
        }
    }
}
