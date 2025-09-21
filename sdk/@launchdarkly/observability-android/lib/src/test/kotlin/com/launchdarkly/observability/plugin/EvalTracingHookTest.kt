package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.DATA_KEY_IDENTIFY_SCOPE
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.DATA_KEY_IDENTIFY_SPAN
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.IDENTIFY_EVENT_FINISH
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.IDENTIFY_EVENT_START
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.INSTRUMENTATION_NAME
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.SEMCONV_IDENTIFY_CONTEXT_ID
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.SEMCONV_IDENTIFY_EVENT_RESULT_VALUE
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.SEMCONV_IDENTIFY_SPAN_NAME
import com.launchdarkly.observability.plugin.EvalTracingHook.Companion.SEMCONV_IDENTIFY_TIMEOUT
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EvalTracingHookTest {

    private lateinit var mockTracer: Tracer
    private lateinit var mockContext: LDContext

    @BeforeEach
    fun setup() {
        mockTracer = mockk(relaxed = true)

        mockkStatic(GlobalOpenTelemetry::class)
        every { GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME) } returns mockTracer

        mockContext = mockk {
            every { fullyQualifiedKey } returns "test-user-123"
        }
    }

    @Nested
    @DisplayName("Before Identify Tests")
    inner class BeforeIdentifyTests {

        @Test
        fun `should not create spans when withSpans is disabled`() {
            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf("test" to "empty_data")
            val hook = EvalTracingHook(withSpans = false, withValue = false)

            val result = hook.beforeIdentify(seriesContext, seriesData)

            assertEquals(1, result.size)
            assertEquals(seriesData, result)
            verify { mockTracer wasNot called }
        }

        @Test
        fun `should create and configure identify span when withSpans is enabled`() {
            val spanScope = mockk<Scope>()
            val span: Span = mockk {
                every { makeCurrent() } returns spanScope
                every { addEvent(any()) } returns this
                every { setAllAttributes(any()) } returns this
            }
            val spanBuilder: SpanBuilder = mockk {
                every { startSpan() } returns span
            }
            every { mockTracer.spanBuilder(any()) } returns spanBuilder

            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf(
                "test" to "empty_data",
                DATA_KEY_IDENTIFY_SPAN to span,
                DATA_KEY_IDENTIFY_SCOPE to spanScope
            )
            val hook = EvalTracingHook(withSpans = true, withValue = false)

            val result = hook.beforeIdentify(seriesContext, seriesData)

            val attributes = Attributes.builder().apply {
                put(SEMCONV_IDENTIFY_CONTEXT_ID, mockContext.fullyQualifiedKey)
                put(SEMCONV_IDENTIFY_TIMEOUT, 5L)
            }

            assertEquals(seriesData, result)
            verify(exactly = 1) { mockTracer.spanBuilder(SEMCONV_IDENTIFY_SPAN_NAME) }
            verify(exactly = 1) { spanBuilder.startSpan() }
            verify(exactly = 1) { span.addEvent(IDENTIFY_EVENT_START) }
            verify(exactly = 1) { span.setAllAttributes(attributes.build()) }
        }
    }

    @Nested
    @DisplayName("After Identify Tests")
    inner class AfterIdentifyTests {

        @Test
        fun `should close span and scope after identify completion`() {
            val spanScope = mockk<Scope> {
                every { close() } returns Unit
            }
            val span: Span = mockk {
                every { addEvent(any(), any<Attributes>()) } returns this
                every { setAllAttributes(any()) } returns this
                every { end() } returns Unit
            }

            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf(
                "test" to "empty_data",
                DATA_KEY_IDENTIFY_SPAN to span,
                DATA_KEY_IDENTIFY_SCOPE to spanScope
            )
            val identifyResult = IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.COMPLETED)
            val hook = EvalTracingHook(withSpans = false, withValue = false)

            val result = hook.afterIdentify(seriesContext, seriesData, identifyResult)

            val attributes = Attributes.builder().apply {
                put(SEMCONV_IDENTIFY_EVENT_RESULT_VALUE, identifyResult.status.name)
            }

            assertEquals(seriesData, result)
            verify(exactly = 1) { span.end() }
            verify(exactly = 1) { spanScope.close() }
            verify(exactly = 1) { span.addEvent(IDENTIFY_EVENT_FINISH, attributes.build()) }
        }
    }
}