package com.launchdarkly.observability.bridge

import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder

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
 * Converts a plain object payload (e.g. a `track` event's `data` carried across a
 * bridge as a `Map`) into OTel [Attributes].
 *
 * OTel [Attributes] is a flat map, so structure is preserved where it can be:
 *  - scalars become scalar attributes: integers (`Int`/`Long`) stay 64-bit longs,
 *    `Float`/`Double` become doubles, plus boolean and string. (Unlike the
 *    `LDClient.track` hook, which receives an `LDValue` whose only number type is
 *    `double`, these direct `LDObserve` APIs keep the caller's integer type.)
 *  - homogeneous scalar lists become array attributes (string / boolean / long /
 *    double arrays);
 *  - nested maps are flattened using dotted keys (e.g. `user.id`);
 *  - lists of objects (or mixed types) are flattened with indexed dotted keys
 *    (e.g. `products.0.product_id`, `products.1.price`);
 *  - an already-built [Attributes] value is merged in, its keys prefixed with the
 *    enclosing key (e.g. `payload.<inner-key>`), preserving the original value types;
 *  - any other value (null, heterogeneous/nested list, arbitrary object) is
 *    skipped — never stringified.
 *
 * Kept here so callers that cannot depend on the LaunchDarkly model types (such as
 * the Flutter plugin) can forward a `Map` without first building an [LDValue].
 */
internal fun Map<String, Any?>.toOtelAttributes(): Attributes {
    val builder = Attributes.builder()
    builder.putMap(prefix = null, source = this)
    return builder.build()
}

private fun AttributesBuilder.putMap(prefix: String?, source: Map<*, *>) {
    for ((rawKey, value) in source) {
        val name = rawKey?.toString() ?: continue
        putValue(if (prefix == null) name else "$prefix.$name", value)
    }
}

private fun AttributesBuilder.putValue(key: String, value: Any?) {
    when (value) {
        is Boolean -> put(AttributeKey.booleanKey(key), value)
        is Int -> put(AttributeKey.longKey(key), value.toLong())
        is Long -> put(AttributeKey.longKey(key), value)
        is Float -> put(AttributeKey.doubleKey(key), value.toDouble())
        is Double -> put(AttributeKey.doubleKey(key), value)
        is String -> put(AttributeKey.stringKey(key), value)
        is Attributes -> putAttributes(key, value)
        is Map<*, *> -> putMap(key, value)
        is List<*> -> putList(key, value)
        else -> {} // skip null / unknown; never stringify
    }
}

private fun AttributesBuilder.putAttributes(prefix: String, attributes: Attributes) {
    attributes.forEach { attrKey, value ->
        @Suppress("UNCHECKED_CAST")
        put(reKey(attrKey, "$prefix.${attrKey.key}") as AttributeKey<Any>, value)
    }
}

private fun AttributesBuilder.putList(key: String, list: List<*>) {
    if (list.isEmpty()) return
    when {
        list.all { it is String } ->
            put(AttributeKey.stringArrayKey(key), list.map { it as String })
        list.all { it is Boolean } ->
            put(AttributeKey.booleanArrayKey(key), list.map { it as Boolean })
        // All-integer lists keep 64-bit long precision; any Float/Double present
        // falls through to a double array.
        list.all { it is Int || it is Long } ->
            put(AttributeKey.longArrayKey(key), list.map { (it as Number).toLong() })
        list.all { it is Number } ->
            put(AttributeKey.doubleArrayKey(key), list.map { (it as Number).toDouble() })
        else -> list.forEachIndexed { index, element -> putValue("$key.$index", element) }
    }
}

private fun reKey(original: AttributeKey<*>, newName: String): AttributeKey<*> = when (original.type) {
    AttributeType.STRING -> AttributeKey.stringKey(newName)
    AttributeType.BOOLEAN -> AttributeKey.booleanKey(newName)
    AttributeType.LONG -> AttributeKey.longKey(newName)
    AttributeType.DOUBLE -> AttributeKey.doubleKey(newName)
    AttributeType.STRING_ARRAY -> AttributeKey.stringArrayKey(newName)
    AttributeType.BOOLEAN_ARRAY -> AttributeKey.booleanArrayKey(newName)
    AttributeType.LONG_ARRAY -> AttributeKey.longArrayKey(newName)
    AttributeType.DOUBLE_ARRAY -> AttributeKey.doubleArrayKey(newName)
}
