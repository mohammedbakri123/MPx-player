package com.mpx.player.playback

import com.mpx.player.domain.repository.MediaPersistenceRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hybrid resume position manager backed by Room database persistence and in-memory cache.
 * Stores playback progress per media ID so resuming a video or audio track restores
 * the exact position across app sessions and process kills.
 */
class PlaybackResumeManager(
    private val persistenceRepository: MediaPersistenceRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val resumeMap = ConcurrentHashMap<String, Long>()

    init {
        // Preload persisted resume positions from Room database on startup
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
     */
    fun getResumePosition(id: String): Long? = resumeMap[id]

    /**
     * Saves resume position to both in-memory cache and persistent Room database.
     */
    fun saveResumePosition(
        id: String,
        positionMs: Long,
        mediaUri: String = "",
        durationMs: Long = 0L,
    ) {
        // Only record position if past 1.5 seconds to avoid micro-resumes at 00:00
        if (positionMs > 1_500L) {
            resumeMap[id] = positionMs
            persistenceRepository?.let { repo ->
                coroutineScope.launch {
                    try {
                        repo.saveResumePosition(
                            mediaId = id,
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
     */
    fun clearResumePosition(id: String) {
        resumeMap.remove(id)
        persistenceRepository?.let { repo ->
            coroutineScope.launch {
                try {
                    repo.clearResumePosition(id)
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
