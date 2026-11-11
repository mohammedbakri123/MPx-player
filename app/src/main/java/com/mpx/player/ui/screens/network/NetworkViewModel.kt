package com.mpx.player.ui.screens.network

import androidx.lifecycle.ViewModel
import com.mpx.player.playback.StreamPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Migration Phase 2d: the Network tab's state, moved out of
 * [NetworkScreen]'s Compose `remember` blocks so it has one owner
 * and can be tested with fakes.
 *
 * Owns all the testable, non-UI state the screen held before: the
 * URL input text, the video-page check and the can-play decision,
 * the shared-link pre-fill, and the play action. Compose-only
 * concerns stay in the screen: layout, and reading the clipboard
 * (an Android platform service the screen hands to [onUrlChange]).
 *
 * The screen holds no history or saved sources — the tab is a URL
 * field with Paste + Play only — so there is no history state to
 * own here. The dependency arrives through the constructor, on the
 * [StreamPlayer] interface only, so a test can pass a fake.
 *
 * No behaviour change: every value below is computed exactly the
 * way the screen computed it before, and the play action builds
 * the same one-item queue (via [StreamPlayer.playStream]) that the
 * screen built inline before.
 */
class NetworkViewModel(
    private val streamPlayer: StreamPlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    /** Current URL text and its derived validation; see [NetworkUiState]. */
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    /** The text field's value changed. */
    fun onUrlChange(text: String) {
        _uiState.value = _uiState.value.copy(urlText = text)
    }

    /**
     * Issue #416: a link shared to the app pre-fills the URL field
     * once. The screen calls this from its `LaunchedEffect` keyed on
     * the shared URL, then invokes its consume callback.
     */
    fun onSharedUrl(url: String) {
        _uiState.value = _uiState.value.copy(urlText = url)
    }

    /**
     * Plays the current URL. A no-op when it cannot play (blank, or
     * a video-page link) — the Play button is disabled then, so this
     * only guards direct callers. The screen navigates to the player
     * after calling this.
     */
    fun playUrl() {
        val state = _uiState.value
        if (!state.canPlay) return
        streamPlayer.playStream(state.trimmedUrl)
    }
}

/**
 * The Network tab's state: the URL input plus the validation the
 * screen renders (error state, Play-button enablement). All three
 * derived values are computed from [urlText] exactly as the screen
 * computed them before: trimmed, a video-page host blocks play,
 * anything else non-blank plays.
 */
data class NetworkUiState(
    val urlText: String = "",
) {
    /** The input, trimmed — what is validated and played. */
    val trimmedUrl: String get() = urlText.trim()

    /** True when the URL points at a video page, not a media file. */
    val isPageUrl: Boolean get() = trimmedUrl.isNotBlank() && isVideoPageUrl(trimmedUrl)

    /** The Play button's enablement: non-blank and not a page URL. */
    val canPlay: Boolean get() = trimmedUrl.isNotBlank() && !isPageUrl
}

/**
 * Hosts that serve web pages, not video files. A link to one of these
 * can never play in a direct-stream player — mpv needs the actual
 * media URL, and extracting it takes yt-dlp, which was removed in
 * #434. mpvEx and MX cannot play these either. The tab says so
 * plainly instead of handing the page to mpv and showing its
 * cryptic "could not play this file" error.
 *
 * Moved verbatim from NetworkScreen in Phase 2d.
 */
private val VIDEO_PAGE_HOSTS = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
    "vimeo.com", "www.vimeo.com",
    "dailymotion.com", "www.dailymotion.com",
    "twitch.tv", "www.twitch.tv",
    "tiktok.com", "www.tiktok.com",
    "facebook.com", "www.facebook.com", "fb.watch",
    "instagram.com", "www.instagram.com",
    "x.com", "twitter.com", "www.twitter.com",
)

private fun isVideoPageUrl(url: String): Boolean {
    val host = url.substringAfter("://", "").substringBefore("/").substringBefore(":")
        .lowercase().trim()
    return host in VIDEO_PAGE_HOSTS
}
