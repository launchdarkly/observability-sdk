package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.logs.EventLogRecordProcessor
import com.launchdarkly.observability.logs.GlobalAttributesLogRecordProcessor
import com.launchdarkly.observability.logs.LogItemPayload
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.traces.EventSpanProcessor
import com.launchdarkly.observability.traces.GlobalAttributesSpanProcessor
import com.launchdarkly.observability.traces.SpanItemPayload
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Runs real OpenTelemetry providers wired like [ObservabilityService]: the global-attribute
 * processor first, then the SDK's export processor feeding the shared [EventQueue].
 */
class GlobalAttributesPipelineTest {

    private val logger = mockk<ObserveLogger>(relaxed = true)
    private val queue = EventQueue()

    @BeforeEach
    fun setup() {
        GlobalAttributesStore.clear()
        GlobalAttributesStore.logger = logger
    }

    @AfterEach
    fun tearDown() {
        GlobalAttributesStore.clear()
        GlobalAttributesStore.logger = null
    }

    // region spans

    @Test
    fun `spans get global attributes set before initialization`() {
        LDObserve.setAttributes(Attributes.of(TENANT, "acme"))
        val tracerProvider = tracerProvider()

        tracerProvider.get("test").spanBuilder("manual").startSpan().end()

        assertEquals("acme", exportedSpans().single().attributes.get(TENANT))
    }

    @Test
    fun `per-call span attributes win over globals and globals do not touch the resource`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "global", REGION, "us"))
        val tracerProvider = tracerProvider(resource = Resource.create(Attributes.of(TENANT, "resource")))

        tracerProvider.get("test").spanBuilder("manual")
            .setAttribute(TENANT, "per-call")
            .setAttribute(AttributeKey.longKey("region"), 7L)
            .startSpan()
            .end()

        val span = exportedSpans().single()
        assertEquals("per-call", span.attributes.get(TENANT))
        assertEquals(7L, span.attributes.get(AttributeKey.longKey("region")))
        assertNull(span.attributes.get(REGION))
        assertEquals("resource", span.resource.attributes.get(TENANT))
    }

    @Test
    fun `span keeps the global values current at span start`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "at-start"))
        val tracerProvider = tracerProvider()

        val span = tracerProvider.get("test").spanBuilder("manual").startSpan()
        GlobalAttributesStore.set(Attributes.of(TENANT, "later", REGION, "eu"))
        span.end()

        val exported = exportedSpans().single()
        assertEquals("at-start", exported.attributes.get(TENANT))
        assertNull(exported.attributes.get(REGION))
    }

    @Test
    fun `removed globals are not applied to later spans`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme", REGION, "us"))
        GlobalAttributesStore.remove(listOf("tenant"))
        val tracerProvider = tracerProvider()

        tracerProvider.get("test").spanBuilder("manual").startSpan().end()

        val exported = exportedSpans().single()
        assertNull(exported.attributes.get(TENANT))
        assertEquals("us", exported.attributes.get(REGION))
    }

    @Test
    fun `reserved session keys never reach spans through globals`() {
        GlobalAttributesStore.set(Attributes.builder().put("session.id", "spoofed").put("tenant", "acme").build())
        val tracerProvider = tracerProvider()

        tracerProvider.get("test").spanBuilder("manual").startSpan().end()

        val exported = exportedSpans().single()
        assertNull(exported.attributes.get(AttributeKey.stringKey("session.id")))
        assertEquals("acme", exported.attributes.get(TENANT))
    }

    // endregion

    // region logs

    @Test
    fun `logs get globals set before initialization`() {
        LDObserve.setAttributes(mapOf("tenant" to "acme"))
        val loggerProvider = loggerProvider()

        loggerProvider.get("test").logRecordBuilder().setBody("hello").emit()

        assertEquals("acme", exportedLogs().single().attributes.get(TENANT))
    }

    @Test
    fun `logs get globals while per-call attributes win`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "global", REGION, "us"))
        val loggerProvider = loggerProvider()

        loggerProvider.get("test").logRecordBuilder()
            .setBody("hello")
            .setAttribute(TENANT, "per-call")
            .emit()

        val log = exportedLogs().single()
        assertEquals("per-call", log.attributes.get(TENANT))
        assertEquals("us", log.attributes.get(REGION))
    }

    // endregion

    @Test
    fun `metric data points get globals while per-call attributes win`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "global", REGION, "us"))
        val reader = InMemoryMetricReader.create()
        val meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build()

        meterProvider.get("test").counterBuilder("requests").build()
            .add(1, GlobalAttributesStore.merge(Attributes.of(TENANT, "per-call")))

        val point = reader.collectAllMetrics().single().longSumData.points.single()
        assertEquals("per-call", point.attributes.get(TENANT))
        assertEquals("us", point.attributes.get(REGION))
    }

    private fun tracerProvider(resource: Resource = Resource.empty()): SdkTracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(GlobalAttributesSpanProcessor())
            .addSpanProcessor(EventSpanProcessor(queue, FakeExportSampler()))
            .build()

    private fun loggerProvider(): SdkLoggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(GlobalAttributesLogRecordProcessor())
            .addLogRecordProcessor(EventLogRecordProcessor(queue))
            .build()

    private fun exportedSpans(): List<SpanData> =
        queuedPayloads().filterIsInstance<SpanItemPayload>().map { it.spanData }

    private fun exportedLogs(): List<LogRecordData> =
        queuedPayloads().filterIsInstance<LogItemPayload>().map { it.logRecord }

    private fun queuedPayloads() =
        queue.earliest(costBudget = Int.MAX_VALUE, limit = 100, except = emptySet())
            ?.items?.map { it.payload }
            .orEmpty()

    private companion object {
        val TENANT: AttributeKey<String> = AttributeKey.stringKey("tenant")
        val REGION: AttributeKey<String> = AttributeKey.stringKey("region")
    }
}
