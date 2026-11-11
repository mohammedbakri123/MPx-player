package com.mpx.player.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Migration Phase 2a: the minimal read surface of [PlaybackManager]
 * that [com.mpx.player.ui.screens.player.PlayerViewModel] needs.
 *
 * The ViewModel depends on this interface — never on the concrete
 * [PlaybackManager] — so a unit test can pass a fake instead of the
 * 1,400-line singleton and its mpv engine. It covers only what the
 * player screen actually uses from playback today:
 *
 * - the playback state flow, and
 * - the process-death queue restore (issue #149).
 *
 * Everything else (transport, subtitles, persistence) still goes
 * through [PlaybackManager] directly from the screen, and moves
 * behind interfaces in later phases. When Phase 3 replaces
 * [PlaybackManager] with a coordinator, the coordinator implements
 * this same interface and the ViewModel does not change.
 */
interface PlaybackStateSource {
    /** Current playback state; the same flow [PlaybackManager] exposes. */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Restores the last persisted queue when the in-memory queue is
     * empty (process death while the player route is restored).
     * Returns false when there was nothing to restore; a no-op when
     * the queue is already non-empty.
     */
    fun restorePersistedQueueIfEmpty(): Boolean
}
