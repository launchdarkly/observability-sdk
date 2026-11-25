package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.logging.LDLogger;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies sampling to each batch of spans before exporting it. A kept span carries the matching
 * rule's ratio. The children of a dropped span are dropped with it, as in the other LaunchDarkly
 * SDKs. Ported from the server-side Java plugin.
 */
public final class SamplingTraceExporter implements SpanExporter {
  private final SpanExporter delegate;
  private final CustomSampler sampler;
  private final LDLogger logger;
  private final AtomicBoolean failureLogged = new AtomicBoolean();

  public SamplingTraceExporter(SpanExporter delegate, CustomSampler sampler, LDLogger logger) {
    this.delegate = delegate;
    this.sampler = sampler;
    this.logger = logger;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    List<SpanData> sampled;
    try {
      sampled = sampler.isSamplingEnabled() ? sampleSpans(spans) : new ArrayList<>(spans);
    } catch (RuntimeException | StackOverflowError e) {
      // Losing the batch would be worse than exporting it unsampled.
      if (failureLogged.compareAndSet(false, true)) {
        logger.warn("Span sampling failed, so the batch is exported unsampled: {}", e.toString());
      }
      sampled = new ArrayList<>(spans);
    }
    if (sampled.isEmpty()) {
      return CompletableResultCode.ofSuccess();
    }
    return delegate.export(sampled);
  }

  private List<SpanData> sampleSpans(Collection<SpanData> spans) {
    ArrayDeque<String> omittedSpanIds = new ArrayDeque<>();
    // Keeps the batch's order, so a parent stays ahead of its children.
    Map<String, SpanData> spanById = new LinkedHashMap<>();
    Map<String, List<String>> childrenByParentId = new HashMap<>();

    for (SpanData span : spans) {
      String spanId = span.getSpanId();
      SpanContext parent = span.getParentSpanContext();
      if (parent.isValid()) {
        childrenByParentId.computeIfAbsent(parent.getSpanId(), ignored -> new ArrayList<>()).add(spanId);
      }
      CustomSampler.SamplingResult result = sampler.sampleSpan(span);
      if (!result.isSampled()) {
        omittedSpanIds.add(spanId);
      } else if (result.getAttributes() != null) {
        spanById.put(spanId, new AttributedSpanData(span, result.getAttributes()));
      } else {
        spanById.put(spanId, span);
      }
    }

    while (!omittedSpanIds.isEmpty()) {
      List<String> children = childrenByParentId.get(omittedSpanIds.removeFirst());
      if (children != null) {
        for (String childId : children) {
          spanById.remove(childId);
          omittedSpanIds.add(childId);
        }
      }
    }
    return new ArrayList<>(spanById.values());
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    return delegate.shutdown();
  }

  /** A span with the sampling attributes added to its own. */
  private static final class AttributedSpanData extends DelegatingSpanData {
    private final Attributes attributes;

    AttributedSpanData(SpanData delegate, Attributes extra) {
      super(delegate);
      this.attributes = delegate.getAttributes().toBuilder().putAll(extra).build();
    }

    @Override
    public Attributes getAttributes() {
      return attributes;
    }

    @Override
    public int getTotalAttributeCount() {
      return attributes.size();
    }
  }
}
