package com.launchdarkly.observability.otlp.json.traces

import com.launchdarkly.observability.otlp.json.JsonTestHelpers.array
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.encodeToTree
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.int
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.obj
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.string
import com.launchdarkly.observability.otlp.json.JsonTestHelpers.stringOrNull
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonSpanAdapterTest {

    @Test
    fun `encodes spans as OTLP JSON with proper field naming`() {
        val traceIdHex = "0102030405060708090a0b0c0d0e0f10"
        val spanIdHex = "1112131415161718"
        val parentSpanIdHex = "2122232425262728"

        val spanCtx = SpanContext.create(traceIdHex, spanIdHex, TraceFlags.getSampled(), TraceState.getDefault())
        val parentCtx = SpanContext.create(traceIdHex, parentSpanIdHex, TraceFlags.getSampled(), TraceState.getDefault())

        val spanData = TestSpanData.builder()
            .setName("GET /users")
            .setKind(SpanKind.CLIENT)
            .setSpanContext(spanCtx)
            .setParentSpanContext(parentCtx)
            .setStartEpochNanos(1_700_000_000_000_000_000L)
            .setEndEpochNanos(1_700_000_001_000_000_000L)
            .setHasEnded(true)
            .setStatus(StatusData.ok())
            .setResource(
                Resource.create(
                    Attributes.of(AttributeKey.stringKey("service.name"), "test-service")
                )
            )
            .setInstrumentationScopeInfo(
                InstrumentationScopeInfo.builder("test-scope").setVersion("1.2.3").build()
            )
            .setAttributes(Attributes.of(AttributeKey.longKey("http.status_code"), 200L))
            .setTotalAttributeCount(1)
            .build()

        val tree = encode(spanData)

        val resourceSpan = obj(array(tree["resourceSpans"])[0])
        val resource = obj(resourceSpan["resource"])
        val firstAttr = obj(array(resource["attributes"])[0])
        assertEquals("service.name", string(firstAttr["key"]))

        val scopeSpan = obj(array(resourceSpan["scopeSpans"])[0])
        val scope = obj(scopeSpan["scope"])
        assertEquals("test-scope", string(scope["name"]))
        assertEquals("1.2.3", string(scope["version"]))

        val span = obj(array(scopeSpan["spans"])[0])

        assertEquals(traceIdHex, string(span["traceId"]))
        assertEquals(spanIdHex, string(span["spanId"]))
        assertEquals(parentSpanIdHex, string(span["parentSpanId"]))
        assertEquals("GET /users", string(span["name"]))
        assertEquals("SPAN_KIND_CLIENT", string(span["kind"]))
        assertEquals("1700000000000000000", string(span["startTimeUnixNano"]))
        assertEquals("1700000001000000000", string(span["endTimeUnixNano"]))
        assertEquals(1, int(span["flags"]))

        val status = obj(span["status"])
        assertEquals("STATUS_CODE_OK", string(status["code"]))
        assertNull(status["message"])

        val attr = obj(array(span["attributes"])[0])
        assertEquals("200", string(obj(attr["value"])["intValue"]))
    }

    @Test
    fun `encodes events links and error status`() {
        val linkedTraceHex = "aabbccddeeff00112233445566778899"
        val linkedSpanHex = "9988776655443322"
        val linkedCtx = SpanContext.create(
            linkedTraceHex, linkedSpanHex, TraceFlags.getDefault(), TraceState.getDefault()
        )

        val event = EventData.create(
            1_700_000_000_000_000_000L,
            "exception",
            Attributes.of(AttributeKey.stringKey("exception.type"), "OOM"),
        )
        val link = LinkData.create(
            linkedCtx,
            Attributes.of(AttributeKey.stringKey("link.kind"), "follows-from"),
        )

        val spanData = TestSpanData.builder()
            .setName("boom")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(
                SpanContext.create(
                    "0102030405060708090a0b0c0d0e0f10",
                    "1112131415161718",
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                )
            )
            .setStartEpochNanos(1L)
            .setEndEpochNanos(2L)
            .setHasEnded(true)
            .setStatus(StatusData.create(StatusCode.ERROR, "out of memory"))
            .setEvents(listOf(event))
            .setTotalRecordedEvents(1)
            .setLinks(listOf(link))
            .setTotalRecordedLinks(1)
            .build()

        val span = drillToSpan(encode(spanData))

        val status = obj(span["status"])
        assertEquals("STATUS_CODE_ERROR", string(status["code"]))
        assertEquals("out of memory", string(status["message"]))

        val firstEvent = obj(array(span["events"])[0])
        assertEquals("exception", string(firstEvent["name"]))
        assertEquals("1700000000000000000", string(firstEvent["timeUnixNano"]))

        val firstLink = obj(array(span["links"])[0])
        assertEquals(linkedTraceHex, string(firstLink["traceId"]))
        assertEquals(linkedSpanHex, string(firstLink["spanId"]))
    }

    @Test
    fun `omits parentSpanId for root spans`() {
        val spanData = TestSpanData.builder()
            .setName("root")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(
                SpanContext.create(
                    "0102030405060708090a0b0c0d0e0f10",
                    "1112131415161718",
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                )
            )
            .setParentSpanContext(SpanContext.getInvalid())
            .setStartEpochNanos(1L)
            .setEndEpochNanos(2L)
            .setHasEnded(true)
            .setStatus(StatusData.unset())
            .build()

        val span = drillToSpan(encode(spanData))
        assertNull(stringOrNull(span["parentSpanId"]))
    }

    @Test
    fun `reports dropped attribute event and link counts`() {
        val spanData = TestSpanData.builder()
            .setName("dropped")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(
                SpanContext.create(
                    "0102030405060708090a0b0c0d0e0f10",
                    "1112131415161718",
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                )
            )
            .setStartEpochNanos(1L)
            .setEndEpochNanos(2L)
            .setHasEnded(true)
            .setStatus(StatusData.unset())
            .setAttributes(Attributes.of(AttributeKey.stringKey("k"), "v"))
            .setTotalAttributeCount(4)
            .setEvents(emptyList())
            .setTotalRecordedEvents(2)
            .setLinks(emptyList())
            .setTotalRecordedLinks(3)
            .build()

        val span = drillToSpan(encode(spanData))
        assertEquals(3, int(span["droppedAttributesCount"]))
        assertEquals(2, int(span["droppedEventsCount"]))
        assertEquals(3, int(span["droppedLinksCount"]))
    }

    @Test
    fun `maps all SpanKind cases to proto-JSON enum strings`() {
        assertEquals(OtlpJsonSpanKind.SPAN_KIND_INTERNAL, JsonSpanAdapter.toJsonSpanKind(SpanKind.INTERNAL))
        assertEquals(OtlpJsonSpanKind.SPAN_KIND_SERVER, JsonSpanAdapter.toJsonSpanKind(SpanKind.SERVER))
        assertEquals(OtlpJsonSpanKind.SPAN_KIND_CLIENT, JsonSpanAdapter.toJsonSpanKind(SpanKind.CLIENT))
        assertEquals(OtlpJsonSpanKind.SPAN_KIND_PRODUCER, JsonSpanAdapter.toJsonSpanKind(SpanKind.PRODUCER))
        assertEquals(OtlpJsonSpanKind.SPAN_KIND_CONSUMER, JsonSpanAdapter.toJsonSpanKind(SpanKind.CONSUMER))
    }

    @Test
    fun `encodes non-empty TraceState in W3C format`() {
        val state = TraceState.builder().put("vendor1", "val1").build()
        assertEquals("vendor1=val1", JsonSpanAdapter.encodeTraceState(state))
    }

    @Test
    fun `returns null for empty TraceState`() {
        assertNull(JsonSpanAdapter.encodeTraceState(TraceState.getDefault()))
    }

    private fun encode(span: SpanData): JsonObject {
        val request = JsonSpanAdapter.toJsonRequest(listOf(span))
        return encodeToTree(request, OtlpJsonExportTraceServiceRequest.serializer())
    }

    private fun drillToSpan(tree: JsonObject): JsonObject {
        val resourceSpan = obj(array(tree["resourceSpans"])[0])
        val scopeSpan = obj(array(resourceSpan["scopeSpans"])[0])
        return obj(array(scopeSpan["spans"])[0])
    }
}
