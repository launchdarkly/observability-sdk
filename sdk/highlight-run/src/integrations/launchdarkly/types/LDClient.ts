import type { Hook } from '@launchdarkly/js-client-sdk'

export interface LDClientMin {
	track(key: string, data?: any, metricValue?: number): void
	identify(ctx: any): void
	addHook(hook: Hook): void
}
