import { Context, SpanKind, Link, Attributes } from '@opentelemetry/api';
import { Sampler, SamplingDecision, SamplingResult } from '@opentelemetry/sdk-trace-base';
import { CachedMatchConfig, SpanSamplingConfig, InputSamplingConfig, LogSamplingConfig, AttributeMatchConfig } from './config';

const LOG_SPAN_NAME = 'launchdarkly.js.log';
const LOG_SEVERITY_ATTRIBUTE = 'log.severity';
const LOG_MESSAGE_ATTRIBUTE = 'log.message';

const SAMPLING_RATIO_ATTRIBUTE = 'launchdarkly.samplingRatio';
const SAMPLER_NAME = 'launchdarkly.CustomSampler';

/**
 * Check if a value matches a match config.
 */
function matchesValue(matchConfig: CachedMatchConfig, value: string): boolean {
	if (matchConfig.operator === 'match') {
		return matchConfig.value === value;
	} else if (matchConfig.operator === 'regex') {
		if (!matchConfig.regex) {
			matchConfig.regex = new RegExp(matchConfig.value);
		}
		return matchConfig.regex.test(value);
	}
	// Unknown operator.

	return false;
}

/**
 * Check if the attributes match the attribute configs.
 */
function matchesAttributes(attributeConfigs: AttributeMatchConfig<CachedMatchConfig>[] | undefined, attributes: Attributes): boolean {
	if (attributeConfigs) {
		for (const attributeConfig of attributeConfigs) {
			const attributeValue = attributes[attributeConfig.key];

			if (typeof attributeValue === 'string') {
				if (!matchesValue(attributeConfig.match, attributeValue)) {
					return false;
				}
			}
		}
	}
	return true;
}

/**
 * Attempts to match the span to the config. The span will match only if all defined conditions are met.
 * 
 * @param config The config to match against.
 * @param spanName The name of the span to match.
 * @param attributes The attributes of the span to match.
 * @returns True if the span matches the config, false otherwise.
 */
function matchesSpanConfig(config: SpanSamplingConfig<CachedMatchConfig>, spanName: string, attributes: Attributes): boolean {
	// Check span name if it's defined in the config
	if (config.spanName) {
		if (!matchesValue(config.spanName, spanName)) {
			return false;
		}
	}

	// Check attributes if they're defined in the config
	if (!matchesAttributes(config.attributes, attributes)) {
		return false;
	}

	// If we reach here, all conditions were met
	return true;
}

function matchesLogConfig(config: LogSamplingConfig<CachedMatchConfig>, attributes: Attributes): boolean {
	if (config.severityText) {
		const severityText = attributes[LOG_SEVERITY_ATTRIBUTE];
		if (typeof severityText === 'string' && !matchesValue(config.severityText, severityText)) {
			return false;
		}
	}

	if (config.message) {
		const message = attributes[LOG_MESSAGE_ATTRIBUTE];
		if (typeof message === 'string' && !matchesValue(config.message, message)) {
			return false;
		}
	}

	// Check attributes if they're defined in the config
	if (!matchesAttributes(config.attributes, attributes)) {
		return false;
	}


	return true;
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
	const truncated = Math.trunc(ratio);
	// A ratio of 1 means 1 in 1. So that will always sample. No need
	// to draw a random number.
	if (truncated === 1) {
		return true;
	}

	// A ratio of 0 means 0 in 1. So that will never sample.
	if (truncated === 0) {
		return false;
	}

	// Math.random() * truncated) would return 0, 1, ... (ratio - 1).
	// Checking for any number in the range will have approximately a 1 in X
	// chance. So we check for 0 as it is part of any range.
	return Math.floor(Math.random() * truncated) === 0;
}

/**
 * Sample a span based on the sampling configuration.
 * 
 * @param configs The sampling configuration.
 * @param spanName The name of the span to sample.
 * @param attributes The attributes of the span to sample.
 * @returns The sampling result.
 */
function sampleSpan(sampler: (ratio: number) => boolean, configs: SpanSamplingConfig<CachedMatchConfig>[] | undefined, spanName: string, attributes: Attributes): SamplingResult {
	if (configs) {
		for (const spanConfig of configs) {
			if (matchesSpanConfig(spanConfig, spanName, attributes)) {
				return {
					decision: sampler(spanConfig.samplingRatio) ? SamplingDecision.RECORD_AND_SAMPLED : SamplingDecision.NOT_RECORD,
					attributes: {
						[SAMPLING_RATIO_ATTRIBUTE]: spanConfig.samplingRatio,
					},
				}
			}
		}
	}
	// Didn't match any sampling config, or there were no configs, so we sample it.
	return {
		decision: SamplingDecision.RECORD_AND_SAMPLED,
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
	attributes: Attributes
): SamplingResult {
	if (configs) {
		for (const logConfig of configs) {
			if (matchesLogConfig(logConfig, attributes)) {
				return {
					decision: sampler(logConfig.samplingRatio) ? SamplingDecision.RECORD_AND_SAMPLED : SamplingDecision.NOT_RECORD,
					attributes: {
						[SAMPLING_RATIO_ATTRIBUTE]: logConfig.samplingRatio,
					},
				}
			}
		}
	}

	// Didn't match any sampling config, or there were no configs, so we sample it.
	return {
		decision: SamplingDecision.RECORD_AND_SAMPLED,
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
		private readonly sampler: (ratio: number) => boolean = defaultSampler
	) {
	}

	shouldSample(
		context: Context,
		traceId: string,
		spanName: string,
		spanKind: SpanKind,
		attributes: Attributes,
		links: Link[]): SamplingResult {
		// Logs are encoded into special spans, so process those special spans using the log rules.
		if (spanName === LOG_SPAN_NAME) {
			return sampleLog(this.sampler, this.config.sampling?.logs, attributes);
		}

		return sampleSpan(this.sampler, this.config.sampling?.spans, spanName, attributes);
	}

	toString(): string {
		return SAMPLER_NAME;
	}
}

