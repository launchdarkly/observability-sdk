import { type HighlightClassOptions } from '../client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import { getCanonicalKey, Hook, LDClient } from '../integrations/launchdarkly'
import { RecordSDK } from '../sdk/record'
import firstloadVersion from '../__generated/version'
import { setupMixpanelIntegration } from '../integrations/mixpanel'
import { setupAmplitudeIntegration } from '../integrations/amplitude'
import { LDRecord } from '../sdk/LDRecord'
import type { RecordOptions } from '../client/types/record'
import { Plugin } from './common'
import { internalLog } from '../sdk/util'

export class Record extends Plugin<RecordOptions> implements LDPlugin {
	record: RecordSDK | undefined

	constructor(projectID?: string | number, options?: RecordOptions) {
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
			if (!projectID) {
				console.warn(
					'@launchdarkly/session-replay is not initializing because projectID was passed undefined.',
				)
				return
			}
			super(options)
			const client_options: HighlightClassOptions = {
				...options,
				organizationID: projectID,
				firstloadVersion,
				environment: options?.environment || 'production',
				appVersion: options?.version,
				sessionSecureID: this.sessionSecureID,
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
		return [
			{
				getMetadata: () => {
					return {
						name: '@launchdarkly/session-replay/hooks',
					}
				},
				afterIdentify: (hookContext, data, _result) => {
					for (const hook of this.record?.getHooks?.(metadata) ??
						[]) {
						hook.afterIdentify?.(hookContext, data, _result)
					}
					this.record?.identify(
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
