package com.launchdarkly.observability.logs

import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] that enqueues every emitted record as a [LogItemPayload] into a shared
 * [EventQueue]. Downstream, a [BatchWorker] pulls items off the queue and hands them to
 * [OtlpLogExporter] for OTLP/JSON export.
 *
 * Replaces the OpenTelemetry `BatchLogRecordProcessor` + `OtlpHttpLogRecordExporter` wiring
 * previously used for logs; mirrors the Swift `LogClient` path that pushes `LogItem`s to the
 * shared event queue.
 *
 * @param eventQueue queue that receives the payloads.
 * @param batchWorker optional worker used to satisfy [forceFlush]; when set, flush drains the
 *   queue before returning.
 */
internal class EventLogRecordProcessor(
    private val eventQueue: EventQueue,
    private val batchWorker: BatchWorker? = null,
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        eventQueue.send(LogItemPayload(logRecord.toLogRecordData()))
    }

    override fun forceFlush(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }
}
