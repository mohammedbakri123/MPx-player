package com.mpx.player.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory resume position manager.
 * Stores playback progress per media ID so resuming a video or audio track restores
 * the exact position.
 *
 * NOTE: Persistent database storage is handled by the Room/persistence worktree.
 * This class stores state purely in memory for the app session.
 */
class PlaybackResumeManager {
    private val resumeMap = ConcurrentHashMap<String, Long>()

    fun getResumePosition(id: String): Long? = resumeMap[id]

    fun saveResumePosition(id: String, positionMs: Long) {
        // Only record position if past 1.5 seconds to avoid micro-resumes at 00:00
        if (positionMs > 1_500L) {
            resumeMap[id] = positionMs
        }
    }

    fun clearResumePosition(id: String) {
        resumeMap.remove(id)
    }

    fun clearAll() {
        resumeMap.clear()
    }
}
