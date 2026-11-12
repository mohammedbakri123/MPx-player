package com.mpx.player.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.screens.local.CreatePlaylistOutcome
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dialogs for [com.mpx.player.ui.screens.player.PlayerScreen] (§6).
 * Add-to-playlist, create-playlist, and subtitle load error alerts.
 */
@Composable
fun PlayerDialogs(
    viewModel: PlayerViewModel,
    showAddToPlaylistDialog: Boolean,
    pendingMedia: PlayableMedia?,
    customPlaylists: List<CustomPlaylist>,
    showCreatePlaylistDialog: Boolean,
    subtitleErrorDialogText: String?,
    onDismissSubtitleError: () -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current

    if (subtitleErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = onDismissSubtitleError,
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                Text(
                    text = stringResource(R.string.player_dialog_unsupported_subtitle),
                    color = MpxTheme.colors.destructive,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = subtitleErrorDialogText,
                    color = MpxTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissSubtitleError) {
                    Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    if (showAddToPlaylistDialog && pendingMedia != null) {
        val current = pendingMedia
        AddToPlaylistDialog(
            playlists = customPlaylists,
            mediaTitle = current.title,
            onDismiss = { viewModel.dismissAddToPlaylist() },
            onSelectPlaylist = { pl ->
                scope.launch {
                    val added = viewModel.addPendingToPlaylist(pl.id)
                    val msg = if (added) {
                        context.getString(R.string.music_added_to_playlist_msg, pl.name)
                    } else {
                        context.getString(R.string.music_already_in_playlist_msg, pl.name)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateNewPlaylist = {
                viewModel.openCreatePlaylist()
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylist() },
            initialIsVideoPlaylist = pendingMedia?.isVideo ?: false,
            onConfirm = { name, isVideo ->
                scope.launch {
                    when (val outcome = viewModel.createPlaylistAndAddPending(name, isVideo)) {
                        is CreatePlaylistOutcome.Created -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.snackbar_playlist_created, outcome.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        is CreatePlaylistOutcome.Added -> {
                            val msg = if (outcome.added > 0) {
                                context.getString(R.string.music_added_to_playlist_msg, outcome.name)
                            } else {
                                context.getString(R.string.music_already_in_playlist_msg, outcome.name)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        CreatePlaylistOutcome.Failed -> {}
                    }
                }
            },
        )
    }
}
