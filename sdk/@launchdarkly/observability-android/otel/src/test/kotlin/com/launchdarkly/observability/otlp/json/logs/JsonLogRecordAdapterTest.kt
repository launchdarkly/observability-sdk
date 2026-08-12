package com.launchdarkly.observability.otlp.json.logs

import com.launchdarkly.observability.otlp.json.JsonTestHelpers.array
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.encodeToTree
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.int
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.obj
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.string
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.stringOrNull
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.logs.TestLogRecordData
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class JsonLogRecordAdapterTest {

    @Test
    fun `encodes log records as OTLP JSON with proper field naming`() {
        val record = TestLogRecordData.builder()
            .setResource(
                Resource.create(
                    Attributes.of(AttributeKey.stringKey("service.name"), "test-service")
                )
            )
            .setInstrumentationScopeInfo(
                InstrumentationScopeInfo.builder("test-scope").setVersion("1.2.3").build()
            )
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setObservedTimestamp(1_700_000_001_000_000_000L, TimeUnit.NANOSECONDS)
            .setSeverity(Severity.INFO)
            .setBody("hello")
            .setAttributes(
                Attributes.of(AttributeKey.longKey("http.status_code"), 200L)
            )
            .build()

        val tree = encode(record)

        val resourceLogs = array(tree["resourceLogs"])
        assertEquals(1, resourceLogs.size)
        val resourceLog = obj(resourceLogs[0])

        val resource = obj(resourceLog["resource"])
        val resourceAttrs = array(resource["attributes"])
        val firstAttr = obj(resourceAttrs[0])
        assertEquals("service.name", string(firstAttr["key"]))
        assertEquals("test-service", string(obj(firstAttr["value"])["stringValue"]))

        val scopeLog = obj(array(resourceLog["scopeLogs"])[0])
        val scope = obj(scopeLog["scope"])
        assertEquals("test-scope", string(scope["name"]))
        assertEquals("1.2.3", string(scope["version"]))

        val logRecord = obj(array(scopeLog["logRecords"])[0])

        assertEquals("1700000000000000000", string(logRecord["timeUnixNano"]))
        assertEquals("1700000001000000000", string(logRecord["observedTimeUnixNano"]))

        assertEquals(Severity.INFO.severityNumber, int(logRecord["severityNumber"]))
        assertEquals("INFO", string(logRecord["severityText"]))

        assertEquals("hello", string(obj(logRecord["body"])["stringValue"]))

        val logAttr = obj(array(logRecord["attributes"])[0])
        assertEquals("http.status_code", string(logAttr["key"]))
        assertEquals("200", string(obj(logAttr["value"])["intValue"]))
    }

    @Test
    fun `encodes traceId and spanId as lowercase hex strings`() {
        val traceIdHex = "0102030405060708090a0b0c0d0e0f10"
        val spanIdHex = "1112131415161718"
        val ctx = SpanContext.create(
            traceIdHex,
            spanIdHex,
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        )

        val record = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setSpanContext(ctx)
            .setAttributes(Attributes.empty())
            .build()

        val tree = encode(record)
        val logRecord = drillToLogRecord(tree)

        assertEquals(traceIdHex, string(logRecord["traceId"]))
        assertEquals(spanIdHex, string(logRecord["spanId"]))
        assertEquals(1, int(logRecord["flags"]))
    }

    @Test
    fun `omits traceId spanId and flags for invalid SpanContext`() {
        val record = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setSpanContext(SpanContext.getInvalid())
            .build()

        val logRecord = drillToLogRecord(encode(record))
        assertNull(stringOrNull(logRecord["traceId"]))
        assertNull(stringOrNull(logRecord["spanId"]))
        assertNull(logRecord["flags"])
    }

    @Test
    fun `encodes array and kvlist log body shapes`() {
        val nested = Value.of(
            io.opentelemetry.api.common.KeyValue.of("env", Value.of("prod"))
        )
        val body = Value.of(
            Value.of("a"),
            Value.of("b"),
        )

        val record = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setBodyValue(body)
            .setAttributes(Attributes.empty())
            .build()

        val arrayBody = obj(drillToLogRecord(encode(record))["body"])
        val values = array(obj(arrayBody["arrayValue"])["values"])
        assertEquals(2, values.size)
        assertEquals("a", string(obj(values[0])["stringValue"]))
        assertEquals("b", string(obj(values[1])["stringValue"]))

        val kvlistRecord = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setBodyValue(nested)
            .setAttributes(Attributes.empty())
            .build()

        val kvBody = obj(drillToLogRecord(encode(kvlistRecord))["body"])
        val kvEntries = array(obj(kvBody["kvlistValue"])["values"])
        val firstEntry = obj(kvEntries[0])
        assertEquals("env", string(firstEntry["key"]))
        assertEquals("prod", string(obj(firstEntry["value"])["stringValue"]))
    }

    @Test
    fun `omits severityNumber for undefined severity`() {
        val record = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setSeverity(Severity.UNDEFINED_SEVERITY_NUMBER)
            .build()

        val logRecord = drillToLogRecord(encode(record))
        assertNull(logRecord["severityNumber"])
        assertNull(logRecord["severityText"])
    }

    @Test
    fun `honours explicit severityText when set`() {
        val record = TestLogRecordData.builder()
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("scope"))
            .setTimestamp(1_700_000_000_000_000_000L, TimeUnit.NANOSECONDS)
            .setSeverity(Severity.ERROR)
            .setSeverityText("custom-error")
            .build()

        val logRecord = drillToLogRecord(encode(record))
        assertEquals("custom-error", string(logRecord["severityText"]))
        assertEquals(Severity.ERROR.severityNumber, int(logRecord["severityNumber"]))
    }

    private fun encode(record: io.opentelemetry.sdk.logs.data.LogRecordData): JsonObject {
        val request = JsonLogRecordAdapter.toJsonRequest(listOf(record))
        return encodeToTree(request, OtlpJsonExportLogsServiceRequest.serializer())
    }

    private fun drillToLogRecord(tree: JsonObject): JsonObject {
        val resourceLog = obj(array(tree["resourceLogs"])[0])
        val scopeLog = obj(array(resourceLog["scopeLogs"])[0])
        return obj(array(scopeLog["logRecords"])[0])
    }
}
