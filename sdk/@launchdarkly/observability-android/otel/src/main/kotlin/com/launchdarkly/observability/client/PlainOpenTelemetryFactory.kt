package com.launchdarkly.observability.client

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider

/**
 * Builds the OpenTelemetry SDK used when no instrumentation provider is supplied.
 *
 * This is the whole reason the OTel-only product can drop `io.opentelemetry.android`: rather than
 * an `OpenTelemetryRum`, it assembles a plain SDK. Nothing here scans the classpath for
 * `AndroidInstrumentation` implementations, so a host application that happens to depend on, say,
 * the OpenTelemetry ANR or OkHttp instrumentation does not get it silently installed.
 *
 * The `session.id` appenders that RUM would otherwise contribute are added explicitly, and the
 * propagators match RUM's defaults so trace context crosses process boundaries identically.
 */
internal fun buildPlainOpenTelemetrySdk(
    session: LDSessionManaging,
    pipeline: OtelPipelineConfigurator,
): OpenTelemetrySdk {
    val tracerProvider = pipeline.configureTracerProvider(
        SdkTracerProvider.builder().addSpanProcessor(SessionIdSpanAppender(session))
    ).build()

    val loggerProvider = pipeline.configureLoggerProvider(
        SdkLoggerProvider.builder().addLogRecordProcessor(SessionIdLogRecordAppender(session))
    ).build()

    val meterProvider = pipeline.configureMeterProvider(SdkMeterProvider.builder()).build()

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setMeterProvider(meterProvider)
        .setPropagators(
            ContextPropagators.create(
                TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(),
                    W3CBaggagePropagator.getInstance(),
                )
            )
        )
        .build()
}
