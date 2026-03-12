package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@link SpanExporter} that applies sampling logic before delegating to another exporter.
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
        if (!sampler.isSamplingEnabled()) {
            return delegate.export(spans);
        }
        List<SpanData> sampled = new ArrayList<>();
        for (SpanData span : spans) {
            CustomSampler.SamplingResult result = sampler.sampleSpan(span);
            if (result.isSampled()) {
                sampled.add(span);
            }
        }
        if (sampled.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        return delegate.export(sampled);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }
}
