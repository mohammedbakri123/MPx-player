package com.mpx.player.playback.engine

/**
 * Test implementation of [EngineHandler] that executes posted runnables immediately.
 */
internal class DirectEngineHandler : EngineHandler {
    override fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    override fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        r.run()
        return true
    }

    override fun removeCallbacks(r: Runnable) {}
    override fun removeCallbacksAndMessages(token: Any?) {}
}
