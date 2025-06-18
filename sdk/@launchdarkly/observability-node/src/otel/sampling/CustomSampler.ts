import { ReadableSpan, TimedEvent } from '@opentelemetry/sdk-trace-base'
import { ExportSampler, SamplingResult } from './ExportSampler'
import {
	LogSamplingConfig,
	SamplingConfig,
	Maybe,
	SpanSamplingConfig,
	MatchConfig,
	AttributeMatchConfig,
	SpanEventMatchConfig,
} from '../../graph/generated/graphql'
import { AnyValue, AnyValueMap } from '@opentelemetry/api-logs'
import { ReadableLogRecord } from '@opentelemetry/sdk-logs'

const ATTR_SAMPLING_RATIO = 'launchdarkly.sampling.ratio'

type RegexCache = Map<string, RegExp>

function isRegexMatchConfig(matchConfig: MatchConfig): boolean {
	return 'regexValue' in matchConfig
}

function isBasicMatchConfig(matchConfig: MatchConfig): boolean {
	return 'matchValue' in matchConfig
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
 * Custom sampler that uses a sampling configuration to determine if a span should be sampled.
 */
export class CustomSampler implements ExportSampler {
	private regexCache: RegexCache = new Map()
	private config: Maybe<SamplingConfig> | undefined
	/**
	 * @param config The sampling configuration.
	 * @param sampler The sampler to use. This is intended to be used for testing purposes.
	 */
	constructor(
		private readonly sampler: (ratio: number) => boolean = defaultSampler,
	) {}
	setConfig(config?: Maybe<SamplingConfig>): void {
		this.config = config
	}

	isSamplingEnabled(): boolean {
		if (this.config?.logs?.length || this.config?.spans?.length) {
			return true
		}
		return false
	}

	/**
	 * Check if a value matches a match config.
	 */
	private matchesValue(
		matchConfig: Maybe<MatchConfig> | undefined,
		value?: AnyValue,
	): boolean {
		if (!matchConfig) {
			return false
		}
		if (isBasicMatchConfig(matchConfig)) {
			return matchConfig.matchValue === value
		}
		if (isRegexMatchConfig(matchConfig)) {
			// Exists due to above match, but could be null.
			const regexValue = matchConfig.regexValue!
			if (regexValue === null) {
				// Is not a valid condition.
				return false
			}
			if (!this.regexCache.has(regexValue)) {
				this.regexCache.set(regexValue, new RegExp(regexValue))
			}
			// Force unwrap because we will always have ensured there is a regex in the cache immediately above.
			const regex = this.regexCache.get(regexValue)!
			if (typeof value === 'string') {
				return regex.test(value)
			}
		}
		// Unknown operator.

		return false
	}

	/**
	 * Check if the attributes match the attribute configs.
	 */
	private matchesAttributes(
		attributeConfigs: Maybe<AttributeMatchConfig[]> | undefined,
		attributes?: AnyValueMap,
	): boolean {
		if (attributeConfigs) {
			for (const attributeConfig of attributeConfigs) {
				let configMatched = false
				// No attributes, so they cannot match.
				if (!attributes) {
					return false
				}
				for (const key of Object.keys(attributes)) {
					if (this.matchesValue(attributeConfig.key, key)) {
						const attributeValue = attributes[key]
						if (
							this.matchesValue(
								attributeConfig.attribute,
								attributeValue,
							)
						) {
							configMatched = true
							break
						}
					}
				}
				if (!configMatched) {
					return false
				}
			}
		}
		return true
	}

	private matchEvent(
		eventConfig: SpanEventMatchConfig,
		event: TimedEvent,
	): boolean {
		if (eventConfig.name) {
			// Match by event name
			if (!this.matchesValue(eventConfig.name, event.name)) {
				return false
			}
		}

		// Match by event attributes if specified
		if (eventConfig.attributes) {
			if (!event.attributes) {
				return false
			}
			if (
				!this.matchesAttributes(
					eventConfig.attributes,
					event.attributes,
				)
			) {
				return false
			}
		}
		return true
	}

	private matchesEvents(
		eventConfigs: Maybe<SpanEventMatchConfig[]> | undefined,
		events: TimedEvent[],
	): boolean {
		if (eventConfigs) {
			for (const eventConfig of eventConfigs) {
				let matched = false
				for (const event of events) {
					if (this.matchEvent(eventConfig, event)) {
						matched = true
						// We only need a single event to match each config.
						break
					}
				}
				if (!matched) {
					return false
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
	private matchesSpanConfig(
		config: SpanSamplingConfig,
		span: ReadableSpan,
	): boolean {
		// Check span name if it's defined in the config
		if (config.name) {
			if (!this.matchesValue(config.name, span.name)) {
				return false
			}
		}

		if (!this.matchesAttributes(config.attributes, span.attributes)) {
			return false
		}

		if (!this.matchesEvents(config.events, span.events)) {
			return false
		}

		// If we reach here, all conditions were met
		return true
	}

	private matchesLogConfig(
		config: LogSamplingConfig,
		record: ReadableLogRecord,
	): boolean {
		if (config.severityText) {
			const severityText = record.severityText
			if (
				typeof severityText === 'string' &&
				!this.matchesValue(config.severityText, severityText)
			) {
				return false
			}
		}

		if (config.message) {
			const message = record.body
			if (
				typeof message === 'string' &&
				!this.matchesValue(config.message, message)
			) {
				return false
			}
		}

		// Check attributes if they're defined in the config
		if (!this.matchesAttributes(config.attributes, record.attributes)) {
			return false
		}
		return true
	}

	/**
	 * Sample a span based on the sampling configuration.
	 *
	 * @param configs The sampling configuration.
	 * @param name The name of the span to sample.
	 * @param attributes The attributes of the span to sample.
	 * @returns The sampling result.
	 */
	sampleSpan(span: ReadableSpan): SamplingResult {
		if (this.config?.spans) {
			for (const spanConfig of this.config.spans) {
				if (this.matchesSpanConfig(spanConfig, span)) {
					return {
						sample: this.sampler(spanConfig.samplingRatio),
						attributes: {
							[ATTR_SAMPLING_RATIO]: spanConfig.samplingRatio,
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
	sampleLog(log: ReadableLogRecord): SamplingResult {
		if (this.config?.logs) {
			for (const logConfig of this.config.logs) {
				if (this.matchesLogConfig(logConfig, log)) {
					return {
						sample: this.sampler(logConfig.samplingRatio),
						attributes: {
							[ATTR_SAMPLING_RATIO]: logConfig.samplingRatio,
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
}
