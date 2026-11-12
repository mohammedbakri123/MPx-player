package com.mpx.player.ui.screens.player

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.MpxBottomSheet
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetAspectPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetAudioDelayPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetAudioTracksPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetDecoderPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetMainPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSleepPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSpeedPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSubtitleBrowsePage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSubtitleDelayPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSubtitleSettingsPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetSubtitleTracksPage
import com.mpx.player.ui.screens.player.components.playbacksheet.PlaybackSheetZoomPage
import com.mpx.player.ui.theme.MpxTheme

/**
 * Playback sheet (issue #153): one MpxBottomSheet replacing the Player Menu
 * AlertDialog and the whole activeDialog chain (Sleep Timer / Audio Tracks /
 * Subtitles / Decoder). Design is inspired by both mpvEx
 * (one focused sheet per function, drilled into from a main sheet) and MX
 * Player (every row shows its CURRENT value on the right).
 *
 * The player is always dark (issue #152), so this sheet is always dark too:
 * it relies on the MpxTheme(theme = "Dark") wrapper MainActivity puts around
 * the Player route — no theme handling of its own.
 *
 * Every behaviour of the old dialogs was carried over: speed cycling became
 * presets + slider, sleep presets + custom time, decoder/aspect/rotation
 * selections, audio/subtitle track selection, external subtitle loading via
 * SAF, subtitle size/style/sync/speed, favourite/shuffle/repeat/PiP/
 * background-play/add-to-playlist/tutorial. The only deliberate changes:
 * sleep-timer and toggle rows stay open after a choice (so the new value is
 * visible immediately) instead of closing the dialog, and the favourite
 * heart uses the identity-blue accent (filled when favourited) instead of
 * red, per the one-accent UX law.
 *
 * No chapters row: MPx has no chapter support, so there is nothing to show.
 * Audio delay and video zoom are new persisted settings (audioDelayMs,
 * videoZoom) applied live to mpv only, mirroring the subtitle timing
 * pattern; on any other engine they show an honest mpv-only note.
 */
enum class PlaybackSheetPage {
    Main,
    Speed,
    SleepTimer,
    Decoder,
    AspectRatio,
    Zoom,
    AudioTracks,
    AudioDelay,
    SubtitleTracks,
    SubtitleBrowse,
    SubtitleDelay,
    SubtitleSettings,
}

