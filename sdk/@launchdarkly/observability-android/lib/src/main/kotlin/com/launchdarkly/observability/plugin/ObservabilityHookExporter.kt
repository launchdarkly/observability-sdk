package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure data-sending logic for observability hook tracing.
 *
 * Manages span lifecycle (start/end) and identify logging.
 * Takes only simple JVM types — no Hook interface, no SDK-specific types.
 * Both [ObservabilityHook] (native Android SDK) and [ObservabilityHookProxy] (C# bridge)
 * delegate here so the tracing logic is written exactly once.
 */
internal class ObservabilityHookExporter(
    private val withSpans: Boolean,
    private val withValue: Boolean,
    private val tracerProvider: (() -> Tracer?),
    maxInFlightSpans: Int = 1024
) {
    private val spans = ConcurrentHashMap<String, Span>(maxInFlightSpans)

    private fun getTracer(): Tracer {
        return tracerProvider.invoke() ?: GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME)
    }

    // -- Evaluation --

    fun beforeEvaluation(evaluationId: String, flagKey: String, contextKey: String) {
        if (!withSpans) return

        val tracer = getTracer()
        val span = tracer.spanBuilder(FEATURE_FLAG_SPAN_NAME)
            .setAllAttributes(
                Attributes.builder()
                    .put(SEMCONV_FEATURE_FLAG_KEY, flagKey)
                    .put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
                    .put(SEMCONV_FEATURE_FLAG_CONTEXT_ID, contextKey)
                    .build()
            )
            .startSpan()

        val evicted = spans.put(evaluationId, span)
        evicted?.end()
    }

    fun afterEvaluation(
        evaluationId: String,
        flagKey: String,
        contextKey: String,
        valueJson: String?,
        variationIndex: Int?,
        inExperiment: Boolean?
    ) {
        val span = spans.remove(evaluationId) ?: return

        val attrBuilder = Attributes.builder()
        attrBuilder.put(SEMCONV_FEATURE_FLAG_KEY, flagKey)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_CONTEXT_ID, contextKey)

        inExperiment?.let {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT, it)
        }

        if (withValue && valueJson != null) {
            attrBuilder.put(SEMCONV_FEATURE_FLAG_RESULT_VALUE, valueJson)
        }

        variationIndex?.let {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX, it.toLong())
        }

        span.addEvent(FEATURE_FLAG_EVENT_NAME, attrBuilder.build())
        span.end()
    }

    // -- Identify --

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        if (!completed) return

        val attrBuilder = Attributes.builder()
        for ((k, v) in contextKeys) {
            attrBuilder.put(AttributeKey.stringKey(k), v)
        }
        attrBuilder.put(AttributeKey.stringKey("key"), canonicalKey)
        attrBuilder.put(AttributeKey.stringKey("canonicalKey"), canonicalKey)
        attrBuilder.put(AttributeKey.stringKey(IDENTIFY_RESULT_STATUS), "completed")

        LDObserve.recordLog("LD.identify", Severity.INFO, attrBuilder.build())
    }

    companion object {
        const val PROVIDER_NAME = "LaunchDarkly"
        const val INSTRUMENTATION_NAME = "com.launchdarkly.observability"
        const val FEATURE_FLAG_SPAN_NAME = "evaluation"
        const val FEATURE_FLAG_EVENT_NAME = "feature_flag"
        const val SEMCONV_FEATURE_FLAG_KEY = "feature_flag.key"
        const val SEMCONV_FEATURE_FLAG_PROVIDER_NAME = "feature_flag.provider.name"
        const val SEMCONV_FEATURE_FLAG_CONTEXT_ID = "feature_flag.context.id"
        const val SEMCONV_FEATURE_FLAG_RESULT_VALUE = "feature_flag.result.value"
        const val CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX = "feature_flag.result.variationIndex"
        const val CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT = "feature_flag.result.reason.inExperiment"
        const val IDENTIFY_RESULT_STATUS = "identify.result.status"
        const val DATA_KEY_EVAL_ID = "evaluationId"
    }
}
