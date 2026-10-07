package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SpanExporter} that applies sampling logic before delegating to another exporter.
 *
 * <p>Sampled spans keep the sampling-ratio attributes returned by {@link CustomSampler}.
 * Children of a dropped span are dropped with it, matching the other LaunchDarkly SDKs.</p>
 */
public final class SamplingTraceExporter implements SpanExporter {

    private final SpanExporter delegate;
    private final CustomSampler sampler;

    public SamplingTraceExporter(SpanExporter delegate, CustomSampler sampler) {
        this.delegate = delegate;
        this.sampler = sampler;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> sampled = sampleSpans(spans);
        if (sampled.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        return delegate.export(sampled);
    }

    private List<SpanData> sampleSpans(Collection<SpanData> spans) {
        if (!sampler.isSamplingEnabled()) {
            return new ArrayList<>(spans);
        }

        ArrayDeque<String> omittedSpanIds = new ArrayDeque<>();
        Map<String, SpanData> spanById = new HashMap<>();
        Map<String, List<String>> childrenByParentId = new HashMap<>();

        for (SpanData span : spans) {
            String spanId = span.getSpanId();
            SpanContext parent = span.getParentSpanContext();
            if (parent != null && parent.isValid()) {
                childrenByParentId
                        .computeIfAbsent(parent.getSpanId(), ignored -> new ArrayList<>())
                        .add(spanId);
            }

            CustomSampler.SamplingResult result = sampler.sampleSpan(span);
            if (result.isSampled()) {
                Attributes extra = result.getAttributes();
                if (extra != null && !extra.isEmpty()) {
                    spanById.put(spanId, new AttributedSpanData(span, extra));
                } else {
                    spanById.put(spanId, span);
                }
            } else {
                omittedSpanIds.add(spanId);
            }
        }

        while (!omittedSpanIds.isEmpty()) {
            String spanId = omittedSpanIds.removeFirst();
            List<String> children = childrenByParentId.get(spanId);
            if (children == null) {
                continue;
            }
            for (String childId : children) {
                spanById.remove(childId);
                omittedSpanIds.add(childId);
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

    /**
     * SpanData view that merges sampling attributes onto the original span.
     */
    @SuppressWarnings("deprecation")
    private static final class AttributedSpanData implements SpanData {
        private final SpanData delegate;
        private final Attributes attributes;

        AttributedSpanData(SpanData delegate, Attributes extra) {
            this.delegate = delegate;
            this.attributes = Attributes.builder()
                    .putAll(delegate.getAttributes())
                    .putAll(extra)
                    .build();
        }

        @Override
        public String getName() { return delegate.getName(); }

        @Override
        public SpanKind getKind() { return delegate.getKind(); }

        @Override
        public SpanContext getSpanContext() { return delegate.getSpanContext(); }

        @Override
        public SpanContext getParentSpanContext() { return delegate.getParentSpanContext(); }

        @Override
        public StatusData getStatus() { return delegate.getStatus(); }

        @Override
        public long getStartEpochNanos() { return delegate.getStartEpochNanos(); }

        @Override
        public Attributes getAttributes() { return attributes; }

        @Override
        public List<EventData> getEvents() { return delegate.getEvents(); }

        @Override
        public List<LinkData> getLinks() { return delegate.getLinks(); }

        @Override
        public long getEndEpochNanos() { return delegate.getEndEpochNanos(); }

        @Override
        public boolean hasEnded() { return delegate.hasEnded(); }

        @Override
        public int getTotalRecordedEvents() { return delegate.getTotalRecordedEvents(); }

        @Override
        public int getTotalRecordedLinks() { return delegate.getTotalRecordedLinks(); }

        @Override
        public int getTotalAttributeCount() { return attributes.size(); }

        @Override
        public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
            return delegate.getInstrumentationLibraryInfo();
        }

        @Override
        public Resource getResource() { return delegate.getResource(); }
    }
}
