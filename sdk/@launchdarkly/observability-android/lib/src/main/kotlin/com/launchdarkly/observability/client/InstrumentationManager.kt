package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.interfaces.Metric
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit

private const val METRICS_PATH = "/v1/metrics"
private const val LOGS_PATH = "/v1/logs"
private const val TRACES_PATH = "/v1/traces"
private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"

/**
 * Manages instrumentation for LaunchDarkly Observability.
 *
 * @param resources The OpenTelemetry resource describing this service.
 */
class InstrumentationManager(
    private val application: Application,
    private val sdkKey: String,
    private val resources: Resource,
    options: Options,
) {
    private val otelRUM: OpenTelemetryRum
    private var otelMeter: Meter
    private var otelLogger: Logger
    private var otelTracer: Tracer

    init {

        // resume at figuring out duration.  hopefully desugaring is not required
        val otelRumConfig = OtelRumConfig().setSessionTimeout(options.sessionTimeout)
        otelRUM = OpenTelemetryRum.builder(application, otelRumConfig)
            .addLoggerProviderCustomizer { sdkLoggerProviderBuilder, application ->
                val logExporter = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(options.otlpEndpoint + LOGS_PATH)
                    .setHeaders { options.customHeaders }
                    .build()

                val processor = BatchLogRecordProcessor.builder(logExporter)
                    .setMaxQueueSize(100)
                    .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
                    .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
                    .setMaxExportBatchSize(10)
                    .build()

                sdkLoggerProviderBuilder
                    .setResource(resources)
                    .addLogRecordProcessor(processor)
            }
            .addTracerProviderCustomizer { sdkTracerProviderBuilder, application ->
                val spanExporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(options.otlpEndpoint + TRACES_PATH)
                    .setHeaders { options.customHeaders }
                    .build()

                val spanProcessor = BatchSpanProcessor.builder(spanExporter)
                    .setMaxQueueSize(100)
                    .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
                    .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
                    .setMaxExportBatchSize(10)
                    .build()

                sdkTracerProviderBuilder
                    .setResource(resources)
                    .addSpanProcessor(spanProcessor)
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

                sdkMeterProviderBuilder
                    .setResource(resources)
                    .registerMetricReader(metricReader)
            }
            .build()

        otelMeter = otelRUM.openTelemetry.meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        otelLogger = otelRUM.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        otelTracer = otelRUM.openTelemetry.tracerProvider.get(INSTRUMENTATION_SCOPE_NAME)
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

    fun recordLog(message: String, level: String, attributes: Attributes) {
        otelLogger.logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSeverityText(level)
            .setAllAttributes(attributes)
            .emit()
    }

    // TODO: add otel span optional param that will take precedence over current span and/or created span
    fun recordError(error: Error, attributes: Attributes) {
        val span = otelTracer
            .spanBuilder("highlight.error")
            .setParent(
                Context.current().with(Span.current())
            )
            .startSpan()

        val attrBuilder = Attributes.builder()
        attrBuilder.putAll(attributes)

        // TODO: should exception.cause be added here?  At least one other SDK is doing this
//        error.cause?.let {
//            span.setAttribute("exception.cause", it.message)
//        }

        span.recordException(error, attrBuilder.build())
        span.end()
    }

    fun startSpan(name: String, attributes: Attributes): Span {
        return otelTracer.spanBuilder(name)
            .setParent(Context.current().with(Span.current()))
            .setAllAttributes(attributes)
            .startSpan()
    }
}
