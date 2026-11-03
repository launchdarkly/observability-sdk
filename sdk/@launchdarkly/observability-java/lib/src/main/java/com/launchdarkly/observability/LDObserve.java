package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.Constants;
import com.launchdarkly.observability.internal.GlobalAttributes;
import com.launchdarkly.observability.internal.OtelManager;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.Collection;

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
     * Merges attributes into the process-wide global attributes, which are
     * added to every span and log record the plugin exports (flag
     * evaluations, error spans and spans or records from instrumentation
     * using {@link #getOpenTelemetry()} included) and to every metric
     * recorded through {@code LDObserve}. A key already in the global
     * attributes takes the new value.
     *
     * <p>Global attributes never replace an attribute the span, record or
     * metric call already has: the attributes passed to an {@code LDObserve}
     * method, or set on a span builder, win; resource attributes are
     * separate and stay as they are. A span gets the values current when it
     * starts; changing them later does not touch spans already started.</p>
     *
     * <p>Kept in memory for the life of the process, including across
     * {@link #shutdown()}. Safe to call before the plugin starts, and from
     * any thread. Keys are matched by name, whatever the attribute type.
     * Null or empty attributes are ignored.</p>
     */
    public static void setAttributes(Attributes attributes) {
        GlobalAttributes.set(attributes);
    }

    /**
     * Removes the global attributes with these key names. See
     * {@link #setAttributes(Attributes)}. Names that are not set are
     * ignored, as is a null collection.
     */
    public static void removeAttributes(Collection<String> keys) {
        GlobalAttributes.remove(keys);
    }

    /**
     * Records a gauge metric.
     */
    public static void recordMetric(String name, double value, Attributes attributes) {
        try {
            OtelManager.getGauge(name).set(value, GlobalAttributes.merge(attributes));
        } catch (LinkageError e) {
            // The synchronous gauge API arrived in OpenTelemetry 1.38. On an
            // older API (Spring Boot 3.2 pins 1.31) the plugin is already off
            // and has logged why; recording must not fail the caller.
        }
    }

    /**
     * Records a counter metric.
     */
    public static void recordCount(String name, long value, Attributes attributes) {
        OtelManager.getCounter(name).add(value, GlobalAttributes.merge(attributes));
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
        OtelManager.getHistogram(name).record(value, GlobalAttributes.merge(attributes));
    }

    /**
     * Records an up/down counter metric.
     */
    public static void recordUpDownCounter(String name, long value, Attributes attributes) {
        OtelManager.getUpDownCounter(name).add(value, GlobalAttributes.merge(attributes));
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
     * Records an error with exception details. Like the other LaunchDarkly
     * server SDKs, an error inside a span the plugin exports is added to that
     * span as an exception event, with {@code attributes} on the event.
     * Otherwise it is recorded on a new error span, a child of the current
     * span if there is one.
     */
    public static void recordError(Throwable throwable, Attributes attributes) {
        if (throwable == null) {
            return;
        }
        Span current = Span.current();
        if (current.isRecording() && OtelManager.ownsSpan(current)) {
            current.recordException(throwable, orEmpty(attributes));
            return;
        }
        Tracer tracer = OtelManager.getTracer();
        Span span = tracer.spanBuilder(Constants.ERROR_SPAN_NAME)
                .setAllAttributes(attributes)
                .startSpan();
        try {
            span.setStatus(StatusCode.ERROR, throwable.getMessage());
            span.recordException(throwable);
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
     * Returns the plugin's OpenTelemetry, so other instrumentation can export
     * to LaunchDarkly through the same providers, resource and sampling
     * rules. It works whether or not the plugin also owns
     * {@code GlobalOpenTelemetry}.
     *
     * <p>Typical uses: expose it as the {@code OpenTelemetry} bean so Spring
     * Boot's Micrometer tracing bridge sends HTTP server spans (with the
     * flag-evaluation spans as children) to LaunchDarkly, or pass it to
     * {@code OpenTelemetryAppender.install} so Logback or Log4j records are
     * exported.</p>
     *
     * <p>The returned instance looks the providers up on every call: before
     * the plugin starts, or after {@link #shutdown()}, it hands out no-op
     * tracers, meters and loggers. Instruments and tracers obtained in that
     * window stay no-ops, so take them after the {@code LDClient} is built
     * (or after {@link #start} with manual start). It cannot be closed;
     * use {@link #shutdown()}.</p>
     */
    public static OpenTelemetry getOpenTelemetry() {
        return OtelManager.getOpenTelemetry();
    }

    /**
     * Manually starts the OTLP providers and fetches the sampling
     * configuration. Needed when
     * {@link ObservabilityOptions.Builder#manualStart(boolean)} is true, or
     * to use {@code LDObserve} without an {@code LDClient}.
     */
    public static void start(String sdkKey, ObservabilityOptions options) {
        if (OtelManager.initialize(sdkKey, options)) {
            OtelManager.fetchSamplingConfig(sdkKey, options);
        }
    }

    /**
     * Flushes all pending telemetry data.
     */
    public static void flush() {
        OtelManager.flush();
    }

    /**
     * Flushes all pending data and shuts down the providers. Call it after
     * the last telemetry is recorded: under Spring Boot, from a bean's
     * destroy method (see
     * {@link ObservabilityOptions.Builder#shutdownHook(boolean)}). Later
     * calls, and the plugin's JVM shutdown hook if it is registered, then
     * find nothing to do.
     */
    public static void shutdown() {
        OtelManager.shutdown();
    }

    private static Attributes orEmpty(Attributes attributes) {
        return attributes != null ? attributes : Attributes.empty();
    }
}
