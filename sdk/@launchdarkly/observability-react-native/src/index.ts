/**
 * This is the API reference for the LaunchDarkly Observability Plugin for React Native.
 *
 * In typical usage you will only need to instantiate the {@link Observability} plugin and pass it to the LaunchDarkly client during initialization.
 *
 * The settings for the observability plugins are defined by the {@link ReactNativeOptions} interface.
 *
 * The {@link LDObserve} singleton is used for manual tracking of events, metrics, and logs.
 * The singleton implements the {@link Observe} interface.
 *
 * # Quick Start
 * ```typescript
 * import {
 *  AutoEnvAttributes,
 *  LDProvider,
 *  ReactNativeLDClient,
 * } from '@launchdarkly/react-native-client-sdk';
 * import { Observability, LDObserve } from '@launchdarkly/observability-react-native'
 *
 * const client = new ReactNativeLDClient(
 *   mobileKey,
 *   AutoEnvAttributes.Enabled,
 *   {
 *      plugins: [
 *        new Observability({
 *          // Error handling is enabled by default for all error types
 *          // Optional configuration:
 *          errorHandling: {
 *            errorSampleRate: 1.0, // Default: 1.0 (capture all errors)
 *            beforeSend: (error, context) => {
 *              // Optional: filter errors before sending
 *              return error
 *            }
 *          }
 *        }),
 *      ]
 *  }
 * )
 * ```
 *
 * @packageDocumentation
 */

import { setupURLPolyfill } from 'react-native-url-polyfill'
// Imported for documentation.
import { ReactNativeOptions } from './api/Options'

// React Native's built-in `URL` does not implement `.origin`, which the
// OpenTelemetry OTLP HTTP exporter relies on. Without a spec-compliant `URL`,
// every trace/log export throws "URL.origin is not implemented" and is silently
// dropped (regardless of Old/New Architecture). Apply the polyfill at the
// package entry so a working `URL` is guaranteed before any exporter runs.
// (An explicit call is used instead of the `/auto` side-effect import so it
// survives the bundler's aggressive tree-shaking.)
setupURLPolyfill()

export { LDObserve } from './sdk/LDObserve'
export type { Observe } from './api/Observe'
export { Observability } from './plugin/observability'
export type { LDPlugin, LDClientMin } from './plugin/plugin'
export * from './api/index'
