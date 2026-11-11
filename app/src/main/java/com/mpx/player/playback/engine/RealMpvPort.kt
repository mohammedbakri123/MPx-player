package com.mpx.player.playback.engine

import android.content.Context
import android.view.Surface
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode

/**
 * Production 1:1 delegate to [is.xyz.mpv.MPV] (finding R-6).
 */
internal class RealMpvPort(
    internal val mpv: MPV = MPV()
) : MpvPort {

    private val eventObservers = mutableMapOf<MpvPort.EventObserver, MPV.EventObserver>()
    private val logObservers = mutableMapOf<MpvPort.LogObserver, MPV.LogObserver>()

    override fun create(context: Context) {
        mpv.create(context)
    }

    override fun init() {
        mpv.init()
    }

    override fun destroy() {
        mpv.destroy()
    }

    override fun attachSurface(surface: Surface) {
        mpv.attachSurface(surface)
    }

    override fun detachSurface() {
        mpv.detachSurface()
    }

    override fun setOptionString(name: String, value: String): Int {
        return mpv.setOptionString(name, value)
    }

    override fun observeProperty(property: String, format: MpvPort.PropertyFormat) {
        val mpvFormat = when (format) {
            MpvPort.PropertyFormat.NONE -> MPV.mpvFormat.MPV_FORMAT_NONE
            MpvPort.PropertyFormat.STRING -> MPV.mpvFormat.MPV_FORMAT_STRING
            MpvPort.PropertyFormat.FLAG -> MPV.mpvFormat.MPV_FORMAT_FLAG
            MpvPort.PropertyFormat.INT64 -> MPV.mpvFormat.MPV_FORMAT_INT64
            MpvPort.PropertyFormat.DOUBLE -> MPV.mpvFormat.MPV_FORMAT_DOUBLE
        }
        mpv.observeProperty(property, mpvFormat)
    }

    override fun addObserver(observer: MpvPort.EventObserver) {
        val wrapper = object : MPV.EventObserver {
            override fun eventProperty(property: String) {
                observer.eventProperty(property)
            }

            override fun eventProperty(property: String, value: Long) {
                observer.eventProperty(property)
            }

            override fun eventProperty(property: String, value: Boolean) {
                observer.eventProperty(property)
            }

            override fun eventProperty(property: String, value: String) {
                observer.eventProperty(property)
            }

            override fun eventProperty(property: String, value: Double) {
                observer.eventProperty(property)
            }

            override fun eventProperty(property: String, value: MPVNode) {
                observer.eventProperty(property)
            }

            override fun event(eventId: Int, data: MPVNode) {
                val endFile = if (eventId == MPV.mpvEvent.MPV_EVENT_END_FILE) {
                    val reason = try { data.get("reason")?.asString() } catch (_: Exception) { null }
                    val error = try { data.get("error")?.asInt() } catch (_: Exception) { null }
                    MpvPort.EndFile(reason, error)
                } else null
                observer.event(eventId, endFile)
            }
        }
        eventObservers[observer] = wrapper
        mpv.addObserver(wrapper)
    }

    override fun removeObserver(observer: MpvPort.EventObserver) {
        val wrapper = eventObservers.remove(observer)
        if (wrapper != null) {
            mpv.removeObserver(wrapper)
        }
    }

    override fun addLogObserver(observer: MpvPort.LogObserver) {
        val wrapper = object : MPV.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) {
                observer.logMessage(prefix, level, text)
            }
        }
        logObservers[observer] = wrapper
        mpv.addLogObserver(wrapper)
    }

    override fun removeLogObserver(observer: MpvPort.LogObserver) {
        val wrapper = logObservers.remove(observer)
        if (wrapper != null) {
            mpv.removeLogObserver(wrapper)
        }
    }

    override fun command(vararg args: String) {
        mpv.command(*args)
    }

    override fun getPropertyString(name: String): String? {
        return mpv.getPropertyString(name)
    }

    override fun getPropertyBoolean(name: String): Boolean? {
        return mpv.getPropertyBoolean(name)
    }

    override fun getPropertyDouble(name: String): Double? {
        return mpv.getPropertyDouble(name)
    }

    override fun getPropertyInt(name: String): Int? {
        return mpv.getPropertyInt(name)
    }

    override fun setPropertyString(name: String, value: String) {
        mpv.setPropertyString(name, value)
    }

    override fun setPropertyBoolean(name: String, value: Boolean) {
        mpv.setPropertyBoolean(name, value)
    }

    override fun setPropertyDouble(name: String, value: Double) {
        mpv.setPropertyDouble(name, value)
    }

    override fun setPropertyInt(name: String, value: Int) {
        mpv.setPropertyInt(name, value)
    }
}
