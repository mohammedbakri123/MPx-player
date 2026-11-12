package com.mpx.player.ui.screens.player.components.playbacksheet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.RepeatMode
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.screens.player.PlaybackSheetPage
import com.mpx.player.ui.screens.settings.parsePlayerMenuLayout

@Composable
internal fun PlaybackSheetMainPage(
    state: PlaybackState,
    currentSettings: AppSettings,
    onPageChange: (PlaybackSheetPage?) -> Unit,
    playbackController: PlaybackController,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onReplayTutorial: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val layoutIds = remember(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds) {
        parsePlayerMenuLayout(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds)
    }
    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_title))
    layoutIds.forEach { id ->
        key(id) {
            when (id) {
                "speed" -> PlaybackSheetRow(
                    icon = Icons.Filled.Speed,
                    title = stringResource(R.string.playback_sheet_speed_title),
                    value = formatMultiplier(state.speed),
                    onClick = { onPageChange(PlaybackSheetPage.Speed) },
                    showChevron = true,
                )
                "sleep" -> PlaybackSheetRow(
                    icon = Icons.Filled.Timer,
                    title = stringResource(R.string.player_sleep_timer),
                    value = state.sleepTimerRemainingSeconds?.let { formatTimeMs(it * 1000L) }
                        ?: stringResource(R.string.player_sleep_timer_off),
                    onClick = { onPageChange(PlaybackSheetPage.SleepTimer) },
                    showChevron = true,
                )
                "repeat" -> PlaybackSheetRow(
                    icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    title = stringResource(R.string.playback_sheet_repeat),
                    value = when (state.repeatMode) {
                        RepeatMode.ONE -> stringResource(R.string.player_cd_repeat_one)
                        RepeatMode.ALL -> stringResource(R.string.player_cd_repeat_all)
                        else -> stringResource(R.string.player_cd_repeat_off)
                    },
                    // Toggle rows stay open so the new value is visible immediately.
                    onClick = { playbackController.cycleRepeatMode() },
                )
                "shuffle" -> PlaybackSheetRow(
                    icon = Icons.Filled.Shuffle,
                    title = stringResource(R.string.player_cd_shuffle),
                    value = stringResource(
                        if (state.shuffleEnabled) R.string.player_state_on else R.string.player_state_off,
                    ),
                    onClick = { playbackController.setShuffleEnabled(!state.shuffleEnabled) },
                )
                "favourite" -> PlaybackSheetRow(
                    icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    title = if (isFavourite) {
                        stringResource(R.string.player_cd_favourite_remove)
                    } else {
                        stringResource(R.string.player_cd_favourite_add)
                    },
                    value = null,
                    onClick = onToggleFavourite,
                )
                // Issue #225: PiP is video-only — hide the row for audio
                // items (the enterPip lambda also refuses them).
                "pip" -> if (state.currentItem?.isVideo == true) PlaybackSheetRow(
                    icon = Icons.Filled.PictureInPictureAlt,
                    title = stringResource(R.string.player_menu_popup_play),
                    value = null,
                    onClick = {
                        onPageChange(null)
                        onEnterPip()
                    },
                )
                "background" -> PlaybackSheetRow(
                    icon = Icons.Filled.Headphones,
                    title = stringResource(R.string.playback_sheet_background_play),
                    value = stringResource(
                        if (currentSettings.backgroundPlay) R.string.player_state_on else R.string.player_state_off,
                    ),
                    subtitle = if (state.currentItem?.isVideo == true) {
                        stringResource(R.string.player_bg_desc_video)
                    } else {
                        stringResource(R.string.player_bg_desc_audio)
                    },
                    onClick = onToggleBackgroundPlay,
                )
                "playlist" -> PlaybackSheetRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = stringResource(R.string.player_menu_add_playlist),
                    value = null,
                    onClick = {
                        onPageChange(null)
                        onAddToPlaylist()
                    },
                )
                "decoder" -> PlaybackSheetRow(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.playback_sheet_decoder),
                    value = state.decoderMode.label,
                    onClick = { onPageChange(PlaybackSheetPage.Decoder) },
                    showChevron = true,
                )
                "aspect" -> PlaybackSheetRow(
                    icon = Icons.Filled.AspectRatio,
                    title = stringResource(R.string.playback_sheet_aspect),
                    value = stringResource(state.aspectRatio.labelRes),
                    onClick = { onPageChange(PlaybackSheetPage.AspectRatio) },
                    showChevron = true,
                )
                "audio" -> PlaybackSheetRow(
                    icon = Icons.Filled.Audiotrack,
                    title = stringResource(R.string.playback_sheet_audio_track),
                    // Issue #262: an empty list (still preparing, or no
                    // discrete tracks) is not "audio off" — keep the
                    // value null so nothing shows, as before. "Off" is
                    // only for tracks present with none selected.
                    value = if (state.audioTracks.isEmpty()) {
                        null
                    } else {
                        state.audioTracks.firstOrNull { it.isSelected }?.label
                            ?: stringResource(R.string.player_state_off)
                    },
                    onClick = { onPageChange(PlaybackSheetPage.AudioTracks) },
                    showChevron = true,
                )
                "subtitles" -> PlaybackSheetRow(
                    icon = Icons.Filled.Subtitles,
                    title = stringResource(R.string.playback_sheet_subtitle_track),
                    value = state.subtitleTracks.firstOrNull { it.isSelected }?.label
                        ?: stringResource(R.string.player_state_off),
                    onClick = { onPageChange(PlaybackSheetPage.SubtitleTracks) },
                    showChevron = true,
                )
                else -> {
                    // parsePlayerMenuLayout already drops unknown ids.
                }
            }
        }
    }
    // Rows with no layout id, in their current relative order.
    PlaybackSheetRow(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.player_menu_tutorial),
        value = null,
        onClick = {
            onPageChange(null)
            onReplayTutorial()
        },
    )
    PlaybackSheetRow(
        icon = Icons.Filled.ZoomIn,
        title = stringResource(R.string.playback_sheet_zoom),
        value = formatMultiplier(currentSettings.videoZoom),
        onClick = { onPageChange(PlaybackSheetPage.Zoom) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.AvTimer,
        title = stringResource(R.string.playback_sheet_audio_delay),
        value = formatDelaySeconds(currentSettings.audioDelayMs),
        onClick = { onPageChange(PlaybackSheetPage.AudioDelay) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.AvTimer,
        title = stringResource(R.string.subtitle_sync_header),
        value = formatDelaySeconds(currentSettings.subtitleDelayMs),
        onClick = { onPageChange(PlaybackSheetPage.SubtitleDelay) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Tune,
        title = stringResource(R.string.playback_sheet_subtitle_settings),
        value = null,
        onClick = { onPageChange(PlaybackSheetPage.SubtitleSettings) },
        showChevron = true,
    )

    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_section_about))
    PlaybackSheetRow(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.playback_sheet_about_title),
        value = state.currentItem?.title,
        onClick = null,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Timer,
        title = stringResource(R.string.playback_sheet_about_duration),
        value = formatTimeMs(
            if (state.durationMs > 0) state.durationMs
            else state.currentItem?.durationMs ?: 0L,
        ),
        onClick = null,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Memory,
        title = stringResource(R.string.playback_sheet_decoder),
        value = state.decoderMode.label,
        onClick = null,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Subtitles,
        title = stringResource(R.string.playback_sheet_about_codec),
        value = state.activeDecoderName ?: stringResource(R.string.player_codec_initializing),
        onClick = null,
    )
    if (state.currentItem?.isVideo == true) {
        PlaybackSheetRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.media_info_title),
            value = null,
            onClick = { onPageChange(PlaybackSheetPage.MediaInfo) },
            showChevron = true,
        )
    }
}
