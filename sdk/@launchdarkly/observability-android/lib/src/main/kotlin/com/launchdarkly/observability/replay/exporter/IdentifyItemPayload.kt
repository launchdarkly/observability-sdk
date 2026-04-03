package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload
import com.launchdarkly.sdk.LDContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

data class IdentifyItemPayload(
    val attributes: Map<String, String>,
    override val timestamp: Long,
    val sessionId: String?
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    /**
     * Queue cost heuristic: each attribute adds a fixed 100 cost units.
     */
    override fun cost(): Int = attributes.size * 100

    companion object {
        private fun flattenAttributes(source: Attributes, into: MutableMap<String, String>) {
            source.asMap().forEach { (key: AttributeKey<*>, value: Any) ->
                when (value) {
                    is String -> into[key.key] = value
                    is Boolean, is Int, is Long, is Double, is Float -> into[key.key] = value.toString()
                    is Iterable<*> -> { /* skip arrays/collections */ }
                    is Array<*> -> { /* skip arrays */ }
                    is BooleanArray, is IntArray, is LongArray, is DoubleArray, is FloatArray -> { /* skip primitive arrays */ }
                    else -> { /* skip unknown types to mirror Swift compactMapValues behavior */ }
                }
            }
        }

        fun from(
            contextFriendlyName: String? = null,
            resourceAttributes: Attributes,
            ldContext: LDContext? = null,
            timestamp: Long = System.currentTimeMillis(),
            sessionId: String?
        ): IdentifyItemPayload {
            val attributes: MutableMap<String, String> = mutableMapOf()

            flattenAttributes(resourceAttributes, attributes)

            // Merge LDContext kind->key entries into attributes
            if (ldContext != null) {
                if (ldContext.isMultiple) {
                    val count = ldContext.individualContextCount
                    for (i in 0 until count) {
                        val sub = ldContext.getIndividualContext(i)
                        val kind = sub.kind.toString()
                        val key = sub.key
                        if (!key.isNullOrEmpty()) {
                            attributes[kind] = key
                        }
                    }
                } else {
                    val kind = ldContext.kind.toString()
                    val key = ldContext.key
                    if (!key.isNullOrEmpty()) {
                        attributes[kind] = key
                    }
                }
            }

            val canonicalKey = ldContext?.fullyQualifiedKey ?: "unknown"
            attributes["key"] = contextFriendlyName ?: canonicalKey
            attributes["canonicalKey"] = canonicalKey

            return IdentifyItemPayload(
                attributes = attributes,
                timestamp = timestamp,
                sessionId = sessionId
            )
        }
    }
}
