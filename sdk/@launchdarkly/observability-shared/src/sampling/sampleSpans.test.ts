import { vi, it, expect, beforeEach } from 'vitest'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { SpanKind } from '@opentelemetry/api'
import { sampleSpans } from './sampleSpans'
import { Maybe, SamplingConfig } from '../graph/generated/graphql'
import { ExportSampler, SamplingResult } from './ExportSampler'
import { LogRecord } from '@opentelemetry/api-logs'

// Helper function to create a mock span
const createMockSpan = (name: string, parentId?: string): ReadableSpan => {
	return {
		name: name,
		kind: SpanKind.INTERNAL,
		spanContext: () => ({
			traceId: 'trace-1',
			spanId: name,
			traceFlags: 0,
			isRemote: false,
			toString: () => `${name}`,
		}),
		parentSpanContext: parentId ? {
			traceId: 'trace-1',
			spanId: parentId,
			traceFlags: 0,
			isRemote: false,
		} : undefined,
		startTime: [0, 0],
		endTime: [0, 0],
		status: { code: 0 },
		attributes: {},
		links: [],
		events: [],
		duration: [0, 0],
		ended: true,
		resource: {
			attributes: {},
			merge: () => ({ attributes: {} }),
		},
		instrumentationLibrary: { name: 'test', version: '1.0' },
		droppedAttributesCount: 0,
		droppedEventsCount: 0,
		droppedLinksCount: 0,
	} as unknown as ReadableSpan
}

// Mock implementation of Sampler
class MockSampler implements ExportSampler {
	constructor(
		private mockResults: Record<string, boolean>,
		private enabled: boolean = true,
	) {}

	setConfig(_config?: Maybe<SamplingConfig>): void {}

	sampleSpan(span: ReadableSpan): SamplingResult {
		const spanId = span.spanContext().spanId
		const shouldSample = this.mockResults[spanId] ?? true

		return {
			sample: shouldSample,
			attributes: this.mockResults[spanId]
				? { samplingRatio: 2 }
				: undefined,
		}
	}

	sampleLog(log: LogRecord): SamplingResult {
		return {
			sample: true,
		}
	}

	isSamplingEnabled(): boolean {
		return this.enabled
	}
}

beforeEach(() => {
	vi.clearAllMocks()
})

it('should remove spans that are not sampled', () => {
	const mockSampler = new MockSampler({
		'span-1': true,
		'span-2': false,
	})

	const spans = [
		createMockSpan('span-1'), // Root span - sampled
		createMockSpan('span-2'), // Root span - not sampled
	]

	const sampledSpans = sampleSpans(spans, mockSampler)

	expect(sampledSpans.length).toBe(1)
	expect(sampledSpans.map((span) => span.spanContext().spanId)).toEqual([
		'span-1',
	])

	expect(sampledSpans[0].attributes['samplingRatio']).toBe(2)
})

it('should remove children of spans that are not sampled', () => {
	const mockSampler = new MockSampler({
		parent: false,
		root: true,
	})

	// Create span hierarchy with parent -> child -> grandchild
	const spans = [
		createMockSpan('parent'),
		createMockSpan('child', 'parent'),
		createMockSpan('grandchild', 'child'),
		createMockSpan('root'),
	]

	const sampledSpans = sampleSpans(spans, mockSampler)

	expect(sampledSpans.length).toBe(1)
	expect(sampledSpans[0].name).toBe('root')
})

it('should not apply sampling when sampling is disabled', () => {
	const mockSampler = {
		sampleLog: vi.fn(() => ({ sample: true })),
		sampleSpan: vi.fn(() => ({ sample: true })),
		isSamplingEnabled: vi.fn(() => false),
		setConfig: vi.fn(),
	}

	const spans = [createMockSpan('span-1'), createMockSpan('span-2')]

	const sampledSpans = sampleSpans(spans, mockSampler)
	expect(mockSampler.sampleSpan).not.toHaveBeenCalled()

	expect(sampledSpans.length).toBe(2)
	expect(sampledSpans).toEqual(spans)
})

it('should apply sampling attributes to sampled spans', () => {
	const mockSampler = new MockSampler({
		'span-1': true,
		'span-2': true,
	})

	const spans = [createMockSpan('span-1'), createMockSpan('span-2')]

	const sampledSpans = sampleSpans(spans, mockSampler)

	expect(sampledSpans.length).toBe(2)
	expect(sampledSpans[0].attributes['samplingRatio']).toBe(2)
	expect(sampledSpans[1].attributes['samplingRatio']).toBe(2)
})
