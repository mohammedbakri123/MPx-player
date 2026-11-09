package com.mpx.player.download

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.mpx.player.ui.screens.download.AUDIO_EXTENSIONS
import java.io.File

/**
 * Issue #412: share a downloaded file with another app, or open it
 * in an external viewer — Seal's `VideoDetailDrawer` share
 * (`ACTION_SEND` + FileProvider) and `FileUtil.openFile`
 * (`ACTION_VIEW` + MIME type).
 *
 * The URI handed out depends on the file kind, because a
 * [DownloadedFile] is not always a plain file: public MediaStore
 * files and SD-card SAF files already carry a `content://` URI,
 * which goes straight into the intent with
 * `FLAG_GRANT_READ_URI_PERMISSION`; app-private files are plain
 * paths and go through the FileProvider (whose paths cover only
 * MPx's own download folders — see `res/xml/file_paths.xml`).
 *
 * Both functions return false instead of throwing when there is no
 * URI to hand out or no app to handle the intent — the caller shows
 * a toast. Never a crash, never a silent no-op.
 */
object DownloadShare {

    /** MIME type from the extension, falling back to video/audio by kind. */
    fun mimeTypeFor(file: DownloadedFile): String {
        val fromExtension = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension)
        return fromExtension
            ?: if (file.extension in AUDIO_EXTENSIONS) "audio/*" else "video/*"
    }

    /**
     * The shareable/openable URI for this file, or null when no URI
     * can be produced (a private file outside the FileProvider
     * paths, or a FileProvider failure).
     */
    fun contentUriFor(context: Context, file: DownloadedFile): Uri? {
        if (file.isPublic || file.isSdCard) {
            return Uri.parse(file.uri)
        }
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(file.uri),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** System share sheet for the file. False when it cannot be shared. */
    fun shareFile(context: Context, file: DownloadedFile): Boolean {
        val uri = contentUriFor(context, file) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Open the file in an external viewer. False when nothing can open it. */
    fun openFile(context: Context, file: DownloadedFile): Boolean {
        val uri = contentUriFor(context, file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
