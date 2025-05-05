import { Context, SpanKind, Attributes, Link } from '@opentelemetry/api';
import { CustomSampler } from './CustomSampler';
import { SamplingDecision } from '@opentelemetry/sdk-trace-base';
import { InputSamplingConfig } from './config';

// Constants used in the tests
const LOG_SPAN_NAME = 'launchdarkly.js.log';
const LOG_SEVERITY_ATTRIBUTE = 'log.severity';
const LOG_MESSAGE_ATTRIBUTE = 'log.message';
const SAMPLING_RATIO_ATTRIBUTE = 'launchdarkly.samplingRatio';

// Mock context, trace ID, and links (not relevant for our tests)
const mockContext: Context = {} as unknown as Context;
const mockTraceId = '0';
const mockLinks: Link[] = [];

// Test helper function that always returns true for sampling
const alwaysSampleFn = vi.fn(() => true);

// Test helper function that always returns false for sampling
const neverSampleFn = vi.fn(() => false);

beforeEach(() => {
	vi.resetAllMocks();
	vi.restoreAllMocks();
});

it('should respect samplingRatio and return a NOT_RECORD decision when sampler returns false', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [
				{
					// Matches all spans.
					samplingRatio: 10 // 1 in 10 sampling rate
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'test-span',
		SpanKind.INTERNAL,
		{},
		mockLinks
	);

	expect(neverSampleFn).toHaveBeenCalledWith(10);
	expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
	expect(result.attributes).toEqual({
		[SAMPLING_RATIO_ATTRIBUTE]: 10
	});
});

// Span sampling tests
it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a span based on name with exact match and sample correctly: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				spans: [
					{
						spanName: { operator: 'match', value: 'test-span' },
						samplingRatio: 42
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			'test-span',
			SpanKind.INTERNAL,
			{},
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 42
		});
	});

