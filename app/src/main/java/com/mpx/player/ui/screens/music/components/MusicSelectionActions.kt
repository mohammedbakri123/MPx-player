package com.mpx.player.ui.screens.music.components

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.mpx.player.R
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioArtist
import com.mpx.player.domain.model.AudioFolder
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.domain.model.Playlist
import com.mpx.player.domain.model.customPlaylistId
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.screens.music.MusicViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composition-scoped actions handler for [com.mpx.player.ui.screens.music.MusicScreen] (§6).
 * Groups navigation, playback orchestration, and the 5x3 selection-action matrix:
 * Tracks / Albums / Artists / Playlists / Folders.
 */
class MusicSelectionActions(
    val viewModel: MusicViewModel,
    val context: Context,
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val playbackCoordinator: PlaybackCoordinator?,
    val onOpenPlayer: () -> Unit,
) {
    // -- Tracks Selection Actions ---------------------------------------------

    fun selectedTracksInView(): List<AudioTrack> = viewModel.selectedTracksInView()

    fun playTrackSelection() {
        val selected = selectedTracksInView()
        if (selected.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(selected, startIndex = 0)
            onOpenPlayer()
            viewModel.clearTrackSelection()
        }
    }

    fun addTrackSelectionToPlaylist() {
        val selected = selectedTracksInView()
        if (selected.isNotEmpty()) {
            viewModel.showAddToPlaylist(selected)
        }
    }

    fun toggleTrackFavourite(track: AudioTrack) = viewModel.toggleTrackFavourite(track)

    fun toggleTrackSelectionFavourite() {
        val selected = selectedTracksInView()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                viewModel.clearTrackSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // -- Albums Selection Actions ---------------------------------------------

    fun albumTracks(album: AudioAlbum): List<AudioTrack> = viewModel.albumTracks(album)

    fun selectedAlbumsInView(): List<AudioAlbum> = viewModel.selectedAlbumsInView()

    fun selectedAlbumTracks(): List<AudioTrack> = viewModel.selectedAlbumTracks()

    fun playAlbumSelection() {
        val albumTrackList = selectedAlbumTracks()
        if (albumTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(albumTrackList, startIndex = 0)
            onOpenPlayer()
            viewModel.clearAlbumSelection()
        }
    }

    fun addAlbumSelectionToPlaylist() {
        val albumTrackList = selectedAlbumTracks()
        if (albumTrackList.isNotEmpty()) {
            viewModel.showAddToPlaylist(albumTrackList)
        }
    }

    fun toggleAlbumSelectionFavourite() {
        val selected = selectedAlbumTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                viewModel.clearAlbumSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // -- Artists Selection Actions --------------------------------------------

    fun artistTracks(artist: AudioArtist): List<AudioTrack> = viewModel.artistTracks(artist)

    fun selectedArtistsInView(): List<AudioArtist> = viewModel.selectedArtistsInView()

    fun selectedArtistTracks(): List<AudioTrack> = viewModel.selectedArtistTracks()

    fun playArtistSelection() {
        val artistTrackList = selectedArtistTracks()
        if (artistTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(artistTrackList, startIndex = 0)
            onOpenPlayer()
            viewModel.clearArtistSelection()
        }
    }

    fun addArtistSelectionToPlaylist() {
        val artistTrackList = selectedArtistTracks()
        if (artistTrackList.isNotEmpty()) {
            viewModel.showAddToPlaylist(artistTrackList)
        }
    }

    fun toggleArtistSelectionFavourite() {
        val selected = selectedArtistTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                viewModel.clearArtistSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // -- Playlists Selection Actions ------------------------------------------

    suspend fun playlistAudioTracks(playlist: Playlist): List<AudioTrack> =
        viewModel.playlistAudioTracks(playlist)

    suspend fun playlistPlayableMedia(playlist: Playlist): List<PlayableMedia> =
        viewModel.playlistPlayableMedia(playlist)

    fun selectedPlaylistsInView(): List<Playlist> = viewModel.selectedPlaylistsInView()

    fun playPlaylistSelection() {
        scope.launch(Dispatchers.IO) {
            val playable = selectedPlaylistsInView().flatMap { playlistPlayableMedia(it) }
            withContext(Dispatchers.Main) {
                if (playable.isNotEmpty()) {
                    playbackCoordinator?.playQueue(playable, startIndex = 0)
                    onOpenPlayer()
                    viewModel.clearPlaylistSelection()
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
            }
        }
    }

    fun addPlaylistSelectionToPlaylist() {
        scope.launch(Dispatchers.IO) {
            val audioTracks = selectedPlaylistsInView().flatMap { playlistAudioTracks(it) }
            withContext(Dispatchers.Main) {
                if (audioTracks.isNotEmpty()) {
                    viewModel.showAddToPlaylist(audioTracks)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
            }
        }
    }

    fun togglePlaylistSelectionFavourite() {
        scope.launch(Dispatchers.IO) {
            val selected = selectedPlaylistsInView().flatMap { playlistAudioTracks(it) }
            if (selected.isEmpty()) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
                }
                return@launch
            }
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                viewModel.clearPlaylistSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // -- Folders Selection Actions --------------------------------------------

    fun folderTracks(folder: AudioFolder): List<AudioTrack> = viewModel.folderTracks(folder)

    fun selectedFoldersInView(): List<AudioFolder> = viewModel.selectedFoldersInView()

    fun selectedFolderTracks(): List<AudioTrack> = viewModel.selectedFolderTracks()

    fun playFolderSelection() {
        val folderTrackList = selectedFolderTracks()
        if (folderTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(folderTrackList, startIndex = 0)
            onOpenPlayer()
            viewModel.clearFolderSelection()
        }
    }

    fun addFolderSelectionToPlaylist() {
        val folderTrackList = selectedFolderTracks()
        if (folderTrackList.isNotEmpty()) {
            viewModel.showAddToPlaylist(folderTrackList)
        }
    }

    fun toggleFolderSelectionFavourite() {
        val selected = selectedFolderTracks()
        if (selected.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val allFav = viewModel.toggleFavouriteForTracks(selected)
            withContext(Dispatchers.Main) {
                viewModel.clearFolderSelection()
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (allFav) R.string.snackbar_removed_favourites
                        else R.string.snackbar_added_to_favourites
                    )
                )
            }
        }
    }

    // -- Common Playback & Navigation Orchestration ---------------------------

    fun shuffleVisibleTracks(sortedTracks: List<AudioTrack>) {
        if (sortedTracks.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(sortedTracks.shuffled(), startIndex = 0)
            onOpenPlayer()
        }
    }

    fun playTrack(sortedTracks: List<AudioTrack>, index: Int) {
        playbackCoordinator?.playAudioQueue(sortedTracks, startIndex = index)
        onOpenPlayer()
    }

    fun playAlbum(album: AudioAlbum) {
        val albumTrackList = albumTracks(album)
        if (albumTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(albumTrackList, startIndex = 0)
            onOpenPlayer()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
            }
        }
    }

    fun playArtist(artist: AudioArtist) {
        val artistTrackList = artistTracks(artist)
        if (artistTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(artistTrackList, startIndex = 0)
            onOpenPlayer()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
            }
        }
    }

    fun playFolder(folder: AudioFolder) {
        val folderTrackList = folderTracks(folder)
        if (folderTrackList.isNotEmpty()) {
            playbackCoordinator?.playAudioQueue(folderTrackList, startIndex = 0)
            onOpenPlayer()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.music_nothing_to_play_msg))
            }
        }
    }

    fun handlePlaylistClick(
        playlist: Playlist,
        customPlaylists: List<CustomPlaylist>,
        onSelectCustomPlaylist: (CustomPlaylist) -> Unit,
    ) {
        val custom = customPlaylists.find { customPlaylistId(it.id) == playlist.id }
        if (custom != null) {
            onSelectCustomPlaylist(custom)
        } else {
            scope.launch(Dispatchers.IO) {
                val memberTracks = viewModel.playlistAudioTracks(playlist)
                withContext(Dispatchers.Main) {
                    if (memberTracks.isNotEmpty()) {
                        playbackCoordinator?.playAudioQueue(memberTracks, startIndex = 0)
                        onOpenPlayer()
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.playlist_empty_title))
                    }
                }
            }
        }
    }
}

@Composable
fun rememberMusicSelectionActions(
    viewModel: MusicViewModel,
    context: Context = LocalContext.current,
    scope: CoroutineScope = rememberCoroutineScope(),
    snackbarHostState: SnackbarHostState,
    playbackCoordinator: PlaybackCoordinator?,
    onOpenPlayer: () -> Unit,
): MusicSelectionActions = remember(viewModel, context, scope, snackbarHostState, playbackCoordinator, onOpenPlayer) {
    MusicSelectionActions(
        viewModel = viewModel,
        context = context,
        scope = scope,
        snackbarHostState = snackbarHostState,
        playbackCoordinator = playbackCoordinator,
        onOpenPlayer = onOpenPlayer,
    )
}
