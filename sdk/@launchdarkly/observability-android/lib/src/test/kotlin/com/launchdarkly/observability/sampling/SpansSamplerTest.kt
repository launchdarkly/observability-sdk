package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.client.InstrumentationManager
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpansSamplerTest {

    @Test
    fun `should record non-error spans when normal sampling enabled`() {
        val sampler = SpansSampler(allowNormalSpans = true, allowErrorSpans = false)

        val result = sample(sampler, spanName = "custom.span")

        assertEquals(SamplingDecision.RECORD_AND_SAMPLE, result.decision)
    }

    @Test
    fun `should drop non-error spans when normal sampling disabled`() {
        val sampler = SpansSampler(allowNormalSpans = false, allowErrorSpans = true)

        val result = sample(sampler, spanName = "custom.span")

        assertEquals(SamplingDecision.DROP, result.decision)
    }

    @Test
    fun `should record error spans when error sampling enabled`() {
        val sampler = SpansSampler(allowNormalSpans = false, allowErrorSpans = true)

        val result = sample(sampler, spanName = InstrumentationManager.ERROR_SPAN_NAME)

        assertEquals(SamplingDecision.RECORD_AND_SAMPLE, result.decision)
    }

    @Test
    fun `should drop error spans when error sampling disabled`() {
        val sampler = SpansSampler(allowNormalSpans = true, allowErrorSpans = false)

        val result = sample(sampler, spanName = InstrumentationManager.ERROR_SPAN_NAME)

        assertEquals(SamplingDecision.DROP, result.decision)
    }

    @Test
    fun `description should match public contract`() {
        val sampler = SpansSampler(allowNormalSpans = true, allowErrorSpans = true)

        assertEquals("LaunchDarklySpansSampler", sampler.description)
    }

    private fun sample(sampler: SpansSampler, spanName: String) = sampler.shouldSample(
        Context.root(),
        "trace-id",
        spanName,
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList()
    )
}
