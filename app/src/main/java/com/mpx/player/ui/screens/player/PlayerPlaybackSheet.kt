package com.mpx.player.ui.screens.player

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.ui.screens.settings.parsePlayerMenuLayout
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.RepeatMode
import com.mpx.player.ui.components.MpxBottomSheet
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.SubtitleSiblingFinder
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlaybackSheet(
    page: PlaybackSheetPage,
    onPageChange: (PlaybackSheetPage?) -> Unit,
    state: PlaybackState,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    playbackManager: PlaybackManager,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onReplayTutorial: () -> Unit,
    onAddToPlaylist: () -> Unit,
    sheetState: SheetState,
) {
    val dismiss = { onPageChange(null) }
    val titleRes = when (page) {
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
        sheetState = sheetState,
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
                if (page != PlaybackSheetPage.Main) {
                    IconButton(onClick = { onPageChange(PlaybackSheetPage.Main) }) {
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
            when (page) {
                PlaybackSheetPage.Main -> PlaybackSheetMainPage(
                    state = state,
                    currentSettings = currentSettings,
                    onPageChange = onPageChange,
                    playbackManager = playbackManager,
                    isFavourite = isFavourite,
                    onToggleFavourite = onToggleFavourite,
                    onEnterPip = onEnterPip,
                    onToggleBackgroundPlay = onToggleBackgroundPlay,
                    onReplayTutorial = onReplayTutorial,
                    onAddToPlaylist = onAddToPlaylist,
                )
                PlaybackSheetPage.Speed -> PlaybackSheetSpeedPage(
                    state = state,
                    playbackManager = playbackManager,
                )
                PlaybackSheetPage.SleepTimer -> PlaybackSheetSleepPage(
                    state = state,
                    playbackManager = playbackManager,
                )
                PlaybackSheetPage.Decoder -> PlaybackSheetDecoderPage(
                    state = state,
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                    playbackManager = playbackManager,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.AspectRatio -> PlaybackSheetAspectPage(
                    state = state,
                    playbackManager = playbackManager,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.Zoom -> PlaybackSheetZoomPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                )
                PlaybackSheetPage.AudioTracks -> PlaybackSheetAudioTracksPage(
                    state = state,
                    playbackManager = playbackManager,
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.AudioDelay -> PlaybackSheetAudioDelayPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                )
                PlaybackSheetPage.SubtitleTracks -> PlaybackSheetSubtitleTracksPage(
                    state = state,
                    playbackManager = playbackManager,
                    subtitleLauncher = subtitleLauncher,
                    onDismiss = dismiss,
                    onOpenBrowse = { onPageChange(PlaybackSheetPage.SubtitleBrowse) },
                )
                PlaybackSheetPage.SubtitleBrowse -> PlaybackSheetSubtitleBrowsePage(
                    state = state,
                    playbackManager = playbackManager,
                    subtitleLauncher = subtitleLauncher,
                    onDismiss = dismiss,
                )
                PlaybackSheetPage.SubtitleDelay -> PlaybackSheetSubtitleDelayPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                )
                PlaybackSheetPage.SubtitleSettings -> PlaybackSheetSubtitleSettingsPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
                )
            }
        }
    }
}

/** Uppercase labelSmall section title in the accent colour (mpvEx More sheet). */
@Composable
private fun PlaybackSheetSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MpxTheme.colors.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/**
 * One MX-style row: leading icon, title, current value on the right,
 * chevron for drill-in rows. Rows with [onClick] null are plain info rows.
 */
@Composable
private fun PlaybackSheetRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    showChevron: Boolean = false,
    iconTint: Color = MpxTheme.colors.primary,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 12.dp)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MpxTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                color = MpxTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MpxTheme.colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Radio row used by the selection sub-sheets (decoder/aspect/rotation/tracks). */
@Composable
private fun PlaybackSheetRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MpxTheme.colors.textPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** "1×" / "1.25×" / "0.75×" — rounds to 2 decimals (pinch produces
 * long floats) and trims the trailing ".0" off whole values, matching
 * the player HUD's formatMultiplier in PlayerScreen (#261). */
private fun formatMultiplier(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    return rounded.toString().removeSuffix(".0") + "×"
}

/** "+0.50s" / "-0.20s" / "0.00s", same format the old subtitle dialog used. */
private fun formatDelaySeconds(delayMs: Int): String =
    if (delayMs == 0) "0.00s" else String.format(Locale.US, "%+.2fs", delayMs / 1000.0)


