package com.launchdarkly.observability.client
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import java.util.concurrent.TimeUnit

private const val URL = "https://otel.observability.app.launchdarkly.com:4318"
private const val URL_METRICS = URL + "/v1/metrics"
private const val URL_LOGS = URL + "/v1/logs"
private const val URL_TRACES = URL + "/v1/traces"
private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"

private const val HEADER_LD_PROJECT = "X-LaunchDarkly-Project"

/**
 * Manages instrumentation for LaunchDarkly Observability.
 *
 * @param resources The OpenTelemetry resource describing this service.
 */
class InstrumentationManager(
    private val sdkKey: String,
    private val client: LDClient,
    private val resources: Resource,
) {
    private var meterProvider: SdkMeterProvider
    private var meter: Meter
    private var loggerProvider: SdkLoggerProvider
    private var logger: Logger

    init {
        meterProvider = createMetricsProvider()
        meter = meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        loggerProvider = createLoggerProvider()
        logger = loggerProvider.get(INSTRUMENTATION_SCOPE_NAME)

        // TODO: pretty sure registering globally is a bad idea, but I'm not sure what the norm is here.
        // I see documentation that talks about being able to signal to the auto configuration system
        // via a specially named configurer implementation: autoconfigure SPI interface: SdkTracerProviderConfigurer
        OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .buildAndRegisterGlobal()

    }

    fun recordMetric(metric: Metric) {
        meter.gaugeBuilder(metric.name).build()
            .set(metric.value, metric.attributes)

        // TODO: O11Y-362: convert attributes to LDValue object and pass instead of LDValue.ofNull()
        client.trackMetric(metric.name, LDValue.ofNull(), metric.value)
    }

    fun recordCount(metric: Metric) {
        // TODO: how to handle long and double casting?
        meter.counterBuilder(metric.name).build()
            .add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        meter.counterBuilder(metric.name).build()
            .add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        meter.histogramBuilder(metric.name).build()
            .record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        meter.upDownCounterBuilder(metric.name)
            .build().add(metric.value.toLong(), metric.attributes)
    }

    fun recordLog(message: String, level: String, attributes: Attributes) {
        logger.logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSeverityText(level)
            // TODO: add highlight.session_id when sessions are supported
            .setAllAttributes(attributes)
            .emit()
    }

    // TODO: add otel span optional param that will take precedence over current span and/or created span
    fun recordError(error: Error, attributes: Attributes) {
        // TODO: add error and attributes to span
        // TODO: add highlight.session_id when sessions are supported

        val withExceptionAttrs = attributes.toBuilder()
            .put("exception.type", error.javaClass.simpleName)
            .put("exception.message", error.message)
            .put("exception.stacktrace", error.stackTraceToString())
            .build();

        this.recordLog(error.message ?: "", "error", withExceptionAttrs)
    }

    private fun createMetricsProvider(): SdkMeterProvider {
        // Build a default OTLP HTTP exporter. Users can swap this out later if
        // they wish to use a different exporter implementation.
        val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint(URL_METRICS)
            .addHeader(HEADER_LD_PROJECT, sdkKey)
            .build()

        // Configure a periodic reader that pushes metrics every 10 seconds.
        val metricReader: PeriodicMetricReader =
            PeriodicMetricReader.builder(metricExporter)
                .setInterval(10, TimeUnit.SECONDS)
                .build()

        // Build the SDK MeterProvider with the supplied resources and the
        // configured metric reader.
        return SdkMeterProvider.builder()
            .setResource(resources)
            .registerMetricReader(metricReader)
            .build()
    }

    private fun createLoggerProvider(): SdkLoggerProvider {
        val logExporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint(URL_LOGS)
            .addHeader(HEADER_LD_PROJECT, sdkKey)
            .build()

        // TODO: are these configuration values supposed to be passed in?
        val processor = BatchLogRecordProcessor.builder(logExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(500, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()

        return SdkLoggerProvider.builder()
            .setResource(resources)
            .addLogRecordProcessor(processor)
            .build()
    }
}
