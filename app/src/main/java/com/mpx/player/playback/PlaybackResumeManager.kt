package com.mpx.player.playback

import com.mpx.player.domain.repository.MediaPersistenceRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    /**
     * Issue #220: the Room preload, as a [Deferred] so readers can wait
     * for it. The manager used to fill the map with a fire-and-forget
     * launch: a read in the first moments after construction (tapping a
     * video right after a cold start, or the process-death restore in
     * PlayerScreen) saw an empty map and started the video from 0.
     * The deferred always completes normally — a load failure falls back
     * to the empty map, exactly as before.
     * Issue #227: keys are the stored mediaId values — URIs for rows
     * written after #227, bare ids for legacy rows (orphans, never
     * looked up, see the class KDoc).
     */
    private val preloadDeferred: Deferred<Unit> = coroutineScope.async {
        persistenceRepository?.let { repo ->
            try {
                val persisted = repo.loadAllResumePositionsMap()
                // putIfAbsent, NOT putAll: a position saved before the
                // preload lands (saveResumePosition writes the map
                // immediately; its Room write is async) is NEWER than the
                // Room row the preload just read, and must never be
                // clobbered by it.
                persisted.forEach { (id, position) -> resumeMap.putIfAbsent(id, position) }
            } catch (_: Exception) {
                // Fall back cleanly to empty in-memory map
            }
        }
    }

    /** Whether the Room preload has finished (successfully or not). */
    val isPreloadComplete: Boolean
        get() = preloadDeferred.isCompleted

    /** Suspends until the Room preload has finished, then returns. */
    suspend fun awaitPreload() {
        preloadDeferred.await()
    }

    /**
     * Suspends until the Room preload has finished, then returns the
     * cached resume position. Use this wherever a read must not race
     * the preload (playQueue, queue restore); the synchronous
     * [getResumePosition] below is only for paths that tolerate a null
     * before the preload lands.
     * The lookup key is [resumeKey] of [mediaUri] (falling back to [id]) —
     * never the bare id alone (issue #227).
     */
    suspend fun getResumePositionAfterPreload(id: String, mediaUri: String = ""): Long? {
        preloadDeferred.await()
        return resumeMap[resumeKey(mediaUri, id)]
    }

    /**
     * Synchronously returns cached resume position for instantaneous seek setup.
     * Returns null until the Room preload lands — only call this where a
     * null is tolerated (issue #220); use [getResumePositionAfterPreload]
     * where the position must be the saved one.
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
