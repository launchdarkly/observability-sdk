import { HighlightOptions, LDClientMin } from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	TrackSeriesContext,
} from '../integrations/launchdarkly/types/Hooks'
import { H } from 'index'
import {
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	getCanonicalKey,
	TRACK_DATA_ATTR,
	TRACK_KEY_ATTR,
	TRACK_METRIC_VALUE_ATTR,
} from '../integrations/launchdarkly'

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
						name: 'H.record.hook',
					}
				},
				afterIdentify: (
					hookContext: IdentifySeriesContext,
					data: IdentifySeriesData,
					_result: IdentifySeriesResult,
				) => {
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
