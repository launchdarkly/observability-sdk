package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;
import com.launchdarkly.observability.internal.OtelManager;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Static singleton public API for LaunchDarkly Observability.
 *
 * <p>Before the plugin is initialized, all methods are safe to call and
 * will use no-op implementations from the OpenTelemetry API. Methods are
 * safe to call from any thread and do not throw on null arguments: a null
 * metric name records nothing, null attributes are treated as empty, and
 * {@link #recordError} ignores a null throwable.</p>
 */
public final class LDObserve {
    private LDObserve() {}

    /**
     * Records a gauge metric.
     */
    public static void recordMetric(String name, double value, Attributes attributes) {
        OtelManager.getGauge(name).set(value, orEmpty(attributes));
    }

    /**
     * Records a counter metric.
     */
    public static void recordCount(String name, long value, Attributes attributes) {
        OtelManager.getCounter(name).add(value, orEmpty(attributes));
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
        OtelManager.getHistogram(name).record(value, orEmpty(attributes));
    }

    /**
     * Records an up/down counter metric.
     */
    public static void recordUpDownCounter(String name, long value, Attributes attributes) {
        OtelManager.getUpDownCounter(name).add(value, orEmpty(attributes));
    }

    /**
     * Records a log message.
     */
    public static void recordLog(String message, Severity severity, Attributes attributes) {
        LogRecordBuilder builder = OtelManager.getLogger().logRecordBuilder();
        if (severity != null) {
            // Severity number and text are separate OTLP fields; the backend
            // UI and severityText sampling rules read the text.
            builder.setSeverity(severity).setSeverityText(severity.name());
        }
        if (message != null) {
            builder.setBody(message);
        }
        builder.setAllAttributes(attributes).emit();
    }

    /**
     * Records an error as a span with exception details.
     */
    public static void recordError(Throwable throwable, Attributes attributes) {
        if (throwable == null) {
            return;
        }
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

    private static Attributes orEmpty(Attributes attributes) {
        return attributes != null ? attributes : Attributes.empty();
    }
}
