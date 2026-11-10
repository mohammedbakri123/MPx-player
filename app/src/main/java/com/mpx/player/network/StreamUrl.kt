package com.mpx.player.network

/**
 * Issue #297: the one stream-URL rule, shared by Me's Network Stream
 * dialog and the Network tab — a supported stream URL starts with
 * http://, https:// or rtsp://. Typed text is trimmed first;
 * paste checks the raw text and trims on accept, so the two paths
 * share the scheme list but keep their own trimming behaviour.
 *
 * Moved here from MeScreen in #435 so the dialog and the tab can
 * never drift apart again.
 */
fun isSupportedStreamUrl(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("rtsp://", ignoreCase = true)
}
