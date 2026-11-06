package com.mpx.player.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

sealed class SubtitleValidationResult {
    data class Supported(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
    ) : SubtitleValidationResult()

    data class Unsupported(
        val fileName: String,
        val reason: String,
    ) : SubtitleValidationResult()
}

/**
 * Utility for handling subtitle files loaded via Storage Access Framework (SAF)
 * or filesystem paths. Validates format support strictly (SRT / VTT / ASS) and provides
 * honest user-facing explanations for unsupported formats.
 */
object SubtitleUtils {

    // MIME types for the three subtitle formats mpv accepts via sub-add
    // (issue #150: these used to come from androidx.media3.common.MimeTypes).
    // Issue #352: .ass/.ssa added — mpv plays ASS natively and the downloader
    // already downloads .ass sidecars (MpxDownloader SUBTITLE_EXTENSIONS) and
    // sub-adds them, so the manual picker must stop rejecting them.
    private const val SUBRIP_MIME = "application/x-subrip"
    private const val VTT_MIME = "text/vtt"
    private const val SSA_MIME = "text/x-ssa"

    fun getDisplayName(context: Context, uri: Uri): String {
        var queryName: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                queryName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) cursor.getString(index) else null
                    } else null
                }
            } catch (_: Exception) {
            }
        }
        return if (!queryName.isNullOrBlank()) {
            queryName
        } else {
            uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle"
        }
    }

    fun validateSubtitleFile(context: Context, uri: Uri): SubtitleValidationResult {
        val fileName = getDisplayName(context, uri)
        val lowerName = fileName.lowercase()
        val mimeTypeFromResolver = try {
            context.contentResolver.getType(uri).orEmpty()
        } catch (_: Exception) {
            ""
        }

        // 1. SRT extension check
        if (lowerName.endsWith(".srt")) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = SUBRIP_MIME,
            )
        }

        // 2. VTT extension check
        if (lowerName.endsWith(".vtt") || lowerName.endsWith(".webvtt")) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = VTT_MIME,
            )
        }

        // 3. ASS/SSA extension check (issue #352) — same MIME the downloader
        // maps "ass" to (MpxDownloader.kt), and mpv sub-adds it natively.
        if (lowerName.endsWith(".ass") || lowerName.endsWith(".ssa")) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = SSA_MIME,
            )
        }

        // 4. MIME type checks if extension is missing or uninformative
        if (mimeTypeFromResolver.equals(VTT_MIME, ignoreCase = true) ||
            mimeTypeFromResolver.contains("text/vtt")
        ) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = VTT_MIME,
            )
        }

        if (mimeTypeFromResolver.equals(SUBRIP_MIME, ignoreCase = true) ||
            mimeTypeFromResolver.contains("subrip") ||
            mimeTypeFromResolver.contains("x-srt")
        ) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = SUBRIP_MIME,
            )
        }

        if (mimeTypeFromResolver.equals(SSA_MIME, ignoreCase = true) ||
            mimeTypeFromResolver.contains("x-ssa") ||
            mimeTypeFromResolver.contains("ssa")
        ) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = SSA_MIME,
            )
        }

        // 5. Sniff content for header signatures if extension is .txt or extensionless
        if (lowerName.endsWith(".txt") || !fileName.contains(".")) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = stream.bufferedReader()
                    val lines = (1..15).mapNotNull { reader.readLine() }
                    val content = lines.joinToString("\n")
                    if (content.contains("WEBVTT")) {
                        return SubtitleValidationResult.Supported(
                            uri = uri,
                            displayName = "$fileName (VTT)",
                            mimeType = VTT_MIME,
                        )
                    }
                    if (content.contains("-->")) {
                        return SubtitleValidationResult.Supported(
                            uri = uri,
                            displayName = "$fileName (SRT)",
                            mimeType = SUBRIP_MIME,
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Honest error for unsupported formats
        val extension = if (fileName.contains('.')) {
            ".${fileName.substringAfterLast('.')}"
        } else {
            context.getString(com.mpx.player.R.string.subtitle_unknown_format)
        }

        return SubtitleValidationResult.Unsupported(
            fileName = fileName,
            reason = context.getString(com.mpx.player.R.string.subtitle_unsupported_format, extension),
        )
    }
}
