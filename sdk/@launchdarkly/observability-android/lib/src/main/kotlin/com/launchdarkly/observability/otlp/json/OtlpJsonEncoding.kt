package com.launchdarkly.observability.otlp.json

import com.launchdarkly.observability.json.JsonByteWriter
import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonAnyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonExportLogsServiceRequest
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonLogRecord
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonResourceLogs
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonScopeLogs
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExemplar
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExponentialHistogramDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExportMetricsServiceRequest
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonHistogramDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonMetric
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonNumberDataPoint
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonNumberValue
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonResourceMetrics
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonScopeMetrics
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonSummaryDataPoint
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonExportTraceServiceRequest
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonResourceSpans
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonScopeSpans
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonSpan
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonStatus

/*
 * OTLP/JSON encoding of the wire-format models. Optional fields are left out when null rather than
 * written as `null`, and fields are written in proto declaration order.
 */

internal fun OtlpJsonExportTraceServiceRequest.toJsonBytes(): ByteArray =
    JsonByteWriter.encode { write(this@toJsonBytes) }

internal fun OtlpJsonExportLogsServiceRequest.toJsonBytes(): ByteArray =
    JsonByteWriter.encode { write(this@toJsonBytes) }

internal fun OtlpJsonExportMetricsServiceRequest.toJsonBytes(): ByteArray =
    JsonByteWriter.encode { write(this@toJsonBytes) }

// Common

private inline fun <T> JsonByteWriter.array(name: String, values: List<T>, element: JsonByteWriter.(T) -> Unit) {
    name(name).beginArray()
    for (value in values) element(value)
    endArray()
}

private inline fun <T> JsonByteWriter.optionalArray(name: String, values: List<T>?, element: JsonByteWriter.(T) -> Unit) {
    if (values != null) array(name, values, element)
}

private fun JsonByteWriter.value(value: JsonStringLong) {
    value(value.value.toString())
}

private fun JsonByteWriter.name(name: String, value: JsonStringLong) {
    name(name).value(value)
}

private fun JsonByteWriter.optional(name: String, value: JsonStringLong?) {
    if (value != null) name(name, value)
}

private fun JsonByteWriter.attributes(name: String, attributes: List<OtlpJsonKeyValue>?) {
    optionalArray(name, attributes) { write(it) }
}

private fun JsonByteWriter.write(resource: OtlpJsonResource) {
    beginObject()
    // An empty list is the default, and defaults are not written.
    if (resource.attributes.isNotEmpty()) array("attributes", resource.attributes) { write(it) }
    optional("droppedAttributesCount", resource.droppedAttributesCount)
    endObject()
}

private fun JsonByteWriter.write(scope: OtlpJsonInstrumentationScope) {
    beginObject()
    name("name").value(scope.name)
    optional("version", scope.version)
    attributes("attributes", scope.attributes)
    optional("droppedAttributesCount", scope.droppedAttributesCount)
    endObject()
}

private fun JsonByteWriter.write(keyValue: OtlpJsonKeyValue) {
    beginObject()
    name("key").value(keyValue.key)
    name("value")
    write(keyValue.value)
    endObject()
}

/** Exactly one member, named for the variant, as the proto `oneof` is mapped to JSON. */
private fun JsonByteWriter.write(value: OtlpJsonAnyValue) {
    beginObject()
    when (value) {
        is OtlpJsonAnyValue.StringVal -> name("stringValue").value(value.value)
        is OtlpJsonAnyValue.BoolVal -> name("boolValue").value(value.value)
        is OtlpJsonAnyValue.IntVal -> name("intValue", value.value)
        is OtlpJsonAnyValue.DoubleVal -> name("doubleValue").value(value.value)
        is OtlpJsonAnyValue.ArrayVal -> {
            name("arrayValue").beginObject()
            if (value.value.values.isNotEmpty()) array("values", value.value.values) { write(it) }
            endObject()
        }
        is OtlpJsonAnyValue.KvListVal -> {
            name("kvlistValue").beginObject()
            if (value.value.values.isNotEmpty()) array("values", value.value.values) { write(it) }
            endObject()
        }
        is OtlpJsonAnyValue.BytesVal -> name("bytesValue").value(value.value)
    }
    endObject()
}

private fun JsonByteWriter.optionalResource(resource: OtlpJsonResource?) {
    if (resource != null) {
        name("resource")
        write(resource)
    }
}

private fun JsonByteWriter.optionalScope(scope: OtlpJsonInstrumentationScope?) {
    if (scope != null) {
        name("scope")
        write(scope)
    }
}

// Traces

private fun JsonByteWriter.write(request: OtlpJsonExportTraceServiceRequest) {
    beginObject()
    array("resourceSpans", request.resourceSpans) { write(it) }
    endObject()
}

