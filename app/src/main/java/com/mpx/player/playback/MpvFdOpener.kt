package com.mpx.player.playback

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Makes `content://` media playable on mpv (issue #150).
 *
 * libmpv cannot open `content://` URIs — it only understands filesystem
 * paths, URLs and its own `fd://` scheme. Files whose MediaStore DATA
 * column is blank fall back to a `content://` URI (see
 * MediaStoreRepository), which used to be rescued by the Media3 fallback;
 * with Media3 gone those files would fail outright.
 *
 * Fix (the same pattern mpv-android itself uses for SAF/content URIs):
 * open a read file descriptor through the ContentResolver and hand mpv
 * `fd://<n>`. The descriptor is DETACHED from its ParcelFileDescriptor,
 * so the raw fd stays valid in this process and libmpv — which runs
 * in-process via JNI — owns it and closes it when its stream ends.
 * Nothing is copied and nothing is persisted: the queue store keeps the
 * original `content://` URI, so a restored queue re-opens a fresh fd.
 *
 * Honest limits:
 * - If the descriptor cannot be opened (e.g. the permission was revoked),
 *   the original URI is returned unchanged and mpv reports a real load
 *   error in the banner — no silent fallback, no fake success.
 * - `fd://` is a plain byte stream: mpv seeks on it only if the underlying
 *   file is seekable (true for local MediaStore files).
 * - A loadfile that mpv rejects before opening the stream leaks one fd
 *   until the process dies; the normal load/failure paths close it.
 */
class MpvFdOpener(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Resolves one media item for the engine: its file URI, its legacy
     * single subtitle URI and every external subtitle URI. Non-content
     * URIs (file paths, http(s), fd://) pass through untouched. */
    fun resolveForEngine(item: PlayableMedia): PlayableMedia {
        val uri = resolveUri(item.uri)
        val subtitleUri = item.subtitleUri?.let { resolveUri(it) }
        val external = if (item.externalSubtitles.isEmpty()) {
            item.externalSubtitles
        } else {
            item.externalSubtitles.map { it.copy(uri = resolveUri(it.uri)) }
        }
        return if (uri == item.uri && subtitleUri == item.subtitleUri && external == item.externalSubtitles) {
            item
        } else {
            item.copy(uri = uri, subtitleUri = subtitleUri, externalSubtitles = external)
        }
    }

    /** Resolves a list of items for the engine (queue loads). */
    fun resolveAllForEngine(items: List<PlayableMedia>): List<PlayableMedia> =
        items.map { resolveForEngine(it) }

    private fun resolveUri(uri: String): String {
        if (!uri.startsWith("content://")) return uri
        return try {
            val pfd = appContext.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?: return uri
            // detachFd() transfers ownership of the raw fd out of the
            // ParcelFileDescriptor: the fd stays open, libmpv closes it
            // when its fd:// stream ends. Without the detach, the
            // ParcelFileDescriptor finalizer could close the fd under mpv.
            "fd://${pfd.detachFd()}"
        } catch (e: Exception) {
            Log.w("MpvFdOpener", "Could not open fd for $uri; leaving it for mpv to fail honestly", e)
            uri
        }
    }
}
