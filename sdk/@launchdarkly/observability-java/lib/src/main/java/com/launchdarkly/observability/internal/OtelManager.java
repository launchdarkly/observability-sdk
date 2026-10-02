package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingLogProcessor;
import com.launchdarkly.observability.internal.sampling.SamplingTraceExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Manages OpenTelemetry provider setup and lifecycle for LaunchDarkly Observability.
 */
public final class OtelManager {

    private static final java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(OtelManager.class.getName());

    private static final AtomicReference<OtelManager> INSTANCE = new AtomicReference<>();
    private static final AtomicReference<SamplingConfig> PENDING_SAMPLING_CONFIG = new AtomicReference<>();
    private static final Object INIT_LOCK = new Object();
    private static Thread shutdownHook;

    private final SdkTracerProvider tracerProvider;
    private final SdkLoggerProvider loggerProvider;
    private final SdkMeterProvider meterProvider;
    private final OpenTelemetrySdk openTelemetrySdk;
    private final Tracer tracer;
    private final Logger logger;
    private final Meter meter;
    private final CustomSampler customSampler;

    private OtelManager(
            SdkTracerProvider tracerProvider,
            SdkLoggerProvider loggerProvider,
            SdkMeterProvider meterProvider,
            OpenTelemetrySdk openTelemetrySdk,
            CustomSampler customSampler
    ) {
        this.tracerProvider = tracerProvider;
        this.loggerProvider = loggerProvider;
        this.meterProvider = meterProvider;
        this.openTelemetrySdk = openTelemetrySdk;
        this.customSampler = customSampler;

        this.tracer = openTelemetrySdk.getTracer(
                Constants.INSTRUMENTATION_SCOPE_NAME,
                Constants.INSTRUMENTATION_VERSION
        );
        this.logger = openTelemetrySdk.getLogsBridge().get(
                Constants.INSTRUMENTATION_SCOPE_NAME
        );
        this.meter = openTelemetrySdk.getMeter(
                Constants.INSTRUMENTATION_SCOPE_NAME
        );
    }

