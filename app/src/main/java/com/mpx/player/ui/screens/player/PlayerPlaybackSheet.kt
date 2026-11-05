package com.mpx.player.ui.screens.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
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
import kotlin.math.roundToInt

/**
 * Playback sheet (issue #153): one MpxBottomSheet replacing the Player Menu
 * AlertDialog and the whole activeDialog chain (Sleep Timer / Audio Tracks /
 * Subtitles / Decoder / Screen Rotation). Design is inspired by both mpvEx
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
    Rotation,
    Zoom,
    AudioTracks,
    AudioDelay,
    SubtitleTracks,
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
        PlaybackSheetPage.Rotation -> R.string.player_rotation_dialog_title
        PlaybackSheetPage.Zoom -> R.string.playback_sheet_zoom
        PlaybackSheetPage.AudioTracks -> R.string.player_dialog_audio_tracks
        PlaybackSheetPage.AudioDelay -> R.string.playback_sheet_audio_delay
        PlaybackSheetPage.SubtitleTracks -> R.string.player_dialog_subtitle_selection
        PlaybackSheetPage.SubtitleDelay -> R.string.subtitle_sync_header
        PlaybackSheetPage.SubtitleSettings -> R.string.playback_sheet_subtitle_settings
    }
    MpxBottomSheet(
        onDismiss = dismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                PlaybackSheetPage.Rotation -> PlaybackSheetRotationPage(
                    currentSettings = currentSettings,
                    onSettingsChanged = onSettingsChanged,
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

/** "1×" / "1.25×" / "0.75×" — trims the trailing ".0" off whole values. */
private fun formatMultiplier(value: Float): String =
    value.toString().removeSuffix(".0") + "×"

/** "+0.50s" / "-0.20s" / "0.00s", same format the old subtitle dialog used. */
private fun formatDelaySeconds(delayMs: Int): String =
    if (delayMs == 0) "0.00s" else "%+.2fs".format(delayMs / 1000.0)

