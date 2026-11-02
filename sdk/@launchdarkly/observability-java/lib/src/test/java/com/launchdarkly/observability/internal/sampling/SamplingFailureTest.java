package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sampling runs on application threads (logs) and in the export path
 * (spans). A failure there must keep the telemetry instead of throwing into
 * the caller or losing the batch.
 */
class SamplingFailureTest {

    @Test
    void logSamplingThatOverflowsTheStackKeepsTheRecordAndDoesNotThrow() {
        // (a|b)* recurses once per character in java.util.regex.
        CustomSampler sampler = new CustomSampler(ratio -> false);
        sampler.setConfig(new SamplingConfig(Collections.emptyList(), List.of(
                new SamplingConfig.LogSamplingConfig(
                        SamplingConfig.MatchConfig.ofRegex("^(a|b)*c"), null, Collections.emptyList(), 0))));
        InMemoryLogRecordExporter exported = InMemoryLogRecordExporter.create();
        SdkLoggerProvider provider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new SamplingLogProcessor(SimpleLogRecordProcessor.create(exported), sampler))
                .build();
        Logger logger = provider.get("test");

        String longMessage = "ab".repeat(200_000);
        assertDoesNotThrow(() -> logger.logRecordBuilder().setBody(longMessage).emit());
        logger.logRecordBuilder().setBody("abc").emit();

        assertEquals(1, exported.getFinishedLogRecordItems().stream()
                .filter(r -> r.getBodyValue().asString().equals(longMessage)).count(),
                "a record the sampler could not evaluate is exported unsampled");
        assertEquals(1, exported.getFinishedLogRecordItems().size(), "a matching record is still dropped");
        provider.close();
    }

    @Test
    void spanSamplingFailureExportsTheBatchUnsampled() {
        CustomSampler sampler = new CustomSampler(ratio -> {
            throw new IllegalStateException("sampler bug");
        });
        sampler.setConfig(new SamplingConfig(List.of(new SamplingConfig.SpanSamplingConfig(
                SamplingConfig.MatchConfig.ofValue("checkout"), Collections.emptyList(), Collections.emptyList(), 2)),
                Collections.emptyList()));
        InMemorySpanExporter exported = InMemorySpanExporter.create();
        SamplingTraceExporter exporter = new SamplingTraceExporter(exported, sampler);

        assertDoesNotThrow(() -> exporter.export(List.of(span("checkout"), span("other"))));

        assertEquals(2, exported.getFinishedSpanItems().size());
    }

    private static SpanData span(String name) {
        return TestSpanData.builder()
                .setName(name)
                .setKind(SpanKind.INTERNAL)
                .setSpanContext(SpanContext.create(
                        TraceId.fromLongs(0, 1), SpanId.fromLong(name.hashCode() | 1L),
                        TraceFlags.getSampled(), TraceState.getDefault()))
                .setStatus(StatusData.unset())
                .setHasEnded(true)
                .setStartEpochNanos(0)
                .setEndEpochNanos(1)
                .setAttributes(Attributes.empty())
                .build();
    }
}
