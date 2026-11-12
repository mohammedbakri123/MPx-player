package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SubtitleCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.subtitle_size_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Small" to stringResource(R.string.subtitle_size_small),
            "Normal" to stringResource(R.string.subtitle_size_normal),
            "Large" to stringResource(R.string.subtitle_size_large),
        ).forEach { (size, label) ->
            ChoiceRow(label, settings.subtitleSize == size) { onUpdate(settings.copy(subtitleSize = size)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_style_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "White" to stringResource(R.string.subtitle_style_white),
            "Yellow" to stringResource(R.string.subtitle_style_yellow),
            "Cyan" to stringResource(R.string.subtitle_style_cyan),
            "Default" to stringResource(R.string.subtitle_style_default),
        ).forEach { (styleKey, styleLabel) ->
            ChoiceRow(styleLabel, settings.subtitleStyle == styleKey) { onUpdate(settings.copy(subtitleStyle = styleKey)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_typeface_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Default" to stringResource(R.string.subtitle_typeface_default),
            "Sans" to stringResource(R.string.subtitle_typeface_sans),
            "Serif" to stringResource(R.string.subtitle_typeface_serif),
            "Mono" to stringResource(R.string.subtitle_typeface_mono),
        ).forEach { (face, label) ->
            ChoiceRow(label, settings.subtitleTypeface == face) { onUpdate(settings.copy(subtitleTypeface = face)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_text_color_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Preset" to stringResource(R.string.subtitle_text_preset),
            "White" to stringResource(R.string.player_subtitle_style_white),
            "Yellow" to stringResource(R.string.player_subtitle_style_yellow),
            "Cyan" to stringResource(R.string.player_subtitle_style_cyan),
            "Green" to stringResource(R.string.subtitle_color_green),
            "Magenta" to stringResource(R.string.subtitle_color_magenta),
            "Red" to stringResource(R.string.subtitle_color_red),
            "Black" to stringResource(R.string.subtitle_color_black),
        ).forEach { (color, label) ->
            ChoiceRow(label, settings.subtitleTextColor == color) { onUpdate(settings.copy(subtitleTextColor = color)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_bg_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Transparent" to stringResource(R.string.subtitle_bg_transparent),
            "Black" to stringResource(R.string.subtitle_bg_black),
            "SemiBlack" to stringResource(R.string.subtitle_bg_semi),
            "White" to stringResource(R.string.subtitle_bg_white),
        ).forEach { (bg, label) ->
            ChoiceRow(label, settings.subtitleBackgroundColor == bg) { onUpdate(settings.copy(subtitleBackgroundColor = bg)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_border_color_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Black" to stringResource(R.string.subtitle_border_black),
            "White" to stringResource(R.string.subtitle_border_white),
            "Yellow" to stringResource(R.string.subtitle_border_yellow),
            "Transparent" to stringResource(R.string.subtitle_border_transparent),
        ).forEach { (border, label) ->
            ChoiceRow(label, settings.subtitleBorderColor == border) { onUpdate(settings.copy(subtitleBorderColor = border)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_border_thickness_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "None" to stringResource(R.string.sort_field_none),
            "Thin" to stringResource(R.string.subtitle_thickness_thin),
            "Medium" to stringResource(R.string.subtitle_thickness_medium),
            "Thick" to stringResource(R.string.subtitle_thickness_thick),
        ).forEach { (thickness, label) ->
            ChoiceRow(label, settings.subtitleBorderThickness == thickness) { onUpdate(settings.copy(subtitleBorderThickness = thickness)) }
        }

        Spacer(Modifier.height(8.dp))
        ToggleRow(stringResource(R.string.subtitle_bold_label), settings.subtitleBold) { onUpdate(settings.copy(subtitleBold = it)) }
        ToggleRow(stringResource(R.string.subtitle_shadow_label), settings.subtitleShadow) { onUpdate(settings.copy(subtitleShadow = it)) }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_alignment_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Bottom" to stringResource(R.string.subtitle_align_bottom),
            "Center" to stringResource(R.string.subtitle_align_center),
            "Top" to stringResource(R.string.subtitle_align_top),
        ).forEach { (align, label) ->
            ChoiceRow(label, settings.subtitleAlignment == align) { onUpdate(settings.copy(subtitleAlignment = align)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_padding_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Small" to stringResource(R.string.subtitle_padding_small),
            "Medium" to stringResource(R.string.subtitle_padding_medium),
            "Large" to stringResource(R.string.subtitle_padding_large),
        ).forEach { (padding, label) ->
            ChoiceRow(label, settings.subtitleBottomPadding == padding) { onUpdate(settings.copy(subtitleBottomPadding = padding)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_scale_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "75" to stringResource(R.string.subtitle_scale_75),
            "100" to stringResource(R.string.subtitle_scale_100),
            "125" to stringResource(R.string.subtitle_scale_125),
            "150" to stringResource(R.string.subtitle_scale_150),
        ).forEach { (scale, label) ->
            ChoiceRow(label, settings.subtitleScale == scale) { onUpdate(settings.copy(subtitleScale = scale)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subtitle_ass_header), style = MaterialTheme.typography.labelMedium, color = MpxTheme.colors.textPrimary)
        listOf(
            "Default" to stringResource(R.string.subtitle_ass_default),
            "Scale" to stringResource(R.string.subtitle_ass_scale),
            "Force" to stringResource(R.string.subtitle_ass_force),
            "Strip" to stringResource(R.string.subtitle_ass_strip),
        ).forEach { (mode, label) ->
            ChoiceRow(label, settings.subtitleAssOverride == mode) { onUpdate(settings.copy(subtitleAssOverride = mode)) }
        }

        Spacer(Modifier.height(8.dp))
        StepperRow(
            label = stringResource(R.string.subtitle_sync_header),
            valueText = if (settings.subtitleDelayMs == 0) "0.00s" else String.format(Locale.US, "%+.2fs", settings.subtitleDelayMs / 1000.0),
            minusContentDescription = stringResource(R.string.subtitle_sync_decrease),
            plusContentDescription = stringResource(R.string.subtitle_sync_increase),
            onMinus = { onUpdate(settings.copy(subtitleDelayMs = (settings.subtitleDelayMs - 100).coerceIn(-10000, 10000))) },
            onPlus = { onUpdate(settings.copy(subtitleDelayMs = (settings.subtitleDelayMs + 100).coerceIn(-10000, 10000))) },
            onReset = if (settings.subtitleDelayMs != 0) {
                { onUpdate(settings.copy(subtitleDelayMs = 0)) }
            } else null,
        )
        StepperRow(
            label = stringResource(R.string.subtitle_speed_header),
            valueText = String.format(Locale.US, "%.1f×", settings.subtitleSpeed),
            minusContentDescription = stringResource(R.string.subtitle_speed_decrease),
            plusContentDescription = stringResource(R.string.subtitle_speed_increase),
            onMinus = {
                val stepped = ((settings.subtitleSpeed * 10).roundToInt() - 1) / 10f
                onUpdate(settings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onPlus = {
                val stepped = ((settings.subtitleSpeed * 10).roundToInt() + 1) / 10f
                onUpdate(settings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onReset = if (settings.subtitleSpeed != 1.0f) {
                { onUpdate(settings.copy(subtitleSpeed = 1.0f)) }
            } else null,
        )
    }
}
