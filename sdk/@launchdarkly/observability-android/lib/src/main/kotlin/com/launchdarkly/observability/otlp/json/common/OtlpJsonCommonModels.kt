package com.launchdarkly.observability.otlp.json.common

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
data class JsonStringLong(val value: Long)

data class OtlpJsonResource(
    val attributes: List<OtlpJsonKeyValue> = emptyList(),
    val droppedAttributesCount: Int? = null,
)

data class OtlpJsonInstrumentationScope(
    val name: String,
    val version: String? = null,
    val attributes: List<OtlpJsonKeyValue>? = null,
    val droppedAttributesCount: Int? = null,
)

data class OtlpJsonKeyValue(
    val key: String,
    val value: OtlpJsonAnyValue,
)

/**
 * Mirrors `opentelemetry.proto.common.v1.AnyValue`. Exactly one associated value is encoded
 * per instance, in the proto3-JSON shape
 * `{"stringValue": "..."} / {"intValue": "42"} / ...`.
 */
sealed class OtlpJsonAnyValue {
    data class StringVal(val value: String) : OtlpJsonAnyValue()

    data class BoolVal(val value: Boolean) : OtlpJsonAnyValue()

    data class IntVal(val value: JsonStringLong) : OtlpJsonAnyValue()

    data class DoubleVal(val value: Double) : OtlpJsonAnyValue()

    data class ArrayVal(val value: ArrayWrapper) : OtlpJsonAnyValue()

    data class KvListVal(val value: KvListWrapper) : OtlpJsonAnyValue()

    data class BytesVal(val value: String) : OtlpJsonAnyValue()

    data class ArrayWrapper(val values: List<OtlpJsonAnyValue> = emptyList())

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
