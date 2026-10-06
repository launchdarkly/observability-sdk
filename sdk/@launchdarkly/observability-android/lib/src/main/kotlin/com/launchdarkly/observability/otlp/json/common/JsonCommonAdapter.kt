package com.launchdarkly.observability.otlp.json.common

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.KeyValue
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.common.ValueType
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Conversions from OpenTelemetry SDK types into the OTLP/JSON wire-format types declared in
 * [OtlpJsonCommonModels.kt]. Shared by every per-signal adapter (logs, traces, metrics).
 *
 * Mirrors the Swift `JsonCommonAdapter`.
 */
object JsonCommonAdapter {
    fun toJsonResource(resource: Resource): OtlpJsonResource {
        return OtlpJsonResource(attributes = toJsonAttributes(resource.attributes))
    }

    fun toJsonInstrumentationScope(scope: InstrumentationScopeInfo): OtlpJsonInstrumentationScope {
        val attrs = scope.attributes
        return OtlpJsonInstrumentationScope(
            name = scope.name,
            version = scope.version,
            attributes = if (attrs.isEmpty) null else toJsonAttributes(attrs),
        )
    }

    fun toJsonAttributes(attributes: Attributes): List<OtlpJsonKeyValue> {
        if (attributes.isEmpty) return emptyList()
        val result = ArrayList<OtlpJsonKeyValue>(attributes.size())
        attributes.forEach { key, value -> result.add(toJsonKeyValue(key, value)) }
        return result
    }

    fun toJsonKeyValue(key: AttributeKey<*>, value: Any): OtlpJsonKeyValue {
        return OtlpJsonKeyValue(key = key.key, value = toJsonAnyValue(key, value))
    }

    /**
     * Converts an attribute [value] (the object stored for an [AttributeKey] in an [Attributes]
     * map) into its OTLP/JSON representation, using [key]'s declared [AttributeType] to decide
     * the wire-format case.
     */
    fun toJsonAnyValue(key: AttributeKey<*>, value: Any): OtlpJsonAnyValue = when (key.type) {
        AttributeType.STRING -> OtlpJsonAnyValue.string(value as String)
        AttributeType.BOOLEAN -> OtlpJsonAnyValue.bool(value as Boolean)
        AttributeType.LONG -> OtlpJsonAnyValue.int((value as Number).toLong())
        AttributeType.DOUBLE -> OtlpJsonAnyValue.double((value as Number).toDouble())
        AttributeType.STRING_ARRAY -> OtlpJsonAnyValue.array(
            @Suppress("UNCHECKED_CAST")
            (value as List<String>).map { OtlpJsonAnyValue.string(it) }
        )
        AttributeType.BOOLEAN_ARRAY -> OtlpJsonAnyValue.array(
            @Suppress("UNCHECKED_CAST")
            (value as List<Boolean>).map { OtlpJsonAnyValue.bool(it) }
        )
        AttributeType.LONG_ARRAY -> OtlpJsonAnyValue.array(
            @Suppress("UNCHECKED_CAST")
            (value as List<Long>).map { OtlpJsonAnyValue.int(it) }
        )
        AttributeType.DOUBLE_ARRAY -> OtlpJsonAnyValue.array(
            @Suppress("UNCHECKED_CAST")
            (value as List<Double>).map { OtlpJsonAnyValue.double(it) }
        )
    }

    /**
     * Converts an OpenTelemetry [Value] (used for log record bodies and nested map/array
     * entries) into OTLP/JSON.
     */
    fun toJsonAnyValue(value: Value<*>): OtlpJsonAnyValue = when (value.type) {
        ValueType.STRING -> OtlpJsonAnyValue.string(value.value as String)
        ValueType.BOOLEAN -> OtlpJsonAnyValue.bool(value.value as Boolean)
        ValueType.LONG -> OtlpJsonAnyValue.int((value.value as Number).toLong())
        ValueType.DOUBLE -> OtlpJsonAnyValue.double((value.value as Number).toDouble())
        ValueType.ARRAY -> {
            @Suppress("UNCHECKED_CAST")
            val list = value.value as List<Value<*>>
            OtlpJsonAnyValue.array(list.map { toJsonAnyValue(it) })
        }
        ValueType.KEY_VALUE_LIST -> {
            @Suppress("UNCHECKED_CAST")
            val list = value.value as List<KeyValue>
            OtlpJsonAnyValue.kvlist(
                list.map { kv ->
                    OtlpJsonKeyValue(key = kv.key, value = toJsonAnyValue(kv.value))
                }
            )
        }
        ValueType.BYTES -> {
            val buffer = value.value as ByteBuffer
            val bytes = ByteArray(buffer.remaining())
            buffer.asReadOnlyBuffer().get(bytes)
            OtlpJsonAnyValue.bytes(Base64.getEncoder().encodeToString(bytes))
        }
    }
}
