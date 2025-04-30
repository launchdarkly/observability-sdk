import { HighlightOptions, LDClientMin } from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	TrackSeriesContext,
} from '../integrations/launchdarkly/types/Hooks'
import type { LDContext } from '../integrations/launchdarkly/types/LDContext'
import type { LDContextCommon } from '../integrations/launchdarkly/types/LDContextCommon'
import type { LDMultiKindContext } from '../integrations/launchdarkly/types/LDMultiKindContext'
import { H } from 'index'

const FEATURE_FLAG_SCOPE = 'feature_flag'
// TODO(vkorolik) reporting environment as `${FEATURE_FLAG_SCOPE}.set.id`
const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
const FEATURE_FLAG_CONTEXT_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.context.key`
const FEATURE_FLAG_VARIANT_ATTR = `${FEATURE_FLAG_SCOPE}.result.variant`
const FEATURE_FLAG_SPAN_NAME = 'evaluation'
const TRACK_SCOPE = 'ld.track'
const TRACK_KEY_ATTR = `${TRACK_SCOPE}.key`
const TRACK_DATA_ATTR = `${TRACK_SCOPE}.data`
const TRACK_METRIC_VALUE_ATTR = `${TRACK_SCOPE}.metric.value`

function encodeKey(key: string): string {
	if (key.includes('%') || key.includes(':')) {
		return key.replace(/%/g, '%25').replace(/:/g, '%3A')
	}
	return key
}

function isMultiContext(context: any): context is LDMultiKindContext {
	return context.kind === 'multi'
}

function getCanonicalKey(context: LDContext) {
	if (isMultiContext(context)) {
		return Object.keys(context)
			.sort()
			.filter((key) => key !== 'kind')
			.map((key) => {
				return `${key}:${encodeKey((context[key] as LDContextCommon).key)}`
			})
			.join(':')
	}

	return context.key
}

export class Record implements LDPlugin {
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
					H.identify(
						getCanonicalKey(hookContext.context),
						{
							key: getCanonicalKey(hookContext.context),
							timeout: hookContext.timeout,
						},
						'LaunchDarkly',
					)
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
				afterTrack(event: TrackSeriesContext) {
					const attrs: {
						[index: string]: number | boolean | string | undefined
					} = {
						[TRACK_KEY_ATTR]: event.key,
						[TRACK_DATA_ATTR]: JSON.stringify(event.data),
						[TRACK_METRIC_VALUE_ATTR]: event.metricValue,
						[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
					}
					if (event.context) {
						attrs[FEATURE_FLAG_CONTEXT_KEY_ATTR] = getCanonicalKey(
							event.context,
						)
					}

					H.track(event.key, attrs)
				},
			},
		]
	}
}
