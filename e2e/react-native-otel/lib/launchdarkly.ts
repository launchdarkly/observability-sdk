import {
	ReactNativeLDClient,
	AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk'
import { Observability } from '@launchdarkly/observability-react-native'
import Constants from 'expo-constants'

let ldClient: ReactNativeLDClient | null = null

// Example user context - in a real app, this would come from your authentication system
const user = {
	key: 'example-user',
	name: 'Example User',
	email: 'example@test.com',
	anonymous: false,
}

const mobileKey = Constants.expoConfig?.extra?.sdkKey
const otlpEndpoint = Constants.expoConfig?.extra?.otel?.endpoint
const serviceName = Constants.expoConfig?.extra?.otel?.serviceName

export async function initializeLaunchDarkly() {
	try {
		if (!mobileKey) {
			console.warn(
				'LaunchDarkly mobile key not configured. Observability will still be initialized for demo purposes.',
			)
			return
		}

		// Initialize LaunchDarkly client with observability plugin
		ldClient = new ReactNativeLDClient(
			mobileKey,
			AutoEnvAttributes.Enabled,
			{
				plugins: [
					new Observability({
						serviceName,
						serviceVersion:
							Constants.expoConfig?.version || '1.0.0',
						otlpEndpoint,
						debug: __DEV__,
						customHeaders: {
							'x-service-name': serviceName,
							'x-environment': __DEV__
								? 'development'
								: 'production',
						},
						sessionTimeout: 30 * 60 * 1000, // 30 minutes
					}),
				],
			},
		)

		await ldClient.identify(user)
		console.log('LaunchDarkly client initialized with observability')
		console.log(`OTLP endpoint: ${otlpEndpoint}`)
	} catch (error) {
		console.error('Failed to initialize LaunchDarkly:', error)
	}
}

export function getLDClient(): ReactNativeLDClient | null {
	return ldClient
}

export async function closeLDClient() {
	if (ldClient) {
		await ldClient.close()
		ldClient = null
	}
}