private fun JsonByteWriter.write(resourceSpans: OtlpJsonResourceSpans) {
    beginObject()
    optionalResource(resourceSpans.resource)
    array("scopeSpans", resourceSpans.scopeSpans) { write(it) }
    optional("schemaUrl", resourceSpans.schemaUrl)
    endObject()
}

private fun JsonByteWriter.write(scopeSpans: OtlpJsonScopeSpans) {
    beginObject()
    optionalScope(scopeSpans.scope)
    array("spans", scopeSpans.spans) { write(it) }
    optional("schemaUrl", scopeSpans.schemaUrl)
    endObject()
}

private fun JsonByteWriter.write(span: OtlpJsonSpan) {
    beginObject()
    name("traceId").value(span.traceId)
    name("spanId").value(span.spanId)
    optional("traceState", span.traceState)
    optional("parentSpanId", span.parentSpanId)
    optional("flags", span.flags)
    name("name").value(span.name)
    name("kind").value(span.kind.name)
    name("startTimeUnixNano", span.startTimeUnixNano)
    name("endTimeUnixNano", span.endTimeUnixNano)
    attributes("attributes", span.attributes)
    optional("droppedAttributesCount", span.droppedAttributesCount)
    optionalArray("events", span.events) { write(it) }
    optional("droppedEventsCount", span.droppedEventsCount)
    optionalArray("links", span.links) { write(it) }
    optional("droppedLinksCount", span.droppedLinksCount)
    span.status?.let {
        name("status")
        write(it)
    }
    endObject()
}

private fun JsonByteWriter.write(event: OtlpJsonSpan.Event) {
    beginObject()
    name("timeUnixNano", event.timeUnixNano)
    name("name").value(event.name)
    attributes("attributes", event.attributes)
    optional("droppedAttributesCount", event.droppedAttributesCount)
    endObject()
}

private fun JsonByteWriter.write(link: OtlpJsonSpan.Link) {
    beginObject()
    name("traceId").value(link.traceId)
    name("spanId").value(link.spanId)
    optional("traceState", link.traceState)
    attributes("attributes", link.attributes)
    optional("droppedAttributesCount", link.droppedAttributesCount)
    optional("flags", link.flags)
    endObject()
}

private fun JsonByteWriter.write(status: OtlpJsonStatus) {
    beginObject()
    optional("message", status.message)
    name("code").value(status.code.name)
    endObject()
}

// Logs

private fun JsonByteWriter.write(request: OtlpJsonExportLogsServiceRequest) {
    beginObject()
    array("resourceLogs", request.resourceLogs) { write(it) }
    endObject()
}

private fun JsonByteWriter.write(resourceLogs: OtlpJsonResourceLogs) {
    beginObject()
    optionalResource(resourceLogs.resource)
    array("scopeLogs", resourceLogs.scopeLogs) { write(it) }
    optional("schemaUrl", resourceLogs.schemaUrl)
    endObject()
}

private fun JsonByteWriter.write(scopeLogs: OtlpJsonScopeLogs) {
    beginObject()
    optionalScope(scopeLogs.scope)
    array("logRecords", scopeLogs.logRecords) { write(it) }
    optional("schemaUrl", scopeLogs.schemaUrl)
    endObject()
}

private fun JsonByteWriter.write(record: OtlpJsonLogRecord) {
    beginObject()
    optional("timeUnixNano", record.timeUnixNano)
    optional("observedTimeUnixNano", record.observedTimeUnixNano)
    optional("severityNumber", record.severityNumber)
    optional("severityText", record.severityText)
    record.body?.let {
        name("body")
        write(it)
    }
    attributes("attributes", record.attributes)
    optional("droppedAttributesCount", record.droppedAttributesCount)
    optional("flags", record.flags)
    optional("traceId", record.traceId)
    optional("spanId", record.spanId)
    optional("eventName", record.eventName)
    endObject()
}

// Metrics

private fun JsonByteWriter.write(request: OtlpJsonExportMetricsServiceRequest) {
    beginObject()
    array("resourceMetrics", request.resourceMetrics) { write(it) }
    endObject()
}

private fun JsonByteWriter.write(resourceMetrics: OtlpJsonResourceMetrics) {
    beginObject()
    optionalResource(resourceMetrics.resource)
    array("scopeMetrics", resourceMetrics.scopeMetrics) { write(it) }
    optional("schemaUrl", resourceMetrics.schemaUrl)
    endObject()
}

private fun JsonByteWriter.write(scopeMetrics: OtlpJsonScopeMetrics) {
    beginObject()
    optionalScope(scopeMetrics.scope)
    array("metrics", scopeMetrics.metrics) { write(it) }
    optional("schemaUrl", scopeMetrics.schemaUrl)
    endObject()
}

