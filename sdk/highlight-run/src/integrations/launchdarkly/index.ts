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
import { LDEvaluationReason } from '@launchdarkly/js-sdk-common/dist/cjs/api/data/LDEvaluationReason'

export type { Hook, LDClient }

export const FEATURE_FLAG_SCOPE = 'feature_flag'
export const LD_SCOPE = 'launchdarkly'
export const FEATURE_FLAG_SPAN_NAME = 'evaluation'
export const FEATURE_FLAG_EVENT_NAME = `${FEATURE_FLAG_SCOPE}.${FEATURE_FLAG_SPAN_NAME}`

export const FEATURE_FLAG_ENV_ATTR = `${FEATURE_FLAG_SCOPE}.set.id`
export const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
export const FEATURE_FLAG_CONTEXT_ATTR = `${FEATURE_FLAG_SCOPE}.contextKeys`
export const FEATURE_FLAG_CONTEXT_ID_ATTR = `${FEATURE_FLAG_SCOPE}.context.id`
export const FEATURE_FLAG_VALUE_ATTR = `${FEATURE_FLAG_SCOPE}.result.value`
export const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
export const FEATURE_FLAG_REASON_ATTRS: {
	[key in keyof LDEvaluationReason]: string
} = {
	kind: `${FEATURE_FLAG_SCOPE}.result.reason.kind`,
	errorKind: `${FEATURE_FLAG_SCOPE}.result.reason.errorKind`,
	ruleIndex: `${FEATURE_FLAG_SCOPE}.result.reason.ruleIndex`,
	ruleId: `${FEATURE_FLAG_SCOPE}.result.reason.ruleId`,
	prerequisiteKey: `${FEATURE_FLAG_SCOPE}.result.reason.prerequisiteKey`,
	inExperiment: `${FEATURE_FLAG_SCOPE}.result.reason.inExperiment`,
	bigSegmentsStatus: `${FEATURE_FLAG_SCOPE}.result.reason.bigSegmentsStatus`,
}
export const FEATURE_FLAG_VARIATION_INDEX_ATTR = `${FEATURE_FLAG_SCOPE}.result.variationIndex`
export const FEATURE_FLAG_APP_ID_ATTR = `${LD_SCOPE}.application.id`
export const FEATURE_FLAG_APP_VERSION_ATTR = `${LD_SCOPE}.application.version`

export const LD_INITIALIZE_EVENT = '$ld:telemetry:session:init'
export const LD_ERROR_EVENT = '$ld:telemetry:error'
export const LD_TRACK_EVENT = '$ld:telemetry:track'
export const LD_METRIC_EVENT = '$ld:telemetry:metric'

export const LD_METRIC_NAME_DOCUMENT_LOAD = 'document_load'

export const LD_IDENTIFY_RESULT_STATUS = 'result.status'

function encodeKey(key: string): string {
	if (key.includes('%') || key.includes(':')) {
		return key.replace(/%/g, '%25').replace(/:/g, '%3A')
	}
	return key
}

function isMultiContext(context: any): context is LDMultiKindContext {
	return context.kind === 'multi'
}

/**
 * Get a canonical key for a given context. The canonical key contains an encoded version of the context
 * keys.
 *
 * This format should be stable and consistent. It isn't for presentation only purposes.
 * It allows linking to a context instance.
 * @param context The context to get a canonical key for.
 * @returns The canonical context key.
 */
export function getCanonicalKey(context: LDContext) {
	if (isMultiContext(context)) {
		return Object.keys(context)
			.sort()
			.filter((key) => key !== 'kind')
			.map((key) => {
				return `${key}:${encodeKey((context[key] as LDContextCommon).key)}`
			})
			.join(':')
	} else if ('kind' in context && context.kind === 'user') {
		// If the kind is a user, then the key is directly the user key.
		return context.key
	} else if (!('kind' in context)) {
		// Legacy user.
		return context.key
	}

	return `${context.kind}:${encodeKey(context.key)}`
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

	return {}
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
		afterIdentify: (hookContext, data, result) => {
			const metadata = {
				...getCanonicalObj(hookContext.context),
				key: getCanonicalKey(hookContext.context),
			}
			hClient.log('LD.identify', 'INFO', metadata)
			if (result.status === 'completed') {
				hClient.identify(metadata.key, metadata, 'LaunchDarkly')
			}
			return data
		},
		afterEvaluation: (hookContext, data, detail) => {
			const eventAttributes: Attributes = {
				[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
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
						eventAttributes[FEATURE_FLAG_REASON_ATTRS[k]!] = value
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

			hClient.startSpan(FEATURE_FLAG_SPAN_NAME, (s) => {
				if (s) {
					s.addEvent(FEATURE_FLAG_EVENT_NAME, eventAttributes)
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
