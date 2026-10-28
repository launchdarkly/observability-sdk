package com.launchdarkly.observability.internal;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.server.integrations.Hook;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Records flag evaluations on the plugin's own tracer.
 *
 * <p>Emits the same data as {@code TracingHook} from
 * {@code launchdarkly-java-server-sdk-otel}: a span per evaluation and a
 * {@code feature_flag} event on the caller's current span. That hook resolves
 * its tracer from {@code GlobalOpenTelemetry} on every evaluation, so when the
 * global is already claimed (the OpenTelemetry Java agent, a library calling
 * {@code GlobalOpenTelemetry.get()} first, or manual start) its spans never
 * reach LaunchDarkly. This hook asks {@link OtelManager} instead, which always
 * holds the plugin's SDK whether or not it is also the global.</p>
 */
public final class EvaluationTracingHook extends Hook {
    static final String HOOK_NAME = "LaunchDarkly Tracing Hook";
    static final String INSTRUMENTATION_NAME = "launchdarkly-client";
    static final String DATA_KEY_SPAN = "variationSpan";
    static final String EVENT_NAME = "feature_flag";

    static final String PROVIDER_NAME = "LaunchDarkly";
    static final String FEATURE_FLAG_PROVIDER_NAME = "feature_flag.provider.name";
    static final String FEATURE_FLAG_KEY = "feature_flag.key";
    static final String FEATURE_FLAG_VALUE = "feature_flag.result.value";
    static final String FEATURE_FLAG_CONTEXT_ID = "feature_flag.context.id";
    static final String FEATURE_FLAG_VARIATION_INDEX = "feature_flag.result.variationIndex";
    static final String FEATURE_FLAG_IN_EXPERIMENT = "feature_flag.result.reason.inExperiment";

    private final boolean withSpans;
    private final boolean withValue;

    public EvaluationTracingHook(boolean withSpans, boolean withValue) {
        super(HOOK_NAME);
        this.withSpans = withSpans;
        this.withValue = withValue;
    }

    @Override
    public Map<String, Object> beforeEvaluation(EvaluationSeriesContext seriesContext, Map<String, Object> data) {
        if (!withSpans) {
            return data;
        }
        Span span = OtelManager.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder(seriesContext.method)
                .setParent(Context.current())
                .setAllAttributes(baseAttributes(seriesContext).build())
                .startSpan();
        Map<String, Object> next = new HashMap<>(data);
        next.put(DATA_KEY_SPAN, span);
        return next;
    }

    @Override
    public Map<String, Object> afterEvaluation(
            EvaluationSeriesContext seriesContext,
            Map<String, Object> data,
            EvaluationDetail<LDValue> detail
    ) {
        Object span = data.get(DATA_KEY_SPAN);
        if (span instanceof Span) {
            ((Span) span).end();
        }

        AttributesBuilder attributes = baseAttributes(seriesContext);
        if (withValue) {
            attributes.put(FEATURE_FLAG_VALUE, detail.getValue().toJsonString());
        }
        if (detail.getReason() != null && detail.getReason().isInExperiment()) {
            attributes.put(FEATURE_FLAG_IN_EXPERIMENT, true);
        }
        if (detail.getVariationIndex() != EvaluationDetail.NO_VARIATION) {
            attributes.put(FEATURE_FLAG_VARIATION_INDEX, (long) detail.getVariationIndex());
        }
        Span.current().addEvent(EVENT_NAME, attributes.build());
        return data;
    }

    private static AttributesBuilder baseAttributes(EvaluationSeriesContext seriesContext) {
        return Attributes.builder()
                .put(FEATURE_FLAG_KEY, seriesContext.flagKey)
                .put(FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
                .put(FEATURE_FLAG_CONTEXT_ID, seriesContext.context.getFullyQualifiedKey());
    }
}
