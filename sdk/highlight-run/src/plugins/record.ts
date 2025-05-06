import { type HighlightClassOptions, LDClientMin } from '../client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type {
	Hook,
	IdentifySeriesContext,
	IdentifySeriesData,
	IdentifySeriesResult,
	TrackSeriesContext,
} from '../integrations/launchdarkly/types/Hooks'
import { Record as RecordAPI } from '../api/record'
import { RecordSDK } from '../sdk/record'
import firstloadVersion from '../__generated/version'
import { setupMixpanelIntegration } from '../integrations/mixpanel'
import { setupAmplitudeIntegration } from '../integrations/amplitude'
import { LDRecord } from '../sdk/LDRecord'
import type { RecordOptions } from '../client/types/record'
import { Plugin } from './common'

export class Record extends Plugin<RecordOptions> implements LDPlugin {
	record!: RecordAPI

	constructor(projectID?: string | number, options?: RecordOptions) {
		// Don't initialize if an projectID is not set.
		if (!projectID) {
			console.info(
				'Highlight is not initializing because projectID was passed undefined.',
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
	}

	getMetadata() {
		return {
			name: '@launchdarkly/session-replay',
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
						name: '@launchdarkly/observability/hooks',
					}
				},
				afterIdentify: (
					hookContext: IdentifySeriesContext,
					data: IdentifySeriesData,
					_result: IdentifySeriesResult,
				) => {
					for (const hook of this.record.getHooks?.(metadata) ?? []) {
						hook.afterIdentify?.(hookContext, data, _result)
					}
					return data
				},
				afterEvaluation: (hookContext, data, detail) => {
					for (const hook of this.record.getHooks?.(metadata) ?? []) {
						hook.afterEvaluation?.(hookContext, data, detail)
					}
					return data
				},
				afterTrack: (hookContext: TrackSeriesContext) => {
					for (const hook of this.record.getHooks?.(metadata) ?? []) {
						hook.afterTrack?.(hookContext)
					}
				},
			},
		]
	}
}
