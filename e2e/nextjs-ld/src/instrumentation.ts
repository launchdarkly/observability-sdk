import { CONSTANTS } from '@/constants'

export async function register() {
	const { registerObservability } =
		await import('@launchdarkly/observability-next/server')
	await registerObservability({
		sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
		serviceName: 'nextjs-ld-backend',
		environment: 'e2e-test',
	})
}
