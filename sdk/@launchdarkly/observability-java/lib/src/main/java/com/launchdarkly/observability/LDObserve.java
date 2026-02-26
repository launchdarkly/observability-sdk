package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;
import com.launchdarkly.observability.internal.OtelManager;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Static singleton public API for LaunchDarkly Observability.
 *
 * <p>Before the plugin is initialized, all methods are safe to call and
 * will use no-op implementations from the OpenTelemetry API.</p>
 */
public final class LDObserve {
    private LDObserve() {}

    /**
     * Records a gauge metric.
     */
    public static void recordMetric(String name, double value, Attributes attributes) {
        Meter meter = OtelManager.getMeter();
        DoubleGauge gauge = meter.gaugeBuilder(name).build();
        gauge.set(value, attributes);
    }

    /**
     * Records a counter metric.
     */
    public static void recordCount(String name, long value, Attributes attributes) {
        Meter meter = OtelManager.getMeter();
        LongCounter counter = meter.counterBuilder(name).build();
        counter.add(value, attributes);
    }

    /**
     * Records a counter increment of 1.
     */
    public static void recordIncr(String name, Attributes attributes) {
        recordCount(name, 1, attributes);
    }

    /**
     * Records a histogram metric.
     */
    public static void recordHistogram(String name, double value, Attributes attributes) {
        Meter meter = OtelManager.getMeter();
        DoubleHistogram histogram = meter.histogramBuilder(name).build();
        histogram.record(value, attributes);
    }

    /**
     * Records an up/down counter metric.
     */
    public static void recordUpDownCounter(String name, long value, Attributes attributes) {
        Meter meter = OtelManager.getMeter();
        LongUpDownCounter counter = meter.upDownCounterBuilder(name).build();
        counter.add(value, attributes);
    }

    /**
     * Records a log message.
     */
    public static void recordLog(String message, Severity severity, Attributes attributes) {
        Logger logger = OtelManager.getLogger();
        logger.logRecordBuilder()
                .setSeverity(severity)
                .setBody(message)
                .setAllAttributes(attributes)
                .emit();
    }

    /**
     * Records an error as a span with exception details.
     */
    public static void recordError(Throwable throwable, Attributes attributes) {
        Tracer tracer = OtelManager.getTracer();
        Span span = tracer.spanBuilder(Constants.ERROR_SPAN_NAME)
                .setAllAttributes(attributes)
                .startSpan();
        try {
            span.setStatus(StatusCode.ERROR, throwable.getMessage());
            span.recordException(throwable);

            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            span.setAttribute("exception.stacktrace", sw.toString());
        } finally {
            span.end();
        }
    }

    /**
     * Starts a new span with the given name and attributes.
     *
     * @return the started span; caller is responsible for calling {@link Span#end()}.
     */
    public static Span startSpan(String name, Attributes attributes) {
        Tracer tracer = OtelManager.getTracer();
        return tracer.spanBuilder(name)
                .setAllAttributes(attributes)
                .startSpan();
    }

    /**
     * Manually starts the OTLP providers. Only needed when
     * {@link ObservabilityOptions.Builder#manualStart(boolean)} is true.
     */
    public static void start(String sdkKey, ObservabilityOptions options) {
        OtelManager.initialize(sdkKey, options);
    }

    /**
     * Flushes all pending telemetry data.
     */
    public static void flush() {
        OtelManager.flush();
    }

    /**
     * Flushes all pending data and shuts down the providers.
     */
    public static void shutdown() {
        OtelManager.shutdown();
    }
}
