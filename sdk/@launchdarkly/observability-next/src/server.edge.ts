import type { ObservabilityEnv } from './util/types'

export { observabilityMiddleware } from './util/observability-middleware'
export { isNodeJsRuntime } from './util/is-node-js-runtime'
export type { ObservabilityEnv } from './util/types'

/**
 * No-op in the edge runtime. The OpenTelemetry-based node SDK that powers
 * server-side observability is not edge-compatible, so initialization only
 * happens in the Node.js runtime.
 */
export async function registerObservability(_: ObservabilityEnv) {}

export function PageRouterObservability(_: ObservabilityEnv): never {
	throw new Error('Do not use PageRouterObservability() in the edge runtime.')
}

export function AppRouterObservability(_: ObservabilityEnv): never {
	throw new Error('Do not use AppRouterObservability() in the edge runtime.')
}

export function EdgeObservability(_: ObservabilityEnv): never {
	throw new Error(`unsupported NEXT_RUNTIME: ${process.env.NEXT_RUNTIME}`)
}
