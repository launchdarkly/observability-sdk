package com.launchdarkly.observability.otlp.json.logs

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonAnyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource
import kotlinx.serialization.Serializable

/**
 * OTLP/JSON wire-format types for the logs signal.
 *
 * Mirrors the Swift `OtlpJsonLogModels.swift`.
 */

@Serializable
data class OtlpJsonExportLogsServiceRequest(
    val resourceLogs: List<OtlpJsonResourceLogs>,
)

@Serializable
data class OtlpJsonResourceLogs(
    val resource: OtlpJsonResource? = null,
    val scopeLogs: List<OtlpJsonScopeLogs>,
    val schemaUrl: String? = null,
)

@Serializable
data class OtlpJsonScopeLogs(
    val scope: OtlpJsonInstrumentationScope? = null,
    val logRecords: List<OtlpJsonLogRecord>,
    val schemaUrl: String? = null,
)

@Serializable
data class OtlpJsonLogRecord(
    val timeUnixNano: JsonStringLong? = null,
    val observedTimeUnixNano: JsonStringLong? = null,
    val severityNumber: Int? = null,
    val severityText: String? = null,
    val body: OtlpJsonAnyValue? = null,
    val attributes: List<OtlpJsonKeyValue>? = null,
    val droppedAttributesCount: Int? = null,
    val flags: Int? = null,
    /** Lowercase hex string (32 chars), per OTLP/JSON spec deviation. */
    val traceId: String? = null,
    /** Lowercase hex string (16 chars), per OTLP/JSON spec deviation. */
    val spanId: String? = null,
    val eventName: String? = null,
)
