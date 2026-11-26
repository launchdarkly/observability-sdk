package com.launchdarkly.observability.internal;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.client.integrations.Hook;
import com.launchdarkly.sdk.client.integrations.IdentifySeriesContext;
import com.launchdarkly.sdk.client.integrations.IdentifySeriesResult;
import com.launchdarkly.sdk.client.integrations.TrackSeriesContext;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The SDK hook: each flag evaluation becomes an {@code evaluation} span, each completed identify an
 * {@code LD.identify} log, and each {@code track} call a {@code track} span, as in the Android
 * plugin's {@code ObservabilityHook}. The plugin wraps it in the SDK's {@code DedupingHook}, so a flag
 * evaluated again with the same result is reported once per window.
 * <p>
 * The evaluation span travels to {@code afterEvaluation} in the series data, so it never outlives the
 * evaluation, unlike the map of spans by evaluation id that Android keeps.
 */
public final class ObservabilityHook extends Hook {
  static final String HOOK_NAME = "Observability Hook";
  static final String DATA_KEY_SPAN = "com.launchdarkly.observability.evaluationSpan";

  private final Supplier<ObservabilityService> service;

  /**
   * @param service the service to report to, or a supplier of null while there is none
   */
  public ObservabilityHook(Supplier<ObservabilityService> service) {
    super(HOOK_NAME);
    this.service = service;
  }

  @Override
  public Map<String, Object> beforeEvaluation(EvaluationSeriesContext seriesContext, Map<String, Object> seriesData) {
    ObservabilityService s = service.get();
    if (s == null) {
      return seriesData;
    }
    Span span = s.beforeEvaluation(seriesContext.flagKey, seriesContext.context.getFullyQualifiedKey());
    Map<String, Object> data = new HashMap<>(seriesData);
    data.put(DATA_KEY_SPAN, span);
    return data;
  }

  @Override
  public Map<String, Object> afterEvaluation(EvaluationSeriesContext seriesContext, Map<String, Object> seriesData,
      EvaluationDetail<LDValue> evaluationDetail) {
    Object span = seriesData.get(DATA_KEY_SPAN);
    ObservabilityService s = service.get();
    if (!(span instanceof Span) || s == null) {
      return seriesData;
    }
    EvaluationReason reason = evaluationDetail.getReason();
    s.afterEvaluation((Span) span, seriesContext.flagKey, seriesContext.context.getFullyQualifiedKey(),
        evaluationDetail.getValue().toJsonString(),
        evaluationDetail.getVariationIndex() == EvaluationDetail.NO_VARIATION
            ? null : evaluationDetail.getVariationIndex(),
        reason == null ? null : reason.isInExperiment());
    return seriesData;
  }

  @Override
  public Map<String, Object> afterIdentify(IdentifySeriesContext seriesContext, Map<String, Object> seriesData,
      IdentifySeriesResult result) {
    ObservabilityService s = service.get();
    if (s != null && result.status == IdentifySeriesResult.IdentifySeriesStatus.COMPLETED) {
      s.afterIdentify(contextKeys(seriesContext.context), seriesContext.context.getFullyQualifiedKey());
    }
    return seriesData;
  }

  @Override
  public void afterTrack(TrackSeriesContext seriesContext) {
    ObservabilityService s = service.get();
    if (s != null) {
      s.track(seriesContext.key, seriesContext.metricValue, AttributeConversion.fromLDValue(seriesContext.data),
          contextKeys(seriesContext.context));
    }
  }

  /** Each context kind and its key, as attributes. */
  static Attributes contextKeys(LDContext context) {
    AttributesBuilder builder = Attributes.builder();
    if (context.isMultiple()) {
      for (int i = 0; i < context.getIndividualContextCount(); i++) {
        LDContext individual = context.getIndividualContext(i);
        builder.put(individual.getKind().toString(), individual.getKey());
      }
    } else {
      builder.put(context.getKind().toString(), context.getKey());
    }
    return builder.build();
  }
}
