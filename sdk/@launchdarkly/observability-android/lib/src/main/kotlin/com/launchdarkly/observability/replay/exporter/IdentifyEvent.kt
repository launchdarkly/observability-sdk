package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.sdk.LDContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

data class IdentifyItemPayload(
    val attributes: Map<String, String>,
    val timestamp: Long
) {
    companion object {
        fun from(
            contextFriendlyName: String? = null,
            resourceAttributes: Attributes,
            ldContext: LDContext? = null,
            timestamp: Long = System.currentTimeMillis(),
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

            val canonicalKey = ldContext?.fullyQualifiedKey ?: "unknown"

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

            // Determine friendly name preference:
            // 1) Provided contextFriendlyName if non-blank
            // 2) If multi-context, prefer "user" key when present and non-blank
            val chosenFriendlyName: String? = when {
                !contextFriendlyName.isNullOrBlank() -> contextFriendlyName
                ldContext?.isMultiple == true -> {
                    var userKey: String? = null
                    val count = ldContext.individualContextCount
                    for (i in 0 until count) {
                        val sub = ldContext.getIndividualContext(i)
                        if (sub.kind.toString() == "user" && !sub.key.isNullOrEmpty()) {
                            userKey = sub.key
                            break
                        }
                    }
                    userKey
                }
                else -> null
            }

            attributes["key"] = chosenFriendlyName ?: canonicalKey
            attributes["canonicalKey"] = canonicalKey

            return IdentifyItemPayload(
                attributes = attributes,
                timestamp = timestamp
            )
        }
    }
}