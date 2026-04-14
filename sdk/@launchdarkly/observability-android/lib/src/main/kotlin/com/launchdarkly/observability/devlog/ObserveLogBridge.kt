package com.launchdarkly.observability.devlog

import com.launchdarkly.logging.LDLogAdapter
import com.launchdarkly.logging.LDLogLevel
import com.launchdarkly.logging.LDLogger

/**
 * Bridges [ObserveLogAdapter] to [LDLogAdapter] so that internal code using [LDLogger] keeps
 * working while the public API is free of `com.launchdarkly.logging` types.
 */
internal fun ObserveLogAdapter.toLDLogAdapter(): LDLogAdapter {
    val outer = this
    return LDLogAdapter { name ->
        val channel = outer.newChannel(name)
        object : LDLogAdapter.Channel {
            override fun isEnabled(level: LDLogLevel): Boolean = true

            override fun log(level: LDLogLevel, message: Any?) {
                channel.log(level.toObserveLevel(), message?.toString() ?: "")
            }

            override fun log(level: LDLogLevel, format: String, param: Any?) {
                channel.log(level.toObserveLevel(), format(format, param))
            }

            override fun log(level: LDLogLevel, format: String, param1: Any?, param2: Any?) {
                channel.log(level.toObserveLevel(), format(format, param1, param2))
            }

            override fun log(level: LDLogLevel, format: String, vararg params: Any?) {
                channel.log(level.toObserveLevel(), format(format, *params))
            }
        }
    }
}

/**
 * Builds an [LDLogger] from an [ObserveLogAdapter], applying the appropriate level filter
 * based on the debug flag.
 */
internal fun buildLDLogger(
    adapter: ObserveLogAdapter,
    loggerName: String,
    debug: Boolean
): LDLogger {
    val ldAdapter = com.launchdarkly.logging.Logs.level(
        adapter.toLDLogAdapter(),
        if (debug) LDLogLevel.DEBUG else LDLogLevel.INFO
    )
    return LDLogger.withAdapter(ldAdapter, loggerName)
}

private fun LDLogLevel.toObserveLevel(): ObserveLogLevel = when (this) {
    LDLogLevel.DEBUG -> ObserveLogLevel.DEBUG
    LDLogLevel.INFO -> ObserveLogLevel.INFO
    LDLogLevel.WARN -> ObserveLogLevel.WARN
    LDLogLevel.ERROR -> ObserveLogLevel.ERROR
    else -> ObserveLogLevel.DEBUG
}

/**
 * Simple `{}` placeholder formatting, equivalent to `SimpleFormat.format` from
 * `com.launchdarkly.logging`.
 */
private fun format(template: String, vararg args: Any?): String {
    val sb = StringBuilder(template.length + args.size * 16)
    var argIdx = 0
    var i = 0
    while (i < template.length) {
        if (i + 1 < template.length && template[i] == '{' && template[i + 1] == '}' && argIdx < args.size) {
            sb.append(args[argIdx])
            argIdx++
            i += 2
        } else {
            sb.append(template[i])
            i++
        }
    }
    return sb.toString()
}
