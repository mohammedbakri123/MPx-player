package com.mpx.player.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun MusicTabsSettingsSection(
    settings: AppSettings,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_tabs_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MpxTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val lastActiveSub = stringResource(R.string.settings_tab_last_active_sub)
    val isOnlyOneVisible = settings.visibleMusicTabsCount <= 1

    TabToggleRow(
        label = stringResource(R.string.music_tab_tracks),
        checked = settings.showMusicTracksTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicTracksTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("tracks", settings.showMusicTracksTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_playlists),
        checked = settings.showMusicPlaylistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicPlaylistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("playlists", settings.showMusicPlaylistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_albums),
        checked = settings.showMusicAlbumsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicAlbumsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("albums", settings.showMusicAlbumsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_artists),
        checked = settings.showMusicArtistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicArtistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("artists", settings.showMusicArtistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_folders),
        checked = settings.showMusicFoldersTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicFoldersTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("folders", settings.showMusicFoldersTab) },
    )
}

@Composable
fun TabToggleRow(
    label: String,
    checked: Boolean,
    isLastVisible: Boolean = false,
    subtitle: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}
