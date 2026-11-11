package com.mpx.player.ui.screens.me
import com.mpx.player.ui.theme.MpxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full History page (#157). The Me History tile used to open the
 * PlaybackHistoryDialog; it now opens this page instead, with the same
 * Room-backed data and the same rows (icon, title, date + resume-at,
 * progress bar). Back returns to Me — exactly one step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    playbackCoordinator: PlaybackCoordinator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Migration Phase 2e: history state lives in MeViewModel now —
    // the total, the paged list, and the clear action. The screen
    // still takes PlaybackCoordinator directly for transport and for
    // resumeManager.clearAll() beside the clear (issue #353); those
    // move behind interfaces in later phases.
    val viewModel: MeViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    // #265: the list is paged (HISTORY_PAGE_SIZE rows at a time) and the
    // title shows the real total. The old code rendered history.size as
    // the total while the observer capped at 100 rows, freezing the count
    // at 100 and hiding every older row. The limit lives in the
    // ViewModel now, so rotation does not drop already-loaded rows.
    val historyTotal by viewModel.historyTotal.collectAsState()
    val history by viewModel.history.collectAsState()

    // #265: load the next page when the user scrolls near the end. The
    // effect keys on history.size too, so it re-fires after each page
    // lands while the user is still sitting at the bottom. It stops by
    // itself once history.size reaches historyTotal.
    val historyListState = rememberLazyListState()
    val historyNearEnd by remember {
        derivedStateOf {
            val info = historyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(historyNearEnd, history.size) {
        viewModel.loadMoreHistoryIfNearEnd(historyNearEnd)
    }

    // #252: the "Resume at" label must only appear when a tap would
    // actually resume — PlaybackCoordinator.playQueue only resumes when
    // settings.resumePlayback is on (forceResume is false for history
    // taps), so with the setting off the label would promise a resume
    // the tap does not deliver. Read fresh on every composition so a
    // Settings change is picked up when returning to this screen.
    val resumePlaybackEnabled = viewModel.isResumePlaybackEnabled()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_dialog_title, historyTotal),
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
                    if (history.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                viewModel.clearHistory()
                                // Issue #353: clearing history must also
                                // forget every saved resume position, or
                                // videos keep offering to resume from the
                                // old position (and the "Resumed from"
                                // banner still appears). The resume
                                // manager's clearAll() clears the
                                // in-memory map, the throttle state, and
                                // the Room table.
                                playbackCoordinator.clearResumePositions()
                                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_cleared_history))
                            }
                        }) {
                            Text(
                                stringResource(R.string.action_clear_all),
                                color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MpxTheme.colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MpxTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.history_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = historyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(history, key = { it.mediaUri }) { item ->
                    val progress = if (item.durationMs > 0L) {
                        (item.playbackPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackCoordinator.playQueue(
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
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MpxShapes.small)
                                    .background(MpxTheme.colors.black)
                                    .border(MpxHairline, MpxTheme.colors.border, MpxShapes.small),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MpxTheme.colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormat.format(Date(item.lastPlayedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                    )
                                    // #252: hidden when resume is off (see
                                    // above), and the 2_000 ms floor matches
                                    // PlaybackCoordinator.shouldResume — a
                                    // 1_501–2_000 ms position would otherwise
                                    // show the label while the tap restarts
                                    // from 0 even with the setting on.
                                    if (resumePlaybackEnabled && item.playbackPositionMs > 2_000L) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.history_resume_at, formatTime(item.playbackPositionMs)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MpxTheme.colors.primary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.history_cd_resume),
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (progress > 0f) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp)),
                                color = MpxTheme.colors.primary,
                                trackColor = MpxTheme.colors.border,
                            )
                        }
                    }
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
