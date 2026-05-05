package com.launchdarkly.observability.otlp.json.metrics

import com.launchdarkly.observability.otlp.json.common.JsonCommonAdapter
import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.ExemplarData
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.LongExemplarData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.SumData
import io.opentelemetry.sdk.metrics.data.SummaryPointData
import io.opentelemetry.sdk.resources.Resource

/**
 * Adapter that converts [MetricData] instances into the OTLP/JSON wire-format types declared
 * in [OtlpJsonMetricModels.kt].
 *
 * Mirrors the Swift `JsonMetricsAdapter`.
 */
object JsonMetricsAdapter {
    fun toJsonRequest(metrics: Collection<MetricData>): OtlpJsonExportMetricsServiceRequest {
        return OtlpJsonExportMetricsServiceRequest(resourceMetrics = toResourceMetrics(metrics))
    }

    fun toResourceMetrics(metrics: Collection<MetricData>): List<OtlpJsonResourceMetrics> {
        if (metrics.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<Resource, LinkedHashMap<InstrumentationScopeInfo, MutableList<OtlpJsonMetric>>>()
        for (metric in metrics) {
            val json = toJsonMetric(metric) ?: continue
            val byScope = grouped.getOrPut(metric.resource) { LinkedHashMap() }
            val list = byScope.getOrPut(metric.instrumentationScopeInfo) { mutableListOf() }
            list.add(json)
        }

        return grouped.map { (resource, byScope) ->
            val scopeMetrics = byScope.map { (scopeInfo, metricList) ->
                OtlpJsonScopeMetrics(
                    scope = JsonCommonAdapter.toJsonInstrumentationScope(scopeInfo),
                    metrics = metricList,
                    schemaUrl = scopeInfo.schemaUrl,
                )
            }
            OtlpJsonResourceMetrics(
                resource = JsonCommonAdapter.toJsonResource(resource),
                scopeMetrics = scopeMetrics,
            )
        }
    }

    internal fun toJsonMetric(metric: MetricData): OtlpJsonMetric? {
        if (metric.data.points.isEmpty()) return null
        val data = toJsonMetricData(metric) ?: return null
        return OtlpJsonMetric(
            name = metric.name,
            description = metric.description.takeIf { it.isNotEmpty() },
            unit = metric.unit.takeIf { it.isNotEmpty() },
            data = data,
        )
    }

    private fun toJsonMetricData(metric: MetricData): OtlpJsonMetric.Data? {
        return when (metric.type) {
            MetricDataType.LONG_GAUGE -> {
                val points = metric.longGaugeData.points.map { toNumberPointFromLong(it) }
                OtlpJsonMetric.Data.Gauge(OtlpJsonGauge(dataPoints = points))
            }
            MetricDataType.DOUBLE_GAUGE -> {
                val points = metric.doubleGaugeData.points.map { toNumberPointFromDouble(it) }
                OtlpJsonMetric.Data.Gauge(OtlpJsonGauge(dataPoints = points))
            }
            MetricDataType.LONG_SUM -> {
                val sumData: SumData<LongPointData> = metric.longSumData
                val points = sumData.points.map { toNumberPointFromLong(it) }
                OtlpJsonMetric.Data.Sum(
                    OtlpJsonSum(
                        dataPoints = points,
                        aggregationTemporality = toJsonTemporality(sumData.aggregationTemporality),
                        isMonotonic = sumData.isMonotonic,
                    )
                )
            }
            MetricDataType.DOUBLE_SUM -> {
                val sumData: SumData<DoublePointData> = metric.doubleSumData
                val points = sumData.points.map { toNumberPointFromDouble(it) }
                OtlpJsonMetric.Data.Sum(
                    OtlpJsonSum(
                        dataPoints = points,
                        aggregationTemporality = toJsonTemporality(sumData.aggregationTemporality),
                        isMonotonic = sumData.isMonotonic,
                    )
                )
            }
            MetricDataType.HISTOGRAM -> {
                val histogram = metric.histogramData
                val points = histogram.points.map { toHistogramPoint(it) }
                OtlpJsonMetric.Data.Histogram(
                    OtlpJsonHistogram(
                        dataPoints = points,
                        aggregationTemporality = toJsonTemporality(histogram.aggregationTemporality),
                    )
                )
            }
            MetricDataType.EXPONENTIAL_HISTOGRAM -> {
                val histogram = metric.exponentialHistogramData
                val points = histogram.points.map { toExponentialHistogramPoint(it) }
                OtlpJsonMetric.Data.ExponentialHistogram(
                    OtlpJsonExponentialHistogram(
                        dataPoints = points,
                        aggregationTemporality = toJsonTemporality(histogram.aggregationTemporality),
                    )
                )
            }
            MetricDataType.SUMMARY -> {
                val points = metric.summaryData.points.map { toSummaryPoint(it) }
                OtlpJsonMetric.Data.Summary(OtlpJsonSummary(dataPoints = points))
            }
            null -> null
        }
    }

    private fun toNumberPointFromLong(point: LongPointData): OtlpJsonNumberDataPoint =
        OtlpJsonNumberDataPoint(
            attributes = jsonAttributes(point.attributes),
            startTimeUnixNano = JsonStringLong(point.startEpochNanos),
            timeUnixNano = JsonStringLong(point.epochNanos),
            value = OtlpJsonNumberValue.Int(point.value),
            exemplars = jsonExemplars(point.exemplars),
        )

    private fun toNumberPointFromDouble(point: DoublePointData): OtlpJsonNumberDataPoint =
        OtlpJsonNumberDataPoint(
            attributes = jsonAttributes(point.attributes),
            startTimeUnixNano = JsonStringLong(point.startEpochNanos),
            timeUnixNano = JsonStringLong(point.epochNanos),
            value = OtlpJsonNumberValue.Double(point.value),
            exemplars = jsonExemplars(point.exemplars),
        )

    private fun toHistogramPoint(point: HistogramPointData): OtlpJsonHistogramDataPoint =
        OtlpJsonHistogramDataPoint(
            attributes = jsonAttributes(point.attributes),
            startTimeUnixNano = JsonStringLong(point.startEpochNanos),
            timeUnixNano = JsonStringLong(point.epochNanos),
            count = JsonStringLong(point.count),
            sum = point.sum,
            bucketCounts = point.counts.map { JsonStringLong(it) },
            explicitBounds = point.boundaries,
            exemplars = jsonExemplars(point.exemplars),
            min = if (point.hasMin()) point.min else null,
            max = if (point.hasMax()) point.max else null,
        )

    private fun toExponentialHistogramPoint(
        point: ExponentialHistogramPointData,
    ): OtlpJsonExponentialHistogramDataPoint =
        OtlpJsonExponentialHistogramDataPoint(
            attributes = jsonAttributes(point.attributes),
            startTimeUnixNano = JsonStringLong(point.startEpochNanos),
            timeUnixNano = JsonStringLong(point.epochNanos),
            count = JsonStringLong(point.count),
            sum = point.sum,
            scale = point.scale,
            zeroCount = JsonStringLong(point.zeroCount),
            positive = toBuckets(point.positiveBuckets),
            negative = toBuckets(point.negativeBuckets),
            exemplars = jsonExemplars(point.exemplars),
            min = if (point.hasMin()) point.min else null,
            max = if (point.hasMax()) point.max else null,
        )

    private fun toSummaryPoint(point: SummaryPointData): OtlpJsonSummaryDataPoint {
        val quantiles = point.values.map {
            OtlpJsonSummaryDataPoint.ValueAtQuantile(quantile = it.quantile, value = it.value)
        }
        return OtlpJsonSummaryDataPoint(
            attributes = jsonAttributes(point.attributes),
            startTimeUnixNano = JsonStringLong(point.startEpochNanos),
            timeUnixNano = JsonStringLong(point.epochNanos),
            count = JsonStringLong(point.count),
            sum = point.sum,
            quantileValues = quantiles.takeIf { it.isNotEmpty() },
        )
    }

    private fun toBuckets(buckets: ExponentialHistogramBuckets): OtlpJsonExponentialHistogramDataPoint.Buckets =
        OtlpJsonExponentialHistogramDataPoint.Buckets(
            offset = buckets.offset,
            bucketCounts = buckets.bucketCounts.map { JsonStringLong(it) },
        )

    private fun jsonExemplars(exemplars: List<ExemplarData>?): List<OtlpJsonExemplar>? {
        if (exemplars.isNullOrEmpty()) return null
        return exemplars.map { toJsonExemplar(it) }
    }

    private fun toJsonExemplar(exemplar: ExemplarData): OtlpJsonExemplar {
        val value: OtlpJsonNumberValue = when (exemplar) {
            is DoubleExemplarData -> OtlpJsonNumberValue.Double(exemplar.value)
            is LongExemplarData -> OtlpJsonNumberValue.Int(exemplar.value)
            else -> OtlpJsonNumberValue.Double(0.0)
        }
        val ctx = exemplar.spanContext
        val traceId = ctx?.takeIf { it.isValid }?.traceId
        val spanId = ctx?.takeIf { it.isValid }?.spanId
        return OtlpJsonExemplar(
            filteredAttributes = jsonAttributes(exemplar.filteredAttributes),
            timeUnixNano = JsonStringLong(exemplar.epochNanos),
            value = value,
            traceId = traceId,
            spanId = spanId,
        )
    }

    internal fun toJsonTemporality(temporality: AggregationTemporality): OtlpJsonAggregationTemporality =
        when (temporality) {
            AggregationTemporality.DELTA -> OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_DELTA
            AggregationTemporality.CUMULATIVE ->
                OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE
        }

    private fun jsonAttributes(attributes: Attributes): List<OtlpJsonKeyValue>? {
        if (attributes.isEmpty) return null
        return JsonCommonAdapter.toJsonAttributes(attributes)
    }
}
