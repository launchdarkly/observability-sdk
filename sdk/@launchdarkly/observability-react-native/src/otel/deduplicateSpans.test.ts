import { deduplicateSpans, FETCH_LIB, XHR_LIB } from './deduplicateSpans'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'
import { describe, it, expect } from 'vitest'

function makeSpan({
	traceId,
	spanId,
	parentSpanId,
	instrumentationLibraryName,
	url,
	method,
	name = 'test-span',
}: {
	traceId: string
	spanId: string
	parentSpanId?: string
	instrumentationLibraryName: string
	url?: string
	method?: string
	name?: string
}): ReadableSpan {
	return {
		name,
		spanContext: () => ({ traceId, spanId }),
		parentSpanContext: parentSpanId
			? { spanId: parentSpanId, traceId, traceFlags: 0 }
			: undefined,
		instrumentationScope: {
			name: instrumentationLibraryName,
			version: '1.0.0',
		},
		attributes: {
			...(url ? { 'http.url': url } : {}),
			...(method ? { 'http.method': method } : {}),
		},
	} as ReadableSpan
}

describe('deduplicateSpans', () => {
	it('exports non-HTTP spans as-is', () => {
		const span = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
		})
		const deduped = deduplicateSpans([span])
		expect(deduped.length).toBe(1)
		expect(deduped[0]).toBe(span)
	})

	it('deduplicates XHR when fetch span exists', () => {
		const fetchSpan = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const xhrSpan = makeSpan({
			traceId: '1',
			spanId: 'b',
			parentSpanId: 'a',
			instrumentationLibraryName: XHR_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const deduped = deduplicateSpans([fetchSpan, xhrSpan])
		expect(deduped.length).toBe(1)
		expect(deduped[0]).toBe(fetchSpan)
	})

	it('replaces XHR with fetch if fetch comes after', () => {
		const xhrSpan = makeSpan({
			traceId: '1',
			spanId: 'b',
			parentSpanId: 'a',
			instrumentationLibraryName: XHR_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const fetchSpan = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const deduped = deduplicateSpans([xhrSpan, fetchSpan])
		expect(deduped.length).toBe(1)
		expect(deduped[0]).toBe(fetchSpan)
	})

	it('keeps both fetch and XHR if different traceIds', () => {
		const fetchSpan = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const xhrSpan = makeSpan({
			traceId: '2',
			spanId: 'b',
			parentSpanId: 'a',
			instrumentationLibraryName: XHR_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const deduped = deduplicateSpans([fetchSpan, xhrSpan])
		expect(deduped.length).toBe(2)
		expect(deduped).toContain(fetchSpan)
		expect(deduped).toContain(xhrSpan)
	})

	it('keeps both fetch and XHR if different reqId', () => {
		const fetchSpan = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const xhrSpan = makeSpan({
			traceId: '1',
			spanId: 'b',
			parentSpanId: 'c',
			instrumentationLibraryName: XHR_LIB,
			url: 'http://x',
			method: 'GET',
		})
		const deduped = deduplicateSpans([fetchSpan, xhrSpan])
		expect(deduped.length).toBe(2)
		expect(deduped).toContain(fetchSpan)
		expect(deduped).toContain(xhrSpan)
	})

	it('exports multiple unrelated HTTP spans', () => {
		const span1 = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://a',
			method: 'GET',
		})
		const span2 = makeSpan({
			traceId: '2',
			spanId: 'b',
			instrumentationLibraryName: FETCH_LIB,
			url: 'http://b',
			method: 'POST',
		})
		const deduped = deduplicateSpans([span1, span2])
		expect(deduped.length).toBe(2)
		expect(deduped).toContain(span1)
		expect(deduped).toContain(span2)
	})
})
