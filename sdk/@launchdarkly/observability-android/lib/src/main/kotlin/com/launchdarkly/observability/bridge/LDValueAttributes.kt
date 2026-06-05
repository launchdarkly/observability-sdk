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
