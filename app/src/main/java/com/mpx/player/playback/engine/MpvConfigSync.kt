package com.mpx.player.playback.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Issue #431/#432: mpv config-folder access (mpvEx parity — mpvEx's
 * AdvancedPreferencesScreen + PlayerActivity sync, Apache-2.0; shape
 * copied, MPx's own code).
 *
 * The user-picked folder (SAF tree URI, stored in
 * [com.mpx.player.domain.settings.AppSettings.mpvConfStorageUri]) is
 * the source of truth for mpv.conf / input.conf: the editor reads from
 * it and writes back to it, and the player-start sync copies its files
 * into the app-private config dir, which is where libmpv actually
 * reads them ([MpvPlayerEngine] sets config-dir there).
 *
 * All SAF tree access goes through the framework DocumentsContract —
 * no new dependency. Everything is best-effort: a revoked permission
 * or a missing file logs and leaves the current local files alone.
 * The sync NEVER deletes anything, so the bundled subfont.ttf /
 * cacert.pem (#429) can never be removed by it.
 */
object MpvConfigSync {
    private const val TAG = "MpvConfigSync"

    const val MPV_CONF = "mpv.conf"
    const val INPUT_CONF = "input.conf"

    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "woff", "woff2")

    /** The app-private mpv config dir — the same dir MpvPlayerEngine points config-dir at. */
    fun configDir(context: Context): File =
        File(context.filesDir, "mpv").apply { mkdirs() }

    /** The picked folder's display name, for the row subtitle — the tree document's own name. Null when nothing is picked or the URI can no longer be read. */
    fun folderDisplayName(context: Context, treeUriString: String): String? {
        if (treeUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (col < 0) null else cursor.getString(col)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Read one config file from the picked folder. Null when no folder is picked, the file is missing, or it cannot be read. */
    fun readConfigFile(context: Context, treeUriString: String, fileName: String): String? {
        if (treeUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            val childUri = findChildUri(context, treeUri, fileName) ?: return null
            context.contentResolver.openInputStream(childUri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            Log.w(TAG, "readConfigFile $fileName failed", e)
            null
        }
    }

    /** Write (create or overwrite) one config file in the picked folder. False when no folder is picked or the write fails. */
    fun writeConfigFile(context: Context, treeUriString: String, fileName: String, text: String): Boolean {
        if (treeUriString.isBlank()) return false
        return try {
            val treeUri = Uri.parse(treeUriString)
            val existing = findChildUri(context, treeUri, fileName)
            val target = existing ?: DocumentsContract.createDocument(
                context.contentResolver, treeUri, "text/plain", fileName,
            ) ?: return false
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                out.write(text.toByteArray())
                out.flush()
            } != null
        } catch (e: Exception) {
            Log.w(TAG, "writeConfigFile $fileName failed", e)
            false
        }
    }

    /** Delete one config file from the picked folder. False when no folder is picked, the file is missing, or the delete fails. */
    fun deleteConfigFile(context: Context, treeUriString: String, fileName: String): Boolean {
        if (treeUriString.isBlank()) return false
        return try {
            val treeUri = Uri.parse(treeUriString)
            val childUri = findChildUri(context, treeUri, fileName) ?: return false
            DocumentsContract.deleteDocument(context.contentResolver, childUri)
        } catch (e: Exception) {
            Log.w(TAG, "deleteConfigFile $fileName failed", e)
            false
        }
    }

    /**
     * Prepare a freshly picked folder (#432, mpvEx shape): create the
     * standard mpv subfolders (fonts, script-opts, scripts, shaders)
     * and an empty mpv.conf if missing. False when no folder is picked
     * or the preparation fails.
     */
    fun prepareConfigFolder(context: Context, treeUriString: String): Boolean {
        if (treeUriString.isBlank()) return false
        return try {
            val treeUri = Uri.parse(treeUriString)
            for (name in listOf("fonts", "script-opts", "scripts", "shaders")) {
                if (findChildUri(context, treeUri, name) == null) {
                    DocumentsContract.createDocument(
                        context.contentResolver, treeUri,
                        DocumentsContract.Document.MIME_TYPE_DIR, name,
                    )
                }
            }
            if (findChildUri(context, treeUri, MPV_CONF) == null) {
                DocumentsContract.createDocument(
                    context.contentResolver, treeUri, "text/plain", MPV_CONF,
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "prepareConfigFolder failed", e)
            false
        }
    }

    /**
     * Player-start sync (#432): copy mpv.conf, input.conf, fonts/*,
     * scripts/* and script-opts/* from the picked folder into
     * [configDir]. A missing file leaves the current local one alone;
     * nothing is ever deleted. Fonts already present are skipped
     * (they are large); scripts and script-opts are overwritten (small
     * files the user edits often). No folder picked = nothing syncs.
     */
    fun sync(context: Context, configDir: File, treeUriString: String) {
        if (treeUriString.isBlank()) return
        try {
            val treeUri = Uri.parse(treeUriString)
            copyTreeFile(context, treeUri, MPV_CONF, File(configDir, MPV_CONF))
            copyTreeFile(context, treeUri, INPUT_CONF, File(configDir, INPUT_CONF))
            copyTreeDir(context, treeUri, "fonts", File(configDir, "fonts"), FONT_EXTENSIONS, overwrite = false)
            copyTreeDir(context, treeUri, "scripts", File(configDir, "scripts"), setOf("lua"), overwrite = true)
            copyTreeDir(context, treeUri, "script-opts", File(configDir, "script-opts"), setOf("conf"), overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "mpv config sync failed", e)
        }
    }

    private fun copyTreeFile(context: Context, treeUri: Uri, name: String, target: File) {
        try {
            val childUri = findChildUri(context, treeUri, name) ?: return
            context.contentResolver.openInputStream(childUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Synced config: $name")
        } catch (e: Exception) {
            Log.w(TAG, "copyTreeFile $name failed", e)
        }
    }

    private fun copyTreeDir(
        context: Context,
        treeUri: Uri,
        dirName: String,
        targetDir: File,
        extensions: Set<String>,
        overwrite: Boolean,
    ) {
        try {
            val dirUri = findChildUri(context, treeUri, dirName) ?: return
            val docId = DocumentsContract.getDocumentId(dirUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idCol < 0 || nameCol < 0) return
                targetDir.mkdirs()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in extensions) continue
                    val target = File(targetDir, name)
                    if (!overwrite && target.exists()) continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                    runCatching {
                        context.contentResolver.openInputStream(childUri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }.onFailure { e -> Log.w(TAG, "copyTreeDir $dirName/$name failed", e) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "copyTreeDir $dirName failed", e)
        }
    }

    /** Find a direct child of the tree by name (case-insensitive), file or directory. */
    private fun findChildUri(context: Context, treeUri: Uri, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idCol < 0 || nameCol < 0) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(nameCol)?.equals(name, ignoreCase = true) == true) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                }
            }
        }
        return null
    }
}
