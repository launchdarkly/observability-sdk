import {
	DeduplicatingExporter,
	FETCH_LIB,
	XHR_LIB,
} from './DeduplicatingExporter'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { ReadableSpan } from '@opentelemetry/sdk-trace-web'
import { ExportResult } from '@opentelemetry/core'
import { describe, it, expect } from 'vitest'

class TestExporter extends OTLPTraceExporter {
	public exported: ReadableSpan[][] = []
	export(spans: ReadableSpan[], cb: (res: ExportResult) => void): void {
		this.exported.push(spans)
		cb({ code: 0 })
	}
	shutdown() {
		return Promise.resolve()
	}
	forceFlush() {
		return Promise.resolve()
	}
}

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

describe('DeduplicatingExporter', () => {
	it('exports non-HTTP spans as-is', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
		const span = makeSpan({
			traceId: '1',
			spanId: 'a',
			instrumentationLibraryName: FETCH_LIB,
		})
		dedup.export([span], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(1)
		expect(testExporter.exported[0][0]).toBe(span)
	})

	it('deduplicates XHR when fetch span exists', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
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
		dedup.export([fetchSpan, xhrSpan], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(1)
		expect(testExporter.exported[0][0]).toBe(fetchSpan)
	})

	it('replaces XHR with fetch if fetch comes after', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
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
		dedup.export([xhrSpan, fetchSpan], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(1)
		expect(testExporter.exported[0][0]).toBe(fetchSpan)
	})

	it('keeps both fetch and XHR if different traceIds', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
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
		dedup.export([fetchSpan, xhrSpan], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(2)
		expect(testExporter.exported[0]).toContain(fetchSpan)
		expect(testExporter.exported[0]).toContain(xhrSpan)
	})

	it('keeps both fetch and XHR if different reqId', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
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
		dedup.export([fetchSpan, xhrSpan], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(2)
		expect(testExporter.exported[0]).toContain(fetchSpan)
		expect(testExporter.exported[0]).toContain(xhrSpan)
	})

	it('exports multiple unrelated HTTP spans', () => {
		const testExporter = new TestExporter()
		const dedup = new DeduplicatingExporter(testExporter)
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
		dedup.export([span1, span2], () => {})
		expect(testExporter.exported.length).toBe(1)
		expect(testExporter.exported[0].length).toBe(2)
		expect(testExporter.exported[0]).toContain(span1)
		expect(testExporter.exported[0]).toContain(span2)
	})
})
