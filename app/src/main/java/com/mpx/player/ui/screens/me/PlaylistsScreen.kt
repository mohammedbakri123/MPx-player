package com.mpx.player.ui.screens.me
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import kotlinx.coroutines.launch

/**
 * Full Playlists page (#157). The Me Playlists tile used to open the
 * PlaylistsDialog; it now opens this page instead, with the same
 * Room-backed data and the same rows. Creating a playlist and the
 * per-playlist details still use the existing dialogs, now hosted by
 * this page instead of MeScreen. Back returns to Me — exactly one step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onNavigateBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    playbackManager: PlaybackManager? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val persistenceRepository = realPlaybackManager.persistenceRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.playlists_dialog_title, customPlaylists.size),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textSecondary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Text(
                            stringResource(R.string.playlists_btn_new),
                            color = MpxTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        if (customPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.playlists_empty_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(customPlaylists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlaylist = pl
                                scope.launch {
                                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MpxShapes.small)
                                .background(MpxTheme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MpxTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            val mediaType = if (pl.isVideoPlaylist) {
                                stringResource(R.string.media_type_video)
                            } else {
                                stringResource(R.string.media_type_audio)
                            }
                            Text(
                                text = stringResource(R.string.playlist_items_type_format, pl.itemCount, mediaType),
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    persistenceRepository.createPlaylist(name, isVideoPlaylist = true)
                    showCreatePlaylistDialog = false
                    snackbarHostState.showSnackbar("Playlist \"$name\" created")
                }
            },
        )
    }

    if (selectedPlaylist != null) {
        val pl = selectedPlaylist!!
        PlaylistDetailsDialog(
            playlist = pl,
            items = playlistItemsForDialog,
            onDismiss = { selectedPlaylist = null },
            onPlayAll = {
                val playable = playlistItemsForDialog.map { item ->
                    PlayableMedia(
                        id = item.mediaId,
                        title = item.title,
                        subtitle = item.subtitle,
                        uri = item.mediaUri,
                        isVideo = item.isVideo,
                        durationMs = item.durationMs,
                    )
                }
                selectedPlaylist = null
                realPlaybackManager.playQueue(playable)
                onOpenPlayer()
            },
            onPlayItem = { item ->
                selectedPlaylist = null
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        ),
                    ),
                )
                onOpenPlayer()
            },
            onRemoveItem = { itemId ->
                scope.launch {
                    persistenceRepository.removeItemFromPlaylist(itemId)
                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                }
            },
            onDeletePlaylist = {
                scope.launch {
                    persistenceRepository.deletePlaylist(pl.id)
                    selectedPlaylist = null
                    snackbarHostState.showSnackbar(context.getString(R.string.music_deleted_playlist_msg, pl.name))
                }
            },
            onRenamePlaylist = { newName ->
                scope.launch {
                    persistenceRepository.renamePlaylist(pl.id, newName)
                    // Issue #230: the dialog reads its title from this
                    // snapshot — update it so the new name shows at once.
                    // The Playlists list refreshes itself via the
                    // observePlaylists Flow.
                    selectedPlaylist = pl.copy(name = newName)
                }
            },
        )
    }
}
