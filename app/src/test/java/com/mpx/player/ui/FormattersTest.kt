package com.mpx.player.ui

import com.mpx.player.ui.screens.local.components.formatDuration
import com.mpx.player.ui.screens.local.components.formatSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for UI utility formatters [formatSize] and [formatDuration] (CP-7, Step 2.4).
 *
 * Verifies byte-identical output across all size and duration bands and edge cases.
 */
class FormattersTest {

    @Test
    fun formatSize_zeroAndNegativeBytes_returnsZeroB() {
        assertEquals("0 B", formatSize(0L))
        assertEquals("0 B", formatSize(-1L))
        assertEquals("0 B", formatSize(-1024L))
    }

    @Test
    fun formatSize_bytesBand() {
        assertEquals("1 B", formatSize(1L))
        assertEquals("512 B", formatSize(512L))
        assertEquals("1023 B", formatSize(1023L))
    }

    @Test
    fun formatSize_kilobytesBand() {
        assertEquals("1.0 KB", formatSize(1024L))
        assertEquals("1.5 KB", formatSize(1536L))
        assertEquals("10.0 KB", formatSize(10240L))
        assertEquals("1023.9 KB", formatSize(1048473L))
    }

    @Test
    fun formatSize_megabytesBand() {
        assertEquals("1.0 MB", formatSize(1048576L))
        assertEquals("500.0 MB", formatSize(524288000L))
        assertEquals("1023.0 MB", formatSize(1072693248L))
    }

    @Test
    fun formatSize_gigabytesBand() {
        assertEquals("1.0 GB", formatSize(1073741824L))
        assertEquals("2.5 GB", formatSize(2684354560L))
        assertEquals("100.0 GB", formatSize(107374182400L))
    }

    @Test
    fun formatDuration_zeroAndNegative_returnsZeroMinutesZeroSeconds() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(-1L))
        assertEquals("0:00", formatDuration(-60000L))
    }

    @Test
    fun formatDuration_subMinuteDurations() {
        assertEquals("0:01", formatDuration(1000L))
        assertEquals("0:09", formatDuration(9000L))
        assertEquals("0:10", formatDuration(10000L))
        assertEquals("0:59", formatDuration(59000L))
    }

    @Test
    fun formatDuration_subHourDurations() {
        // Single digit minutes (M:SS)
        assertEquals("1:00", formatDuration(60000L))
        assertEquals("1:05", formatDuration(65000L))
        assertEquals("9:59", formatDuration(599000L))

        // Multi digit minutes (MM:SS)
        assertEquals("10:00", formatDuration(600000L))
        assertEquals("12:34", formatDuration(754000L))
        assertEquals("59:59", formatDuration(3599000L))
    }

    @Test
    fun formatDuration_hourAndAboveDurations() {
        // Single digit hours (H:MM:SS)
        assertEquals("1:00:00", formatDuration(3600000L))
        assertEquals("1:01:05", formatDuration(3665000L))
        assertEquals("2:15:30", formatDuration(8130000L))

        // Multi digit hours (HH:MM:SS)
        assertEquals("10:00:00", formatDuration(36000000L))
        assertEquals("24:00:00", formatDuration(86400000L))
    }
}
