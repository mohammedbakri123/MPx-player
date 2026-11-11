package com.mpx.player.ui.screens.network

import com.mpx.player.playback.StreamPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Migration Phase 2d: the Network tab's ViewModel, tested through
 * a fake [StreamPlayer] only — no mocking framework, no Android
 * runtime. The ViewModel updates its state synchronously (no
 * viewModelScope collectors), so no test dispatcher is needed.
 */
class NetworkViewModelTest {

    private lateinit var streamPlayer: FakeStreamPlayer

    @Before
    fun setUp() {
        streamPlayer = FakeStreamPlayer()
    }

    private fun createViewModel() = NetworkViewModel(streamPlayer)

    @Test
    fun `empty url cannot play`() {
        val viewModel = createViewModel()

        assertEquals("", viewModel.uiState.value.urlText)
        assertFalse(viewModel.uiState.value.canPlay)
        assertFalse(viewModel.uiState.value.isPageUrl)

        viewModel.playUrl()
        assertTrue(streamPlayer.playedUrls.isEmpty())
    }

    @Test
    fun `stream url can play and is handed to the player trimmed`() {
        val viewModel = createViewModel()
        viewModel.onUrlChange("  https://example.com/video.mp4  ")

        assertEquals("  https://example.com/video.mp4  ", viewModel.uiState.value.urlText)
        assertEquals("https://example.com/video.mp4", viewModel.uiState.value.trimmedUrl)
        assertFalse(viewModel.uiState.value.isPageUrl)
        assertTrue(viewModel.uiState.value.canPlay)

        viewModel.playUrl()

        assertEquals(listOf("https://example.com/video.mp4"), streamPlayer.playedUrls)
    }

    @Test
    fun `blank url cannot play`() {
        val viewModel = createViewModel()
        viewModel.onUrlChange("   ")

        assertFalse(viewModel.uiState.value.canPlay)

        viewModel.playUrl()
        assertTrue(streamPlayer.playedUrls.isEmpty())
    }

    @Test
    fun `video-page url is flagged and never plays`() {
        val viewModel = createViewModel()

        // Issue #438: a YouTube page is not a stream — the tab says
        // so instead of handing it to mpv.
        viewModel.onUrlChange("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(viewModel.uiState.value.isPageUrl)
        assertFalse(viewModel.uiState.value.canPlay)
        viewModel.playUrl()
        assertTrue(streamPlayer.playedUrls.isEmpty())

        // Short links and other page hosts count too; the host match
        // is case-insensitive.
        viewModel.onUrlChange("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(viewModel.uiState.value.isPageUrl)
        viewModel.onUrlChange("https://WWW.TWITCH.TV/somechannel")
        assertTrue(viewModel.uiState.value.isPageUrl)
        viewModel.playUrl()
        assertTrue(streamPlayer.playedUrls.isEmpty())
    }

    @Test
    fun `non-page host with a path plays`() {
        val viewModel = createViewModel()
        viewModel.onUrlChange("https://cdn.example.com/v.mp4?token=abc")

        assertFalse(viewModel.uiState.value.isPageUrl)
        assertTrue(viewModel.uiState.value.canPlay)

        viewModel.playUrl()
        assertEquals(
            listOf("https://cdn.example.com/v.mp4?token=abc"),
            streamPlayer.playedUrls,
        )
    }

    @Test
    fun `shared url pre-fills the field`() {
        val viewModel = createViewModel()

        // Issue #416: a link shared to the app pre-fills the field.
        viewModel.onSharedUrl("https://example.com/shared.mp4")

        assertEquals("https://example.com/shared.mp4", viewModel.uiState.value.urlText)
        assertTrue(viewModel.uiState.value.canPlay)
    }

    /** Fake stream player: records every URL the ViewModel plays. */
    private class FakeStreamPlayer : StreamPlayer {
        val playedUrls = mutableListOf<String>()

        override fun playStream(url: String) {
            playedUrls += url
        }
    }
}
