import {
	FirstLoadListeners,
	GenerateSecureID,
	getPreviousSessionData,
	type HighlightClassOptions,
	HighlightOptions,
	LDClientMin,
} from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	TrackSeriesContext,
} from '../integrations/launchdarkly/types/Hooks'
import {
	FEATURE_FLAG_CONTEXT_KEY_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	getCanonicalKey,
	TRACK_DATA_ATTR,
	TRACK_KEY_ATTR,
	TRACK_METRIC_VALUE_ATTR,
} from '../integrations/launchdarkly'
import { Record as RecordAPI } from '../api/record'
import { RecordSDK } from '../sdk/record'
import { loadCookieSessionData } from '../client/utils/sessionStorage/highlightSession'
import { setCookieWriteEnabled } from '../client/utils/storage'
import { initializeFetchListener } from '../listeners/fetch'
import { initializeWebSocketListener } from '../listeners/web-socket'
import firstloadVersion from '../__generated/version'
import { setupMixpanelIntegration } from '../integrations/mixpanel'
import { setupAmplitudeIntegration } from '../integrations/amplitude'

// TODO(vkorolik) move to @launchdarkly package
export class Record implements LDPlugin {
	sessionSecureID!: string
	record!: RecordAPI
	firstloadListeners!: FirstLoadListeners

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

		let previousSession = getPreviousSessionData()
		this.sessionSecureID = GenerateSecureID()
		if (previousSession?.sessionSecureID) {
			this.sessionSecureID = previousSession.sessionSecureID
		}

		// `init` was already called, do not reinitialize
		if (this.initCalled) {
			return
		}
		this.initCalled = true

		const client_options: HighlightClassOptions = {
			...options,
			organizationID: projectID,
			firstloadVersion,
			environment: options?.environment || 'production',
			appVersion: options?.version,
			sessionSecureID: this.sessionSecureID,
		}

		initializeFetchListener()
		initializeWebSocketListener()
		this.firstloadListeners = new FirstLoadListeners(client_options)
		if (!options?.manualStart) {
			this.firstloadListeners.startListening()
		}

		this.record = new RecordSDK(client_options, this.firstloadListeners)
		if (!options?.manualStart) {
			void this.record.start()
		}

		if (
			!options?.integrations?.mixpanel?.disabled &&
			options?.integrations?.mixpanel?.projectToken
		) {
			setupMixpanelIntegration(options.integrations.mixpanel)
		}

		if (
			!options?.integrations?.amplitude?.disabled &&
			options?.integrations?.amplitude?.apiKey
		) {
			setupAmplitudeIntegration(options.integrations.amplitude)
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
		this.record.register(client, environmentMetadata)
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
					this.record.identify(
						getCanonicalKey(hookContext.context),
						{
							key: getCanonicalKey(hookContext.context),
							timeout: hookContext.timeout,
						},
						'LaunchDarkly',
					)
					return data
				},
				afterTrack: (event: TrackSeriesContext) => {
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

					this.record.track(event.key, attrs)
				},
			},
		]
	}
}
