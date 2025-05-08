import type { LDClientMin } from '../client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	TrackSeriesContext,
} from '../integrations/launchdarkly/types/Hooks'
import { Observe as ObserveAPI } from '../api/observe'
import { ObserveSDK } from '../sdk/observe'
import { LDObserve } from '../sdk/LDObserve'
import {
	FEATURE_FLAG_APP_VERSION_ATTR,
	FEATURE_FLAG_CLIENT_SIDE_ID_ATTR,
	FEATURE_FLAG_CONTEXT_ATTR,
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_ENV_ATTR,
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_SCOPE,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_VARIANT_ATTR,
	getCanonicalKey,
	getCanonicalObj,
} from '../integrations/launchdarkly'
import type { ObserveOptions } from '../client/types/observe'
import { Plugin } from './common'
import {
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'
import { Attributes } from '@opentelemetry/api'

export class Observe extends Plugin<ObserveOptions> implements LDPlugin {
	observe!: ObserveAPI

	constructor(projectID?: string | number, options?: ObserveOptions) {
		// Don't initialize if an projectID is not set.
		if (!projectID) {
			console.info(
				'Highlight is not initializing because projectID was passed undefined.',
			)
			return
		}
		super(options)
		this.observe = new ObserveSDK({
			backendUrl: options?.backendUrl ?? 'https://pub.highlight.io',
			otlpEndpoint:
				options?.otel?.otlpEndpoint ?? 'https://otel.highlight.io',
			projectId: projectID,
			sessionSecureId: this.sessionSecureID,
			environment: options?.environment ?? 'production',
			networkRecordingOptions:
				typeof options?.networkRecording === 'object'
					? options.networkRecording
					: undefined,
			tracingOrigins: options?.tracingOrigins,
			serviceName: options?.serviceName ?? 'highlight-browser',
			instrumentations: options?.otel?.instrumentations,
		})
		LDObserve.load(this.observe)
	}
	getMetadata() {
		return {
			name: '@launchdarkly/observability',
		}
	}
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		this.observe.register(client, environmentMetadata)
	}
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		const metaAttrs = {
			[ATTR_TELEMETRY_SDK_NAME]: metadata.sdk.name,
			[ATTR_TELEMETRY_SDK_VERSION]: metadata.sdk.version,
			[FEATURE_FLAG_CLIENT_SIDE_ID_ATTR]: metadata.clientSideId,
			[FEATURE_FLAG_ENV_ATTR]: metadata.application?.id,
			[FEATURE_FLAG_APP_VERSION_ATTR]: metadata.application?.version,
		}
		return [
			{
				getMetadata: () => {
					return {
						name: '@launchdarkly/observability/hooks',
					}
				},
				afterIdentify: (
					hookContext: IdentifySeriesContext,
					data: IdentifySeriesData,
					_result: IdentifySeriesResult,
				) => {
					for (const hook of this.observe.getHooks?.(metadata) ??
						[]) {
						hook.afterIdentify?.(hookContext, data, _result)
					}

					this.observe.recordLog('LD.identify', 'info', {
						...metaAttrs,
						key: getCanonicalKey(hookContext.context),
						context: JSON.stringify(
							getCanonicalObj(hookContext.context),
						),
						timeout: hookContext.timeout,
					})
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
					for (const hook of this.observe.getHooks?.(metadata) ??
						[]) {
						hook.afterEvaluation?.(hookContext, data, detail)
					}

					const eventAttributes: Attributes = {
						[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
						[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
						[FEATURE_FLAG_VARIANT_ATTR]: JSON.stringify(
							detail.value,
						),
					}

					if (hookContext.context) {
						eventAttributes[FEATURE_FLAG_CONTEXT_ATTR] =
							JSON.stringify(getCanonicalObj(hookContext.context))
						eventAttributes[FEATURE_FLAG_CONTEXT_KEY_ATTR] =
							getCanonicalKey(hookContext.context)
					}

					this.observe.startSpan(
						FEATURE_FLAG_SPAN_NAME,
						{
							...metaAttrs,
							attributes: eventAttributes,
						},
						(s) => {
							if (s) {
								s.addEvent(FEATURE_FLAG_SCOPE, eventAttributes)
							}
						},
					)

					return data
				},
				afterTrack: (hookContext: TrackSeriesContext) => {
					for (const hook of this.observe.getHooks?.(metadata) ??
						[]) {
						hook.afterTrack?.(hookContext)
					}
					this.observe.recordLog('LD.track', 'info', {
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
