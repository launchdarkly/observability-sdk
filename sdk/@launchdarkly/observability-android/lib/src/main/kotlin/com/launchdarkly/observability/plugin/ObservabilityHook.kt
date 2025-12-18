package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope

/**
 * This class is a hook implementation for recording flag evaluation and identify events
 * on spans.
 */
class ObservabilityHook

/**
 * Creates an [ObservabilityHook]
 *
 * @param withSpans will include child spans for the various hook series when they happen
 * @param withValue will include the value of the feature flag in the recorded evaluation events
 * @param tracerProvider optional tracer provider function, if not provided will use GlobalOpenTelemetry
 */
internal constructor(
    private val withSpans: Boolean, 
    private val withValue: Boolean,
    private val tracerProvider: (() -> Tracer?)
) : Hook(HOOK_NAME) {

    override fun beforeEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        val tracer = tracerProvider.invoke() ?: GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME)
        return beforeEvaluationInternal(
            tracer,
            seriesContext,
            seriesData
        )
    }

    private fun beforeEvaluationInternal(
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
        retSeriesData[DATA_KEY_FEATURE_FLAG_SPAN] = span
        return retSeriesData
    }

    override fun afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>,
        evaluationDetail: EvaluationDetail<LDValue>
    ): Map<String, Any> {
        val value = seriesData[DATA_KEY_FEATURE_FLAG_SPAN]
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
        Span.current().addEvent(FEATURE_FLAG_EVENT_NAME, attrBuilder.build())
        return seriesData
    }

    override fun beforeIdentify(
        seriesContext: IdentifySeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        val tracer = tracerProvider.invoke() ?: GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME)
        return beforeIdentifyInternal(
            tracer,
            seriesContext,
            seriesData
        )
    }

    private fun beforeIdentifyInternal(
        tracer: Tracer,
        seriesContext: IdentifySeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        if (!withSpans) {
            return seriesData
        }

        val spanBuilder = tracer.spanBuilder(SEMCONV_IDENTIFY_SPAN_NAME)
            .setParent(Context.current().with(Span.current()))
        val span = spanBuilder.startSpan()
        span.addEvent(IDENTIFY_EVENT_START)

        val attrBuilder = Attributes.builder()
        attrBuilder.put(SEMCONV_IDENTIFY_CONTEXT_ID, seriesContext.context.fullyQualifiedKey)
        seriesContext.timeout?.let {
            attrBuilder.put(SEMCONV_IDENTIFY_TIMEOUT, it.toLong())
        }
        span.setAllAttributes(attrBuilder.build())

        return HashMap(seriesData).apply {
            this[DATA_KEY_IDENTIFY_SPAN] = span
            this[DATA_KEY_IDENTIFY_SCOPE] = span.makeCurrent()
        }
    }

    override fun afterIdentify(
        seriesContext: IdentifySeriesContext,
        seriesData: Map<String, Any>,
        result: IdentifySeriesResult
    ): Map<String, Any> {
        val span = seriesData[DATA_KEY_IDENTIFY_SPAN] as? Span
        val scope = seriesData[DATA_KEY_IDENTIFY_SCOPE] as? Scope

        span?.let {
            val attrBuilder = Attributes.builder()
            attrBuilder.put(SEMCONV_IDENTIFY_EVENT_RESULT_VALUE, result.status.name)

            it.addEvent(IDENTIFY_EVENT_FINISH, attrBuilder.build())
            it.end()
        }

        // Closes the current span scope and restores the previous span context
        scope?.close()

        return seriesData
    }

    companion object {
        const val PROVIDER_NAME: String = "LaunchDarkly"
        const val HOOK_NAME: String = "Observability Hook"
        const val INSTRUMENTATION_NAME: String = "com.launchdarkly.observability"
        const val DATA_KEY_FEATURE_FLAG_SPAN: String = "variationSpan"
        const val FEATURE_FLAG_EVENT_NAME: String = "feature_flag"
        const val SEMCONV_FEATURE_FLAG_CONTEXT_ID: String = "feature_flag.context.id"
        const val SEMCONV_FEATURE_FLAG_PROVIDER_NAME: String = "feature_flag.provider.name"
        const val SEMCONV_FEATURE_FLAG_KEY: String = "feature_flag.key"
        const val SEMCONV_FEATURE_FLAG_RESULT_VALUE: String = "feature_flag.result.value"
        const val CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX: String = "feature_flag.result.variationIndex"
        const val CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT: String = "feature_flag.result.reason.inExperiment"
        const val SEMCONV_IDENTIFY_SPAN_NAME: String = "Identify"
        const val SEMCONV_IDENTIFY_CONTEXT_ID: String = "identify.context.id"
        const val SEMCONV_IDENTIFY_TIMEOUT: String = "identify.timeout"
        const val IDENTIFY_EVENT_START: String = "start"
        const val IDENTIFY_EVENT_FINISH: String = "finish"
        const val SEMCONV_IDENTIFY_EVENT_RESULT_VALUE: String = "result"
        const val DATA_KEY_IDENTIFY_SPAN: String = "identifySpan"
        const val DATA_KEY_IDENTIFY_SCOPE: String = "identifyScope"
    }
}
