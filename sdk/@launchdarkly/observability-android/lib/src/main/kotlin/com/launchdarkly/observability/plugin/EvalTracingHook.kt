package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import com.launchdarkly.sdk.android.integrations.Hook
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

class EvalTracingHook
/**
 * Creates a [EvalTracingHook]
 *
 * @param withSpans will include child spans for the various hook series when they happen
 * @param withValue will include the value of the feature flag in the recorded evaluation events
 */ internal constructor(private val withSpans: Boolean, private val withValue: Boolean) :
    Hook(HOOK_NAME) {
    // TODO: O11Y-374: add before/after identify support
    override fun beforeEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        return beforeEvaluationInternal(
            GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME),
            seriesContext,
            seriesData
        )
    }

    fun beforeEvaluationInternal(
        tracer: Tracer,
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        if (!withSpans) {
            return seriesData
        }

        val builder = tracer.spanBuilder(seriesContext.method)
            .setParent(Context.current().with(Span.current()))

        val attrBuilder = Attributes.builder()
        attrBuilder.put(SEMCONV_FEATURE_FLAG_KEY, seriesContext.flagKey)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
        builder.setAllAttributes(attrBuilder.build())
        val span = builder.startSpan()
        val retSeriesData: MutableMap<String, Any> = HashMap(seriesData)
        retSeriesData[DATA_KEY_SPAN] = span
        return retSeriesData
    }

    override fun afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>,
        evaluationDetail: EvaluationDetail<LDValue>
    ): Map<String, Any> {
        val value = seriesData[DATA_KEY_SPAN]
        if (value is Span) {
            value.end()
        }

        val attrBuilder = Attributes.builder()
        attrBuilder.put(SEMCONV_FEATURE_FLAG_KEY, seriesContext.flagKey)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)

        evaluationDetail.reason?.isInExperiment?.let {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT, it)
        }

        attrBuilder.put(SEMCONV_FEATURE_FLAG_CONTEXT_ID, seriesContext.context.fullyQualifiedKey)
        if (withValue) {
            attrBuilder.put(SEMCONV_FEATURE_FLAG_RESULT_VALUE, evaluationDetail.value.toJsonString())
        }

        if (evaluationDetail.variationIndex != EvaluationDetail.NO_VARIATION) {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX, evaluationDetail.variationIndex.toLong())
        }

        // Here we make best effort the log the event and let the library handle the "no current span" case; which at the
        // time of writing this, it does handle.
        Span.current().addEvent(EVENT_NAME, attrBuilder.build())
        return seriesData
    }

    companion object {
        const val PROVIDER_NAME: String = "LaunchDarkly"
        const val HOOK_NAME: String = "LaunchDarkly Evaluation Tracing Hook"
        const val INSTRUMENTATION_NAME: String = "com.launchdarkly.observability"
        const val DATA_KEY_SPAN: String = "variationSpan"
        const val EVENT_NAME: String = "feature_flag"
        const val SEMCONV_FEATURE_FLAG_CONTEXT_ID: String = "feature_flag.context.id"
        const val SEMCONV_FEATURE_FLAG_PROVIDER_NAME: String = "feature_flag.provider.name"
        const val SEMCONV_FEATURE_FLAG_KEY: String = "feature_flag.key"
        const val SEMCONV_FEATURE_FLAG_RESULT_VALUE: String = "feature_flag.result.value"
        const val CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX: String = "feature_flag.result.variationIndex"
        const val CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT: String = "feature_flag.result.reason.inExperiment"
    }
}
