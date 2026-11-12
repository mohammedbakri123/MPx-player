package com.mpx.player.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.RepeatMode
import com.mpx.player.ui.screens.player.PlaybackSheetPage
import com.mpx.player.ui.screens.settings.parsePlayerMenuLayout
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * MX top-bar actions (issue #177): the user's chosen actions from
 * the Player Layout editor (#158), in their order, as icon-only
 * buttons inside the top bar row.
 */
@Composable
fun PlayerTopBarActions(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    state: PlaybackState,
    currentSettings: AppSettings,
    isFavourite: Boolean,
    onOpenSheet: (PlaybackSheetPage) -> Unit,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    playbackController: PlaybackController,
) {
    val layoutIds = remember(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds) {
        parsePlayerMenuLayout(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds)
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(layoutIds, key = { it }) { id ->
            PlayerTopBarActionItem(
                id = id,
                state = state,
                currentSettings = currentSettings,
                isFavourite = isFavourite,
                onOpenSheet = onOpenSheet,
                onToggleFavourite = onToggleFavourite,
                onEnterPip = onEnterPip,
                onToggleBackgroundPlay = onToggleBackgroundPlay,
                onAddToPlaylist = onAddToPlaylist,
                playbackController = playbackController,
            )
        }
    }
}

@Composable
fun PlayerTopBarActionItem(
    id: String,
    state: PlaybackState,
    currentSettings: AppSettings,
    isFavourite: Boolean,
    onOpenSheet: (PlaybackSheetPage) -> Unit,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    playbackController: PlaybackController,
) {
    val context = LocalContext.current
    when (id) {
        "audio" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Audiotrack,
            contentDescription = stringResource(R.string.playback_sheet_audio_track),
            active = false,
            onClick = { onOpenSheet(PlaybackSheetPage.AudioTracks) },
        )
        "subtitles" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Subtitles,
            contentDescription = stringResource(R.string.playback_sheet_subtitle_track),
            active = false,
            onClick = { onOpenSheet(PlaybackSheetPage.SubtitleTracks) },
        )
        "favourite" -> PlayerTopBarActionButton(
            icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavourite) R.string.player_cd_favourite_remove else R.string.player_cd_favourite_add,
            ),
            active = isFavourite,
            onClick = onToggleFavourite,
        )
        "shuffle" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = stringResource(R.string.player_cd_shuffle),
            active = state.shuffleEnabled,
            onClick = { playbackController.setShuffleEnabled(!state.shuffleEnabled) },
        )
        "repeat" -> PlayerTopBarActionButton(
            icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = stringResource(R.string.playback_sheet_repeat),
            active = state.repeatMode != RepeatMode.OFF,
            onClick = { playbackController.cycleRepeatMode() },
        )
        "pip" -> if (state.currentItem?.isVideo == true) PlayerTopBarActionButton(
            icon = Icons.Filled.PictureInPictureAlt,
            contentDescription = stringResource(R.string.player_menu_popup_play),
            active = false,
            onClick = onEnterPip,
        )
        "background" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Headphones,
            contentDescription = stringResource(R.string.playback_sheet_background_play),
            active = currentSettings.backgroundPlay,
            onClick = onToggleBackgroundPlay,
        )
        "speed" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Speed,
            contentDescription = stringResource(R.string.playback_sheet_speed_title),
            active = false,
            onClick = { onOpenSheet(PlaybackSheetPage.Speed) },
        )
        "aspect" -> PlayerTopBarActionButton(
            icon = Icons.Filled.AspectRatio,
            contentDescription = stringResource(R.string.playback_sheet_aspect),
            active = false,
            onClick = {
                val modes = AspectRatioMode.values()
                val next = modes[(modes.indexOf(state.aspectRatio) + 1) % modes.size]
                playbackController.setAspectRatio(next)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_menu_aspect, context.getString(next.labelRes)),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        "sleep" -> PlayerTopBarActionButton(
            icon = Icons.Filled.Timer,
            contentDescription = stringResource(R.string.player_sleep_timer),
            active = state.sleepTimerRemainingSeconds != null,
            onClick = { onOpenSheet(PlaybackSheetPage.SleepTimer) },
        )
        "playlist" -> PlayerTopBarActionButton(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            contentDescription = stringResource(R.string.player_menu_add_playlist),
            active = false,
            onClick = onAddToPlaylist,
        )
        else -> {}
    }
}

@Composable
fun PlayerTopBarActionButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    FloatingIsland(
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MpxTheme.colors.primary else MpxTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}
