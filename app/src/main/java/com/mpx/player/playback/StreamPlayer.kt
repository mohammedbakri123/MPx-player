package com.mpx.player.playback

/**
 * Migration Phase 2d: the minimal play surface of [PlaybackCoordinator]
 * that [com.mpx.player.ui.screens.network.NetworkViewModel] needs.
 *
 * The ViewModel depends on this interface — never on the concrete
 * [PlaybackCoordinator] — so a unit test can pass a fake instead of the
 * coordinator and its mpv engine. It covers only what the
 * Network tab actually uses from playback today: hand a stream URL
 * to the player as a one-item queue.
 *
 * Everything else still goes through [PlaybackCoordinator] directly from
 * the screens that use it, and moves behind interfaces in later
 * phases. Phase 3 replaced the old PlaybackManager with the
 * coordinator, which implements this same interface, so the ViewModel
 * did not change.
 */
interface StreamPlayer {
    /**
     * Plays [url] as a one-item queue. The queue item is built
     * exactly the way NetworkScreen built it before this phase:
     * title from the last path segment (or "Network Stream" when
     * there is none), the URL as both subtitle and uri, video,
     * unknown duration.
     */
    fun playStream(url: String)
}
