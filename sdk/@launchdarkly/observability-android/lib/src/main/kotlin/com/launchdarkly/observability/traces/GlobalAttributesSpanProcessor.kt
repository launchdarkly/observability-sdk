package com.launchdarkly.observability.traces

import com.launchdarkly.observability.client.GlobalAttributesStore
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Stamps the global attributes current at span start onto every span.
 *
 * Unlike the upstream `GlobalAttributesSpanAppender`, globals only fill keys the span does not
 * already carry, so attributes supplied when the span was built always win.
 */
internal class GlobalAttributesSpanProcessor(
    private val globals: () -> Attributes = GlobalAttributesStore::current,
) : SpanProcessor {

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        val current = globals()
        if (current.isEmpty) return

        val present = span.attributes.asMap().keys.mapTo(HashSet()) { it.key }
        for ((key, value) in current.asMap()) {
            if (key.key in present) continue
            @Suppress("UNCHECKED_CAST")
            span.setAttribute(key as AttributeKey<Any>, value)
        }
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false
}
