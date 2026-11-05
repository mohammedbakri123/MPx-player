package com.mpx.player.ui.screens.local

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxBadge
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceS
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.fuzzySearch

/**
 * Whole-page search (#142). Replaces the old inline Local search: one
 * search field over ALL folders and files, filtered instantly as the
 * user types from the already-loaded MediaStore lists — no rescan per
 * keystroke. Matching is hand-rolled fuzzy (see FuzzySearch): prefix
 * hits first, then substring, then subsequence, alphabetical inside a
 * rank. Results are grouped Folders / Files. Back is exactly one step
 * (the caller's BackHandler / back button closes the page).
 *
 * This renders inside LocalScreen's composition on purpose: folder and
 * video taps reuse LocalScreen's own openFolder / playVideoWithFolder
 * and its folder/video menu dialogs, and the lists are the same live
 * state LocalScreen already holds — no data passing, no extra scan.
 */
@Composable
fun LocalSearchScreen(
    folders: List<MediaFolder>,
    videos: List<VideoFile>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    showThumbnails: Boolean,
    showFieldSize: Boolean,
    showFieldDate: Boolean,
    showFieldDuration: Boolean,
    showFieldLocation: Boolean,
    isFavourite: (VideoFile) -> Boolean,
    onFolderClick: (MediaFolder) -> Unit,
    onFolderMenuClick: (MediaFolder) -> Unit,
    onVideoClick: (VideoFile) -> Unit,
    onVideoMenuClick: (VideoFile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Instant filtering over the already-loaded lists; remember() keeps
    // the work to one pass per keystroke, no scan involved.
    val matchedFolders = remember(query, folders) {
        fuzzySearch(query, folders, nameOf = { it.name }, pathOf = { it.path })
    }
    val matchedVideos = remember(query, videos) {
        fuzzySearch(query, videos, nameOf = { it.title }, pathOf = { it.path })
    }
    val hasQuery = query.isNotBlank()
    val noResults = hasQuery && matchedFolders.isEmpty() && matchedVideos.isEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MpxTheme.colors.textPrimary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        stringResource(R.string.search_page_placeholder),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 14.sp,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_search),
                                tint = MpxTheme.colors.textPrimary,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MpxShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MpxTheme.colors.surfaceVariant,
                    unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                    focusedBorderColor = MpxTheme.colors.primary,
                    unfocusedBorderColor = MpxTheme.colors.border,
                    focusedTextColor = MpxTheme.colors.textPrimary,
                    unfocusedTextColor = MpxTheme.colors.textPrimary,
                ),
            )
        }

        when {
            !hasPermission -> {
                EmptyStateView(
                    title = stringResource(R.string.empty_permission_title),
                    message = stringResource(R.string.empty_permission_desc),
                    icon = Icons.Filled.Folder,
                    actionLabel = stringResource(R.string.empty_permission_action),
                    onActionClick = onRequestPermission,
                )
            }
            !hasQuery -> {
                EmptyStateView(
                    title = stringResource(R.string.search_page_hint_title),
                    message = stringResource(R.string.search_page_hint_desc),
                    icon = Icons.Filled.Search,
                )
            }
            noResults -> {
                EmptyStateView(
                    title = stringResource(R.string.empty_search_title),
                    message = stringResource(R.string.empty_search_desc, query.trim()),
                    icon = Icons.Filled.Search,
                    actionLabel = stringResource(R.string.empty_search_action),
                    onActionClick = { query = "" },
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (matchedFolders.isNotEmpty()) {
                        item(contentType = "search_group_header") {
                            SearchResultGroupHeader(
                                title = stringResource(R.string.local_section_folders),
                                count = matchedFolders.size,
                            )
                        }
                        items(
                            items = matchedFolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "search_folder_row" },
                        ) { folder ->
                            FolderItemRow(
                                folder = folder,
                                showThumbnails = showThumbnails,
                                showSize = showFieldSize,
                                showDate = showFieldDate,
                                onClick = { onFolderClick(folder) },
                                onMenuClick = { onFolderMenuClick(folder) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                    }
                    if (matchedVideos.isNotEmpty()) {
                        item(contentType = "search_group_header") {
                            SearchResultGroupHeader(
                                title = stringResource(R.string.section_files_header),
                                count = matchedVideos.size,
                            )
                        }
                        items(
                            items = matchedVideos,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "search_video_row" },
                        ) { video ->
                            VideoItemRow(
                                video = video,
                                isFavourite = isFavourite(video),
                                showThumbnails = showThumbnails,
                                showSize = showFieldSize,
                                showDate = showFieldDate,
                                showDuration = showFieldDuration,
                                showLocation = showFieldLocation,
                                onClick = { onVideoClick(video) },
                                onMenuClick = { onVideoMenuClick(video) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 128.dp),
                                color = MpxTheme.colors.border,
                                thickness = MpxHairline,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Group header for the search results: uppercase muted label + count badge. */
@Composable
private fun SearchResultGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MpxSpaceL, vertical = MpxSpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MpxTheme.colors.textMuted,
        )
        Spacer(modifier = Modifier.size(MpxSpaceS))
        MpxBadge(text = count.toString())
    }
}
