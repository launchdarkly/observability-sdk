import { Attributes, AttributeValue } from '@opentelemetry/api'
import {
	ReadableSpan,
	TimedEvent,
} from '@opentelemetry/sdk-trace-base'
import {
	CachedMatchConfig,
	SpanSamplingConfig,
	InputSamplingConfig,
	LogSamplingConfig,
	AttributeMatchConfig,
	SpanEventMatchConfig,
} from './config'
import { Sampler, SamplingResult } from './Sampler'
const LOG_SPAN_NAME = 'launchdarkly.js.log'
const LOG_SEVERITY_ATTRIBUTE = 'log.severity'
const LOG_MESSAGE_ATTRIBUTE = 'log.message'

const SAMPLING_RATIO_ATTRIBUTE = 'launchdarkly.samplingRatio'
const SAMPLER_NAME = 'launchdarkly.CustomSampler'

/**
 * Check if a value matches a match config.
 */
function matchesValue(matchConfig: CachedMatchConfig, value?: AttributeValue): boolean {
	if (matchConfig.operator === 'match') {
		return matchConfig.value === value
	} else if (matchConfig.operator === 'regex') {
		if (!matchConfig.regex) {
			matchConfig.regex = new RegExp(matchConfig.value)
		}
		if (typeof value === 'string') {
			return matchConfig.regex.test(value)
		}
	}
	// Unknown operator.

	return false
}

/**
 * Check if the attributes match the attribute configs.
 */
function matchesAttributes(
	attributeConfigs: AttributeMatchConfig<CachedMatchConfig>[] | undefined,
	attributes: Attributes,
): boolean {
	if (attributeConfigs) {
		for (const attributeConfig of attributeConfigs) {
			const attributeValue = attributes[attributeConfig.key]
			if (!matchesValue(attributeConfig.match, attributeValue)) {
				return false
			}
		}
	}
	return true
}

function matchEvent(
	eventConfig: SpanEventMatchConfig<CachedMatchConfig>,
	event: TimedEvent,
): boolean {
	// Match by event name
	if (!matchesValue(eventConfig.name, event.name)) {
		return false;
	}

	// Match by event attributes if specified
	if (eventConfig.attributes) {
		if (!event.attributes) {
			return false;
		}
		if (!matchesAttributes(eventConfig.attributes, event.attributes)) {
			return false;
		}
	}
	return true;
}

function matchesEvents(
	eventConfigs: SpanEventMatchConfig<CachedMatchConfig>[] | undefined,
	events: TimedEvent[],
): boolean {
	if (eventConfigs) {
		for (const eventConfig of eventConfigs) {
			let matched = false;
			for (const event of events) {
				if (matchEvent(eventConfig, event)) {
					matched = true;
					// We only need a single event to match each config. 
					break;
				}
			}
			if (!matched) {
				return false;
			}
		}
	}
	return true
}

/**
 * Attempts to match the span to the config. The span will match only if all defined conditions are met.
 *
 * @param config The config to match against.
 * @param name The name of the span to match.
 * @param attributes The attributes of the span to match.
 * @returns True if the span matches the config, false otherwise.
 */
function matchesSpanConfig(
	config: SpanSamplingConfig<CachedMatchConfig>,
	span: ReadableSpan,
): boolean {
	// Check span name if it's defined in the config
	if (config.name) {
		if (!matchesValue(config.name, span.name)) {
			return false
		}
	}

	if (!matchesAttributes(config.attributes, span.attributes)) {
		return false
	}

	if (!matchesEvents(config.events, span.events)) {
		return false
	}

	// If we reach here, all conditions were met
	return true
}

function matchesLogConfig(
	config: LogSamplingConfig<CachedMatchConfig>,
	attributes: Attributes,
): boolean {
	if (config.severityText) {
		const severityText = attributes[LOG_SEVERITY_ATTRIBUTE]
		if (
			typeof severityText === 'string' &&
			!matchesValue(config.severityText, severityText)
		) {
			return false
		}
	}

	if (config.message) {
		const message = attributes[LOG_MESSAGE_ATTRIBUTE]
		if (
			typeof message === 'string' &&
			!matchesValue(config.message, message)
		) {
			return false
		}
	}

	// Check attributes if they're defined in the config
	if (!matchesAttributes(config.attributes, attributes)) {
		return false
	}

	return true
}

/**
 * Determine if an item should be sampled based on the sampling ratio.
 *
 * This function is not used for any purpose requiring cryptographic security.
 *
 * @param ratio The sampling ratio.
 * @returns True if the item should be sampled, false otherwise.
 */
export function defaultSampler(ratio: number) {
	const truncated = Math.trunc(ratio)
	// A ratio of 1 means 1 in 1. So that will always sample. No need
	// to draw a random number.
	if (truncated === 1) {
		return true
	}

	// A ratio of 0 means 0 in 1. So that will never sample.
	if (truncated === 0) {
		return false
	}

	// Math.random() * truncated) would return 0, 1, ... (ratio - 1).
	// Checking for any number in the range will have approximately a 1 in X
	// chance. So we check for 0 as it is part of any range.
	return Math.floor(Math.random() * truncated) === 0
}

/**
 * Sample a span based on the sampling configuration.
 *
 * @param configs The sampling configuration.
 * @param name The name of the span to sample.
 * @param attributes The attributes of the span to sample.
 * @returns The sampling result.
 */
function sampleSpan(
	sampler: (ratio: number) => boolean,
	configs: SpanSamplingConfig<CachedMatchConfig>[] | undefined,
	span: ReadableSpan,
): SamplingResult {
	if (configs) {
		for (const spanConfig of configs) {
			if (matchesSpanConfig(spanConfig, span)) {
				return {
					sample: sampler(spanConfig.samplingRatio),
					attributes: {
						[SAMPLING_RATIO_ATTRIBUTE]: spanConfig.samplingRatio,
					},
				}
			}
		}
	}
	// Didn't match any sampling config, or there were no configs, so we sample it.
	return {
		sample: true,
	}
}

/**
 * Sample a log based on the sampling configuration.
 *
 * @param configs The sampling configuration.
 * @param attributes The attributes of the log to sample.
 * @returns The sampling result.
 */
function sampleLog(
	sampler: (ratio: number) => boolean,
	configs: LogSamplingConfig<CachedMatchConfig>[] | undefined,
	span: ReadableSpan,
): SamplingResult {
	if (configs) {
		for (const logConfig of configs) {
			if (matchesLogConfig(logConfig, span.attributes)) {
				return {
					sample: sampler(logConfig.samplingRatio),
					attributes: {
						[SAMPLING_RATIO_ATTRIBUTE]: logConfig.samplingRatio,
					},
				}
			}
		}
	}

	// Didn't match any sampling config, or there were no configs, so we sample it.
	return {
		sample: true,
	}
}

/**
 * Custom sampler that uses a sampling configuration to determine if a span should be sampled.
 */
export class CustomSampler implements Sampler {
	/**
	 * @param config The sampling configuration.
	 * @param sampler The sampler to use. This is intended to be used for testing purposes.
	 */
	constructor(
		private readonly config: InputSamplingConfig,
		private readonly sampler: (ratio: number) => boolean = defaultSampler,
	) { }

	shouldSample(span: ReadableSpan): SamplingResult {
		// Logs are encoded into special spans, so process those special spans using the log rules.
		if (span.name === LOG_SPAN_NAME) {
			return sampleLog(
				this.sampler,
				this.config.logs,
				span,
			)
		}

		return sampleSpan(
			this.sampler,
			this.config.spans,
			span,
		)
	}

	toString(): string {
		return SAMPLER_NAME
	}
}
