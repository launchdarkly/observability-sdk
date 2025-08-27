package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.MockExportSampler
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SampleSpansTest {

    @Nested
    @DisplayName("Sampling Disabled Tests")
    inner class SamplingDisabledTests {

        @Test
        fun `should return all spans when sampling is disabled`() {
            val sampler = MockExportSampler(isSamplingEnabled = { false })

            val spans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span2", "span2-id"),
                createMockSpan("span3", "span3-id")
            )

            val result = sampleSpans(spans, sampler)

            assertEquals(spans, result)
            assertEquals(3, result.size)
        }
    }

    @Nested
    @DisplayName("Sampling Enabled Tests")
    inner class SamplingEnabledTests {

        @Test
        fun `should handle empty input list`() {
            val sampler = MockExportSampler(isSamplingEnabled = { true })

            val result = sampleSpans(emptyList(), sampler)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when no spans are sampled`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = { SamplingResult(sample = false) }
            )

            val spans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span2", "span2-id"),
                createMockSpan("span3", "span3-id")
            )

            val result = sampleSpans(spans, sampler)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return all spans when all are sampled without additional attributes`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = { SamplingResult(sample = true, attributes = null) }
            )

            val spans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span2", "span2-id"),
                createMockSpan("span3", "span3-id")
            )

            val result = sampleSpans(spans, sampler)

            assertEquals(spans, result)
            assertEquals(3, result.size)
        }

        @Test
        fun `should return subset when some spans are sampled`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = {
                    if (it.name == "span2") {
                        SamplingResult(sample = false)
                    } else {
                        SamplingResult(sample = true)
                    }
                }
            )

            val span1 = createMockSpan("span1", "span1-id")
            val span2 = createMockSpan("span2", "span2-id")
            val span3 = createMockSpan("span3", "span3-id")
            val spans = listOf(span1, span2, span3)

            val result = sampleSpans(spans, sampler)

            assertEquals(2, result.size)
            assertEquals(span1, result[0])
            assertEquals(span3, result[1])
        }

        @Test
        fun `should add sampling attributes to spans when provided and preserve the original ones`() {
            val originalAttributes = Attributes.builder()
                .put("service.name", "api-service")
                .put("environment", "production")
                .build()

            val instrumentationScope = mockk<InstrumentationScopeInfo>()
            val originalSpan = createMockSpan(
                "test-span",
                "span-id",
                traceId = "trace-123",
                parentSpanId = "parent-123",
                attributes = originalAttributes,
                instrumentationScope = instrumentationScope
            )

            val spans = listOf(originalSpan)

            val samplingAttributes = Attributes.builder()
                .put("launchdarkly.sampling.ratio", 42L)
                .build()

            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = {
                    if (it.name == "test-span") {
                        SamplingResult(sample = true, attributes = samplingAttributes)
                    } else {
                        SamplingResult(sample = false)
                    }
                }
            )

            val result = sampleSpans(spans, sampler)

            assertEquals(1, result.size)
            val clonedSpan = result[0]
            assertNotSame(originalSpan, clonedSpan) // Should be a new instance with merged attributes

            // Verify the result has both original properties
            assertEquals("trace-123", clonedSpan.traceId)
            assertEquals("span-id", clonedSpan.spanId)
            assertEquals("parent-123", clonedSpan.parentSpanId)
            assertEquals(instrumentationScope, clonedSpan.instrumentationScopeInfo)

            // Verify the result has both original and merged attributes
            val resultAttributes = clonedSpan.attributes
            assertEquals("api-service", resultAttributes.get(AttributeKey.stringKey("service.name")))
            assertEquals("production", resultAttributes.get(AttributeKey.stringKey("environment")))
            assertEquals(42L, resultAttributes.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }
    }

    @Nested
    @DisplayName("Parent-Child Relationship Tests")
    inner class ParentChildRelationshipTests {

        @Test
        fun `should remove child and grandchild spans when parent is not sampled`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = {
                    if (it.name == "parent") {
                        SamplingResult(sample = false)
                    } else {
                        SamplingResult(sample = true)
                    }
                }
            )

            val parentSpan = createMockSpan("parent", "parent-id")
            val childSpan = createMockSpan("child", "child-id", parentSpanId = "parent-id")
            val grandchildSpan = createMockSpan("grandchild", "grandchild-id", parentSpanId = "child-id")
            val unrelatedSpan = createMockSpan("unrelated", "unrelated-id")

            val spans = listOf(parentSpan, childSpan, grandchildSpan, unrelatedSpan)

            val result = sampleSpans(spans, sampler)

            assertEquals(1, result.size)
            assertEquals(unrelatedSpan, result[0])
        }

        @Test
        fun `should keep child spans when parent is sampled`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = { SamplingResult(sample = true) }
            )

            val parentSpan = createMockSpan("parent", "parent-id")
            val childSpan1 = createMockSpan("child1", "child1-id", parentSpanId = "parent-id")
            val childSpan2 = createMockSpan("child2", "child2-id", parentSpanId = "parent-id")

            val spans = listOf(parentSpan, childSpan1, childSpan2)

            val result = sampleSpans(spans, sampler)

            assertEquals(3, result.size)
            assertTrue(result.containsAll(spans))
        }

        @Test
        fun `should remove child spans even if parent is sampled but child is not`() {
            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = {
                    if (it.name == "child") {
                        SamplingResult(sample = false)
                    } else {
                        SamplingResult(sample = true)
                    }
                }
            )

            val parentSpan = createMockSpan("parent", "parent-id")
            val childSpan = createMockSpan("child", "child-id", parentSpanId = "parent-id")
            val grandchildSpan = createMockSpan("grandchild", "grandchild-id", parentSpanId = "child-id")

            val spans = listOf(parentSpan, childSpan, grandchildSpan)

            val result = sampleSpans(spans, sampler)

            assertEquals(1, result.size)
            assertEquals(parentSpan, result[0])
        }

        @Test
        fun `should handle complex span hierarchy with mixed sampling`() {
            // Create a complex hierarchy:
            // parent1 (sampled) -> child1 (not sampled) -> grandchild1 (sampled)
            // parent2 (not sampled) -> child2 (sampled) -> grandchild2 (sampled)
            // unrelated (sampled)

            val sampler = MockExportSampler(
                isSamplingEnabled = { true },
                sampleSpan = {
                    when (it.name) {
                        "parent1", "grandchild1", "child2", "grandchild2", "unrelated" -> SamplingResult(sample = true)
                        "child1", "parent2" -> SamplingResult(sample = false)
                        else -> SamplingResult(sample = false)
                    }
                }
            )

            val parent1 = createMockSpan("parent1", "parent1-id")
            val child1 = createMockSpan("child1", "child1-id", parentSpanId = "parent1-id")
            val grandchild1 = createMockSpan("grandchild1", "grandchild1-id", parentSpanId = "child1-id")

            val parent2 = createMockSpan("parent2", "parent2-id")
            val child2 = createMockSpan("child2", "child2-id", parentSpanId = "parent2-id")
            val grandchild2 = createMockSpan("grandchild2", "grandchild2-id", parentSpanId = "child2-id")

            val unrelated = createMockSpan("unrelated", "unrelated-id")

            val spans = listOf(parent1, child1, grandchild1, parent2, child2, grandchild2, unrelated)

            val result = sampleSpans(spans, sampler)

            // child1 and grandchild1 should be removed because child1 is not sampled
            // parent2, child2, grandchild2 should all be removed because parent2 is not sampled
            assertEquals(2, result.size)
            assertTrue(result.contains(parent1))
            assertTrue(result.contains(unrelated))
        }
    }

    private fun createMockSpan(
        name: String,
        spanId: String,
        parentSpanId: String? = null,
        traceId: String = "trace-id",
        attributes: Attributes = Attributes.empty(),
        instrumentationScope: InstrumentationScopeInfo? = null
    ): SpanData {
        val spanContext = mockk<SpanContext>().apply {
            every { getSpanId() } returns spanId
        }

        val parentSpanContext = if (parentSpanId != null) {
            mockk<SpanContext>().apply {
                every { getSpanId() } returns parentSpanId
            }
        } else null

        return mockk<SpanData>().apply {
            every { getName() } returns name
            every { getSpanContext() } returns spanContext
            every { getParentSpanContext() } returns parentSpanContext
            every { getTraceId() } returns traceId
            every { getSpanId() } returns spanId
            every { getParentSpanId() } returns parentSpanId
            every { getAttributes() } returns attributes
            every { instrumentationScopeInfo } returns instrumentationScope
        }
    }
}
