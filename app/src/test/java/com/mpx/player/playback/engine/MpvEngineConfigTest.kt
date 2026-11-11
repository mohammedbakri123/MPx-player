package com.mpx.player.playback.engine

import com.mpx.player.playback.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Migration Phase 4, step 4: unit tests for the extracted
 * [MpvEngineConfig].
 *
 * The config class itself is not unit-testable: every function
 * touches mpv directly (same as [MpvCoreEngine] and
 * [MpvTrackSelection]), so it is verified by the CI build and on
 * device only. The three exceptions are the pure helpers in its
 * companion object, kept there precisely so they can be tested
 * without mpv. These tests pin them: a wrong answer in
 * [MpvEngineConfig.hwdecValueFor]/[MpvEngineConfig.hwdecValueForStage]
 * initialises mpv with the wrong decoder (or silently drops the
 * hwdec option), and a wrong answer in
 * [MpvEngineConfig.mpvVideoZoom] replays issue #172 — every video
 * playing at 2x the fit size, cropped and zoomed.
 */
class MpvEngineConfigTest {

    // --- hwdecValueFor ---

    @Test
    fun `HW mode maps to mediacodec`() {
        assertEquals("mediacodec", MpvEngineConfig.hwdecValueFor(DecoderMode.HW))
    }

    @Test
    fun `HW_PLUS mode maps to mediacodec-copy`() {
        assertEquals("mediacodec-copy", MpvEngineConfig.hwdecValueFor(DecoderMode.HW_PLUS))
    }

    @Test
    fun `SW mode maps to no`() {
        assertEquals("no", MpvEngineConfig.hwdecValueFor(DecoderMode.SW))
    }

    // --- hwdecValueForStage ---

    @Test
    fun `HW stage maps to mediacodec`() {
        assertEquals("mediacodec", MpvEngineConfig.hwdecValueForStage(MpvCoreEngine.DecoderStage.HW))
    }

    @Test
    fun `HW_PLUS stage maps to mediacodec-copy`() {
        assertEquals(
            "mediacodec-copy",
            MpvEngineConfig.hwdecValueForStage(MpvCoreEngine.DecoderStage.HW_PLUS),
        )
    }

    @Test
    fun `SW stage maps to no`() {
        assertEquals("no", MpvEngineConfig.hwdecValueForStage(MpvCoreEngine.DecoderStage.SW))
    }

    // --- mpvVideoZoom ---

    @Test
    fun `no zoom is zero`() {
        assertEquals(0.0, MpvEngineConfig.mpvVideoZoom(1.0f), 1e-9)
    }

    @Test
    fun `double size is one`() {
        assertEquals(1.0, MpvEngineConfig.mpvVideoZoom(2.0f), 1e-9)
    }

    @Test
    fun `half size is minus one`() {
        assertEquals(-1.0, MpvEngineConfig.mpvVideoZoom(0.5f), 1e-9)
    }

    @Test
    fun `below-range zoom clamps to half size, never NaN`() {
        assertEquals(-1.0, MpvEngineConfig.mpvVideoZoom(0.0f), 1e-9)
        assertEquals(-1.0, MpvEngineConfig.mpvVideoZoom(-2.0f), 1e-9)
    }

    @Test
    fun `above-range zoom clamps to double size`() {
        assertEquals(1.0, MpvEngineConfig.mpvVideoZoom(3.0f), 1e-9)
    }
}
