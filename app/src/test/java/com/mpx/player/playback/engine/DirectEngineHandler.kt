package com.mpx.player.playback.engine

/**
 * Test implementation of [EngineHandler] that executes posted runnables immediately,
 * while queueing delayed runnables until explicitly run or cancelled.
 */
internal class DirectEngineHandler : EngineHandler {
    private val delayedRunnables = mutableListOf<Pair<Runnable, Long>>()

    override fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    override fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        delayedRunnables.add(r to delayMillis)
        return true
    }

    override fun removeCallbacks(r: Runnable) {
        delayedRunnables.removeAll { it.first == r }
    }

    override fun removeCallbacksAndMessages(token: Any?) {
        delayedRunnables.clear()
    }

    fun runDelayedTasks() {
        val tasks = delayedRunnables.toList()
        delayedRunnables.clear()
        tasks.forEach { it.first.run() }
    }

    val pendingDelayedCount: Int
        get() = delayedRunnables.size
}
