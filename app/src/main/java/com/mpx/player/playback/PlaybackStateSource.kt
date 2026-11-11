package com.mpx.player.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Migration Phase 2a: the minimal read surface of [PlaybackCoordinator]
 * that [com.mpx.player.ui.screens.player.PlayerViewModel] needs.
 *
 * The ViewModel depends on this interface — never on the concrete
 * [PlaybackCoordinator] — so a unit test can pass a fake instead of the
 * coordinator and its mpv engine. It covers only what the
 * player screen actually uses from playback today:
 *
 * - the playback state flow, and
 * - the process-death queue restore (issue #149).
 *
 * Everything else (transport, subtitles, persistence) still goes
 * through [PlaybackCoordinator] directly from the screen, and moves
 * behind interfaces in later phases. Phase 3 replaced the old
 * PlaybackManager with the coordinator, which implements this same
 * interface, so the ViewModel did not change.
 */
interface PlaybackStateSource {
    /** Current playback state; the same flow [PlaybackCoordinator] exposes. */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Restores the last persisted queue when the in-memory queue is
     * empty (process death while the player route is restored).
     * Returns false when there was nothing to restore; a no-op when
     * the queue is already non-empty.
     */
    fun restorePersistedQueueIfEmpty(): Boolean
}
