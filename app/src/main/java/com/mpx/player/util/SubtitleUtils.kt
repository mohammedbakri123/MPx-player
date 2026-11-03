package com.mpx.player.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MimeTypes

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
 * or filesystem paths. Validates format support strictly (SRT / VTT) and provides
 * honest user-facing explanations for unsupported formats.
 */
object SubtitleUtils {

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
                mimeType = MimeTypes.APPLICATION_SUBRIP,
            )
        }

        // 2. VTT extension check
        if (lowerName.endsWith(".vtt") || lowerName.endsWith(".webvtt")) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = MimeTypes.TEXT_VTT,
            )
        }

        // 3. MIME type checks if extension is missing or uninformative
        if (mimeTypeFromResolver.equals(MimeTypes.TEXT_VTT, ignoreCase = true) ||
            mimeTypeFromResolver.contains("text/vtt")
        ) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = MimeTypes.TEXT_VTT,
            )
        }

        if (mimeTypeFromResolver.equals(MimeTypes.APPLICATION_SUBRIP, ignoreCase = true) ||
            mimeTypeFromResolver.contains("subrip") ||
            mimeTypeFromResolver.contains("x-srt")
        ) {
            return SubtitleValidationResult.Supported(
                uri = uri,
                displayName = fileName,
                mimeType = MimeTypes.APPLICATION_SUBRIP,
            )
        }

        // 4. Sniff content for header signatures if extension is .txt or extensionless
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
                            mimeType = MimeTypes.TEXT_VTT,
                        )
                    }
                    if (content.contains("-->")) {
                        return SubtitleValidationResult.Supported(
                            uri = uri,
                            displayName = "$fileName (SRT)",
                            mimeType = MimeTypes.APPLICATION_SUBRIP,
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
            "unrecognized format"
        }

        return SubtitleValidationResult.Unsupported(
            fileName = fileName,
            reason = "Unsupported format '$extension'. MPx Player currently supports .srt (SubRip) and .vtt (WebVTT) subtitle files.",
        )
    }
}
