import { type HighlightPublicInterface, MetricCategory } from '../../client'
import type { ErrorMessage, Source } from '../../client/types/shared-types'
import type { IntegrationClient } from '../index'
import type { LDClientMin as LDClient } from './types/LDClient'
import type { Hook } from './types/Hooks'
import type { RecordMetric } from '../../client/types/types'
import { BufferedClass } from '../../sdk/buffer'
import { LDPluginEnvironmentMetadata } from '../../plugins/plugin'
import type { Attributes } from '@opentelemetry/api'
import type {
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	LDContext,
	LDContextCommon,
	LDMultiKindContext,
} from '@launchdarkly/js-client-sdk'

export type { Hook, LDClient }

export const FEATURE_FLAG_SCOPE = 'feature_flag'
export const LD_SCOPE = 'launchdarkly'
export const FEATURE_FLAG_SPAN_NAME = 'evaluation'

export const FEATURE_FLAG_ENV_ATTR = `${FEATURE_FLAG_SCOPE}.set.id`
export const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
export const FEATURE_FLAG_CONTEXT_ATTR = `${FEATURE_FLAG_SCOPE}.context`
export const FEATURE_FLAG_CONTEXT_ID_ATTR = `${FEATURE_FLAG_CONTEXT_ATTR}.id`
export const FEATURE_FLAG_VARIANT_ATTR = `${FEATURE_FLAG_SCOPE}.result.variant`
export const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
export const FEATURE_FLAG_IN_EXPERIMENT_ATTR = `${FEATURE_FLAG_SCOPE}.result.reason.in_experiment`
export const FEATURE_FLAG_VARIATION_INDEX_ATTR = `${FEATURE_FLAG_SCOPE}.result.variation_index`
export const FEATURE_FLAG_APP_ID_ATTR = `${LD_SCOPE}.application.id`
export const FEATURE_FLAG_APP_VERSION_ATTR = `${LD_SCOPE}.application.version`

export const LD_INITIALIZE_EVENT = '$ld:telemetry:session:init'
export const LD_ERROR_EVENT = '$ld:telemetry:error'
export const LD_TRACK_EVENT = '$ld:telemetry:track'
export const LD_METRIC_EVENT = '$ld:telemetry:metric'

export const LD_METRIC_NAME_DOCUMENT_LOAD = 'document_load'

function encodeKey(key: string): string {
	if (key.includes('%') || key.includes(':')) {
		return key.replace(/%/g, '%25').replace(/:/g, '%3A')
	}
	return key
}

function isMultiContext(context: any): context is LDMultiKindContext {
	return context.kind === 'multi'
}

