package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.MpxPalettePicker
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.ui.theme.mpxPaletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppearanceCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        val themeOptions = listOf(
            "System" to stringResource(R.string.theme_option_system),
            "Dark" to stringResource(R.string.theme_option_dark),
            "Light" to stringResource(R.string.theme_option_light),
        )
        themeOptions.forEach { (themeCode, themeLabel) ->
            ChoiceRow(themeLabel, settings.appTheme == themeCode) {
                onUpdate(settings.copy(appTheme = themeCode))
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.theme_applied_snackbar, themeLabel),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.theme_color_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        val surfaceOptions = listOf(
            "TrueBlack" to stringResource(R.string.theme_surface_true_black),
            "Charcoal" to stringResource(R.string.theme_surface_charcoal),
            "Graphite" to stringResource(R.string.theme_surface_graphite),
            "Midnight" to stringResource(R.string.theme_surface_midnight),
            "Forest" to stringResource(R.string.theme_surface_forest),
            "Plum" to stringResource(R.string.theme_surface_plum),
        )
        surfaceOptions.forEach { (surfaceCode, surfaceLabel) ->
            ChoiceRow(surfaceLabel, settings.themeColor == surfaceCode) {
                onUpdate(settings.copy(themeColor = surfaceCode))
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.theme_applied_snackbar, surfaceLabel),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.palette_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        MpxPalettePicker(
            selectedId = settings.colorPalette,
            onPaletteSelected = { paletteId ->
                onUpdate(settings.copy(colorPalette = paletteId))
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.palette_applied_snackbar,
                            context.getString(mpxPaletteFor(paletteId).nameRes),
                        ),
                    )
                }
            },
        )
    }
}
