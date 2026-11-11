package com.mpx.player.playback.engine

import android.os.Handler
import android.os.Looper

/**
 * Seam abstracting [android.os.Handler] main-thread posting for [MpvCoreEngine].
 *
 * In production, [RealEngineHandler] delegates to `Handler(Looper.getMainLooper())`.
 * In unit tests, a direct/synchronous handler executes posts immediately on the
 * calling thread without needing Android framework looper infrastructure.
 */
internal interface EngineHandler {
    fun post(r: Runnable): Boolean
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean
    fun removeCallbacks(r: Runnable)
    fun removeCallbacksAndMessages(token: Any?)
}

internal class RealEngineHandler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : EngineHandler {
    override fun post(r: Runnable): Boolean = handler.post(r)
    override fun postDelayed(r: Runnable, delayMillis: Long): Boolean = handler.postDelayed(r, delayMillis)
    override fun removeCallbacks(r: Runnable) = handler.removeCallbacks(r)
    override fun removeCallbacksAndMessages(token: Any?) = handler.removeCallbacksAndMessages(token)
}
