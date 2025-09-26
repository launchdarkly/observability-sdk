package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.SamplingApiService
import com.launchdarkly.observability.sampling.CompositeLogExporter
import com.launchdarkly.observability.sampling.CompositeSpanExporter
import com.launchdarkly.observability.sampling.CustomSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import com.launchdarkly.observability.sampling.SamplingLogExporter
import com.launchdarkly.observability.sampling.SamplingTraceExporter
import com.launchdarkly.observability.utils.notNull
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.session.SessionConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val METRICS_PATH = "/v1/metrics"
private const val LOGS_PATH = "/v1/logs"
private const val TRACES_PATH = "/v1/traces"
private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"

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
    private val otelRUM: OpenTelemetryRum
    private var otelMeter: Meter
    private var otelLogger: Logger
    private var otelTracer: Tracer
    private var customSampler = CustomSampler()
    private val graphqlClient = GraphQLClient(options.backendUrl)
    private val samplingApiService = SamplingApiService(graphqlClient)
    private var inMemorySpanExporter: InMemorySpanExporter? = null
    private var inMemoryLogExporter: InMemoryLogRecordExporter? = null
    private var telemetryInspector: TelemetryInspector? = null

    private var spanProcessor: BatchSpanProcessor? = null
    private var logProcessor: BatchLogRecordProcessor? = null
    private var metricsReader: PeriodicMetricReader? = null

    //TODO: Evaluate if this class should have a close/shutdown method to close this scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val otelRumConfig = OtelRumConfig().setSessionConfig(
            SessionConfig(backgroundInactivityTimeout = options.sessionBackgroundTimeout)
        )

        otelRUM = OpenTelemetryRum.builder(application, otelRumConfig)
            .addLoggerProviderCustomizer { sdkLoggerProviderBuilder, application ->
                val logExporter = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(options.otlpEndpoint + LOGS_PATH)
                    .setHeaders { options.customHeaders }
                    .build()

                sdkLoggerProviderBuilder.setResource(resources)

                if (options.debug) {
                    val exporters = mutableListOf<LogRecordExporter>(logExporter)
                    inMemoryLogExporter = InMemoryLogRecordExporter.create().also {
                        exporters.add(it)
                    }

                    val debugLogExporter = object : LogRecordExporter {
                        override fun export(logRecords: Collection<LogRecordData>): CompletableResultCode {
                            for (record in logRecords) {
                                logger.info(record.toString()) // TODO: Figure out why logger.debug is being blocked by Log.isLoggable is adapter.
                            }
                            return CompletableResultCode.ofSuccess()
                        }

                        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
                        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
                    }

                    exporters.add(debugLogExporter)

                    val compositeExporter = CompositeLogExporter(exporters)
                    val samplingLogExporter = SamplingLogExporter(compositeExporter, customSampler)
                    val logProcessor = getBatchLogRecordProcessor(samplingLogExporter)
                    this@InstrumentationManager.logProcessor = logProcessor

                    sdkLoggerProviderBuilder.addLogRecordProcessor(logProcessor)
                } else {
                    val samplingLogExporter = SamplingLogExporter(logExporter, customSampler)
                    val logProcessor = getBatchLogRecordProcessor(samplingLogExporter)
                    this@InstrumentationManager.logProcessor = logProcessor

                    sdkLoggerProviderBuilder.addLogRecordProcessor(logProcessor)
                }
            }
            .addTracerProviderCustomizer { sdkTracerProviderBuilder, application ->
                val spanExporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(options.otlpEndpoint + TRACES_PATH)
                    .setHeaders { options.customHeaders }
                    .build()

                sdkTracerProviderBuilder.setResource(resources)

                if (options.debug) {
                    val spanExporters = mutableListOf<SpanExporter>(spanExporter)
                    inMemorySpanExporter = InMemorySpanExporter.create().also {
                        spanExporters.add(it)
                    }

                    val debugExporter = object : SpanExporter {
                        override fun export(spans: Collection<SpanData>): CompletableResultCode {
                            for (span in spans) {
                                logger.info(span.toString())
                            }
                            return CompletableResultCode.ofSuccess()
                        }

                        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
                        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
                    }
                    spanExporters.add(debugExporter)

                    val compositeExporter = CompositeSpanExporter(spanExporters)
                    val samplingTraceExporter = SamplingTraceExporter(compositeExporter, customSampler)
                    val spanProcessor = getBatchSpanProcessor(samplingTraceExporter)
                    this@InstrumentationManager.spanProcessor = spanProcessor

                    sdkTracerProviderBuilder.addSpanProcessor(spanProcessor)
                } else {
                    val samplingTraceExporter = SamplingTraceExporter(spanExporter, customSampler)
                    val spanProcessor = getBatchSpanProcessor(samplingTraceExporter)
                    this@InstrumentationManager.spanProcessor = spanProcessor

                    sdkTracerProviderBuilder.addSpanProcessor(spanProcessor)
                }
            }
            .addMeterProviderCustomizer { sdkMeterProviderBuilder, application ->
                val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder()
                    .setEndpoint(options.otlpEndpoint + METRICS_PATH)
                    .setHeaders { options.customHeaders }
                    .build()

                // Configure a periodic reader that pushes metrics every 10 seconds.
                val metricReader: PeriodicMetricReader =
                    PeriodicMetricReader.builder(metricExporter)
                        .setInterval(10, TimeUnit.SECONDS)
                        .build()
                this@InstrumentationManager.metricsReader = metricReader

                sdkMeterProviderBuilder
                    .setResource(resources)
                    .registerMetricReader(metricReader)
            }
            .build()

        if (options.debug) {
            telemetryInspector = notNull(inMemorySpanExporter, inMemoryLogExporter) { spanExporter, logExporter ->
                TelemetryInspector(spanExporter, logExporter)
            }
        }

        otelMeter = otelRUM.openTelemetry.meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        otelLogger = otelRUM.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        otelTracer = otelRUM.openTelemetry.tracerProvider.get(INSTRUMENTATION_SCOPE_NAME)

        scope.launch {
            val samplingConfig = getSamplingConfig()
            if (samplingConfig != null) logger.info("Sampling configuration was successfully loaded")
            customSampler.setConfig(samplingConfig)
        }
    }

    private fun getBatchLogRecordProcessor(logRecordExporter: LogRecordExporter): BatchLogRecordProcessor {
        return BatchLogRecordProcessor.builder(logRecordExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()
    }

    private fun getBatchSpanProcessor(spanExporter: SpanExporter): BatchSpanProcessor {
        return BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()
    }

    fun recordMetric(metric: Metric) {
        otelMeter.gaugeBuilder(metric.name).build()
            .set(metric.value, metric.attributes)
    }

    fun recordCount(metric: Metric) {
        // TODO: handle double casting to long better
        otelMeter.counterBuilder(metric.name).build()
            .add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        otelMeter.counterBuilder(metric.name).build()
            .add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        otelMeter.histogramBuilder(metric.name).build()
            .record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        otelMeter.upDownCounterBuilder(metric.name)
            .build().add(metric.value.toLong(), metric.attributes)
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
        val span = otelTracer
            .spanBuilder("highlight.error")
            .setParent(
                Context.current().with(Span.current())
            )
            .startSpan()

        val attrBuilder = Attributes.builder()
        attrBuilder.putAll(attributes)

        span.recordException(error, attrBuilder.build())
        span.end()
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
        val results = mutableListOf<CompletableResultCode>()

        spanProcessor?.let {
            results.add(it.forceFlush())
        }
        logProcessor?.let {
            results.add(it.forceFlush())
        }
        metricsReader?.let {
            results.add(it.forceFlush())
        }

        // Wait for all flush operations to complete with a single 5 second timeout
        return CompletableResultCode.ofAll(results).join(5, TimeUnit.SECONDS).isSuccess
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