export function getCanonicalKey(context: LDContext) {
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

export function getCanonicalObj(context: LDContext) {
	if (isMultiContext(context)) {
		return Object.keys(context)
			.sort()
			.filter((key) => key !== 'kind')
			.map((key) => {
				return {
					[key]: encodeKey((context[key] as LDContextCommon).key),
				}
			})
			.reduce((acc, obj) => {
				return { ...acc, ...obj }
			}, {} as Attributes)
	}

	return context.key
}

export function setupLaunchDarklyIntegration(
	hClient: HighlightPublicInterface,
	ldClient: LDClient,
) {
	ldClient.addHook({
		getMetadata: () => {
			return {
				name: 'highlight.run',
			}
		},
		afterIdentify: (hookContext, data, _result) => {
			hClient.log('LD.identify', 'INFO', {
				key: getCanonicalKey(hookContext.context),
				context: JSON.stringify(getCanonicalObj(hookContext.context)),
				timeout: hookContext.timeout,
			})
			hClient.identify(
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
			const eventAttributes: Attributes = {
				[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
				[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
				[FEATURE_FLAG_VARIANT_ATTR]: JSON.stringify(detail.value),
				[FEATURE_FLAG_IN_EXPERIMENT_ATTR]: detail.reason?.inExperiment,
				[FEATURE_FLAG_VARIATION_INDEX_ATTR]:
					detail.variationIndex ?? undefined,
			}

			if (hookContext.context) {
				eventAttributes[FEATURE_FLAG_CONTEXT_ATTR] = JSON.stringify(
					getCanonicalObj(hookContext.context),
				)
				eventAttributes[FEATURE_FLAG_CONTEXT_ID_ATTR] = getCanonicalKey(
					hookContext.context,
				)
			}

			hClient.startSpan(FEATURE_FLAG_SPAN_NAME, (s) => {
				if (s) {
					s.addEvent(FEATURE_FLAG_SCOPE, eventAttributes)
				}
			})

			return data
		},
	})
}

export class LaunchDarklyIntegrationSDK implements IntegrationClient {
	client: LDClient
	metadata?: LDPluginEnvironmentMetadata
	constructor(client: LDClient, metadata?: LDPluginEnvironmentMetadata) {
		this.client = client
		this.metadata = metadata
	}

	getHooks(_: LDPluginEnvironmentMetadata): Hook[] {
		return []
	}

	init(sessionSecureID: string) {
		this.client.track(LD_INITIALIZE_EVENT, {
			sessionID: sessionSecureID,
		})
	}

	recordGauge(sessionSecureID: string, metric: RecordMetric) {
		// only record web vitals
		if (
			metric.category !== MetricCategory.WebVital &&
			metric.name !== LD_METRIC_NAME_DOCUMENT_LOAD
		) {
			return
		}
		// ignore Jank metric, sent on interaction
		if (metric.name === 'Jank') {
			return
		}
		this.client.track(
			`${LD_METRIC_EVENT}:${metric.name.toLowerCase()}`,
			{
				...metric.tags
					?.map((t) => ({ [t.name]: t.value }))
					.reduce((a, b) => ({ ...a, ...b }), {}),
				category: metric.category,
				group: metric.group,
				sessionID: sessionSecureID,
			},
			metric.value,
		)
	}

	identify(
		_sessionSecureID: string,
		_user_identifier: string,
		_user_object = {},
		_source?: Source,
	) {
		// noop - no highlight forwarding of identify call
	}

	error(sessionSecureID: string, error: ErrorMessage) {
		this.client.track(LD_ERROR_EVENT, {
			...error,
			sessionID: sessionSecureID,
		})
	}

	track(sessionSecureID: string, metadata: object) {
		const event = (metadata as unknown as { event?: string }).event
		this.client.track(
			event ? `${LD_TRACK_EVENT}:${event}` : LD_TRACK_EVENT,
			{
				...metadata,
				sessionID: sessionSecureID,
			},
		)
	}
}

export class LaunchDarklyIntegration
	extends BufferedClass<IntegrationClient>
	implements IntegrationClient
{
	client: LaunchDarklyIntegrationSDK
	constructor(client: LDClient, metadata?: LDPluginEnvironmentMetadata) {
		super()
		this.client = new LaunchDarklyIntegrationSDK(client, metadata)
	}

	getHooks(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [
			{
				getMetadata: () => {
					return {
						name: 'highlight.run/ld',
					}
				},
				afterIdentify: (
					hookContext: IdentifySeriesContext,
					data: IdentifySeriesData,
					_result: IdentifySeriesResult,
				) => {
					this.load(this.client)
					return data
				},
			},
		]
	}

	init(sessionSecureID: string) {
		return this._bufferCall('init', [sessionSecureID])
	}

	recordGauge(sessionSecureID: string, metric: RecordMetric) {
		return this._bufferCall('recordGauge', [sessionSecureID, metric])
	}

	identify(
		sessionSecureID: string,
		user_identifier: string,
		user_object = {},
		source?: Source,
	) {
		return this._bufferCall('identify', [
			sessionSecureID,
			user_identifier,
			user_object,
			source,
		])
	}

	error(sessionSecureID: string, error: ErrorMessage) {
		return this._bufferCall('error', [sessionSecureID, error])
	}

	track(sessionSecureID: string, metadata: object) {
		return this._bufferCall('track', [sessionSecureID, metadata])
	}
}
