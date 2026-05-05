package com.launchdarkly.observability.otlp.json.traces

import com.launchdarkly.observability.otlp.json.common.JsonCommonAdapter
import com.launchdarkly.observability.otlp.json.common.JsonStringLong
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData

/**
 * Adapter that converts [SpanData] instances into the OTLP/JSON wire-format types declared
 * in [OtlpJsonTraceModels.kt].
 *
 * Mirrors the Swift `JsonSpanAdapter`.
 */
object JsonSpanAdapter {
    fun toJsonRequest(spans: List<SpanData>): OtlpJsonExportTraceServiceRequest {
        return OtlpJsonExportTraceServiceRequest(resourceSpans = toResourceSpans(spans))
    }

    fun toResourceSpans(spans: List<SpanData>): List<OtlpJsonResourceSpans> {
        if (spans.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<Resource, LinkedHashMap<InstrumentationScopeInfo, MutableList<OtlpJsonSpan>>>()
        for (span in spans) {
            val byScope = grouped.getOrPut(span.resource) { LinkedHashMap() }
            val list = byScope.getOrPut(span.instrumentationScopeInfo) { mutableListOf() }
            list.add(toJsonSpan(span))
        }

        return grouped.map { (resource, byScope) ->
            val scopeSpans = byScope.map { (scopeInfo, spanList) ->
                OtlpJsonScopeSpans(
                    scope = JsonCommonAdapter.toJsonInstrumentationScope(scopeInfo),
                    spans = spanList,
                    schemaUrl = scopeInfo.schemaUrl,
                )
            }
            OtlpJsonResourceSpans(
                resource = JsonCommonAdapter.toJsonResource(resource),
                scopeSpans = scopeSpans,
            )
        }
    }

    internal fun toJsonSpan(span: SpanData): OtlpJsonSpan {
        val attrs = span.attributes
        val attributes = if (attrs.isEmpty) null else JsonCommonAdapter.toJsonAttributes(attrs)

        val events = span.events
            .takeIf { it.isNotEmpty() }
            ?.map { toJsonEvent(it) }
        val links = span.links
            .takeIf { it.isNotEmpty() }
            ?.map { toJsonLink(it) }

        val droppedAttrs = (span.totalAttributeCount - attrs.size()).coerceAtLeast(0)
        val droppedEvents = (span.totalRecordedEvents - span.events.size).coerceAtLeast(0)
        val droppedLinks = (span.totalRecordedLinks - span.links.size).coerceAtLeast(0)

        val ctx = span.spanContext
        val parentCtx = span.parentSpanContext

        return OtlpJsonSpan(
            traceId = ctx.traceId,
            spanId = ctx.spanId,
            traceState = encodeTraceState(ctx.traceState),
            parentSpanId = parentCtx.takeIf { it.isValid }?.spanId,
            flags = ctx.traceFlags.asByte().toInt() and 0xFF,
            name = span.name,
            kind = toJsonSpanKind(span.kind),
            startTimeUnixNano = JsonStringLong(span.startEpochNanos),
            endTimeUnixNano = JsonStringLong(span.endEpochNanos),
            attributes = attributes,
            droppedAttributesCount = droppedAttrs.takeIf { it > 0 },
            events = events,
            droppedEventsCount = droppedEvents.takeIf { it > 0 },
            links = links,
            droppedLinksCount = droppedLinks.takeIf { it > 0 },
            status = toJsonStatus(span.status),
        )
    }

    internal fun toJsonEvent(event: EventData): OtlpJsonSpan.Event {
        val attrs = event.attributes
        val droppedAttrs = (event.totalAttributeCount - attrs.size()).coerceAtLeast(0)
        return OtlpJsonSpan.Event(
            timeUnixNano = JsonStringLong(event.epochNanos),
            name = event.name,
            attributes = if (attrs.isEmpty) null else JsonCommonAdapter.toJsonAttributes(attrs),
            droppedAttributesCount = droppedAttrs.takeIf { it > 0 },
        )
    }

    internal fun toJsonLink(link: LinkData): OtlpJsonSpan.Link {
        val ctx: SpanContext = link.spanContext
        val attrs = link.attributes
        val droppedAttrs = (link.totalAttributeCount - attrs.size()).coerceAtLeast(0)
        return OtlpJsonSpan.Link(
            traceId = ctx.traceId,
            spanId = ctx.spanId,
            traceState = encodeTraceState(ctx.traceState),
            attributes = if (attrs.isEmpty) null else JsonCommonAdapter.toJsonAttributes(attrs),
            droppedAttributesCount = droppedAttrs.takeIf { it > 0 },
            flags = ctx.traceFlags.asByte().toInt() and 0xFF,
        )
    }

    internal fun toJsonSpanKind(kind: SpanKind): OtlpJsonSpanKind = when (kind) {
        SpanKind.INTERNAL -> OtlpJsonSpanKind.SPAN_KIND_INTERNAL
        SpanKind.SERVER -> OtlpJsonSpanKind.SPAN_KIND_SERVER
        SpanKind.CLIENT -> OtlpJsonSpanKind.SPAN_KIND_CLIENT
        SpanKind.PRODUCER -> OtlpJsonSpanKind.SPAN_KIND_PRODUCER
        SpanKind.CONSUMER -> OtlpJsonSpanKind.SPAN_KIND_CONSUMER
    }

    internal fun toJsonStatus(status: StatusData): OtlpJsonStatus = when (status.statusCode) {
        StatusCode.OK -> OtlpJsonStatus(code = OtlpJsonStatusCode.STATUS_CODE_OK)
        StatusCode.ERROR -> OtlpJsonStatus(
            code = OtlpJsonStatusCode.STATUS_CODE_ERROR,
            message = status.description.takeIf { it.isNotEmpty() },
        )
        StatusCode.UNSET, null -> OtlpJsonStatus(code = OtlpJsonStatusCode.STATUS_CODE_UNSET)
    }

    /**
     * Encodes [TraceState] in the W3C tracestate header format (`key1=value1,key2=value2`),
     * per the OTLP spec.
     */
    internal fun encodeTraceState(traceState: TraceState): String? {
        if (traceState.isEmpty) return null
        val sb = StringBuilder()
        traceState.forEach { key, value ->
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(key).append('=').append(value)
        }
        return sb.toString()
    }
}
