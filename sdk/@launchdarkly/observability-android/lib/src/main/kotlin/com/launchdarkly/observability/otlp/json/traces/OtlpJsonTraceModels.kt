package com.launchdarkly.observability.otlp.json.traces

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource

/**
 * OTLP/JSON wire-format types for the traces signal.
 *
 * Mirrors the Swift `OtlpJsonTraceModels.swift`.
 */

data class OtlpJsonExportTraceServiceRequest(
    val resourceSpans: List<OtlpJsonResourceSpans>,
)

data class OtlpJsonResourceSpans(
    val resource: OtlpJsonResource? = null,
    val scopeSpans: List<OtlpJsonScopeSpans>,
    val schemaUrl: String? = null,
)

data class OtlpJsonScopeSpans(
    val scope: OtlpJsonInstrumentationScope? = null,
    val spans: List<OtlpJsonSpan>,
    val schemaUrl: String? = null,
)

data class OtlpJsonSpan(
    /** Lowercase hex string (32 chars), per OTLP/JSON spec deviation. */
    val traceId: String,
    /** Lowercase hex string (16 chars), per OTLP/JSON spec deviation. */
    val spanId: String,
    val traceState: String? = null,
    /** Lowercase hex string (16 chars), per OTLP/JSON spec deviation. */
    val parentSpanId: String? = null,
    val flags: Int? = null,
    val name: String,
    val kind: OtlpJsonSpanKind,
    val startTimeUnixNano: JsonStringLong,
    val endTimeUnixNano: JsonStringLong,
    val attributes: List<OtlpJsonKeyValue>? = null,
    val droppedAttributesCount: Int? = null,
    val events: List<Event>? = null,
    val droppedEventsCount: Int? = null,
    val links: List<Link>? = null,
    val droppedLinksCount: Int? = null,
    val status: OtlpJsonStatus? = null,
) {
    data class Event(
        val timeUnixNano: JsonStringLong,
        val name: String,
        val attributes: List<OtlpJsonKeyValue>? = null,
        val droppedAttributesCount: Int? = null,
    )

    data class Link(
        /** Lowercase hex string (32 chars). */
        val traceId: String,
        /** Lowercase hex string (16 chars). */
        val spanId: String,
        val traceState: String? = null,
        val attributes: List<OtlpJsonKeyValue>? = null,
        val droppedAttributesCount: Int? = null,
        val flags: Int? = null,
    )
}

/**
 * Encoded as the proto-JSON enum string form (e.g. `"SPAN_KIND_CLIENT"`).
 */
enum class OtlpJsonSpanKind {
    SPAN_KIND_UNSPECIFIED,
    SPAN_KIND_INTERNAL,
    SPAN_KIND_SERVER,
    SPAN_KIND_CLIENT,
    SPAN_KIND_PRODUCER,
    SPAN_KIND_CONSUMER,
}

data class OtlpJsonStatus(
    val message: String? = null,
    val code: OtlpJsonStatusCode,
)

enum class OtlpJsonStatusCode {
    STATUS_CODE_UNSET,
    STATUS_CODE_OK,
    STATUS_CODE_ERROR,
}
