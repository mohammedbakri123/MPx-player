package com.mpx.player.ui.screens.local

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.model.VideoFile
import com.mpx.player.playback.PlaybackCoordinator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composition-scoped actions handler for [LocalScreen] (§6).
 * Groups the navigation, playback orchestration, selection actions,
 * and permission lifecycle callbacks.
 */
class LocalActions(
    val viewModel: LocalViewModel,
    val context: Context,
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val playbackCoordinator: PlaybackCoordinator?,
    val onOpenPlayer: () -> Unit,
    val getCurrentFolder: () -> MediaFolder?,
    val setCurrentFolder: (MediaFolder?) -> Unit,
    val getShowSearchPage: () -> Boolean,
    val setShowSearchPage: (Boolean) -> Unit,
) {
    fun updateViewMode(newMode: ViewMode) {
        viewModel.updateViewMode(newMode)
    }

    fun updateTreeMode(tree: Boolean) {
        viewModel.updateTreeMode(tree)
    }

    fun clearSelection() {
        viewModel.clearSelection()
    }

    fun toggleFolderSelection(folder: MediaFolder) {
        viewModel.toggleFolderSelection(folder)
    }

    fun toggleVideoSelection(video: VideoFile) {
        viewModel.toggleVideoSelection(video)
    }

    // #146: shared "back to root" — the back button and the breadcrumb's
    // Local segment both run this (clears the folder and its search state).
    // Issue #276: also clear the multi-select — at root in folder mode
    // selectedVideosInView() returns emptyList, so a folder's video
    // selection would survive as a lying "N selected" badge whose actions
    // resolve to nothing.
    fun goToRoot() {
        setCurrentFolder(null)
        viewModel.clearFolderViewState()
    }

    fun openFolder(folder: MediaFolder) {
        // Issue #276: a new folder is a new view — a selection made in
        // the previous folder no longer resolves against this folder's
        // videos, so clear it instead of leaving a lying badge behind.
        // Issue #186: one layout for Home and all folders — the folder
        // contents use the shared viewMode, no per-folder lookup.
        setCurrentFolder(folder)
        viewModel.openFolder(folder)
    }

    fun refreshFolderVideos(folderId: String) {
        viewModel.refreshFolderVideos(folderId)
    }

    fun refreshFolders(showAll: Boolean) {
        viewModel.refreshFolders(showAll)
    }

    // #375: back from a folder goes up exactly one level (MX/mpvEx),
    // not straight to root — unless the folder is already a root, in
    // which case there is no parent and it goes to the root list.
    fun goToParentFolder() {
        val parent = getCurrentFolder()?.let { viewModel.findParentFolder(it.path) }
        if (parent != null) openFolder(parent) else goToRoot()
    }

    // #378: startEntry resumes a specific video from the folder (quick-play
    // FAB) instead of starting at its first one. Defaults to null — every
    // other caller (tile taps, Play all) keeps the from-the-start behaviour.
    fun playFolder(folder: MediaFolder, startEntry: PlaybackHistoryItem? = null) {
        scope.launch(Dispatchers.IO) {
            val videos = viewModel.videosInFolder(folder.id)
            withContext(Dispatchers.Main) {
                if (videos.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.snackbar_no_playable_videos, folder.name),
                        )
                    }
                } else {
                    val startIndex = startEntry?.let { entry ->
                        videos.indexOfFirst { it.id == entry.mediaId || it.path == entry.mediaUri }
                    }?.takeIf { it >= 0 } ?: 0
                    playbackCoordinator?.playVideoFiles(videos, startIndex)
                    onOpenPlayer()
                }
            }
        }
    }

    fun playVideoWithFolder(video: VideoFile) {
        scope.launch(Dispatchers.IO) {
            val vids = if (video.bucketId.isNotBlank()) {
                val found = viewModel.videosInFolder(video.bucketId)
                if (found.isNotEmpty()) found else listOf(video)
            } else {
                listOf(video)
            }
            val startIndex = vids.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            withContext(Dispatchers.Main) {
                playbackCoordinator?.playVideoFiles(vids, startIndex)
                onOpenPlayer()
            }
        }
    }

    // Issue #378: the last played video in a list (see LocalViewModel).
    fun lastPlayedIn(videos: List<VideoFile>): VideoFile? =
        viewModel.lastPlayedIn(videos)

    fun clearMediaStateOnPermissionLoss() {
        // Issue #319: the folder is closed here, not in the ViewModel
        // — the screen owns currentFolder — so the ON_RESUME
        // refreshFolderVideos below is skipped instead of rescanning
        // a folder the app can no longer read.
        setCurrentFolder(null)
        viewModel.clearMediaStateOnPermissionLoss()
    }

    // #144: multi-select actions for the action bar. They resolve the
    // current selection to videos: directly selected videos PLUS — for
    // selected folders — the videos inside them (#233: both halves are
    // combined, never videos-first with a folders-only fallback).
    fun playSelection() {
        val currentFolder = getCurrentFolder()
        val showSearchPage = getShowSearchPage()
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val all = viewModel.resolveSelectionVideos(currentFolder, showSearchPage)
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.first().name,
                        ),
                    )
                } else {
                    playbackCoordinator?.playVideoFiles(all)
                    onOpenPlayer()
                }
                viewModel.clearSelection()
            }
        }
    }

    fun playSelectionFolder() {
        val currentFolder = getCurrentFolder()
        val showSearchPage = getShowSearchPage()
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isNotEmpty()) {
            val first = videos.first()
            scope.launch(Dispatchers.IO) {
                val vids = if (first.bucketId.isNotBlank()) {
                    viewModel.videosInFolder(first.bucketId)
                } else {
                    listOf(first)
                }
                val all = (vids + viewModel.videosInFolders(folders))
                    .distinctBy { it.id.ifBlank { it.path } }
                val startIndex = all.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
                withContext(Dispatchers.Main) {
                    playbackCoordinator?.playVideoFiles(all, startIndex)
                    onOpenPlayer()
                    viewModel.clearSelection()
                }
            }
        } else if (folders.isNotEmpty()) {
            playSelection()
        }
    }

    fun addSelectionToPlaylist() {
        val currentFolder = getCurrentFolder()
        val showSearchPage = getShowSearchPage()
        val videos = viewModel.selectedVideosInView(currentFolder, showSearchPage)
        val folders = viewModel.selectedFoldersInView(currentFolder, showSearchPage)
        if (videos.isEmpty() && folders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val all = viewModel.resolveSelectionVideos(currentFolder, showSearchPage)
            withContext(Dispatchers.Main) {
                if (all.isEmpty()) {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.snackbar_no_playable_videos,
                            folders.firstOrNull()?.name ?: "",
                        ),
                    )
                } else {
                    viewModel.showAddToPlaylist(all)
                }
            }
        }
    }

    fun toggleVideoFavourite(video: VideoFile) {
        viewModel.toggleVideoFavourite(video)
    }

    fun toggleSelectionFavourite() {
        val currentFolder = getCurrentFolder()
        val showSearchPage = getShowSearchPage()
        scope.launch {
            when (viewModel.toggleSelectionFavourite(currentFolder, showSearchPage)) {
                SelectionFavouriteOutcome.ADDED -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_added_to_favourites),
                )
                SelectionFavouriteOutcome.REMOVED -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_removed_favourites),
                )
                SelectionFavouriteOutcome.EMPTY -> snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.snackbar_no_playable_videos,
                        viewModel.selectedFoldersInView(currentFolder, showSearchPage)
                            .firstOrNull()?.name ?: "",
                    ),
                )
            }
        }
    }
}

@Composable
fun rememberLocalActions(
    viewModel: LocalViewModel,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    playbackCoordinator: PlaybackCoordinator?,
    onOpenPlayer: () -> Unit,
    getCurrentFolder: () -> MediaFolder?,
    setCurrentFolder: (MediaFolder?) -> Unit,
    getShowSearchPage: () -> Boolean,
    setShowSearchPage: (Boolean) -> Unit,
): LocalActions = remember(
    viewModel,
    context,
    scope,
    snackbarHostState,
    playbackCoordinator,
    onOpenPlayer,
) {
    LocalActions(
        viewModel = viewModel,
        context = context,
        scope = scope,
        snackbarHostState = snackbarHostState,
        playbackCoordinator = playbackCoordinator,
        onOpenPlayer = onOpenPlayer,
        getCurrentFolder = getCurrentFolder,
        setCurrentFolder = setCurrentFolder,
        getShowSearchPage = getShowSearchPage,
        setShowSearchPage = setShowSearchPage,
    )
}
