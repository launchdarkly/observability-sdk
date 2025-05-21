import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import {
	FEATURE_FLAG_APP_ID_ATTR,
	FEATURE_FLAG_APP_VERSION_ATTR,
	FEATURE_FLAG_CONTEXT_ATTR,
	FEATURE_FLAG_CONTEXT_ID_ATTR,
	FEATURE_FLAG_ENV_ATTR,
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_REASON_ATTRS,
	FEATURE_FLAG_SCOPE,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_VALUE_ATTR,
	FEATURE_FLAG_VARIATION_INDEX_ATTR,
	getCanonicalKey,
	getCanonicalObj,
	Hook,
	LD_IDENTIFY_RESULT_STATUS,
	LDClient,
} from '../integrations/launchdarkly'
import { Observe as ObserveAPI } from '../api/observe'
import { ObserveSDK } from '../sdk/observe'
import type { BrowserTracingConfig } from '../client/otel'
import { LDObserve } from '../sdk/LDObserve'
import type { ObserveOptions } from '../client/types/observe'
import { Plugin } from './common'
import {
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'
import { Attributes } from '@opentelemetry/api'
import { internalLog } from '../sdk/util'
import { LDEvaluationReason } from '@launchdarkly/js-sdk-common/dist/cjs/api/data/LDEvaluationReason'

export class Observe extends Plugin<ObserveOptions> implements LDPlugin {
	observe: ObserveAPI | undefined
	options: ObserveOptions | undefined

	constructor(options?: ObserveOptions) {
		super(options)
		this.options = options
	}

	private initialize(
		ldCredential: string | undefined,
		options?: ObserveOptions,
	) {
		try {
			// Don't run init when called outside of the browser.
			if (
				typeof window === 'undefined' ||
				typeof document === 'undefined'
			) {
				console.warn(
					'@launchdarkly/observability is not initializing because it is not supported in this environment.',
				)
				return
			}
			// Don't initialize if an projectID is not set.
			if (!ldCredential) {
				console.warn(
					'@launchdarkly/observability is not initializing because the SDK credential is undefined.',
				)
				return
			}
			const clientOptions: BrowserTracingConfig = {
				backendUrl:
					options?.backendUrl ??
					'https://pub.observability.app.launchdarkly.com',
				otlpEndpoint:
					options?.otel?.otlpEndpoint ??
					'https://otel.observability.app.launchdarkly.com',
				projectId: ldCredential,
				sessionSecureId: this.sessionSecureID,
				environment: options?.environment ?? 'production',
				networkRecordingOptions:
					typeof options?.networkRecording === 'object'
						? options.networkRecording
						: undefined,
				tracingOrigins: options?.tracingOrigins,
				serviceName: options?.serviceName ?? 'browser',
				instrumentations: options?.otel?.instrumentations,
				eventNames: options?.otel?.eventNames,
			}
			this.observe = new ObserveSDK(clientOptions)
			LDObserve.load(this.observe)
		} catch (error) {
			internalLog(
				`Error initializing @launchdarkly/observability SDK`,
				'error',
				error,
			)
		}
	}
	getMetadata() {
		return {
			name: '@launchdarkly/observability',
		}
	}
	register(
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		this.observe?.register(client, environmentMetadata)
	}
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		const metaAttrs = {
			[ATTR_TELEMETRY_SDK_NAME]: metadata.sdk.name,
			[ATTR_TELEMETRY_SDK_VERSION]: metadata.sdk.version,
			[FEATURE_FLAG_ENV_ATTR]: metadata.clientSideId,
			[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
			...(metadata.application?.id
				? { [FEATURE_FLAG_APP_ID_ATTR]: metadata.application.id }
				: {}),
			...(metadata.application?.version
				? {
						[FEATURE_FLAG_APP_VERSION_ATTR]:
							metadata.application.version,
					}
				: {}),
		}
		this.initialize(
			metadata.sdkKey ?? metadata.mobileKey ?? metadata.clientSideId,
			this.options,
		)
		return [
			{
				getMetadata: () => {
					return {
						name: '@launchdarkly/observability/hooks',
					}
				},
				afterIdentify: (hookContext, data, result) => {
					for (const hook of this.observe?.getHooks?.(metadata) ??
						[]) {
						hook.afterIdentify?.(hookContext, data, result)
					}

					this.observe?.recordLog('LD.identify', 'info', {
						...metaAttrs,
						key: getCanonicalKey(hookContext.context),
						context: JSON.stringify(
							getCanonicalObj(hookContext.context),
						),
						timeout: hookContext.timeout,
						[LD_IDENTIFY_RESULT_STATUS]: result.status,
					})
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
					for (const hook of this.observe?.getHooks?.(metadata) ??
						[]) {
						hook.afterEvaluation?.(hookContext, data, detail)
					}

					const eventAttributes: Attributes = {
						[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
						[FEATURE_FLAG_VALUE_ATTR]: JSON.stringify(detail.value),
						// only set the following keys when values are truthy
						...(detail.variationIndex
							? {
									[FEATURE_FLAG_VARIATION_INDEX_ATTR]:
										detail.variationIndex,
								}
							: {}),
					}
					if (detail.reason) {
						for (const attr in FEATURE_FLAG_REASON_ATTRS) {
							const k = attr as keyof LDEvaluationReason
							const value = detail.reason[k]
							if (value) {
								eventAttributes[FEATURE_FLAG_REASON_ATTRS[k]!] =
									value
							}
						}
					}

					if (hookContext.context) {
						eventAttributes[FEATURE_FLAG_CONTEXT_ATTR] =
							JSON.stringify(getCanonicalObj(hookContext.context))
						eventAttributes[FEATURE_FLAG_CONTEXT_ID_ATTR] =
							getCanonicalKey(hookContext.context)
					}
					const attributes = { ...metaAttrs, ...eventAttributes }
					this.observe?.startSpan(FEATURE_FLAG_SPAN_NAME, (s) => {
						if (s) {
							s.addEvent(FEATURE_FLAG_SCOPE, attributes)
						}
					})

					return data
				},
				afterTrack: (hookContext) => {
					for (const hook of this.observe?.getHooks?.(metadata) ??
						[]) {
						hook.afterTrack?.(hookContext)
					}
					this.observe?.recordLog('LD.track', 'info', {
						...metaAttrs,
						key: hookContext.key,
						value: hookContext.metricValue,
						...(typeof hookContext.data === 'object'
							? hookContext.data
							: {}),
					})
				},
			},
		]
	}
}
