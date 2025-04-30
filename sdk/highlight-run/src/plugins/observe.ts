import { HighlightOptions, LDClientMin } from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
} from '../integrations/launchdarkly/types/Hooks'
import { H } from 'index'
import {
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_SCOPE,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_VARIANT_ATTR,
	getCanonicalKey,
} from '../integrations/launchdarkly'

export class Observe implements LDPlugin {
	sessionSecureID: string | undefined
	constructor(projectID?: string | number, opts?: HighlightOptions) {
		const session = H.init(projectID, opts)
		if (session) {
			this.sessionSecureID = session.sessionSecureID
		}
	}
	getMetadata() {
		return {
			name: '@launchdarkly/observability.record',
		}
	}
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		// TODO(vkorolik) report metadata as resource attrs?
		H.registerLD(client, environmentMetadata)
	}
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [
			{
				getMetadata: () => {
					return {
						name: 'HighlightHook',
					}
				},
				afterIdentify: (
					hookContext: IdentifySeriesContext,
					data: IdentifySeriesData,
					_result: IdentifySeriesResult,
				) => {
					H.log('LD.identify', 'INFO', {
						key: getCanonicalKey(hookContext.context),
						timeout: hookContext.timeout,
					})
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
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

					H.startSpan(FEATURE_FLAG_SPAN_NAME, (s) => {
						if (s) {
							s.addEvent(FEATURE_FLAG_SCOPE, eventAttributes)
						}
					})

					return data
				},
			},
		]
	}
}
