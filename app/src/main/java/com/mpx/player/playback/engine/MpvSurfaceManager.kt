package com.mpx.player.playback.engine

import android.util.Log
import android.view.Surface

/**
 * Manages native surface attachment, surface sizing, surface loss detection,
 * and video output (VO) recovery (e.g. vo=null to vo=gpu) under libmpv (PI-2, Phase 8 Step 3.4).
 *
 * Encapsulates:
 * - [surfaceLock]: synchronization lock for all surface and VO state mutations.
 * - [currentSurface]: the currently attached Android [Surface], null when detached.
 * - [voIsNull]: tracking whether libmpv's VO is currently set or reported as "null".
 * - VO restore logic across attachSurface, onSurfaceChanged, time-pos, vo property updates,
 *   and load preparation.
 */
internal class MpvSurfaceManager(
    private val port: MpvPort,
) {
    companion object {
        private const val TAG = "MpvSurfaceManager"
    }

    internal val surfaceLock = Any()

    internal var currentSurface: Surface? = null
        private set

    /**
     * Whether libmpv's "vo" is currently "null" (issue #161).
     * Synchronized under [surfaceLock].
     */
    internal var voIsNull: Boolean = false

    internal val hasSurface: Boolean
        get() = synchronized(surfaceLock) { currentSurface != null }

    /**
     * Attaches [surface] to mpv and ensures VO is restored to "gpu".
     *
     * Reference-pattern alignment (mpv-android's BaseMPVView.surfaceCreated):
     * - When [willLoad] is true and [voIsNull] is false, skips setting "vo" because
     *   loadfile itself initialises VO against the attached surface.
     * - When [voIsNull] is true, restores "vo=gpu" even if [willLoad] is true.
     */
    fun attachSurface(surface: Surface, willLoad: Boolean) {
        synchronized(surfaceLock) {
            currentSurface = surface
            try {
                port.attachSurface(surface)
                try {
                    port.setPropertyString("force-window", "yes")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed setting force-window=yes on attach", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface", e)
            }

            if (voIsNull) {
                try {
                    port.setPropertyString("vo", "gpu")
                    voIsNull = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu on attach", e)
                }
            } else if (willLoad) {
                // A load is about to run: loadfile itself initialises
                // VO against the attached surface; do not touch "vo".
            } else {
                try {
                    port.setPropertyString("vo", "gpu")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed setting vo=gpu on attach", e)
                }
            }
        }
    }

    /**
     * SYNCHRONOUS detach barrier:
     * Sets vo=null, force-window=no, and detaches the surface before returning so that
     * libmpv terminates its native rendering window handle before surfaceDestroyed returns.
     */
    fun detachSurface() {
        synchronized(surfaceLock) {
            currentSurface = null
            voIsNull = true
            try {
                port.setPropertyString("vo", "null")
                port.setPropertyString("force-window", "no")
                port.detachSurface()
            } catch (e: Exception) {
                Log.w(TAG, "Error in detachSurface barrier", e)
            }
        }
    }

    /**
     * Reports surface dimensions to mpv via "android-surface-size" and restores "vo=gpu"
     * if VO was nulled while surface was alive.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        try {
            port.setPropertyString("android-surface-size", "${width}x${height}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed reporting android-surface-size", e)
        }
        restoreVoIfNull("surfaceChanged")
    }

    /**
     * Restores "vo=gpu" if a surface is attached and [voIsNull] is true.
     * Safe to call on time-pos, before loadfile, or during surface change.
     */
    fun restoreVoIfNull(reason: String = ""): Boolean {
        synchronized(surfaceLock) {
            if (currentSurface != null && voIsNull) {
                return try {
                    port.setPropertyString("vo", "gpu")
                    voIsNull = false
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed restoring vo=gpu on $reason", e)
                    false
                }
            }
        }
        return false
    }

    /**
     * Handles "vo" property changes reported by libmpv.
     * When mpv reports "null", sets [voIsNull] and attempts immediate restore if a surface is attached.
     * When mpv reports a live VO, clears [voIsNull].
     */
    fun onVoPropertyChanged() {
        synchronized(surfaceLock) {
            val vo = try {
                port.getPropertyString("vo")
            } catch (_: Exception) {
                null
            }
            if (vo == "null") {
                voIsNull = true
                if (currentSurface != null) {
                    try {
                        port.setPropertyString("vo", "gpu")
                        voIsNull = false
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed restoring vo=gpu on vo change", e)
                    }
                }
            } else if (vo != null) {
                voIsNull = false
            }
        }
    }
}
