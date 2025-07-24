package com.launchdarkly.observability.client
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import java.time.Instant
import java.util.concurrent.TimeUnit

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
    private lateinit var meterProvider: MeterProvider
    private lateinit var loggerProvider: LoggerProvider

    init {
        initMetrics()
        initLogs()

        // TODO: does this meter provider need to be global?
    }

    fun recordMetric(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").gaugeBuilder(metric.name).build().set(metric.value, metric.attributes)

        // TODO: convert attributes to LDValue object and pass instead of LDValue.ofNull()
        client.trackMetric(metric.name, LDValue.ofNull(), metric.value)
    }

    fun recordCount(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        // TODO: how to handle long and double casting?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build().add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build().add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").histogramBuilder(metric.name).build().record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").upDownCounterBuilder(metric.name).build().add(metric.value.toLong(), metric.attributes)
    }

    fun recordLog(message: String, level: String, attributes: Attributes) {
        loggerProvider.get("com.launchdarkly.observability").logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setAttribute("severityText", level)
            // TODO: add highlight.session_id when sessions are supported
            .setAllAttributes(attributes)
            .emit()
    }

    // TODO: add otel span optional param
    fun recordError(error: Error, attributes: Attributes) {
        // TODO: add error and attributes to span

        val withExceptionAttrs = attributes.toBuilder()
            .put("exception.type", error.javaClass.simpleName)
            .put("exception.message", error.message)
            .put("exception.stacktrace", error.stackTraceToString())
            .build();

        this.recordLog(error.message ?: "", "error", withExceptionAttrs)
    }
    
    private fun initMetrics() {
        // Build a default OTLP HTTP exporter. Users can swap this out later if
        // they wish to use a different exporter implementation.
        val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint("https://otel.observability.app.launchdarkly.com:4318" + "/v1/metrics")
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

        // Configure a periodic reader that pushes metrics every 10 seconds.
        val metricReader: PeriodicMetricReader =
            PeriodicMetricReader.builder(metricExporter)
                .setInterval(10, TimeUnit.SECONDS)
                .build()

        // Build the SDK MeterProvider with the supplied resources and the
        // configured metric reader.
        meterProvider = SdkMeterProvider.builder()
            .setResource(resources)
            .registerMetricReader(metricReader)
            .build()
    }

    private fun initLogs() {
        val logExporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint("https://otel.observability.app.launchdarkly.com:4318" + "/v1/logs")
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

        // TODO: are these configuration values supposed to be passed in?
        val processor = BatchLogRecordProcessor.builder(logExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(500, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()

        loggerProvider = SdkLoggerProvider.builder()
            .setResource(resources)
            .addLogRecordProcessor(processor)
            .build()
    }
}