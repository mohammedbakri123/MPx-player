package com.mpx.player.ui.screens.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekThumbnailProviderTest {

    @Test
    fun isSeekPreviewSupportedSource_acceptsLocalAndRejectsNetworkOrBlank() {
        assertTrue(isSeekPreviewSupportedSource("content://media/external/video/media/42", null))
        assertTrue(isSeekPreviewSupportedSource("file:///storage/emulated/0/Movies/clip.mp4", null))
        assertTrue(isSeekPreviewSupportedSource("/storage/emulated/0/Movies/clip.mp4", null))
        assertTrue(isSeekPreviewSupportedSource("asset:///sample.mp4", null))
        assertTrue(
            isSeekPreviewSupportedSource(
                uri = "http://ignored.invalid/local-fallback",
                filePath = "/storage/emulated/0/Movies/clip.mp4",
            ),
        )

        assertFalse(isSeekPreviewSupportedSource("https://example.com/stream.m3u8", null))
        assertFalse(isSeekPreviewSupportedSource("rtsp://camera.local/live", null))
        assertFalse(isSeekPreviewSupportedSource("", null))
        assertFalse(isSeekPreviewSupportedSource("   ", "   "))
    }

    @Test
    fun quantizePreviewTimestampMs_clampsAndRoundsToExpectedBuckets() {
        // Short clip (< 20s) uses 500ms buckets.
        assertEquals(0L, quantizePreviewTimestampMs(-500L, 12_000L))
        assertEquals(2_500L, quantizePreviewTimestampMs(2_300L, 12_000L))
        assertEquals(12_000L, quantizePreviewTimestampMs(15_000L, 12_000L))

        // Normal video (<= 1h) uses 1000ms buckets.
        assertEquals(14_000L, quantizePreviewTimestampMs(14_400L, 300_000L))
        assertEquals(15_000L, quantizePreviewTimestampMs(14_600L, 300_000L))

        // Long feature (> 1h) uses 2000ms buckets.
        assertEquals(120_000L, quantizePreviewTimestampMs(120_900L, 7_200_000L))
        assertEquals(122_000L, quantizePreviewTimestampMs(121_100L, 7_200_000L))
    }

    @Test
    fun computeThumbnailTargetSize_preservesAspectWithinBounds() {
        // Landscape 1080p -> 320x180
        assertEquals(320 to 180, computeThumbnailTargetSize(1920, 1080))
        // Portrait 9:16 (1080x1920) -> 101x180
        assertEquals(101 to 180, computeThumbnailTargetSize(1080, 1920))
        // Unknown dimensions -> fallback 320x180
        assertEquals(320 to 180, computeThumbnailTargetSize(0, 0))
    }

    @Test
    fun computePreviewPopupOffsetPx_centersOnThumbAndClampsToDockEdges() {
        // Left edge clamp
        assertEquals(0f, computePreviewPopupOffsetPx(thumbCenterX = 30f, cardWidthPx = 160f, containerWidthPx = 600f), 0.01f)
        // Middle centered
        assertEquals(220f, computePreviewPopupOffsetPx(thumbCenterX = 300f, cardWidthPx = 160f, containerWidthPx = 600f), 0.01f)
        // Right edge clamp
        assertEquals(440f, computePreviewPopupOffsetPx(thumbCenterX = 590f, cardWidthPx = 160f, containerWidthPx = 600f), 0.01f)
        // Narrow container smaller than card -> 0f
        assertEquals(0f, computePreviewPopupOffsetPx(thumbCenterX = 50f, cardWidthPx = 160f, containerWidthPx = 120f), 0.01f)
    }
}
