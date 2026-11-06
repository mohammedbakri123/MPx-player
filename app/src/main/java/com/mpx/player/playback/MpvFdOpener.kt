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
 *
 * Lazy resolution (issues #213/#219): an fd is single-use — libmpv
 * closes it when its stream ends — so NOTHING here resolves a whole
 * queue up front. The engine resolves only the item it is about to
 * loadfile, from the queue's original URIs, at load time; the stored
 * queue always keeps the original `content://` URIs. There is
 * deliberately no resolve-all helper any more: resolving N items to
 * load one leaked N-1 descriptors every queue load.
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

    /** Resolves ONLY the subtitle URIs of one media item for the engine:
     * its legacy single subtitle URI and every external subtitle URI.
     * The media URI is left untouched — used by replaceMediaItem, which
     * only sub-adds subtitles and never reloads the media, so opening an
     * fd for the media there would leak one descriptor per subtitle pick
     * (issue #219). Non-content URIs pass through untouched. */
    fun resolveSubtitlesOnly(item: PlayableMedia): PlayableMedia {
        val subtitleUri = item.subtitleUri?.let { resolveUri(it) }
        val external = resolveExternalSubtitles(item.externalSubtitles)
        return if (subtitleUri == item.subtitleUri && external == item.externalSubtitles) {
            item
        } else {
            item.copy(subtitleUri = subtitleUri, externalSubtitles = external)
        }
    }

    /** Resolves just a list of external subtitles (URIs only). Used where
     * only the newly added subtitles are sub-added, so no fd is opened
     * for a subtitle that is never handed to mpv. */
    fun resolveExternalSubtitles(external: List<ExternalSubtitle>): List<ExternalSubtitle> =
        if (external.isEmpty()) {
            external
        } else {
            external.map { it.copy(uri = resolveUri(it.uri)) }
        }

    /**
     * Closes the raw descriptors behind `fd://` URIs that [resolveUri]
     * opened but that were never handed to libmpv (issue #332: pending
     * subtitles overwritten, dropped as stale, or abandoned on stop /
     * release). Non-`fd://` URIs are no-ops — nothing was opened for
     * them. Only call for descriptors this side still owns: once a
     * sub-add command has run, libmpv owns the descriptor (its stream
     * closes it when it ends), so closing here would double-close.
     * A descriptor that is already closed is silently ignored.
     */
    fun closeFds(uris: List<String>) {
        for (uri in uris) {
            if (!uri.startsWith("fd://")) continue
            val fd = uri.removePrefix("fd://").toIntOrNull() ?: continue
            try {
                // Os.close() takes a FileDescriptor, not a raw int —
                // adoptFd wraps the int and close() closes the fd.
                android.os.ParcelFileDescriptor.adoptFd(fd).close()
            } catch (_: Exception) {
                // Already closed, or never a valid fd — nothing to do.
            }
        }
    }

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
