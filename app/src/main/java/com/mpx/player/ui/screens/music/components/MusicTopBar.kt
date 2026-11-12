package com.mpx.player.ui.screens.music.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.screens.local.ViewMode
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSelectionTopBar(
    selectionCount: Int,
    onClearSelection: () -> Unit,
) {
    // #185: selection top bar — count + close. Back exits too.
    MpxCompactTopBar(
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_exit_selection),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.multiselect_selected_count,
                    selectionCount,
                ),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = MpxTheme.colors.textPrimary,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTopBar(
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    canShuffle: Boolean,
    onShuffle: () -> Unit,
    viewMode: ViewMode,
    onToggleViewMode: () -> Unit,
    onEqualizerClick: () -> Unit,
    onOpenSortPopup: () -> Unit,
    onRefresh: () -> Unit,
    onManageTabsClick: () -> Unit,
) {
    MpxCompactTopBar(
        title = {
            if (isSearching) {
                // #326: Local folder-view pattern — the title slot
                // becomes the search field while searching, so the
                // field gets the full bar width instead of sharing
                // a row with the shuffle button.
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.music_search_placeholder),
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 14.sp,
                        )
                    },
                    singleLine = true,
                    shape = MpxShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxTheme.colors.surfaceVariant,
                        unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                )
            } else {
                Text(
                    text = stringResource(R.string.music_screen_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        actions = {
            if (isSearching) {
                // #326: while searching only Close + Shuffle stay —
                // the field needs the width, and shuffle-the-matches
                // is the action that matters mid-search.
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close_search),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
                IconButton(
                    onClick = onShuffle,
                    enabled = canShuffle,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.music_shuffle_all),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            } else {
                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = stringResource(R.string.cd_toggle_view),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
                IconButton(onClick = onEqualizerClick) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = stringResource(R.string.music_cd_equalizer),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
                // #326: search and shuffle as quiet icon buttons, in
                // the Local folder-view order (search, then the play
                // action) before the overflow menu
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
                IconButton(
                    onClick = onShuffle,
                    enabled = canShuffle,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.music_shuffle_all),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.local_menu_view_sort)) },
                            onClick = {
                                menuExpanded = false
                                onOpenSortPopup()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.local_menu_refresh)) },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.music_menu_manage_tabs), color = MpxTheme.colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.Tab, contentDescription = null, tint = MpxTheme.colors.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onManageTabsClick()
                            },
                        )
                    }
                }
            }
        },
    )
}
