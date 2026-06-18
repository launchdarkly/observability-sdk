import * as withObservabilityAppRouter from './util/with-observability-app-router'
import * as withObservabilityPageRouter from './util/with-observability-page-router'

import { isNodeJsRuntime } from './util/is-node-js-runtime'
import type { ObservabilityEnv } from './util/types'

export { Handlers, LDObserve } from '@launchdarkly/observability-node'
export { observabilityMiddleware } from './util/observability-middleware'
export { isNodeJsRuntime } from './util/is-node-js-runtime'
export { registerObservability } from './util/register-observability'
export type { ObservabilityEnv } from './util/types'

type PageRouterObservabilityHandler = ReturnType<
	typeof withObservabilityPageRouter.Highlight
>

/**
 * Default Pages Router wrapper. Alias for {@link PageRouterObservability}.
 */
export const Observability = PageRouterObservability

/**
 * Wrap a Pages Router API handler to record LaunchDarkly traces. Only supported
 * in the Node.js runtime.
 */
export function PageRouterObservability(
	env: ObservabilityEnv,
): PageRouterObservabilityHandler {
	if (isNodeJsRuntime()) {
		return withObservabilityPageRouter.Highlight(env)
	}
	throw new Error(`unidentified NEXT_RUNTIME: ${process.env.NEXT_RUNTIME}`)
}

/**
 * Wrap an App Router route handler to record LaunchDarkly traces. Only supported
 * in the Node.js runtime.
 */
export function AppRouterObservability(env: ObservabilityEnv) {
	if (isNodeJsRuntime()) {
		return withObservabilityAppRouter.Highlight(env)
	}
	throw new Error(`unidentified NEXT_RUNTIME: ${process.env.NEXT_RUNTIME}`)
}

/**
 * Edge runtime is not yet supported by the LaunchDarkly observability node SDK.
 */
export function EdgeObservability(_: ObservabilityEnv): never {
	throw new Error(`unsupported NEXT_RUNTIME: ${process.env.NEXT_RUNTIME}`)
}
