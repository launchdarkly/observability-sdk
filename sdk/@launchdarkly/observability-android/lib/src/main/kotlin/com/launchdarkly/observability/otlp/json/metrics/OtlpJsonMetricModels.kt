package com.launchdarkly.observability.otlp.json.metrics

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource

/**
 * OTLP/JSON wire-format types for the metrics signal.
 *
 * Mirrors the Swift `OtlpJsonMetricModels.swift`.
 */

data class OtlpJsonExportMetricsServiceRequest(
    val resourceMetrics: List<OtlpJsonResourceMetrics>,
)

data class OtlpJsonResourceMetrics(
    val resource: OtlpJsonResource? = null,
    val scopeMetrics: List<OtlpJsonScopeMetrics>,
    val schemaUrl: String? = null,
)

data class OtlpJsonScopeMetrics(
    val scope: OtlpJsonInstrumentationScope? = null,
    val metrics: List<OtlpJsonMetric>,
    val schemaUrl: String? = null,
)

/**
 * Mirrors `opentelemetry.proto.metrics.v1.Metric`. The proto `data` field is a `oneof`, so we
 * encode exactly one of `gauge` / `sum` / `histogram` / `exponentialHistogram` / `summary`.
 */
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

data class OtlpJsonGauge(
    val dataPoints: List<OtlpJsonNumberDataPoint>,
)

data class OtlpJsonSum(
    val dataPoints: List<OtlpJsonNumberDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
    val isMonotonic: Boolean,
)

data class OtlpJsonHistogram(
    val dataPoints: List<OtlpJsonHistogramDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
)

data class OtlpJsonExponentialHistogram(
    val dataPoints: List<OtlpJsonExponentialHistogramDataPoint>,
    val aggregationTemporality: OtlpJsonAggregationTemporality,
)

data class OtlpJsonSummary(
    val dataPoints: List<OtlpJsonSummaryDataPoint>,
)

data class OtlpJsonNumberDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val value: OtlpJsonNumberValue,
    val exemplars: List<OtlpJsonExemplar>? = null,
    val flags: Int? = null,
)

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
    data class Buckets(
        val offset: Int,
        val bucketCounts: List<JsonStringLong>,
    )
}

data class OtlpJsonSummaryDataPoint(
    val attributes: List<OtlpJsonKeyValue>? = null,
    val startTimeUnixNano: JsonStringLong,
    val timeUnixNano: JsonStringLong,
    val count: JsonStringLong,
    val sum: Double,
    val quantileValues: List<ValueAtQuantile>? = null,
    val flags: Int? = null,
) {
    data class ValueAtQuantile(
        val quantile: Double,
        val value: Double,
    )
}

data class OtlpJsonExemplar(
    val filteredAttributes: List<OtlpJsonKeyValue>? = null,
    val timeUnixNano: JsonStringLong,
    val value: OtlpJsonNumberValue,
    /** Lowercase hex string (32 chars), per OTLP/JSON spec deviation. */
    val traceId: String? = null,
    /** Lowercase hex string (16 chars), per OTLP/JSON spec deviation. */
    val spanId: String? = null,
)

/** Mirrors the `oneof` numeric value used by both `NumberDataPoint` and `Exemplar`. */
sealed class OtlpJsonNumberValue {
    data class Int(val value: Long) : OtlpJsonNumberValue()
    data class Double(val value: kotlin.Double) : OtlpJsonNumberValue()
}

/** Encoded as the proto-JSON enum string form. */
enum class OtlpJsonAggregationTemporality {
    AGGREGATION_TEMPORALITY_UNSPECIFIED,
    AGGREGATION_TEMPORALITY_DELTA,
    AGGREGATION_TEMPORALITY_CUMULATIVE,
}
