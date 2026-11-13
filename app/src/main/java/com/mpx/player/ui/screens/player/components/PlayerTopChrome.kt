package com.mpx.player.ui.screens.player.components

import android.widget.Toast
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.screens.player.PlaybackSheetPage
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.theme.MpxTheme

/**
 * Top chrome overlay for [com.mpx.player.ui.screens.player.PlayerScreen].
 * Floating island redesign:
 * - Four separate header pills (Back, Title, Decoder, MoreVert)
 * - Strip menu directly below header pills containing action pills
 * - No full-width gradient scrim
 */
@Composable
fun PlayerTopChrome(
    onClose: () -> Unit,
    state: PlaybackState,
    currentSettings: AppSettings,
    isFavourite: Boolean,
    isLandscapeOrientation: Boolean,
    topBarActionsListState: LazyListState,
    onOpenSheet: (PlaybackSheetPage) -> Unit,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val edgeInset = if (isLandscapeOrientation) 24.dp else 16.dp
    var isStripExpanded by remember { mutableStateOf(false) }

    val stripWidthFraction by animateFloatAsState(
        targetValue = if (isLandscapeOrientation) {
            if (isStripExpanded) 0.80f else 0.30f
        } else {
            if (isStripExpanded) 0.95f else 0.80f
        },
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "stripWidthFraction",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Header: 4 separate pills in one row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pill 1: Back pill (circular)
            FloatingIsland(
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_cd_back),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            }

            // Pill 2: Name pill (title takes middle space, single line ellipsized)
            FloatingIsland(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = state.currentItem?.title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Pill 3: Decoder pill (video only, keeps tap-to-cycle decoder behaviour)
            if (state.currentItem?.isVideo == true) {
                val decoderPillText = state.activeDecoderName?.let { name ->
                    val codec = MpxMediaCodecSelector.shortCodecLabel(name)
                    if (codec != null) "${state.decoderMode.label} • $codec" else state.decoderMode.label
                } ?: state.decoderMode.label

                FloatingIsland(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clickable {
                            val nextDecoder = when (state.decoderMode) {
                                DecoderMode.HW -> DecoderMode.HW_PLUS
                                DecoderMode.HW_PLUS -> DecoderMode.SW
                                DecoderMode.SW -> DecoderMode.HW
                            }
                            viewModel.setDecoderMode(nextDecoder)
                            viewModel.updateSettings(viewModel.loadSettingsNow().copy(decoderMode = nextDecoder.label))
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_toast_decoder, context.getString(nextDecoder.titleRes)),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = decoderPillText,
                            color = MpxTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Pill 4: Three-dots pill (MoreVert, opens Playback sheet Main page)
            FloatingIsland(
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = { onOpenSheet(PlaybackSheetPage.Main) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.player_cd_more),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            }
        }

        // 2. Small gap directly below header pills
        Spacer(Modifier.height(8.dp))

        // 3. Strip Menu: floating strip under the header (controlled by settings toggle).
        // In portrait it stays centered (80% / 95% expanded). In landscape
        // it aligns to the LEFT edge — same edge inset as the header —
        // instead of floating centered on screen. The strip Box is a
        // direct child of the centered Column, so align(Start) overrides
        // the Column's centering for this child only; the start padding
        // puts its left edge at the header's inset. Expanding in
        // landscape grows to the right from that left edge.
        if (currentSettings.showPlayerStripMenu) {
            Box(
                modifier = if (isLandscapeOrientation) {
                    Modifier
                        .align(Alignment.Start)
                        .padding(start = edgeInset)
                        .fillMaxWidth(stripWidthFraction)
                } else {
                    Modifier.fillMaxWidth(stripWidthFraction)
                },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PlayerTopBarActions(
                        modifier = Modifier.weight(1f, fill = false),
                        listState = topBarActionsListState,
                        state = state,
                        currentSettings = currentSettings,
                        isFavourite = isFavourite,
                        onOpenSheet = onOpenSheet,
                        onToggleFavourite = onToggleFavourite,
                        onEnterPip = onEnterPip,
                        onToggleBackgroundPlay = onToggleBackgroundPlay,
                        onAddToPlaylist = onAddToPlaylist,
                        playbackController = viewModel,
                    )

                    Spacer(Modifier.width(6.dp))

                    // Expand / collapse chevron button
                    FloatingIsland(
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { isStripExpanded = !isStripExpanded },
                    ) {
                        Icon(
                            imageVector = if (isStripExpanded) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = stringResource(
                                if (isStripExpanded) R.string.player_cd_collapse else R.string.player_cd_expand,
                            ),
                            tint = MpxTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
