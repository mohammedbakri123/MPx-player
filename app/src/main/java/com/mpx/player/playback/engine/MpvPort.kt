package com.mpx.player.playback.engine

import android.content.Context
import android.view.Surface

/**
 * Seam isolating the native mpv library ([is.xyz.mpv.MPV]) from the engine
 * and playback codebase (finding R-6).
 *
 * In production, [RealMpvPort] delegates 1:1 to [is.xyz.mpv.MPV].
 * In tests, [FakeMpvPort] records commands, options, and properties, and
 * allows deterministic event emission and ordering verification.
 */
internal interface MpvPort {

    fun create(context: Context)
    fun init()
    fun destroy()

    fun attachSurface(surface: Surface)
    fun detachSurface()

    fun setOptionString(name: String, value: String): Int

    fun observeProperty(property: String, format: PropertyFormat)
    fun addObserver(observer: EventObserver)
    fun removeObserver(observer: EventObserver)
    fun addLogObserver(observer: LogObserver)
    fun removeLogObserver(observer: LogObserver)

    fun command(vararg args: String)

    fun getPropertyString(name: String): String?
    fun getPropertyBoolean(name: String): Boolean?
    fun getPropertyDouble(name: String): Double?
    fun getPropertyInt(name: String): Int?

    fun setPropertyString(name: String, value: String)
    fun setPropertyBoolean(name: String, value: Boolean)
    fun setPropertyDouble(name: String, value: Double)
    fun setPropertyInt(name: String, value: Int)

    enum class PropertyFormat {
        NONE,
        STRING,
        FLAG,
        INT64,
        DOUBLE,
    }

    /**
     * Carries the only two fields ever read from MPVNode on END_FILE
     * (reason and error code). Decouples the engine from native MPVNode.
     */
    data class EndFile(
        val reason: String?,
        val error: Long?,
    )

    fun interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    interface EventObserver {
        fun eventProperty(property: String)
        fun event(eventId: Int, endFile: EndFile?)
    }

    companion object {
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_START_FILE = 6
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_VIDEO_RECONFIG = 17
    }
}
