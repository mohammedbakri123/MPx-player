package com.mpx.player.playback.engine

import android.content.Context
import android.view.Surface

/**
 * Test double for [MpvPort] (finding R-6).
 *
 * Records commands, set options, and properties without throwing native exceptions.
 * Allows observers to be invoked synchronously or queued for deterministic event-ordering tests.
 */
internal class FakeMpvPort(
    var createException: Exception? = null,
    var initException: Exception? = null,
    var optionReturnCode: Int = 0,
) : MpvPort {

    var isCreated: Boolean = false
    var isInitialized: Boolean = false
    var isDestroyed: Boolean = false
    var attachedSurface: Surface? = null

    val recordedCommands = mutableListOf<List<String>>()
    val recordedOptions = mutableListOf<Pair<String, String>>()
    val recordedProperties = mutableMapOf<String, Any>()
    val propertyHistory = mutableListOf<Pair<String, Any>>()
    val observedProperties = mutableMapOf<String, MpvPort.PropertyFormat>()

    val eventObservers = mutableListOf<MpvPort.EventObserver>()
    val logObservers = mutableListOf<MpvPort.LogObserver>()

    var queueEvents: Boolean = false
    val queuedActions = mutableListOf<() -> Unit>()

    override fun create(context: Context) {
        createException?.let { throw it }
        isCreated = true
    }

    override fun init() {
        initException?.let { throw it }
        isInitialized = true
    }

    override fun destroy() {
        isDestroyed = true
    }

    override fun attachSurface(surface: Surface) {
        attachedSurface = surface
    }

    override fun detachSurface() {
        attachedSurface = null
    }

    override fun setOptionString(name: String, value: String): Int {
        recordedOptions.add(name to value)
        return optionReturnCode
    }

    override fun observeProperty(property: String, format: MpvPort.PropertyFormat) {
        observedProperties[property] = format
    }

    override fun addObserver(observer: MpvPort.EventObserver) {
        eventObservers.add(observer)
    }

    override fun removeObserver(observer: MpvPort.EventObserver) {
        eventObservers.remove(observer)
    }

    override fun addLogObserver(observer: MpvPort.LogObserver) {
        logObservers.add(observer)
    }

    override fun removeLogObserver(observer: MpvPort.LogObserver) {
        logObservers.remove(observer)
    }

    override fun command(vararg args: String) {
        recordedCommands.add(args.toList())
    }

    override fun getPropertyString(name: String): String? = recordedProperties[name] as? String
    override fun getPropertyBoolean(name: String): Boolean? = recordedProperties[name] as? Boolean
    override fun getPropertyDouble(name: String): Double? = (recordedProperties[name] as? Number)?.toDouble()
    override fun getPropertyInt(name: String): Int? = (recordedProperties[name] as? Number)?.toInt()

    override fun setPropertyString(name: String, value: String) {
        recordedProperties[name] = value
        propertyHistory.add(name to value)
    }

    override fun setPropertyBoolean(name: String, value: Boolean) {
        recordedProperties[name] = value
        propertyHistory.add(name to value)
    }

    override fun setPropertyDouble(name: String, value: Double) {
        recordedProperties[name] = value
        propertyHistory.add(name to value)
    }

    override fun setPropertyInt(name: String, value: Int) {
        recordedProperties[name] = value
        propertyHistory.add(name to value)
    }

    fun emitProperty(property: String, value: Any? = null) {
        if (value != null) {
            recordedProperties[property] = value
        }
        val action = {
            eventObservers.toList().forEach { it.eventProperty(property) }
        }
        if (queueEvents) {
            queuedActions.add(action)
        } else {
            action()
        }
    }

    fun emitEvent(eventId: Int, endFile: MpvPort.EndFile? = null) {
        val action = {
            eventObservers.toList().forEach { it.event(eventId, endFile) }
        }
        if (queueEvents) {
            queuedActions.add(action)
        } else {
            action()
        }
    }

    fun emitLog(prefix: String, level: Int, text: String) {
        val action = {
            logObservers.toList().forEach { it.logMessage(prefix, level, text) }
        }
        if (queueEvents) {
            queuedActions.add(action)
        } else {
            action()
        }
    }

    fun drainQueuedEvents() {
        val actions = queuedActions.toList()
        queuedActions.clear()
        actions.forEach { it() }
    }
}
