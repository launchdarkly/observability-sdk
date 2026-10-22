package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelManagerTest {

    @BeforeEach
    @AfterEach
    void reset() {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void samplingConfigArrivingBeforeManualStartIsApplied() {
        OtelManager.setSamplingConfig(sampleConfig());
        assertFalse(OtelManager.isInitialized());

        OtelManager.initialize("sdk-test", options());

        assertTrue(OtelManager.isInitialized());
        assertTrue(OtelManager.isSamplingConfigured());
    }

    @Test
    void concurrentInitializeBuildsOneManager() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            failures.incrementAndGet();
                            return;
                        }
                        OtelManager.initialize("sdk-test", options());
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEqualsZero(failures.get());
        assertTrue(OtelManager.isInitialized());
    }

    @Test
    void instrumentsAreCachedByNamePerProvider() {
        OtelManager.initialize("sdk-test", options());

        assertSame(OtelManager.getGauge("cpu"), OtelManager.getGauge("cpu"));
        assertSame(OtelManager.getCounter("orders"), OtelManager.getCounter("orders"));
        assertSame(OtelManager.getHistogram("latency"), OtelManager.getHistogram("latency"));
        assertSame(OtelManager.getUpDownCounter("queue"), OtelManager.getUpDownCounter("queue"));
        assertNotSame(OtelManager.getGauge("cpu"), OtelManager.getGauge("memory"));

        Object gaugeBeforeRestart = OtelManager.getGauge("cpu");
        OtelManager.shutdown();
        OtelManager.initialize("sdk-test", options());
        assertNotSame(gaugeBeforeRestart, OtelManager.getGauge("cpu"),
                "a restarted provider must not reuse instruments from the old one");
    }

    @Test
    void restartKeepsOneShutdownHook() {
        OtelManager.initialize("sdk-test", options());
        Thread hook = OtelManager.shutdownHookForTest();
        assertNotNull(hook);

        OtelManager.shutdown();
        OtelManager.initialize("sdk-test", options());

        assertSame(hook, OtelManager.shutdownHookForTest(), "a restart must not register another JVM shutdown hook");
    }

    @Test
    void onlyTheFirstSdkKeyRegistersUntilShutdown() {
        assertTrue(OtelManager.registerClient("sdk-a"));
        assertTrue(OtelManager.registerClient("sdk-a"), "another client for the same key shares the providers");
        assertFalse(OtelManager.registerClient("sdk-b"));

        OtelManager.shutdown();
        assertTrue(OtelManager.registerClient("sdk-b"), "after shutdown a new key can register");
    }

    private static void assertEqualsZero(int failures) {
        assertTrue(failures == 0, "concurrent initialize failures: " + failures);
    }

    @Test
    void shutdownHookIsRegisteredByDefault() {
        OtelManager.initialize("sdk-test", options());
        assertNotNull(OtelManager.shutdownHookForTest());
    }

    @Test
    void shutdownHookCanBeLeftToTheFramework() {
        OtelManager.initialize("sdk-test", new ObservabilityOptions.Builder()
                .otlpEndpoint("https://otel.example.test:4318")
                .shutdownHook(false)
                .build());

        assertTrue(OtelManager.isInitialized());
        assertNull(OtelManager.shutdownHookForTest(),
                "with shutdownHook(false) the application stops the plugin; a JVM hook would race it");
    }

    private static ObservabilityOptions options() {
        return new ObservabilityOptions.Builder()
                .serviceName("test-service")
                .otlpEndpoint("https://otel.example.test:4318")
                .backendUrl("https://backend.example.test")
                .build();
    }

    private static SamplingConfig sampleConfig() {
        return new SamplingConfig(
                List.of(new SamplingConfig.SpanSamplingConfig(
                        SamplingConfig.MatchConfig.ofValue("health-check"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        1
                )),
                Collections.emptyList()
        );
    }
}
