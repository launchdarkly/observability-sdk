import { type HighlightClassOptions } from '../client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import {
	FEATURE_FLAG_APP_ID_ATTR,
	FEATURE_FLAG_APP_VERSION_ATTR,
	FEATURE_FLAG_ENV_ATTR,
	FEATURE_FLAG_PROVIDER_ATTR,
	getCanonicalKey,
	getCanonicalObj,
	Hook,
	LDClient,
} from '../integrations/launchdarkly'
import { RecordSDK } from '../sdk/record'
import firstloadVersion from '../version'
import { setupMixpanelIntegration } from '../integrations/mixpanel'
import { setupAmplitudeIntegration } from '../integrations/amplitude'
import { LDRecord } from '../sdk/LDRecord'
import type { RecordOptions } from '../client/types/record'
import { Plugin } from './common'
import { internalLog } from '../sdk/util'
import {
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'

export class Record extends Plugin<RecordOptions> implements LDPlugin {
	record: RecordSDK | undefined
	options: RecordOptions | undefined

	constructor(options?: RecordOptions) {
		super(options)
		this.options = options
	}

	private initialize(
		ldCredential: string | undefined,
		options?: RecordOptions,
	) {
		try {
			// Don't run init when called outside of the browser.
			if (
				typeof window === 'undefined' ||
				typeof document === 'undefined'
			) {
				console.warn(
					'@launchdarkly/session-replay is not initializing because it is not supported in this environment.',
				)
				return
			}
			if (typeof Worker === 'undefined') {
				console.warn(
					'@launchdarkly/session-replay is not initializing because Worker is not supported.',
				)
				return
			}
			// Don't initialize if an projectID is not set.
			if (!ldCredential) {
				console.warn(
					'@launchdarkly/session-replay is not initializing because projectID was passed undefined.',
				)
				return
			}
			const client_options: HighlightClassOptions = {
				...options,
				organizationID: ldCredential,
				firstloadVersion,
				environment: options?.environment || 'production',
				appVersion: options?.version,
				sessionSecureID: this.sessionSecureID,
				privacySetting: options?.privacySetting ?? 'strict',
			}

			this.record = new RecordSDK(client_options)
			if (!options?.manualStart) {
				void this.record.start()
			}
			LDRecord.load(this.record)

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
		} catch (error) {
			internalLog(
				`Error initializing @launchdarkly/session-replay SDK`,
				'error',
				error,
			)
		}
	}

	getMetadata() {
		return {
			name: '@launchdarkly/session-replay',
		}
	}

	register(
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		this.record?.register(client, environmentMetadata)
	}

	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		const metaAttrs = {
			[ATTR_TELEMETRY_SDK_NAME]: metadata.sdk.name,
			[ATTR_TELEMETRY_SDK_VERSION]: metadata.sdk.version,
			[FEATURE_FLAG_ENV_ATTR]: metadata.clientSideId,
			[FEATURE_FLAG_PROVIDER_ATTR]: 'LaunchDarkly',
			...(metadata.application?.id
				? { [FEATURE_FLAG_APP_ID_ATTR]: metadata.application.id }
				: {}),
			...(metadata.application?.version
				? {
						[FEATURE_FLAG_APP_VERSION_ATTR]:
							metadata.application.version,
					}
				: {}),
		}
		this.initialize(
			metadata.sdkKey ?? metadata.mobileKey ?? metadata.clientSideId,
			this.options,
		)
		return [
			{
				getMetadata: () => {
					return {
						name: '@launchdarkly/session-replay/hooks',
					}
				},
				afterIdentify: (hookContext, data, result) => {
					for (const hook of this.record?.getHooks?.(metadata) ??
						[]) {
						hook.afterIdentify?.(hookContext, data, result)
					}
					if (result.status === 'completed') {
						const metadata = {
							...getCanonicalObj(hookContext.context),
							key: getCanonicalKey(hookContext.context),
						}
						this.record?.identify(
							metadata.key,
							{
								...metaAttrs,
								...metadata,
							},
							'LaunchDarkly',
						)
					}
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
					for (const hook of this.record?.getHooks?.(metadata) ??
						[]) {
						hook.afterEvaluation?.(hookContext, data, detail)
					}
					return data
				},
				afterTrack: (hookContext) => {
					for (const hook of this.record?.getHooks?.(metadata) ??
						[]) {
						hook.afterTrack?.(hookContext)
					}
					this.record?.track(hookContext.key, {
						data: hookContext.data,
						value: hookContext.metricValue,
					})
				},
			},
		]
	}
}
