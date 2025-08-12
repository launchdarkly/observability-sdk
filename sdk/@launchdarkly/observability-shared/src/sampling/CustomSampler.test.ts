import { SpanKind, Attributes } from '@opentelemetry/api'
import { CustomSampler, defaultSampler } from './CustomSampler'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { it, expect, beforeEach, vi, describe } from 'vitest'
import spanTestScenarios from './span-test-scenarios.json'
import logTestScenarios from './log-test-scenarios.json'
import { SamplingConfig } from '../graph/generated/graphql'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'

interface SpanTestScenario {
	description: string
	samplingConfig: SamplingConfig
	inputSpan: {
		name: string
		attributes?: Record<string, any>
		events?: Array<{
			name: string
			attributes?: Record<string, any>
		}>
	}
	inputLog?: {
		message?: string
		attributes?: Record<string, any>
		severityText?: string
	}
	samplerFunctionCases: Array<{
		type: 'always' | 'never'
		expected_result: {
			sample: boolean
			attributes: Record<string, any> | null
		}
	}>
}

interface LogTestScenario {
	description: string
	samplingConfig: SamplingConfig
	inputLog: {
		message?: string
		attributes?: Record<string, any>
		severityText?: string
	}
	samplerFunctionCases: Array<{
		type: 'always' | 'never'
		expected_result: {
			sample: boolean
			attributes: Record<string, any> | null
		}
	}>
}

// Test helper function that always returns true for sampling
const alwaysSampleFn = vi.fn(() => true)

// Test helper function that always returns false for sampling
const neverSampleFn = vi.fn(() => false)

const samplerFunctions: Record<string, () => boolean> = {
	always: alwaysSampleFn,
	never: neverSampleFn,
}

// Mock implementation of IResource
const mockResource = {
	attributes: {},
	merge: (other: any | null) => (other === null ? mockResource : other),
}

// Helper function to create a mock ReadableSpan
const createMockSpan = ({
	name,
	attributes = {},
	events = [],
}: {
	name: string
	attributes?: Attributes
	events?: Array<{
		name: string
		attributes?: Attributes
		time?: [number, number]
	}>
}) => {
	return {
		name,
		attributes,
		kind: SpanKind.INTERNAL,
		spanContext: () => ({
			traceId: '0',
			spanId: '0',
			traceFlags: 0,
		}),
		parentSpanId: undefined,
		startTime: [0, 0],
		endTime: [0, 0],
		status: { code: 0 },
		links: [],
		events: events.map((e) => ({
			name: e.name,
			attributes: e.attributes || {},
			time: e.time || [0, 0],
		})),
		duration: [0, 0],
		ended: false,
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

beforeEach(() => {
	vi.resetAllMocks()
	vi.restoreAllMocks()
})

const runTestSpanScenarios = (scenarios: SpanTestScenario[]) => {
	scenarios.forEach((scenario) => {
		scenario.samplerFunctionCases.forEach((samplerCase) => {
			const samplerFn = samplerFunctions[samplerCase.type]

			it(`${scenario.description} - ${samplerCase.type}`, () => {
				const config = scenario.samplingConfig as SamplingConfig
				const sampler = new CustomSampler(samplerFn)
				sampler.setConfig(config)
				expect(sampler.isSamplingEnabled()).toBe(true)

				const mockSpan = createMockSpan({
					name: scenario.inputSpan.name,
					attributes: scenario.inputSpan.attributes || {},
					events: scenario.inputSpan.events || [],
				})

				const result = sampler.sampleSpan(mockSpan)

				expect(result.sample).toBe(samplerCase.expected_result.sample)

				if (samplerCase.expected_result.attributes) {
					expect(result.attributes).toEqual(
						samplerCase.expected_result.attributes,
					)
				} else {
					expect(result.attributes).toBeUndefined()
				}
			})
		})
	})
}

const runTestLogScenarios = (scenarios: LogTestScenario[]) => {
	scenarios.forEach((scenario) => {
		scenario.samplerFunctionCases.forEach((samplerCase) => {
			const samplerFn = samplerFunctions[samplerCase.type]

			it(`${scenario.description} - ${samplerCase.type}`, () => {
				const config = scenario.samplingConfig as SamplingConfig
				const sampler = new CustomSampler(samplerFn)
				sampler.setConfig(config)
				expect(sampler.isSamplingEnabled()).toBe(true)

				const result = sampler.sampleLog(
					scenario.inputLog as ReadableLogRecord,
				)

				expect(result.sample).toBe(samplerCase.expected_result.sample)

				if (samplerCase.expected_result.attributes) {
					expect(result.attributes).toEqual(
						samplerCase.expected_result.attributes,
					)
				} else {
					expect(result.attributes).toBeUndefined()
				}
			})
		})
	})
}

describe('Span Sampling Tests', () => {
	runTestSpanScenarios(spanTestScenarios as unknown as SpanTestScenario[])
})

describe('Log Sampling Tests', () => {
	runTestLogScenarios(logTestScenarios as unknown as LogTestScenario[])
})

it('should get approximately the correct number of samples', () => {
	const samples = 100000
	let sampled = 0
	let notSampled = 1

	for (let i = 0; i < samples; i++) {
		const result = defaultSampler(2)
		if (result) {
			sampled += 1
		} else {
			notSampled += 1
		}
	}

	const lowerBound = samples / 2 - (samples / 2) * 0.1
	const upperBound = samples / 2 + (samples / 2) * 0.1

	expect(sampled).toBeGreaterThan(lowerBound)
	expect(sampled).toBeLessThan(upperBound)
	expect(notSampled).toBeGreaterThan(lowerBound)
	expect(notSampled).toBeLessThan(upperBound)
})

it('should not sample things with a sampling ratio of 0', () => {
	const result = defaultSampler(0)
	expect(result).toBe(false)
})

it('should return false if sampling is not enabled', () => {
	const sampler = new CustomSampler(undefined)
	expect(sampler.isSamplingEnabled()).toBe(false)
})
