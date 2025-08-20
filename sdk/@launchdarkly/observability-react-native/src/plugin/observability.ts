import { LDClientMin, LDPlugin } from './plugin'
import { ReactNativeOptions } from '../api/Options'
import { ObservabilityClient } from '../client/ObservabilityClient'
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
	getCanonicalObj,
	LD_IDENTIFY_RESULT_STATUS,
} from '../constants/featureFlags'
import type { LDEvaluationReason } from '@launchdarkly/js-sdk-common'
import {
	IdentifySeriesData,
	IdentifySeriesResult,
	Hook,
	IdentifySeriesContext,
	EvaluationSeriesContext,
	EvaluationSeriesData,
} from '@launchdarkly/react-native-client-sdk'

class TracingHook implements Hook {
	private metaAttributes: Attributes = {}

	constructor(
		private metadata: LDPluginEnvironmentMetadata,
		private readonly _options?: ReactNativeOptions,
	) {
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
		if (result.status === 'completed') {
			_LDObserve.recordLog(`LD.identify`, 'info', {
				...this.metaAttributes,
				...getCanonicalObj(hookContext.context),
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
					getCanonicalObj(hookContext.context),
				)
				eventAttributes[FEATURE_FLAG_CONTEXT_ID_ATTR] = getCanonicalKey(
					hookContext.context,
				)
			}

			const allAttributes = { ...this.metaAttributes, ...eventAttributes }

			_LDObserve.startActiveSpan(FEATURE_FLAG_SPAN_NAME, (span) => {
				span.addEvent(FEATURE_FLAG_SCOPE, allAttributes)

				span.setAttributes({
					[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
					[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
					[FEATURE_FLAG_VALUE_ATTR]: JSON.stringify(detail.value),
				})

				span.setStatus({ code: 1 })
				span.end()
			})

			_LDObserve.recordLog(
				`Feature flag "${hookContext.flagKey}" evaluated`,
				'debug',
				allAttributes,
			)
		} catch (error) {
			_LDObserve.recordError(error as Error, {
				'flag.key': hookContext.flagKey,
				'error.context': 'feature_flag_evaluation_tracing',
			})
		}

		return data
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
