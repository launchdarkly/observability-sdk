import type {
	LDContext,
	integrations,
	LDPluginEnvironmentMetadata,
	LDPluginMetadata,
	LDPluginBase,
} from '@launchdarkly/js-server-sdk-common'

// The client interface varies per SDK. This SDK is primarily designed for use with the Node server SDK, but
// using this interface we don't preclude it from being used with other SDKs.
// This minimal interface is also more resilient to changes.
export interface LDClientMin {
	// Currently the server integration doesn't require any SDK methods.
}

export type LDPlugin = LDPluginBase<LDClientMin, integrations.Hook>
