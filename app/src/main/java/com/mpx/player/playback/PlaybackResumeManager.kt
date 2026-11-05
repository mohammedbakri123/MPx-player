package com.mpx.player.playback

import com.mpx.player.domain.repository.MediaPersistenceRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hybrid resume position manager backed by Room database persistence and in-memory cache.
 * Stores playback progress per media item so resuming a video or audio track restores
 * the exact position across app sessions and process kills.
 *
 * Issue #227: rows are keyed by the stable media URI/path ([resumeKey]), NOT by the
 * bare MediaStore row id. `_ID` is only unique per volume, so two videos on
 * internal storage and an SD card could share one id — and one resume row —
 * and video B would resume at video A's saved position. The URI/path
 * (`PlayableMedia.uri`: the file path, or a volume-qualified content URI when
 * no path is available) is unique across volumes, exactly like the keys
 * favourites/history already use. The Room PK stays the String `mediaId`;
 * only the VALUES written change, so no migration is needed — rows written
 * before this fix (keyed by bare ids) become orphans that are never looked
 * up again and are harmless.
 */
class PlaybackResumeManager(
    private val persistenceRepository: MediaPersistenceRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        /**
         * The resume key for one media item: its stable URI/path, falling back
         * to the bare id only when no URI is available (issue #227). Every
         * save/get/clear path must go through this so the key can never
         * silently revert to the bare id.
         */
        fun resumeKey(mediaUri: String, fallbackId: String): String =
            mediaUri.ifBlank { fallbackId }
    }

    private val resumeMap = ConcurrentHashMap<String, Long>()

    init {
        // Preload persisted resume positions from Room database on startup.
        // Keys are the stored mediaId values: URIs for rows written after
        // issue #227, bare ids for legacy rows (orphans — never looked up,
        // see the class KDoc).
        persistenceRepository?.let { repo ->
            coroutineScope.launch {
                try {
                    val persisted = repo.loadAllResumePositionsMap()
                    resumeMap.putAll(persisted)
                } catch (_: Exception) {
                    // Fall back cleanly to empty in-memory map
                }
            }
        }
    }

    /**
     * Synchronously returns cached resume position for instantaneous seek setup.
     * The lookup key is [resumeKey] of [mediaUri] (falling back to [id]) —
     * never the bare id alone (issue #227).
     */
    fun getResumePosition(id: String, mediaUri: String = ""): Long? =
        resumeMap[resumeKey(mediaUri, id)]

    /**
     * Saves resume position to both in-memory cache and persistent Room database.
     * Stored under the [resumeKey] of [mediaUri] (falling back to [id]), so the
     * Room `mediaId` value is the media URI, not the bare MediaStore id
     * (issue #227).
     */
    fun saveResumePosition(
        id: String,
        positionMs: Long,
        mediaUri: String = "",
        durationMs: Long = 0L,
    ) {
        // Only record position if past 1.5 seconds to avoid micro-resumes at 00:00
        if (positionMs > 1_500L) {
            val key = resumeKey(mediaUri, id)
            resumeMap[key] = positionMs
            persistenceRepository?.let { repo ->
                coroutineScope.launch {
                    try {
                        repo.saveResumePosition(
                            mediaId = key,
                            mediaUri = mediaUri.ifBlank { id },
                            positionMs = positionMs,
                            durationMs = durationMs,
                        )
                    } catch (_: Exception) {
                        // In-memory position remains intact if DB write fails
                    }
                }
            }
        }
    }

    /**
     * Clears resume position from in-memory cache and Room database.
     * The cleared key is [resumeKey] of [mediaUri] (falling back to [id]) —
     * never the bare id alone (issue #227).
     */
    fun clearResumePosition(id: String, mediaUri: String = "") {
        val key = resumeKey(mediaUri, id)
        resumeMap.remove(key)
        persistenceRepository?.let { repo ->
            coroutineScope.launch {
                try {
                    repo.clearResumePosition(key)
                } catch (_: Exception) {
                    // Ignored
                }
            }
        }
    }

    /**
     * Clears all resume positions from cache and Room database.
     */
    fun clearAll() {
        resumeMap.clear()
        persistenceRepository?.let { repo ->
            coroutineScope.launch {
                try {
                    repo.clearAllResumePositions()
                } catch (_: Exception) {
                    // Ignored
                }
            }
        }
    }
}
