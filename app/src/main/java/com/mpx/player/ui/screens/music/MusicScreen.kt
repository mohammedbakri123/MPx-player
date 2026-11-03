package com.mpx.player.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.data.repository.MediaRepositoryStub
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.Playlist
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = PlaybackManager.getInstance(LocalContext.current),
) {
    val repository = remember { MediaRepositoryStub() }
    val tracks = remember { repository.getAudioTracks() }
    val playlists = remember { repository.getPlaylists() }
    val albums = remember { repository.getAudioAlbums() }
    val artists = remember { repository.getAudioArtists() }
    val folders = remember { repository.getAudioFolders() }

    val tabs = listOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxSurface,
                ),
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Equalizer is scheduled for audio phase")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = MpxTextPrimary,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Music library scan wired in Phase 1")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MpxTextPrimary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Scrollable 5-Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MpxSurface,
                contentColor = MpxPrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MpxPrimary,
                    )
                },
                divider = {
                    HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selectedTab == index) MpxPrimary else MpxTextSecondary,
                            )
                        },
                    )
                }
            }

            // Search Bar + Shuffle All Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search songs...",
                            color = MpxTextMuted,
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MpxTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxSurfaceElevated,
                        unfocusedContainerColor = MpxSurfaceVariant,
                        focusedBorderColor = MpxPrimary,
                        unfocusedBorderColor = MpxBorder,
                        focusedTextColor = MpxTextPrimary,
                        unfocusedTextColor = MpxTextPrimary,
                    ),
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = {
                        playbackManager.playAudioQueue(tracks.shuffled(), startIndex = 0)
                        onOpenPlayer()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MpxPrimary,
                        contentColor = MpxTextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Shuffle All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Honest milestone notice
            HonestStatusBanner(
                text = "Structure matches MX Music tabs. Tap any track or 'Shuffle All' to open the Player.",
            )

            // Tab Content
            when (selectedTab) {
                0 -> TracksTabContent(
                    tracks = tracks,
                    onTrackClick = { index ->
                        playbackManager.playAudioQueue(tracks, startIndex = index)
                        onOpenPlayer()
                    },
                )
                1 -> PlaylistsTabContent(
                    playlists = playlists,
                    onPlaylistClick = {
                        playbackManager.playAudioQueue(tracks, startIndex = 0)
                        onOpenPlayer()
                    },
                )
                2 -> AlbumsTabContent(
                    albums = albums,
                    onAlbumClick = {
                        playbackManager.playAudioQueue(tracks, startIndex = 0)
                        onOpenPlayer()
                    },
                )
                3 -> ArtistsTabContent(
                    artists = artists,
                    onArtistClick = {
                        playbackManager.playAudioQueue(tracks, startIndex = 0)
                        onOpenPlayer()
                    },
                )
                4 -> FoldersTabContent(
                    folders = folders,
                    onFolderClick = {
                        playbackManager.playAudioQueue(tracks, startIndex = 0)
                        onOpenPlayer()
                    },
                )
            }
        }
    }
}

@Composable
private fun TracksTabContent(
    tracks: List<AudioTrack>,
    onTrackClick: (trackIndex: Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackClick(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpxSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MpxPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = MpxTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = track.durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextMuted,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { onTrackClick(index) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Track options",
                        tint = MpxTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MpxBorder,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<Playlist>,
    onPlaylistClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpxPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MpxPrimary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "New Playlist",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MpxPrimary,
                        ),
                    )
                    Text(
                        text = "Create custom playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
                    )
                }
            }
            HorizontalDivider(color = MpxBorder, thickness = 0.5.dp)
        }

        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpxSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MpxPrimary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MpxTextPrimary,
                    )
                    Text(
                        text = "${playlist.itemCount} songs • ${playlist.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MpxBorder,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AudioAlbum>,
    onAlbumClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MpxSurfaceVariant)
                    .clickable { onAlbumClick() }
                    .padding(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpxSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MpxPrimary.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MpxTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.artist} • ${album.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<AudioArtist>,
    onArtistClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MpxSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MpxPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MpxTextPrimary,
                    )
                    Text(
                        text = "${artist.trackCount} tracks • ${artist.albumCount} album",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 78.dp),
                color = MpxBorder,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<AudioFolder>,
    onFolderClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MpxSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MpxPrimary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MpxTextPrimary,
                    )
                    Text(
                        text = "${folder.trackCount} tracks • ${folder.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MpxBorder,
                thickness = 0.5.dp,
            )
        }
    }
}
