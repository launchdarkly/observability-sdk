package com.launchdarkly.observability.otlp.json.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * OTLP/JSON wire-format types shared by every signal (logs, traces, metrics).
 *
 * These follow the canonical Protobuf-to-JSON mapping
 * (https://protobuf.dev/programming-guides/json/) with the OTLP-specific deviations called
 * out in the OpenTelemetry specification:
 *
 *  - 64-bit integers (e.g. `timeUnixNano`, `intValue`) are serialized as JSON strings of
 *    decimal digits (see [JsonStringLong]).
 *  - `traceId` / `spanId` are serialized as lowercase hexadecimal strings (32 / 16 hex
 *    chars), NOT base64. Each signal handles that locally.
 *
 * Field names use the standard proto-JSON `lowerCamelCase` form so any compliant OTLP/HTTP
 * receiver can decode the payload.
 */

/**
 * 64-bit integer that serializes as a JSON string, as required by the proto3 JSON mapping
 * for `int64` / `uint64` / `fixed64` fields.
 */
@Serializable(with = JsonStringLongSerializer::class)
data class JsonStringLong(val value: Long)

object JsonStringLongSerializer : KSerializer<JsonStringLong> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonStringLong", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonStringLong) {
        encoder.encodeString(value.value.toString())
    }

    override fun deserialize(decoder: Decoder): JsonStringLong =
        JsonStringLong(decoder.decodeString().toLong())
}

@Serializable
data class OtlpJsonResource(
    val attributes: List<OtlpJsonKeyValue> = emptyList(),
    val droppedAttributesCount: Int? = null,
)

@Serializable
data class OtlpJsonInstrumentationScope(
    val name: String,
    val version: String? = null,
    val attributes: List<OtlpJsonKeyValue>? = null,
    val droppedAttributesCount: Int? = null,
)

@Serializable
data class OtlpJsonKeyValue(
    val key: String,
    val value: OtlpJsonAnyValue,
)

/**
 * Mirrors `opentelemetry.proto.common.v1.AnyValue`. Exactly one associated value is encoded
 * per instance. The serializer emits the proto3-JSON shape
 * `{"stringValue": "..."} / {"intValue": "42"} / ...`.
 */
@Serializable(with = OtlpJsonAnyValueSerializer::class)
sealed class OtlpJsonAnyValue {
    @Serializable
    data class StringVal(@SerialName("stringValue") val value: String) : OtlpJsonAnyValue()

    @Serializable
    data class BoolVal(@SerialName("boolValue") val value: Boolean) : OtlpJsonAnyValue()

    @Serializable
    data class IntVal(@SerialName("intValue") val value: JsonStringLong) : OtlpJsonAnyValue()

    @Serializable
    data class DoubleVal(@SerialName("doubleValue") val value: Double) : OtlpJsonAnyValue()

    @Serializable
    data class ArrayVal(@SerialName("arrayValue") val value: ArrayWrapper) : OtlpJsonAnyValue()

    @Serializable
    data class KvListVal(@SerialName("kvlistValue") val value: KvListWrapper) : OtlpJsonAnyValue()

    @Serializable
    data class BytesVal(@SerialName("bytesValue") val value: String) : OtlpJsonAnyValue()

    @Serializable
    data class ArrayWrapper(val values: List<OtlpJsonAnyValue> = emptyList())

    @Serializable
    data class KvListWrapper(val values: List<OtlpJsonKeyValue> = emptyList())

    companion object {
        fun string(value: String): OtlpJsonAnyValue = StringVal(value)
        fun bool(value: Boolean): OtlpJsonAnyValue = BoolVal(value)
        fun int(value: Long): OtlpJsonAnyValue = IntVal(JsonStringLong(value))
        fun double(value: Double): OtlpJsonAnyValue = DoubleVal(value)
        fun array(values: List<OtlpJsonAnyValue>): OtlpJsonAnyValue = ArrayVal(ArrayWrapper(values))
        fun kvlist(values: List<OtlpJsonKeyValue>): OtlpJsonAnyValue = KvListVal(KvListWrapper(values))
        fun bytes(base64: String): OtlpJsonAnyValue = BytesVal(base64)
    }
}

object OtlpJsonAnyValueSerializer : KSerializer<OtlpJsonAnyValue> {
    override val descriptor: SerialDescriptor = OtlpJsonAnyValueSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OtlpJsonAnyValue) {
        val surrogate = when (value) {
            is OtlpJsonAnyValue.StringVal -> OtlpJsonAnyValueSurrogate(stringValue = value.value)
            is OtlpJsonAnyValue.BoolVal -> OtlpJsonAnyValueSurrogate(boolValue = value.value)
            is OtlpJsonAnyValue.IntVal -> OtlpJsonAnyValueSurrogate(intValue = value.value)
            is OtlpJsonAnyValue.DoubleVal -> OtlpJsonAnyValueSurrogate(doubleValue = value.value)
            is OtlpJsonAnyValue.ArrayVal -> OtlpJsonAnyValueSurrogate(arrayValue = value.value)
            is OtlpJsonAnyValue.KvListVal -> OtlpJsonAnyValueSurrogate(kvlistValue = value.value)
            is OtlpJsonAnyValue.BytesVal -> OtlpJsonAnyValueSurrogate(bytesValue = value.value)
        }
        encoder.encodeSerializableValue(OtlpJsonAnyValueSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OtlpJsonAnyValue {
        val surrogate = decoder.decodeSerializableValue(OtlpJsonAnyValueSurrogate.serializer())
        return when {
            surrogate.stringValue != null -> OtlpJsonAnyValue.StringVal(surrogate.stringValue)
            surrogate.boolValue != null -> OtlpJsonAnyValue.BoolVal(surrogate.boolValue)
            surrogate.intValue != null -> OtlpJsonAnyValue.IntVal(surrogate.intValue)
            surrogate.doubleValue != null -> OtlpJsonAnyValue.DoubleVal(surrogate.doubleValue)
            surrogate.arrayValue != null -> OtlpJsonAnyValue.ArrayVal(surrogate.arrayValue)
            surrogate.kvlistValue != null -> OtlpJsonAnyValue.KvListVal(surrogate.kvlistValue)
            surrogate.bytesValue != null -> OtlpJsonAnyValue.BytesVal(surrogate.bytesValue)
            else -> OtlpJsonAnyValue.StringVal("")
        }
    }
}

@Serializable
internal data class OtlpJsonAnyValueSurrogate(
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val intValue: JsonStringLong? = null,
    val doubleValue: Double? = null,
    val arrayValue: OtlpJsonAnyValue.ArrayWrapper? = null,
    val kvlistValue: OtlpJsonAnyValue.KvListWrapper? = null,
    val bytesValue: String? = null,
)
