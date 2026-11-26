package com.mpx.player.ui.screens.player.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object SeekThumbnailDefaults {
    const val MAX_WIDTH_PX = 320
    const val MAX_HEIGHT_PX = 180
    const val CACHE_ENTRY_COUNT = 24
    const val DRAG_DEBOUNCE_MS = 50L
}

/**
 * Returns true when [uri] or [filePath] refers to a local source that
 * Android's [MediaMetadataRetriever] can seek quickly without stalling a
 * network socket.
 */
internal fun isSeekPreviewSupportedSource(uri: String, filePath: String?): Boolean {
    val candidates = listOfNotNull(uri.takeIf { it.isNotBlank() }, filePath?.takeIf { it.isNotBlank() })
    return candidates.any { source ->
        source.startsWith("content://") ||
            source.startsWith("file://") ||
            source.startsWith("/") ||
            source.startsWith("asset:///")
    }
}

internal fun previewBucketSizeMs(durationMs: Long): Long = when {
    durationMs <= 0L -> 1000L
    durationMs < 20_000L -> 500L
    durationMs > 3_600_000L -> 2000L
    else -> 1000L
}

/**
 * Quantizes [positionMs] into cacheable frame buckets within `[0, durationMs]`.
 */
internal fun quantizePreviewTimestampMs(positionMs: Long, durationMs: Long): Long {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val clamped = positionMs.coerceIn(0L, safeDuration)
    val bucket = previewBucketSizeMs(safeDuration)
    val quantized = ((clamped + bucket / 2L) / bucket) * bucket
    return quantized.coerceIn(0L, safeDuration)
}

/**
 * Computes bounded thumbnail decode dimensions preserving the video's aspect ratio.
 */
internal fun computeThumbnailTargetSize(
    videoWidth: Int,
    videoHeight: Int,
    maxWidthPx: Int = SeekThumbnailDefaults.MAX_WIDTH_PX,
    maxHeightPx: Int = SeekThumbnailDefaults.MAX_HEIGHT_PX,
): Pair<Int, Int> {
    if (videoWidth <= 0 || videoHeight <= 0) {
        return maxWidthPx to maxHeightPx
    }
    val widthScale = maxWidthPx.toFloat() / videoWidth.toFloat()
    val heightScale = maxHeightPx.toFloat() / videoHeight.toFloat()
    val scale = minOf(widthScale, heightScale, 1f)
    val targetW = (videoWidth * scale).roundToInt().coerceAtLeast(64)
    val targetH = (videoHeight * scale).roundToInt().coerceAtLeast(64)
    return targetW to targetH
}

/**
 * Computes the horizontal offset of a floating seek preview card centered at
 * [thumbCenterX] and clamped inside `[0, containerWidthPx - cardWidthPx]`.
 */
internal fun computePreviewPopupOffsetPx(
    thumbCenterX: Float,
    cardWidthPx: Float,
    containerWidthPx: Float,
): Float {
    val maxOffset = (containerWidthPx - cardWidthPx).coerceAtLeast(0f)
    return (thumbCenterX - cardWidthPx / 2f).coerceIn(0f, maxOffset)
}

/**
 * Asynchronous frame extractor for live YouTube / MX Player style seek previews.
 *
 * Keeps a single [MediaMetadataRetriever] session per video guarded by a [Mutex]
 * so fast drags reuse the open demuxer instead of re-opening on every scrub tick,
 * caches recent quantized frame buckets in an [LruCache], and degrades gracefully
 * to `null` for streams or codecs unsupported by platform decoders.
 */
