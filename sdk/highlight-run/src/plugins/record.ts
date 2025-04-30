import {
	FirstLoadListeners,
	GenerateSecureID,
	getPreviousSessionData,
	HighlightOptions,
	LDClientMin,
} from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
} from '../integrations/launchdarkly/types/Hooks'
import type { LDContext } from '../integrations/launchdarkly/types/LDContext'
import type { LDContextCommon } from '../integrations/launchdarkly/types/LDContextCommon'
import type { LDMultiKindContext } from '../integrations/launchdarkly/types/LDMultiKindContext'
import { H } from 'index'
import { RecordSDK } from '../sdk/record'
import { initializeFetchListener } from '../listeners/fetch'
import { initializeWebSocketListener } from '../listeners/web-socket'

const FEATURE_FLAG_SCOPE = 'feature_flag'
// TODO(vkorolik) reporting environment as `${FEATURE_FLAG_SCOPE}.set.id`
const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
const FEATURE_FLAG_CONTEXT_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.context.key`
const FEATURE_FLAG_VARIANT_ATTR = `${FEATURE_FLAG_SCOPE}.result.variant`
const FEATURE_FLAG_SPAN_NAME = 'evaluation'

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

// TODO(vkorolik) move file to LD npm package
export class Record implements LDPlugin {
	firstloadListeners: FirstLoadListeners
	constructor(projectID?: string | number, opts?: HighlightOptions) {
		if (!projectID) {
			throw new Error(
				'Project ID is required for Record plugin initialization',
			)
		}
		let previousSession = getPreviousSessionData()
		let sessionSecureID = GenerateSecureID()
		if (previousSession?.sessionSecureID) {
			sessionSecureID = previousSession.sessionSecureID
		}
		const clientOptions = {
			...opts,
			organizationID: projectID,
			sessionSecureID,
		}
		// TODO(vkorolik) here or from sdk?
		this.firstloadListeners = new FirstLoadListeners(clientOptions)
		this.firstloadListeners.startListening()
		const highlight = new RecordSDK(clientOptions, this.firstloadListeners)
		// TODO(vkorolik) firstload listeners
		initializeFetchListener()
		initializeWebSocketListener()
		if (!opts?.manualStart) {
			highlight.init()
		}
	}
	getMetadata() {
		return {
			name: 'observability.record',
		}
	}
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		throw new Error('Method not implemented.')
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
						[index: string]: number | boolean | string
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
