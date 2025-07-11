import { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { Attributes } from '@opentelemetry/api'
import { merge } from '@opentelemetry/core'
import { ExportSampler } from './ExportSampler'

export function cloneReadableSpanWithAttributes(
	span: ReadableSpan,
	attributes: Attributes,
): ReadableSpan {
	const spanContext = span.spanContext()
	const cloned = {
		name: span.name,
		kind: span.kind,
		spanContext: () => spanContext,
		parentSpanContext: span.parentSpanContext,
		startTime: span.startTime,
		endTime: span.endTime,
		status: span.status,
		attributes: merge(span.attributes, attributes),
		links: span.links,
		events: span.events,
		duration: span.duration,
		ended: span.ended,
		resource: span.resource,
		instrumentationScope: span.instrumentationScope,
		droppedAttributesCount: span.droppedAttributesCount,
		droppedEventsCount: span.droppedEventsCount,
		droppedLinksCount: span.droppedLinksCount,
	}
	return cloned
}

export function sampleSpans(
	items: ReadableSpan[],
	sampler: ExportSampler,
): ReadableSpan[] {
	if (!sampler.isSamplingEnabled()) {
		return items
	}
	const omittedSpansIds: string[] = []
	const spanById: Record<string, ReadableSpan> = {}
	const childrenByParentId: Record<string, string[]> = {}

	// The first pass we sample items which are directly impacted by a sampling decision.
	// We also build a map of children spans by parent span id, which allows us to quickly traverse the span tree.
	for (const item of items) {
		if (item.parentSpanContext?.spanId) {
			childrenByParentId[item.parentSpanContext.spanId] =
				childrenByParentId[item.parentSpanContext.spanId] || []
			childrenByParentId[item.parentSpanContext.spanId].push(
				item.spanContext().spanId,
			)
		}
		const sampleResult = sampler.sampleSpan(item)
		if (sampleResult.sample) {
			if (sampleResult.attributes) {
				spanById[item.spanContext().spanId] =
					cloneReadableSpanWithAttributes(
						item,
						sampleResult.attributes,
					)
			} else {
				spanById[item.spanContext().spanId] = item
			}
		} else {
			omittedSpansIds.push(item.spanContext().spanId)
		}
	}

	// Find all children of spans that have been sampled out and remove them.
	// Repeat until there are no more children to remove.
	while (omittedSpansIds.length > 0) {
		const spanId = omittedSpansIds.shift()
		const affectedSpans: string[] | undefined = childrenByParentId[spanId!]
		if (!affectedSpans) {
			continue
		}

		for (const spanIdToRemove of affectedSpans) {
			delete spanById[spanIdToRemove]
			omittedSpansIds.push(spanIdToRemove)
		}
	}
	return Object.values(spanById)
}
