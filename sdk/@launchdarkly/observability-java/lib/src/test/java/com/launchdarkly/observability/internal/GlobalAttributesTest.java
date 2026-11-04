package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalAttributesTest {

    private static final AttributeKey<String> TENANT = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> REGION = AttributeKey.stringKey("region");
    private static final AttributeKey<Long> TIER = AttributeKey.longKey("tier");
    private static final AttributeKey<String> TIER_STRING = AttributeKey.stringKey("tier");

    @BeforeEach
    @AfterEach
    void reset() {
        GlobalAttributes.resetForTest();
    }

    @Test
    void setMergesByKeyAndRemoveDropsKeys() {
        LDObserve.setAttributes(Attributes.of(TENANT, "acme", REGION, "us-east-1"));
        LDObserve.setAttributes(Attributes.of(REGION, "eu-west-1", TIER, 3L));

        assertEquals(Attributes.of(TENANT, "acme", REGION, "eu-west-1", TIER, 3L), GlobalAttributes.get());

        LDObserve.removeAttributes(List.of("region", "not-set"));
        assertEquals(Attributes.of(TENANT, "acme", TIER, 3L), GlobalAttributes.get());
    }

    @Test
    void keysMatchByNameWhateverTheType() {
        LDObserve.setAttributes(Attributes.of(TIER, 3L));
        LDObserve.setAttributes(Attributes.of(TIER_STRING, "gold"));
        assertEquals(Attributes.of(TIER_STRING, "gold"), GlobalAttributes.get());

        LDObserve.removeAttributes(List.of("tier"));
        assertTrue(GlobalAttributes.get().isEmpty());
    }

    @Test
    void nullAndEmptyArgumentsAreIgnored() {
        LDObserve.setAttributes(Attributes.of(TENANT, "acme"));
        Attributes before = GlobalAttributes.get();

        LDObserve.setAttributes(null);
        LDObserve.setAttributes(Attributes.empty());
        LDObserve.removeAttributes(null);
        LDObserve.removeAttributes(List.of());

        assertSame(before, GlobalAttributes.get());
    }

    @Test
    void mergeKeepsPerCallValues() {
        Attributes perCall = Attributes.of(REGION, "per-call");
        assertSame(perCall, GlobalAttributes.merge(perCall), "no globals must not copy the attributes");
        assertEquals(Attributes.empty(), GlobalAttributes.merge(null));

        LDObserve.setAttributes(Attributes.of(TENANT, "acme", REGION, "global", TIER, 3L));

        assertEquals(Attributes.of(TENANT, "acme", REGION, "per-call", TIER, 3L), GlobalAttributes.merge(perCall));
        assertEquals(Attributes.of(TENANT, "acme", REGION, "global", TIER_STRING, "gold"),
                GlobalAttributes.merge(Attributes.of(TIER_STRING, "gold")),
                "a per-call key of another type still wins over the global of the same name");
        assertEquals(GlobalAttributes.get(), GlobalAttributes.merge(null));
    }

    @Test
    void concurrentSetsAreAllKept() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        LDObserve.setAttributes(Attributes.of(AttributeKey.longKey("k." + thread + "." + i), (long) i));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads * perThread, GlobalAttributes.get().size());
    }

    @Test
    void spanProcessorFillsOnlyKeysTheSpanStartedWithout() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(new GlobalAttributesSpanProcessor())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            LDObserve.setAttributes(Attributes.of(TENANT, "acme", REGION, "global", TIER, 3L));
            Span early = provider.get("test").spanBuilder("early")
                    .setAllAttributes(Attributes.of(REGION, "builder", TIER_STRING, "gold"))
                    .startSpan();
            LDObserve.setAttributes(Attributes.of(TENANT, "changed-later"));
            early.end();

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals("acme", span.getAttributes().get(TENANT), "a span keeps the values current at start");
            assertEquals("builder", span.getAttributes().get(REGION));
            assertEquals("gold", span.getAttributes().get(TIER_STRING));
            assertNull(span.getAttributes().get(TIER), "a key of another type with the same name is not added");
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void logProcessorFillsOnlyKeysTheRecordIsMissing() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider provider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new GlobalAttributesLogProcessor())
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        try {
            provider.get("test").logRecordBuilder().setBody("no globals yet").emit();
            LDObserve.setAttributes(Attributes.of(TENANT, "acme", REGION, "global"));
            provider.get("test").logRecordBuilder()
                    .setBody("with globals")
                    .setAllAttributes(Attributes.of(REGION, "per-call"))
                    .emit();

            List<LogRecordData> records = exporter.getFinishedLogRecordItems();
            assertTrue(records.get(0).getAttributes().isEmpty());
            assertEquals(Attributes.of(TENANT, "acme", REGION, "per-call"), records.get(1).getAttributes());
        } finally {
            provider.shutdown();
        }
    }
}
