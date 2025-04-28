import type { HighlightOptions, LDClientMin } from 'client'
import type { LDPlugin, LDPluginEnvironmentMetadata } from './plugin'
import type { Hook } from '../integrations/launchdarkly/types/Hooks'

export class Record implements LDPlugin {
	constructor(projectID?: string | number, opts?: HighlightOptions) {}
	getMetadata() {
		throw new Error('Method not implemented.')
	}
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void {
		throw new Error('Method not implemented.')
	}
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[] {
		throw new Error('Method not implemented.')
	}
}
