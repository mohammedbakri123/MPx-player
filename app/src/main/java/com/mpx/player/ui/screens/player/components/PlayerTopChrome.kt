package com.mpx.player.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme

/**
 * Top chrome overlay for [com.mpx.player.ui.screens.player.PlayerScreen] (§6).
 * Back button, title, actions row, decoder pill, and sheet menu launcher.
 */
@Composable
fun PlayerTopChrome(
    onClose: () -> Unit,
    state: PlaybackState,
    currentSettings: AppSettings,
    isFavourite: Boolean,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MpxTheme.colors.black.copy(alpha = 0.75f), Color.Transparent),
                ),
            )
            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_cd_back),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Text(
                    text = state.currentItem?.title ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(8.dp))

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

                val decoderPillText = state.activeDecoderName?.let { name ->
                    val codec = MpxMediaCodecSelector.shortCodecLabel(name)
                    if (codec != null) "${state.decoderMode.label} • $codec" else state.decoderMode.label
                } ?: state.decoderMode.label

                if (state.currentItem?.isVideo == true) Surface(
                    modifier = Modifier
                        .clip(MpxShapes.small)
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
                    color = Color.Transparent,
                    shape = MpxShapes.small,
                ) {
                    Text(
                        text = decoderPillText,
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                IconButton(onClick = { onOpenSheet(PlaybackSheetPage.Main) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.player_cd_more),
                        tint = MpxTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}
