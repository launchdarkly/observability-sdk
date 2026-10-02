package com.launchdarkly.observability.bridge

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder

/**
 * Converts untyped `Map<String, Any?>` dictionaries (from bridge layers like .NET MAUI)
 * into OTel [io.opentelemetry.api.common.Attributes].
 *
 * OTel Java [io.opentelemetry.api.common.Attributes] is a flat key-value structure, so nested maps are flattened
 * with dot-separated keys (e.g. `"nested.child"` for `{"nested": {"child": ...}}`).
 */
object AttributeConverter {

    /**
     * Converts a `Map<String, Any?>` into OTel [io.opentelemetry.api.common.Attributes].
     * Nested maps are flattened with dot-separated keys.
     */
    fun convert(source: Map<String, Any?>?): Attributes {
        if (source.isNullOrEmpty()) return Attributes.empty()
        val builder = Attributes.builder()
        flattenInto(builder, "", source)
        return builder.build()
    }

    internal fun flattenInto(builder: AttributesBuilder, prefix: String, source: Map<String, Any?>) {
        source.forEach { (key, value) ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            putValue(builder, fullKey, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun putValue(builder: AttributesBuilder, key: String, value: Any?) {
        when (value) {
            is String -> builder.put(AttributeKey.stringKey(key), value)
            is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
            is Long -> builder.put(AttributeKey.longKey(key), value)
            is Int -> builder.put(AttributeKey.longKey(key), value.toLong())
            is Double -> builder.put(AttributeKey.doubleKey(key), value)
            is Float -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            is Map<*, *> -> flattenInto(builder, key, value as Map<String, Any?>)
            is List<*> -> putList(builder, key, value)
            null -> {}
            else -> builder.put(AttributeKey.stringKey(key), value.toString())
        }
    }

    internal fun putList(builder: AttributesBuilder, key: String, list: List<*>) {
        if (list.isEmpty()) return
        val first = list.firstOrNull { it != null } ?: return
        when (first) {
            is String -> builder.put(AttributeKey.stringArrayKey(key), list.filterIsInstance<String>())
            is Boolean -> builder.put(AttributeKey.booleanArrayKey(key), list.filterIsInstance<Boolean>())
            is Long -> builder.put(AttributeKey.longArrayKey(key), list.filterIsInstance<Long>())
            is Int -> builder.put(AttributeKey.longArrayKey(key), list.filterIsInstance<Int>().map { it.toLong() })
            is Double -> builder.put(AttributeKey.doubleArrayKey(key), list.filterIsInstance<Double>())
            is Float -> builder.put(AttributeKey.doubleArrayKey(key), list.filterIsInstance<Float>().map { it.toDouble() })
            else -> builder.put(AttributeKey.stringArrayKey(key), list.map { it?.toString() ?: "" })
        }
    }
}