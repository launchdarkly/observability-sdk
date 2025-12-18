package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.sdk.LDContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

data class IdentifyItemPayload(
    val attributes: Map<String, String>,
    val timestamp: Long,
    val sessionId: String?
) {
    companion object {
        fun from(
            contextFriendlyName: String? = null,
            resourceAttributes: Attributes,
            ldContext: LDContext? = null,
            timestamp: Long = System.currentTimeMillis(),
            sessionId: String?
        ): IdentifyItemPayload {
            val attributes: MutableMap<String, String> = mutableMapOf()

            // Convert resource attributes to a flat string map, skipping array-like values
            resourceAttributes.asMap().forEach { (key: AttributeKey<*>, value: Any) ->
                when (value) {
                    is String -> attributes[key.key] = value
                    is Boolean, is Int, is Long, is Double, is Float -> attributes[key.key] = value.toString()
                    is Iterable<*> -> { /* skip arrays/collections */ }
                    is Array<*> -> { /* skip arrays */ }
                    is BooleanArray, is IntArray, is LongArray, is DoubleArray, is FloatArray -> { /* skip primitive arrays */ }
                    else -> { /* skip unknown types to mirror Swift compactMapValues behavior */ }
                }
            }

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