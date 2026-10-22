package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingLogProcessor;
import com.launchdarkly.observability.internal.sampling.SamplingTraceExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.export.ProxyOptions;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    // Before init, and for a null instrument name, LDObserve records into
    // this no-op meter instead of throwing into the caller.
    private static final Meter NOOP_METER =
            OpenTelemetry.noop().getMeter(Constants.INSTRUMENTATION_SCOPE_NAME);
    // Guarded by INIT_LOCK. One JVM shutdown hook for the process; it shuts
    // down whichever manager is current, so a restart does not add another.
    private static Thread shutdownHook;
    // Guarded by INIT_LOCK. The SDK key of the first LDClient that
    // registered the plugin; the providers carry it as the project id.
    private static String registeredSdkKey;

    private final SdkTracerProvider tracerProvider;
    private final SdkLoggerProvider loggerProvider;
    private final SdkMeterProvider meterProvider;
    private final OpenTelemetrySdk openTelemetrySdk;
    private final Tracer tracer;
    private final Logger logger;
    private final Meter meter;
    private final CustomSampler customSampler;

    // Instruments are cached by name so recording a metric does not run the
    // SDK's builder and registration lookup on every call.
    private final Map<String, DoubleGauge> gauges = new ConcurrentHashMap<>();
    private final Map<String, LongCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();
    private final Map<String, LongUpDownCounter> upDownCounters = new ConcurrentHashMap<>();

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
     *
     * <p>Never throws. A bad option (an endpoint that is not a URL) or an
     * OpenTelemetry SDK on the classpath too old for the plugin is logged,
     * and the plugin stays a no-op, so observability cannot stop the
     * application or the LaunchDarkly client from starting.</p>
     *
     * @return whether providers are running when this returns
     */
    public static boolean initialize(String sdkKey, ObservabilityOptions options) {
        synchronized (INIT_LOCK) {
            if (INSTANCE.get() != null) {
                log.fine("OtelManager already initialized, skipping");
                return true;
            }
            OtelManager manager;
            try {
                manager = create(sdkKey, options);
            } catch (RuntimeException | LinkageError e) {
                // LinkageError: Spring Boot and other BOMs pin their own
                // OpenTelemetry version, which can be older than this plugin
                // needs. LDClient only catches Exception from a plugin, so
                // a NoSuchMethodError here would abort application startup.
                String hint = e instanceof LinkageError
                        ? " OpenTelemetry SDK on the classpath: " + sdkVersion() + "; this plugin needs "
                                + Constants.MIN_OTEL_SDK_VERSION + " or newer."
                        : "";
                log.log(Level.SEVERE, "LaunchDarkly observability could not start and exports nothing;"
                        + " flag evaluations are unaffected." + hint + " Cause: " + e, e);
                return false;
            }
            INSTANCE.set(manager);
            SamplingConfig pending = PENDING_SAMPLING_CONFIG.get();
            if (pending != null) {
                manager.customSampler.setConfig(pending);
            }
            // One hook for the process, and none when the application owns
            // shutdown (see ObservabilityOptions.Builder#shutdownHook).
            if (options.isShutdownHook() && shutdownHook == null) {
                shutdownHook = new Thread(() -> {
                    OtelManager current = INSTANCE.getAndSet(null);
                    if (current != null) {
                        current.shutdownProviders();
                    }
                }, "ld-observability-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
            return true;
        }
    }

    /**
     * Records that an {@code LDClient} with this SDK key is registering the
     * plugin. The providers are process-wide and carry one project id, so
     * only the first key is used: a later client with a different key gets
     * {@code false}, and its plugin neither starts providers nor replaces the
     * sampling configuration. A client with the same key gets {@code true}.
     */
    public static boolean registerClient(String sdkKey) {
        synchronized (INIT_LOCK) {
            if (registeredSdkKey == null) {
                registeredSdkKey = sdkKey;
                return true;
            }
            return registeredSdkKey.equals(sdkKey);
        }
    }

    /** The OpenTelemetry SDK version on the classpath, as the SDK reports it. */
    static String sdkVersion() {
        try {
            String version = Resource.getDefault().getAttribute(AttributeKey.stringKey("telemetry.sdk.version"));
            return version == null ? "unknown" : version;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private static OtelManager create(String sdkKey, ObservabilityOptions options) {
        Resource resource = buildResource(sdkKey, options);
        String endpoint = options.getOtlpEndpoint();
        Map<String, String> headers = options.getCustomHeaders();
        ProxyOptions proxy = options.getProxySelector() == null
                ? null
                : ProxyOptions.create(options.getProxySelector());

        CustomSampler customSampler = new CustomSampler();

        // Each exporter below reports its own otlp.exporter.* counters to
        // GlobalOpenTelemetry's meter provider, which is the plugin's once it
        // registers, so they would land in the customer's LaunchDarkly
        // metrics next to their own. They are switched off.

        // Trace exporter with sampling
        OtlpHttpSpanExporterBuilder spanExporterBuilder = OtlpHttpSpanExporter.builder()
                .setMeterProvider(MeterProvider.noop())
                .setEndpoint(endpoint + Constants.TRACES_PATH)
                .setCompression("gzip")
                .setHeaders(() -> headers);
        if (proxy != null) {
            spanExporterBuilder.setProxy(proxy);
        }
        OtlpHttpSpanExporter rawSpanExporter = spanExporterBuilder.build();
        SamplingTraceExporter samplingTraceExporter =
                new SamplingTraceExporter(rawSpanExporter, customSampler);

        // Always record, as the Node plugin does. The default parent-based
        // sampler would drop a span, flag evaluations included, whenever the
        // current parent comes unsampled from the Java agent, another SDK or
        // an upstream service. Volume is governed by the backend rules that
        // SamplingTraceExporter applies at export.
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(BatchSpanProcessor.builder(samplingTraceExporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .setExporterTimeout(Duration.ofSeconds(30))
                        .build())
                .setResource(resource)
                .build();

        // Log exporter with level filter and sampling
        OtlpHttpLogRecordExporterBuilder logExporterBuilder = OtlpHttpLogRecordExporter.builder()
                .setMeterProvider(MeterProvider.noop())
                .setEndpoint(endpoint + Constants.LOGS_PATH)
                .setCompression("gzip")
                .setHeaders(() -> headers);
        if (proxy != null) {
            logExporterBuilder.setProxyOptions(proxy);
        }
        OtlpHttpLogRecordExporter rawLogExporter = logExporterBuilder.build();
        BatchLogRecordProcessor batchLogProcessor = BatchLogRecordProcessor.builder(rawLogExporter)
                .setExporterTimeout(Duration.ofSeconds(30))
                .build();
        SamplingLogProcessor samplingLogProcessor =
                new SamplingLogProcessor(batchLogProcessor, customSampler);
        LogLevelFilterProcessor levelFilter = new LogLevelFilterProcessor(
                samplingLogProcessor, options.getLogsApiLevel().getSeverityNumber());

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(levelFilter)
                .setResource(resource)
                .build();

        // Metric exporter
        OtlpHttpMetricExporterBuilder metricExporterBuilder = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint + Constants.METRICS_PATH)
                .setCompression("gzip")
                .setHeaders(() -> headers);
        try {
            metricExporterBuilder.setMeterProvider(MeterProvider.noop());
        } catch (NoSuchMethodError e) {
            // Added in 1.50 together with the metric exporter's own
            // counters; older metric exporters count nothing. Spring Boot
            // 3.5 pins 1.49.
        }
        if (proxy != null) {
            metricExporterBuilder.setProxyOptions(proxy);
        }
        OtlpHttpMetricExporter metricExporter = metricExporterBuilder.build();

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
            // GlobalOpenTelemetry was already set (the OTel Java agent, the
            // application's own SDK, or a library that called
            // GlobalOpenTelemetry.get() first). Build without registering
            // globally. LDObserve and EvaluationTracingHook use this SDK
            // through OtelManager, so they keep exporting to LaunchDarkly.
            log.warning("GlobalOpenTelemetry already set. Building SDK without global registration;"
                    + " LaunchDarkly telemetry is still exported through the plugin's own providers.");
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

    /**
     * A view of the plugin's OpenTelemetry that resolves the providers on
     * every call, so it works even when taken before the providers start.
     * It has no {@code close()}: frameworks that close beans (Spring infers
     * a destroy method from the instance) must not stop the plugin's
     * providers behind its back.
     */
    private static final OpenTelemetry VIEW = new OpenTelemetry() {
        @Override
        public TracerProvider getTracerProvider() {
            OtelManager manager = INSTANCE.get();
            return manager != null ? manager.openTelemetrySdk.getTracerProvider() : TracerProvider.noop();
        }

        @Override
        public MeterProvider getMeterProvider() {
            OtelManager manager = INSTANCE.get();
            return manager != null ? manager.openTelemetrySdk.getMeterProvider() : MeterProvider.noop();
        }

        @Override
        public LoggerProvider getLogsBridge() {
            OtelManager manager = INSTANCE.get();
            return manager != null ? manager.openTelemetrySdk.getLogsBridge() : LoggerProvider.noop();
        }

        @Override
        public ContextPropagators getPropagators() {
            OtelManager manager = INSTANCE.get();
            return manager != null ? manager.openTelemetrySdk.getPropagators() : ContextPropagators.noop();
        }

        @Override
        public String toString() {
            return "LaunchDarklyObservabilityOpenTelemetry{started=" + (INSTANCE.get() != null) + "}";
        }
    };

    public static OpenTelemetry getOpenTelemetry() {
        return VIEW;
    }

    public static Tracer getTracer() {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            return manager.tracer;
        }
        // OpenTelemetry.noop() does not install a process-wide global. Calling
        // GlobalOpenTelemetry.getTracer before init would lock the JVM onto a
        // no-op for everything else in the process.
        return OpenTelemetry.noop().getTracer(Constants.INSTRUMENTATION_SCOPE_NAME);
    }

    /**
     * Returns a tracer from the plugin's own SDK under another
     * instrumentation scope, whether or not that SDK is also the global.
     */
    public static Tracer getTracer(String instrumentationScopeName) {
        OtelManager manager = INSTANCE.get();
        if (manager != null) {
            return manager.openTelemetrySdk.getTracer(instrumentationScopeName);
        }
        return OpenTelemetry.noop().getTracer(instrumentationScopeName);
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
        return NOOP_METER;
    }

    /**
     * Returns the gauge with this name, built once per provider. Before
     * initialization, or for a null name, it is a no-op and is not cached.
     */
    public static DoubleGauge getGauge(String name) {
        OtelManager manager = INSTANCE.get();
        if (manager == null || name == null) {
            return NOOP_METER.gaugeBuilder("").build();
        }
        return manager.gauges.computeIfAbsent(name, n -> manager.meter.gaugeBuilder(n).build());
    }

    /** Returns the counter with this name; see {@link #getGauge}. */
    public static LongCounter getCounter(String name) {
        OtelManager manager = INSTANCE.get();
        if (manager == null || name == null) {
            return NOOP_METER.counterBuilder("").build();
        }
        return manager.counters.computeIfAbsent(name, n -> manager.meter.counterBuilder(n).build());
    }

    /** Returns the histogram with this name; see {@link #getGauge}. */
    public static DoubleHistogram getHistogram(String name) {
        OtelManager manager = INSTANCE.get();
        if (manager == null || name == null) {
            return NOOP_METER.histogramBuilder("").build();
        }
        return manager.histograms.computeIfAbsent(name, n -> manager.meter.histogramBuilder(n).build());
    }

    /** Returns the up/down counter with this name; see {@link #getGauge}. */
    public static LongUpDownCounter getUpDownCounter(String name) {
        OtelManager manager = INSTANCE.get();
        if (manager == null || name == null) {
            return NOOP_METER.upDownCounterBuilder("").build();
        }
        return manager.upDownCounters.computeIfAbsent(name, n -> manager.meter.upDownCounterBuilder(n).build());
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
            registeredSdkKey = null;
        }
        if (manager != null) {
            manager.shutdownProviders();
        }
    }

    static Thread shutdownHookForTest() {
        synchronized (INIT_LOCK) {
            return shutdownHook;
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
            registeredSdkKey = null;
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

        // User attributes are merged last so they can override the defaults.
        return Resource.getDefault()
                .merge(Resource.create(attrs.build()))
                .merge(Resource.create(options.getResourceAttributes()));
    }
}
