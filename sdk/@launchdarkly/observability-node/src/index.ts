/**
 * This is the API reference for the LaunchDarkly Observability Plugin for Node.js.
 *
 * In typical usage you will only need to instantiate the {@link Observability} plugin and pass it to the LaunchDarkly client during initialization.
 *
 * The settings for the observability plugins are defined by the {@link NodeOptions} interface.
 *
 * The {@link LDObserve} singleton is used for manual tracking of events, metrics, and logs.
 * The singleton implements the {@link Observe} interface.
 *
 * # Quick Start
 * ```typescript
 * import { init } from '@launchdarkly/node-server-sdk'
 * import { Observability } from '@launchdarkly/observability-node'
 *
 * const client = init(
 *   'sdk-key',
 *   {
 *     plugins: [
 *       new Observability(),
 *     ],
 *   },
 * )
 * ```
 *
 * @packageDocumentation
 */

// Imported for documentation.
import { NodeOptions } from './api/Options.js'

export { LDObserve } from './sdk/LDObserve.js'
export { Observe } from './api/Observe.js'
export { Observability } from './plugin/observability.js'
export * as Handlers from './client/handlers.js'
export * from './api/index.js'
