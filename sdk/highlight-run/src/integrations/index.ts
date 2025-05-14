import type { ErrorMessage, Source } from '../client/types/shared-types'
import { RecordMetric } from '../client/types/types'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import type { Hook } from './launchdarkly'

export interface IntegrationClient {
	init(sessionSecureID: string): void

	identify(
		sessionSecureID: string,
		user_identifier: string,
		user_object: object,
		source?: Source,
	): void

	error(sessionSecureID: string, error: ErrorMessage): void

	track(sessionSecureID: string, metadata: object): void

	recordGauge(sessionSecureID: string, metric: RecordMetric): void

	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
}
