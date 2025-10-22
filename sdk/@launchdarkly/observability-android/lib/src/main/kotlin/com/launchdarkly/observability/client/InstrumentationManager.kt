package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.SamplingApiService
import com.launchdarkly.observability.sampling.CustomSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import com.launchdarkly.observability.sampling.SamplingLogExporter
import com.launchdarkly.observability.sampling.SamplingTraceExporter
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.android.session.SessionConfig
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
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages instrumentation for LaunchDarkly Observability.
 *
 * @param application The application instance.
 * @param sdkKey The SDK key.
 * @param resources The OpenTelemetry resource describing this service.
 * @param logger The logger.
 * @param options Additional options.
 */
class InstrumentationManager(
    private val application: Application,
    private val sdkKey: String,
    private val resources: Resource,
    private val logger: LDLogger,
    private val options: Options,
) {
    companion object {
        private const val METRICS_PATH = "/v1/metrics"
        private const val LOGS_PATH = "/v1/logs"
        private const val TRACES_PATH = "/v1/traces"
        private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"
        const val ERROR_SPAN_NAME = "highlight.error"
        private const val BATCH_MAX_QUEUE_SIZE = 100
        private const val BATCH_SCHEDULE_DELAY_MS = 1000L
        private const val BATCH_EXPORTER_TIMEOUT_MS = 5000L
        private const val BATCH_MAX_EXPORT_SIZE = 10
        private const val METRICS_EXPORT_INTERVAL_MS = 10_000L
        private const val FLUSH_TIMEOUT_SECONDS = 5L
    }

    private val otelRUM: OpenTelemetryRum
    private var otelMeter: Meter
    private var otelLogger: Logger
    private var otelTracer: Tracer
    private var customSampler = CustomSampler()
    private val graphqlClient = GraphQLClient(options.backendUrl)
    private val samplingApiService = SamplingApiService(graphqlClient)
    private var inMemorySpanExporter: InMemorySpanExporter? = null
    private var inMemoryLogExporter: InMemoryLogRecordExporter? = null
    private var inMemoryMetricExporter: InMemoryMetricExporter? = null
    private var telemetryInspector: TelemetryInspector? = null
    private var spanProcessor: BatchSpanProcessor? = null
    private var logProcessor: BatchLogRecordProcessor? = null
    private var metricsReader: PeriodicMetricReader? = null
    private var launchTimeInstrumentation: LaunchTimeInstrumentation? = null
    private val gaugeCache = ConcurrentHashMap<String, DoubleGauge>()
    private val counterCache = ConcurrentHashMap<String, LongCounter>()
    private val histogramCache = ConcurrentHashMap<String, DoubleHistogram>()
    private val upDownCounterCache = ConcurrentHashMap<String, LongUpDownCounter>()

    //TODO: Evaluate if this class should have a close/shutdown method to close this scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val otelRumConfig = createOtelRumConfig()

        val rumBuilder = OpenTelemetryRum.builder(application, otelRumConfig)
            .addLoggerProviderCustomizer { sdkLoggerProviderBuilder, _ ->
                return@addLoggerProviderCustomizer if (options.disableLogs && options.disableErrorTracking) {
                    sdkLoggerProviderBuilder
                } else {
                    configureLoggerProvider(sdkLoggerProviderBuilder)
                }
            }
            .addTracerProviderCustomizer { sdkTracerProviderBuilder, _ ->
                return@addTracerProviderCustomizer if (options.disableTraces && options.disableErrorTracking) {
                    sdkTracerProviderBuilder
                } else {
                    configureTracerProvider(sdkTracerProviderBuilder)
                }
            }
            .addMeterProviderCustomizer { sdkMeterProviderBuilder, _ ->
                return@addMeterProviderCustomizer if (options.disableMetrics) {
                    sdkMeterProviderBuilder
                } else {
                    configureMeterProvider(sdkMeterProviderBuilder)
                }
            }

        if (!options.disableMetrics) {
            launchTimeInstrumentation = LaunchTimeInstrumentation(
                application = application,
                metricRecorder = this::recordHistogram
            ).also {
                rumBuilder.addInstrumentation(it)
            }
        }

        otelRUM = rumBuilder.build()

        initializeTelemetryInspector()
        loadSamplingConfigAsync()

        otelMeter = otelRUM.openTelemetry.meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        otelLogger = otelRUM.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        otelTracer = otelRUM.openTelemetry.tracerProvider.get(INSTRUMENTATION_SCOPE_NAME)
    }

    private fun createOtelRumConfig(): OtelRumConfig {
        val config = OtelRumConfig()
            .setDiskBufferingConfig(
                DiskBufferingConfig.create(
                    enabled = isAnySignalEnabled(options),
                    debugEnabled = options.debug
                )
            )
            .setSessionConfig(SessionConfig(backgroundInactivityTimeout = options.sessionBackgroundTimeout))

        if (options.disableErrorTracking) {
            // Disables Crash reporter
            // "crash" is the instrumentation name defined in [io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation.java]
            config.suppressInstrumentation("crash")
        }

        return config
    }

    private fun isAnySignalEnabled(options: Options): Boolean {
        return !options.disableLogs || !options.disableTraces || !options.disableMetrics || !options.disableErrorTracking
    }

    private fun configureLoggerProvider(sdkLoggerProviderBuilder: SdkLoggerProviderBuilder): SdkLoggerProviderBuilder {
        val primaryLogExporter = createOtlpLogExporter()
        sdkLoggerProviderBuilder.setResource(resources)

        val finalExporter = createLogExporter(primaryLogExporter)
        val processor = createBatchLogRecordProcessor(finalExporter)

        logProcessor = processor
        return sdkLoggerProviderBuilder.addLogRecordProcessor(processor)
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

    private fun createOtlpLogExporter(): LogRecordExporter {
        return OtlpHttpLogRecordExporter.builder()
            .setEndpoint(options.otlpEndpoint + LOGS_PATH)
            .setHeaders { options.customHeaders }
            .build()
    }

    private fun createOtlpSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder()
            .setEndpoint(options.otlpEndpoint + TRACES_PATH)
            .setHeaders { options.customHeaders }
            .build()
    }

    private fun createOtlpMetricExporter(): MetricExporter {
        return OtlpHttpMetricExporter.builder()
            .setEndpoint(options.otlpEndpoint + METRICS_PATH)
            .setHeaders { options.customHeaders }
            .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
            .build()
    }

    private fun createLogExporter(primaryExporter: LogRecordExporter): LogRecordExporter {
        val baseExporter = if (options.debug) {
            LogRecordExporter.composite(
                buildList {
                    add(primaryExporter)
                    add(DebugLogExporter(logger))
                    add(InMemoryLogRecordExporter.create().also { inMemoryLogExporter = it })
                }
            )
        } else {
            primaryExporter
        }

        val conditionalExporter = ConditionalLogRecordExporter(
            delegate = baseExporter,
            allowNormalLogs = !options.disableLogs,
            allowCrashes = !options.disableErrorTracking
        )

        return SamplingLogExporter(conditionalExporter, customSampler)
    }

    private fun createSpanExporter(primaryExporter: SpanExporter): SpanExporter {
        val baseExporter = if (options.debug) {
            SpanExporter.composite(
                buildList {
                    add(primaryExporter)
                    add(DebugSpanExporter(logger))
                    add(InMemorySpanExporter.create().also { inMemorySpanExporter = it })
                }
            )
        } else {
            primaryExporter
        }

        val conditionalExporter = ConditionalSpanExporter(
            delegate = baseExporter,
            allowNormalSpans = !options.disableTraces,
            allowErrorSpans = !options.disableErrorTracking
        )

        return SamplingTraceExporter(conditionalExporter, customSampler)
    }

    private fun createMetricExporter(primaryExporter: MetricExporter): MetricExporter {
        return if (options.debug) {
            CompositeMetricExporter(
                buildList {
                    add(primaryExporter)
                    add(DebugMetricExporter(logger))
                    add(InMemoryMetricExporter.create().also { inMemoryMetricExporter = it })
                }
            )
        } else {
            primaryExporter
        }
    }

    private fun createPeriodicMetricReader(metricExporter: MetricExporter): PeriodicMetricReader {
        // Configure a periodic reader that pushes metrics every 10 seconds.
        return PeriodicMetricReader.builder(metricExporter)
            .setInterval(METRICS_EXPORT_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun initializeTelemetryInspector() {
        if (options.debug) {
            telemetryInspector = TelemetryInspector(inMemorySpanExporter, inMemoryLogExporter, inMemoryMetricExporter)
        }
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

    private fun createBatchLogRecordProcessor(logRecordExporter: LogRecordExporter): BatchLogRecordProcessor {
        return BatchLogRecordProcessor.builder(logRecordExporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
    }

    private fun createBatchSpanProcessor(spanExporter: SpanExporter): BatchSpanProcessor {
        return BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
    }

    fun recordMetric(metric: Metric) {
        val gauge = gaugeCache.getOrPut(metric.name) {
            otelMeter.gaugeBuilder(metric.name).build()
        }
        gauge.set(metric.value, metric.attributes)
    }

    fun recordCount(metric: Metric) {
        // TODO: handle double casting to long better
        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        counter.add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        // It increments the value until the metric is exported, then it’s reset.
        counter.add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        val histogram = histogramCache.getOrPut(metric.name) {
            otelMeter.histogramBuilder(metric.name).build()
        }
        histogram.record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        val upDownCounter = upDownCounterCache.getOrPut(metric.name) {
            otelMeter.upDownCounterBuilder(metric.name).build()
        }
        upDownCounter.add(metric.value.toLong(), metric.attributes)
    }

    fun recordLog(message: String, severity: Severity, attributes: Attributes) {
        otelLogger.logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSeverity(severity)
            .setSeverityText(severity.toString())
            .setAllAttributes(attributes)
            .emit()
    }

    fun recordError(error: Error, attributes: Attributes) {
        if (!options.disableErrorTracking) {
            val span = otelTracer
                .spanBuilder(ERROR_SPAN_NAME)
                .setParent(Context.current().with(Span.current()))
                .startSpan()

            val attrBuilder = Attributes.builder()
            attrBuilder.putAll(attributes)

            span.recordException(error, attrBuilder.build())
            span.end()
        }
    }

    fun startSpan(name: String, attributes: Attributes): Span {
        return otelTracer.spanBuilder(name)
            .setAllAttributes(attributes)
            .startSpan()
    }

    /**
     * Returns the telemetry inspector if debug option is enabled.
     *
     * @return TelemetryInspector instance or null
     */
    fun getTelemetryInspector(): TelemetryInspector? = telemetryInspector

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
    fun flush(): Boolean {
        val results = listOfNotNull(
            spanProcessor?.forceFlush(),
            logProcessor?.forceFlush(),
            metricsReader?.forceFlush()
        )

        // Wait for all flush operations to complete with a single 5 second timeout
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
}
