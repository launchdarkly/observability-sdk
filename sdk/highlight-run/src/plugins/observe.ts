import type { HighlightOptions, LDClientMin } from '../client'
import { GenerateSecureID } from '../client/utils/secure-id'
import {
	getPreviousSessionData,
	loadCookieSessionData,
} from '../client/utils/sessionStorage/highlightSession'
import { setCookieWriteEnabled } from '../client/utils/storage'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
} from '../integrations/launchdarkly/types/Hooks'
import { Observe as ObserveAPI } from '../api/observe'
import { ObserveSDK } from '../sdk/observe'
import {
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	FEATURE_FLAG_SCOPE,
	FEATURE_FLAG_SPAN_NAME,
	FEATURE_FLAG_VARIANT_ATTR,
	getCanonicalKey,
} from '../integrations/launchdarkly'

// TODO(vkorolik) move to @launchdarkly package
export class Observe implements LDPlugin {
	observe!: ObserveAPI
	private readonly initCalled: boolean = false

	constructor(projectID?: string | number, options?: HighlightOptions) {
		// Don't run init when called outside of the browser.
		if (typeof window === 'undefined' || typeof document === 'undefined') {
			return
		}

		// Don't initialize if an projectID is not set.
		if (!projectID) {
			console.info(
				'Highlight is not initializing because projectID was passed undefined.',
			)
			return
		}

		if (options?.sessionCookie) {
			loadCookieSessionData()
		} else {
			setCookieWriteEnabled(false)
		}

		// TODO(vkorolik) race condition with record.ts SDK getting/setting session ID
		let previousSession = getPreviousSessionData()
		let sessionSecureID = GenerateSecureID()
		if (previousSession?.sessionSecureID) {
			sessionSecureID = previousSession.sessionSecureID
		}

		// `init` was already called, do not reinitialize
		if (this.initCalled) {
			return
		}
		this.initCalled = true

		this.observe = new ObserveSDK({
			backendUrl: options?.backendUrl ?? 'https://pub.highlight.io',
			otlpEndpoint: options?.otlpEndpoint ?? 'https://otel.highlight.io',
			projectId: projectID,
			sessionSecureId: sessionSecureID,
			environment: options?.environment ?? 'production',
			networkRecordingOptions:
				typeof options?.networkRecording === 'object'
					? options.networkRecording
					: undefined,
			tracingOrigins: options?.tracingOrigins,
			serviceName: options?.serviceName ?? 'highlight-browser',
			instrumentations: options?.otel?.instrumentations,
		})
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
		this.observe.register(client, environmentMetadata)
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
					this.observe.recordLog('LD.identify', 'INFO', {
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

					this.observe.startSpan(FEATURE_FLAG_SPAN_NAME, (s) => {
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
