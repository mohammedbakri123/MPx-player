package com.mpx.player.download

import android.content.Context
import android.net.Uri
import com.mpx.player.data.local.entity.CommandTemplateEntity
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.domain.settings.AppSettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Issue #413: backup / restore of the downloader's data — Seal's
 * export/import dialog (`ExportImportDialog.kt`, `BackupUtil.kt`).
 * Reinstalling or switching phones used to lose the whole download
 * history, the recent-links list (#406) and the custom command.
 *
 * One JSON file, `version: 1`, written and read through the Storage
 * Access Framework pickers (no new permission, no new dependency —
 * `org.json` is built into Android). It covers history entries, the
 * recent-URL list, the custom command + its on/off toggle, and the
 * command templates (#410). Cookie profiles are deliberately NOT in
 * the backup: they hold live session cookies, and writing them into
 * a plain file the user might share is a footgun.
 *
 * Import merges, except two settings. History entries are inserted
 * as NEW rows (backup ids come from another install and could
 * collide), skipped when a row with the same URL + completion time
 * already exists, so importing the same file twice does not
 * duplicate. Templates insert with REPLACE by id. The custom
 * command and recent links are *replaced* by the backup's — the
 * caller asks first (see the confirm dialog in SettingsScreen).
 */
object DownloadBackup {

    const val BACKUP_VERSION = 1

    sealed interface ImportResult {
        data class Success(val historyImported: Int, val templatesImported: Int) : ImportResult
        data object InvalidFile : ImportResult
    }

    /** Build the backup document from the current state. */
    private suspend fun buildJson(
        downloader: MpxDownloader,
        settingsRepository: AppSettingsRepository,
    ): JSONObject {
        val settings = settingsRepository.load()
        val history = JSONArray()
        for (entry in downloader.downloadHistory.value) {
            history.put(JSONObject().apply {
                put("videoId", entry.videoId)
                put("extractor", entry.extractor)
                put("url", entry.url)
                put("title", entry.title)
                put("uploader", entry.uploader)
                put("thumbnailUrl", entry.thumbnailUrl)
                put("fileUri", entry.fileUri)
                put("fileName", entry.fileName)
                put("fileSize", entry.fileSize)
                put("isPublic", entry.isPublic)
                put("isSdCard", entry.isSdCard)
                put("completedAt", entry.completedAt)
            })
        }
        val templates = JSONArray()
        for (template in downloader.getCommandTemplates().first()) {
            templates.put(JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("template", template.template)
                put("createdAt", template.createdAt)
            })
        }
        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("history", history)
            put("recentUrls", JSONArray(settings.downloadRecentUrls.split('\n').filter { it.isNotBlank() }))
            put("customCommand", settings.customCommand)
            put("customCommandEnabled", settings.customCommandEnabled)
            put("templates", templates)
        }
    }

    /** Write the backup to the picked file. False on any failure. */
    suspend fun writeBackup(
        context: Context,
        uri: Uri,
        downloader: MpxDownloader,
        settingsRepository: AppSettingsRepository,
    ): Boolean {
        return try {
            val json = buildJson(downloader, settingsRepository)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Read a backup file and apply it. A wrong version, unreadable
     * file or malformed JSON is [ImportResult.InvalidFile]; a valid
     * file always applies what it can (a bad row is skipped, the
     * rest still lands).
     */
    suspend fun readBackup(
        context: Context,
        uri: Uri,
        downloader: MpxDownloader,
        settingsRepository: AppSettingsRepository,
    ): ImportResult {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return ImportResult.InvalidFile
        } catch (_: Exception) {
            return ImportResult.InvalidFile
        }
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return ImportResult.InvalidFile
        }
        if (root.optInt("version", -1) != BACKUP_VERSION) return ImportResult.InvalidFile

        var historyImported = 0
        val historyArray = root.optJSONArray("history")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.optJSONObject(i) ?: continue
                val url = obj.optString("url", "")
                if (url.isBlank()) continue
                val completedAt = obj.optLong("completedAt", 0)
                // Dedup: the same URL + completion time is already here
                // (re-importing the same file), so skip it.
                val existing = try {
                    downloader.findHistoryByUrl(url)
                } catch (_: Exception) {
                    null
                }
                if (existing != null && existing.completedAt == completedAt) continue
                val entry = DownloadHistoryEntity(
                    videoId = obj.optString("videoId").ifBlank { null },
                    extractor = obj.optString("extractor").ifBlank { null },
                    url = url,
                    title = obj.optString("title", url),
                    uploader = obj.optString("uploader").ifBlank { null },
                    thumbnailUrl = obj.optString("thumbnailUrl").ifBlank { null },
                    fileUri = obj.optString("fileUri", ""),
                    fileName = obj.optString("fileName", url),
                    fileSize = obj.optLong("fileSize", 0),
                    isPublic = obj.optBoolean("isPublic", true),
                    isSdCard = obj.optBoolean("isSdCard", false),
                    completedAt = completedAt,
                )
                try {
                    downloader.importHistoryEntry(entry)
                    historyImported++
                } catch (_: Exception) {
                    // One bad row must not abort the rest.
                }
            }
        }

        var templatesImported = 0
        val templatesArray = root.optJSONArray("templates")
        if (templatesArray != null) {
            for (i in 0 until templatesArray.length()) {
                val obj = templatesArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                val template = obj.optString("template", "")
                if (name.isBlank() || template.isBlank()) continue
                val saved = try {
                    downloader.saveCommandTemplate(
                        obj.optString("id").ifBlank { null },
                        name,
                        template,
                    )
                } catch (_: Exception) {
                    false
                }
                if (saved) templatesImported++
            }
        }

        // The two settings are replaced by the backup's — the caller
        // asks first (confirm dialog), so this is expected, not a
        // surprise.
        val settings = settingsRepository.load()
        val recentUrls = root.optJSONArray("recentUrls")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val url = array.optString(i, "")
                    if (url.isNotBlank()) add(url)
                }
            }.joinToString("\n")
        } ?: settings.downloadRecentUrls
        settingsRepository.save(
            settings.copy(
                downloadRecentUrls = recentUrls,
                customCommand = root.optString("customCommand", settings.customCommand),
                customCommandEnabled = root.optBoolean("customCommandEnabled", settings.customCommandEnabled),
            ),
        )
        return ImportResult.Success(historyImported, templatesImported)
    }
}
