import type { LDMultiKindContext } from './types/LDMultiKindContext'
import type { LDContext } from './types/LDContext'
import type { LDContextCommon } from './types/LDContextCommon'
import {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
} from './types/Hooks'
import { type HighlightPublicInterface, MetricCategory } from '../../client'
import type { ErrorMessage, Source } from '../../client/types/shared-types'
import type { IntegrationClient } from '../index'
import type { LDClientMin } from './types/LDClient'
import type { RecordMetric } from '../../client/types/types'
import { BufferedClass } from '../../sdk/buffer'
import { LDPluginEnvironmentMetadata } from '../../plugins/plugin'

export const FEATURE_FLAG_SCOPE = 'feature_flag'
export const FEATURE_FLAG_ENV_ATTR = `${FEATURE_FLAG_SCOPE}.set.id`
export const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
export const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
export const FEATURE_FLAG_CONTEXT_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.context.key`
export const FEATURE_FLAG_VARIANT_ATTR = `${FEATURE_FLAG_SCOPE}.result.variant`
export const FEATURE_FLAG_CLIENT_SIDE_ID_ATTR = `${FEATURE_FLAG_SCOPE}.client_side_id`
export const FEATURE_FLAG_APP_VERSION_ATTR = `${FEATURE_FLAG_SCOPE}.app_version`
export const FEATURE_FLAG_SPAN_NAME = 'evaluation'
export const TRACK_SPAN_NAME = 'track'

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

export function setupLaunchDarklyIntegration(
	hClient: HighlightPublicInterface,
	ldClient: LDClientMin,
) {
	ldClient.addHook({
		getMetadata: () => {
			return {
				name: 'highlight.run',
			}
		},
		afterIdentify: (
			hookContext: IdentifySeriesContext,
			data: IdentifySeriesData,
			_result: IdentifySeriesResult,
		) => {
			hClient.log('LD.identify', 'INFO', {
				key: getCanonicalKey(hookContext.context),
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
			const eventAttributes: {
				[index: string]: number | boolean | string
			} = {
				[FEATURE_FLAG_KEY_ATTR]: hookContext.flagKey,
				[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
				[FEATURE_FLAG_VARIANT_ATTR]: JSON.stringify(detail.value),
			}

			if (hookContext.context) {
				eventAttributes[FEATURE_FLAG_CONTEXT_KEY_ATTR] =
					getCanonicalKey(hookContext.context)
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
	client: LDClientMin
	metadata?: LDPluginEnvironmentMetadata
	constructor(client: LDClientMin, metadata?: LDPluginEnvironmentMetadata) {
		this.client = client
		this.metadata = metadata
	}

	getHooks(_: LDPluginEnvironmentMetadata): Hook[] {
		return []
	}

	init(sessionSecureID: string) {
		this.client.track(LD_INITIALIZE_EVENT, {
			sessionSecureID,
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
				...metric,
				sessionSecureID,
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
			sessionSecureID,
		})
	}

	track(sessionSecureID: string, metadata: object) {
		const event = (metadata as unknown as { event?: string }).event
		this.client.track(
			event ? `${LD_TRACK_EVENT}:${event}` : LD_TRACK_EVENT,
			{
				...metadata,
				sessionSecureID,
			},
		)
	}
}

export class LaunchDarklyIntegration
	extends BufferedClass<IntegrationClient>
	implements IntegrationClient
{
	client: LaunchDarklyIntegrationSDK
	constructor(client: LDClientMin, metadata?: LDPluginEnvironmentMetadata) {
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
					console.log('vadim', 'LD.identify', 'afterIdentify')
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
