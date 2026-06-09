package com.launchdarkly.observability.bridge

import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

/**
 * Converts an object payload (e.g. a `track` event's `data`) into OTel [Attributes].
 *
 * Only object payloads have key/value members; scalar/array payloads map to empty
 * attributes. Mirrors the web SDK: nested arrays/objects are skipped because OTel
 * [Attributes] is a flat scalar map.
 */
internal fun LDValue.toAttributes(): Attributes {
    if (type != LDValueType.OBJECT) return Attributes.empty()
    val builder = Attributes.builder()
    for (key in keys()) {
        val value = get(key)
        when (value.type) {
            LDValueType.BOOLEAN -> builder.put(AttributeKey.booleanKey(key), value.booleanValue())
            LDValueType.NUMBER -> builder.put(AttributeKey.doubleKey(key), value.doubleValue())
            LDValueType.STRING -> builder.put(AttributeKey.stringKey(key), value.stringValue())
            else -> {} // skip null/array/object
        }
    }
    return builder.build()
}

/**
 * Converts a plain JSON value (e.g. a `track` event's `data` carried across a
 * bridge as Java collections/primitives) into an [LDValue].
 *
 * The inverse of the value mapping used by [toAttributes], kept here so callers
 * that cannot depend on the LaunchDarkly model types (such as the Flutter
 * plugin) can forward a `Map` and let the bridge build the [LDValue].
 */
internal fun Any?.toLDValue(): LDValue = when (this) {
    null -> LDValue.ofNull()
    is Boolean -> LDValue.of(this)
    is Int -> LDValue.of(this)
    is Long -> LDValue.of(this)
    is Float -> LDValue.of(this)
    is Double -> LDValue.of(this)
    is String -> LDValue.of(this)
    is Map<*, *> -> {
        val builder = LDValue.buildObject()
        for ((key, value) in this) {
            if (key is String) builder.put(key, value.toLDValue())
        }
        builder.build()
    }
    is List<*> -> {
        val builder = LDValue.buildArray()
        for (element in this) builder.add(element.toLDValue())
        builder.build()
    }
    else -> LDValue.of(this.toString())
}
