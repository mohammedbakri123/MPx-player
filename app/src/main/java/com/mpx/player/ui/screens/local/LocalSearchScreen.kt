package com.mpx.player.ui.screens.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxBadge
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.fuzzySearch

/**
 * Whole-page search (#142). Replaces the old inline Local search: one
 * search field over ALL folders and files, filtered instantly as the user
 * types from the already-loaded MediaStore lists — no rescan per
 * keystroke. Matching is hand-rolled fuzzy (see FuzzySearch): prefix hits
 * first, then substring, then subsequence, alphabetical inside a rank.
 * Back is exactly one step (the caller's BackHandler / back button
 * closes the page).
 *
 * Results use MPx's own tiles (#165), the same FolderItemRow /
 * VideoItemRow Local itself renders — no mpvEx cards anymore (#159's
 * MpxCard layouts are gone). Folders first, then files, each group under
 * its own header with a count badge; rows carry the Sort & View field
 * flags (size, date, count, duration, location, full name) exactly as
 * Local does, and the duration badge comes from VideoItemRow itself, so
 * search never renders its own badge. Long-press enters the same
 * multi-select Local uses (#144): the selection state and toggles are
 * passed in from LocalScreen, and the action bar resolves the selection
 * against the full lists while the search page is open — a tap while
 * selecting toggles instead of opening/playing. The play FAB hides
 * while selecting.
 *
 * This renders inside LocalScreen's composition on purpose: folder and
 * video taps reuse LocalScreen's own openFolder / playVideoWithFolder,
 * and the lists are the same live state LocalScreen already holds — no
 * data passing, no extra scan. There is no tile menu on the search page
 * — folder tap opens the folder, video tap plays.
 */
@Composable
fun LocalSearchScreen(
    folders: List<MediaFolder>,
    videos: List<VideoFile>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    showThumbnails: Boolean,
    showFieldSize: Boolean,
    showFieldDuration: Boolean,
    showFieldCount: Boolean = true,
    showFieldDate: Boolean = false,
    showFieldLocation: Boolean = true,
    showFullName: Boolean = false,
    selectedFolderIds: Set<String> = emptySet(),
    selectedVideoIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleFolderSelection: (MediaFolder) -> Unit = {},
    onToggleVideoSelection: (VideoFile) -> Unit = {},
    // Issue #321: search-result list rows get the favourite heart too.
    favourites: List<FavouriteItem> = emptyList(),
    onToggleVideoFavourite: (VideoFile) -> Unit = {},
    onFolderClick: (MediaFolder) -> Unit,
    onVideoClick: (VideoFile) -> Unit,
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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
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
                shape = MpxShapes.extraLarge,
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
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        // Folders first, then files — the same order Local
                        // uses, each group under its own header. The rows
                        // are Local's own tiles, so they look and behave
                        // exactly like the main lists.
                        if (matchedFolders.isNotEmpty()) {
                            item(contentType = "search_section") {
                                SearchSectionHeader(
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
                                    showCount = showFieldCount,
                                    showFullName = showFullName,
                                    // #144: tap toggles while selecting,
                                    // long-press enters selection mode.
                                    isSelected = selectedFolderIds.contains(
                                        folder.id.ifBlank { folder.path }
                                    ),
                                    onLongClick = { onToggleFolderSelection(folder) },
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleFolderSelection(folder)
                                        } else {
                                            onFolderClick(folder)
                                        }
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    color = MpxTheme.colors.border,
                                    thickness = MpxHairline,
                                )
                            }
                        }
                        if (matchedVideos.isNotEmpty()) {
                            item(contentType = "search_section") {
                                SearchSectionHeader(
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
                                    showThumbnails = showThumbnails,
                                    // Issue #321: search-result list rows
                                    // get the favourite heart too.
                                    isFavourite = favourites.any { it.mediaUri == video.path },
                                    onToggleFavourite = { onToggleVideoFavourite(video) },
                                    showSize = showFieldSize,
                                    showDate = showFieldDate,
                                    showDuration = showFieldDuration,
                                    showLocation = showFieldLocation,
                                    // #144: tap toggles while selecting,
                                    // long-press enters selection mode.
                                    isSelected = selectedVideoIds.contains(
                                        video.id.ifBlank { video.path }
                                    ),
                                    onLongClick = { onToggleVideoSelection(video) },
                                    showFullName = showFullName,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleVideoSelection(video)
                                        } else {
                                            onVideoClick(video)
                                        }
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 130.dp),
                                    color = MpxTheme.colors.border,
                                    thickness = MpxHairline,
                                )
                            }
                        }
                    }

                    // Blue rounded-square play FAB, bottom-right above the
                    // dock. Plays the first matched video; hidden when
                    // there is nothing playable to play, and while
                    // selecting (taps toggle then).
                    if (matchedVideos.isNotEmpty() && !isSelectionMode) {
                        FloatingActionButton(
                            onClick = { onVideoClick(matchedVideos.first()) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                            containerColor = MpxTheme.colors.primary,
                            // #148: the icon sits on the primary
                            // background, so it uses White, not
                            // textPrimary (near-black in Light).
                            contentColor = Color.White,
                            shape = MpxShapes.large,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_quick_play),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Group header for the search results: uppercase muted label + count
 * badge, Local's section-header look without the sort action (search
 * results are ranked by match, not sorted).
 */
@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MpxTheme.colors.textMuted,
        )
        Spacer(modifier = Modifier.width(8.dp))
        MpxBadge(text = count.toString())
    }
}
