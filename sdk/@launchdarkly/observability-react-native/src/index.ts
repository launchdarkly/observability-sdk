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
 *        new Observability(),
 *      ]
 *  }
 * )
 * ```
 *
 * @packageDocumentation
 */

// Imported for documentation.
import { ReactNativeOptions } from './api/Options'

export { LDObserve } from './sdk/LDObserve'
export type { Observe } from './api/Observe'
export { Observability } from './plugin/observability'
export * from './api/index'
