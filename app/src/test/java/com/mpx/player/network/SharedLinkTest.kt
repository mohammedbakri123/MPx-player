package com.mpx.player.network

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {

    @Test
    fun `urlFromIntent returns null for null intent or unhandled action`() {
        assertNull(SharedLink.urlFromIntent(null))

        val mainIntent = Intent(Intent.ACTION_MAIN)
        assertNull(SharedLink.urlFromIntent(mainIntent))
    }

    @Test
    fun `urlFromIntent extracts URL from ACTION_VIEW intent`() {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/stream.m3u8")
        }
        assertEquals("https://example.com/stream.m3u8", SharedLink.urlFromIntent(viewIntent))

        val httpIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("http://example.com/video.mp4")
        }
        assertEquals("http://example.com/video.mp4", SharedLink.urlFromIntent(httpIntent))

        // Non-http scheme in ACTION_VIEW returns null
        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://media/external/video/media/10")
        }
        assertNull(SharedLink.urlFromIntent(fileIntent))
    }

    @Test
    fun `urlFromIntent extracts URL from ACTION_SEND EXTRA_TEXT and strips punctuation`() {
        // Plain URL
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "https://cdn.example.com/video.mp4")
        }
        assertEquals("https://cdn.example.com/video.mp4", SharedLink.urlFromIntent(sendIntent))

        // URL inside prose with trailing punctuation
        val proseIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Check out this video: https://cdn.example.com/video.mp4! Have fun.")
        }
        assertEquals("https://cdn.example.com/video.mp4", SharedLink.urlFromIntent(proseIntent))

        // URL in parentheses/brackets
        val bracketIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Stream link (https://stream.example.org/live.m3u8).")
        }
        assertEquals("https://stream.example.org/live.m3u8", SharedLink.urlFromIntent(bracketIntent))

        // No URL in text returns null
        val noUrlIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Hello world, no link here!")
        }
        assertNull(SharedLink.urlFromIntent(noUrlIntent))
    }
}
