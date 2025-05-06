/**
 * This file contains sampling configuration types.
 *
 * Examples:
 * ```TypeScript
 * // Sample 1% of all `flag_evaluation` spans
 * const config: SamplingConfig = {
 *   sampling: {
 *     spans: [{
 *       spanName: { operator: 'match', value: 'flag_evaluation' },
 *       samplingRatio: 100
 *     }]
 *   }
 * }
 * ```
 *
 * ```TypeScript
 * // Sample 1% of all `flag_evaluation` spans with the attribute `feature_flag.key` set to `my-feature-flag`
 * const config: SamplingConfig = {
 *   sampling: {
 *     spans: [{
 *       spanName: { operator: 'match', value: 'flag_evaluation' },
 *       attributes: [{
 *         key: 'feature_flag.key',
 *         match: { operator: 'match', value: 'my-feature-flag' }
 *       }],
 *       samplingRatio: 100
 *     }]
 *   }
 * }
 * }
 * ```
 *
 * ```TypeScript
 * // Disable sampling for all logs with the severity text 'debug'
 * const config: SamplingConfig = {
 *   sampling: {
 *     logs: [{
 *       severityText: { operator: 'match', value: 'debug' },
 *       samplingRatio: 0
 *     }]
 *   }
 * }
 * ```
 */

/**
 * A match config for a regex.
 */
export interface RegexMatchConfig {
	operator: 'regex'
	/**
	 * A regex in string format.
	 */
	value: string
}

/**
 * A match config for a basic string match.
 */
export interface BasicMatchConfig {
	operator: 'match'
	value: string | number | boolean
}

export type MatchConfig = RegexMatchConfig | BasicMatchConfig

/**
 * At runtime, for regex matches, we cache the regex to avoid re-compiling it on every match.
 */
export type CachedMatchConfig = MatchConfig & {
	regex?: RegExp
}

/**
 * A match config for an attribute match.
 */
export interface AttributeMatchConfig<TMatchConfig extends MatchConfig> {
	key: string
	match: TMatchConfig
}

/**
 * Configuration for sampling spans based on their name and attributes.
 *
 * In order for a span to match this config it must match the spanName and all attributes.
 * If no spanName is specified, any span matching the specified attribute rules will be sampled.
 * If no attributes are specified, any span with the matching spanName will be sampled.
 * If neither spanName nor attributes are specified, all spans will be sampled based on the ratio.
 */
export interface SpanSamplingConfig<TMatchConfig extends MatchConfig> {
	/**
	 * The name of the span to match. If omitted any span matching the specified attribute rules will be sampled.
	 */
	name?: TMatchConfig
	/**
	 * A list of attribute match configs. If omitted the spans with the matching span name will be sampled.
	 *
	 * In order to match each attributes listed must match. This is an implicit AND operation.
	 */
	attributes?: AttributeMatchConfig<TMatchConfig>[]
	/**
	 * The ratio of spans to sample. Expressed in the form 1/n. So if the ratio is 10, then 1 out of every 10 spans will be sampled.
	 */
	samplingRatio: number
}

/**
 * Configuration for sampling logs based on their attributes and body.
 *
 * In order for a log to match this config it must match all specified matchers.
 * If no matchers are specified, all logs will be sampled based on the ratio.
 */
export interface LogSamplingConfig<TMatchConfig extends MatchConfig> {
	/**
	 * A list of attribute match configs.
	 *
	 * In order to match each attributes listed must match. This is an implicit AND operation.
	 */
	attributes?: AttributeMatchConfig<TMatchConfig>[]

	/**
	 * A match config for the message of the log.
	 */
	message?: TMatchConfig

	/**
	 * The severity text of the log.
	 */
	severityText?: TMatchConfig

	/**
	 *
	 */
	samplingRatio: number
}

/**
 * Sampling configuration.
 */
export interface SamplingConfig<TMatchConfig extends MatchConfig> {
	spans?: SpanSamplingConfig<TMatchConfig>[]
	logs?: LogSamplingConfig<TMatchConfig>[]
}

/**
 * Represents the configuration as it was received from the server.
 */
export type InputSamplingConfig = SamplingConfig<MatchConfig>

/**
 * Cached sampling configuration. Includes memoized regexes.
 */
export type CachedSamplingConfig = SamplingConfig<CachedMatchConfig>
