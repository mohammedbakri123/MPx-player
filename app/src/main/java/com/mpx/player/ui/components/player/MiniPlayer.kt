package com.mpx.player.ui.components.player
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.AsyncThumbnail
import com.mpx.player.ui.components.AsyncVideoThumbnail
import com.mpx.player.util.ThumbnailLoader
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

fun formatTimeMs(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * MX Player inspired persistent mini-player bar.
 * Directly wired to the real mpv engine via PlaybackManager.
 * Displays the real playing media item, real live progress tick, real duration,
 * and responsive play/pause/prev/next controls.
 */
@Composable
fun MiniPlayer(
    playbackManager: PlaybackManager,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playbackManager.playbackState.collectAsState()
    val item = state.currentItem
    var showQueue by remember { mutableStateOf(false) }

    // Issue #291: the queue dialog used to outlive the mini-player —
    // it was rendered outside the AnimatedVisibility and showQueue was
    // never cleared when playback stopped, so stopping playback left
    // the dialog floating over the home screen with an empty queue.
    // Clear the flag when the item goes null (stop() sets currentItem
    // to null, PlaybackManager.kt:1099), and gate the render on the
    // item too so it vanishes in the same composition instead of one
    // frame later. A track change (non-null item to another non-null
    // item) does not dismiss the dialog.
    LaunchedEffect(item) {
        if (item == null) showQueue = false
    }

    if (showQueue && item != null) {
        QueueDialog(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onDismiss = { showQueue = false },
            onSelect = { index ->
                showQueue = false
                // Issue #203: this is a pick inside the live queue, not a
                // new queue — playLiveQueueItem keeps the shuffle state
                // (pre-shuffle order + flag) instead of destroying it.
                playbackManager.playLiveQueueItem(index)
            },
        )
    }

    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        if (item == null) return@AnimatedVisibility

        val progress = if (state.durationMs > 0) {
            (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPlayer() },
            color = MpxTheme.colors.black,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Real live progress indicator bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.border,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Current track artwork: real MediaStore/ID3 thumbnail via the
                    // same cache keys the list rows use, so artwork already loaded
                    // while browsing shows instantly. Falls back to the honest
                    // vector placeholder only when no artwork exists.
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.isVideo) {
                            AsyncVideoThumbnail(
                                mediaUri = item.uri,
                                mediaId = item.id,
                                title = item.title,
                                modifier = Modifier.fillMaxSize(),
                                iconSize = 24.dp,
                            )
                        } else {
                            AsyncThumbnail(
                                cacheKey = "audio_track_${item.id.ifBlank { item.uri }}",
                                loadBitmap = {
                                    ThumbnailLoader.loadAudioTrackThumbnail(
                                        context = context,
                                        trackId = item.id,
                                        trackPath = item.uri,
                                        width = 160,
                                        height = 160,
                                    )
                                },
                                placeholderIcon = Icons.Filled.MusicNote,
                                modifier = Modifier.fillMaxSize(),
                                iconSize = 24.dp,
                                contentDescription = stringResource(R.string.mini_player_audio),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Real title and duration info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            ),
                            color = MpxTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        val timeFormatted = "${formatTimeMs(state.currentPositionMs)} / ${formatTimeMs(state.durationMs)}"
                        Text(
                            text = if (item.subtitle.isNotBlank()) "${item.subtitle} • $timeFormatted" else timeFormatted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MpxTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Controls: Prev (if available), Hero Play/Pause, Next, Close
                    if (state.hasPrevious) {
                        IconButton(
                            onClick = { playbackManager.skipPrevious() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.mini_player_prev),
                                tint = MpxTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = { playbackManager.togglePlayPause() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) stringResource(R.string.mini_player_pause) else stringResource(R.string.mini_player_play),
                            tint = MpxTheme.colors.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    if (state.hasNext) {
                        IconButton(
                            onClick = { playbackManager.skipNext() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.mini_player_next),
                                tint = MpxTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Queue button (issue #73): opens the real current queue.
                    IconButton(
                        onClick = { showQueue = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.mini_player_queue),
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = { playbackManager.stop() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.mini_player_close),
                            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real current-queue list for the mini-player queue button (issue #73).
 * Shows exactly PlaybackState.queue — never invented entries. Tapping an
 * entry plays that queue position via PlaybackManager.playLiveQueueItem
 * (issue #203: a live-queue pick, so the shuffle state is preserved); the
 * current entry is highlighted and labelled. Screenshot-only evidence
 * per the V1 re-grounding report: the local mini-player has no layout
 * in the decoded MX tree, so this follows the screenshots, not the decode.
 */
@Composable
private fun QueueDialog(
    queue: List<PlayableMedia>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.mini_player_queue)) },
        text = {
            if (queue.isEmpty()) {
                Text(text = stringResource(R.string.mini_player_queue_empty))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    itemsIndexed(queue) { index, entry ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(index) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isCurrent) Icons.Filled.PlayArrow
                                else if (entry.isVideo) Icons.Filled.Movie
                                else Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) MpxTheme.colors.primary
                                else MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) MpxTheme.colors.primary
                                    else MpxTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.subtitle.isNotBlank()) {
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.mini_player_queue_now_playing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MpxTheme.colors.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}
