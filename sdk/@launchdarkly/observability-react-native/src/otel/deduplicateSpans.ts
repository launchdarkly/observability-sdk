import { ReadableSpan } from '@opentelemetry/sdk-trace-web'

export const FETCH_LIB = '@opentelemetry/instrumentation-fetch'
export const XHR_LIB = '@opentelemetry/instrumentation-xml-http-request'

/**
 * Removes duplicate spans from an array of spans based on span ID and trace ID.
 * This is necessary because some instrumentation libraries may generate
 * duplicate spans for the same HTTP request, which can lead to inflated
 * telemetry data and incorrect metrics. The function prioritizes fetch spans
 * over XHR spans when duplicates are detected.
 */
export const deduplicateSpans = (spans: ReadableSpan[]): ReadableSpan[] => {
	const seenSpans = new Map<string, ReadableSpan>()
	const dedupedSpans: ReadableSpan[] = []

	for (const span of spans) {
		const sc = span.spanContext()
		const traceId = sc.traceId
		const lib = span.instrumentationLibrary.name
		const url = span.attributes['http.url']
		const method = span.attributes['http.method']

		if (!url || !method) {
			dedupedSpans.push(span)
			continue
		}

		const reqId =
			lib === XHR_LIB && span.parentSpanId
				? span.parentSpanId // xhr requests can have a parent span from fetch instrumentation
				: sc.spanId

		const key = `${traceId}|${reqId}`
		const existing = seenSpans.get(key)

		if (!existing) {
			seenSpans.set(key, span)
			dedupedSpans.push(span)
			continue
		}

		const existingLib = existing.instrumentationLibrary.name

		if (existingLib === FETCH_LIB && lib === XHR_LIB) {
			continue
		}

		if (existingLib === XHR_LIB && lib === FETCH_LIB) {
			const idx = dedupedSpans.indexOf(existing)

			if (idx !== -1) {
				// Replace the XHR span with the fetch span
				dedupedSpans[idx] = span
			}

			seenSpans.set(key, span)
			continue
		}
	}

	return dedupedSpans
}
