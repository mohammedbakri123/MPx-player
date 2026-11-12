package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun DecoderCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.decoder_mode_title),
            style = MaterialTheme.typography.titleSmall,
            color = MpxTheme.colors.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        listOf(
            "HW" to stringResource(R.string.decoder_hw),
            "HW+" to stringResource(R.string.decoder_hw_plus),
            "SW" to stringResource(R.string.decoder_sw),
        ).forEach { (mode, label) ->
            ChoiceRow(label, settings.decoderMode == mode) {
                onUpdate(settings.copy(decoderMode = mode))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.decoder_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
