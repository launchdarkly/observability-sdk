package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplingTraceExporterTest {

    @Test
    void exportKeepsSamplingRatioOnSpan() {
        CustomSampler sampler = new CustomSampler(ratio -> true);
        sampler.setConfig(new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofValue("checkout"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        5
                )),
                Collections.emptyList()
        ));
        CapturingExporter capture = new CapturingExporter();
        SamplingTraceExporter exporter = new SamplingTraceExporter(capture, sampler);

        Attributes original = Attributes.of(AttributeKey.stringKey("http.route"), "/checkout");
        exporter.export(List.of(span("checkout", spanId('1'), null, original)));

        assertEquals(1, capture.exported.size());
        Attributes attributes = capture.exported.get(0).getAttributes();
        assertEquals("/checkout", attributes.get(AttributeKey.stringKey("http.route")));
        assertEquals(5L, attributes.get(AttributeKey.longKey("launchdarkly.sampling.ratio")));
    }

    @Test
    void exportDropsChildrenOfUnsampledSpans() {
        CustomSampler sampler = new CustomSampler(ratio -> false);
        sampler.setConfig(new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofValue("health-check"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        1
                )),
                Collections.emptyList()
        ));
        CapturingExporter capture = new CapturingExporter();
        SamplingTraceExporter exporter = new SamplingTraceExporter(capture, sampler);

        SpanContext parent = context(spanId('1'));
        SpanData root = span("health-check", spanId('1'), null, Attributes.empty());
        SpanData child = span("db-query", spanId('2'), parent, Attributes.empty());

        exporter.export(List.of(root, child));

        assertTrue(capture.exported.isEmpty());
    }

    private static SpanData span(String name, String spanId, SpanContext parent, Attributes attributes) {
        TestSpanData.Builder builder = TestSpanData.builder()
                .setName(name)
                .setKind(SpanKind.INTERNAL)
                .setSpanContext(context(spanId))
                .setStatus(StatusData.unset())
                .setHasEnded(true)
                .setStartEpochNanos(0)
                .setEndEpochNanos(1)
                .setAttributes(attributes);
        if (parent != null) {
            builder.setParentSpanContext(parent);
        }
        return builder.build();
    }

    private static SpanContext context(String spanId) {
        return SpanContext.create(traceId(), spanId, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static String traceId() {
        return "0".repeat(31) + "a";
    }

    private static String spanId(char tail) {
        return "0".repeat(15) + tail;
    }

    private static final class CapturingExporter implements SpanExporter {
        private final List<SpanData> exported = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exported.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
