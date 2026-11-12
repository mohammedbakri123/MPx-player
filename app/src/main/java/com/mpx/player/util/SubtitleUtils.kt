package com.mpx.player.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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
    // Issue #352: .ass/.ssa added — mpv plays ASS natively and
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

        // 3. ASS/SSA extension check (issue #352) — mpv sub-adds it natively.
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

        // Honest error for unsupported formats. The getString() lookups
        // fall back to plain messages when the resource lookup returns
        // null — which happens in host JVM unit tests, where unmocked
        // framework methods return default values
        // (unitTests.isReturnDefaultValues = true) instead of throwing.
        // Context.getString() is final and the stub returns null without
        // delegating to getResources(), so no fake Resources can
        // intercept it; the fallback keeps this function total instead.
        val extension = if (fileName.contains('.')) {
            ".${fileName.substringAfterLast('.')}"
        } else {
            context.getString(com.mpx.player.R.string.subtitle_unknown_format)
                ?: "unknown format"
        }

        return SubtitleValidationResult.Unsupported(
            fileName = fileName,
            reason = context.getString(com.mpx.player.R.string.subtitle_unsupported_format, extension)
                ?: "Unsupported subtitle format ($extension)",
        )
    }

    // Issue #441: mpv sub-codepage detection for external subtitle files.
    // Arabic subtitles are commonly saved as Windows-1256, not UTF-8, and
    // mpv's auto-detection reads them as UTF-8/Latin-1, so every Arabic
    // letter renders as a wrong Latin letter (mojibake). The file loads
    // fine — only the decoding is wrong, and mpv-android shows the same
    // garbage, so it is mpv's detection failing, not an MPx-only bug.
    private const val MAX_DETECT_BYTES = 64 * 1024
    private const val MIN_ARABIC_LETTERS = 20
    private val WINDOWS_1256: Charset = Charset.forName("windows-1256")

    /**
     * Issue #441: detect the mpv sub-codepage for an external subtitle
     * file. Returns "cp1256" when the file is NOT valid UTF-8 and decodes
     * as Windows-1256 with substantial Arabic content; null otherwise,
     * which means "leave mpv's default alone". Any read/decode failure
     * returns null too — detection must never break a subtitle load.
     *
     * Only Windows-1256 is detected: other legacy encodings (e.g. Cyrillic
     * cp1251) are left to mpv's default, exactly as today — no file gets
     * worse than it already is.
     */
    fun detectSubtitleCodepage(context: Context, uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                // A bounded sample is enough to decide the encoding, and
                // keeps a huge subtitle file from being read in full.
                val buf = ByteArray(MAX_DETECT_BYTES)
                var read = 0
                while (read < buf.size) {
                    val n = stream.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                buf.copyOf(read)
            } ?: return null
            if (bytes.isEmpty()) return null
            // Valid UTF-8 (with or without a BOM) — mpv handles it.
            if (isValidUtf8(bytes)) return null
            val text = try {
                WINDOWS_1256.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                return null
            }
            val letters = text.count { it.isLetter() }
            if (letters == 0) return null
            val arabic = text.count {
                it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
            }
            // Substantial Arabic content: at least 20 Arabic letters and
            // at least 5% of the letters. The reported Hannibal file has
            // 8807 Arabic characters; a mostly-English file with a few
            // Arabic words also qualifies, which is correct — cp1256
            // decodes ASCII identically to UTF-8.
            if (arabic >= MIN_ARABIC_LETTERS && arabic * 20 >= letters) "cp1256" else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }
}
