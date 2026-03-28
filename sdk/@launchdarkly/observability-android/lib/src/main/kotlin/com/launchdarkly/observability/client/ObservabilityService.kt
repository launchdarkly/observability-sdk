package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.SamplingApiService
import com.launchdarkly.observability.plugin.ObservabilityHookExporter
import com.launchdarkly.observability.sampling.CustomSampler
import com.launchdarkly.observability.sampling.ExportSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import com.launchdarkly.observability.sampling.SamplingLogProcessor
import com.launchdarkly.observability.sampling.SamplingTraceExporter
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
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
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
    private val logger: LDLogger,
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
    private var spanProcessor: SpanProcessor? = null
    private var logProcessor: LogRecordProcessor? = null
    private var metricsReader: PeriodicMetricReader? = null
    private val gaugeCache = ConcurrentHashMap<String, DoubleGauge>()
    private val counterCache = ConcurrentHashMap<String, LongCounter>()
    private val histogramCache = ConcurrentHashMap<String, DoubleHistogram>()
    private val upDownCounterCache = ConcurrentHashMap<String, LongUpDownCounter>()
    internal val hookExporter: ObservabilityHookExporter

    //TODO: Evaluate if this class should have a close/shutdown method to close this scope
    private val scope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    init {
        val otelRumConfig = createOtelRumConfig()

        var capturedSessionManager: SessionManager? = null

        val rumBuilder = OpenTelemetryRum.builder(application, otelRumConfig)
            .addLoggerProviderCustomizer { sdkLoggerProviderBuilder, _ ->
                val processor = createLoggerProcessor(
                    sdkLoggerProviderBuilder,
                    customSampler,
                    sdkKey,
                    resources,
                    logger,
                    telemetryInspector,
                    observabilityOptions,
                )
                logProcessor = processor
                return@addLoggerProviderCustomizer sdkLoggerProviderBuilder.addLogRecordProcessor(processor)
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
            contextFriendlyName = observabilityOptions.contextFriendlyName
        )
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

    private fun configureTracerProvider(sdkTracerProviderBuilder: SdkTracerProviderBuilder): SdkTracerProviderBuilder {
        val primarySpanExporter = createOtlpSpanExporter()
        sdkTracerProviderBuilder.setResource(resources)

        val finalExporter = createSpanExporter(primarySpanExporter)
        val processor = createBatchSpanProcessor(finalExporter)

        spanProcessor = processor
        return sdkTracerProviderBuilder.addSpanProcessor(processor)
    }

    private fun configureMeterProvider(sdkMeterProviderBuilder: SdkMeterProviderBuilder): SdkMeterProviderBuilder {
        val primaryMetricExporter = createOtlpMetricExporter()

        val finalExporter = createMetricExporter(primaryMetricExporter)
        val metricReader = createPeriodicMetricReader(finalExporter)

        metricsReader = metricReader
        return sdkMeterProviderBuilder
            .setResource(resources)
            .registerMetricReader(metricReader)
    }

    private fun createOtlpSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder()
            .setEndpoint(observabilityOptions.otlpEndpoint + TRACES_PATH)
            .setHeaders { observabilityOptions.customHeaders }
            .build()
    }

    private fun createOtlpMetricExporter(): MetricExporter {
        return OtlpHttpMetricExporter.builder()
            .setEndpoint(observabilityOptions.otlpEndpoint + METRICS_PATH)
            .setHeaders { observabilityOptions.customHeaders }
            .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
            .build()
    }

    private fun createSpanExporter(primaryExporter: SpanExporter): SpanExporter {
        val baseExporter = if (observabilityOptions.debug) {
            SpanExporter.composite(
                buildList {
                    add(primaryExporter)
                    add(DebugSpanExporter(logger))
                    telemetryInspector?.let { add(it.spanExporter) }
                }
            )
        } else {
            primaryExporter
        }

        return SamplingTraceExporter(baseExporter, customSampler)
    }

    private fun createMetricExporter(primaryExporter: MetricExporter): MetricExporter {
        return if (observabilityOptions.debug) {
            CompositeMetricExporter(
                buildList {
                    add(primaryExporter)
                    add(DebugMetricExporter(logger))
                    telemetryInspector?.let { add(it.metricExporter) }
                }
            )
        } else {
            primaryExporter
        }
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

    private fun createBatchSpanProcessor(spanExporter: SpanExporter): BatchSpanProcessor {
        return BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
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
        attributes: Attributes
    ) {
        if (observabilityOptions.logsApiLevel.level > severity.severityNumber) return

        otelLogger.logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSeverity(severity)
            .setSeverityText(severity.toString())
            .setAllAttributes(attributes)
            .emit()
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
     * Returns the tracer instance for creating spans.
     *
     * @return Tracer instance
     */
    fun getTracer(): Tracer = otelTracer

    /**
     * Flushes all pending telemetry data (traces, logs, metrics).
     * @return true if all flush operations succeeded, false otherwise
     */
    override fun flush(): Boolean {
        val results = listOfNotNull(
            spanProcessor?.forceFlush(),
            logProcessor?.forceFlush(),
            metricsReader?.forceFlush()
        )

        return CompletableResultCode.ofAll(results)
            .join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .isSuccess
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
        private const val BATCH_MAX_QUEUE_SIZE = 100
        private const val BATCH_SCHEDULE_DELAY_MS = 1000L
        private const val BATCH_EXPORTER_TIMEOUT_MS = 5000L
        private const val BATCH_MAX_EXPORT_SIZE = 10
        private const val METRICS_EXPORT_INTERVAL_MS = 10_000L
        private const val FLUSH_TIMEOUT_SECONDS = 5L

        internal fun createLoggerProcessor(
            sdkLoggerProviderBuilder: SdkLoggerProviderBuilder,
            exportSampler: ExportSampler,
            sdkKey: String,
            resource: Resource,
            logger: LDLogger,
            telemetryInspector: TelemetryInspector?,
            observabilityOptions: ObservabilityOptions,
        ): LogRecordProcessor {
            val primaryLogExporter = createOtlpLogExporter(observabilityOptions)
            sdkLoggerProviderBuilder.setResource(resource)

            val finalExporter = createLogExporter(
                primaryExporter = primaryLogExporter,
                logger = logger,
                telemetryInspector = telemetryInspector,
                observabilityOptions = observabilityOptions
            )

            return SamplingLogProcessor(
                delegate = createBatchLogRecordProcessor(finalExporter),
                sampler = exportSampler
            )
        }

        private fun createOtlpLogExporter(observabilityOptions: ObservabilityOptions): LogRecordExporter {
            return OtlpHttpLogRecordExporter.builder()
                .setEndpoint(observabilityOptions.otlpEndpoint + LOGS_PATH)
                .setHeaders { observabilityOptions.customHeaders }
                .build()
        }

        private fun createLogExporter(
            primaryExporter: LogRecordExporter,
            logger: LDLogger,
            telemetryInspector: TelemetryInspector?,
            observabilityOptions: ObservabilityOptions
        ): LogRecordExporter {
            return if (observabilityOptions.debug) {
                LogRecordExporter.composite(
                    buildList {
                        add(primaryExporter)
                        add(DebugLogExporter(logger))
                        telemetryInspector?.let { add(it.logExporter) }
                    }
                )
            } else {
                primaryExporter
            }
        }

        fun createBatchLogRecordProcessor(logRecordExporter: LogRecordExporter): BatchLogRecordProcessor {
            return BatchLogRecordProcessor.builder(logRecordExporter)
                .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
                .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
                .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
                .build()
        }
    }
}
