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
 * Converts a plain object payload (e.g. a `track` event's `data` carried across
 * a bridge as a `Map`) directly into OTel [Attributes].
 *
 * Mirrors [toAttributes] for [LDValue]: only scalar members contribute (boolean,
 * number -> double, string); null/array/object members are skipped because OTel
 * [Attributes] is a flat scalar map. Kept here so callers that cannot depend on
 * the LaunchDarkly model types (such as the Flutter plugin) can forward a `Map`
 * without first building an [LDValue].
 */
internal fun Map<String, Any?>.toAttributes(): Attributes {
    val builder = Attributes.builder()
    for ((key, value) in this) {
        when (value) {
            is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
            is Int -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            is Long -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            is Float -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            is Double -> builder.put(AttributeKey.doubleKey(key), value)
            is String -> builder.put(AttributeKey.stringKey(key), value)
            else -> {} // skip null/array/object
        }
    }
    return builder.build()
}
