package com.mpx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.ui.components.AsyncVideoThumbnail
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.MpxCard
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.fuzzySearch

/**
 * Whole-page search (#142, restyled #159 to mpvEx's look). Replaces the
 * old inline Local search: one search field over ALL folders and files,
 * filtered instantly as the user types from the already-loaded MediaStore
 * lists — no rescan per keystroke. Matching is hand-rolled fuzzy (see
 * FuzzySearch): prefix hits first, then substring, then subsequence,
 * alphabetical inside a rank. Back is exactly one step (the caller's
 * BackHandler / back button closes the page).
 *
 * #159 presentation, from mpvEx's FileSystemSearchContent (read-only
 * study — rebuilt in MPx's own code, nothing copied): full-width rounded
 * search bar with a magnifier and X clear; folder results as rounded
 * cards (icon tile, title, path, "N Videos" + size chips); video results
 * as rounded cards (16:9 thumbnail with duration badge, title, size
 * chip); folders first, then videos, no dividers or group headers; a blue
 * rounded-square play FAB bottom-right that plays the first matched
 * video; the dock stays visible below.
 *
 * This renders inside LocalScreen's composition on purpose: folder and
 * video taps reuse LocalScreen's own openFolder / playVideoWithFolder,
 * and the lists are the same live state LocalScreen already holds — no
 * data passing, no extra scan. There is no tile menu on the search page
 * (mpvEx's search has none; the same actions live on the main Local
 * page) — folder tap opens the folder, video tap plays.
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
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 12.dp,
                            bottom = 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Folders first, then videos — mpvEx's order, no
                        // group headers or dividers; the card shapes tell
                        // them apart.
                        items(
                            items = matchedFolders,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "search_folder_card" },
                        ) { folder ->
                            SearchFolderCard(
                                folder = folder,
                                onClick = { onFolderClick(folder) },
                            )
                        }
                        items(
                            items = matchedVideos,
                            key = { it.id.ifBlank { it.path } },
                            contentType = { "search_video_card" },
                        ) { video ->
                            SearchVideoCard(
                                video = video,
                                showThumbnails = showThumbnails,
                                showFieldSize = showFieldSize,
                                showFieldDuration = showFieldDuration,
                                onClick = { onVideoClick(video) },
                            )
                        }
                    }

                    // Blue rounded-square play FAB, bottom-right above the
                    // dock. Plays the first matched video; hidden when
                    // there is nothing playable to play.
                    if (matchedVideos.isNotEmpty()) {
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
 * Folder result, mpvEx FolderCard's list shape: rounded card, 64dp
 * folder icon tile, title (2 lines), parent path below, "N Videos" +
 * size chips. Tap opens the folder and closes the search page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFolderCard(
    folder: MediaFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MpxCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MpxShapes.medium)
                    .background(MpxTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MpxTheme.colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Parent path without the folder's own name, as mpvEx
                // shows it.
                val parentPath = folder.path.substringBeforeLast("/", folder.path)
                if (parentPath.isNotEmpty()) {
                    Text(
                        text = parentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val hasChips = folder.videoCount > 0 ||
                    folder.totalSizeFormatted.isNotBlank()
                if (hasChips) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (folder.videoCount > 0) {
                            SearchChip(
                                stringResource(
                                    R.string.folder_item_videos_count,
                                    folder.videoCount,
                                ),
                            )
                        }
                        if (folder.totalSizeFormatted.isNotBlank()) {
                            SearchChip(folder.totalSizeFormatted)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Video result, mpvEx VideoCard's list shape: rounded card, 128dp 16:9
 * thumbnail with the duration badge bottom-right, title (2 lines), size
 * chip below. Tap plays the video.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchVideoCard(
    video: VideoFile,
    showThumbnails: Boolean,
    showFieldSize: Boolean,
    showFieldDuration: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MpxCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MpxShapes.medium)
                    .background(MpxTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncVideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 48.dp,
                    showThumbnails = showThumbnails,
                )
                if (showFieldDuration && video.formattedDuration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(MpxShapes.extraSmall)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showFieldSize && video.formattedSize.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SearchChip(video.formattedSize)
                    }
                }
            }
        }
    }
}

/** Small info chip, mpvEx's shape: 8dp rounded, surfaceVariant, 8x4 padding. */
@Composable
private fun SearchChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(MpxTheme.colors.surfaceVariant, MpxShapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MpxTheme.colors.textPrimary,
    )
}