@Composable
private fun PlaybackSheetMainPage(
    state: PlaybackState,
    currentSettings: AppSettings,
    onPageChange: (PlaybackSheetPage?) -> Unit,
    playbackManager: PlaybackManager,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onReplayTutorial: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    // Issue #160: the Main page used to hardcode its rows in fixed sections,
    // so the persisted Player Layout order (Settings > Player Layout) had no
    // effect here — parsePlayerMenuLayout was only read by the editor and the
    // top-bar actions (#177). The 13 layout rows now render in the persisted
    // order, using the same parse and the same remember keys as the top bar,
    // so a reorder or hide in the editor shows up here too. It is live: an
    // editor change recomposes with the new order, no restart needed. Each
    // row is keyed by its id so a reorder keeps row identity across
    // recomposition.
    //
    // Consequence, stated plainly: the fixed Video / Audio / Subtitles
    // section titles are gone — they only make sense with fixed grouping,
    // and an arbitrary layout order can put Decoder between Speed and Sleep.
    // The "Playback" title stays as the page title and the "About this file"
    // section stays at the end (info rows, not actions). The default layout
    // (decoder,audio,subtitles,...) is the order the editor and the top bar
    // already show, so the default sheet now follows that same order instead
    // of the old hardcoded one — sheet, top bar and editor finally agree.
    // Rows with no layout id (tutorial, zoom, audio delay, subtitle delay,
    // subtitle settings) follow after the 13 in their current relative order.
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
                    onClick = { playbackManager.cycleRepeatMode() },
                )
                "shuffle" -> PlaybackSheetRow(
                    icon = Icons.Filled.Shuffle,
                    title = stringResource(R.string.player_cd_shuffle),
                    value = stringResource(
                        if (state.shuffleEnabled) R.string.player_state_on else R.string.player_state_off,
                    ),
                    onClick = { playbackManager.setShuffleEnabled(!state.shuffleEnabled) },
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
}

