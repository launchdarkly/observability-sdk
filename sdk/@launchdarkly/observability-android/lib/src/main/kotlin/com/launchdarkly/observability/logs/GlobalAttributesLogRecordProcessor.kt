package com.launchdarkly.observability.logs

import com.launchdarkly.observability.client.GlobalAttributesStore
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * Stamps the current global attributes onto every log record.
 *
 * Unlike the upstream `GlobalAttributesLogRecordAppender`, globals only fill keys the record does
 * not already carry, so attributes supplied with the log always win.
 */
internal class GlobalAttributesLogRecordProcessor(
    private val globals: () -> Attributes = GlobalAttributesStore::current,
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        val current = globals()
        if (current.isEmpty) return

        val present = logRecord.attributes.asMap().keys.mapTo(HashSet()) { it.key }
        for ((key, value) in current.asMap()) {
            if (key.key in present) continue
            @Suppress("UNCHECKED_CAST")
            logRecord.setAttribute(key as AttributeKey<Any>, value)
        }
    }
}
