package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * LDObserve sits on application request paths, so a null argument must not
 * throw into the caller, before or after the providers start.
 */
class LDObserveArgumentsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        OtelManager.resetForTest();
        GlobalOpenTelemetry.resetForTest();
    }

    @ParameterizedTest(name = "initialized={0}")
    @ValueSource(booleans = {false, true})
    void nullArgumentsDoNotThrow(boolean initialized) {
        if (initialized) {
            OtelManager.initialize("sdk-test", new ObservabilityOptions.Builder()
                    .otlpEndpoint("https://otel.example.test:4318")
                    .backendUrl("https://backend.example.test")
                    .build());
        }

        assertDoesNotThrow(() -> LDObserve.recordMetric(null, 1.0, null));
        assertDoesNotThrow(() -> LDObserve.recordCount(null, 1, null));
        assertDoesNotThrow(() -> LDObserve.recordIncr(null, null));
        assertDoesNotThrow(() -> LDObserve.recordHistogram(null, 1.0, null));
        assertDoesNotThrow(() -> LDObserve.recordUpDownCounter(null, 1, null));
        assertDoesNotThrow(() -> LDObserve.recordMetric("gauge", 1.0, null));
        assertDoesNotThrow(() -> LDObserve.recordCount("count", 1, null));
        assertDoesNotThrow(() -> LDObserve.recordHistogram("histogram", 1.0, null));
        assertDoesNotThrow(() -> LDObserve.recordUpDownCounter("updown", 1, null));
        assertDoesNotThrow(() -> LDObserve.recordLog(null, null, null));
        assertDoesNotThrow(() -> LDObserve.recordLog("message", Severity.WARN, null));
        assertDoesNotThrow(() -> LDObserve.recordError(null, null));
        assertDoesNotThrow(() -> LDObserve.recordError(new RuntimeException(), null));
        assertDoesNotThrow(() -> {
            Span span = LDObserve.startSpan(null, null);
            assertNotNull(span);
            span.end();
        });
    }
}
