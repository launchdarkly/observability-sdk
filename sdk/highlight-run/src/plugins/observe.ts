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
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_SCOPE,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_VARIANT_ATTR,
	getCanonicalKey,
} from '../integrations/launchdarkly'
import type { ObserveOptions } from '../client/types/observe'
import { Plugin } from './common'

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
			otlpEndpoint: options?.otlpEndpoint ?? 'https://otel.highlight.io',
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
						key: getCanonicalKey(hookContext.context),
						timeout: hookContext.timeout,
					})
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
					for (const hook of this.observe.getHooks?.(metadata) ??
						[]) {
						hook.afterEvaluation?.(hookContext, data, detail)
					}

					const eventAttributes: {
						[index: string]: number | boolean | string | undefined
					} = {
						[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
						[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
						[FEATURE_FLAG_VARIANT_ATTR]: JSON.stringify(
							detail.value,
						),
					}

					if (hookContext.context) {
						eventAttributes[FEATURE_FLAG_CONTEXT_KEY_ATTR] =
							getCanonicalKey(hookContext.context)
					}

					this.observe.startSpan(
						FEATURE_FLAG_SPAN_NAME,
						{ attributes: eventAttributes },
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
				},
			},
		]
	}
}
