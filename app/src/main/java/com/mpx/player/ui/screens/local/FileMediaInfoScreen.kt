package com.mpx.player.ui.screens.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.playback.MediaInfo
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.screens.player.components.playbacksheet.MediaInfoContent
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.FileMediaInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standalone Media Information screen for a file in the list
 * (mpvEx-style: long-press -> Info icon -> info page BEFORE playback).
 * The compact top bar (back + "Media Information" title, same 56dp
 * [MpxCompactTopBar] the other screens use), a vertically scrollable
 * column, and the shared [MediaInfoContent] — the same sections/rows
 * as the player version (issue #468).
 *
 * Loads once via [FileMediaInfoReader] on Dispatchers.IO (disk I/O);
 * a missing/unreadable file yields null, which the shared content
 * renders as "No media loaded" — never a crash.
 */
@Composable
fun FileMediaInfoScreen(
    filePath: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        mediaInfo = withContext(Dispatchers.IO) {
            FileMediaInfoReader.readMediaInfo(filePath)
        }
        loaded = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            MpxCompactTopBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.media_info_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (!loaded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MpxTheme.colors.primary,
                    )
                }
            } else {
                MediaInfoContent(mediaInfo)
            }
        }
    }
}
