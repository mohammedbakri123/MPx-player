package com.mpx.player.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Last playback queue restored after a process death (issue #149). */
data class PersistedQueue(
    val items: List<PlayableMedia>,
    val currentIndex: Int,
)

/**
 * The queue persistence seam [PlaybackQueue] depends on. The
 * SharedPreferences implementation is [PlaybackQueueStore]; tests
 * pass an in-memory fake. A failed write or a corrupt blob is
 * treated as "nothing persisted" and never breaks playback.
 */
interface QueueStore {
    /** Saves [items] with [currentIndex] as the live position. */
    fun save(items: List<PlayableMedia>, currentIndex: Int)

    /** Loads the last saved queue, or null when there is none. */
    fun load(): PersistedQueue?

    /** Clears the saved queue. */
    fun clear()
}

/**
 * Persists the last playback queue in SharedPreferences as one JSON blob
 * (issue #149).
 *
 * Why SharedPreferences and not Room: the queue is session state, not
 * relational data, and MpxDatabase is version 1 with
 * fallbackToDestructiveMigration — a new Room table would need a version
 * bump plus a hand-written Migration, otherwise upgrading would wipe the
 * user's favourites, history and resume positions. A single opaque prefs
 * string avoids that risk entirely and needs no codegen.
 *
 * Positions are deliberately NOT stored here: they already live in Room
 * via PlaybackResumeManager (updatePosition() saves them continuously
 * during playback), and playQueue() re-applies them under its normal
 * resume rules. This store only answers "what was playing, and where in
 * the queue was it".
 *
 * Writes happen on queue mutations and track transitions only, never from
 * the position ticker, so a write costs one small JSON encode per track
 * change. A failed write or a corrupt blob is treated as "nothing
 * persisted" and never breaks playback.
 */
class PlaybackQueueStore(context: Context) : QueueStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    override fun save(items: List<PlayableMedia>, currentIndex: Int) {
        if (items.isEmpty()) {
            clear()
            return
        }
        try {
            val root = JSONObject()
            root.put(KEY_INDEX, currentIndex.coerceIn(0, items.lastIndex))
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put(FIELD_ID, item.id)
                obj.put(FIELD_TITLE, item.title)
                obj.put(FIELD_SUBTITLE, item.subtitle)
                obj.put(FIELD_URI, item.uri)
                obj.put(FIELD_IS_VIDEO, item.isVideo)
                obj.put(FIELD_DURATION_MS, item.durationMs)
                item.subtitleUri?.let { obj.put(FIELD_SUBTITLE_URI, it) }
                item.filePath?.takeIf { it.isNotBlank() }?.let { obj.put(FIELD_FILE_PATH, it) }
                val subs = JSONArray()
                for (ext in item.externalSubtitles) {
                    val sub = JSONObject()
                    sub.put(FIELD_URI, ext.uri)
                    sub.put(FIELD_NAME, ext.name)
                    sub.put(FIELD_MIME_TYPE, ext.mimeType)
                    // Issue #441: the detected codepage travels with the
                    // subtitle so a restart keeps it; absent in queues
                    // saved before this fix, which read back as null.
                    ext.codepage?.let { sub.put(FIELD_CODEPAGE, it) }
                    subs.put(sub)
                }
                obj.put(FIELD_EXTERNAL_SUBTITLES, subs)
                arr.put(obj)
            }
            root.put(KEY_ITEMS, arr)
            prefs.edit().putString(KEY_QUEUE, root.toString()).apply()
        } catch (_: Exception) {
            // A failed queue write must never break playback; the next
            // successful write replaces it, and load() treats garbage as absent.
        }
    }

    override fun load(): PersistedQueue? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray(KEY_ITEMS) ?: return null
            val items = mutableListOf<PlayableMedia>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString(FIELD_ID, "")
                val uri = obj.optString(FIELD_URI, "")
                // An item without an id or a playable uri cannot be restored.
                if (id.isBlank() || uri.isBlank()) continue
                val subs = mutableListOf<ExternalSubtitle>()
                val subsArr = obj.optJSONArray(FIELD_EXTERNAL_SUBTITLES)
                if (subsArr != null) {
                    for (j in 0 until subsArr.length()) {
                        val sub = subsArr.optJSONObject(j) ?: continue
                        val subUri = sub.optString(FIELD_URI, "")
                        if (subUri.isBlank()) continue
                        subs.add(
                            ExternalSubtitle(
                                uri = subUri,
                                name = sub.optString(FIELD_NAME, ""),
                                mimeType = sub.optString(FIELD_MIME_TYPE, ""),
                                codepage = sub.optString(FIELD_CODEPAGE, "").ifBlank { null },
                            )
                        )
                    }
                }
                items.add(
                    PlayableMedia(
                        id = id,
                        title = obj.optString(FIELD_TITLE, ""),
                        subtitle = obj.optString(FIELD_SUBTITLE, ""),
                        uri = uri,
                        isVideo = obj.optBoolean(FIELD_IS_VIDEO, true),
                        durationMs = obj.optLong(FIELD_DURATION_MS, 0L),
                        subtitleUri = obj.optString(FIELD_SUBTITLE_URI, "").ifBlank { null },
                        externalSubtitles = subs,
                        filePath = obj.optString(FIELD_FILE_PATH, "").ifBlank { null },
                    )
                )
            }
            if (items.isEmpty()) return null
            val index = root.optInt(KEY_INDEX, 0).coerceIn(0, items.lastIndex)
            return PersistedQueue(items, index)
        } catch (_: Exception) {
            return null
        }
    }

    override fun clear() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    companion object {
        private const val PREFS_NAME = "mpx_playback_queue"
        private const val KEY_QUEUE = "queue_json"
        private const val KEY_ITEMS = "items"
        private const val KEY_INDEX = "index"
        private const val FIELD_ID = "id"
        private const val FIELD_TITLE = "title"
        private const val FIELD_SUBTITLE = "subtitle"
        private const val FIELD_URI = "uri"
        private const val FIELD_IS_VIDEO = "isVideo"
        private const val FIELD_DURATION_MS = "durationMs"
        private const val FIELD_SUBTITLE_URI = "subtitleUri"
        private const val FIELD_FILE_PATH = "filePath"
        private const val FIELD_EXTERNAL_SUBTITLES = "externalSubtitles"
        private const val FIELD_NAME = "name"
        private const val FIELD_MIME_TYPE = "mimeType"
        private const val FIELD_CODEPAGE = "codepage"
    }
}
