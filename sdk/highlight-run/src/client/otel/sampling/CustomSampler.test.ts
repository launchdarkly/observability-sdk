import { SpanKind, Attributes } from '@opentelemetry/api'
import { CustomSampler, defaultSampler } from './CustomSampler'
import { InputSamplingConfig } from './config'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { it, expect, beforeEach, vi } from 'vitest'

// Constants used in the tests
const LOG_SPAN_NAME = 'launchdarkly.js.log'
const LOG_SEVERITY_ATTRIBUTE = 'log.severity'
const LOG_MESSAGE_ATTRIBUTE = 'log.message'
const SAMPLING_RATIO_ATTRIBUTE = 'launchdarkly.samplingRatio'

// Test helper function that always returns true for sampling
const alwaysSampleFn = vi.fn(() => true)

// Test helper function that always returns false for sampling
const neverSampleFn = vi.fn(() => false)

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
		events: events.map(e => ({
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

it('should respect samplingRatio and return a NOT_RECORD decision when sampler returns false', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				// Matches all spans.
				samplingRatio: 10, // 1 in 10 sampling rate
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ name: 'test-span' })
	const result = sampler.shouldSample(mockSpan)

	expect(neverSampleFn).toHaveBeenCalledWith(10)
	expect(result.sample).toBe(false)
	expect(result.attributes).toEqual({
		[SAMPLING_RATIO_ATTRIBUTE]: 10,
	})
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on name with exact match and sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					name: { operator: 'match', value: 'test-span' },
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ name: 'test-span' })
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should always sample a span when the span name does not match', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				name: { operator: 'match', value: 'test-span' },
				samplingRatio: 42,
			},
		],
	}

	// We say to neverSampleFn, but the span doesn't match, so it should pass through.
	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ name: 'other-span' })
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span name based on regex and sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					name: {
						operator: 'regex',
						value: 'test-span-\\d+',
					},
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ name: 'test-span-123' })
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn, 'string'],
	['sample', alwaysSampleFn, 42],
	['sample', alwaysSampleFn, true],
	['sample', alwaysSampleFn, false],
	['no sample', neverSampleFn, 'string'],
	['no sample', neverSampleFn, 42],
	['no sample', neverSampleFn, true],
	['no sample', neverSampleFn, false],
])(
	'should match a span based on a single attribute and sample correctly: %s',
	(_, sampleFn, value) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					attributes: [
						{
							key: 'example.attribute',
							match: { operator: 'match', value },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: 'http-request',
			attributes: { 'example.attribute': value },
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match a span when the attribute does not exist', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				attributes: [
					{
						key: 'example.attribute',
						match: { operator: 'match', value: 'value' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: 'http-request',
		attributes: { },
	})
	const result = sampler.shouldSample(mockSpan)

	expect(result.sample).toBe(true)

	expect(result.attributes).toBeUndefined()
});

it('should always sample a span when the attribute does not match', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				attributes: [
					{
						key: 'http.method',
						match: { operator: 'match', value: 'GET' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: 'http-request',
		attributes: { 'http.method': 'POST' },
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it('should respect samplingRatio for logs and return a NOT_RECORD decision when sampler returns false', () => {
	const config: InputSamplingConfig = {
		logs: [
			{
				// Matches all logs.
				samplingRatio: 100, // 1 in 100 sampling rate for debug logs
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: LOG_SPAN_NAME,
		attributes: { [LOG_SEVERITY_ATTRIBUTE]: 'debug' },
	})
	const result = sampler.shouldSample(mockSpan)

	expect(neverSampleFn).toHaveBeenCalledWith(100)

	expect(result.sample).toBe(false)
	expect(result.attributes).toEqual({
		[SAMPLING_RATIO_ATTRIBUTE]: 100,
	})
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should sample a span based on multiple attributes (AND logic): %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' },
						},
						{
							key: 'http.status_code',
							match: { operator: 'match', value: '200' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: 'http-request',
			attributes: { 'http.method': 'GET', 'http.status_code': '200' },
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should always sample a span when the attribute does not match', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				attributes: [
					{
						key: 'http.method',
						match: { operator: 'match', value: 'GET' },
					},
					{
						key: 'http.status_code',
						match: { operator: 'match', value: '200' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: 'http-request',
		attributes: { 'http.method': 'GET', 'http.status_code': '404' },
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on combination of name, attributes and event and then sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					name: { operator: 'match', value: 'http-request' },
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' },
						},
					],
					events: [
						{
							name: { operator: 'match', value: 'db-operation' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, alwaysSampleFn)
		const mockSpan = createMockSpan({
			name: 'http-request',
			attributes: { 'http.method': 'GET' },
			events: [
				{
					name: 'db-operation',
				},
			],
		})
		const result = sampler.shouldSample(mockSpan)

		expect(result.sample).toBe(true)
		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match a span when name matches but attribute does not', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				name: { operator: 'match', value: 'http-request' },
				attributes: [
					{
						key: 'http.method',
						match: { operator: 'match', value: 'GET' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: 'http-request',
		attributes: { 'http.method': 'POST' },
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a log based on severity and sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			logs: [
				{
					severityText: { operator: 'match', value: 'error' },
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: LOG_SPAN_NAME,
			attributes: { [LOG_SEVERITY_ATTRIBUTE]: 'error' },
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match a log when severity does not match', () => {
	const config: InputSamplingConfig = {
		logs: [
			{
				severityText: { operator: 'match', value: 'error' },
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: LOG_SPAN_NAME,
		attributes: { [LOG_SEVERITY_ATTRIBUTE]: 'info' },
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a log based on message content with exact match: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			logs: [
				{
					message: {
						operator: 'match',
						value: 'Connection failed',
					},
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: LOG_SPAN_NAME,
			attributes: { [LOG_MESSAGE_ATTRIBUTE]: 'Connection failed' },
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a log based on message content with regex match: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			logs: [
				{
					message: { operator: 'regex', value: 'Error: .*' },
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: LOG_SPAN_NAME,
			attributes: { [LOG_MESSAGE_ATTRIBUTE]: 'Error: Connection timed out' },
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])('should match a log based on custom attributes: %s', (_, sampleFn) => {
	const config: InputSamplingConfig = {
		logs: [
			{
				attributes: [
					{
						key: 'service.name',
						match: { operator: 'match', value: 'api-gateway' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, sampleFn)
	const mockSpan = createMockSpan({
		name: LOG_SPAN_NAME,
		attributes: {
			'service.name': 'api-gateway',
			[LOG_MESSAGE_ATTRIBUTE]: 'Some message',
		},
	})
	const result = sampler.shouldSample(mockSpan)

	if (sampleFn === alwaysSampleFn) {
		expect(result.sample).toBe(true)
	} else {
		expect(result.sample).toBe(false)
	}

	expect(result.attributes).toEqual({
		[SAMPLING_RATIO_ATTRIBUTE]: 42,
	})
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a log based on combination of severity, message and attributes: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			logs: [
				{
					severityText: { operator: 'match', value: 'error' },
					message: {
						operator: 'regex',
						value: 'Database.*failed',
					},
					attributes: [
						{
							key: 'service.name',
							match: {
								operator: 'match',
								value: 'database-service',
							},
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const attributes: Attributes = {
			[LOG_SEVERITY_ATTRIBUTE]: 'error',
			[LOG_MESSAGE_ATTRIBUTE]: 'Database connection failed',
			'service.name': 'database-service',
		}

		const mockSpan = createMockSpan({
			name: LOG_SPAN_NAME,
			attributes,
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match a log when one criteria in combination does not match', () => {
	const config: InputSamplingConfig = {
		logs: [
			{
				severityText: { operator: 'match', value: 'error' },
				message: { operator: 'regex', value: 'Database.*failed' },
				attributes: [
					{
						key: 'service.name',
						match: {
							operator: 'match',
							value: 'database-service',
						},
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const attributes: Attributes = {
		[LOG_SEVERITY_ATTRIBUTE]: 'error',
		[LOG_MESSAGE_ATTRIBUTE]: 'Database connection failed',
		'service.name': 'api-service', // This doesn't match
	}

	const mockSpan = createMockSpan({
		name: LOG_SPAN_NAME,
		attributes,
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it('should fall back to always sampling when no configuration matches', () => {
	const config: InputSamplingConfig = {
		spans: [],
		logs: [],
	}

	const sampler = new CustomSampler(config)
	const mockSpan = createMockSpan({ name: 'some-span' })
	const result = sampler.shouldSample(mockSpan)

	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it('should identify itself with the correct name', () => {
	const config: InputSamplingConfig = {
		spans: [],
		logs: [],
	}

	const sampler = new CustomSampler(config)
	expect(sampler.toString()).toBe('launchdarkly.CustomSampler')
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

// Span Event sampling tests
it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on event name with exact match and sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'match', value: 'test-event' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'test-span',
			events: [{ name: 'test-event' }] 
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should always sample a span when the event name does not match', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				events: [
					{
						name: { operator: 'match', value: 'test-event' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	// We say to neverSampleFn, but the span doesn't match, so it should pass through.
	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ 
		name: 'test-span',
		events: [{ name: 'other-event' }] 
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on event name using regex and sample correctly: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'regex', value: 'test-event-\\d+' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'test-span',
			events: [{ name: 'test-event-123' }] 
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should sample a span if it contains at least one matching event among many events: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'match', value: 'target-event' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'test-span',
			events: [
				{ name: 'other-event-1' },
				{ name: 'target-event' },
				{ name: 'other-event-2' },
			]
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on a combination of span name, attributes, and events: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					name: { operator: 'match', value: 'http-request' },
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' },
						},
					],
					events: [
						{
							name: { operator: 'match', value: 'http-response' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({
			name: 'http-request',
			attributes: { 'http.method': 'GET' },
			events: [{ name: 'http-response' }]
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match when one criteria in combination does not match (event name)', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				name: { operator: 'match', value: 'http-request' },
				attributes: [
					{
						key: 'http.method',
						match: { operator: 'match', value: 'GET' },
					},
				],
				events: [
					{
						name: { operator: 'match', value: 'http-response' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({
		name: 'http-request',
		attributes: { 'http.method': 'GET' },
		events: [{ name: 'wrong-event-name' }] // This doesn't match
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it('should not match when a span has no events but event matching is required', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				events: [
					{
						name: { operator: 'match', value: 'test-event' },
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ 
		name: 'test-span',
		events: [] // No events
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match multiple event criteria (requires all specified events to be present): %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'match', value: 'event-1' },
						},
						{
							name: { operator: 'match', value: 'event-2' },
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'test-span',
			events: [
				{ name: 'event-1' },
				{ name: 'event-2' },
				{ name: 'event-3' },
			]
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match a span based on event name and event attributes: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'match', value: 'db-operation' },
							attributes: [
								{
									key: 'db.operation',
									match: { operator: 'match', value: 'query' }
								}
							]
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'database-span',
			events: [{ 
				name: 'db-operation', 
				attributes: { 'db.operation': 'query' } 
			}]
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)

it('should not match when event attributes do not match', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				events: [
					{
						name: { operator: 'match', value: 'db-operation' },
						attributes: [
							{
								key: 'db.operation',
								match: { operator: 'match', value: 'query' }
							}
						]
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ 
		name: 'database-span',
		events: [{ 
			name: 'db-operation', 
			attributes: { 'db.operation': 'insert' } // Doesn't match 'query'
		}]
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it('should not match when event has no attributes but attributes are required', () => {
	const config: InputSamplingConfig = {
		spans: [
			{
				events: [
					{
						name: { operator: 'match', value: 'db-operation' },
						attributes: [
							{
								key: 'db.operation',
								match: { operator: 'match', value: 'query' }
							}
						]
					},
				],
				samplingRatio: 42,
			},
		],
	}

	const sampler = new CustomSampler(config, neverSampleFn)
	const mockSpan = createMockSpan({ 
		name: 'database-span',
		events: [{ 
			name: 'db-operation'
			// No attributes provided
		}]
	})
	const result = sampler.shouldSample(mockSpan)

	// If no config matches, we default to sampling
	expect(result.sample).toBe(true)
	expect(result.attributes).toBeUndefined()
})

it.each([
	['sample', alwaysSampleFn],
	['no sample', neverSampleFn],
])(
	'should match multiple event criteria with different attribute requirements: %s',
	(_, sampleFn) => {
		const config: InputSamplingConfig = {
			spans: [
				{
					events: [
						{
							name: { operator: 'match', value: 'event-1' },
							attributes: [
								{
									key: 'status',
									match: { operator: 'match', value: 'success' }
								}
							]
						},
						{
							name: { operator: 'match', value: 'event-2' },
							attributes: [
								{
									key: 'operation',
									match: { operator: 'match', value: 'complete' }
								}
							]
						},
					],
					samplingRatio: 42,
				},
			],
		}

		const sampler = new CustomSampler(config, sampleFn)
		const mockSpan = createMockSpan({ 
			name: 'test-span',
			events: [
				{ 
					name: 'event-1', 
					attributes: { 'status': 'success' } 
				},
				{ 
					name: 'event-2', 
					attributes: { 'operation': 'complete' } 
				},
			]
		})
		const result = sampler.shouldSample(mockSpan)

		if (sampleFn === alwaysSampleFn) {
			expect(result.sample).toBe(true)
		} else {
			expect(result.sample).toBe(false)
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42,
		})
	},
)
