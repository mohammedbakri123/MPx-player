package com.mpx.player.playback.engine

import android.util.Log
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.DecoderMode
import kotlin.math.log2

/**
 * Phase 4 step 4: engine configuration, extracted from [MpvPlayerEngine].
 *
 * Owns translating MPx settings into mpv options at startup and on
 * change: the playback tweaks ([applyPlaybackTweaks] — audio delay +
 * video zoom), the hardware-decoding mode ([applyHwdecForMode], via
 * the pure [hwdecValueFor]/[hwdecValueForStage] mappings), and the
 * log2 video-zoom conversion ([mpvVideoZoom]). [MpvPlayerEngine]
 * stays the [PlayerEngine] implementation and is a thin facade
 * delegating to this class.
 *
 * mpv access comes from the core ([MpvCoreEngine.mpv]) — no second MPV
 * instance is created. The one coupling back to the facade is the
 * #226 ZOOM interplay: [applyPlaybackTweaksTo] must not overwrite
 * ZOOM's 2x magnification on an unrelated settings change, and
 * leaving ZOOM must restore the zoom the user had before — but the
 * presentation state that decides that (`currentAspectRatio`,
 * `zoomBeforeAspectZoom`, `videoZoomAtAspectZoomEntry`) is owned by
 * the facade's [MpvPlayerEngine.setAspectRatio]/`setVideoZoom`,
 * which stay in the facade (presentation, not config). So this class
 * reaches that state only through the narrow [ZoomState] interface
 * the facade implements as a small anonymous object delegating to
 * its private vars: the config class reads one boolean and
 * reads/writes two restore-point values, and owns nothing about
 * aspect modes itself.
 *
 * Construction order matters: the core's init applies the saved
 * settings through [applyPlaybackTweaksTo] (the core variant — it
 * takes the core and the zoom state explicitly, so it can run before
 * this class exists, same pattern as
 * [MpvSubtitleRenderer.applySubtitleSettingsTo]). The facade's
 * [MpvCoreEngine.Host] callbacks delegate to the pure companion
 * functions ([hwdecValueFor], [hwdecValueForStage]) during init and
 * to this instance only after construction. [applyHwdecForMode] is
 * safe as an instance call: the core invokes it only from `load()`,
 * never from its init.
 *
 * Verifiable by CI build + device only: every function here touches
 * mpv directly, so there are no unit tests for the class itself —
 * with three exceptions in the companion object, pure logic kept
 * there precisely so it can be tested without mpv (see
 * MpvEngineConfigTest): [hwdecValueFor], [hwdecValueForStage] and
 * [mpvVideoZoom].
 */
