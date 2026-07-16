import { LDClientMin, LDPlugin } from './plugin'
import { ReactNativeOptions } from '../api/Options'
import { TrackProperties } from '../api/TrackProperties'
import { flattenTrackProperties } from '../utils/trackAttributes'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { startInternalActiveSpan } from '../internal/internalSpans'
import { ExposureDeduper } from '@launchdarkly/observability-shared'
import { _LDObserve } from '../sdk/LDObserve'
import type {
	LDEvaluationDetail,
	LDPluginEnvironmentMetadata,
	LDPluginMetadata,
} from '@launchdarkly/js-sdk-common'
import { Attributes } from '@opentelemetry/api'
import {
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'
import {
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_VALUE_ATTR,
	FEATURE_FLAG_VARIATION_INDEX_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_CONTEXT_ATTR,
	FEATURE_FLAG_CONTEXT_ID_ATTR,
	FEATURE_FLAG_ENV_ATTR,
	FEATURE_FLAG_REASON_ATTRS,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_SCOPE,
	getCanonicalKey,
	getContextKeys,
	LD_IDENTIFY_RESULT_STATUS,
	LD_INTERNAL_ATTR,
	LD_TRACK_SPAN_NAME,
} from '../constants/featureFlags'
import type { LDEvaluationReason } from '@launchdarkly/js-sdk-common'
import {
	IdentifySeriesData,
	IdentifySeriesResult,
	Hook,
	IdentifySeriesContext,
	EvaluationSeriesContext,
	EvaluationSeriesData,
	TrackSeriesContext,
} from '@launchdarkly/react-native-client-sdk'

class TracingHook implements Hook {
	private metaAttributes: Attributes = {}
	private readonly exposureDeduper: ExposureDeduper

	constructor(
		private metadata: LDPluginEnvironmentMetadata,
		private readonly _options?: ReactNativeOptions,
	) {
		this.exposureDeduper = new ExposureDeduper(
			_options?.flagExposureDedupeWindowMillis,
		)
		this.metaAttributes = {
			[ATTR_TELEMETRY_SDK_NAME]:
				'@launchdarkly/observability-react-native',
			[ATTR_TELEMETRY_SDK_VERSION]: metadata.sdk?.version || 'unknown',
			[FEATURE_FLAG_ENV_ATTR]: metadata.mobileKey || 'unknown',
			[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
		}
	}

	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-react-native/tracing-hook',
		}
	}

	afterIdentify(
		hookContext: IdentifySeriesContext,
		data: IdentifySeriesData,
		result: IdentifySeriesResult,
	): IdentifySeriesData {
		// The evaluation context changed, so previously recorded exposures are
		// no longer relevant for deduplication.
		this.exposureDeduper.reset()

		if (result.status === 'completed') {
			_LDObserve.recordLog(`LD.identify`, 'info', {
				...this.metaAttributes,
				...getContextKeys(hookContext.context),
				key:
					this._options?.contextFriendlyName?.(hookContext.context) ??
					getCanonicalKey(hookContext.context),
				canonicalKey: getCanonicalKey(hookContext.context),
				timeout: hookContext.timeout,
				[LD_IDENTIFY_RESULT_STATUS]: result.status,
			})
		}
		return data
	}

	afterEvaluation(
		hookContext: EvaluationSeriesContext,
		data: EvaluationSeriesData,
		detail: LDEvaluationDetail,
	): EvaluationSeriesData {
		try {
			const canonicalKey = hookContext.context
				? getCanonicalKey(hookContext.context)
				: undefined

			// Deduplicate repeated exposures that resolve to the same result
			// within the configured window, so that frequent re-evaluations
			// (e.g. React re-renders) don't emit a span per evaluation.
			const dedupeKey = [
				hookContext.flagKey,
				JSON.stringify(detail.value),
				detail.variationIndex ?? '',
				detail.reason?.kind ?? '',
				detail.reason?.ruleId ?? '',
				canonicalKey ?? '',
			].join('|')
			if (!this.exposureDeduper.shouldRecord(dedupeKey)) {
				return data
			}

			const eventAttributes: Attributes = {
				[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
				[FEATURE_FLAG_VALUE_ATTR]: JSON.stringify(detail.value),
				...(detail.variationIndex !== undefined &&
				detail.variationIndex !== null
					? {
							[FEATURE_FLAG_VARIATION_INDEX_ATTR]:
								detail.variationIndex,
						}
					: {}),
			}

			if (detail.reason) {
				for (const attr in FEATURE_FLAG_REASON_ATTRS) {
					const reasonKey = attr as keyof LDEvaluationReason
					const value = detail.reason[reasonKey]
					if (value !== undefined && value !== null) {
						eventAttributes[FEATURE_FLAG_REASON_ATTRS[reasonKey]!] =
							value
					}
				}
			}

			if (hookContext.context) {
				eventAttributes[FEATURE_FLAG_CONTEXT_ATTR] = JSON.stringify(
					getContextKeys(hookContext.context),
				)
				eventAttributes[FEATURE_FLAG_CONTEXT_ID_ATTR] = canonicalKey
			}

			const allAttributes = { ...this.metaAttributes, ...eventAttributes }

			startInternalActiveSpan(
				this._options?.serviceName,
				FEATURE_FLAG_SPAN_NAME,
				(span) => {
					span.addEvent(FEATURE_FLAG_SCOPE, allAttributes)

					span.setAttributes({
						[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
						[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
						[FEATURE_FLAG_VALUE_ATTR]: JSON.stringify(detail.value),
						// Mark this as SDK-internal telemetry so it can be filtered
						// out universally (independent of instrumentation scope).
						[LD_INTERNAL_ATTR]: true,
					})

					span.setStatus({ code: 1 })
					span.end()
				},
			)

			_LDObserve.recordLog(
				`Feature flag "${hookContext.flagKey}" evaluated`,
				'debug',
				allAttributes,
			)

			// Only start the dedupe window once the exposure has been emitted,
			// so a throw above doesn't suppress later evaluations.
			this.exposureDeduper.markRecorded(dedupeKey)
		} catch (error) {
			_LDObserve.recordError(error as Error, {
				'flag.key': hookContext.flagKey,
				'error.context': 'feature_flag_evaluation_tracing',
			})
		}

		return data
	}

	afterTrack(hookContext: TrackSeriesContext): void {
		try {
			const trackAttributes: Attributes = {
				...this.metaAttributes,
				...(hookContext.context
					? getContextKeys(hookContext.context)
					: {}),
				// Flatten user-supplied track data the same way LDObserve.track
				// does, so nested objects/arrays survive as dotted attributes
				// instead of being dropped by OpenTelemetry.
				...(typeof hookContext.data === 'object' &&
				hookContext.data !== null
					? flattenTrackProperties(
							hookContext.data as TrackProperties,
						)
					: {}),
				// Reserved fields are written last so caller data can't clobber them.
				key: hookContext.key,
				...(hookContext.metricValue !== undefined &&
				hookContext.metricValue !== null
					? { value: hookContext.metricValue }
					: {}),
			}

			startInternalActiveSpan(
				this._options?.serviceName,
				LD_TRACK_SPAN_NAME,
				(span) => {
					span.setAttributes(trackAttributes)
					span.setStatus({ code: 1 })
					span.end()
				},
			)
		} catch (error) {
			_LDObserve.recordError(error as Error, {
				'track.key': hookContext.key,
				'error.context': 'track_tracing',
			})
		}
	}
}

export class Observability implements LDPlugin {
	constructor(private readonly _options?: ReactNativeOptions) {}

	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-react-native',
		}
	}

	register(
		_client: LDClientMin,
		metadata: LDPluginEnvironmentMetadata,
	): void {
		const sdkKey = metadata.sdkKey || metadata.mobileKey || ''
		_LDObserve._init(new ObservabilityClient(sdkKey, this._options || {}))
	}

	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [new TracingHook(metadata, this._options)]
	}
}
