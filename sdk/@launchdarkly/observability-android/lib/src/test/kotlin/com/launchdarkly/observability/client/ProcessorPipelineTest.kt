package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.logs.EventLogRecordProcessor
import com.launchdarkly.observability.logs.LogItemPayload
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.traces.EventSpanProcessor
import com.launchdarkly.observability.traces.SpanItemPayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessorPipelineTest {

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
    fun `user span processors run in order after globals and before export, and their mutations are exported`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme"))
        val calls = mutableListOf<String>()
        val first = RecordingSpanProcessor("first", calls) { span ->
            assertEquals("acme", span.getAttribute(TENANT))
            span.setAttribute(MUTATED, "first")
        }
        val second = RecordingSpanProcessor("second", calls) { span ->
            assertEquals("first", span.getAttribute(MUTATED))
            span.setAttribute(SECOND, true)
        }
        val exportRecorder = RecordingSpanProcessor("export", calls)
        val tracerProvider = tracerProvider(userProcessors = listOf(first, second), extraSdkProcessor = exportRecorder)

        tracerProvider.get("test").spanBuilder("manual").startSpan().end()

        assertEquals(
            listOf("first.onStart", "second.onStart", "export.onStart", "first.onEnd", "second.onEnd", "export.onEnd"),
            calls,
        )
        val exported = exportedSpans().single()
        assertEquals("acme", exported.attributes.get(TENANT))
        assertEquals("first", exported.attributes.get(MUTATED))
        assertEquals(true, exported.attributes.get(SECOND))
    }

    @Test
    fun `a throwing user span processor does not break export`() {
        val throwing = mockk<SpanProcessor> {
            every { isStartRequired } returns true
            every { isEndRequired } returns true
            every { onStart(any(), any()) } throws IllegalStateException("boom")
            every { onEnd(any()) } throws IllegalStateException("boom")
            every { forceFlush() } throws IllegalStateException("boom")
            every { shutdown() } throws IllegalStateException("boom")
        }
        val tracerProvider = tracerProvider(userProcessors = listOf(throwing))

        tracerProvider.get("test").spanBuilder("manual").setAttribute(TENANT, "acme").startSpan().end()

        assertEquals("acme", exportedSpans().single().attributes.get(TENANT))
        tracerProvider.forceFlush().join(1, java.util.concurrent.TimeUnit.SECONDS)
        tracerProvider.shutdown().join(1, java.util.concurrent.TimeUnit.SECONDS)
        verify(atLeast = 4) { logger.error(any<String>(), any<Throwable>()) }
    }

    @Test
    fun `user span processors receive forceFlush and shutdown`() {
        val processor = mockk<SpanProcessor>(relaxed = true) {
            every { forceFlush() } returns CompletableResultCode.ofSuccess()
            every { shutdown() } returns CompletableResultCode.ofSuccess()
        }
        val tracerProvider = tracerProvider(userProcessors = listOf(processor))

        tracerProvider.forceFlush()
        tracerProvider.shutdown()

        verify(exactly = 1) { processor.forceFlush() }
        verify(exactly = 1) { processor.shutdown() }
    }

    // endregion

    // region logs

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

    @Test
    fun `user log processors run in order after globals and their mutations are exported`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme"))
        val calls = mutableListOf<String>()
        val first = RecordingLogProcessor("first", calls) { record ->
            assertEquals("acme", record.getAttribute(TENANT))
            record.setAttribute(MUTATED, "first")
        }
        val second = RecordingLogProcessor("second", calls) { record ->
            assertEquals("first", record.getAttribute(MUTATED))
        }
        val loggerProvider = loggerProvider(
            userProcessors = listOf(first, second),
            extraSdkProcessor = RecordingLogProcessor("export", calls),
        )

        loggerProvider.get("test").logRecordBuilder().setBody("hello").emit()

        assertEquals(listOf("first.onEmit", "second.onEmit", "export.onEmit"), calls)
        val log = exportedLogs().single()
        assertEquals("acme", log.attributes.get(TENANT))
        assertEquals("first", log.attributes.get(MUTATED))
    }

    @Test
    fun `a throwing user log processor does not break export`() {
        val throwing = mockk<LogRecordProcessor> {
            every { onEmit(any(), any()) } throws IllegalStateException("boom")
            every { forceFlush() } throws IllegalStateException("boom")
            every { shutdown() } throws IllegalStateException("boom")
        }
        val loggerProvider = loggerProvider(userProcessors = listOf(throwing))

        loggerProvider.get("test").logRecordBuilder().setBody("hello").setAttribute(TENANT, "acme").emit()

        assertEquals("acme", exportedLogs().single().attributes.get(TENANT))
        loggerProvider.forceFlush().join(1, java.util.concurrent.TimeUnit.SECONDS)
        loggerProvider.shutdown().join(1, java.util.concurrent.TimeUnit.SECONDS)
        verify(atLeast = 3) { logger.error(any<String>(), any<Throwable>()) }
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

    private fun tracerProvider(
        userProcessors: List<SpanProcessor> = emptyList(),
        extraSdkProcessor: SpanProcessor? = null,
        resource: Resource = Resource.empty(),
    ): SdkTracerProvider {
        val sdkProcessors = listOfNotNull(extraSdkProcessor, EventSpanProcessor(queue, FakeExportSampler()))
        val builder = SdkTracerProvider.builder().setResource(resource)
        ProcessorPipeline.spanProcessors(userProcessors, sdkProcessors, logger).forEach { builder.addSpanProcessor(it) }
        return builder.build()
    }

    private fun loggerProvider(
        userProcessors: List<LogRecordProcessor> = emptyList(),
        extraSdkProcessor: LogRecordProcessor? = null,
    ): SdkLoggerProvider {
        val sdkProcessors = listOfNotNull(extraSdkProcessor, EventLogRecordProcessor(queue))
        val builder = SdkLoggerProvider.builder()
        ProcessorPipeline.logProcessors(userProcessors, sdkProcessors, logger).forEach { builder.addLogRecordProcessor(it) }
        return builder.build()
    }

    private fun exportedSpans(): List<SpanData> =
        queuedPayloads().filterIsInstance<SpanItemPayload>().map { it.spanData }

    private fun exportedLogs(): List<LogRecordData> =
        queuedPayloads().filterIsInstance<LogItemPayload>().map { it.logRecord }

    private fun queuedPayloads() =
        queue.earliest(costBudget = Int.MAX_VALUE, limit = 100, except = emptySet())
            ?.items?.map { it.payload }
            .orEmpty()

    private class RecordingSpanProcessor(
        private val name: String,
        private val calls: MutableList<String>,
        private val onStartAction: (ReadWriteSpan) -> Unit = {},
    ) : SpanProcessor {
        override fun onStart(parentContext: Context, span: ReadWriteSpan) {
            calls += "$name.onStart"
            onStartAction(span)
        }

        override fun isStartRequired() = true

        override fun onEnd(span: ReadableSpan) {
            calls += "$name.onEnd"
        }

        override fun isEndRequired() = true
    }

    private class RecordingLogProcessor(
        private val name: String,
        private val calls: MutableList<String>,
        private val onEmitAction: (ReadWriteLogRecord) -> Unit = {},
    ) : LogRecordProcessor {
        override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
            calls += "$name.onEmit"
            onEmitAction(logRecord)
        }
    }

    private companion object {
        val TENANT: AttributeKey<String> = AttributeKey.stringKey("tenant")
        val REGION: AttributeKey<String> = AttributeKey.stringKey("region")
        val MUTATED: AttributeKey<String> = AttributeKey.stringKey("user.mutated")
        val SECOND: AttributeKey<Boolean> = AttributeKey.booleanKey("user.second")
    }
}
