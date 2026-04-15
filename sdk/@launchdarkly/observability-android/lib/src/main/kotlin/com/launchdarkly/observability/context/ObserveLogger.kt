package com.launchdarkly.observability.context

/**
 * Internal logger used throughout the Observability SDK.
 *
 * Wraps an [ObserveLogAdapter.Channel] with level filtering and convenience
 * methods that mirror common logging APIs.
 */
class ObserveLogger internal constructor(
    private val channel: ObserveLogAdapter.Channel,
    private val minLevel: ObserveLogLevel,
) {
    fun debug(message: String) {
        if (minLevel <= ObserveLogLevel.DEBUG) channel.log(ObserveLogLevel.DEBUG, message)
    }

    fun info(message: String) {
        if (minLevel <= ObserveLogLevel.INFO) channel.log(ObserveLogLevel.INFO, message)
    }

    fun warn(message: String) {
        if (minLevel <= ObserveLogLevel.WARN) channel.log(ObserveLogLevel.WARN, message)
    }

    fun warn(message: String, t: Throwable) {
        if (minLevel <= ObserveLogLevel.WARN) channel.log(ObserveLogLevel.WARN, "$message: ${t.message}")
    }

    fun error(message: String) {
        if (minLevel <= ObserveLogLevel.ERROR) channel.log(ObserveLogLevel.ERROR, message)
    }

    fun error(message: String, t: Throwable) {
        if (minLevel <= ObserveLogLevel.ERROR) channel.log(ObserveLogLevel.ERROR, "$message: ${t.message}")
    }

    fun error(t: Throwable) {
        if (minLevel <= ObserveLogLevel.ERROR) channel.log(ObserveLogLevel.ERROR, t.message ?: t.toString())
    }

    companion object {
        fun build(adapter: ObserveLogAdapter, name: String, debug: Boolean): ObserveLogger {
            val level = if (debug) ObserveLogLevel.DEBUG else ObserveLogLevel.INFO
            return ObserveLogger(adapter.newChannel(name), level)
        }
    }
}
