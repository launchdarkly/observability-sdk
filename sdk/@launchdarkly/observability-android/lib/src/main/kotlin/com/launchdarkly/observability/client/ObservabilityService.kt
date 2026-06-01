package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.bridge.emitLog
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.logs.EventLogRecordProcessor
import com.launchdarkly.observability.logs.OtlpLogExporter
import com.launchdarkly.observability.metrics.EventMetricExporter
import com.launchdarkly.observability.metrics.OtlpMetricExporter
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.SamplingApiService
import com.launchdarkly.observability.otlp.OtlpConfiguration
import com.launchdarkly.observability.otlp.OtlpHttpClient
import com.launchdarkly.observability.plugin.ObservabilityHookExporter
import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.CustomSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import com.launchdarkly.observability.sampling.SamplingLogProcessor
import com.launchdarkly.observability.traces.EventSpanProcessor
import com.launchdarkly.observability.traces.OtlpTraceExporter
import com.launchdarkly.observability.util.requireMainThread
import com.launchdarkly.sdk.LDValue
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.session.SessionConfig
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.DoubleGauge
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The [ObservabilityService] can be used for recording observability data such as
 * metrics, logs, errors, and traces.
 *
 * This class is responsible for setting up and managing the OpenTelemetry RUM (Real User Monitoring)
 * instrumentation. It configures the providers for logs, traces, and metrics based on the
 * provided options. It also handles dynamic sampling configuration and provides methods to
 * record various telemetry signals.
 *
 * It is recommended to use the [com.launchdarkly.observability.plugin.Observability] plugin with the LaunchDarkly Android
 * Client SDK, as that will automatically initialize the [com.launchdarkly.observability.sdk.LDObserve] singleton instance.
 *
 * @param application The application instance.
 * @param sdkKey The SDK key for authentication.
 * @param resources The OpenTelemetry resource describing this service.
 * @param logger The logger for internal logging.
 * @param observabilityOptions Additional configuration options for the SDK.
 */