    /**
     * Initializes the OTel providers with the given options and SDK key.
     *
     * <p>Safe for concurrent callers. Only the first call builds providers and
     * registers a shutdown hook. Sampling configuration fetched before this
     * returns is applied to the new sampler.</p>
     */
    public static void initialize(String sdkKey, ObservabilityOptions options) {
        synchronized (INIT_LOCK) {
            if (INSTANCE.get() != null) {
                log.fine("OtelManager already initialized, skipping");
                return;
            }
            OtelManager manager = create(sdkKey, options);
            INSTANCE.set(manager);
            SamplingConfig pending = PENDING_SAMPLING_CONFIG.get();
            if (pending != null) {
                manager.customSampler.setConfig(pending);
            }
            shutdownHook = new Thread(() -> {
                OtelManager current = INSTANCE.get();
                if (current != null) {
                    current.shutdownProviders();
                }
            }, "ld-observability-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    private static OtelManager create(String sdkKey, ObservabilityOptions options) {
        Resource resource = buildResource(sdkKey, options);
        String endpoint = options.getOtlpEndpoint();

        CustomSampler customSampler = new CustomSampler();

        // Trace exporter with sampling
        OtlpHttpSpanExporter rawSpanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint + Constants.TRACES_PATH)
                .setCompression("gzip")
                .build();
        SamplingTraceExporter samplingTraceExporter =
                new SamplingTraceExporter(rawSpanExporter, customSampler);

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(samplingTraceExporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .setExporterTimeout(Duration.ofSeconds(30))
                        .build())
                .setResource(resource)
                .build();

        // Log exporter with sampling
        OtlpHttpLogRecordExporter rawLogExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(endpoint + Constants.LOGS_PATH)
                .setCompression("gzip")
                .build();
        BatchLogRecordProcessor batchLogProcessor = BatchLogRecordProcessor.builder(rawLogExporter)
                .setExporterTimeout(Duration.ofSeconds(30))
                .build();
        SamplingLogProcessor samplingLogProcessor =
                new SamplingLogProcessor(batchLogProcessor, customSampler);

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(samplingLogProcessor)
                .setResource(resource)
                .build();

        // Metric exporter
        OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint + Constants.METRICS_PATH)
                .setCompression("gzip")
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofSeconds(5))
                        .build())
                .setResource(resource)
                .build();

        // Propagator
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();

        OpenTelemetrySdk sdk;
        try {
            sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setLoggerProvider(loggerProvider)
                    .setMeterProvider(meterProvider)
                    .setPropagators(ContextPropagators.create(propagator))
                    .buildAndRegisterGlobal();
        } catch (IllegalStateException e) {
            // GlobalOpenTelemetry was already set (e.g., by user's own config).
            // Build without registering globally; TracingHook will use whatever
            // is globally registered.
            log.log(Level.WARNING,
                    "GlobalOpenTelemetry already set. Building SDK without global registration.", e);
            sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setLoggerProvider(loggerProvider)
                    .setMeterProvider(meterProvider)
                    .setPropagators(ContextPropagators.create(propagator))
                    .build();
        }

        OtelManager manager = new OtelManager(
                tracerProvider, loggerProvider, meterProvider, sdk, customSampler);

        if (options.isDebug()) {
            log.info("OtelManager initialized with endpoint: " + endpoint);
        }
        return manager;
    }

    /**
     * Sets the sampling configuration received from the backend.
     *
     * <p>If providers have not been started yet (manual start), the config is
     * kept and applied when {@link #initialize} runs. Concurrent callers share
     * one config reference; the latest write wins.</p>
     */
    public static void setSamplingConfig(SamplingConfig config) {
        PENDING_SAMPLING_CONFIG.set(config);
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            manager.customSampler.setConfig(config);
        }
    }

    public static Tracer getTracer() {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            return manager.tracer;
        }
        // OpenTelemetry.noop() does not install a process-wide global. Calling
        // GlobalOpenTelemetry.getTracer before init would lock the JVM onto a
        // no-op, and TracingHook would never see the plugin's exporters.
        return OpenTelemetry.noop().getTracer(Constants.INSTRUMENTATION_SCOPE_NAME);
    }

    public static Logger getLogger() {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            return manager.logger;
        }
        return OpenTelemetry.noop().getLogsBridge().get(Constants.INSTRUMENTATION_SCOPE_NAME);
    }

    public static Meter getMeter() {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            return manager.meter;
        }
        return OpenTelemetry.noop().getMeter(Constants.INSTRUMENTATION_SCOPE_NAME);
    }

    /**
     * Flushes all pending telemetry data.
     */
    public static void flush() {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            manager.flushProviders();
        }
    }

    /**
     * Shuts down all providers and flushes pending data.
     */
    public static void shutdown() {
        OtelManager manager;
        synchronized (INIT_LOCK) {
            manager = INSTANCE.getAndSet(null);
        }
        if (manager != null) {
            manager.shutdownProviders();
        }
    }

    static boolean isInitialized() {
        return INSTANCE.get() != null;
    }

    static boolean isSamplingConfigured() {
        OtelManager manager = INSTANCE.get();
        return manager != null && manager.customSampler.isSamplingEnabled();
    }

    /**
     * Drops process-wide state so tests can initialize again. Removes the
     * shutdown hook registered by {@link #initialize}.
     */
    static void resetForTest() {
        OtelManager manager;
        synchronized (INIT_LOCK) {
            manager = INSTANCE.getAndSet(null);
            PENDING_SAMPLING_CONFIG.set(null);
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown is already in progress.
                }
                shutdownHook = null;
            }
        }
        if (manager != null) {
            manager.shutdownProviders();
        }
    }

    private void flushProviders() {
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
    }

    private void shutdownProviders() {
        flushProviders();
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        meterProvider.shutdown().join(10, TimeUnit.SECONDS);
    }

    private static Resource buildResource(String sdkKey, ObservabilityOptions options) {
        io.opentelemetry.api.common.AttributesBuilder attrs = Attributes.builder();
        attrs.put(Constants.PROJECT_ID_ATTRIBUTE, sdkKey);
        attrs.put("telemetry.distro.name", Constants.INSTRUMENTATION_SCOPE_NAME);
        attrs.put("telemetry.distro.version", Constants.INSTRUMENTATION_VERSION);

        if (options.getServiceName() != null && !options.getServiceName().isEmpty()) {
            attrs.put("service.name", options.getServiceName());
        }
        if (options.getServiceVersion() != null && !options.getServiceVersion().isEmpty()) {
            attrs.put("service.version", options.getServiceVersion());
        }
        if (options.getEnvironment() != null && !options.getEnvironment().isEmpty()) {
            attrs.put("deployment.environment.name", options.getEnvironment());
        }

        return Resource.getDefault().merge(Resource.create(attrs.build()));
    }
}
