package com.launchdarkly.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that LDObserve methods are safe to call before initialization
 * (they delegate to no-op OTel implementations).
 */
class LDObserveTest {

    @Test
    void recordMetricBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordMetric("test.gauge", 1.0, Attributes.empty()));
    }

    @Test
    void recordCountBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordCount("test.count", 1, Attributes.empty()));
    }

    @Test
    void recordIncrBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordIncr("test.incr", Attributes.empty()));
    }

    @Test
    void recordHistogramBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordHistogram("test.histogram", 42.5, Attributes.empty()));
    }

    @Test
    void recordUpDownCounterBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordUpDownCounter("test.updown", 1, Attributes.empty()));
    }

    @Test
    void recordLogBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordLog("test message", Severity.INFO, Attributes.empty()));
    }

    @Test
    void recordErrorBeforeInitDoesNotThrow() {
        assertDoesNotThrow(() ->
                LDObserve.recordError(new RuntimeException("test"), Attributes.empty()));
    }

    @Test
    void startSpanBeforeInitReturnsNonNull() {
        Span span = LDObserve.startSpan("test-span", Attributes.empty());
        assertNotNull(span);
        span.end();
    }

    @Test
    void flushBeforeInitDoesNotThrow() {
        assertDoesNotThrow(LDObserve::flush);
    }

    @Test
    void shutdownBeforeInitDoesNotThrow() {
        assertDoesNotThrow(LDObserve::shutdown);
    }
}
