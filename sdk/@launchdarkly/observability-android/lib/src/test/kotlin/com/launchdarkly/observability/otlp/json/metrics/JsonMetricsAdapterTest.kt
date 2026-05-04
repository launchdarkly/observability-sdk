package com.launchdarkly.observability.otlp.json.metrics

import com.launchdarkly.observability.otlp.json.JsonTestHelpers.array
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.boolean
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.double
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.encodeToTree
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.obj
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.string
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongExemplarData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.metrics.TestMetricData
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonMetricsAdapterTest {

    private val resource = Resource.create(
        Attributes.of(AttributeKey.stringKey("service.name"), "test-service")
    )
    private val scope: InstrumentationScopeInfo =
        InstrumentationScopeInfo.builder("test-scope").setVersion("1.2.3").build()

    private val startNanos = 1_700_000_000_000_000_000L
    private val endNanos = 1_700_000_001_000_000_000L

    @Test
    fun `encodes LongSum as a sum container with asInt and AGGREGATION_TEMPORALITY_DELTA`() {
        val point = ImmutableLongPointData.create(
            startNanos,
            endNanos,
            Attributes.of(AttributeKey.stringKey("host"), "h1"),
            42L,
        )
        val metric = TestMetricData.builder()
            .setResource(resource)
            .setInstrumentationScopeInfo(scope)
            .setName("requests.total")
            .setDescription("Total request count")
            .setUnit("1")
            .setLongSumData(
                ImmutableSumData.create(
                    /* isMonotonic = */ true,
                    AggregationTemporality.DELTA,
                    listOf<LongPointData>(point),
                )
            )
            .build()

        val metricJson = drillToMetric(metric)
        assertEquals("requests.total", string(metricJson["name"]))
        assertEquals("Total request count", string(metricJson["description"]))
        assertEquals("1", string(metricJson["unit"]))

        val sum = obj(metricJson["sum"])
        assertEquals("AGGREGATION_TEMPORALITY_DELTA", string(sum["aggregationTemporality"]))
        assertEquals(true, boolean(sum["isMonotonic"]))

        val dataPoint = obj(array(sum["dataPoints"])[0])
        assertEquals("1700000000000000000", string(dataPoint["startTimeUnixNano"]))
        assertEquals("1700000001000000000", string(dataPoint["timeUnixNano"]))
        assertEquals("42", string(dataPoint["asInt"]))

        val attribute = obj(array(dataPoint["attributes"])[0])
        assertEquals("host", string(attribute["key"]))
    }

    @Test
    fun `encodes DoubleGauge as a gauge container with asDouble`() {
        val point = ImmutableDoublePointData.create(
            startNanos,
            endNanos,
            Attributes.empty(),
            12.5,
        )
        val metric = TestMetricData.builder()
            .setResource(resource)
            .setInstrumentationScopeInfo(scope)
            .setName("cpu.usage")
            .setDescription("")
            .setUnit("")
            .setDoubleGaugeData(ImmutableGaugeData.create(listOf(point)))
            .build()

        val metricJson = drillToMetric(metric)
        assertEquals("cpu.usage", string(metricJson["name"]))
        assertNull(metricJson["description"])
        assertNull(metricJson["unit"])

        val gauge = obj(metricJson["gauge"])
        val dataPoint = obj(array(gauge["dataPoints"])[0])
        assertEquals(12.5, double(dataPoint["asDouble"]))
        assertNull(dataPoint["asInt"])
    }

    @Test
    fun `encodes Histogram with string bucketCounts and explicit bounds`() {
        val point = ImmutableHistogramPointData.create(
            startNanos,
            endNanos,
            Attributes.empty(),
            /* sum = */ 27.0,
            /* hasMin = */ true,
            /* min = */ 1.0,
            /* hasMax = */ true,
            /* max = */ 10.0,
            /* boundaries = */ listOf(5.0),
            /* counts = */ listOf(2L, 1L),
        )
        val metric = TestMetricData.builder()
            .setResource(resource)
            .setInstrumentationScopeInfo(scope)
            .setName("request.duration")
            .setDescription("")
            .setUnit("ms")
            .setHistogramData(
                ImmutableHistogramData.create(
                    AggregationTemporality.CUMULATIVE,
                    listOf(point),
                )
            )
            .build()

        val metricJson = drillToMetric(metric)

        val histogram = obj(metricJson["histogram"])
        assertEquals("AGGREGATION_TEMPORALITY_CUMULATIVE", string(histogram["aggregationTemporality"]))

        val dataPoint = obj(array(histogram["dataPoints"])[0])
        assertEquals("3", string(dataPoint["count"]))

        val bucketCounts = array(dataPoint["bucketCounts"]).map { string(it) }
        assertEquals(listOf("2", "1"), bucketCounts)

        val bounds = array(dataPoint["explicitBounds"]).map { double(it) }
        assertEquals(listOf(5.0), bounds)

        assertEquals(27.0, double(dataPoint["sum"]))
        assertEquals(1.0, double(dataPoint["min"]))
        assertEquals(10.0, double(dataPoint["max"]))
    }

    @Test
    fun `encodes exemplar traceId and spanId as lowercase hex`() {
        val traceIdHex = "0102030405060708090a0b0c0d0e0f10"
        val spanIdHex = "1112131415161718"
        val ctx = SpanContext.create(traceIdHex, spanIdHex, TraceFlags.getDefault(), TraceState.getDefault())
        val exemplar = ImmutableLongExemplarData.create(
            Attributes.empty(),
            endNanos,
            ctx,
            7L,
        )
        val point = ImmutableLongPointData.create(
            startNanos,
            endNanos,
            Attributes.empty(),
            7L,
            listOf(exemplar),
        )
        val metric = TestMetricData.builder()
            .setResource(resource)
            .setInstrumentationScopeInfo(scope)
            .setName("x")
            .setDescription("")
            .setUnit("")
            .setLongGaugeData(ImmutableGaugeData.create(listOf(point)))
            .build()

        val metricJson = drillToMetric(metric)
        val gauge = obj(metricJson["gauge"])
        val dataPoint = obj(array(gauge["dataPoints"])[0])
        val exemplarJson = obj(array(dataPoint["exemplars"])[0])

        assertEquals(traceIdHex, string(exemplarJson["traceId"]))
        assertEquals(spanIdHex, string(exemplarJson["spanId"]))
        assertEquals("7", string(exemplarJson["asInt"]))
        assertEquals(endNanos.toString(), string(exemplarJson["timeUnixNano"]))
    }

    @Test
    fun `drops metrics with no data points`() {
        val metric = TestMetricData.builder()
            .setResource(resource)
            .setInstrumentationScopeInfo(scope)
            .setName("empty")
            .setDescription("")
            .setUnit("")
            .setLongSumData(
                ImmutableSumData.create(
                    /* isMonotonic = */ false,
                    AggregationTemporality.CUMULATIVE,
                    emptyList<LongPointData>(),
                )
            )
            .build()

        val request = JsonMetricsAdapter.toJsonRequest(listOf(metric))
        assertTrue(request.resourceMetrics.isEmpty())
    }

    @Test
    fun `maps AggregationTemporality cases to proto-JSON enum strings`() {
        assertEquals(
            OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            JsonMetricsAdapter.toJsonTemporality(AggregationTemporality.DELTA),
        )
        assertEquals(
            OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
            JsonMetricsAdapter.toJsonTemporality(AggregationTemporality.CUMULATIVE),
        )
    }

    private fun drillToMetric(metric: io.opentelemetry.sdk.metrics.data.MetricData): JsonObject {
        val request = JsonMetricsAdapter.toJsonRequest(listOf(metric))
        val tree = encodeToTree(request, OtlpJsonExportMetricsServiceRequest.serializer())

        val resourceMetric = obj(array(tree["resourceMetrics"])[0])
        val scopeMetric = obj(array(resourceMetric["scopeMetrics"])[0])
        return obj(array(scopeMetric["metrics"])[0])
    }
}
