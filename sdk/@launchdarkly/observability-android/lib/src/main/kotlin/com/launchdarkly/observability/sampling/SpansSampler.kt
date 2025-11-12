package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.client.InstrumentationManager
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult

/**
 * OpenTelemetry sampler that differentiates between normal spans and error spans.
 *
 * The constructor flags determine whether each span category is recorded or not.
 */
class SpansSampler(
    private val allowNormalSpans: Boolean,
    private val allowErrorSpans: Boolean
) : Sampler {

    override fun shouldSample(
        parentContext: Context,
        traceId: String,
        spanName: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: List<LinkData>
    ): SamplingResult {
        return if (shouldRecordSpan(spanName)) {
            SamplingResult.recordAndSample()
        } else {
            SamplingResult.drop()
        }
    }

    override fun getDescription(): String = "LaunchDarklySpansSampler"

    private fun shouldRecordSpan(spanName: String): Boolean {
        val isErrorSpan = spanName == InstrumentationManager.ERROR_SPAN_NAME

        return when {
            isErrorSpan -> allowErrorSpans
            else -> allowNormalSpans
        }
    }
}