/** The proto `data` oneof is written as the one member named for its variant. */
private fun JsonByteWriter.write(metric: OtlpJsonMetric) {
    beginObject()
    name("name").value(metric.name)
    optional("description", metric.description)
    optional("unit", metric.unit)
    when (val data = metric.data) {
        is OtlpJsonMetric.Data.Gauge -> {
            name("gauge").beginObject()
            array("dataPoints", data.value.dataPoints) { write(it) }
            endObject()
        }
        is OtlpJsonMetric.Data.Sum -> {
            name("sum").beginObject()
            array("dataPoints", data.value.dataPoints) { write(it) }
            name("aggregationTemporality").value(data.value.aggregationTemporality.name)
            name("isMonotonic").value(data.value.isMonotonic)
            endObject()
        }
        is OtlpJsonMetric.Data.Histogram -> {
            name("histogram").beginObject()
            array("dataPoints", data.value.dataPoints) { write(it) }
            name("aggregationTemporality").value(data.value.aggregationTemporality.name)
            endObject()
        }
        is OtlpJsonMetric.Data.ExponentialHistogram -> {
            name("exponentialHistogram").beginObject()
            array("dataPoints", data.value.dataPoints) { write(it) }
            name("aggregationTemporality").value(data.value.aggregationTemporality.name)
            endObject()
        }
        is OtlpJsonMetric.Data.Summary -> {
            name("summary").beginObject()
            array("dataPoints", data.value.dataPoints) { write(it) }
            endObject()
        }
    }
    endObject()
}

private fun JsonByteWriter.numberValue(value: OtlpJsonNumberValue) {
    when (value) {
        is OtlpJsonNumberValue.Int -> name("asInt", JsonStringLong(value.value))
        is OtlpJsonNumberValue.Double -> name("asDouble").value(value.value)
    }
}

private fun JsonByteWriter.write(point: OtlpJsonNumberDataPoint) {
    beginObject()
    attributes("attributes", point.attributes)
    name("startTimeUnixNano", point.startTimeUnixNano)
    name("timeUnixNano", point.timeUnixNano)
    numberValue(point.value)
    optionalArray("exemplars", point.exemplars) { write(it) }
    optional("flags", point.flags)
    endObject()
}

private fun JsonByteWriter.write(point: OtlpJsonHistogramDataPoint) {
    beginObject()
    attributes("attributes", point.attributes)
    name("startTimeUnixNano", point.startTimeUnixNano)
    name("timeUnixNano", point.timeUnixNano)
    name("count", point.count)
    optional("sum", point.sum)
    optionalArray("bucketCounts", point.bucketCounts) { value(it) }
    optionalArray("explicitBounds", point.explicitBounds) { value(it) }
    optionalArray("exemplars", point.exemplars) { write(it) }
    optional("flags", point.flags)
    optional("min", point.min)
    optional("max", point.max)
    endObject()
}

private fun JsonByteWriter.write(point: OtlpJsonExponentialHistogramDataPoint) {
    beginObject()
    attributes("attributes", point.attributes)
    name("startTimeUnixNano", point.startTimeUnixNano)
    name("timeUnixNano", point.timeUnixNano)
    name("count", point.count)
    optional("sum", point.sum)
    name("scale").value(point.scale)
    optional("zeroCount", point.zeroCount)
    point.positive?.let {
        name("positive")
        write(it)
    }
    point.negative?.let {
        name("negative")
        write(it)
    }
    optional("flags", point.flags)
    optionalArray("exemplars", point.exemplars) { write(it) }
    optional("min", point.min)
    optional("max", point.max)
    endObject()
}

private fun JsonByteWriter.write(buckets: OtlpJsonExponentialHistogramDataPoint.Buckets) {
    beginObject()
    name("offset").value(buckets.offset)
    array("bucketCounts", buckets.bucketCounts) { value(it) }
    endObject()
}

private fun JsonByteWriter.write(point: OtlpJsonSummaryDataPoint) {
    beginObject()
    attributes("attributes", point.attributes)
    name("startTimeUnixNano", point.startTimeUnixNano)
    name("timeUnixNano", point.timeUnixNano)
    name("count", point.count)
    name("sum").value(point.sum)
    optionalArray("quantileValues", point.quantileValues) {
        beginObject()
        name("quantile").value(it.quantile)
        name("value").value(it.value)
        endObject()
    }
    optional("flags", point.flags)
    endObject()
}

private fun JsonByteWriter.write(exemplar: OtlpJsonExemplar) {
    beginObject()
    attributes("filteredAttributes", exemplar.filteredAttributes)
    name("timeUnixNano", exemplar.timeUnixNano)
    numberValue(exemplar.value)
    optional("traceId", exemplar.traceId)
    optional("spanId", exemplar.spanId)
    endObject()
}
