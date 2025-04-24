import type { LDClient, LDCredentials } from '@launchdarkly/js-client-sdk'

interface Plugin {
	register(client: LDClient, credentials: LDCredentials)
}
