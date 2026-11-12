package com.mpx.player.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.components.ChoiceRow
import com.mpx.player.ui.screens.settings.components.MusicTabsSettingsSection
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun ListCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings, Boolean) -> Unit,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.pref_default_folder_view_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        ChoiceRow(stringResource(R.string.view_mode_list), settings.localViewMode.equals("LIST", ignoreCase = true)) {
            onUpdate(settings.copy(localViewMode = "LIST"), true)
        }
        ChoiceRow(stringResource(R.string.view_mode_grid), settings.localViewMode.equals("GRID", ignoreCase = true)) {
            onUpdate(settings.copy(localViewMode = "GRID"), true)
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow(stringResource(R.string.settings_separate_view_title), settings.separateViewLayout) {
            onUpdate(settings.copy(separateViewLayout = it), true)
        }
        Text(
            stringResource(R.string.settings_separate_view_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ToggleRow(stringResource(R.string.list_file_view), settings.fileView) {
            onUpdate(settings.copy(fileView = it), true)
        }
        Text(
            stringResource(R.string.list_file_view_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        ToggleRow(stringResource(R.string.list_show_all_folders), settings.showAllFolders) {
            onUpdate(settings.copy(showAllFolders = it), false)
        }
        ToggleRow(stringResource(R.string.list_show_hidden), settings.showHiddenFiles) {
            onUpdate(settings.copy(showHiddenFiles = it), false)
        }
        ToggleRow(stringResource(R.string.list_show_thumbnails), settings.showThumbnails) {
            onUpdate(settings.copy(showThumbnails = it), false)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.border.copy(alpha = 0.5f), thickness = MpxHairline)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_tabs_header),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        MusicTabsSettingsSection(settings = settings, onToggleTab = onToggleTab)
    }
}
