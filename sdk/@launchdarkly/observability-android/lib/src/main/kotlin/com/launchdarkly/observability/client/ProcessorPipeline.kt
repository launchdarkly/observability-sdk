package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.logs.GlobalAttributesLogRecordProcessor
import com.launchdarkly.observability.traces.GlobalAttributesSpanProcessor
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Orders the span and log processors registered on the OpenTelemetry providers:
 * global attributes first, then the customer's processors (from
 * [com.launchdarkly.observability.api.ObservabilityOptions.spanProcessors] /
 * [com.launchdarkly.observability.api.ObservabilityOptions.logProcessors]) in the order given, then
 * the SDK's own sampling/export processors. Customer processors therefore see global attributes,
 * and anything they add is exported.
 */
internal object ProcessorPipeline {

    fun spanProcessors(
        userProcessors: List<SpanProcessor>,
        sdkProcessors: List<SpanProcessor>,
        logger: ObserveLogger,
    ): List<SpanProcessor> = buildList {
        add(GlobalAttributesSpanProcessor())
        userProcessors.forEach { add(GuardedSpanProcessor(it, logger)) }
        addAll(sdkProcessors)
    }

    fun logProcessors(
        userProcessors: List<LogRecordProcessor>,
        sdkProcessors: List<LogRecordProcessor>,
        logger: ObserveLogger,
    ): List<LogRecordProcessor> = buildList {
        add(GlobalAttributesLogRecordProcessor())
        userProcessors.forEach { add(GuardedLogRecordProcessor(it, logger)) }
        addAll(sdkProcessors)
    }
}

/**
 * Isolates a customer [SpanProcessor]: anything it throws is logged and swallowed, so it can neither
 * crash the app nor stop later processors (including export) from running.
 */
internal class GuardedSpanProcessor(
    private val delegate: SpanProcessor,
    private val logger: ObserveLogger,
) : SpanProcessor {

    private val startRequired = guard("isStartRequired", fallback = false) { delegate.isStartRequired }
    private val endRequired = guard("isEndRequired", fallback = false) { delegate.isEndRequired }

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        guard("onStart", fallback = Unit) { delegate.onStart(parentContext, span) }
    }

    override fun isStartRequired(): Boolean = startRequired

    override fun onEnd(span: ReadableSpan) {
        guard("onEnd", fallback = Unit) { delegate.onEnd(span) }
    }

    override fun isEndRequired(): Boolean = endRequired

    override fun forceFlush(): CompletableResultCode =
        guard("forceFlush", fallback = CompletableResultCode.ofFailure()) { delegate.forceFlush() }

    override fun shutdown(): CompletableResultCode =
        guard("shutdown", fallback = CompletableResultCode.ofFailure()) { delegate.shutdown() }

    private inline fun <T> guard(callback: String, fallback: T, block: () -> T): T =
        guardProcessorCall(logger, delegate, callback, fallback, block)
}

/**
 * Isolates a customer [LogRecordProcessor]: anything it throws is logged and swallowed, so it can
 * neither crash the app nor stop later processors (including export) from running.
 */
internal class GuardedLogRecordProcessor(
    private val delegate: LogRecordProcessor,
    private val logger: ObserveLogger,
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        guard("onEmit", fallback = Unit) { delegate.onEmit(context, logRecord) }
    }

    override fun forceFlush(): CompletableResultCode =
        guard("forceFlush", fallback = CompletableResultCode.ofFailure()) { delegate.forceFlush() }

    override fun shutdown(): CompletableResultCode =
        guard("shutdown", fallback = CompletableResultCode.ofFailure()) { delegate.shutdown() }

    private inline fun <T> guard(callback: String, fallback: T, block: () -> T): T =
        guardProcessorCall(logger, delegate, callback, fallback, block)
}

private inline fun <T> guardProcessorCall(
    logger: ObserveLogger,
    processor: Any,
    callback: String,
    fallback: T,
    block: () -> T,
): T = try {
    block()
} catch (t: Throwable) {
    logger.error("Custom processor ${processor.javaClass.name}.$callback threw", t)
    fallback
}
