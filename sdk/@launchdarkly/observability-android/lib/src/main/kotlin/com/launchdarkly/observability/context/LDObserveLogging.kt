package com.launchdarkly.observability.context

import android.util.Log

/**
 * Default [ObserveLogAdapter] that writes to Android's native [Log] API.
 *
 * ```kotlin
 * val options = ObservabilityOptions(
 *     logAdapter = LDObserveLogging.adapter(),
 * )
 * ```
 */
object LDObserveLogging {

    /**
     * Returns an [ObserveLogAdapter] backed by Android's [Log].
     */
    @JvmStatic
    fun adapter(): ObserveLogAdapter = ObserveLogAdapter { name -> ChannelImpl(name) }

    private class ChannelImpl(private val tag: String) : ObserveLogAdapter.Channel {
        override fun log(level: ObserveLogLevel, message: String) {
            when (level) {
                ObserveLogLevel.DEBUG -> Log.d(tag, message)
                ObserveLogLevel.INFO -> Log.i(tag, message)
                ObserveLogLevel.WARN -> Log.w(tag, message)
                ObserveLogLevel.ERROR -> Log.e(tag, message)
            }
        }
    }
}