internal class MpvEngineConfig(
    private val core: MpvCoreEngine,
    private val zoomState: ZoomState,
) {

    /**
     * The narrow slice of the facade's #226 ZOOM presentation state
     * that [applyPlaybackTweaksTo] needs. Implemented by the facade
     * as an anonymous object delegating to its private vars; the
     * config class never sees the aspect mode itself, only whether
     * ZOOM is the active mode.
     */
    interface ZoomState {
        /** True while the facade's current aspect mode is ZOOM. */
        val isZoomAspectActive: Boolean

        /**
         * The live mpv video-zoom (log2 scale) that was active when
         * ZOOM was entered, to restore when leaving ZOOM. Null while
         * ZOOM is not the active mode.
         */
        var zoomBeforeAspectZoom: Double?

        /**
         * The persisted settings.videoZoom value at the moment ZOOM
         * was entered — the baseline [applyPlaybackTweaksTo] compares
         * against to tell a user-driven zoom change apart from an
         * unrelated settings change while ZOOM is active. Null while
         * ZOOM is not the active mode.
         */
        var videoZoomAtAspectZoomEntry: Float?
    }

    companion object {
        private const val TAG = "MpvPlayerEngine"

        // Phase 4 step 4: the core variant — takes the core and the
        // zoom state explicitly so the core's init can apply the
        // saved settings before the facade's `config` property is
        // assigned (see the Host above). Kept as a companion function
        // for exactly that call: the instance [applyPlaybackTweaks]
        // below delegates to it.
        internal fun applyPlaybackTweaksTo(
            core: MpvCoreEngine,
            settings: AppSettings,
            zoomState: ZoomState,
        ) {
            if (!core.mpvInitialized || core.isReleased) return
            try {
                core.setRuntimeProperty("audio-delay", (settings.audioDelayMs / 1000.0).toString())
                if (zoomState.isZoomAspectActive) {
                    // Issue #226: ZOOM owns video-zoom (2x magnification, see
                    // setAspectRatio). An unrelated settings change — audio
                    // delay, subtitle style — must not silently undo it. Only
                    // a user-driven zoom change moves it: the sheet slider and
                    // the pinch-end save both change settings.videoZoom, and
                    // then the new value becomes both the live zoom and the
                    // restore point for leaving ZOOM.
                    if (settings.videoZoom != zoomState.videoZoomAtAspectZoomEntry) {
                        val zoom = mpvVideoZoom(settings.videoZoom)
                        core.setRuntimeProperty("video-zoom", zoom.toString())
                        zoomState.zoomBeforeAspectZoom = zoom
                        zoomState.videoZoomAtAspectZoomEntry = settings.videoZoom
                    }
                } else {
                    core.setRuntimeProperty("video-zoom", mpvVideoZoom(settings.videoZoom).toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed applying playback tweaks to mpv", e)
            }
        }

        /**
         * mpv's video-zoom is log2, not a plain multiplier (#172): 0 is
         * unscaled, 1 doubles the size, -1 halves it. MPx's videoZoom is a
         * plain multiplier (1.0 = no zoom), so convert with log2. Clamped to
         * the 0.5..2.0 UI range first, so a stray persisted value can never
         * produce NaN (log2 of 0) or an extreme zoom.
         *
         * Lives here as a companion (not in the facade) because it is the
         * settings-to-mpv conversion this class owns; the facade's
         * presentation functions ([MpvPlayerEngine.setVideoZoom],
         * [MpvPlayerEngine.setAspectRatio]) call it qualified
         * (`MpvEngineConfig.mpvVideoZoom(...)`) for the same conversion.
         * Pure logic, kept in the companion object so it is
         * unit-testable without mpv (see MpvEngineConfigTest).
         */
        internal fun mpvVideoZoom(mpxZoom: Float): Double =
            log2(mpxZoom.coerceIn(0.5f, 2.0f).toDouble())

        /**
         * mpv hwdec option value for a user decoder mode. Pure; safe
         * during the core's init (the core calls it before this class
         * exists). Unit-testable without mpv (see MpvEngineConfigTest).
         */
        internal fun hwdecValueFor(mode: DecoderMode): String = hwdecValueForStage(
            when (mode) {
                DecoderMode.HW -> MpvCoreEngine.DecoderStage.HW
                DecoderMode.HW_PLUS -> MpvCoreEngine.DecoderStage.HW_PLUS
                DecoderMode.SW -> MpvCoreEngine.DecoderStage.SW
            }
        )

        /**
         * mpv hwdec option value for a fallback stage. Pure; safe
         * during the core's init and the fallback chain. Unit-testable
         * without mpv (see MpvEngineConfigTest).
         */
        internal fun hwdecValueForStage(stage: MpvCoreEngine.DecoderStage): String = when (stage) {
            MpvCoreEngine.DecoderStage.HW -> "mediacodec"
            MpvCoreEngine.DecoderStage.HW_PLUS -> "mediacodec-copy"
            MpvCoreEngine.DecoderStage.SW -> "no"
        }
    }

    /**
     * Applies audio delay + video zoom (issue #153, Playback sheet) to a
     * running mpv instance. Both are runtime-changeable mpv properties, so
     * this is safe to call whenever settings change mid-playback — same
     * pattern as [MpvSubtitleRenderer.applySubtitleSettings]. No-op before
     * init or after release. audio-delay is in seconds (positive = audio
     * later). videoZoom is MPx's plain multiplier (1.0 = no zoom,
     * 0.5..2.0) — it is converted to mpv's log2 video-zoom
     * ([mpvVideoZoom]) because mpv reads the property as log2: 0 is
     * unscaled, 1 doubles the size. Sending the plain multiplier
     * straight through (#172) made every video play at 2x the fit size,
     * cropped and zoomed.
     */
    fun applyPlaybackTweaks(settings: AppSettings) {
        applyPlaybackTweaksTo(core, settings, zoomState)
    }

    /**
     * Applies a decoder mode to the running mpv instance: records the
     * mode + stage on the core and sets the mpv "hwdec" property. A
     * live hwdec property change only affects the NEXT decoder init —
     * the caller ([MpvPlayerEngine.setDecoderMode]) reloads the current
     * item so the new decoder takes effect. Called by the core only
     * from `load()`, never from its init, so the instance call is safe.
     */
    fun applyHwdecForMode(mode: DecoderMode) {
        core.decoderMode = mode
        core.currentDecoderStage = when (mode) {
            DecoderMode.HW -> MpvCoreEngine.DecoderStage.HW
            DecoderMode.HW_PLUS -> MpvCoreEngine.DecoderStage.HW_PLUS
            DecoderMode.SW -> MpvCoreEngine.DecoderStage.SW
        }
        val hwdecVal = hwdecValueFor(mode)
        try {
            core.port.setPropertyString("hwdec", hwdecVal)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set hwdec option to $hwdecVal", e)
        }
    }
}
