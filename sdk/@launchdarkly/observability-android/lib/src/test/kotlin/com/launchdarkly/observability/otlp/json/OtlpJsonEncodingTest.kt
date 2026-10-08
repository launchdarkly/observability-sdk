package com.launchdarkly.observability.otlp.json

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonAnyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonExportLogsServiceRequest
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonLogRecord
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonResourceLogs
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonScopeLogs
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonAggregationTemporality
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExemplar
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExponentialHistogram
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExponentialHistogramDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExportMetricsServiceRequest
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonGauge
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonHistogram
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonHistogramDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonMetric
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonNumberDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonNumberValue
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonResourceMetrics
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonScopeMetrics
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonSum
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonSummary
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonSummaryDataPoint
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonExportTraceServiceRequest
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonResourceSpans
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonScopeSpans
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonSpan
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonSpanKind
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonStatus
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Exact OTLP/JSON output: field order, left-out optional fields, and the proto3 JSON spellings. */
class OtlpJsonEncodingTest {
    private val ts = JsonStringLong(1_700_000_000_000_000_000)

    private fun logs(record: OtlpJsonLogRecord, resource: OtlpJsonResource? = null, scope: OtlpJsonInstrumentationScope? = null): String =
        String(
            OtlpJsonExportLogsServiceRequest(listOf(OtlpJsonResourceLogs(resource, listOf(OtlpJsonScopeLogs(scope, listOf(record)))))).toJsonBytes(),
            Charsets.UTF_8
        )

    private fun metric(metric: OtlpJsonMetric): String {
        val json = String(
            OtlpJsonExportMetricsServiceRequest(listOf(OtlpJsonResourceMetrics(scopeMetrics = listOf(OtlpJsonScopeMetrics(metrics = listOf(metric)))))).toJsonBytes(),
            Charsets.UTF_8
        )
        return json.removePrefix("""{"resourceMetrics":[{"scopeMetrics":[{"metrics":[""").removeSuffix("]}]}]}")
    }

    @Test
    fun `a log record with nothing set is an empty object`() {
        assertEquals("""{"resourceLogs":[{"scopeLogs":[{"logRecords":[{}]}]}]}""", logs(OtlpJsonLogRecord()))
    }

    @Test
    fun `a full log record keeps proto field order and writes 64-bit integers as strings`() {
        val record = OtlpJsonLogRecord(
            timeUnixNano = ts,
            observedTimeUnixNano = JsonStringLong(2),
            severityNumber = 9,
            severityText = "INFO",
            body = OtlpJsonAnyValue.string("hello"),
            attributes = emptyList(),
            droppedAttributesCount = 1,
            flags = 1,
            traceId = "t",
            spanId = "s",
            eventName = "e",
        )
        assertEquals(
            """{"resourceLogs":[{"scopeLogs":[{"logRecords":[{"timeUnixNano":"1700000000000000000","observedTimeUnixNano":"2",""" +
                """"severityNumber":9,"severityText":"INFO","body":{"stringValue":"hello"},"attributes":[],"droppedAttributesCount":1,""" +
                """"flags":1,"traceId":"t","spanId":"s","eventName":"e"}]}]}]}""",
            logs(record)
        )
    }

    @Test
    fun `a resource leaves out an empty attribute list, a scope keeps one`() {
        val json = logs(
            OtlpJsonLogRecord(),
            resource = OtlpJsonResource(),
            scope = OtlpJsonInstrumentationScope(name = "n", attributes = emptyList()),
        )
        assertEquals(
            """{"resourceLogs":[{"resource":{},"scopeLogs":[{"scope":{"name":"n","attributes":[]},"logRecords":[{}]}]}]}""",
            json
        )
    }

    @Test
    fun `each AnyValue is one member named for its variant`() {
        val values = listOf(
            OtlpJsonAnyValue.string("s"),
            OtlpJsonAnyValue.bool(false),
            OtlpJsonAnyValue.int(-42),
            OtlpJsonAnyValue.double(1.5),
            OtlpJsonAnyValue.array(listOf(OtlpJsonAnyValue.int(1))),
            OtlpJsonAnyValue.array(emptyList()),
            OtlpJsonAnyValue.kvlist(listOf(OtlpJsonKeyValue("k", OtlpJsonAnyValue.bool(true)))),
            OtlpJsonAnyValue.kvlist(emptyList()),
            OtlpJsonAnyValue.bytes("AAE="),
        )
        val bodies = values.map { logs(OtlpJsonLogRecord(body = it)).substringAfter("\"body\":").removeSuffix("}]}]}]}") }
        assertEquals(
            listOf(
                """{"stringValue":"s"}""",
                """{"boolValue":false}""",
                """{"intValue":"-42"}""",
                """{"doubleValue":1.5}""",
                """{"arrayValue":{"values":[{"intValue":"1"}]}}""",
                """{"arrayValue":{}}""",
                """{"kvlistValue":{"values":[{"key":"k","value":{"boolValue":true}}]}}""",
                """{"kvlistValue":{}}""",
                """{"bytesValue":"AAE="}""",
            ),
            bodies
        )
    }

