package com.mpx.player.util

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvUtilsTest {

    @Test
    fun `returns true when UiModeManager reports television`() {
        assertTrue(
            TvUtils.isTelevisionConfig(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                currentModeType = Configuration.UI_MODE_TYPE_TELEVISION,
                hasLeanbackFeature = false,
            ),
        )
    }

    @Test
    fun `returns true when configuration uiMode is television`() {
        assertTrue(
            TvUtils.isTelevisionConfig(
                uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES,
                currentModeType = Configuration.UI_MODE_TYPE_NORMAL,
                hasLeanbackFeature = false,
            ),
        )
    }

    @Test
    fun `returns true when device declares leanback feature`() {
        assertTrue(
            TvUtils.isTelevisionConfig(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                currentModeType = null,
                hasLeanbackFeature = true,
            ),
        )
    }

    @Test
    fun `returns false on standard phone configuration`() {
        assertFalse(
            TvUtils.isTelevisionConfig(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES,
                currentModeType = Configuration.UI_MODE_TYPE_NORMAL,
                hasLeanbackFeature = false,
            ),
        )
    }
}