class SeekThumbnailProvider(
    private val context: Context,
    private val uri: String,
    private val filePath: String?,
    private val isVideo: Boolean,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val supported = isVideo && isSeekPreviewSupportedSource(uri, filePath)
    private val retrieverMutex = Mutex()
    private val frameCache = LruCache<Long, Bitmap>(SeekThumbnailDefaults.CACHE_ENTRY_COUNT)

    private var retriever: MediaMetadataRetriever? = null
    private var openFailed = false
    private var closed = false
    private var activeJob: Job? = null
    private var lastRequestedBucketMs: Long = Long.MIN_VALUE

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    fun requestPreview(
        positionMs: Long,
        durationMs: Long,
        videoWidth: Int,
        videoHeight: Int,
    ) {
        if (!supported || openFailed || closed || durationMs <= 0L) return

        val bucketMs = quantizePreviewTimestampMs(positionMs, durationMs)
        val cached = synchronized(frameCache) { frameCache.get(bucketMs) }
        if (cached != null) {
            activeJob?.cancel()
            lastRequestedBucketMs = bucketMs
            _previewBitmap.value = cached
            return
        }

        if (bucketMs == lastRequestedBucketMs && activeJob?.isActive == true) return
        lastRequestedBucketMs = bucketMs
        val isInitialFrame = _previewBitmap.value == null
        val (targetW, targetH) = computeThumbnailTargetSize(videoWidth, videoHeight)

        activeJob?.cancel()
        activeJob = scope.launch {
            if (!isInitialFrame) {
                delay(SeekThumbnailDefaults.DRAG_DEBOUNCE_MS)
            }
            val bitmap = withContext(Dispatchers.IO) {
                retrieverMutex.withLock {
                    coroutineContext.ensureActive()
                    if (closed || openFailed) return@withLock null
                    synchronized(frameCache) { frameCache.get(bucketMs) }?.let { return@withLock it }

                    val activeRetriever = ensureRetrieverLocked() ?: return@withLock null
                    val extracted = extractFrame(activeRetriever, bucketMs * 1000L, targetW, targetH)
                    if (extracted != null) {
                        synchronized(frameCache) { frameCache.put(bucketMs, extracted) }
                    }
                    extracted
                }
            }
            coroutineContext.ensureActive()
            if (bitmap != null && !closed && lastRequestedBucketMs != Long.MIN_VALUE) {
                _previewBitmap.value = bitmap
            }
        }
    }

    fun clearPreview() {
        activeJob?.cancel()
        activeJob = null
        lastRequestedBucketMs = Long.MIN_VALUE
        _previewBitmap.value = null
    }

    fun close() {
        closed = true
        activeJob?.cancel()
        activeJob = null
        lastRequestedBucketMs = Long.MIN_VALUE
        _previewBitmap.value = null
        synchronized(frameCache) {
            frameCache.evictAll()
        }
        val toRelease = retriever
        retriever = null
        if (toRelease != null) {
            scope.launch(Dispatchers.IO) {
                retrieverMutex.withLock {
                    safeRelease(toRelease)
                }
            }
        }
    }

    private fun ensureRetrieverLocked(): MediaMetadataRetriever? {
        retriever?.let { return it }
        if (openFailed || closed) return null

        val instance = MediaMetadataRetriever()
        val opened = openSource(instance)
        if (!opened) {
            safeRelease(instance)
            openFailed = true
            return null
        }
        retriever = instance
        return instance
    }

    private fun openSource(instance: MediaMetadataRetriever): Boolean {
        val candidates = listOfNotNull(
            uri.takeIf { it.isNotBlank() },
            filePath?.takeIf { it.isNotBlank() && it != uri },
        )
        for (source in candidates) {
            when {
                source.startsWith("content://") -> {
                    if (openContentUri(instance, Uri.parse(source))) return true
                }
                source.startsWith("asset:///") -> {
                    val opened = try {
                        appContext.assets.openFd(source.removePrefix("asset:///")).use { afd ->
                            instance.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }
                    if (opened) return true
                }
                source.startsWith("file://") -> {
                    val path = Uri.parse(source).path
                    if (!path.isNullOrBlank() && openFilePath(instance, path)) return true
                }
                source.startsWith("/") -> {
                    if (openFilePath(instance, source)) return true
                }
            }
        }
        return false
    }

    private fun openContentUri(instance: MediaMetadataRetriever, contentUri: Uri): Boolean {
        val openedByFd = try {
            appContext.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                instance.setDataSource(pfd.fileDescriptor)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
        if (openedByFd) return true
        return try {
            instance.setDataSource(appContext, contentUri)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openFilePath(instance: MediaMetadataRetriever, path: String): Boolean {
        val file = File(path)
        if (file.isFile && file.canRead()) {
            val openedDirect = try {
                instance.setDataSource(file.absolutePath)
                true
            } catch (_: Exception) {
                false
            }
            if (openedDirect) return true
        }
        val contentUri = resolveRawPathToContentUri(path) ?: return false
        return openContentUri(instance, contentUri)
    }

    private fun resolveRawPathToContentUri(path: String): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val args = arrayOf(path)
        val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.getExternalVolumeNames(appContext)
                    .map { MediaStore.Video.Media.getContentUri(it) }
                    .ifEmpty { listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) }
            } catch (_: Exception) {
                listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            }
        } else {
            listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        for (collection in collections) {
            try {
                appContext.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                        if (idCol >= 0) {
                            return ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractFrame(
        instance: MediaMetadataRetriever,
        timeUs: Long,
        targetW: Int,
        targetH: Int,
    ): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                instance.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetW,
                    targetH,
                ) ?: instance.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetW,
                    targetH,
                )
            } else {
                val raw = instance.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: instance.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return null
                if (raw.width > targetW || raw.height > targetH) {
                    val scale = minOf(
                        targetW.toFloat() / raw.width.toFloat(),
                        targetH.toFloat() / raw.height.toFloat(),
                    )
                    val outW = (raw.width * scale).roundToInt().coerceAtLeast(1)
                    val outH = (raw.height * scale).roundToInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(raw, outW, outH, true)
                    if (scaled !== raw) {
                        raw.recycle()
                    }
                    scaled
                } else {
                    raw
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun safeRelease(instance: MediaMetadataRetriever) {
        // MediaMetadataRetriever only implements AutoCloseable on API 29+ (minSdk is 24).
        try {
            instance.release()
        } catch (_: Exception) {
        }
    }
}
