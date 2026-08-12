package com.launchdarkly.observability.otlp.json.metrics

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * OTLP/JSON wire-format types for the metrics signal.
 *
 * Mirrors the Swift `OtlpJsonMetricModels.swift`.
 */

@Serializable
data class OtlpJsonExportMetricsServiceRequest(
    val resourceMetrics: List<OtlpJsonResourceMetrics>,
)

@Serializable
data class OtlpJsonResourceMetrics(
    val resource: OtlpJsonResource? = null,
    val scopeMetrics: List<OtlpJsonScopeMetrics>,
    val schemaUrl: String? = null,
)

@Serializable
data class OtlpJsonScopeMetrics(
    val scope: OtlpJsonInstrumentationScope? = null,
    val metrics: List<OtlpJsonMetric>,
    val schemaUrl: String? = null,
)

/**
 * Mirrors `opentelemetry.proto.metrics.v1.Metric`. The proto `data` field is a `oneof`, so we
 * encode exactly one of `gauge` / `sum` / `histogram` / `exponentialHistogram` / `summary`.
 */
@Serializable(with = OtlpJsonMetricSerializer::class)
data class OtlpJsonMetric(
    val name: String,
    val description: String? = null,
    val unit: String? = null,
    val data: Data,
) {
    sealed class Data {
        data class Gauge(val value: OtlpJsonGauge) : Data()
        data class Sum(val value: OtlpJsonSum) : Data()
        data class Histogram(val value: OtlpJsonHistogram) : Data()
        data class ExponentialHistogram(val value: OtlpJsonExponentialHistogram) : Data()
        data class Summary(val value: OtlpJsonSummary) : Data()
    }
}

object OtlpJsonMetricSerializer : KSerializer<OtlpJsonMetric> {
    override val descriptor: SerialDescriptor = OtlpJsonMetricSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OtlpJsonMetric) {
        val surrogate = OtlpJsonMetricSurrogate(
            name = value.name,
            description = value.description,
            unit = value.unit,
            gauge = (value.data as? OtlpJsonMetric.Data.Gauge)?.value,
            sum = (value.data as? OtlpJsonMetric.Data.Sum)?.value,
            histogram = (value.data as? OtlpJsonMetric.Data.Histogram)?.value,
            exponentialHistogram = (value.data as? OtlpJsonMetric.Data.ExponentialHistogram)?.value,
            summary = (value.data as? OtlpJsonMetric.Data.Summary)?.value,
        )
        encoder.encodeSerializableValue(OtlpJsonMetricSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OtlpJsonMetric {
        val surrogate = decoder.decodeSerializableValue(OtlpJsonMetricSurrogate.serializer())
        val data: OtlpJsonMetric.Data = when {
            surrogate.gauge != null -> OtlpJsonMetric.Data.Gauge(surrogate.gauge)
            surrogate.sum != null -> OtlpJsonMetric.Data.Sum(surrogate.sum)
            surrogate.histogram != null -> OtlpJsonMetric.Data.Histogram(surrogate.histogram)
            surrogate.exponentialHistogram != null ->
                OtlpJsonMetric.Data.ExponentialHistogram(surrogate.exponentialHistogram)
            surrogate.summary != null -> OtlpJsonMetric.Data.Summary(surrogate.summary)
            else -> OtlpJsonMetric.Data.Gauge(OtlpJsonGauge(dataPoints = emptyList()))
        }
        return OtlpJsonMetric(
            name = surrogate.name,
            description = surrogate.description,
            unit = surrogate.unit,
            data = data,
        )
    }
}

@Serializable
internal data class OtlpJsonMetricSurrogate(
    val name: String,
    val description: String? = null,
    val unit: String? = null,
    val gauge: OtlpJsonGauge? = null,
    val sum: OtlpJsonSum? = null,
    val histogram: OtlpJsonHistogram? = null,
    val exponentialHistogram: OtlpJsonExponentialHistogram? = null,
    val summary: OtlpJsonSummary? = null,
)

@Serializable
data class OtlpJsonGauge(
    val dataPoints: List<OtlpJsonNumberDataPoint>,
)

@Serializable
data class OtlpJsonSum(
    val dataPoints: List<OtlpJsonNumberDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
    val isMonotonic: Boolean,
)

@Serializable
data class OtlpJsonHistogram(
    val dataPoints: List<OtlpJsonHistogramDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
)

@Serializable
data class OtlpJsonExponentialHistogram(
    val dataPoints: List<OtlpJsonExponentialHistogramDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
)

@Serializable
data class OtlpJsonSummary(
    val dataPoints: List<OtlpJsonSummaryDataPoint>,
)

@Serializable(with = OtlpJsonNumberDataPointSerializer::class)
data class OtlpJsonNumberDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val value: OtlpJsonNumberValue,
    val exemplars: List<OtlpJsonExemplar>? = null,
    val flags: Int? = null,
)

