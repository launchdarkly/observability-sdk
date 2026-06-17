import type { ObservabilityEnv } from './util/types'

export { observabilityMiddleware } from './util/observability-middleware'
export { isNodeJsRuntime } from './util/is-node-js-runtime'
export type { ObservabilityEnv } from './util/types'

// `import type` is erased at build time, so referencing the node SDK here keeps
// `server.edge.js` free of any runtime `@launchdarkly/observability-node`
// import while letting the edge build expose the same `/server` surface as the
// Node build.
type Observe = import('@launchdarkly/observability-node').Observe
type HandlersNamespace =
	typeof import('@launchdarkly/observability-node').Handlers

const edgeUnsupported = (name: string): never => {
	throw new Error(
		`${name} is not supported in the edge runtime. The OpenTelemetry-based ` +
			`node SDK that powers server-side observability is not edge-compatible.`,
	)
}

/**
 * Edge-runtime stub for the manual-tracking singleton. Re-exported so importing
 * `LDObserve` from `@launchdarkly/observability-next/server` in edge code
 * resolves at bundle time (matching the Node build and `server.d.ts`); any
 * access throws a clear error instead of failing as a missing export.
 */
export const LDObserve: Observe = new Proxy({} as Observe, {
	get() {
		return edgeUnsupported('LDObserve')
	},
})

/**
 * Edge-runtime stub for the request handlers. See {@link LDObserve} for why this
 * is re-exported rather than omitted.
 */
export const Handlers: HandlersNamespace = new Proxy({} as HandlersNamespace, {
	get() {
		return edgeUnsupported('Handlers')
	},
})

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
