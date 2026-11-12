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
import com.mpx.player.ui.screens.settings.components.ChoiceRowWithSubtitle
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.LocaleHelper

@Composable
fun LanguageCategorySection(
    activeLanguage: String,
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val languageOptions = LocaleHelper.SUPPORTED_LANGUAGES
        languageOptions.forEach { option ->
            ChoiceRowWithSubtitle(
                title = stringResource(option.titleRes),
                subtitle = stringResource(option.descRes),
                selected = activeLanguage.equals(option.code, ignoreCase = true),
            ) {
                onUpdate(settings.copy(appLanguage = option.code))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.lang_settings_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
