package com.mpx.player.download

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Issue #433: full-settings XML export / import (mpvEx SettingsManager
 * shape — root element, per-preference key/type/value, export date —
 * rebuilt for MPx's SharedPreferences; NOT the downloader-only JSON
 * backup from #413, which stays separate).
 *
 * One XML file holds the ENTIRE app settings file. Values ride as
 * element text, not attributes: mpv.conf / input.conf are multi-line,
 * and XML attribute normalization would flatten their newlines to
 * spaces on import. String sets use mpvEx's "|||" separator.
 *
 * Import parses the whole file first and only then writes, in one
 * editor transaction (clear + putAll + commit): a malformed file
 * fails with no partial write. It REPLACES the current settings, so
 * the caller asks first and the result dialog says a restart is
 * needed — the running app keeps its in-memory copy until then.
 */
object SettingsBackup {

    const val BACKUP_VERSION = 1

    private const val TAG_ROOT = "mpxSettings"
    private const val TAG_PREFERENCES = "preferences"
    private const val TAG_PREFERENCE = "preference"
    private const val ATTR_KEY = "key"
    private const val ATTR_TYPE = "type"
    private const val ATTR_EXPORT_DATE = "exportDate"
    private const val ATTR_VERSION = "version"

    private const val TYPE_STRING = "string"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_STRING_SET = "stringSet"
    private const val STRING_SET_SEPARATOR = "|||"

    data class ExportStats(val totalExported: Int)

    data class ImportStats(
        val imported: Int = 0,
        val failed: Int = 0,
        val version: String = "unknown",
    )

    sealed interface ImportResult {
        data class Success(val stats: ImportStats) : ImportResult
        data object InvalidFile : ImportResult
    }

    fun defaultExportFilename(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "mpx-settings_${dateFormat.format(Date())}.xml"
    }

    suspend fun exportSettings(context: Context, outputUri: Uri): Result<ExportStats> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    val stats = writeSettingsToXml(context, outputStream)
                    Result.success(stats)
                } ?: Result.failure(Exception("Failed to open output stream"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importSettings(context: Context, inputUri: Uri): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    Result.success(readSettingsFromXml(context, inputStream))
                } ?: Result.failure(Exception("Failed to open input stream"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun writeSettingsToXml(context: Context, outputStream: java.io.OutputStream): ExportStats {
        val prefs = context.getSharedPreferences(
            SharedPrefsAppSettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val serializer: XmlSerializer = Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        serializer.startTag(null, TAG_ROOT)
        serializer.attribute(null, ATTR_VERSION, BACKUP_VERSION.toString())
        serializer.attribute(
            null,
            ATTR_EXPORT_DATE,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        )

        var exportedCount = 0
        serializer.startTag(null, TAG_PREFERENCES)
        for ((key, value) in prefs.all) {
            if (value == null) continue
            val type = when (value) {
                is String -> TYPE_STRING
                is Int -> TYPE_INT
                is Long -> TYPE_LONG
                is Float -> TYPE_FLOAT
                is Boolean -> TYPE_BOOLEAN
                is Set<*> -> TYPE_STRING_SET
                else -> continue
            }
            serializer.startTag(null, TAG_PREFERENCE)
            serializer.attribute(null, ATTR_KEY, key)
            serializer.attribute(null, ATTR_TYPE, type)
            val text = when (value) {
                is Set<*> -> value.joinToString(STRING_SET_SEPARATOR) { it.toString() }
                else -> value.toString()
            }
            serializer.text(text)
            serializer.endTag(null, TAG_PREFERENCE)
            exportedCount++
        }
        serializer.endTag(null, TAG_PREFERENCES)

        serializer.endTag(null, TAG_ROOT)
        serializer.endDocument()
        serializer.flush()

        return ExportStats(totalExported = exportedCount)
    }

    private fun readSettingsFromXml(context: Context, inputStream: java.io.InputStream): ImportResult {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        // The whole file is parsed first; nothing is written until it
        // parses cleanly, so a malformed file cannot half-apply.
        var version = "unknown"
        val values = mutableMapOf<String, Any>()
        var failed = 0
        try {
            var eventType = parser.eventType
            var sawRoot = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        TAG_ROOT -> {
                            sawRoot = true
                            version = parser.getAttributeValue(null, ATTR_VERSION) ?: "unknown"
                        }
                        TAG_PREFERENCE -> {
                            val key = parser.getAttributeValue(null, ATTR_KEY)
                            val type = parser.getAttributeValue(null, ATTR_TYPE)
                            // nextText() reads the element's text (newlines
                            // intact) and lands on its END_TAG.
                            val text = try {
                                parser.nextText()
                            } catch (_: Exception) {
                                failed++
                                null
                            }
                            if (key.isNullOrBlank() || type.isNullOrBlank() || text == null) {
                                failed++
                            } else {
                                val parsed: Any? = when (type) {
                                    TYPE_STRING -> text
                                    TYPE_INT -> text.toIntOrNull()
                                    TYPE_LONG -> text.toLongOrNull()
                                    TYPE_FLOAT -> text.toFloatOrNull()
                                    TYPE_BOOLEAN -> text.toBooleanStrictOrNull()
                                    TYPE_STRING_SET -> text.split(STRING_SET_SEPARATOR)
                                        .filter { it.isNotEmpty() }.toSet()
                                    else -> null
                                }
                                if (parsed == null) failed++ else values[key] = parsed
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            if (!sawRoot) return ImportResult.InvalidFile
        } catch (_: Exception) {
            return ImportResult.InvalidFile
        }

        val prefs = context.getSharedPreferences(
            SharedPrefsAppSettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        prefs.edit().apply {
            clear()
            for ((key, value) in values) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            // commit() writes synchronously: the import replaces the
            // whole settings file, and a swipe-away right after must
            // not lose it.
        }.commit()

        return ImportResult.Success(
            ImportStats(imported = values.size, failed = failed, version = version),
        )
    }
}
