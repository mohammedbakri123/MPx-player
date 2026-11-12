package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.MusicTabsSettingsSection

@Composable
fun TabsCategorySection(
    settings: AppSettings,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MusicTabsSettingsSection(settings = settings, onToggleTab = onToggleTab)
    }
}
