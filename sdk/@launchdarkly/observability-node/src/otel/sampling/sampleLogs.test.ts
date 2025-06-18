import { vi, it, expect, beforeEach } from 'vitest'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import { sampleLogs } from './sampleLogs'
import { Maybe, SamplingConfig } from '../../graph/generated/graphql'
import { ExportSampler, SamplingResult } from './ExportSampler'

// Helper function to create a mock log record
const createMockLogRecord = (
	severityText: string,
	message: string,
	attributes: Record<string, any> = {},
): ReadableLogRecord => {
	return {
		severityText,
		body: message,
		attributes,
		hrTime: [0, 0],
		hrTimeObserved: [0, 0],
		resource: {
			attributes: {},
			merge: () => ({ attributes: {} }),
		},
		instrumentationScope: { name: 'test', version: '1.0' },
		droppedAttributesCount: 0,
		severityNumber: 0,
		traceId: 'trace-1',
		spanId: 'span-1',
		traceFlags: 0,
	} as unknown as ReadableLogRecord
}

// Mock implementation of Sampler
class MockSampler implements ExportSampler {
	constructor(
		private mockResults: Record<string, boolean>,
		private enabled: boolean = true,
	) {}

	setConfig(_config?: Maybe<SamplingConfig>): void {}

	sampleSpan(_span: any): SamplingResult {
		return {
			sample: true,
		}
	}

	sampleLog(log: ReadableLogRecord): SamplingResult {
		const logId = `${log.severityText}-${log.body}`
		const shouldSample = this.mockResults[logId] ?? true

		return {
			sample: shouldSample,
			attributes: this.mockResults[logId]
				? { samplingRatio: 2 }
				: undefined,
		}
	}

	isSamplingEnabled(): boolean {
		return this.enabled
	}
}

beforeEach(() => {
	vi.clearAllMocks()
})

it('should return all logs when sampling is disabled', () => {
	const mockSampler = new MockSampler({}, false)
	const logs = [
		createMockLogRecord('info', 'test log 1'),
		createMockLogRecord('error', 'test log 2'),
	]

	const sampledLogs = sampleLogs(logs, mockSampler)

	expect(sampledLogs.length).toBe(2)
	expect(sampledLogs).toEqual(logs)
})

it('should remove logs that are not sampled', () => {
	const mockSampler = new MockSampler({
		'info-test log 1': true,
		'error-test log 2': false,
	})

	const logs = [
		createMockLogRecord('info', 'test log 1'),
		createMockLogRecord('error', 'test log 2'),
	]

	const sampledLogs = sampleLogs(logs, mockSampler)

	expect(sampledLogs.length).toBe(1)
	expect(sampledLogs[0].body).toBe('test log 1')
	expect(sampledLogs[0].attributes['samplingRatio']).toBe(2)
})

it('should apply sampling attributes to sampled logs', () => {
	const mockSampler = new MockSampler({
		'info-test log 1': true,
		'error-test log 2': true,
	})

	const logs = [
		createMockLogRecord('info', 'test log 1'),
		createMockLogRecord('error', 'test log 2'),
	]

	const sampledLogs = sampleLogs(logs, mockSampler)

	expect(sampledLogs.length).toBe(2)
	expect(sampledLogs[0].attributes['samplingRatio']).toBe(2)
	expect(sampledLogs[1].attributes['samplingRatio']).toBe(2)
})

it('should handle empty log array', () => {
	const mockSampler = new MockSampler({})
	const logs: ReadableLogRecord[] = []

	const sampledLogs = sampleLogs(logs, mockSampler)

	expect(sampledLogs.length).toBe(0)
})

it('should handle logs with no sampling attributes', () => {
	const mockSampler = new MockSampler({
		'info-test log 1': true,
	})

	const logs = [createMockLogRecord('info', 'test log 1')]

	const sampledLogs = sampleLogs(logs, mockSampler)

	expect(sampledLogs.length).toBe(1)
	expect(sampledLogs[0].attributes['samplingRatio']).toBe(2)
})