class ObservabilityService(
    private val application: Application,
    private val sdkKey: String,
    private val resources: Resource,
    private val logger: ObserveLogger,
    private val observabilityOptions: ObservabilityOptions,
) : Observe {
    private val otelRUM: OpenTelemetryRum
    var sessionManager: SessionManager? = null
        private set
    private var otelMeter: Meter
    private var otelLogger: Logger
    private var otelTracer: Tracer
    private var customSampler = CustomSampler()
    private val graphqlClient = GraphQLClient(
        endpoint = observabilityOptions.backendUrl,
        logger = logger
    )
    private val samplingApiService = SamplingApiService(graphqlClient)
    private val telemetryInspector: TelemetryInspector? = observabilityOptions.telemetryInspector
    private val otlpConfiguration = OtlpConfiguration(headers = observabilityOptions.customHeaders)
    private val eventQueue = EventQueue()
    private val batchWorker = BatchWorker(eventQueue = eventQueue, logger = logger)
    private var metricsReader: PeriodicMetricReader? = null
    private val gaugeCache = ConcurrentHashMap<String, DoubleGauge>()
    private val counterCache = ConcurrentHashMap<String, LongCounter>()
    private val histogramCache = ConcurrentHashMap<String, DoubleHistogram>()
    private val upDownCounterCache = ConcurrentHashMap<String, LongUpDownCounter>()
    internal val hookExporter: ObservabilityHookExporter

    //TODO: Evaluate if this class should have a close/shutdown method to close this scope
    private val scope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    init {
        requireMainThread { "ObservabilityService must be initialized on the main thread" }

        registerOtlpExporters()
        val otelRumConfig = createOtelRumConfig()

        var capturedSessionManager: SessionManager? = null

        val rumBuilder = OpenTelemetryRum.builder(application, otelRumConfig)
            .addLoggerProviderCustomizer { sdkLoggerProviderBuilder, _ ->
                return@addLoggerProviderCustomizer configureLoggerProvider(sdkLoggerProviderBuilder)
            }
            .addTracerProviderCustomizer { sdkTracerProviderBuilder, _ ->
                return@addTracerProviderCustomizer configureTracerProvider(sdkTracerProviderBuilder)
            }
            .addMeterProviderCustomizer { sdkMeterProviderBuilder, _ ->
                return@addMeterProviderCustomizer configureMeterProvider(sdkMeterProviderBuilder)
            }

        rumBuilder.addInstrumentation(object : AndroidInstrumentation {
            override val name = "ld-session-manager-bridge"
            override fun install(ctx: InstallationContext) {
                capturedSessionManager = ctx.sessionManager
            }
        })

        if (observabilityOptions.instrumentations.launchTime) {
            addLaunchTimeInstrumentation(rumBuilder)
        }

        otelRUM = rumBuilder.build()
        sessionManager = capturedSessionManager
        if (sessionManager == null) {
            logger.warn("SessionManager was not captured during OpenTelemetryRum.build(); session-dependent features will be unavailable.")
        }
        loadSamplingConfigAsync()

        otelMeter = otelRUM.openTelemetry.meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        otelLogger = otelRUM.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        otelTracer = otelRUM.openTelemetry.tracerProvider.get(INSTRUMENTATION_SCOPE_NAME)

        hookExporter = ObservabilityHookExporter(
            withSpans = true,
            withValue = true,
            tracerProvider = { getTracer() },
            contextFriendlyName = observabilityOptions.contextFriendlyName,
            logger = logger,
        )

        batchWorker.start()
    }

    private fun registerOtlpExporters() {
        val logExporter = OtlpLogExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + LOGS_PATH,
                config = otlpConfiguration,
            ),
        )
        val traceExporter = OtlpTraceExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + TRACES_PATH,
                config = otlpConfiguration,
            ),
        )
        val metricExporter = OtlpMetricExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + METRICS_PATH,
                config = otlpConfiguration,
            ),
        )
        batchWorker.addExporter(logExporter)
        batchWorker.addExporter(traceExporter)
        batchWorker.addExporter(metricExporter)
    }

    private fun createOtelRumConfig(): OtelRumConfig {
        val config = OtelRumConfig()
            .setSessionConfig(SessionConfig(backgroundInactivityTimeout = observabilityOptions.sessionBackgroundTimeout))

        if (!observabilityOptions.instrumentations.crashReporting) {
            // Disables [io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation.java]
            config.suppressInstrumentation("crash")
        }

        if(!observabilityOptions.instrumentations.activityLifecycle) {
            // Disables [io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation.java]
            config.suppressInstrumentation("activity")
        }

        return config
    }

    private fun addLaunchTimeInstrumentation(rumBuilder: OpenTelemetryRumBuilder) {
        val launchTimeInstrumentation = LaunchTimeInstrumentation(
            application = application,
            metricRecorder = { metric ->
                val histogram = histogramCache.getOrPut(metric.name) {
                    otelMeter.histogramBuilder(metric.name).build()
                }
                histogram.record(metric.value, metric.attributes.addSessionId())
            }
        )
        rumBuilder.addInstrumentation(launchTimeInstrumentation)
    }

    private fun configureLoggerProvider(sdkLoggerProviderBuilder: SdkLoggerProviderBuilder): SdkLoggerProviderBuilder {
        sdkLoggerProviderBuilder.setResource(resources)

        val delegates = buildList<LogRecordProcessor> {
            add(EventLogRecordProcessor(eventQueue = eventQueue, batchWorker = batchWorker))
            if (observabilityOptions.debug) {
                add(SimpleLogRecordProcessor.create(DebugLogExporter(logger)))
            }
            telemetryInspector?.let {
                add(SimpleLogRecordProcessor.create(it.logExporter))
            }
        }

        val otlpProcessor = SamplingLogProcessor(
            delegate = LogRecordProcessor.composite(delegates),
            sampler = customSampler,
        )
        sdkLoggerProviderBuilder.addLogRecordProcessor(otlpProcessor)

        return sdkLoggerProviderBuilder
    }

    private fun configureTracerProvider(sdkTracerProviderBuilder: SdkTracerProviderBuilder): SdkTracerProviderBuilder {
        sdkTracerProviderBuilder.setResource(resources)

        val debugExporters = buildList<io.opentelemetry.sdk.trace.export.SpanExporter> {
            if (observabilityOptions.debug) {
                add(DebugSpanExporter(logger))
            }
            telemetryInspector?.let {
                add(it.spanExporter)
            }
        }
        val delegateExporter = if (debugExporters.isNotEmpty()) {
            io.opentelemetry.sdk.trace.export.SpanExporter.composite(debugExporters)
        } else {
            null
        }

        val otlpProcessor = EventSpanProcessor(
            eventQueue = eventQueue,
            sampler = customSampler,
            delegateExporter = delegateExporter,
            batchWorker = batchWorker,
        )
        sdkTracerProviderBuilder.addSpanProcessor(otlpProcessor)

        return sdkTracerProviderBuilder
    }

    private fun configureMeterProvider(sdkMeterProviderBuilder: SdkMeterProviderBuilder): SdkMeterProviderBuilder {
        val eventExporter = EventMetricExporter(
            eventQueue = eventQueue,
            temporalitySelector = AggregationTemporalitySelector.deltaPreferred(),
            batchWorker = batchWorker,
        )

        val finalExporter: MetricExporter = if (observabilityOptions.debug || telemetryInspector != null) {
            CompositeMetricExporter(
                buildList {
                    add(eventExporter)
                    if (observabilityOptions.debug) add(DebugMetricExporter(logger))
                    telemetryInspector?.let { add(it.metricExporter) }
                }
            )
        } else {
            eventExporter
        }

        val metricReader = createPeriodicMetricReader(finalExporter)
        metricsReader = metricReader
        return sdkMeterProviderBuilder
            .setResource(resources)
            .registerMetricReader(metricReader)
    }

    private fun createPeriodicMetricReader(metricExporter: MetricExporter): PeriodicMetricReader {
        return PeriodicMetricReader.builder(metricExporter)
            .setInterval(METRICS_EXPORT_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun loadSamplingConfigAsync() {
        scope.launch {
            val samplingConfig = getSamplingConfig()
            if (samplingConfig != null) {
                logger.info("Sampling configuration was successfully loaded")
            }
            customSampler.setConfig(samplingConfig)
        }
    }

    override fun recordMetric(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val gauge = gaugeCache.getOrPut(metric.name) {
            otelMeter.gaugeBuilder(metric.name).build()
        }
        gauge.set(metric.value, metric.attributes.addSessionId())
    }

    override fun recordCount(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        // TODO: handle double casting to long better
        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        counter.add(metric.value.toLong(), metric.attributes.addSessionId())
    }

    override fun recordIncr(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        // It increments the value until the metric is exported, then it's reset.
        counter.add(1, metric.attributes.addSessionId())
    }

    override fun recordHistogram(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val histogram = histogramCache.getOrPut(metric.name) {
            otelMeter.histogramBuilder(metric.name).build()
        }
        histogram.record(metric.value, metric.attributes.addSessionId())
    }

    override fun recordUpDownCounter(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val upDownCounter = upDownCounterCache.getOrPut(metric.name) {
            otelMeter.upDownCounterBuilder(metric.name).build()
        }
        upDownCounter.add(metric.value.toLong(), metric.attributes.addSessionId())
    }

    override fun recordLog(
        message: String,
        severity: Severity,
        attributes: Attributes,
        spanContext: io.opentelemetry.api.trace.SpanContext?
    ) {
        if (observabilityOptions.logsApiLevel.level > severity.severityNumber) return
        otelLogger.emitLog(message, severity, attributes, spanContext)
    }

    override fun recordError(error: Error, attributes: Attributes) {
        if (!observabilityOptions.tracesApi.includeErrors) return

        val span = otelTracer
            .spanBuilder(ERROR_SPAN_NAME)
            .setParent(Context.current().with(Span.current()))
            .startSpan()

        val attrBuilder = Attributes.builder()
        attrBuilder.putAll(attributes)

        span.recordException(error, attrBuilder.build())
        span.end()
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        if (!observabilityOptions.tracesApi.includeSpans) return Span.getInvalid()

        return otelTracer.spanBuilder(name)
            .setAllAttributes(attributes)
            .startSpan()
    }

    /**
     * Emits a `launchdarkly.track` span — the shared code path for both the LD client
     * `afterTrack` hook (i.e. when the application calls `LDClient.track(...)`) and direct
     * [com.launchdarkly.observability.sdk.LDObserve.track] calls.
     *
     * The actual span emission, attribute spread, and error swallow live on [hookExporter];
     * this method owns the `productAnalyticsApi.trackEvents` feature gate so the exporter
     * stays decoupled from the options struct.
     */
    fun track(key: String, data: LDValue?, metricValue: Double?) {
        if (!observabilityOptions.productAnalyticsApi.trackEvents) return

        hookExporter.track(
            key = key,
            data = data,
            metricValue = metricValue,
        )
    }

    /**
     * Returns the tracer instance for creating spans.
     *
     * @return Tracer instance
     */
    fun getTracer(): Tracer = otelTracer

    /**
     * Returns the logger instance for recording log records.
     *
     * @return Logger instance
     */
    fun getLogger(): io.opentelemetry.api.logs.Logger = otelLogger

    /**
     * Requests a flush of all pending telemetry data (traces, logs, metrics).
     *
     * Export to the network happens asynchronously via [BatchWorker] on background dispatchers;
     * this call does not wait for the queue to drain.
     *
     * Logs and spans are enqueued synchronously from their processors' `onEmit` / `onEnd`, so
     * nothing is buffered inside the processors — no processor flush is required.
     *
     * Metrics are different: [PeriodicMetricReader] collects instrument values on an interval
     * and only pushes them to the exporter (our [EventMetricExporter]) when the interval
     * elapses or when `forceFlush()` is invoked. That call is itself asynchronous — it schedules
     * collection on the reader's executor and completes only once the samples have been handed
     * to the exporter — so we join on its [CompletableResultCode] (with a small timeout) before
     * signalling the batch worker. That guarantees metric samples are enqueued before the
     * worker starts draining.
     */
    override fun flush() {
        metricsReader?.forceFlush()?.join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        batchWorker.flush()
    }

    /**
     * Fetches sampling configuration from GraphQL endpoint
     * @return SamplingConfig or null if error occurs
     */
    private suspend fun getSamplingConfig(): SamplingConfig? {
        return try {
            samplingApiService.getSamplingConfig(sdkKey)
        } catch (err: Exception) {
            logger.warn("Failed to get sampling config: ${err.message}")
            null
        }
    }

    private fun Attributes.addSessionId() = this.toBuilder().put(SESSION_ID_ATTRIBUTE, otelRUM.rumSessionId).build()

    companion object {
        private const val METRICS_PATH = "/v1/metrics"
        private const val LOGS_PATH = "/v1/logs"
        private const val TRACES_PATH = "/v1/traces"
        private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"
        const val ERROR_SPAN_NAME = "highlight.error"
        const val SESSION_ID_ATTRIBUTE = "session.id"
        private const val METRICS_EXPORT_INTERVAL_MS = 10_000L

        // Upper bound on how long [flush] waits for the metric reader to finish collecting
        // pending instrument values and hand them to the exporter. Kept short so flush stays
        // responsive even if the reader's executor is backed up.
        private const val FLUSH_TIMEOUT_SECONDS = 5L
    }
}