it('should always sample a span when the span name does not match', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [
				{
					spanName: { operator: 'match', value: 'test-span' },
					samplingRatio: 1
				}
			]
		}
	};

	// We say to neverSampleFn, but the span doesn't match, so it should pass through.
	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'other-span',
		SpanKind.INTERNAL,
		{},
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a span name based on regex and sample correctly: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				spans: [
					{
						spanName: { operator: 'regex', value: 'test-span-\\d+' },
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			'test-span-123',
			SpanKind.INTERNAL,
			{},
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a span based on a single attribute and sample correctly: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				spans: [
					{
						attributes: [
							{
								key: 'http.method',
								match: { operator: 'match', value: 'GET' }
							}
						],
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			'http-request',
			SpanKind.CLIENT,
			{ 'http.method': 'GET' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it('should always sample a span when the attribute does not match', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [
				{
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' }
						}
					],
					samplingRatio: 1
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'http-request',
		SpanKind.CLIENT,
		{ 'http.method': 'POST' },
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it('should respect samplingRatio for logs and return a NOT_RECORD decision when sampler returns false', () => {
	const config: InputSamplingConfig = {
		sampling: {
			logs: [
				{
					// Matches all logs.
					samplingRatio: 100 // 1 in 100 sampling rate for debug logs
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		LOG_SPAN_NAME,
		SpanKind.INTERNAL,
		{ [LOG_SEVERITY_ATTRIBUTE]: 'debug' },
		mockLinks
	);

	expect(neverSampleFn).toHaveBeenCalledWith(100);

	expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
	expect(result.attributes).toEqual({
		[SAMPLING_RATIO_ATTRIBUTE]: 100
	});
});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should sample a span based on multiple attributes (AND logic): %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				spans: [
					{
						attributes: [
							{
								key: 'http.method',
								match: { operator: 'match', value: 'GET' }
							},
							{
								key: 'http.status_code',
								match: { operator: 'match', value: '200' }
							}
						],
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			'http-request',
			SpanKind.CLIENT,
			{ 'http.method': 'GET', 'http.status_code': '200' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it('should always sample a span when the attribute does not match', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [
				{
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' }
						},
						{
							key: 'http.status_code',
							match: { operator: 'match', value: '200' }
						}
					],
					samplingRatio: 1
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'http-request',
		SpanKind.CLIENT,
		{ 'http.method': 'GET', 'http.status_code': '404' },
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a span based on combination of name and attributes and then sample correctly: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				spans: [
					{
						spanName: { operator: 'match', value: 'http-request' },
						attributes: [
							{
								key: 'http.method',
								match: { operator: 'match', value: 'GET' }
							}
						],
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, alwaysSampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			'http-request',
			SpanKind.CLIENT,
			{ 'http.method': 'GET' },
			mockLinks
		);

		expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it('should not match a span when name matches but attribute does not', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [
				{
					spanName: { operator: 'match', value: 'http-request' },
					attributes: [
						{
							key: 'http.method',
							match: { operator: 'match', value: 'GET' }
						}
					],
					samplingRatio: 1
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'http-request',
		SpanKind.CLIENT,
		{ 'http.method': 'POST' },
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a log based on severity and sample correctly: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				logs: [
					{
						severityText: { operator: 'match', value: 'error' },
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			LOG_SPAN_NAME,
			SpanKind.INTERNAL,
			{ [LOG_SEVERITY_ATTRIBUTE]: 'error' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it('should not match a log when severity does not match', () => {
	const config: InputSamplingConfig = {
		sampling: {
			logs: [
				{
					severityText: { operator: 'match', value: 'error' },
					samplingRatio: 1
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		LOG_SPAN_NAME,
		SpanKind.INTERNAL,
		{ [LOG_SEVERITY_ATTRIBUTE]: 'info' },
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a log based on message content with exact match: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				logs: [
					{
						message: { operator: 'match', value: 'Connection failed' },
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			LOG_SPAN_NAME,
			SpanKind.INTERNAL,
			{ [LOG_MESSAGE_ATTRIBUTE]: 'Connection failed' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a log based on message content with regex match: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				logs: [
					{
						message: { operator: 'regex', value: 'Error: .*' },
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			LOG_SPAN_NAME,
			SpanKind.INTERNAL,
			{ [LOG_MESSAGE_ATTRIBUTE]: 'Error: Connection timed out' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a log based on custom attributes: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				logs: [
					{
						attributes: [
							{
								key: 'service.name',
								match: { operator: 'match', value: 'api-gateway' }
							}
						],
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			LOG_SPAN_NAME,
			SpanKind.INTERNAL,
			{ 'service.name': 'api-gateway', [LOG_MESSAGE_ATTRIBUTE]: 'Some message' },
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it.each([["sample", alwaysSampleFn], ["no sample", neverSampleFn]])
	('should match a log based on combination of severity, message and attributes: %s', (_, sampleFn) => {
		const config: InputSamplingConfig = {
			sampling: {
				logs: [
					{
						severityText: { operator: 'match', value: 'error' },
						message: { operator: 'regex', value: 'Database.*failed' },
						attributes: [
							{
								key: 'service.name',
								match: { operator: 'match', value: 'database-service' }
							}
						],
						samplingRatio: 1
					}
				]
			}
		};

		const sampler = new CustomSampler(config, sampleFn);
		const attributes: Attributes = {
			[LOG_SEVERITY_ATTRIBUTE]: 'error',
			[LOG_MESSAGE_ATTRIBUTE]: 'Database connection failed',
			'service.name': 'database-service'
		};

		const result = sampler.shouldSample(
			mockContext,
			mockTraceId,
			LOG_SPAN_NAME,
			SpanKind.INTERNAL,
			attributes,
			mockLinks
		);

		if (sampleFn === alwaysSampleFn) {
			expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
		} else {
			expect(result.decision).toBe(SamplingDecision.NOT_RECORD);
		}

		expect(result.attributes).toEqual({
			[SAMPLING_RATIO_ATTRIBUTE]: 1
		});
	});

it('should not match a log when one criteria in combination does not match', () => {
	const config: InputSamplingConfig = {
		sampling: {
			logs: [
				{
					severityText: { operator: 'match', value: 'error' },
					message: { operator: 'regex', value: 'Database.*failed' },
					attributes: [
						{
							key: 'service.name',
							match: { operator: 'match', value: 'database-service' }
						}
					],
					samplingRatio: 1
				}
			]
		}
	};

	const sampler = new CustomSampler(config, neverSampleFn);
	const attributes: Attributes = {
		[LOG_SEVERITY_ATTRIBUTE]: 'error',
		[LOG_MESSAGE_ATTRIBUTE]: 'Database connection failed',
		'service.name': 'api-service' // This doesn't match
	};

	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		LOG_SPAN_NAME,
		SpanKind.INTERNAL,
		attributes,
		mockLinks
	);

	// If no config matches, we default to sampling
	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it('should fall back to always sampling when no configuration matches', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [],
			logs: []
		}
	};

	const sampler = new CustomSampler(config);
	const result = sampler.shouldSample(
		mockContext,
		mockTraceId,
		'some-span',
		SpanKind.INTERNAL,
		{},
		mockLinks
	);

	expect(result.decision).toBe(SamplingDecision.RECORD_AND_SAMPLED);
	expect(result.attributes).toBeUndefined();
});

it('should identify itself with the correct name', () => {
	const config: InputSamplingConfig = {
		sampling: {
			spans: [],
			logs: []
		}
	};

	const sampler = new CustomSampler(config);
	expect(sampler.toString()).toBe('launchdarkly.CustomSampler');
});