object OtlpJsonNumberDataPointSerializer : KSerializer<OtlpJsonNumberDataPoint> {
    override val descriptor: SerialDescriptor = OtlpJsonNumberDataPointSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OtlpJsonNumberDataPoint) {
        val asInt = (value.value as? OtlpJsonNumberValue.Int)?.value?.let { JsonStringLong(it) }
        val asDouble = (value.value as? OtlpJsonNumberValue.Double)?.value
        val surrogate = OtlpJsonNumberDataPointSurrogate(
            attributes = value.attributes,
            startTimeUnixNano = value.startTimeUnixNano,
            timeUnixNano = value.timeUnixNano,
            asInt = asInt,
            asDouble = asDouble,
            exemplars = value.exemplars,
            flags = value.flags,
        )
        encoder.encodeSerializableValue(OtlpJsonNumberDataPointSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OtlpJsonNumberDataPoint {
        val s = decoder.decodeSerializableValue(OtlpJsonNumberDataPointSurrogate.serializer())
        val value: OtlpJsonNumberValue = when {
            s.asInt != null -> OtlpJsonNumberValue.Int(s.asInt.value)
            s.asDouble != null -> OtlpJsonNumberValue.Double(s.asDouble)
            else -> OtlpJsonNumberValue.Double(0.0)
        }
        return OtlpJsonNumberDataPoint(
            attributes = s.attributes,
            startTimeUnixNano = s.startTimeUnixNano,
            timeUnixNano = s.timeUnixNano,
            value = value,
            exemplars = s.exemplars,
            flags = s.flags,
        )
    }
}

@Serializable
internal data class OtlpJsonNumberDataPointSurrogate(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val asInt: JsonStringLong? = null,
    val asDouble: Double? = null,
    val exemplars: List<OtlpJsonExemplar>? = null,
    val flags: Int? = null,
)

@Serializable
data class OtlpJsonHistogramDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    /** uint64 encoded as a JSON string per proto3 mapping. */
    val count: JsonStringLong,
    val sum: Double? = null,
    /** Each entry is uint64 encoded as a JSON string. */
    val bucketCounts: List<JsonStringLong>? = null,
    val explicitBounds: List<Double>? = null,
    val exemplars: List<OtlpJsonExemplar>? = null,
    val flags: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
)

@Serializable
data class OtlpJsonExponentialHistogramDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val count: JsonStringLong,
    val sum: Double? = null,
    val scale: Int,
    val zeroCount: JsonStringLong? = null,
    val positive: Buckets? = null,
    val negative: Buckets? = null,
    val flags: Int? = null,
    val exemplars: List<OtlpJsonExemplar>? = null,
    val min: Double? = null,
    val max: Double? = null,
) {
    @Serializable
    data class Buckets(
        val offset: Int,
        val bucketCounts: List<JsonStringLong>,
    )
}

@Serializable
data class OtlpJsonSummaryDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val count: JsonStringLong,
    val sum: Double,
    val quantileValues: List<ValueAtQuantile>? = null,
    val flags: Int? = null,
) {
    @Serializable
    data class ValueAtQuantile(
        val quantile: Double,
        val value: Double,
    )
}

@Serializable(with = OtlpJsonExemplarSerializer::class)
data class OtlpJsonExemplar(
    val filteredAttributes: List<OtlpJsonKeyValue>? = null,
    val timeUnixNano: JsonStringLong,
    val value: OtlpJsonNumberValue,
    /** Lowercase hex string (32 chars), per OTLP/JSON spec deviation. */
    val traceId: String? = null,
    /** Lowercase hex string (16 chars), per OTLP/JSON spec deviation. */
    val spanId: String? = null,
)

object OtlpJsonExemplarSerializer : KSerializer<OtlpJsonExemplar> {
    override val descriptor: SerialDescriptor = OtlpJsonExemplarSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OtlpJsonExemplar) {
        val asInt = (value.value as? OtlpJsonNumberValue.Int)?.value?.let { JsonStringLong(it) }
        val asDouble = (value.value as? OtlpJsonNumberValue.Double)?.value
        val surrogate = OtlpJsonExemplarSurrogate(
            filteredAttributes = value.filteredAttributes,
            timeUnixNano = value.timeUnixNano,
            asInt = asInt,
            asDouble = asDouble,
            traceId = value.traceId,
            spanId = value.spanId,
        )
        encoder.encodeSerializableValue(OtlpJsonExemplarSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OtlpJsonExemplar {
        val s = decoder.decodeSerializableValue(OtlpJsonExemplarSurrogate.serializer())
        val value: OtlpJsonNumberValue = when {
            s.asInt != null -> OtlpJsonNumberValue.Int(s.asInt.value)
            s.asDouble != null -> OtlpJsonNumberValue.Double(s.asDouble)
            else -> OtlpJsonNumberValue.Double(0.0)
        }
        return OtlpJsonExemplar(
            filteredAttributes = s.filteredAttributes,
            timeUnixNano = s.timeUnixNano,
            value = value,
            traceId = s.traceId,
            spanId = s.spanId,
        )
    }
}

@Serializable
internal data class OtlpJsonExemplarSurrogate(
    val filteredAttributes: List<OtlpJsonKeyValue>? = null,
    val timeUnixNano: JsonStringLong,
    val asInt: JsonStringLong? = null,
    val asDouble: Double? = null,
    val traceId: String? = null,
    val spanId: String? = null,
)

/** Mirrors the `oneof` numeric value used by both `NumberDataPoint` and `Exemplar`. */
sealed class OtlpJsonNumberValue {
    data class Int(@SerialName("asInt") val value: Long) : OtlpJsonNumberValue()
    data class Double(@SerialName("asDouble") val value: kotlin.Double) : OtlpJsonNumberValue()
}

/** Encoded as the proto-JSON enum string form. */
@Serializable
enum class OtlpJsonAggregationTemporality {
    AGGREGATION_TEMPORALITY_UNSPECIFIED,
    AGGREGATION_TEMPORALITY_DELTA,
    AGGREGATION_TEMPORALITY_CUMULATIVE,
}
