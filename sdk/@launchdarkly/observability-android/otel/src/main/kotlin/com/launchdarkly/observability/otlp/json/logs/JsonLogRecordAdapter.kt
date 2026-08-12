package com.launchdarkly.observability.otlp.json.logs

import com.launchdarkly.observability.otlp.json.common.JsonCommonAdapter
import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.resources.Resource

/**
 * Adapter that converts [LogRecordData] instances into the OTLP/JSON wire-format types
 * declared in [OtlpJsonLogModels.kt].
 *
 * Mirrors the Swift `JsonLogRecordAdapter`.
 */
object JsonLogRecordAdapter {
    fun toJsonRequest(logRecords: List<LogRecordData>): OtlpJsonExportLogsServiceRequest {
        return OtlpJsonExportLogsServiceRequest(resourceLogs = toResourceLogs(logRecords))
    }

    fun toResourceLogs(logRecords: List<LogRecordData>): List<OtlpJsonResourceLogs> {
        if (logRecords.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<Resource, LinkedHashMap<InstrumentationScopeInfo, MutableList<OtlpJsonLogRecord>>>()
        for (record in logRecords) {
            val byScope = grouped.getOrPut(record.resource) { LinkedHashMap() }
            val list = byScope.getOrPut(record.instrumentationScopeInfo) { mutableListOf() }
            list.add(toJsonLogRecord(record))
        }

        return grouped.map { (resource, byScope) ->
            val scopeLogs = byScope.map { (scopeInfo, records) ->
                OtlpJsonScopeLogs(
                    scope = JsonCommonAdapter.toJsonInstrumentationScope(scopeInfo),
                    logRecords = records,
                    schemaUrl = scopeInfo.schemaUrl,
                )
            }
            OtlpJsonResourceLogs(
                resource = JsonCommonAdapter.toJsonResource(resource),
                scopeLogs = scopeLogs,
            )
        }
    }

    internal fun toJsonLogRecord(record: LogRecordData): OtlpJsonLogRecord {
        val severity = record.severity
        val severityNumber = severity.severityNumber
            .takeIf { severity != Severity.UNDEFINED_SEVERITY_NUMBER }
        val severityText = record.severityText ?: severity
            .takeIf { it != Severity.UNDEFINED_SEVERITY_NUMBER }
            ?.name

        val body = record.bodyValue?.let { JsonCommonAdapter.toJsonAnyValue(it) }

        val attrs = record.attributes
        val jsonAttrs = if (attrs.isEmpty) null else JsonCommonAdapter.toJsonAttributes(attrs)

        val spanContext = record.spanContext
        val traceId: String?
        val spanId: String?
        val flags: Int?
        if (spanContext != null && spanContext.isValid) {
            traceId = spanContext.traceId
            spanId = spanContext.spanId
            flags = spanContext.traceFlags.asByte().toInt() and 0xFF
        } else {
            traceId = null
            spanId = null
            flags = null
        }

        val observedNanos = record.observedTimestampEpochNanos
            .takeIf { it != 0L }
            ?.let { JsonStringLong(it) }

        return OtlpJsonLogRecord(
            timeUnixNano = JsonStringLong(record.timestampEpochNanos),
            observedTimeUnixNano = observedNanos,
            severityNumber = severityNumber,
            severityText = severityText,
            body = body,
            attributes = jsonAttrs,
            flags = flags,
            traceId = traceId,
            spanId = spanId,
            eventName = record.eventName,
        )
    }
}
