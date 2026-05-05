package com.launchdarkly.observability.otlp.json.traces

import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import com.launchdarkly.observability.otlp.json.common.OtlpJsonInstrumentationScope
import com.launchdarkly.observability.otlp.json.common.OtlpJsonKeyValue
import com.launchdarkly.observability.otlp.json.common.OtlpJsonResource
import kotlinx.serialization.Serializable

/**
 * OTLP/JSON wire-format types for the traces signal.
 *
 * Mirrors the Swift `OtlpJsonTraceModels.swift`.
 */

@Serializable
data class OtlpJsonExportTraceServiceRequest(
    val resourceSpans: List<OtlpJsonResourceSpans>,
)

@Serializable
data class OtlpJsonResourceSpans(
    val resource: OtlpJsonResource? = null,
    val scopeSpans: List<OtlpJsonScopeSpans>,
    val schemaUrl: String? = null,
)

@Serializable
data class OtlpJsonScopeSpans(
    val scope: OtlpJsonInstrumentationScope? = null,
    val spans: List<OtlpJsonSpan>,
    val schemaUrl: String? = null,
)

@Serializable
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
    @Serializable
    data class Event(
        val timeUnixNano: JsonStringLong,
        val name: String,
        val attributes: List<OtlpJsonKeyValue>? = null,
        val droppedAttributesCount: Int? = null,
    )

    @Serializable
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
@Serializable
enum class OtlpJsonSpanKind {
    SPAN_KIND_UNSPECIFIED,
    SPAN_KIND_INTERNAL,
    SPAN_KIND_SERVER,
    SPAN_KIND_CLIENT,
    SPAN_KIND_PRODUCER,
    SPAN_KIND_CONSUMER,
}

@Serializable
data class OtlpJsonStatus(
    val message: String? = null,
    val code: OtlpJsonStatusCode,
)

@Serializable
enum class OtlpJsonStatusCode {
    STATUS_CODE_UNSET,
    STATUS_CODE_OK,
    STATUS_CODE_ERROR,
}
