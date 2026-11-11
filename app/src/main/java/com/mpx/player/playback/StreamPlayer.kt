package com.mpx.player.playback

/**
 * Migration Phase 2d: the minimal play surface of [PlaybackManager]
 * that [com.mpx.player.ui.screens.network.NetworkViewModel] needs.
 *
 * The ViewModel depends on this interface — never on the concrete
 * [PlaybackManager] — so a unit test can pass a fake instead of the
 * 1,400-line singleton and its mpv engine. It covers only what the
 * Network tab actually uses from playback today: hand a stream URL
 * to the player as a one-item queue.
 *
 * Everything else still goes through [PlaybackManager] directly from
 * the screens that use it, and moves behind interfaces in later
 * phases. When Phase 3 replaces [PlaybackManager] with a
 * coordinator, the coordinator implements this same interface and
 * the ViewModel does not change.
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