    @Test
    fun `a span writes enums by name and leaves out what is unset`() {
        val span = OtlpJsonSpan(
            traceId = "t",
            spanId = "s",
            name = "n",
            kind = OtlpJsonSpanKind.SPAN_KIND_CLIENT,
            startTimeUnixNano = ts,
            endTimeUnixNano = JsonStringLong(0),
            events = listOf(OtlpJsonSpan.Event(timeUnixNano = JsonStringLong(1), name = "e")),
            links = listOf(OtlpJsonSpan.Link(traceId = "lt", spanId = "ls", flags = 1)),
            status = OtlpJsonStatus(code = OtlpJsonStatusCode.STATUS_CODE_ERROR),
        )
        val json = String(
            OtlpJsonExportTraceServiceRequest(listOf(OtlpJsonResourceSpans(scopeSpans = listOf(OtlpJsonScopeSpans(spans = listOf(span)), OtlpJsonScopeSpans(spans = emptyList(), schemaUrl = "u"))))).toJsonBytes(),
            Charsets.UTF_8
        )
        assertEquals(
            """{"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"t","spanId":"s","name":"n","kind":"SPAN_KIND_CLIENT",""" +
                """"startTimeUnixNano":"1700000000000000000","endTimeUnixNano":"0","events":[{"timeUnixNano":"1","name":"e"}],""" +
                """"links":[{"traceId":"lt","spanId":"ls","flags":1}],"status":{"code":"STATUS_CODE_ERROR"}}]},{"spans":[],"schemaUrl":"u"}]}]}""",
            json
        )
    }

    @Test
    fun `gauge and sum points carry their value as asInt or asDouble`() {
        val points = listOf(
            OtlpJsonNumberDataPoint(startTimeUnixNano = JsonStringLong(1), timeUnixNano = JsonStringLong(2), value = OtlpJsonNumberValue.Int(3)),
            OtlpJsonNumberDataPoint(
                startTimeUnixNano = JsonStringLong(1),
                timeUnixNano = JsonStringLong(2),
                value = OtlpJsonNumberValue.Double(0.5),
                exemplars = listOf(OtlpJsonExemplar(timeUnixNano = JsonStringLong(4), value = OtlpJsonNumberValue.Int(5), traceId = "t")),
            ),
        )
        assertEquals(
            """{"name":"g","gauge":{"dataPoints":[{"startTimeUnixNano":"1","timeUnixNano":"2","asInt":"3"},""" +
                """{"startTimeUnixNano":"1","timeUnixNano":"2","asDouble":0.5,"exemplars":[{"timeUnixNano":"4","asInt":"5","traceId":"t"}]}]}}""",
            metric(OtlpJsonMetric("g", data = OtlpJsonMetric.Data.Gauge(OtlpJsonGauge(points))))
        )
        assertEquals(
            """{"name":"s","description":"d","unit":"ms","sum":{"dataPoints":[],"aggregationTemporality":"AGGREGATION_TEMPORALITY_DELTA","isMonotonic":true}}""",
            metric(
                OtlpJsonMetric(
                    "s", "d", "ms",
                    OtlpJsonMetric.Data.Sum(OtlpJsonSum(emptyList(), OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_DELTA, true))
                )
            )
        )
    }

    @Test
    fun `histogram points write counts as strings and bounds as numbers`() {
        val point = OtlpJsonHistogramDataPoint(
            startTimeUnixNano = JsonStringLong(1),
            timeUnixNano = JsonStringLong(2),
            count = JsonStringLong(7),
            sum = 12.5,
            bucketCounts = listOf(JsonStringLong(1), JsonStringLong(6)),
            explicitBounds = listOf(10.0),
            min = -1.0,
            max = 99.0,
        )
        assertEquals(
            """{"name":"h","histogram":{"dataPoints":[{"startTimeUnixNano":"1","timeUnixNano":"2","count":"7","sum":12.5,""" +
                """"bucketCounts":["1","6"],"explicitBounds":[10.0],"min":-1.0,"max":99.0}],"aggregationTemporality":"AGGREGATION_TEMPORALITY_CUMULATIVE"}}""",
            metric(OtlpJsonMetric("h", data = OtlpJsonMetric.Data.Histogram(OtlpJsonHistogram(listOf(point), OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE))))
        )
    }

    @Test
    fun `exponential histogram and summary points`() {
        val exponential = OtlpJsonExponentialHistogramDataPoint(
            startTimeUnixNano = JsonStringLong(1),
            timeUnixNano = JsonStringLong(2),
            count = JsonStringLong(3),
            scale = -2,
            zeroCount = JsonStringLong(1),
            positive = OtlpJsonExponentialHistogramDataPoint.Buckets(offset = -1, bucketCounts = listOf(JsonStringLong(2))),
        )
        assertEquals(
            """{"name":"e","exponentialHistogram":{"dataPoints":[{"startTimeUnixNano":"1","timeUnixNano":"2","count":"3","scale":-2,""" +
                """"zeroCount":"1","positive":{"offset":-1,"bucketCounts":["2"]}}],"aggregationTemporality":"AGGREGATION_TEMPORALITY_DELTA"}}""",
            metric(OtlpJsonMetric("e", data = OtlpJsonMetric.Data.ExponentialHistogram(OtlpJsonExponentialHistogram(listOf(exponential), OtlpJsonAggregationTemporality.AGGREGATION_TEMPORALITY_DELTA))))
        )

        val summary = OtlpJsonSummaryDataPoint(
            startTimeUnixNano = JsonStringLong(1),
            timeUnixNano = JsonStringLong(2),
            count = JsonStringLong(2),
            sum = 4.0,
            quantileValues = listOf(OtlpJsonSummaryDataPoint.ValueAtQuantile(quantile = 0.5, value = 1.0)),
        )
        assertEquals(
            """{"name":"q","summary":{"dataPoints":[{"startTimeUnixNano":"1","timeUnixNano":"2","count":"2","sum":4.0,""" +
                """"quantileValues":[{"quantile":0.5,"value":1.0}]}]}}""",
            metric(OtlpJsonMetric("q", data = OtlpJsonMetric.Data.Summary(OtlpJsonSummary(listOf(summary)))))
        )
    }
}
