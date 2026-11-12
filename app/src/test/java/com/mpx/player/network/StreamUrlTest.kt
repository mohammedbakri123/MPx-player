package com.mpx.player.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlTest {

    @Test
    fun `isSupportedStreamUrl returns true for http, https, and rtsp`() {
        assertTrue(isSupportedStreamUrl("http://example.com/live.m3u8"))
        assertTrue(isSupportedStreamUrl("https://example.com/video.mp4"))
        assertTrue(isSupportedStreamUrl("rtsp://192.168.1.100:554/live"))
    }

    @Test
    fun `isSupportedStreamUrl is case-insensitive and trims whitespace`() {
        assertTrue(isSupportedStreamUrl("  HTTP://EXAMPLE.COM/STREAM  "))
        assertTrue(isSupportedStreamUrl("  Https://example.com/stream\n"))
        assertTrue(isSupportedStreamUrl("\tRTSP://stream.local  "))
    }

    @Test
    fun `isSupportedStreamUrl returns false for unsupported schemes or blank text`() {
        assertFalse(isSupportedStreamUrl(""))
        assertFalse(isSupportedStreamUrl("   "))
        assertFalse(isSupportedStreamUrl("ftp://example.com/file.mp4"))
        assertFalse(isSupportedStreamUrl("file:///storage/emulated/0/video.mp4"))
        assertFalse(isSupportedStreamUrl("content://media/external/video/media/1"))
        assertFalse(isSupportedStreamUrl("smb://nas/share/video.mkv"))
        assertFalse(isSupportedStreamUrl("example.com/video.mp4"))
    }
}
