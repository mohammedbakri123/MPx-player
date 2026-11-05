package com.mpx.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mpx.player.domain.model.AudioAlbum
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.domain.model.VideoFile
import androidx.compose.material3.MaterialTheme
import com.mpx.player.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

/**
 * Reusable asynchronous thumbnail loader that checks the in-memory LruCache synchronously,
 * fetches real MediaStore / ID3 thumbnails asynchronously on Dispatchers.IO if uncached,
 * and renders an honest vector placeholder icon while loading or if no media thumbnail exists.
 */
@Composable
fun AsyncThumbnail(
    cacheKey: String,
    loadBitmap: suspend () -> Bitmap?,
    placeholderIcon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    contentDescription: String? = null,
    showThumbnails: Boolean = true,
    // Folder tiles pass the theme primary here so the folder placeholder reads
    // as app-identity blue; other placeholders keep the neutral default.
    placeholderTint: Color = MpxTextSecondary,
) {
    val cachedBitmap = remember(cacheKey) { ThumbnailLoader.getCached(cacheKey) }
    var bitmap by remember(cacheKey) { mutableStateOf(cachedBitmap) }

    LaunchedEffect(cacheKey, showThumbnails) {
        if (showThumbnails && cachedBitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                loadBitmap()
            }
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    // With thumbnails off, always show the honest placeholder icon, even for
    // media whose thumbnail is already in the in-memory cache.
    val displayBitmap = if (showThumbnails) bitmap else null
    Crossfade(
        targetState = displayBitmap,
        animationSpec = tween(durationMillis = 180),
        label = "thumbnailCrossfade",
        modifier = modifier,
    ) { currentBitmap ->
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MpxBlack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = contentDescription,
                    tint = placeholderTint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
fun AsyncFolderThumbnail(
    folder: MediaFolder,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val cacheKey = "folder_${folder.id.ifBlank { folder.path }}"

    AsyncThumbnail(
        cacheKey = cacheKey,
        loadBitmap = {
            ThumbnailLoader.loadFolderThumbnail(
                context = context,
                folderId = folder.id,
                folderPath = folder.path,
                width = 240,
                height = 240,
            )
        },
        placeholderIcon = Icons.Filled.Folder,
        placeholderTint = MpxPrimary,
        modifier = modifier,
        iconSize = iconSize,
        contentDescription = "Folder ${folder.name}",
        showThumbnails = showThumbnails,
    )
}

@Composable
fun AsyncAudioTrackThumbnail(
    track: AudioTrack,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val cacheKey = "audio_track_${track.id.ifBlank { track.path }}"

    AsyncThumbnail(
        cacheKey = cacheKey,
        loadBitmap = {
            ThumbnailLoader.loadAudioTrackThumbnail(
                context = context,
                trackId = track.id,
                trackPath = track.path,
                width = 160,
                height = 160,
            )
        },
        placeholderIcon = Icons.Filled.MusicNote,
        modifier = modifier,
        iconSize = iconSize,
        contentDescription = "Track ${track.title}",
        showThumbnails = showThumbnails,
    )
}

@Composable
fun AsyncAlbumThumbnail(
    album: AudioAlbum,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val cacheKey = "album_${album.title}"

    AsyncThumbnail(
        cacheKey = cacheKey,
        loadBitmap = {
            ThumbnailLoader.loadAlbumThumbnail(
                context = context,
                albumTitle = album.title,
                width = 240,
                height = 240,
            )
        },
        placeholderIcon = Icons.Filled.MusicNote,
        modifier = modifier,
        iconSize = iconSize,
        contentDescription = "Album ${album.title}",
        showThumbnails = showThumbnails,
    )
}

@Composable
fun AsyncVideoThumbnail(
    video: VideoFile,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val cacheKey = "video_${video.id.ifBlank { video.path }}"
    val videoIdLong = video.id.toLongOrNull() ?: -1L

    AsyncThumbnail(
        cacheKey = cacheKey,
        loadBitmap = {
            ThumbnailLoader.loadVideoThumbnail(
                context = context,
                videoId = videoIdLong,
                filePath = video.path,
                width = 320,
                height = 240,
            )
        },
        placeholderIcon = Icons.Filled.PlayArrow,
        modifier = modifier,
        iconSize = iconSize,
        contentDescription = "Video ${video.title}",
        showThumbnails = showThumbnails,
    )
}

@Composable
fun AsyncVideoThumbnail(
    mediaUri: String,
    mediaId: String,
    title: String = "",
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val cacheKey = "video_${mediaId.ifBlank { mediaUri }}"
    val videoIdLong = mediaId.toLongOrNull() ?: -1L

    AsyncThumbnail(
        cacheKey = cacheKey,
        loadBitmap = {
            ThumbnailLoader.loadVideoThumbnail(
                context = context,
                videoId = videoIdLong,
                filePath = mediaUri,
                width = 320,
                height = 240,
            )
        },
        placeholderIcon = Icons.Filled.PlayArrow,
        modifier = modifier,
        iconSize = iconSize,
        contentDescription = if (title.isNotBlank()) "Video $title" else null,
        showThumbnails = showThumbnails,
    )
}