private fun rotationLabel(settings: AppSettings): Int =
    if (settings.autoScreenRotation || settings.screenOrientation == "auto") {
        R.string.player_rotation_auto
    } else if (settings.screenOrientation.equals("portrait", ignoreCase = true)) {
        R.string.player_orientation_portrait
    } else {
        R.string.player_orientation_landscape
    }

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
    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_title))
    PlaybackSheetRow(
        icon = Icons.Filled.Speed,
        title = stringResource(R.string.playback_sheet_speed_title),
        value = formatMultiplier(state.speed),
        onClick = { onPageChange(PlaybackSheetPage.Speed) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Timer,
        title = stringResource(R.string.player_sleep_timer),
        value = state.sleepTimerRemainingSeconds?.let { formatTimeMs(it * 1000L) }
            ?: stringResource(R.string.player_sleep_timer_off),
        onClick = { onPageChange(PlaybackSheetPage.SleepTimer) },
        showChevron = true,
    )
    PlaybackSheetRow(
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
    PlaybackSheetRow(
        icon = Icons.Filled.Shuffle,
        title = stringResource(R.string.player_cd_shuffle),
        value = stringResource(
            if (state.shuffleEnabled) R.string.player_state_on else R.string.player_state_off,
        ),
        onClick = { playbackManager.setShuffleEnabled(!state.shuffleEnabled) },
    )
    PlaybackSheetRow(
        icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        title = if (isFavourite) {
            stringResource(R.string.player_cd_favourite_remove)
        } else {
            stringResource(R.string.player_cd_favourite_add)
        },
        value = null,
        onClick = onToggleFavourite,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.PictureInPictureAlt,
        title = stringResource(R.string.player_menu_popup_play),
        value = null,
        onClick = {
            onPageChange(null)
            onEnterPip()
        },
    )
    PlaybackSheetRow(
        icon = Icons.Filled.PlayArrow,
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
    PlaybackSheetRow(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        title = stringResource(R.string.player_menu_add_playlist),
        value = null,
        onClick = {
            onPageChange(null)
            onAddToPlaylist()
        },
    )
    PlaybackSheetRow(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.player_menu_tutorial),
        value = null,
        onClick = {
            onPageChange(null)
            onReplayTutorial()
        },
    )

    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_section_video))
    PlaybackSheetRow(
        icon = Icons.Filled.Memory,
        title = stringResource(R.string.playback_sheet_decoder),
        value = state.decoderMode.label,
        onClick = { onPageChange(PlaybackSheetPage.Decoder) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.AspectRatio,
        title = stringResource(R.string.playback_sheet_aspect),
        value = stringResource(state.aspectRatio.labelRes),
        onClick = { onPageChange(PlaybackSheetPage.AspectRatio) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.ScreenRotation,
        title = stringResource(R.string.playback_sheet_rotation),
        value = stringResource(rotationLabel(currentSettings)),
        onClick = { onPageChange(PlaybackSheetPage.Rotation) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.ZoomIn,
        title = stringResource(R.string.playback_sheet_zoom),
        value = formatMultiplier(currentSettings.videoZoom),
        onClick = { onPageChange(PlaybackSheetPage.Zoom) },
        showChevron = true,
    )

    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_section_audio))
    PlaybackSheetRow(
        icon = Icons.Filled.Audiotrack,
        title = stringResource(R.string.playback_sheet_audio_track),
        value = state.audioTracks.firstOrNull { it.isSelected }?.label,
        onClick = { onPageChange(PlaybackSheetPage.AudioTracks) },
        showChevron = true,
    )
    PlaybackSheetRow(
        icon = Icons.Filled.AvTimer,
        title = stringResource(R.string.playback_sheet_audio_delay),
        value = formatDelaySeconds(currentSettings.audioDelayMs),
        onClick = { onPageChange(PlaybackSheetPage.AudioDelay) },
        showChevron = true,
    )

    PlaybackSheetSectionTitle(stringResource(R.string.playback_sheet_section_subtitles))
    PlaybackSheetRow(
        icon = Icons.Filled.Subtitles,
        title = stringResource(R.string.playback_sheet_subtitle_track),
        value = state.subtitleTracks.firstOrNull { it.isSelected }?.label
            ?: stringResource(R.string.player_state_off),
        onClick = { onPageChange(PlaybackSheetPage.SubtitleTracks) },
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
                    selected = state.speed == speed,
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
    // same string the Off chip and the stripe caption use); the first tap
    // in either direction starts a timer at the 30-minute default, and
    // only then does the value step ±5 from the running timer.
    val timerMinutes = state.sleepTimerMinutes
    StepperRow(
        label = stringResource(R.string.playback_sheet_sleep_custom),
        valueText = timerMinutes?.let { "$it min" }
            ?: stringResource(R.string.player_sleep_timer_off),
        minusContentDescription = stringResource(R.string.playback_sheet_sleep_custom),
        plusContentDescription = stringResource(R.string.playback_sheet_sleep_custom),
        onMinus = {
            val mins = timerMinutes?.let { (it - 5).coerceIn(5, 180) } ?: 30
            playbackManager.setSleepTimer(mins)
            Toast.makeText(
                context,
                context.getString(R.string.player_sleep_timer_set_toast, mins),
                Toast.LENGTH_SHORT,
            ).show()
        },
        onPlus = {
            val mins = timerMinutes?.let { (it + 5).coerceIn(5, 180) } ?: 30
            playbackManager.setSleepTimer(mins)
            Toast.makeText(
                context,
                context.getString(R.string.player_sleep_timer_set_toast, mins),
                Toast.LENGTH_SHORT,
            ).show()
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
private fun PlaybackSheetRotationPage(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isAuto = currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto"
    val isLandscape = !currentSettings.autoScreenRotation &&
        currentSettings.screenOrientation.equals("landscape", ignoreCase = true)
    val isPortrait = !currentSettings.autoScreenRotation &&
        currentSettings.screenOrientation.equals("portrait", ignoreCase = true)
    Column {
        PlaybackSheetRadioRow(
            title = stringResource(R.string.player_rotation_auto),
            subtitle = stringResource(R.string.player_rotation_auto_desc),
            selected = isAuto,
            onClick = {
                onSettingsChanged(
                    currentSettings.copy(autoScreenRotation = true, screenOrientation = "auto"),
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_rotation_auto),
                    Toast.LENGTH_SHORT,
                ).show()
                onDismiss()
            },
        )
        PlaybackSheetRadioRow(
            title = stringResource(R.string.player_orientation_landscape),
            subtitle = stringResource(R.string.player_orientation_landscape_desc),
            selected = isLandscape,
            onClick = {
                onSettingsChanged(
                    currentSettings.copy(autoScreenRotation = false, screenOrientation = "landscape"),
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_rotation_landscape),
                    Toast.LENGTH_SHORT,
                ).show()
                onDismiss()
            },
        )
        PlaybackSheetRadioRow(
            title = stringResource(R.string.player_orientation_portrait),
            subtitle = stringResource(R.string.player_orientation_portrait_desc),
            selected = isPortrait,
            onClick = {
                onSettingsChanged(
                    currentSettings.copy(autoScreenRotation = false, screenOrientation = "portrait"),
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_rotation_portrait),
                    Toast.LENGTH_SHORT,
                ).show()
                onDismiss()
            },
        )
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
        minusContentDescription = stringResource(R.string.playback_sheet_audio_delay),
        plusContentDescription = stringResource(R.string.playback_sheet_audio_delay),
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                onDismiss()
                subtitleLauncher.launch(arrayOf("*/*"))
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
            minusContentDescription = stringResource(R.string.subtitle_sync_header),
            plusContentDescription = stringResource(R.string.subtitle_sync_header),
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
            valueText = "%.1f×".format(speed),
            minusContentDescription = stringResource(R.string.subtitle_speed_header),
            plusContentDescription = stringResource(R.string.subtitle_speed_header),
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
