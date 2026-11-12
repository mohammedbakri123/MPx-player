package com.mpx.player.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.ui.screens.settings.PreferenceCard
import com.mpx.player.ui.screens.settings.PreferenceCategoryCard
import com.mpx.player.ui.screens.settings.PreferenceSectionHeader
import com.mpx.player.ui.theme.MpxTheme

@Composable
fun SettingsCategoryList(
    categories: List<SettingCategory>,
    onSelectCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        // 28dp rounded search bar opening the working search page.
        item {
            PreferenceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        null,
                        tint = MpxTheme.colors.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.pref_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MpxTheme.colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_appearance)) }
        item {
            PreferenceCategoryCard(
                categories = listOf("appearance", "layout", "language", "tabs").mapNotNull { id -> categories.find { it.id == id } },
                onSelect = onSelectCategory,
            )
        }
        item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_playback)) }
        item {
            PreferenceCategoryCard(
                categories = listOf("player", "gestures", "pip").mapNotNull { id -> categories.find { it.id == id } },
                onSelect = onSelectCategory,
            )
        }
        item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_files)) }
        item {
            PreferenceCategoryCard(
                categories = listOf("folders", "list").mapNotNull { id -> categories.find { it.id == id } },
                onSelect = onSelectCategory,
            )
        }
        item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_media)) }
        item {
            PreferenceCategoryCard(
                categories = listOf("decoder", "subtitle").mapNotNull { id -> categories.find { it.id == id } },
                onSelect = onSelectCategory,
            )
        }
        item { PreferenceSectionHeader(text = stringResource(R.string.pref_section_advanced)) }
        item {
            PreferenceCategoryCard(
                categories = listOf("advanced", "about").mapNotNull { id -> categories.find { it.id == id } },
                onSelect = onSelectCategory,
            )
        }
    }
}