/**
 * Parameter object for [PlayerPlaybackSheet], collapsing the 14 parameters
 * into a single data class (§6, Step 5.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
data class PlaybackSheetState(
    val page: PlaybackSheetPage,
    val onPageChange: (PlaybackSheetPage?) -> Unit,
    val state: PlaybackState,
    val currentSettings: AppSettings,
    val onSettingsChanged: (AppSettings) -> Unit,
    val playbackController: PlaybackController,
    val subtitleLauncher: ActivityResultLauncher<Array<String>>,
    val isFavourite: Boolean,
    val onToggleFavourite: () -> Unit,
    val onEnterPip: () -> Unit,
    val onToggleBackgroundPlay: () -> Unit,
    val onReplayTutorial: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val sheetState: SheetState,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlaybackSheet(
    sheetState: PlaybackSheetState,
) {
    val dismiss = { sheetState.onPageChange(null) }
    val titleRes = when (sheetState.page) {
        PlaybackSheetPage.Main -> R.string.playback_sheet_title
        PlaybackSheetPage.Speed -> R.string.playback_sheet_speed_title
        PlaybackSheetPage.SleepTimer -> R.string.player_sleep_timer_dialog_title
        PlaybackSheetPage.Decoder -> R.string.player_dialog_decoder_selection
        PlaybackSheetPage.AspectRatio -> R.string.playback_sheet_aspect
        PlaybackSheetPage.Zoom -> R.string.playback_sheet_zoom
        PlaybackSheetPage.AudioTracks -> R.string.player_dialog_audio_tracks
        PlaybackSheetPage.AudioDelay -> R.string.playback_sheet_audio_delay
        PlaybackSheetPage.SubtitleTracks -> R.string.player_dialog_subtitle_selection
        PlaybackSheetPage.SubtitleBrowse -> R.string.player_subtitle_browse_title
        PlaybackSheetPage.SubtitleDelay -> R.string.subtitle_sync_header
        PlaybackSheetPage.SubtitleSettings -> R.string.playback_sheet_subtitle_settings
    }
    MpxBottomSheet(
        onDismiss = dismiss,
        sheetState = sheetState.sheetState,
    ) {
        // Issue #362: cap the sheet at 560dp and center it. In
        // landscape the uncapped fillMaxWidth stretched edge to edge;
        // in portrait on a phone the width is already under 560dp, so
        // nothing changes there, and wide tablets get the same centered
        // column. The content keeps its vertical scroll.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header: back to the main sheet from any sub-sheet, title,
            // close. Back is exactly one step: sub-sheet -> main sheet,
            // main sheet -> dismissed.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sheetState.page != PlaybackSheetPage.Main) {
                    IconButton(onClick = { sheetState.onPageChange(PlaybackSheetPage.Main) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MpxTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = dismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            }
            when (sheetState.page) {
                PlaybackSheetPage.Main -> PlaybackSheetMainPage(
                    state = sheetState.state,
                    currentSettings = sheetState.currentSettings,
                    onPageChange = sheetState.onPageChange,
                    playbackController = sheetState.playbackController,
                    isFavourite = sheetState.isFavourite,
                    onToggleFavourite = sheetState.onToggleFavourite,
                    onEnterPip = sheetState.onEnterPip,
                    onToggleBackgroundPlay = sheetState.onToggleBackgroundPlay,
                    onReplayTutorial = sheetState.onReplayTutorial,
                    onAddToPlaylist = sheetState.onAddToPlaylist,
                )
                PlaybackSheetPage.Speed -> PlaybackSheetSpeedPage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                )
                PlaybackSheetPage.SleepTimer -> PlaybackSheetSleepPage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                )
                PlaybackSheetPage.Decoder -> PlaybackSheetDecoderPage(
                    state = sheetState.state,
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                    playbackController = sheetState.playbackController,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.AspectRatio -> PlaybackSheetAspectPage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.Zoom -> PlaybackSheetZoomPage(
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                )
                PlaybackSheetPage.AudioTracks -> PlaybackSheetAudioTracksPage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.AudioDelay -> PlaybackSheetAudioDelayPage(
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                )
                PlaybackSheetPage.SubtitleTracks -> PlaybackSheetSubtitleTracksPage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                    subtitleLauncher = sheetState.subtitleLauncher,
                    onDismiss = dismiss,
                    onOpenBrowse = { sheetState.onPageChange(PlaybackSheetPage.SubtitleBrowse) },
                )
                PlaybackSheetPage.SubtitleBrowse -> PlaybackSheetSubtitleBrowsePage(
                    state = sheetState.state,
                    playbackController = sheetState.playbackController,
                    subtitleLauncher = sheetState.subtitleLauncher,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.SubtitleDelay -> PlaybackSheetSubtitleDelayPage(
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                )
                PlaybackSheetPage.SubtitleSettings -> PlaybackSheetSubtitleSettingsPage(
                    currentSettings = sheetState.currentSettings,
                    onSettingsChanged = sheetState.onSettingsChanged,
                )
            }
        }
    }
}

/**
 * Backward-compatible overload accepting individual parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlaybackSheet(
    page: PlaybackSheetPage,
    onPageChange: (PlaybackSheetPage?) -> Unit,
    state: PlaybackState,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    playbackController: PlaybackController,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onReplayTutorial: () -> Unit,
    onAddToPlaylist: () -> Unit,
    sheetState: SheetState,
) = PlayerPlaybackSheet(
    sheetState = PlaybackSheetState(
        page = page,
        onPageChange = onPageChange,
        state = state,
        currentSettings = currentSettings,
        onSettingsChanged = onSettingsChanged,
        playbackController = playbackController,
        subtitleLauncher = subtitleLauncher,
        isFavourite = isFavourite,
        onToggleFavourite = onToggleFavourite,
        onEnterPip = onEnterPip,
        onToggleBackgroundPlay = onToggleBackgroundPlay,
        onReplayTutorial = onReplayTutorial,
        onAddToPlaylist = onAddToPlaylist,
        sheetState = sheetState,
    ),
)