@Composable
private fun PlaybackSheetSpeedPage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
) {
    Text(
        text = formatMultiplier(state.speed),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MpxTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    // Presets, two rows of three (the old menu only cycled 1.0/1.25/1.5/2.0/0.75).
    listOf(
        listOf(0.5f, 0.75f, 1.0f),
        listOf(1.25f, 1.5f, 2.0f),
    ).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { speed ->
                FilterChip(
                    // Issue #249: epsilon, not Float == — the slider
                    // (0.5..2.0, 29 steps = 0.05 per step) produces
                    // inexact floats, so a slider-set 1.25x may not equal
                    // the 1.25f preset exactly. 0.01f is far above float
                    // rounding (~1e-7) and far below half the smallest
                    // preset gap (0.125), so no two chips can match at
                    // once. Display-only: tapping a chip still sets the
                    // exact preset speed via setSpeed below.
                    selected = abs(state.speed - speed) < 0.01f,
                    onClick = { playbackManager.setSpeed(speed) },
                    label = { Text(formatMultiplier(speed)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Slider(
        value = state.speed,
        onValueChange = { playbackManager.setSpeed(it) },
        valueRange = 0.5f..2.0f,
        steps = 29,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("0.5×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
        Text("2×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun PlaybackSheetSleepPage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
) {
    val context = LocalContext.current
    state.sleepTimerRemainingSeconds?.let { remaining ->
        Text(
            text = formatTimeMs(remaining * 1000L),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MpxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
    val choices = listOf(
        15 to R.string.player_sleep_timer_15,
        30 to R.string.player_sleep_timer_30,
        45 to R.string.player_sleep_timer_45,
        60 to R.string.player_sleep_timer_60,
        0 to R.string.player_sleep_timer_off,
    )
    // Presets as chips; unlike the old dialog the sheet stays open so the
    // remaining time above is visible immediately after a choice.
    choices.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { (mins, labelRes) ->
                val isSelected = if (mins == 0) {
                    state.sleepTimerMinutes == null
                } else {
                    state.sleepTimerMinutes == mins
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (mins == 0) {
                            playbackManager.cancelSleepTimer()
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_sleep_timer_canceled_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            playbackManager.setSleepTimer(mins)
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_sleep_timer_set_toast, mins),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    label = { Text(stringResource(labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            // Pad a short row so chips keep an even width.
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    // Custom time: same ±5 minute stepper idea as the old presets, but any
    // 5..180 minute value.
    // Issue #208: the stepper used to read the set value through a
    // `?: 30` fallback, so with no timer running it showed "30 min" while
    // the Off chip was selected — and the first + tap set a 35-minute timer
    // from that phantom base. With no timer the stepper now shows Off (the
    // same string the Off chip and the sleep chip near the controls use); the first tap
    // in either direction starts a timer at the 30-minute default, and
    // only then does the value step ±5 from the running timer.
    // Issue #238: while a timer runs, the base is the REMAINING time
    // ceiled to minutes — not sleepTimerMinutes, which is the set duration
    // frozen at set time (the tick loop only updates
    // sleepTimerRemainingSeconds). A minute into a 30 min timer the
    // stepper shows "29 min" and + sets 34, matching the headline
    // countdown and the sleep chip near the controls. Ceil, not floor: 29:59 remaining
    // is still "30 min" until it actually drops below 29 minutes.
    val timerMinutes: Int? = state.sleepTimerRemainingSeconds?.let { remaining ->
        ceil(remaining / 60.0).toInt()
    }
    StepperRow(
        label = stringResource(R.string.playback_sheet_sleep_custom),
        valueText = timerMinutes?.let { "$it min" }
            ?: stringResource(R.string.player_sleep_timer_off),
        minusContentDescription = stringResource(R.string.sleep_timer_decrease),
        plusContentDescription = stringResource(R.string.sleep_timer_increase),
        onMinus = {
            val mins = timerMinutes?.let { (it - 5).coerceIn(5, 180) } ?: 30
            // Issue #285: minus at the 5-minute floor is a no-op — with
            // 4:59 remaining the stepper shows "5 min" and the stepped
            // value is also 5, so re-arming setSleepTimer would reset the
            // countdown to a full 5:00, making the timer longer.
            if (timerMinutes == null || mins != timerMinutes) {
                playbackManager.setSleepTimer(mins)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_set_toast, mins),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onPlus = {
            val mins = timerMinutes?.let { (it + 5).coerceIn(5, 180) } ?: 30
            // Issue #285: same as minus — plus at the 180-minute ceiling
            // is a no-op instead of re-arming a full 180.
            if (timerMinutes == null || mins != timerMinutes) {
                playbackManager.setSleepTimer(mins)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_set_toast, mins),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onReset = if (state.sleepTimerMinutes != null) {
            {
                playbackManager.cancelSleepTimer()
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_canceled_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            null
        },
    )
}

@Composable
private fun PlaybackSheetDecoderPage(
    state: PlaybackState,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    playbackManager: PlaybackManager,
    onDismiss: () -> Unit,
) {
    Column {
        DecoderMode.values().forEach { mode ->
            PlaybackSheetRadioRow(
                title = stringResource(mode.titleRes),
                subtitle = stringResource(mode.descriptionRes),
                selected = state.decoderMode == mode,
                onClick = {
                    playbackManager.setDecoderMode(mode)
                    onSettingsChanged(currentSettings.copy(decoderMode = mode.label))
                    onDismiss()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.player_active_codec,
                state.activeDecoderName ?: stringResource(R.string.player_codec_initializing),
            ),
            color = MpxTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        state.decoderFallbackNote?.let { note ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0x33FFB74D),
                shape = MpxShapes.small,
                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = note,
                        color = Color(0xFFFFE082),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSheetAspectPage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
    onDismiss: () -> Unit,
) {
    Column {
        AspectRatioMode.values().forEach { mode ->
            PlaybackSheetRadioRow(
                title = stringResource(mode.labelRes),
                subtitle = null,
                selected = state.aspectRatio == mode,
                onClick = {
                    playbackManager.setAspectRatio(mode)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun PlaybackSheetZoomPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        Text(
            text = formatMultiplier(currentSettings.videoZoom),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MpxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Slider(
            value = currentSettings.videoZoom,
            onValueChange = { onSettingsChanged(currentSettings.copy(videoZoom = it)) },
            valueRange = 0.5f..2.0f,
            steps = 29,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0.5×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
            Text("2×", color = MpxTheme.colors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** Audio delay stepper, shared by the Audio Tracks page and the Audio Delay page. */
@Composable
private fun AudioDelayStepper(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val delayMs = currentSettings.audioDelayMs
    StepperRow(
        label = stringResource(R.string.playback_sheet_audio_delay),
        valueText = formatDelaySeconds(delayMs),
        minusContentDescription = stringResource(R.string.audio_delay_decrease),
        plusContentDescription = stringResource(R.string.audio_delay_increase),
        onMinus = {
            onSettingsChanged(
                currentSettings.copy(audioDelayMs = (delayMs - 100).coerceIn(-10000, 10000)),
            )
        },
        onPlus = {
            onSettingsChanged(
                currentSettings.copy(audioDelayMs = (delayMs + 100).coerceIn(-10000, 10000)),
            )
        },
        onReset = if (delayMs != 0) {
            { onSettingsChanged(currentSettings.copy(audioDelayMs = 0)) }
        } else {
            null
        },
    )
}

@Composable
private fun PlaybackSheetAudioTracksPage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        if (state.audioTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.player_audio_no_tracks),
                color = MpxTheme.colors.textSecondary,
            )
        } else {
            state.audioTracks.forEach { track ->
                PlaybackSheetRadioRow(
                    title = track.label,
                    subtitle = stringResource(R.string.player_track_language, track.language),
                    selected = track.isSelected,
                    onClick = {
                        playbackManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                        onDismiss()
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        AudioDelayStepper(
            currentSettings = currentSettings,
            onSettingsChanged = onSettingsChanged,
        )
    }
}

@Composable
private fun PlaybackSheetAudioDelayPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        AudioDelayStepper(
            currentSettings = currentSettings,
            onSettingsChanged = onSettingsChanged,
        )
    }
}

@Composable
private fun PlaybackSheetSubtitleTracksPage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    onDismiss: () -> Unit,
    onOpenBrowse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                // MX-style: a local video opens the in-app browser at
                // its own folder; anything without a folder (a network
                // stream) keeps the system picker.
                if (state.currentItem?.filePath?.isNotBlank() == true) {
                    onOpenBrowse()
                } else {
                    onDismiss()
                    subtitleLauncher.launch(arrayOf("*/*"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MpxTheme.colors.primary),
        ) {
            Icon(
                imageVector = Icons.Filled.FileUpload,
                contentDescription = null,
                tint = MpxTheme.colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_subtitle_open_file),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_subtitle_available_tracks),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        PlaybackSheetRadioRow(
            title = stringResource(R.string.player_subtitle_off),
            subtitle = null,
            selected = state.selectedSubtitleTrackIndex == -1,
            onClick = {
                playbackManager.selectSubtitleTrack(null, null)
                onDismiss()
            },
        )
        if (state.subtitleTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.player_subtitle_no_tracks),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 6.dp),
            )
        } else {
            state.subtitleTracks.forEach { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playbackManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = track.isSelected,
                        onClick = {
                            playbackManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.label,
                                color = MpxTheme.colors.textPrimary,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (track.isExternal) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = MpxTheme.colors.primary.copy(alpha = 0.2f),
                                    shape = MpxShapes.extraSmall,
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_subtitle_external),
                                        color = MpxTheme.colors.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.player_track_language, track.language),
                            color = MpxTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * MX-style subtitle file browser: lists the subtitle files in the
 * current video's folder (starting there, like MX's Open), with
 * subfolders to walk into and a ".." row to go up. Tapping a file
 * loads it exactly like a system-picker pick. When the folder cannot
 * be listed (no All files access on Android 11+) the page says so and
 * offers the grant; the system picker stays as the fallback at the
 * bottom for subtitles living anywhere else.
 */
@Composable
private fun PlaybackSheetSubtitleBrowsePage(
    state: PlaybackState,
    playbackManager: PlaybackManager,
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val startDir = remember(state.currentItem?.id) {
        state.currentItem?.filePath?.let { File(it).parentFile }
    }
    var currentDir by remember(state.currentItem?.id) { mutableStateOf(startDir) }
    var entries by remember { mutableStateOf<List<SubtitleSiblingFinder.BrowserEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val hasAccess = remember { SubtitleSiblingFinder.hasAllFilesAccess(context) }

    LaunchedEffect(currentDir) {
        val dir = currentDir
        loading = true
        entries = withContext(Dispatchers.IO) {
            dir?.let { SubtitleSiblingFinder.listSubtitleDir(it) }
        }
        loading = false
    }

    fun openAllFilesSettings() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: Exception) {
            // A ROM without that settings page: the system picker
            // below still works, so there is nothing else to do.
        }
    }

    fun pickFile(file: File) {
        when (val validation = SubtitleUtils.validateSubtitleFile(context, Uri.fromFile(file))) {
            is SubtitleValidationResult.Supported -> {
                playbackManager.loadExternalSubtitle(
                    uri = validation.uri,
                    displayName = validation.displayName,
                    mimeType = validation.mimeType,
                )
                onDismiss()
            }
            is SubtitleValidationResult.Unsupported -> {
                // The browser only lists supported extensions.
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = currentDir?.absolutePath ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        if (!hasAccess) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MpxShapes.large,
                color = MpxTheme.colors.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.player_subtitle_all_files_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MpxTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_subtitle_all_files_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { openAllFilesSettings() }) {
                        Text(
                            text = stringResource(R.string.player_subtitle_all_files_allow),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (loading) {
            Text(
                text = stringResource(R.string.player_subtitle_browse_loading),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
        } else if (entries == null) {
            Text(
                text = stringResource(R.string.player_subtitle_browse_unavailable),
                color = MpxTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
        } else {
            val listed = entries ?: emptyList()
            if (currentDir?.parentFile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentDir = currentDir?.parentFile }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "..",
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (listed.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_subtitle_browse_empty),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                listed.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) {
                                    currentDir = entry.file
                                } else {
                                    pickFile(entry.file)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (entry.isDirectory) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = entry.file.name,
                            color = MpxTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MpxTheme.colors.borderSubtle, thickness = MpxHairline)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                onDismiss()
                subtitleLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.FileUpload,
                contentDescription = null,
                tint = MpxTheme.colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_subtitle_browse_system_picker),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaybackSheetSubtitleDelayPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val delayMs = currentSettings.subtitleDelayMs
    Column {
        StepperRow(
            label = stringResource(R.string.subtitle_sync_header),
            valueText = formatDelaySeconds(delayMs),
            minusContentDescription = stringResource(R.string.subtitle_sync_decrease),
            plusContentDescription = stringResource(R.string.subtitle_sync_increase),
            onMinus = {
                onSettingsChanged(
                    currentSettings.copy(subtitleDelayMs = (delayMs - 100).coerceIn(-10000, 10000)),
                )
            },
            onPlus = {
                onSettingsChanged(
                    currentSettings.copy(subtitleDelayMs = (delayMs + 100).coerceIn(-10000, 10000)),
                )
            },
            onReset = if (delayMs != 0) {
                { onSettingsChanged(currentSettings.copy(subtitleDelayMs = 0)) }
            } else {
                null
            },
        )
        // Same unconditional note the old dialog carried: sync/speed are
        // mpv-only, so the disclosure stays even when mpv is active.
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.subtitle_timing_mpv_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PlaybackSheetSubtitleSettingsPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.player_subtitle_text_size),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Small" to R.string.player_subtitle_size_small,
                "Normal" to R.string.player_subtitle_size_normal,
                "Large" to R.string.player_subtitle_size_large,
            ).forEach { (size, sizeRes) ->
                val isSelected = currentSettings.subtitleSize.equals(size, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSettingsChanged(currentSettings.copy(subtitleSize = size)) },
                    label = { Text(stringResource(sizeRes), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val speed = currentSettings.subtitleSpeed
        StepperRow(
            label = stringResource(R.string.subtitle_speed_header),
            valueText = String.format(Locale.US, "%.1f×", speed),
            minusContentDescription = stringResource(R.string.subtitle_speed_decrease),
            plusContentDescription = stringResource(R.string.subtitle_speed_increase),
            onMinus = {
                val stepped = ((speed * 10).roundToInt() - 1) / 10f
                onSettingsChanged(currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onPlus = {
                val stepped = ((speed * 10).roundToInt() + 1) / 10f
                onSettingsChanged(currentSettings.copy(subtitleSpeed = stepped.coerceIn(0.5f, 2.0f)))
            },
            onReset = if (speed != 1.0f) {
                { onSettingsChanged(currentSettings.copy(subtitleSpeed = 1.0f)) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.player_subtitle_style_color),
            style = MaterialTheme.typography.labelMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "White" to R.string.player_subtitle_style_white,
                "Yellow" to R.string.player_subtitle_style_yellow,
                "Cyan" to R.string.player_subtitle_style_cyan,
                "Default" to R.string.player_subtitle_style_default,
            ).forEach { (styleKey, styleRes) ->
                val isSelected = currentSettings.subtitleStyle.equals(styleKey, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSettingsChanged(currentSettings.copy(subtitleStyle = styleKey)) },
                    label = { Text(stringResource(styleRes), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                )
            }
        }
        // Same unconditional note the old dialog carried (see Subtitle Delay).
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.subtitle_timing_mpv_note),
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textSecondary,
        )
    }
}
