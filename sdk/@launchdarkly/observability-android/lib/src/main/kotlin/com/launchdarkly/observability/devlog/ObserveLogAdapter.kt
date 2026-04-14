package com.launchdarkly.observability.devlog

/**
 * Logging adapter interface for the Observability SDK.
 *
 * Implement this interface to direct SDK log output to your preferred logging framework.
 * The default implementation ([LDObserveLogging]) writes to Android's native [android.util.Log].
 */
fun interface ObserveLogAdapter {

    /**
     * Creates a logging channel for the given tag/name.
     */
    fun newChannel(name: String): Channel

    /**
     * A logging channel that receives formatted log messages.
     */
    interface Channel {
        fun log(level: ObserveLogLevel, message: String)
    }
}

/**
 * Log levels used by [ObserveLogAdapter].
 */
enum class ObserveLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
