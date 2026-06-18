// Centralize env access so Next inlines the NEXT_PUBLIC_* values.
export const CONSTANTS = {
	LAUNCHDARKLY_CLIENT_SIDE_ID:
		process.env.NEXT_PUBLIC_LAUNCHDARKLY_CLIENT_SIDE_ID ?? '',
	LAUNCHDARKLY_SDK_KEY: process.env.LAUNCHDARKLY_SDK_KEY ?? '',
}
